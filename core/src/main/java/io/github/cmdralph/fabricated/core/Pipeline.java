package io.github.cmdralph.fabricated.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import io.github.cmdralph.fabricated.core.accesswidener.AccessWidenerFile.InjectedInterface;
import io.github.cmdralph.fabricated.core.archive.ArchiveException;
import io.github.cmdralph.fabricated.core.archive.Hashing;
import io.github.cmdralph.fabricated.core.archive.SafeArchive;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;
import io.github.cmdralph.fabricated.core.cache.TranslationCache;
import io.github.cmdralph.fabricated.core.config.FabricatedConfig;
import io.github.cmdralph.fabricated.core.metadata.FabricModMetadata;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding;
import io.github.cmdralph.fabricated.core.remap.MappingException;
import io.github.cmdralph.fabricated.core.remap.MappingTable;
import io.github.cmdralph.fabricated.core.report.Report;
import io.github.cmdralph.fabricated.core.report.Report.ModRow;
import io.github.cmdralph.fabricated.core.report.Report.Status;
import io.github.cmdralph.fabricated.core.resolve.Candidate;
import io.github.cmdralph.fabricated.core.resolve.CandidateScanner;
import io.github.cmdralph.fabricated.core.resolve.DependencyResolver;
import io.github.cmdralph.fabricated.core.resolve.FabricApiPlan;
import io.github.cmdralph.fabricated.core.runtime.FabricLoaderRuntime;
import io.github.cmdralph.fabricated.core.runtime.MappingResolvers;
import io.github.cmdralph.fabricated.core.runtime.ModContainerImpl;
import io.github.cmdralph.fabricated.core.translate.LinkageAnalysis;
import io.github.cmdralph.fabricated.core.translate.ModIds;
import io.github.cmdralph.fabricated.core.translate.ModTranslator;
import io.github.cmdralph.fabricated.core.translate.PackageGuard;
import io.github.cmdralph.fabricated.core.translate.TranslatedMod;
import io.github.cmdralph.fabricated.core.translate.TranslationEnvironment;
import io.github.cmdralph.fabricated.core.translate.TranslationEnvironment.Namespace;
import io.github.cmdralph.fabricated.core.translate.TranslationException;
import io.github.cmdralph.fabricated.fabricloader.version.VersionParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModOrigin;

/**
 * The complete discovery-time pipeline, independent of NeoForge: scan, filter, resolve, translate,
 * guard packages, analyse linkage, and build the Fabric Loader runtime. The platform glue supplies the
 * inputs, installs {@link Result#runtime()} as the global {@code FabricLoader}, and hands the outputs to
 * NeoForge.
 */
public final class Pipeline {
	/** Fabric Loader version Fabricated reports for {@code fabricloader} dependencies. */
	public static final String FABRIC_LOADER_COMPAT_VERSION = "0.19.5";

	/** A NeoForge mod already found by NeoForge's own discovery. */
	public record NeoForgeMod(String id, String version, String displayName) {
	}

	/** Supplies the intermediary mapping table on demand (only for obfuscated versions). */
	@FunctionalInterface
	public interface MappingSupplier {
		/**
		 * @param mojangMappingsPath the {@code mojangMappingsPath} setting (null to find the file NeoForge's installer left)
		 * @return the table and a hash identifying it
		 */
		Mappings get(TranslationCache cache, String mojangMappingsPath) throws MappingException, IOException;
	}

	/** A mapping table and its identity hash. */
	public record Mappings(MappingTable table, String hash) {
	}

	/**
	 * Inputs from the platform.
	 *
	 * @param platformPackages packages owned by NeoForge, Minecraft, libraries and Fabricated's own jars
	 * @param nativeApiClasses classes of Fabricated's native Fabric API implementation
	 */
	public record Input(
			Path gameDir,
			Path configDir,
			List<Path> fabricJars,
			String minecraftVersion,
			String neoForgeVersion,
			boolean clientSide,
			Namespace namespace,
			String runtimeMixinVersion,
			String lowestMixinBehavior,
			String fabricatedVersion,
			boolean development,
			ClassBytesSource gameClasses,
			ClassBytesSource untransformableClasses,
			Set<String> platformPackages,
			FabricApiPlan fabricApiPlan,
			ClassBytesSource nativeApiClasses,
			Map<String, List<String>> nativeApiInterfaces,
			List<NeoForgeMod> neoForgeMods,
			MappingSupplier mappings,
			String[] launchArguments) {
	}

	/** A translated mod that will be handed to NeoForge. */
	public record LoadedMod(TranslatedMod translated, Set<String> excludedPackages) {
	}

	/** Pipeline output. */
	public record Result(
			FabricatedConfig config,
			List<LoadedMod> mods,
			Map<String, List<InjectedInterface>> injectedInterfaces,
			FabricLoaderRuntime runtime,
			Map<String, ModContainerImpl> containers,
			Report report,
			TranslationCache cache,
			Set<String> dependencyFailures) {
	}

	private Pipeline() {
	}

	public static Result run(Input in) {
		FabricatedConfig config = FabricatedConfig.load(in.configDir());
		Report report = new Report(in.fabricatedVersion());
		report.environment("Minecraft", in.minecraftVersion())
				.environment("NeoForge", in.neoForgeVersion())
				.environment("Side", in.clientSide() ? "client" : "dedicated server")
				.environment("Fabric mod namespace", in.namespace() == Namespace.INTERMEDIARY ? "intermediary (remapped to Mojang names)" : "Mojang names (no remapping)")
				.environment("Mixin", in.runtimeMixinVersion())
				.environment("Java", Runtime.version().toString())
				.environment("Fabric API plan", in.fabricApiPlan().fabricApiVersion())
				.environment("Fabric Loader API", FABRIC_LOADER_COMPAT_VERSION);
		config.warnings().forEach(w -> report.line("Configuration warnings", w));

		EnvType side = in.clientSide() ? EnvType.CLIENT : EnvType.SERVER;
		TranslationCache cache;
		try {
			cache = new TranslationCache(in.gameDir(), config.cacheEnabled(), config.verifyIntegrity());
		} catch (IOException e) {
			for (Path jar : in.fabricJars()) {
				report.mod(new ModRow(jar.getFileName().toString(), "", "", Status.DISABLED,
						"Fabricated could not create its cache directory: " + e.getMessage(),
						"Make sure the game directory is writable and does not contain symbolic links under .fabricated/."));
			}
			return new Result(config, List.of(), Map.of(), newRuntime(in, side, null), Map.of(), report, null, Set.of());
		}

		CandidateScanner.Result scan = new CandidateScanner(cache, config.limits()).scan(in.fabricJars());
		scan.rejected().forEach(r -> report.mod(new ModRow(r.origin(), "", "", Status.REJECTED, r.reason(), "Make sure this is an unmodified Fabric mod jar for this Minecraft version.")));
		scan.notes().forEach(n -> report.line("Discovery notes", n));

		// Filtering: config, environment, Fabric API plan, native NeoForge duplicates.
		Map<String, Pipeline.NeoForgeMod> neoMods = new HashMap<>();
		for (NeoForgeMod m : in.neoForgeMods()) neoMods.put(m.id(), m);
		FabricApiPlan plan = in.fabricApiPlan();
		List<Candidate> eligible = new ArrayList<>();
		// NeoForge refuses two mods with one id, and distinct Fabric ids can share one ("a-b", "a_b").
		Map<String, Candidate> byNeoId = new HashMap<>();
		for (Candidate c : scan.candidates()) {
			FabricatedConfig.ModOverride override = config.override(c.id());
			String version = c.metadata().getVersion().getFriendlyString();
			if (override.disabled()) {
				report.mod(new ModRow(c.id(), version, c.origin(), Status.SKIPPED, "disabled in config/fabricated.json", ""));
				continue;
			}
			if (!c.metadata().getEnvironment().matches(side)) {
				report.mod(new ModRow(c.id(), version, c.origin(), Status.SKIPPED, "only for the " + c.metadata().getEnvironment().name().toLowerCase(java.util.Locale.ROOT) + " side", ""));
				continue;
			}
			FabricApiPlan.Module module = plan.module(c.id());
			if (module != null && module.plan() == FabricApiPlan.Plan.NATIVE) {
				report.mod(new ModRow(c.id(), version, c.origin(), Status.SKIPPED, "replaced by Fabricated's native implementation", ""));
				continue;
			}
			if (module != null && module.plan() == FabricApiPlan.Plan.UNSUPPORTED) {
				report.mod(new ModRow(c.id(), version, c.origin(), Status.SKIPPED, "Fabric API module not supported on NeoForge: " + module.reason(), ""));
				continue;
			}
			String neoId = ModIds.toNeoForgeId(c.id());
			if (neoMods.containsKey(neoId)) {
				report.mod(new ModRow(c.id(), version, c.origin(), Status.SKIPPED,
						"a NeoForge build of this mod is installed (" + neoMods.get(neoId).displayName() + " " + neoMods.get(neoId).version() + "); using it instead",
						"Remove the NeoForge build to use the Fabric one."));
				continue;
			}
			Candidate clash = byNeoId.putIfAbsent(neoId, c);
			if (clash != null) {
				report.mod(new ModRow(c.id(), version, c.origin(), Status.DISABLED,
						"its NeoForge mod id '" + neoId + "' is also the id of Fabric mod " + clash.id() + " (" + clash.origin() + "), and NeoForge cannot load both",
						"Remove one of the two mods."));
				continue;
			}
			eligible.add(c);
		}

		// Built-in providers.
		DependencyResolver resolver = new DependencyResolver();
		resolver.builtin("minecraft", parseVersion(in.minecraftVersion()), "Minecraft");
		resolver.builtin("java", parseVersion(Integer.toString(Runtime.version().feature())), "Java");
		resolver.builtin("fabricloader", parseVersion(FABRIC_LOADER_COMPAT_VERSION), "Fabric Loader (provided by Fabricated)");
		resolver.builtin("fabric-loader", parseVersion(FABRIC_LOADER_COMPAT_VERSION), "Fabric Loader (provided by Fabricated)");
		Map<String, String> hints = new HashMap<>();
		String fapiHint = "Install Fabric API " + plan.fabricApiVersion() + " (the official Fabric jar) in the mods folder; Fabricated loads the parts that work on NeoForge.";
		hints.put("fabric-api", fapiHint);
		hints.put("fabric", fapiHint);
		for (FabricApiPlan.Module m : plan.modules().values()) {
			if (m.plan() == FabricApiPlan.Plan.NATIVE) {
				resolver.builtin(m.id(), parseVersion(m.version()), m.id() + " (native)");
				report.mod(new ModRow(m.id(), m.version(), "Fabricated", Status.PROVIDED_NATIVELY, "", ""));
			} else if (m.plan() == FabricApiPlan.Plan.UNSUPPORTED) {
				hints.put(m.id(), "The Fabric API module " + m.id() + " cannot run on NeoForge (" + m.reason() + "); this mod cannot be used with Fabricated.");
			} else {
				hints.put(m.id(), fapiHint);
			}
		}
		for (NeoForgeMod m : in.neoForgeMods()) {
			resolver.neoForgeMod(m.id(), parseVersion(m.version()), m.displayName());
		}
		Map<String, List<String>> aliases = new HashMap<>();
		config.overrides().forEach((id, o) -> aliases.put(id, o.aliases()));

		// Mappings (obfuscated versions only).
		MappingTable mappingTable = null;
		String mappingsHash = "none";
		boolean mappingsAvailable = true;
		// Without Fabric mods to translate the table isn't needed; skip building it (it takes a few seconds).
		if (in.namespace() == Namespace.INTERMEDIARY && !eligible.isEmpty()) {
			try {
				Mappings m = in.mappings().get(cache, config.mojangMappingsPath());
				mappingTable = m.table();
				mappingsHash = m.hash();
			} catch (MappingException | IOException e) {
				for (Candidate c : eligible) {
					report.mod(new ModRow(c.id(), c.metadata().getVersion().getFriendlyString(), c.origin(), Status.DISABLED,
							"Minecraft's official mapping file is unavailable: " + e.getMessage(),
							"Reinstall NeoForge with its installer (it downloads the mapping file), or set \"mojangMappingsPath\" in config/fabricated.json."));
				}
				eligible.clear();
				mappingsAvailable = false;
			}
		}
		// Nothing to translate without a mapping table on an obfuscated version (no Fabric mods, or the mappings failed).
		boolean canTranslate = in.namespace() != Namespace.INTERMEDIARY ? mappingsAvailable : mappingTable != null;
		TranslationEnvironment env = canTranslate
				? new TranslationEnvironment(in.minecraftVersion(), in.neoForgeVersion(), in.clientSide(), in.namespace(),
						in.runtimeMixinVersion(), in.lowestMixinBehavior(), in.fabricatedVersion(), mappingTable, mappingsHash,
						new ClassIndex(in.gameClasses()), in.untransformableClasses())
				: null;
		if (mappingTable != null) {
			report.environment("Mappings", mappingTable.classCount() + " classes, " + mappingTable.memberCount() + " members");
		}
		FabricLoaderRuntime runtime = newRuntime(in, side, mappingTable);

		// Resolve → translate → guard, repeated until no further mods drop out.
		Map<String, Drop> dropped = new LinkedHashMap<>();
		List<TranslatedMod> translated = List.of();
		Map<String, PackageGuard.Decision> decisions = Map.of();
		Map<String, List<String>> linkage = Map.of();
		DependencyResolver.Result resolution = null;
		Map<String, List<PreflightFinding>> failedFindings = new HashMap<>();
		// Later resolution rounds hit the cache for mods translated earlier in this launch; remember the real work.
		Map<String, Long> translatedThisLaunch = new ConcurrentHashMap<>();
		// Mods the resolver disabled for unmet dependencies (strictDependencies turns these into errors).
		Set<String> dependencyFailures = new java.util.TreeSet<>();
		// Every round either removes at least one mod or finishes, so this bound is never the limiting factor.
		for (int round = 0; round <= eligible.size() + 1 && env != null; round++) {
			List<Candidate> active = eligible.stream().filter(c -> !dropped.containsKey(c.id())).toList();
			Map<String, String> unavailable = new HashMap<>();
			dropped.forEach((id, d) -> unavailable.put(id, d.reason()));
			resolution = resolver.resolve(active, aliases, hints, unavailable);
			for (DependencyResolver.Disabled d : resolution.disabled()) {
				dropped.put(d.candidate().id(), new Drop(d.reason(), d.suggestion(), d.candidate()));
				dependencyFailures.add(d.candidate().id());
			}

			Set<String> activeIds = resolution.ordered().stream().map(Candidate::id).collect(Collectors.toSet());
			ModTranslator translator = new ModTranslator(env, cache, config.limits());
			Map<String, TranslatedMod> results = new ConcurrentHashMap<>();
			Map<String, TranslationException> failures = new ConcurrentHashMap<>();
			resolution.ordered().parallelStream().forEach(c -> {
				try {
					List<String> after = loadAfter(c, activeIds, neoMods);
					results.put(c.id(), translator.translate(c, effectiveOverride(config, plan, c.id()), after));
				} catch (TranslationException e) {
					failures.put(c.id(), e);
				} catch (RuntimeException e) {
					failures.put(c.id(), new TranslationException("Unexpected error: " + e, "Report this to Fabricated with the log file.", List.of(), e));
				}
			});
			results.values().forEach(t -> {
				if (!t.cacheHit()) translatedThisLaunch.put(t.candidate().id(), t.millis());
			});
			boolean changed = !resolution.disabled().isEmpty();
			for (Map.Entry<String, TranslationException> f : failures.entrySet()) {
				Candidate failed = resolution.ordered().stream().filter(c -> c.id().equals(f.getKey())).findFirst().orElse(null);
				dropped.put(f.getKey(), new Drop(f.getValue().getMessage(), f.getValue().suggestion(), failed));
				failedFindings.put(f.getKey(), f.getValue().findings());
				changed = true;
			}
			List<TranslatedMod> ordered = new ArrayList<>();
			for (Candidate c : resolution.ordered()) {
				TranslatedMod t = results.get(c.id());
				if (t != null) ordered.add(t);
			}

			PackageGuard.Result guard = new PackageGuard(config.limits()).check(ordered, in.platformPackages());
			for (PackageGuard.Conflict conflict : guard.conflicts()) {
				dropped.put(conflict.mod().candidate().id(), new Drop(conflict.reason(), conflict.suggestion(), conflict.mod().candidate()));
				changed = true;
			}

			if (!changed) {
				// Linkage analysis on the final set.
				LinkageAnalysis analysis = new LinkageAnalysis(cache, config.limits(), in.clientSide());
				String fingerprint = Hashing.sha256(ordered.stream().map(t -> t.candidate().id() + "@" + t.candidate().sha256())
						.sorted().collect(Collectors.joining(",")) + "|" + in.minecraftVersion() + "|" + in.neoForgeVersion() + "|" + plan.fabricApiVersion()
						+ "|" + in.fabricatedVersion());
				try (JarClassSource jars = new JarClassSource(ordered, config.limits())) {
					linkage = analysis.analyse(ordered, in.gameClasses().or(in.nativeApiClasses()).or(jars), in.nativeApiInterfaces(), fingerprint);
				}
				boolean linkageDropped = false;
				for (TranslatedMod t : ordered) {
					List<String> issues = linkage.get(t.neoForgeId());
					if (issues == null) continue;
					boolean fapiModule = plan.module(t.candidate().id()) != null;
					boolean fabricApiReference = issues.stream().anyMatch(i -> i.contains("net.fabricmc.fabric."));
					if (config.strictLinkage() || fapiModule && fabricApiReference) {
						dropped.put(t.candidate().id(), new Drop("references code that does not exist at runtime: " + issues.getFirst(), fapiModule
								? "This Fabric API module depends on internals that Fabricated implements differently; mods needing it cannot run yet."
								: "Update the mod, or set \"strictLinkage\": false to load it anyway.", t.candidate()));
						linkageDropped = true;
					}
				}
				translated = ordered;
				decisions = guard.decisions();
				if (!linkageDropped) break;
			}
		}

		// Report disabled mods.
		for (Map.Entry<String, Drop> d : dropped.entrySet()) {
			Candidate c = d.getValue().candidate();
			report.mod(new ModRow(d.getKey(), c != null ? c.metadata().getVersion().getFriendlyString() : "", c != null ? c.origin() : "",
					Status.DISABLED, d.getValue().reason(), d.getValue().suggestion()));
			for (PreflightFinding f : failedFindings.getOrDefault(d.getKey(), List.of())) {
				report.line("Mixin pre-flight failures", d.getKey() + ": " + f);
			}
		}
		if (resolution != null) {
			resolution.warnings().forEach(w -> report.line("Dependency warnings", w));
			resolution.crossLoaderLinks().forEach((id, targets) -> report.line("Cross-loader dependencies",
					id + " uses NeoForge build(s) of " + targets + "; this usually works for multi-loader libraries but is not guaranteed"));
		}

		// Assemble outputs and the Fabric Loader runtime.
		List<LoadedMod> loaded = new ArrayList<>();
		Map<String, List<InjectedInterface>> injections = new TreeMap<>();
		Map<String, ModContainerImpl> containers = new LinkedHashMap<>();
		Set<String> finalIds = new HashSet<>();
		for (TranslatedMod t : translated) {
			if (dropped.containsKey(t.candidate().id())) continue;
			finalIds.add(t.candidate().id());
		}
		for (TranslatedMod t : translated) {
			Candidate c = t.candidate();
			if (!finalIds.contains(c.id())) continue;
			PackageGuard.Decision decision = decisions.get(t.neoForgeId());
			Set<String> excluded = decision != null ? decision.excludedPackages() : Set.of();
			loaded.add(new LoadedMod(t, excluded));
			for (InjectedInterface ii : t.injectedInterfaces()) injections.computeIfAbsent(ii.target(), k -> new ArrayList<>()).add(ii);

			Long millis = translatedThisLaunch.get(c.id());
			String detail = (millis == null ? "cached" : "translated in " + millis + " ms")
					+ (t.accessTransformers() > 0 ? ", " + t.accessTransformers() + " access transformer(s)" : "")
					+ (t.injectedInterfaces().isEmpty() ? "" : ", " + t.injectedInterfaces().size() + " injected interface(s)");
			report.mod(new ModRow(c.id(), c.metadata().getVersion().getFriendlyString(), c.origin(), Status.LOADED, detail, ""));
			for (PreflightFinding f : t.findings()) report.line("Mixin pre-flight warnings", c.id() + ": " + f
					+ (f.suggestion().isEmpty() ? "" : " - " + f.suggestion()));
			for (String n : t.notes()) report.line("Translation notes", c.id() + ": " + n);
			FabricApiPlan.Module planned = plan.module(c.id());
			if (planned != null && !planned.skipMixins().isEmpty()) {
				report.line("Fabric API mixins skipped (conflict with NeoForge)", c.id() + ": " + planned.skipMixins()
						+ (planned.reason().isEmpty() ? "" : " - " + planned.reason()));
			}
			if (decision != null) decision.notes().forEach(n -> report.line("Package guard", c.id() + " " + n));
			List<String> issues = linkage.get(t.neoForgeId());
			if (issues != null) issues.forEach(i -> report.line("Linkage warnings (code that will fail if it runs)", c.id() + ": " + i));

			ModOrigin origin = c.parentId() == null
					? new ModContainerImpl.Origin(ModOrigin.Kind.PATH, List.of(c.path()), null, null)
					: new ModContainerImpl.Origin(ModOrigin.Kind.NESTED, List.of(), c.parentId(), c.origin().substring(c.origin().lastIndexOf("!/") + 2));
			ModContainerImpl container = new ModContainerImpl(c.metadata(), List.of(t.jar()), origin,
					finalIds.contains(c.parentId()) ? c.parentId() : null,
					c.nestedChildren().stream().filter(finalIds::contains).toList(), runtime);
			List<String> extraIds = new ArrayList<>(c.metadata().getProvides());
			extraIds.addAll(config.override(c.id()).aliases());
			runtime.addMod(container, extraIds);
			runtime.addEntrypoints(container, c.metadata().entrypoints(), config.override(c.id()).skipEntrypoints());
			runtime.addLanguageAdapters(container, c.metadata().languageAdapters());
			containers.put(c.id(), container);
		}

		// Built-ins and NeoForge mods, visible through Fabric's API.
		addBuiltin(runtime, "minecraft", in.minecraftVersion(), "Minecraft");
		addBuiltin(runtime, "java", Integer.toString(Runtime.version().feature()), "Java " + Runtime.version().feature());
		addBuiltin(runtime, "fabricloader", FABRIC_LOADER_COMPAT_VERSION, "Fabric Loader (Fabricated)");
		for (FabricApiPlan.Module m : plan.modules().values()) {
			if (m.plan() == FabricApiPlan.Plan.NATIVE) addBuiltin(runtime, m.id(), m.version(), m.id() + " (Fabricated)");
		}
		for (NeoForgeMod m : in.neoForgeMods()) addBuiltin(runtime, m.id(), m.version(), m.displayName());

		cache.notes().forEach(n -> report.line("Cache", n));
		return new Result(config, List.copyOf(loaded), injections, runtime, containers, report, cache,
				Set.copyOf(dependencyFailures));
	}

	/** Why a mod was disabled; {@code candidate} is null when it is unknown. */
	private record Drop(String reason, String suggestion, Candidate candidate) {
	}

	private static FabricLoaderRuntime newRuntime(Input in, EnvType side, MappingTable mappings) {
		return new FabricLoaderRuntime(side, in.gameDir(), in.configDir(), in.minecraftVersion(), in.development(),
				mappings != null ? MappingResolvers.intermediary(mappings) : MappingResolvers.identity(), () -> in.launchArguments());
	}

	/** User overrides, plus the plan's known-conflicting mixins for pass-through Fabric API modules. */
	static FabricatedConfig.ModOverride effectiveOverride(FabricatedConfig config, FabricApiPlan plan, String id) {
		FabricatedConfig.ModOverride user = config.override(id);
		FabricApiPlan.Module module = plan.module(id);
		if (module == null || module.skipMixins().isEmpty()) return user;
		Set<String> skip = new java.util.TreeSet<>(user.skipMixins());
		skip.addAll(module.skipMixins());
		return new FabricatedConfig.ModOverride(user.disabled(), java.util.Collections.unmodifiableSet(skip), user.skipEntrypoints(),
				user.aliases(), user.ignorePreflight());
	}

	private static List<String> loadAfter(Candidate c, Set<String> activeIds, Map<String, NeoForgeMod> neoMods) {
		List<String> after = new ArrayList<>();
		for (ModDependency dep : c.metadata().getDependencies()) {
			if (dep.getKind() != ModDependency.Kind.DEPENDS && dep.getKind() != ModDependency.Kind.RECOMMENDS) continue;
			String id = dep.getModId();
			if (activeIds.contains(id) && !id.equals(c.id())) after.add(ModIds.toNeoForgeId(id));
			else if (neoMods.containsKey(id)) after.add(id);
			else if (neoMods.containsKey(ModIds.toNeoForgeId(id))) after.add(ModIds.toNeoForgeId(id));
		}
		return after;
	}

	private static void addBuiltin(FabricLoaderRuntime runtime, String id, String version, String name) {
		FabricModMetadata md = FabricModMetadata.builtin(id, parseVersion(version), name, "");
		runtime.addMod(new ModContainerImpl(md, List.of(), new ModContainerImpl.Origin(ModOrigin.Kind.UNKNOWN, List.of(), null, null),
				null, List.of(), runtime), List.of());
	}

	private static Version parseVersion(String version) {
		try {
			return VersionParser.parse(version == null || version.isEmpty() ? "0" : version, false);
		} catch (VersionParsingException e) {
			throw new IllegalArgumentException("Unparseable version " + version, e);
		}
	}

	/**
	 * Class source over the translated jars. The class index is built on first use only, so a warm launch
	 * (linkage results cached) does not open any jar; opened jars are closed by {@link #close()}.
	 */
	static final class JarClassSource implements ClassBytesSource, AutoCloseable {
		private final List<TranslatedMod> mods;
		private final io.github.cmdralph.fabricated.core.archive.ArchiveLimits limits;
		private final Map<Path, SafeArchive> open = new ConcurrentHashMap<>();
		private Map<String, Path> owners;

		JarClassSource(List<TranslatedMod> mods, io.github.cmdralph.fabricated.core.archive.ArchiveLimits limits) {
			this.mods = mods;
			this.limits = limits;
		}

		private synchronized Map<String, Path> owners() {
			if (owners == null) {
				owners = new HashMap<>();
				for (TranslatedMod t : mods) {
					try (SafeArchive archive = SafeArchive.open(t.jar(), limits.forTranslatedJars())) {
						for (String name : archive.classEntryNames()) owners.putIfAbsent(name, t.jar());
					} catch (Exception e) {
						// Mods that cannot be opened were already reported during translation.
					}
				}
			}
			return owners;
		}

		@Override
		public byte[] read(String internalName) throws IOException {
			Path jar = owners().get(internalName + ".class");
			if (jar == null) return null;
			try {
				SafeArchive archive = open.computeIfAbsent(jar, j -> {
					try {
						return SafeArchive.open(j, limits.forTranslatedJars());
					} catch (ArchiveException e) {
						throw new UncheckedIOException(readFailure(internalName, j, e));
					}
				});
				return archive.read(internalName + ".class");
			} catch (UncheckedIOException e) {
				throw e.getCause();
			} catch (ArchiveException e) {
				throw readFailure(internalName, jar, e);
			}
		}

		private static IOException readFailure(String internalName, Path jar, ArchiveException e) {
			return new IOException("Could not read " + internalName + " from " + jar + ": " + e.getMessage(), e);
		}

		@Override
		public void close() {
			for (SafeArchive a : open.values()) {
				try {
					a.close();
				} catch (IOException ignored) {
					// closing a read-only archive cannot lose data
				}
			}
			open.clear();
		}
	}
}
