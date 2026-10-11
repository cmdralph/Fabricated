package io.github.cmdralph.fabricated.core.translate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

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
import io.github.cmdralph.fabricated.core.cache.TranslationCache;
import io.github.cmdralph.fabricated.core.config.FabricatedConfig.ModOverride;
import io.github.cmdralph.fabricated.core.json.JsonException;
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
import io.github.cmdralph.fabricated.core.resolve.CandidateScanner;
import io.github.cmdralph.fabricated.core.translate.TranslationEnvironment.Namespace;
import net.fabricmc.api.EnvType;

/**
 * Translates one Fabric mod into the files NeoForge loads. Deterministic, cached, and bytes-only: no
 * mod code is loaded or run.
 */
public final class ModTranslator {
	public static final String OVERLAY = "overlay.jar";
	public static final String REMAPPED = "remapped.jar";
	public static final String AT_PATH = "META-INF/accesstransformer.cfg";

	private final TranslationEnvironment env;
	private final TranslationCache cache;
	private final io.github.cmdralph.fabricated.core.archive.ArchiveLimits limits;

	public ModTranslator(TranslationEnvironment env, TranslationCache cache, io.github.cmdralph.fabricated.core.archive.ArchiveLimits limits) {
		this.env = env;
		this.cache = cache;
		this.limits = limits;
	}

	/**
	 * @param loadAfter NeoForge ids this mod should load after (its dependencies that are present)
	 */
	public TranslatedMod translate(Candidate candidate, ModOverride override, List<String> loadAfter) throws TranslationException {
		String neoId = ModIds.toNeoForgeId(candidate.id());
		if (!ModIds.isValidNeoForgeId(neoId) || ModIds.RESERVED.contains(neoId)) {
			throw new TranslationException("Mod id '" + candidate.id() + "' cannot be used on NeoForge", "Report this to the mod author.");
		}
		List<String> after = loadAfter.stream().sorted().distinct().toList();
		String key = Hashing.sha256(String.join("|", candidate.sha256(), env.minecraftVersion(), env.neoForgeVersion(),
				env.side(), env.fabricatedVersion(), Integer.toString(TranslationEnvironment.TRANSLATION_SCHEMA),
				env.mappingsHash(), env.runtimeMixinVersion(), override.fingerprint(), String.join(",", after)));
		String entryName = TranslationCache.entryName(neoId, key);

		Optional<TranslationCache.Entry> hit = cache.lookup(entryName);
		if (hit.isPresent()) {
			return fromCache(candidate, neoId, hit.get());
		}

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
			FileView files = name -> view != null ? view.get(name) : archive.read(name);

			Map<String, byte[]> overlay = new TreeMap<>();
			List<PreflightFinding> findings = new ArrayList<>();
			List<ModsTomlWriter.MixinConfigSpec> mixinSpecs = processMixins(candidate, override, files, overlay, findings);

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
					AccessTransformerConverter.Result at = AccessTransformerConverter.convert(parsed, env.game());
					notes.addAll(at.warnings());
					injections = at.injectedInterfaces();
					atLines = at.lineCount();
					if (!at.isEmpty()) overlay.put(AT_PATH, at.accessTransformer().getBytes(StandardCharsets.UTF_8));
				}
			}

			FabricModMetadata md = candidate.metadata();
			ModsTomlWriter.Spec spec = new ModsTomlWriter.Spec(neoId, md.getVersion().getFriendlyString(), md.getName(),
					md.getDescription(), List.copyOf(md.getAuthors()), List.copyOf(md.getContributors()),
					List.copyOf(md.getLicense()), md.getContact().get("homepage").orElse(null),
					md.getContact().get("issues").orElse(null), md.getIconPath(256).orElse(null), mixinSpecs,
					overlay.containsKey(AT_PATH), after);
			overlay.put(CandidateScanner.NEOFORGE_MODS_TOML, ModsTomlWriter.write(spec, "[0,)").getBytes(StandardCharsets.UTF_8));
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
			TranslationCache.Entry entry = cache.store(entryName, outputs, meta);
			Path jar = remapped != null ? entry.file(REMAPPED) : candidate.path();
			return new TranslatedMod(candidate, neoId, jar, entry.file(OVERLAY), List.copyOf(findings), atLines,
					List.copyOf(notes), injections, false, millis);
		} catch (ArchiveException e) {
			throw new TranslationException("The jar is unsafe or corrupt: " + e.getMessage(), "Re-download the mod from its official page.");
		} catch (MappingException e) {
			throw new TranslationException("Remapping failed: " + e.getMessage(), "The mod may use bytecode Fabricated cannot process; report it.", List.of(), e);
		} catch (AccessWidenerException e) {
			throw new TranslationException("Invalid access widener: " + e.getMessage(), "Report this to the mod author.");
		} catch (IOException e) {
			throw new TranslationException("I/O error while translating: " + e.getMessage(), "Check disk space and permissions of the game directory.", List.of(), e);
		}
	}

	/** Reads an entry of the mod being translated (remapped when applicable). */
	@FunctionalInterface
	private interface FileView {
		byte[] read(String name) throws ArchiveException;
	}

	private List<ModsTomlWriter.MixinConfigSpec> processMixins(Candidate candidate, ModOverride override, FileView files,
			Map<String, byte[]> overlay, List<PreflightFinding> findings) throws ArchiveException, TranslationException {
		FabricModMetadata md = candidate.metadata();
		String behavior = MixinCompat.clampForNeoForge(MixinCompat.fabricBehaviorVersion(md.getDependencies()), env.runtimeMixinVersion(),
				env.lowestMixinBehavior());
		EnvType side = env.clientSide() ? EnvType.CLIENT : EnvType.SERVER;
		List<ModsTomlWriter.MixinConfigSpec> specs = new ArrayList<>();
		MixinPreflight preflight = new MixinPreflight(env.game(), classSource(files), env.untransformable(), env.clientSide());

		for (MixinEntry entry : md.mixins()) {
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
				if (config.refmap() != null) {
					byte[] refmapData = files.read(config.refmap());
					if (refmapData != null) refmap = Refmap.parse(new String(refmapData, StandardCharsets.UTF_8));
				}
			} catch (JsonException e) {
				throw new TranslationException("Mixin config " + entry.config() + " is invalid: " + e.getMessage(), "Report this to the mod author.");
			}

			Set<String> skip = skipsFor(entry.config(), config, override.skipMixins());
			if (!override.ignorePreflight()) {
				findings.addAll(preflight.check(entry.config(), config, refmap, skip));
			}
			if (!skip.isEmpty()) {
				overlay.put(entry.config(), config.withoutMixins(skip).getBytes(StandardCharsets.UTF_8));
			}
			specs.add(new ModsTomlWriter.MixinConfigSpec(entry.config(), behavior));
		}

		List<PreflightFinding> failures = findings.stream().filter(f -> f.severity() == Severity.FAIL).toList();
		if (!failures.isEmpty()) {
			String summary = failures.stream().limit(3).map(f -> (f.mixin() != null ? f.mixin() + ": " : "") + f.message())
					.collect(Collectors.joining("; "));
			if (failures.size() > 3) summary += "; and " + (failures.size() - 3) + " more";
			String skipHint = failures.stream().filter(f -> f.mixin() != null)
					.map(f -> f.config() + ":" + relativeMixin(f.config(), f.mixin(), files)).distinct()
					.collect(Collectors.joining("\", \""));
			throw new TranslationException("Mixin pre-flight failed: " + summary,
					"Update the mod or ask its author for a NeoForge build. If the affected mixins are cosmetic, add \"skipMixins\": [\""
							+ skipHint + "\"] for " + candidate.id() + " in config/fabricated.json.",
					failures, null);
		}
		return specs;
	}

	private static String relativeMixin(String configName, String mixinDotted, FileView files) {
		try {
			byte[] data = files.read(configName);
			if (data != null) {
				MixinConfigFile cfg = MixinConfigFile.parse(new String(data, StandardCharsets.UTF_8));
				String pkg = cfg.mixinPackage();
				if (pkg != null && mixinDotted.startsWith(pkg + ".")) return mixinDotted.substring(pkg.length() + 1);
			}
		} catch (ArchiveException | JsonException ignored) {
			// fall back to the full name
		}
		return mixinDotted;
	}

	/** Skips apply as {@code "config.json:Name"} (one config) or {@code "Name"} (every config). */
	static Set<String> skipsFor(String configName, MixinConfigFile config, Set<String> configured) {
		Set<String> declared = new HashSet<>(config.allMixins());
		Set<String> ret = new TreeSet<>();
		for (String s : configured) {
			int colon = s.indexOf(':');
			String cfg = colon >= 0 ? s.substring(0, colon) : null;
			String name = colon >= 0 ? s.substring(colon + 1) : s;
			if (cfg != null && !cfg.equals(configName)) continue;
			if (declared.contains(name)) ret.add(name);
		}
		return ret;
	}

	private static ClassBytesSource classSource(FileView files) {
		return internalName -> {
			try {
				return files.read(internalName + ".class");
			} catch (ArchiveException e) {
				throw new IOException(e.getMessage(), e);
			}
		};
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

	private static Set<String> collectRefmapPaths(FabricModMetadata md, SafeArchive archive) throws ArchiveException {
		Set<String> refmaps = new TreeSet<>();
		for (MixinEntry entry : md.mixins()) {
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
		int at = meta.get("accessTransformers") instanceof Number n ? n.intValue() : 0;
		Path jar = env.namespace() == Namespace.INTERMEDIARY ? entry.file(REMAPPED) : candidate.path();
		return new TranslatedMod(candidate, neoId, jar, entry.file(OVERLAY), List.copyOf(findings), at, List.copyOf(notes),
				List.copyOf(injections), true, 0);
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

}
