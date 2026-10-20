package io.github.cmdralph.fabricated.core.translate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Semaphore;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import io.github.cmdralph.fabricated.core.accesswidener.AccessTransformerConverter;
import io.github.cmdralph.fabricated.core.accesswidener.AccessWidenerException;
import io.github.cmdralph.fabricated.core.accesswidener.AccessWidenerFile;
import io.github.cmdralph.fabricated.core.accesswidener.AccessWidenerFile.InjectedInterface;
import io.github.cmdralph.fabricated.core.accesswidener.AccessWidenerParser;
import io.github.cmdralph.fabricated.core.archive.ArchiveException;
import io.github.cmdralph.fabricated.core.archive.DeterministicJarWriter;
import io.github.cmdralph.fabricated.core.archive.Hashing;
import io.github.cmdralph.fabricated.core.archive.SafeArchive;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.EnvironmentStripper;
import io.github.cmdralph.fabricated.core.cache.TranslationCache;
import io.github.cmdralph.fabricated.core.config.FabricatedConfig.ModOverride;
import io.github.cmdralph.fabricated.core.json.JsonException;
import io.github.cmdralph.fabricated.core.metadata.ModIds;
import io.github.cmdralph.fabricated.core.metadata.FabricModMetadata;
import io.github.cmdralph.fabricated.core.metadata.FabricModMetadata.MixinEntry;
import io.github.cmdralph.fabricated.core.mixin.MixinConfigFile;
import io.github.cmdralph.fabricated.core.mixin.MixinPreflight;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding.Severity;
import io.github.cmdralph.fabricated.core.mixin.Refmap;
import io.github.cmdralph.fabricated.core.remap.JarRemapper;
import io.github.cmdralph.fabricated.core.remap.MappingException;
import io.github.cmdralph.fabricated.core.remap.TokenRemapper;
import io.github.cmdralph.fabricated.core.resolve.Candidate;
import io.github.cmdralph.fabricated.core.translate.TranslationEnvironment.Namespace;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.metadata.CustomValue;

/**
 * Translates one Fabric mod into the files NeoForge loads. Deterministic, cached, and bytes-only: no
 * mod code is loaded or run.
 */
public final class ModTranslator {
	public static final String OVERLAY = "overlay.jar";
	public static final String REMAPPED = "remapped.jar";
	public static final String AT_PATH = "META-INF/accesstransformer.cfg";
	/** The entrypoint of a mod's page in Sodium's options, also the mod property Sodium for NeoForge reads it from. */
	private static final String SODIUM_CONFIG_ENTRYPOINT = "sodium:config_api_user";
	/** A mod's overrides of Sodium's mixin options, as a custom value (Fabric) or a key of its mods.toml entry (NeoForge). */
	private static final String SODIUM_OPTIONS = "sodium:options";

	/** Heap a translation may count on, in MiB: a quarter of the maximum, shared by all translations at once. */
	private static final int MEMORY_MIB = (int) Math.max(64, Math.min(Integer.MAX_VALUE, Runtime.getRuntime().maxMemory() / 4 / (1 << 20)));
	private static final Semaphore MEMORY = new Semaphore(MEMORY_MIB);

	private final TranslationEnvironment env;
	private final TranslationCache cache;
	private final io.github.cmdralph.fabricated.core.archive.ArchiveLimits limits;
	private final PlatformRenames renames;

	public ModTranslator(TranslationEnvironment env, TranslationCache cache, io.github.cmdralph.fabricated.core.archive.ArchiveLimits limits) {
		this.env = env;
		this.cache = cache;
		this.limits = limits;
		this.renames = PlatformRenames.of(env.untransformable());
	}

	/**
	 * @param loadAfter NeoForge ids this mod should load after (its dependencies that are present)
	 */
	public TranslatedMod translate(Candidate candidate, ModOverride override, List<String> loadAfter) throws TranslationException {
		String neoId = ModIds.toNeoForgeId(candidate.id());
		if (ModIds.RESERVED.contains(neoId)) {
			throw new TranslationException("Mod id '" + candidate.id() + "' cannot be used on NeoForge", "Report this to the mod author.");
		}
		List<String> after = loadAfter.stream().sorted().distinct().toList();
		String key = Hashing.sha256(String.join("|", candidate.sha256(), env.minecraftVersion(), env.neoForgeVersion(),
				env.side(), env.fabricatedVersion(), Integer.toString(TranslationEnvironment.TRANSLATION_SCHEMA),
				env.mappingsHash(), env.runtimeMixinVersion(), override.fingerprint(), String.join(",", after)));
		String entryName = TranslationCache.entryName(neoId, key);

		Optional<TranslationCache.Entry> hit = env.namespace() == Namespace.INTERMEDIARY
				? cache.lookup(entryName, OVERLAY, REMAPPED) : cache.lookup(entryName, OVERLAY);
		if (hit.isPresent() && installedUnchanged(hit.get())) {
			return fromCache(candidate, neoId, hit.get());
		}

		// Translating holds a mod's files several times over (entries, remapped jar, cache copy); mods are translated in
		// parallel, so large ones take turns instead of exhausting the heap together.
		int permits = memoryPermits(candidate.path());
		MEMORY.acquireUninterruptibly(permits);
		try {
			long start = System.nanoTime();
			try (SafeArchive archive = SafeArchive.open(candidate.path(), limits)) {
				Map<String, byte[]> remapped = null;
				List<String> notes = new ArrayList<>();
				TokenRemapper tokens = null;
				if (env.namespace() == Namespace.INTERMEDIARY) {
					tokens = new TokenRemapper(env.mappings());
					Set<String> refmaps = collectRefmapPaths(candidate.metadata(), archive);
					JarRemapper.Result result = new JarRemapper(env.mappings()).remap(archive, refmaps);
					remapped = result.files();
					if (!result.strippedSignatureFiles().isEmpty()) {
						notes.add("Removed jar signature files (signatures cannot survive remapping): " + result.strippedSignatureFiles());
					}
				}
				Map<String, byte[]> view = remapped;
				// Mixin classes and configs are read several times; reading an entry again would count against the
				// archive's decompression limit again.
				Map<String, byte[]> read = new HashMap<>();
				FileView files = view != null ? view::get : name -> {
					byte[] data = read.get(name);
					if (data == null && !read.containsKey(name)) read.put(name, data = archive.read(name));
					return data;
				};

				Map<String, byte[]> overlay = new TreeMap<>();
				List<PreflightFinding> findings = new ArrayList<>();
				Map<String, String> installedClasses = new TreeMap<>();
				List<ModsTomlWriter.MixinConfigSpec> mixinSpecs = processMixins(candidate, override, files,
						remapped != null ? remapped.keySet() : archive.classEntryNames(), overlay, findings, installedClasses);

				List<InjectedInterface> injections = List.of();
				int atLines = 0;
				String tweakerPath = candidate.metadata().classTweaker();
				if (tweakerPath != null) {
					byte[] data = files.read(tweakerPath);
					if (data == null) {
						notes.add("Access widener " + tweakerPath + " declared in fabric.mod.json is missing; ignored");
					} else {
						String text = new String(data, StandardCharsets.UTF_8);
						if (tokens != null) text = tokens.remap(text);
						AccessWidenerFile parsed = AccessWidenerParser.parse(text);
						checkNamespace(parsed.namespace(), candidate);
						checkEnumExtensions(parsed, candidate);
						AccessTransformerConverter.Result at = AccessTransformerConverter.convert(parsed, env.game());
						notes.addAll(at.warnings());
						injections = at.injectedInterfaces();
						atLines = at.lineCount();
						if (!at.isEmpty()) overlay.put(AT_PATH, at.accessTransformer().getBytes(StandardCharsets.UTF_8));
					}
				}

				Set<String> environmentClasses = remapped != null ? environmentClasses(remapped.keySet(), files)
						: environmentClasses(archive.classEntryNames(), archive::read);
				int renamed = remapped != null ? applyRenames(remapped, remapped.keySet(), files)
						: applyRenames(overlay, archive.classEntryNames(), archive::read);
				if (renamed > 0) notes.add(renamed + " class(es) use NeoForge members as Forge Config API Port's copy of NeoForge declares them; the references are adapted to NeoForge's own");

				FabricModMetadata md = candidate.metadata();
				ModsTomlWriter.Spec spec = new ModsTomlWriter.Spec(neoId, md.getVersion().getFriendlyString(), md.getName(),
						md.getDescription(), List.copyOf(md.getAuthors()), List.copyOf(md.getContributors()),
						List.copyOf(md.getLicense()), url(md.getContact().get("homepage").orElse(null), "homepage", notes),
						url(md.getContact().get("issues").orElse(null), "issues", notes), md.getIconPath(256).filter(archive::contains).orElse(null), mixinSpecs,
						overlay.containsKey(AT_PATH), after, sodiumProperties(md, notes), sodiumOptions(md, notes));
				overlay.put(ModsTomlWriter.PATH, ModsTomlWriter.write(spec).getBytes(StandardCharsets.UTF_8));
				overlay.put("META-INF/MANIFEST.MF", overlayManifest(neoId));

				Map<String, byte[]> outputs = new LinkedHashMap<>();
				outputs.put(OVERLAY, jarBytes(overlay));
				if (remapped != null) outputs.put(REMAPPED, jarBytes(remapped));

				long millis = (System.nanoTime() - start) / 1_000_000;
				Map<String, Object> meta = new LinkedHashMap<>();
				meta.put("modId", candidate.id());
				meta.put("version", md.getVersion().getFriendlyString());
				meta.put("source", candidate.origin());
				meta.put("sourceSha256", candidate.sha256());
				meta.put("minecraft", env.minecraftVersion());
				meta.put("neoforge", env.neoForgeVersion());
				meta.put("side", env.side());
				meta.put("millis", millis);
				meta.put("accessTransformers", atLines);
				meta.put("notes", notes);
				meta.put("findings", findings.stream().map(ModTranslator::findingToJson).toList());
				meta.put("injectedInterfaces", injections.stream().map(ModTranslator::injectionToJson).toList());
				meta.put("environmentClasses", List.copyOf(environmentClasses));
				meta.put("installedClasses", installedClasses);
				TranslationCache.Entry entry = cache.store(entryName, outputs, meta);
				Path jar = remapped != null ? entry.file(REMAPPED) : candidate.path();
				return new TranslatedMod(candidate, neoId, jar, entry.file(OVERLAY), List.copyOf(findings), atLines,
						List.copyOf(notes), injections, environmentClasses, false, millis);
			} catch (ArchiveException e) {
				throw new TranslationException("The jar is unsafe or corrupt: " + e.getMessage(), "Re-download the mod from its official page.");
			} catch (MappingException e) {
				throw new TranslationException("Remapping failed: " + e.getMessage(), "The mod may use bytecode Fabricated cannot process; report it.", List.of(), e);
			} catch (AccessWidenerException e) {
				throw new TranslationException("Invalid access widener: " + e.getMessage(), "Report this to the mod author.");
			} catch (IOException e) {
				throw new TranslationException("I/O error while translating: " + e.getMessage(), "Check disk space and permissions of the game directory.", List.of(), e);
			}
		} finally {
			MEMORY.release(permits);
		}
	}

	/**
	 * A translation's share of {@link #MEMORY}, never more than all of it. Remapping (1.21.11) holds a jar's entries
	 * uncompressed about four times over (the entries, the output buffer as it grows, and its copy), so it is sized
	 * from the uncompressed total: a jar of data that compresses well (data packs' JSON, 10 to 20 times) would get a
	 * fraction of what it uses from the compressed size. Otherwise about ten times the compressed jar.
	 */
	private int memoryPermits(Path jar) {
		long bytes;
		try {
			if (env.namespace() == Namespace.INTERMEDIARY) {
				try (SafeArchive archive = SafeArchive.open(jar, limits)) {
					bytes = 4 * Math.max(archive.declaredUncompressedSize(), Files.size(jar));
				}
			} else {
				bytes = 10 * Files.size(jar);
			}
		} catch (IOException e) {
			bytes = 0; // the translation reports it
		}
		return (int) Math.max(1, Math.min(MEMORY_MIB, bytes / (1 << 20)));
	}

	/** Classes whose bytes mention Fabric's {@code @Environment} annotations, to strip for the other side at class load. */
	private static Set<String> environmentClasses(Collection<String> entries, FileView files) throws ArchiveException {
		Set<String> classes = new TreeSet<>();
		for (String name : entries) {
			if (!name.endsWith(".class") || name.startsWith("META-INF/")) continue;
			byte[] data = files.read(name);
			if (data != null && EnvironmentStripper.mayNeedStripping(data)) classes.add(name.substring(0, name.length() - ".class".length()));
		}
		return Set.copyOf(classes);
	}

	/**
	 * Puts the mod's classes that use members NeoForge renamed (see {@link PlatformRenames}), with the references
	 * renamed, into {@code target}; returns how many there were.
	 */
	private int applyRenames(Map<String, byte[]> target, Collection<String> entries, FileView files) throws ArchiveException {
		int count = 0;
		for (String name : List.copyOf(entries)) {
			if (!name.endsWith(".class") || name.startsWith("META-INF/")) continue;
			byte[] data = target.containsKey(name) ? target.get(name) : files.read(name);
			byte[] renamed = data == null ? null : renames.apply(data);
			if (renamed != null) {
				target.put(name, renamed);
				count++;
			}
		}
		return count;
	}

	/** Reads an entry of the mod being translated (remapped when applicable). */
	@FunctionalInterface
	private interface FileView {
		byte[] read(String name) throws ArchiveException;
	}

	/** @param installedClasses receives the installed classes pre-flight read, with their fingerprints */
	private List<ModsTomlWriter.MixinConfigSpec> processMixins(Candidate candidate, ModOverride override, FileView files,
			Collection<String> entries, Map<String, byte[]> overlay, List<PreflightFinding> findings, Map<String, String> installedClasses)
			throws ArchiveException, TranslationException {
		FabricModMetadata md = candidate.metadata();
		String behavior = MixinCompat.clampForNeoForge(MixinCompat.fabricBehaviorVersion(md.getDependencies()), env.runtimeMixinVersion(),
				env.lowestMixinBehavior());
		EnvType side = env.clientSide() ? EnvType.CLIENT : EnvType.SERVER;
		List<ModsTomlWriter.MixinConfigSpec> specs = new ArrayList<>();
		// Pre-flight sees the mod as it will load: with the handler methods left out below.
		FileView translated = name -> overlay.containsKey(name) ? overlay.get(name) : files.read(name);
		MixinPreflight preflight = new MixinPreflight(env.installedIndex(), env.installed(), classSource(translated), env.untransformable(),
				env.clientSide(), MixinCompat.missingTargetsFatal(behavior));
		Map<String, MixinConfigFile> configs = new HashMap<>();
		Map<String, Refmap> refmaps = new HashMap<>();

		for (MixinEntry entry : md.compatHints().mixinsOr(md.mixins())) {
			if (!entry.environment().matches(side)) continue;
			byte[] data = files.read(entry.config());
			if (data == null) {
				throw new TranslationException("Mixin config " + entry.config() + " declared in fabric.mod.json is missing from the jar",
						"The jar is incomplete; re-download it.");
			}
			MixinConfigFile config;
			Refmap refmap = Refmap.EMPTY;
			try {
				config = MixinConfigFile.parse(new String(data, StandardCharsets.UTF_8));
				byte[] parentData = config.parent() != null ? files.read(config.parent()) : null;
				if (parentData != null) config = config.inheriting(MixinConfigFile.parse(new String(parentData, StandardCharsets.UTF_8)));
				if (config.refmap() != null) {
					byte[] refmapData = files.read(config.refmap());
					if (refmapData != null) refmap = Refmap.parse(new String(refmapData, StandardCharsets.UTF_8));
				}
			} catch (JsonException e) {
				throw new TranslationException("Mixin config " + entry.config() + " is invalid: " + e.getMessage(), "Report this to the mod author.");
			}
			configs.put(entry.config(), config);
			refmaps.put(entry.config(), refmap);

			Skips skips = skipsFor(entry.config(), config, override.skipMixins());
			Set<String> wholeSkips = skips.mixins();
			for (Map.Entry<String, Set<String>> handlers : skips.members().entrySet()) {
				String path = config.internalName(handlers.getKey()) + ".class";
				byte[] mixin = files.read(path);
				if (mixin == null) continue;
				Set<String> missing = new TreeSet<>(handlers.getValue());
				overlay.put(path, withoutMembers(mixin, handlers.getValue(), missing));
				for (String method : missing) {
					findings.add(new PreflightFinding(Severity.WARN, entry.config(), config.internalName(handlers.getKey()).replace('/', '.'),
							"has no method or field " + method + " to leave out", ""));
				}
			}
			Set<String> skip = withDependents(entry.config(), config, wholeSkips, translated, findings);
			overlay.putAll(preflight.repairs(entry.config(), config, refmap, skip, findings));
			if (!override.ignorePreflight()) {
				List<PreflightFinding> checked = preflight.check(entry.config(), config, refmap, skip);
				findings.addAll(checked);
				// As Mixin does with an injector that may fail: only the failing handler goes, the rest of the mixin
				// (its interfaces, accessors and other injectors) still applies.
				Set<String> leftOut = new TreeSet<>(skip);
				Map<String, Set<String>> handlers = new TreeMap<>();
				for (PreflightFinding f : checked) {
					if (f.severity() != Severity.LEFT_OUT) continue;
					if (f.handler() == null) leftOut.add(relativeMixin(config, f.mixin()));
					else handlers.computeIfAbsent(f.mixin().replace('.', '/') + ".class", k -> new TreeSet<>()).add(f.handler());
				}
				leaveOutHandlers(handlers, translated, overlay);
				if (leftOut.size() > skip.size()) skip = withDependents(entry.config(), config, leftOut, translated, findings);
			}
			if (!skip.isEmpty()) {
				overlay.put(entry.config(), config.withoutMixins(skip).getBytes(StandardCharsets.UTF_8));
			}
			specs.add(new ModsTomlWriter.MixinConfigSpec(entry.config(), behavior));
		}
		if (!override.ignorePreflight()) checkPluginMixins(entries, configs, refmaps, preflight, translated, overlay, findings);
		installedClasses.putAll(preflight.installedClasses());

		List<PreflightFinding> failures = findings.stream().filter(f -> f.severity() == Severity.FAIL).toList();
		if (!failures.isEmpty()) {
			String summary = failures.stream().limit(3).map(f -> (f.mixin() != null ? f.mixin() + ": " : "") + f.message())
					.collect(Collectors.joining("; "));
			if (failures.size() > 3) summary += "; and " + (failures.size() - 3) + " more";
			String skipHint = failures.stream().filter(f -> f.mixin() != null)
					.map(f -> f.config() + ":" + relativeMixin(configs.get(f.config()), f.mixin())).distinct()
					.collect(Collectors.joining("\", \""));
			throw new TranslationException("Mixin pre-flight failed: " + summary,
					"Update the mod or ask its author for a NeoForge build. If the affected mixins are cosmetic, add \"skipMixins\": [\""
							+ skipHint + "\"] for " + candidate.id() + " in config/fabricated.json.",
					failures, null);
		}
		return specs;
	}

	private static void leaveOutHandlers(Map<String, Set<String>> handlers, FileView files, Map<String, byte[]> overlay) throws ArchiveException {
		for (Map.Entry<String, Set<String>> h : handlers.entrySet()) {
			byte[] mixin = files.read(h.getKey());
			if (mixin != null) overlay.put(h.getKey(), withoutMembers(mixin, h.getValue(), new HashSet<>()));
		}
	}

	/**
	 * Mixins a config's plugin adds at runtime ({@code IMixinConfigPlugin.getMixins}) are listed in no config, so they
	 * are found as the other classes in the config's package (Mixin requires them there). Pre-flight checks them as
	 * listed; an injector that would crash is left out, while problems of the whole mixin (a target missing on this
	 * side, say) are not reported: the plugin may well never add it.
	 */
	private static void checkPluginMixins(Collection<String> entries, Map<String, MixinConfigFile> configs, Map<String, Refmap> refmaps,
			MixinPreflight preflight, FileView files, Map<String, byte[]> overlay, List<PreflightFinding> findings) throws ArchiveException {
		Set<String> listed = new HashSet<>();
		for (MixinConfigFile config : configs.values()) {
			for (String mixin : config.allMixins()) listed.add(config.internalName(mixin) + ".class");
		}
		for (Map.Entry<String, MixinConfigFile> e : new TreeMap<>(configs).entrySet()) {
			MixinConfigFile config = e.getValue();
			if (config.plugin() == null || config.plugin().isBlank() || config.mixinPackage() == null || config.mixinPackage().isEmpty()) continue;
			String prefix = config.internalName("");
			List<String> unlisted = new ArrayList<>();
			for (String entry : entries) {
				if (entry.startsWith(prefix) && entry.endsWith(".class") && !entry.contains("$") && listed.add(entry)) {
					unlisted.add(entry.substring(prefix.length(), entry.length() - ".class".length()).replace('/', '.'));
				}
			}
			if (unlisted.isEmpty()) continue;
			Map<String, Set<String>> handlers = new TreeMap<>();
			for (PreflightFinding f : preflight.check(e.getKey(), config.listing(unlisted), refmaps.get(e.getKey()), Set.of())) {
				if (f.severity() != Severity.LEFT_OUT || f.handler() == null) continue;
				findings.add(f);
				handlers.computeIfAbsent(f.mixin().replace('.', '/') + ".class", k -> new TreeSet<>()).add(f.handler());
			}
			leaveOutHandlers(handlers, files, overlay);
		}
	}

	/** The mixin's name as its config lists it. */
	private static String relativeMixin(MixinConfigFile config, String mixinDotted) {
		String pkg = config != null ? config.mixinPackage() : null;
		return pkg != null && mixinDotted.startsWith(pkg + ".") ? mixinDotted.substring(pkg.length() + 1) : mixinDotted;
	}

	/**
	 * {@code skip} plus the kept mixins that use an interface only a skipped mixin adds to its target (one of the mod's
	 * own interfaces): with the adding mixin gone, their casts to it would fail at runtime. Repeated until stable.
	 */
	private static Set<String> withDependents(String configName, MixinConfigFile config, Set<String> skip, FileView files,
			List<PreflightFinding> findings) throws ArchiveException {
		if (skip.isEmpty()) return skip;
		Set<String> all = new TreeSet<>(skip);
		Map<String, String> addedBy = new HashMap<>();
		for (boolean grew = true; grew; ) {
			grew = false;
			for (String mixin : all) {
				byte[] data = files.read(config.internalName(mixin) + ".class");
				if (data == null) continue;
				for (String itf : new ClassReader(data).getInterfaces()) {
					if (files.read(itf + ".class") != null) addedBy.putIfAbsent(itf, mixin);
				}
			}
			// Still added when a kept mixin implements it too.
			for (String mixin : config.allMixins()) {
				byte[] data = all.contains(mixin) ? null : files.read(config.internalName(mixin) + ".class");
				if (data != null) addedBy.keySet().removeAll(List.of(new ClassReader(data).getInterfaces()));
			}
			for (String mixin : config.allMixins()) {
				if (all.contains(mixin)) continue;
				byte[] data = files.read(config.internalName(mixin) + ".class");
				if (data == null) continue;
				Set<String> uses = referencedClasses(data);
				for (Map.Entry<String, String> e : addedBy.entrySet()) {
					if (!uses.contains(e.getKey())) continue;
					all.add(mixin);
					findings.add(new PreflightFinding(Severity.WARN, configName, config.internalName(mixin).replace('/', '.'),
							"also left out: it uses " + e.getKey().replace('/', '.') + ", which only the skipped mixin " + e.getValue() + " adds", ""));
					grew = true;
					break;
				}
				if (grew) break;
			}
		}
		return all;
	}

	/** Every class a class file refers to: in its signature, members, instructions and annotations. */
	private static Set<String> referencedClasses(byte[] data) {
		Set<String> refs = new HashSet<>();
		new ClassReader(data).accept(new ClassRemapper(new ClassVisitor(Opcodes.ASM9) {
		}, new Remapper() {
			@Override
			public String map(String internalName) {
				refs.add(internalName);
				return internalName;
			}
		}), ClassReader.SKIP_FRAMES);
		return refs;
	}

	/**
	 * The skip entries that apply to {@code configName}, for mixins it declares: {@code "config.json:Name"} (one config)
	 * or {@code "Name"} (every config) leaves out the whole mixin; {@code "Name#member"} only the methods and fields of
	 * that name in a mixin kept otherwise (when its other injections still work on NeoForge).
	 */
	record Skips(Set<String> mixins, Map<String, Set<String>> members) {
	}

	static Skips skipsFor(String configName, MixinConfigFile config, Set<String> configured) {
		Set<String> declared = new HashSet<>(config.allMixins());
		Set<String> mixins = new TreeSet<>();
		Map<String, Set<String>> members = new TreeMap<>();
		for (String s : configured) {
			String name = skipTarget(configName, s);
			if (name == null) continue;
			int hash = name.indexOf('#');
			String mixin = hash < 0 ? name : name.substring(0, hash);
			if (!declared.contains(mixin)) continue;
			if (hash < 0) mixins.add(mixin);
			else members.computeIfAbsent(mixin, k -> new TreeSet<>()).add(name.substring(hash + 1));
		}
		members.keySet().removeAll(mixins);
		return new Skips(mixins, members);
	}

	/** The mixin part of a skip entry when it applies to {@code configName}, else null. */
	private static String skipTarget(String configName, String skip) {
		int colon = skip.indexOf(':');
		if (colon >= 0 && !skip.substring(0, colon).equals(configName)) return null;
		return colon >= 0 ? skip.substring(colon + 1) : skip;
	}

	/**
	 * {@code data} without the methods and fields named in {@code names} (a method name with its descriptor removes only
	 * that method); the names found are removed from {@code missing}.
	 */
	static byte[] withoutMembers(byte[] data, Set<String> names, Set<String> missing) {
		ClassReader reader = new ClassReader(data);
		// A fresh constant pool, so nothing the removed members referred to is left behind (see withDependents).
		ClassWriter writer = new ClassWriter(0);
		reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				String exact = name + descriptor;
				if (!names.contains(name) && !names.contains(exact)) return super.visitMethod(access, name, descriptor, signature, exceptions);
				missing.remove(name);
				missing.remove(exact);
				return null;
			}

			@Override
			public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
				if (!names.contains(name)) return super.visitField(access, name, descriptor, signature, value);
				missing.remove(name);
				return null;
			}
		}, 0);
		return writer.toByteArray();
	}

	private static ClassBytesSource classSource(FileView files) {
		return internalName -> files.read(internalName + ".class");
	}

	private void checkNamespace(String namespace, Candidate candidate) throws TranslationException {
		boolean ok = switch (env.namespace()) {
			case INTERMEDIARY -> namespace.equals("intermediary") || namespace.equals("named");
			case OFFICIAL -> namespace.equals("official") || namespace.equals("named");
		};
		if (!ok) {
			throw new TranslationException("Access widener of " + candidate.id() + " uses the '" + namespace
					+ "' namespace, so the mod was built for a different Minecraft version",
					"Install the build of " + candidate.id() + " made for Minecraft " + env.minecraftVersion() + ".");
		}
	}

	/**
	 * A class tweaker's enum extensions only declare the constants the mod's enum mixins add: Mixin adds them, from
	 * 0.17.1 (pre-flight checks the mixins themselves).
	 */
	private void checkEnumExtensions(AccessWidenerFile tweaker, Candidate candidate) throws TranslationException {
		if (!tweaker.enumExtensions().isEmpty() && !MixinCompat.supportsEnumExtensions(env.runtimeMixinVersion())) {
			throw new TranslationException(candidate.id() + " adds enum constants with Mixin's enum extensions, which need Mixin 0.17.1 or later;"
					+ " this NeoForge runs Mixin " + env.runtimeMixinVersion(), "Update NeoForge, or use a build of " + candidate.id() + " made for it.");
		}
	}

	private static Set<String> collectRefmapPaths(FabricModMetadata md, SafeArchive archive) throws ArchiveException {
		Set<String> refmaps = new TreeSet<>();
		for (MixinEntry entry : md.compatHints().mixinsOr(md.mixins())) {
			String json = archive.readText(entry.config());
			if (json == null) continue;
			try {
				MixinConfigFile cfg = MixinConfigFile.parse(json);
				if (cfg.refmap() != null) refmaps.add(cfg.refmap());
			} catch (JsonException ignored) {
				// reported later with a clear message when the config is processed
			}
		}
		return refmaps;
	}

	private static byte[] overlayManifest(String neoId) {
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		manifest.getMainAttributes().putValue("Fabricated-Translated-Mod", neoId);
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			manifest.write(out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static byte[] jarBytes(Map<String, byte[]> files) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		DeterministicJarWriter.write(out, files);
		return out.toByteArray();
	}

	/** Whether the installed classes a cached translation's pre-flight read are as they were (an installed mod may have changed). */
	private boolean installedUnchanged(TranslationCache.Entry entry) {
		if (!(entry.metadata().get("installedClasses") instanceof Map<?, ?> classes)) return true;
		try {
			for (Map.Entry<?, ?> c : classes.entrySet()) {
				if (!env.installed().fingerprint(String.valueOf(c.getKey())).equals(c.getValue())) return false;
			}
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	private TranslatedMod fromCache(Candidate candidate, String neoId, TranslationCache.Entry entry) {
		Map<String, Object> meta = entry.metadata();
		List<String> notes = new ArrayList<>();
		if (meta.get("notes") instanceof List<?> list) list.forEach(o -> notes.add(String.valueOf(o)));
		List<PreflightFinding> findings = new ArrayList<>();
		if (meta.get("findings") instanceof List<?> list) {
			for (Object o : list) {
				if (o instanceof Map<?, ?> m) findings.add(findingFromJson(m));
			}
		}
		List<InjectedInterface> injections = new ArrayList<>();
		if (meta.get("injectedInterfaces") instanceof List<?> list) {
			for (Object o : list) {
				if (o instanceof Map<?, ?> m) injections.add(injectionFromJson(m));
			}
		}
		Set<String> environmentClasses = new TreeSet<>();
		if (meta.get("environmentClasses") instanceof List<?> list) list.forEach(o -> environmentClasses.add(String.valueOf(o)));
		int at = meta.get("accessTransformers") instanceof Number n ? n.intValue() : 0;
		Path jar = env.namespace() == Namespace.INTERMEDIARY ? entry.file(REMAPPED) : candidate.path();
		return new TranslatedMod(candidate, neoId, jar, entry.file(OVERLAY), List.copyOf(findings), at, List.copyOf(notes),
				List.copyOf(injections), Set.copyOf(environmentClasses), true, 0);
	}

	private static Map<String, Object> findingToJson(PreflightFinding f) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("severity", f.severity().name());
		m.put("config", f.config());
		m.put("mixin", f.mixin());
		m.put("message", f.message());
		m.put("suggestion", f.suggestion());
		return m;
	}

	private static PreflightFinding findingFromJson(Map<?, ?> m) {
		Severity severity;
		try {
			severity = Severity.valueOf(String.valueOf(m.get("severity")));
		} catch (IllegalArgumentException e) {
			severity = Severity.INFO;
		}
		return new PreflightFinding(severity, String.valueOf(m.get("config")), m.get("mixin") instanceof String s ? s : null,
				String.valueOf(m.get("message")), String.valueOf(m.get("suggestion")));
	}

	private static Map<String, Object> injectionToJson(InjectedInterface i) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("target", i.target());
		m.put("interface", i.interfaceName());
		m.put("signature", i.genericSignature());
		m.put("transitive", i.transitive());
		m.put("line", i.line());
		return m;
	}

	private static InjectedInterface injectionFromJson(Map<?, ?> m) {
		return new InjectedInterface(String.valueOf(m.get("target")), String.valueOf(m.get("interface")),
				String.valueOf(m.get("signature")), Boolean.TRUE.equals(m.get("transitive")),
				m.get("line") instanceof Number n ? n.intValue() : 0);
	}


	/**
	 * A contact URL NeoForge can take. FML parses {@code displayURL} and {@code issueTrackerURL} with
	 * {@code new URL(...)} when it reads the mod file, and a malformed one makes the whole file invalid (an error that
	 * stops the game). Fabric doesn't validate contact URLs, so "github.com/x/y" without a scheme is common; it is left
	 * out instead.
	 */
	static String url(String value, String what, List<String> notes) {
		if (value == null || value.isBlank()) return null;
		try {
			java.net.URI uri = new java.net.URI(value.strip());
			if (uri.getScheme() == null) throw new java.net.URISyntaxException(value, "no scheme");
			uri.toURL();
			return uri.toString();
		} catch (java.net.URISyntaxException | java.net.MalformedURLException | IllegalArgumentException e) {
			notes.add("Left out the contact " + what + " URL \"" + value + "\": NeoForge only accepts complete URLs");
			return null;
		}
	}

	/**
	 * The mod's page in Sodium's options: an entrypoint on Fabric, a mod property naming the class for Sodium for NeoForge,
	 * which instantiates it itself (so only a plain class, one per mod).
	 */
	static Map<String, String> sodiumProperties(FabricModMetadata md, List<String> notes) {
		List<FabricModMetadata.EntrypointSpec> pages = md.entrypoints().getOrDefault(SODIUM_CONFIG_ENTRYPOINT, List.of());
		if (pages.isEmpty()) return Map.of();
		FabricModMetadata.EntrypointSpec page = pages.getFirst();
		if (!page.adapter().equals("default") || page.value().contains("::")) {
			notes.add("Its page in Sodium's options (" + page.value() + ") is left out: Sodium for NeoForge only takes a class");
			return Map.of();
		}
		if (pages.size() > 1) notes.add("Sodium for NeoForge takes one options page per mod: " + page.value() + " is used, the others are left out");
		return Map.of(SODIUM_CONFIG_ENTRYPOINT, page.value());
	}

	/** The mod's overrides of Sodium's mixin options: a custom value on Fabric, a key of its {@code [[mods]]} entry for Sodium for NeoForge. */
	static Map<String, Boolean> sodiumOptions(FabricModMetadata md, List<String> notes) {
		CustomValue options = md.getCustomValue(SODIUM_OPTIONS);
		if (options == null) return Map.of();
		if (options.getType() != CustomValue.CvType.OBJECT) {
			notes.add("Its " + SODIUM_OPTIONS + " is not an object; left out");
			return Map.of();
		}
		Map<String, Boolean> overrides = new LinkedHashMap<>();
		for (Map.Entry<String, CustomValue> option : options.getAsObject()) {
			if (option.getValue().getType() == CustomValue.CvType.BOOLEAN) {
				overrides.put(option.getKey(), option.getValue().getAsBoolean());
			} else {
				notes.add("Its " + SODIUM_OPTIONS + " entry " + option.getKey() + " is not true or false; left out");
			}
		}
		return overrides;
	}
}
