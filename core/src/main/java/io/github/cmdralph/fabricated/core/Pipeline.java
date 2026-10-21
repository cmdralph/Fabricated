package io.github.cmdralph.fabricated.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.github.cmdralph.fabricated.core.accesswidener.AccessWidenerFile.InjectedInterface;
import io.github.cmdralph.fabricated.core.archive.ArchiveException;
import io.github.cmdralph.fabricated.core.archive.Hashing;
import io.github.cmdralph.fabricated.core.archive.SafeArchive;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;
import io.github.cmdralph.fabricated.core.bytecode.InstalledClasses;
import io.github.cmdralph.fabricated.core.cache.TranslationCache;
import io.github.cmdralph.fabricated.core.config.FabricatedConfig;
import io.github.cmdralph.fabricated.core.metadata.CompatHints;
import io.github.cmdralph.fabricated.core.metadata.CustomValueImpl;
import io.github.cmdralph.fabricated.core.metadata.FabricModMetadata;
import io.github.cmdralph.fabricated.core.metadata.FabricModMetadata.EntrypointSpec;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding.Severity;
import io.github.cmdralph.fabricated.core.remap.JarRemapper;
import io.github.cmdralph.fabricated.core.remap.MappingException;
import io.github.cmdralph.fabricated.core.remap.MappingTable;
import io.github.cmdralph.fabricated.core.report.Report;
import io.github.cmdralph.fabricated.core.report.Report.ModRow;
import io.github.cmdralph.fabricated.core.report.Report.Status;
import io.github.cmdralph.fabricated.core.resolve.Candidate;
import io.github.cmdralph.fabricated.core.resolve.CandidateScanner;
import io.github.cmdralph.fabricated.core.resolve.CompatPlan;
import io.github.cmdralph.fabricated.core.resolve.DependencyResolver;
import io.github.cmdralph.fabricated.core.resolve.FabricApiPlan;
import io.github.cmdralph.fabricated.core.runtime.FabricLoaderRuntime;
import io.github.cmdralph.fabricated.core.runtime.MappingResolvers;
import io.github.cmdralph.fabricated.core.runtime.ModContainerImpl;
import io.github.cmdralph.fabricated.core.translate.DefaultBridge;
import io.github.cmdralph.fabricated.core.translate.JarClassSource;
import io.github.cmdralph.fabricated.core.translate.LinkageAnalysis;
import io.github.cmdralph.fabricated.core.metadata.ModIds;
import io.github.cmdralph.fabricated.core.translate.ModTranslator;
import io.github.cmdralph.fabricated.core.translate.PackageGuard;
import io.github.cmdralph.fabricated.core.translate.Interop;
import io.github.cmdralph.fabricated.core.translate.TranslatedMod;
import io.github.cmdralph.fabricated.core.translate.TranslationEnvironment;
import io.github.cmdralph.fabricated.core.translate.TranslationEnvironment.Namespace;
import io.github.cmdralph.fabricated.core.translate.TranslationException;
import io.github.cmdralph.fabricated.fabricloader.version.VersionParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.CustomValue;
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

	/**
	 * A NeoForge mod NeoForge's own discovery found, or one nested in a mod file it found.
	 *
	 * @param properties its mod properties ({@code [modproperties.<id>]} in its mods.toml) as plain values (strings,
	 *                   numbers, booleans, lists and maps): Fabric code sees them as the mod's custom values, which is how
	 *                   NeoForge mods speak to Fabric code (Sodium's {@code fabric-renderer-api-v1:contains_renderer}
	 *                   makes Fabric API's default renderer stand down)
	 * @param files      its jar (or directory), the root of its container's files to Fabric code (multi-loader mods find
	 *                   their own files through Fabric's API when it is present); for a mod nested in another file, that
	 *                   file
	 */
	public record NeoForgeMod(String id, String version, String displayName, Map<String, Object> properties, List<Path> files) {
		/** The mod property listing the Fabric mod ids a NeoForge mod provides (Sodium provides Indium). */
		static final String PROVIDES = "fabric:provides";

		public NeoForgeMod {
			properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
			files = List.copyOf(files);
		}

		/** The Fabric mod ids it provides. */
		public List<String> provides() {
			if (!(properties.get(PROVIDES) instanceof List<?> ids)) return List.of();
			return ids.stream().filter(String.class::isInstance).map(String.class::cast).toList();
		}
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
	 * @param cache               the launch's cache, created from {@code config}
	 * @param mixinExtrasVersion  the MixinExtras version NeoForge runs, provided to Fabric mods as {@code mixinextras}
	 *                            (as Fabric Loader does), or null when unknown
	 * @param gameFiles           Minecraft's files as NeoForge loads them: the root of the {@code minecraft} mod
	 * @param fabricatedFiles     Fabricated's in-game mod: the root of the mods it provides (Fabric Loader, the native
	 *                            Fabric API modules)
	 * @param platformPackages    packages owned by NeoForge, Minecraft, libraries and Fabricated's own jars
	 * @param nativeApiClasses    classes of Fabricated's native Fabric API implementation
	 * @param nativeApiInterfaces interfaces Fabricated's own mixins add to game classes, read only when needed
	 * @param installedClasses    the classes of Fabricated's Fabric API and of the NeoForge mods, which Fabric mixins may target
	 */
	public record Input(
			Path gameDir,
			Path configDir,
			FabricatedConfig config,
			TranslationCache cache,
			List<Path> fabricJars,
			String minecraftVersion,
			String neoForgeVersion,
			boolean clientSide,
			Namespace namespace,
			String runtimeMixinVersion,
			String mixinExtrasVersion,
			String lowestMixinBehavior,
			String fabricatedVersion,
			boolean development,
			ClassBytesSource gameClasses,
			List<Path> gameFiles,
			List<Path> fabricatedFiles,
			ClassBytesSource untransformableClasses,
			Set<String> platformPackages,
			FabricApiPlan fabricApiPlan,
			ClassBytesSource nativeApiClasses,
			Supplier<Map<String, List<String>>> nativeApiInterfaces,
			List<NeoForgeMod> neoForgeMods,
			InstalledClasses installedClasses,
			MappingSupplier mappings,
			String[] launchArguments) {
	}

	/**
	 * A translated mod that will be handed to NeoForge.
	 *
	 * @param packages          what its module leaves out of its jar and serves from other mods' jars ({@link PackageGuard})
	 * @param pluginAnnotations what to add to its scan data for the NeoForge builds of other mods to find its plugins
	 */
	public record LoadedMod(TranslatedMod translated, PackageGuard.Decision packages, List<Interop.Annotation> pluginAnnotations) {
	}

	/**
	 * Pipeline output.
	 *
	 * @param injectedInterfaces the interface injections of {@code mods}, by target class (immutable: class loading reads
	 *                           it from any thread)
	 * @param defaultBridges     the default method bridges of {@code mods}, by target class (immutable)
	 * @param environmentClasses the classes of {@code mods} with {@code @Environment} annotations (immutable)
	 */
	public record Result(
			FabricatedConfig config,
			List<LoadedMod> mods,
			Map<String, List<InjectedInterface>> injectedInterfaces,
			Map<String, List<DefaultBridge>> defaultBridges,
			Set<String> environmentClasses,
			FabricLoaderRuntime runtime,
			Map<String, ModContainerImpl> containers,
			Report report,
			Set<String> dependencyFailures) {

		Result(FabricatedConfig config, List<LoadedMod> mods, FabricLoaderRuntime runtime, Map<String, ModContainerImpl> containers,
				Report report, Set<String> dependencyFailures) {
			this(config, List.copyOf(mods), injectedInterfacesOf(mods), defaultBridgesOf(mods), environmentClassesOf(mods), runtime, containers,
					report, dependencyFailures);
		}

		/**
		 * This result without the given mods (by Fabric id), which NeoForge refused to load: their classes will never
		 * exist, so their interface injections and environment classes must not apply to anything.
		 */
		public Result without(Set<String> fabricIds) {
			List<LoadedMod> kept = mods.stream().filter(m -> !fabricIds.contains(m.translated().candidate().id())).toList();
			return new Result(config, kept, runtime, containers, report, dependencyFailures);
		}

		private static Map<String, List<InjectedInterface>> injectedInterfacesOf(List<LoadedMod> mods) {
			Map<String, List<InjectedInterface>> injections = new HashMap<>();
			for (LoadedMod mod : mods) {
				for (InjectedInterface ii : mod.translated().injectedInterfaces()) {
					injections.computeIfAbsent(ii.target(), k -> new ArrayList<>()).add(ii);
				}
			}
			injections.replaceAll((target, list) -> List.copyOf(list));
			return Map.copyOf(injections);
		}

		private static Map<String, List<DefaultBridge>> defaultBridgesOf(List<LoadedMod> mods) {
			Map<String, List<DefaultBridge>> bridges = new HashMap<>();
			for (LoadedMod mod : mods) {
				for (DefaultBridge bridge : mod.translated().defaultBridges()) {
					List<DefaultBridge> list = bridges.computeIfAbsent(bridge.target(), k -> new ArrayList<>());
					if (!list.contains(bridge)) list.add(bridge);
				}
			}
			bridges.replaceAll((target, list) -> List.copyOf(list));
			return Map.copyOf(bridges);
		}

		private static Set<String> environmentClassesOf(List<LoadedMod> mods) {
			Set<String> classes = new HashSet<>();
			for (LoadedMod mod : mods) classes.addAll(mod.translated().environmentClasses());
			return Set.copyOf(classes);
		}
	}

	/**
	 * Fabric mods whose API Fabricated provides itself, by id: their jar isn't loaded, and they are provided at its
	 * version, so the mods requiring them resolve as they would on Fabric.
	 */
	private static final Map<String, String> REPLACED = Map.of(
			"forgeconfigapiport", "Fabricated provides Forge Config API Port's Fabric API on NeoForge's own config system, which the port copies",
			"modmenu", "Fabricated shows the config screens Fabric mods offer through Mod Menu's API in NeoForge's mod list");

	/** What is known about other people's Fabric mods (a mod's NeoForge build, when installed, is used before this is looked at). */
	private static final CompatPlan COMPAT = CompatPlan.builtin();

	/** A mod the platform provides (Minecraft, Java, Fabric Loader, Fabricated, native Fabric API modules). */
	private record Builtin(String id, String version, String name, List<String> aliases) {
	}

	/** Why a mod was disabled. */
	private record Drop(String reason, String suggestion, Candidate candidate) {
	}

	private final Input in;
	private final FabricatedConfig config;
	private final FabricApiPlan plan;
	private final Report report;
	private final EnvType side;
	/** NeoForge mods, one per id (the newest). */
	private final List<NeoForgeMod> neoForgeMods;
	/** NeoForge mods by id, and by the NeoForge form of each id they provide. */
	private final Map<String, NeoForgeMod> neoMods = new LinkedHashMap<>();
	private final List<Builtin> builtins;
	/** Compat patches already reported as ignored ("mod target"): patches are worked out again every round. */
	private final Set<String> ignoredPatches = new HashSet<>();

	private Pipeline(Input in) {
		this.in = in;
		this.config = in.config();
		this.plan = in.fabricApiPlan();
		this.report = new Report(in.fabricatedVersion());
		this.side = in.clientSide() ? EnvType.CLIENT : EnvType.SERVER;
		// A mod file may nest a copy of a mod found elsewhere: NeoForge loads the newest.
		Map<String, NeoForgeMod> byId = new LinkedHashMap<>();
		for (NeoForgeMod m : in.neoForgeMods()) byId.merge(m.id(), m, (a, b) -> parseVersion(b.version()).compareTo(parseVersion(a.version())) > 0 ? b : a);
		this.neoForgeMods = List.copyOf(byId.values());
		neoMods.putAll(byId);
		for (NeoForgeMod m : neoForgeMods) {
			for (String alias : m.provides()) neoMods.putIfAbsent(ModIds.toNeoForgeId(alias), m);
		}
		this.builtins = new ArrayList<>(builtins(in));
	}

	public static Result run(Input in) {
		return new Pipeline(in).run();
	}

	private static List<Builtin> builtins(Input in) {
		List<Builtin> list = new ArrayList<>();
		list.add(new Builtin("minecraft", in.minecraftVersion(), "Minecraft", List.of()));
		list.add(new Builtin("java", Integer.toString(Runtime.version().feature()), "Java " + Runtime.version().feature(), List.of()));
		list.add(new Builtin("fabricloader", FABRIC_LOADER_COMPAT_VERSION, "Fabric Loader (provided by Fabricated)", List.of("fabric-loader")));
		list.add(new Builtin(ModIds.FABRICATED, in.fabricatedVersion(), "Fabricated", List.of()));
		if (in.mixinExtrasVersion() != null) {
			list.add(new Builtin("mixinextras", in.mixinExtrasVersion(), "MixinExtras (provided by NeoForge)", List.of("mixin_extras")));
		}
		for (FabricApiPlan.Module m : in.fabricApiPlan().modules().values()) {
			if (m.plan() == FabricApiPlan.Plan.NATIVE) list.add(new Builtin(m.id(), m.version(), m.id() + " (provided by Fabricated)", List.of()));
		}
		return List.copyOf(list);
	}

	private Result run() {
		report.environment("Minecraft", in.minecraftVersion())
				.environment("NeoForge", in.neoForgeVersion())
				.environment("Side", in.clientSide() ? "client" : "dedicated server")
				.environment("Fabric mod namespace", in.namespace() == Namespace.INTERMEDIARY ? "intermediary (remapped to Mojang names)" : "Mojang names (no remapping)")
				.environment("Mixin", in.runtimeMixinVersion())
				.environment("MixinExtras", in.mixinExtrasVersion() != null ? in.mixinExtrasVersion() : "not found")
				.environment("Java", Runtime.version().toString())
				.environment("Fabric API plan", plan.fabricApiVersion())
				.environment("Fabric Loader API", FABRIC_LOADER_COMPAT_VERSION);
		config.warnings().forEach(w -> report.line("Configuration warnings", w));

		CandidateScanner.Result scan = new CandidateScanner(in.cache(), config.limits()).scan(in.fabricJars());
		scan.rejected().forEach(r -> report.mod(new ModRow(r.origin(), "", "", Status.REJECTED, r.reason(), "Make sure this is an unmodified Fabric mod jar for this Minecraft version.")));
		scan.notes().forEach(n -> report.line("Discovery notes", n));
		List<Candidate> eligible = filter(scan.candidates());
		for (FabricApiPlan.Module m : plan.modules().values()) {
			if (m.plan() == FabricApiPlan.Plan.NATIVE) report.mod(new ModRow(m.id(), m.version(), "Fabricated", Status.PROVIDED_NATIVELY, "", ""));
		}

		MappingTable mappingTable = null;
		String mappingsHash = "none";
		// Without Fabric mods to translate the table isn't needed; skip building it (it takes a few seconds).
		if (in.namespace() == Namespace.INTERMEDIARY && !eligible.isEmpty()) {
			try {
				Mappings m = in.mappings().get(in.cache(), config.mojangMappingsPath());
				mappingTable = m.table();
				mappingsHash = m.hash();
				report.environment("Mappings", mappingTable.classCount() + " classes, " + mappingTable.memberCount() + " members");
			} catch (MappingException | IOException e) {
				for (Candidate c : eligible) {
					report.mod(new ModRow(c.id(), c.version(), c.origin(), Status.DISABLED, "Minecraft's official mapping file is unavailable: " + e.getMessage(),
							"Reinstall NeoForge with its installer (it downloads the mapping file), or set \"mojangMappingsPath\" in config/fabricated.json."));
				}
				eligible = List.of();
			}
		}
		FabricLoaderRuntime runtime = new FabricLoaderRuntime(side, in.gameDir(), in.configDir(), in.minecraftVersion(), in.development(),
				mappingTable != null ? MappingResolvers.intermediary(mappingTable) : MappingResolvers.identity(), in::launchArguments);
		if (eligible.isEmpty()) return assemble(runtime, new Rounds());

		ClassIndex game = new ClassIndex(in.gameClasses());
		// Mixins into the Fabric mods' classes are checked too, against those classes as they will load: on 1.21.11
		// remapped from intermediary names, as translating each mod remaps it.
		List<SafeArchive> opened = new ArrayList<>();
		InstalledClasses installed = in.installedClasses().with(fabricJars(eligible, opened, mappingTable == null ? null : new JarRemapper(mappingTable)));
		TranslationEnvironment env = new TranslationEnvironment(in.minecraftVersion(), in.neoForgeVersion(), in.clientSide(), in.namespace(),
				in.runtimeMixinVersion(), in.lowestMixinBehavior(), in.fabricatedVersion(), mappingTable, mappingsHash, game, in.untransformableClasses(),
				installed, installed.isEmpty() ? game : new ClassIndex(in.gameClasses().or(installed)), unsupportedPackages(scan.candidates()));
		Rounds rounds = new Rounds();
		try {
			rounds.run(scan.candidates(), eligible, resolver(), new ModTranslator(env, in.cache(), config.limits()));
		} finally {
			synchronized (opened) {
				for (SafeArchive archive : opened) {
					try {
						archive.close();
					} catch (IOException e) {
						// Read only; nothing to lose.
					}
				}
			}
		}
		return assemble(runtime, rounds);
	}

	/**
	 * The classes of the Fabric mods about to load, for pre-flight. A jar is opened only when a lookup names a class in
	 * one of its packages, then kept open until translation ends ({@code opened}).
	 *
	 * @param remapper remaps each class read to runtime names (1.21.11), or null where the jars have them (26.x)
	 */
	private List<InstalledClasses.Jar> fabricJars(List<Candidate> candidates, List<SafeArchive> opened, JarRemapper remapper) {
		List<InstalledClasses.Jar> jars = new ArrayList<>();
		for (Candidate c : candidates) {
			Supplier<SafeArchive> archive = new Supplier<>() {
				private boolean tried;
				private SafeArchive open;

				@Override
				public synchronized SafeArchive get() {
					if (!tried) {
						tried = true;
						try {
							open = SafeArchive.open(c.path(), config.limits());
							synchronized (opened) {
								opened.add(open);
							}
						} catch (ArchiveException e) {
							open = null; // translating it reports the problem
						}
					}
					return open;
				}
			};
			jars.add(new InstalledClasses.Jar(c.metadata().getName(), name -> {
				int slash = name.lastIndexOf('/');
				if (!c.packages().contains(slash < 0 ? "" : name.substring(0, slash))) return false;
				SafeArchive a = archive.get();
				return a != null && a.contains(name + ".class");
			}, name -> {
				SafeArchive a = archive.get();
				byte[] data = a == null ? null : a.read(name + ".class");
				if (data == null || remapper == null) return data;
				try {
					return remapper.remapClass(data, name);
				} catch (MappingException e) {
					return null; // not verified then; translating the mod reports it
				}
			}));
		}
		return jars;
	}

	/**
	 * The packages of the unsupported Fabric API modules installed (the renderer API on 1.21.11) that no other jar has,
	 * with their module: a mixin using a class there fails as Mixin applies it.
	 */
	private Map<String, String> unsupportedPackages(List<Candidate> candidates) {
		Map<String, String> unsupported = new HashMap<>();
		Set<String> elsewhere = new HashSet<>();
		for (Candidate c : candidates) {
			FabricApiPlan.Module module = plan.module(c.id());
			if (module != null && module.plan() == FabricApiPlan.Plan.UNSUPPORTED) c.packages().forEach(p -> unsupported.putIfAbsent(p, c.id()));
			else elsewhere.addAll(c.packages());
		}
		unsupported.keySet().removeAll(elsewhere);
		return unsupported;
	}

	/** Candidates that should load, reporting the others. */
	private List<Candidate> filter(List<Candidate> candidates) {
		Map<String, String> builtinNames = new HashMap<>();
		for (Builtin b : builtins) builtinNames.put(b.id(), b.name());
		List<Candidate> eligible = new ArrayList<>();
		// NeoForge refuses two mods with one id, and distinct Fabric ids can share one ("a-b", "a_b").
		Map<String, Candidate> byNeoId = new HashMap<>();
		for (Candidate c : candidates) {
			String skipped = skipReason(c, builtinNames);
			if (skipped != null) {
				report.mod(new ModRow(c.id(), c.version(), c.origin(), Status.SKIPPED, skipped, ""));
				continue;
			}
			String replacement = REPLACED.get(c.id());
			if (replacement != null) {
				builtins.add(new Builtin(c.id(), c.version(), replacement, List.of()));
				report.mod(new ModRow(c.id(), c.version(), c.origin(), Status.SKIPPED, replacement, ""));
				continue;
			}
			String neoId = ModIds.toNeoForgeId(c.id());
			NeoForgeMod neo = neoMods.get(neoId);
			if (neo != null && neo.id().equals(neoId)) {
				report.mod(new ModRow(c.id(), c.version(), c.origin(), Status.SKIPPED,
						"a NeoForge build of this mod is installed (" + neo.displayName() + " " + neo.version() + "); using it instead",
						"Remove the NeoForge build to use the Fabric one."));
				continue;
			}
			if (neo != null) {
				report.mod(new ModRow(c.id(), c.version(), c.origin(), Status.SKIPPED,
						"NeoForge mod " + neo.displayName() + " " + neo.version() + " provides it", ""));
				continue;
			}
			CompatPlan.Entry known = COMPAT.entry(c.id());
			String neoForgeBuildOnly = known.neoForgeBuildOnly();
			if (neoForgeBuildOnly != null && !config.override(c.id()).loadUnsupported()) {
				report.mod(new ModRow(c.id(), c.version(), c.origin(), Status.DISABLED, "its Fabric build can't run on NeoForge: " + neoForgeBuildOnly,
						known.suggestion() != null ? known.suggestion() : "Install " + c.metadata().getName() + " for NeoForge instead (from the same download page)."));
				continue;
			}
			// Even on request: the pair is broken whatever the player accepts.
			String pairing = notWithNeoForge(known);
			if (pairing != null) {
				report.mod(new ModRow(c.id(), c.version(), c.origin(), Status.DISABLED, "its Fabric build can't run next to " + pairing,
						"Use the NeoForge builds of both, or the Fabric builds of both."));
				continue;
			}
			if (neoForgeBuildOnly != null) report.line("Unsupported mods loaded on request (loadUnsupported)", c.id() + ": " + neoForgeBuildOnly);
			FabricatedConfig.ModOverride override = config.override(c.id());
			if (override.ignoreHints() && !c.metadata().compatHints().isEmpty()) {
				c = c.withMetadata(c.metadata().withCompatHints(CompatHints.NONE));
			}
			FabricApiPlan.Module module = plan.module(c.id());
			if (module != null && !module.dropDependencies().isEmpty()) {
				c = c.withMetadata(c.metadata().withoutDependencies(module.dropDependencies()));
				report.line("Fabric API modules loaded without a dependency",
						c.id() + " without " + String.join(", ", module.dropDependencies()) + ": " + module.reason());
			}
			CompatHints hints = c.metadata().compatHints();
			for (String w : hints.warnings()) report.line("Compatibility hint warnings", c.id() + ": " + w);
			if (!hints.isEmpty()) report.line("Compatibility hints (from the mod's fabric.mod.json)", c.id() + " " + hints.describe());
			Candidate clash = byNeoId.putIfAbsent(neoId, c);
			if (clash != null) {
				report.mod(new ModRow(c.id(), c.version(), c.origin(), Status.DISABLED,
						"its NeoForge mod id '" + neoId + "' is also the id of Fabric mod " + clash.id() + " (" + clash.origin() + "), and NeoForge cannot load both",
						"Remove one of the two mods."));
				continue;
			}
			eligible.add(c);
		}
		return eligible;
	}

	/** The installed NeoForge mod next to which a Fabric build can't run, with why, or {@code null}. */
	private String notWithNeoForge(CompatPlan.Entry known) {
		for (Map.Entry<String, String> pair : known.notWithNeoForge().entrySet()) {
			NeoForgeMod neo = neoMods.get(ModIds.toNeoForgeId(pair.getKey()));
			if (neo != null) return "the NeoForge mod " + neo.displayName() + " " + neo.version() + ": " + pair.getValue();
		}
		return null;
	}

	private String skipReason(Candidate c, Map<String, String> builtinNames) {
		if (config.override(c.id()).disabled()) return "disabled in config/fabricated.json";
		if (!c.metadata().getEnvironment().matches(side)) {
			return "only for the " + c.metadata().getEnvironment().name().toLowerCase(Locale.ROOT) + " side";
		}
		FabricApiPlan.Module module = plan.module(c.id());
		if (module != null && module.plan() == FabricApiPlan.Plan.UNSUPPORTED) return "Fabric API module not supported on NeoForge: " + module.reason();
		if (module != null && module.plan() == FabricApiPlan.Plan.NATIVE) return "replaced by Fabricated's native implementation";
		if (builtinNames.containsKey(c.id())) return "already provided by " + builtinNames.get(c.id());
		return null;
	}

	private DependencyResolver resolver() {
		DependencyResolver resolver = new DependencyResolver();
		for (Builtin b : builtins) {
			resolver.builtin(b.id(), parseVersion(b.version()), b.name());
			for (String alias : b.aliases()) resolver.builtin(alias, parseVersion(b.version()), b.name());
		}
		neoMods.forEach((id, m) -> resolver.neoForgeMod(id, parseVersion(m.version()), m.displayName()));
		return resolver;
	}

	/** What to tell the player when a dependency on {@code id} is missing. */
	private Map<String, String> missingHints() {
		Map<String, String> hints = new HashMap<>();
		String fapiHint = "Install Fabric API " + plan.fabricApiVersion() + " (the official Fabric jar) in the mods folder; Fabricated loads the parts that work on NeoForge.";
		hints.put("fabric-api", fapiHint);
		hints.put("fabric", fapiHint);
		for (FabricApiPlan.Module m : plan.modules().values()) {
			if (m.plan() == FabricApiPlan.Plan.UNSUPPORTED) {
				hints.put(m.id(), "The Fabric API module " + m.id() + " cannot run on NeoForge (" + m.reason() + "); this mod cannot be used with Fabricated.");
			} else if (m.plan() != FabricApiPlan.Plan.NATIVE) {
				hints.put(m.id(), fapiHint);
			}
		}
		return hints;
	}

	/** Resolve → translate → guard packages → analyse linkage, repeated until no further mod drops out. */
	private final class Rounds {
		final Map<String, Drop> dropped = new LinkedHashMap<>();
		final Map<String, List<PreflightFinding>> failedFindings = new HashMap<>();
		/** Later rounds hit the cache for mods translated earlier in this launch; this remembers the real work. */
		final Map<String, Long> translatedThisLaunch = new HashMap<>();
		/** Mods the resolver disabled for unmet dependencies (strictDependencies turns these into errors). */
		final Set<String> dependencyFailures = new TreeSet<>();
		List<TranslatedMod> translated = List.of();
		Map<String, PackageGuard.Decision> decisions = Map.of();
		Map<String, List<String>> linkage = Map.of();
		/** Compat patches that applied in the last round, by target mod id. */
		Map<String, List<AppliedPatch>> patches = Map.of();
		DependencyResolver.Result resolution;

		/** Every Fabric jar found, loading or not: what a mod that isn't loading would have provided. */
		private List<Candidate> scanned = List.of();

		void run(List<Candidate> scanned, List<Candidate> eligible, DependencyResolver resolver, ModTranslator translator) {
			this.scanned = scanned;
			Map<String, List<String>> aliases = new HashMap<>();
			config.overrides().forEach((id, o) -> aliases.put(id, o.aliases()));
			Map<String, String> hints = missingHints();
			PackageGuard packageGuard = new PackageGuard(config.limits());
			// Every round either removes at least one mod or finishes, so this bound is never the limiting factor.
			for (int round = 0; round <= eligible.size() + 1; round++) {
				List<Candidate> active = eligible.stream().filter(c -> !dropped.containsKey(c.id())).toList();
				Map<String, String> unavailable = new HashMap<>();
				dropped.forEach((id, d) -> unavailable.put(id, d.reason()));
				resolution = resolver.resolve(active, aliases, hints, unavailable);
				boolean changed = !resolution.disabled().isEmpty();
				for (DependencyResolver.Disabled d : resolution.disabled()) {
					dropped.put(d.candidate().id(), new Drop(d.reason(), d.suggestion(), d.candidate()));
					dependencyFailures.add(d.candidate().id());
				}
				List<TranslatedMod> ordered = translate(resolution.ordered(), translator, aliases);
				changed |= ordered.size() != resolution.ordered().size();

				PackageGuard.Result guard = packageGuard.check(ordered, in.platformPackages());
				for (PackageGuard.Conflict conflict : guard.conflicts()) {
					dropped.put(conflict.mod().candidate().id(), new Drop(conflict.reason(), conflict.suggestion(), conflict.mod().candidate()));
					changed = true;
				}
				if (changed) continue;
				translated = ordered;
				decisions = guard.decisions();
				if (!analyseLinkage(ordered)) break;
			}
		}

		/** Translates in parallel; failures are dropped. Returns the translated mods in resolution order. */
		private List<TranslatedMod> translate(List<Candidate> mods, ModTranslator translator, Map<String, List<String>> aliases) {
			Map<String, String> providerOf = providerOf(mods, aliases);
			patches = patchesFor(mods);
			Map<String, List<AppliedPatch>> applied = patches;
			Map<String, TranslatedMod> results = new ConcurrentHashMap<>();
			Map<Candidate, TranslationException> failures = new ConcurrentHashMap<>();
			mods.parallelStream().forEach(c -> {
				try {
					FabricApiPlan.Module module = plan.module(c.id());
					results.put(c.id(), translator.translate(c, effectiveOverride(config, plan, c, applied.getOrDefault(c.id(), List.of())),
							module != null ? Set.copyOf(module.skipMixins()) : Set.of(),
							loadAfter(c, providerOf)));
				} catch (TranslationException e) {
					failures.put(c, e);
				} catch (StackOverflowError e) {
					// ASM parses nested annotation values recursively: a hostile class can nest them without end.
					failures.put(c, new TranslationException("The jar is unsafe or corrupt: a class is nested too deeply to read",
							"Remove " + c.id() + " or get it from a trusted source.", List.of(), e));
				} catch (RuntimeException e) {
					failures.put(c, new TranslationException("Unexpected error: " + e,
							"Report this to Fabricated with logs/fabric-bridge-report.txt (it has the stack trace).", List.of(), e));
				}
			});
			failures.forEach((c, e) -> {
				dropped.put(c.id(), new Drop(e.getMessage(), e.suggestion(), c));
				failedFindings.put(c.id(), e.findings());
				if (e.getCause() != null) {
					for (String line : causeLines(c.id(), e.getCause())) report.line("Translation errors", line);
				}
			});
			List<TranslatedMod> ordered = new ArrayList<>();
			for (Candidate c : mods) {
				TranslatedMod t = results.get(c.id());
				if (t == null) continue;
				ordered.add(t);
				if (!t.cacheHit()) translatedThisLaunch.put(c.id(), t.millis());
			}
			return ordered;
		}

		/** Linkage analysis on the final set; returns whether it disabled a mod (then another round runs). */
		private boolean analyseLinkage(List<TranslatedMod> ordered) {
			LinkageAnalysis analysis = new LinkageAnalysis(in.cache(), config.limits(), in.clientSide());
			Map<String, String> unloaded = unloadedPackages(ordered);
			String fingerprint = Hashing.sha256(ordered.stream().map(t -> t.candidate().id() + "@" + t.candidate().sha256())
					.sorted().collect(Collectors.joining(",")) + "|" + in.minecraftVersion() + "|" + in.neoForgeVersion() + "|" + plan.fabricApiVersion()
					+ "|" + in.fabricatedVersion() + "|" + String.join(",", new TreeSet<>(unloaded.keySet())));
			try (JarClassSource jars = new JarClassSource(ordered, config.limits())) {
				linkage = analysis.analyse(ordered, in.gameClasses().or(in.nativeApiClasses()).or(jars), in.nativeApiInterfaces(), fingerprint,
						unloaded, name -> in.installedClasses().owner(name) != null);
			}
			boolean dropped = false;
			for (TranslatedMod t : ordered) {
				List<String> issues = linkage.get(t.neoForgeId());
				if (issues == null) continue;
				String unloadedUse = issues.stream().filter(LinkageAnalysis::isUnloadedUse).findFirst().orElse(null);
				if (unloadedUse != null) {
					// Mixin would stop the game applying it, whatever strictLinkage says.
					this.dropped.put(t.candidate().id(), new Drop("a required mixin uses a mod that isn't loading: " + unloadedUse,
							"See why that mod isn't loading in this report; this one can load once it does.", t.candidate()));
					dropped = true;
					continue;
				}
				FabricApiPlan.Module module = plan.module(t.candidate().id());
				boolean fapiModule = module != null;
				// What a module misses of Fabric Loader is Fabricated's to provide, not a reason to drop the module.
				boolean fabricApiReference = issues.stream().anyMatch(i -> i.contains("net.fabricmc.fabric.") && !LinkageAnalysis.isLoaderIssue(i));
				// A module planned without some of its dependencies refers to what they would add, where its code never
				// runs (on 1.21.11 the model loading API's wrapper models call the renderer API's emitQuads, which NeoForge
				// never calls): those stay warnings, and its plan's CI check covers the rest.
				boolean plannedWithout = fapiModule && !module.dropDependencies().isEmpty();
				if (config.strictLinkage() || fapiModule && fabricApiReference && !plannedWithout) {
					this.dropped.put(t.candidate().id(), new Drop("references code that does not exist at runtime: " + issues.getFirst(), fapiModule
							? "This Fabric API module depends on internals that Fabricated implements differently; mods needing it cannot run yet."
							: "Update the mod, or set \"strictLinkage\": false to load it anyway.", t.candidate()));
					dropped = true;
				}
			}
			return dropped;
		}

		/**
		 * The packages only Fabric jars that aren't loading have (disabled, refused or dropped), each with one such
		 * mod's id, outside the platform's. Classes there exist nowhere else unless an installed NeoForge mod or
		 * Fabricated has them, which the linkage analysis checks per class.
		 */
		private Map<String, String> unloadedPackages(List<TranslatedMod> ordered) {
			Set<String> loading = new HashSet<>();
			for (TranslatedMod t : ordered) loading.addAll(t.candidate().packages());
			Map<String, String> unloaded = new HashMap<>();
			Set<String> loadingIds = ordered.stream().map(t -> t.candidate().id()).collect(Collectors.toSet());
			for (Candidate c : scanned) {
				// A mod only for the other side isn't on Fabric either: a mixin using it (in a class of its own mod that
				// only that side loads, as GlitchCore's into its client render helper) is as safe as it is there.
				if (loadingIds.contains(c.id()) || !c.metadata().getEnvironment().matches(side)) continue;
				for (String p : c.packages()) {
					// A library jar the platform already provides isn't loading, but its classes exist.
					if (!loading.contains(p) && !in.platformPackages().contains(p)) unloaded.putIfAbsent(p, c.id());
				}
			}
			return unloaded;
		}
	}

	/** A compat patch from mod {@code patchId} that applies to its target this round. */
	private record AppliedPatch(String patchId, CompatHints.Patch patch) {
	}

	/**
	 * Compat patches among {@code mods}, by target: a patch applies while its mod depends on the target and both are
	 * loading (and the player didn't turn the target's hints off). Recomputed every round, so a patch whose own mod
	 * drops out stops applying.
	 */
	private Map<String, List<AppliedPatch>> patchesFor(List<Candidate> mods) {
		Set<String> ids = mods.stream().map(Candidate::id).collect(Collectors.toSet());
		Map<String, List<AppliedPatch>> byTarget = new HashMap<>();
		for (Candidate c : mods) {
			for (CompatHints.Patch patch : c.metadata().compatHints().patches()) {
				String target = patch.target();
				boolean depends = c.metadata().getDependencies().stream()
						.anyMatch(d -> d.getKind() == ModDependency.Kind.DEPENDS && d.getModId().equals(target));
				if (target.equals(c.id()) || !ids.contains(target) || config.override(target).ignoreHints()) continue;
				if (!depends) {
					if (ignoredPatches.add(c.id() + " " + target)) {
						report.line("Compatibility hint warnings", c.id() + ": the patch for " + target + " is ignored, as " + c.id() + " does not depend on it");
					}
					continue;
				}
				byTarget.computeIfAbsent(target, k -> new ArrayList<>()).add(new AppliedPatch(c.id(), patch));
			}
		}
		return byTarget;
	}

	/** Every id a candidate answers to (its own, {@code provides}, config aliases) → that candidate's id. */
	private static Map<String, String> providerOf(List<Candidate> mods, Map<String, List<String>> aliases) {
		Map<String, String> providerOf = new HashMap<>();
		for (Candidate c : mods) {
			providerOf.putIfAbsent(c.id(), c.id());
			for (String id : c.metadata().getProvides()) providerOf.putIfAbsent(id, c.id());
			for (String id : aliases.getOrDefault(c.id(), List.of())) providerOf.putIfAbsent(id, c.id());
		}
		return providerOf;
	}

	/** NeoForge ids the mod's overlay should order it after: its present dependencies, Fabric or NeoForge. */
	private List<String> loadAfter(Candidate c, Map<String, String> providerOf) {
		List<String> after = new ArrayList<>();
		for (ModDependency dep : c.metadata().getDependencies()) {
			if (dep.getKind() != ModDependency.Kind.DEPENDS && dep.getKind() != ModDependency.Kind.RECOMMENDS) continue;
			String id = dep.getModId();
			String provider = providerOf.get(id);
			if (provider != null) {
				if (!provider.equals(c.id())) after.add(ModIds.toNeoForgeId(provider));
			} else {
				NeoForgeMod neo = neoMods.getOrDefault(id, neoMods.get(ModIds.toNeoForgeId(id)));
				if (neo != null) after.add(neo.id());
			}
		}
		return after;
	}

	/** Reports the outcome and builds the Fabric Loader runtime's view of every mod. */
	private Result assemble(FabricLoaderRuntime runtime, Rounds rounds) {
		for (Map.Entry<String, Drop> d : rounds.dropped.entrySet()) {
			Candidate c = d.getValue().candidate();
			report.mod(new ModRow(d.getKey(), c.version(), c.origin(), Status.DISABLED, d.getValue().reason(), d.getValue().suggestion()));
			for (PreflightFinding f : rounds.failedFindings.getOrDefault(d.getKey(), List.of())) {
				report.line("Mixin pre-flight failures", d.getKey() + ": " + f);
			}
		}
		if (rounds.resolution != null) {
			rounds.resolution.warnings().forEach(w -> report.line("Dependency warnings", w));
			rounds.resolution.crossLoaderLinks().forEach((id, targets) -> report.line("Cross-loader dependencies",
					id + " uses NeoForge build(s) of " + targets + "; this usually works for multi-loader libraries but is not guaranteed"));
		}

		// Built-ins first: a Fabric mod's "provides" never shadows Minecraft, Fabricated or a native module.
		for (Builtin b : builtins) addBuiltin(runtime, b.id(), b.version(), b.name(), b.aliases(), Map.of(), builtinFiles(b.id()));

		List<LoadedMod> loaded = new ArrayList<>();
		Map<String, ModContainerImpl> containers = new LinkedHashMap<>();
		Set<String> finalIds = rounds.translated.stream().map(t -> t.candidate().id()).filter(id -> !rounds.dropped.containsKey(id))
				.collect(Collectors.toSet());
		for (TranslatedMod t : rounds.translated) {
			Candidate c = t.candidate();
			if (!finalIds.contains(c.id())) continue;
			PackageGuard.Decision decision = rounds.decisions.get(t.neoForgeId());
			reportLoaded(t, rounds.translatedThisLaunch.get(c.id()), decision, rounds.linkage.get(t.neoForgeId()));

			ModOrigin origin = c.parentId() == null
					? new ModContainerImpl.Origin(ModOrigin.Kind.PATH, List.of(c.path()), null, null)
					: new ModContainerImpl.Origin(ModOrigin.Kind.NESTED, List.of(), c.parentId(), c.origin().substring(c.origin().lastIndexOf("!/") + 2));
			ModContainerImpl container = new ModContainerImpl(c.metadata(), List.of(t.jar()), origin,
					finalIds.contains(c.parentId()) ? c.parentId() : null,
					c.nestedChildren().stream().filter(finalIds::contains).toList(), runtime);
			FabricatedConfig.ModOverride override = config.override(c.id());
			List<String> extraIds = new ArrayList<>(c.metadata().getProvides());
			extraIds.addAll(override.aliases());
			runtime.addMod(container, extraIds);
			Set<String> skipEntrypoints = new HashSet<>(override.skipEntrypoints());
			for (AppliedPatch p : rounds.patches.getOrDefault(c.id(), List.of())) {
				skipEntrypoints.addAll(p.patch().skipEntrypoints());
				report.line("Compatibility patches", c.id() + " <- " + p.patchId() + ": skips mixins " + p.patch().skipMixins()
						+ ", entrypoints " + p.patch().skipEntrypoints());
			}
			Map<String, List<EntrypointSpec>> entrypoints = new LinkedHashMap<>(c.metadata().compatHints().entrypointsOf(c.metadata().entrypoints()));
			entrypoints.keySet().removeAll(skipEntrypoints);
			runtime.addEntrypoints(container, entrypoints);
			List<String> pluginNotes = new ArrayList<>();
			List<Interop.Annotation> plugins = Interop.annotations(entrypoints, pluginNotes);
			plugins.forEach(p -> report.line("Plugins for other mods", c.id() + ": " + p.host() + " plugin " + p.className()));
			pluginNotes.forEach(n -> report.line("Plugins for other mods", c.id() + ": " + n));
			loaded.add(new LoadedMod(t, decision != null ? decision : PackageGuard.Decision.NONE, plugins));
			runtime.addLanguageAdapters(container, c.metadata().languageAdapters());
			containers.put(c.id(), container);
		}
		// NeoForge mods, visible through Fabric's API.
		for (NeoForgeMod m : neoForgeMods) {
			Map<String, CustomValue> customValues = new LinkedHashMap<>();
			m.properties().forEach((key, value) -> customValues.put(key, CustomValueImpl.fromJson(value)));
			addBuiltin(runtime, m.id(), m.version(), m.displayName(), m.provides(), customValues, m.files());
		}

		in.cache().notes().forEach(n -> report.line("Cache", n));
		return new Result(config, loaded, runtime, containers, report, Set.copyOf(rounds.dependencyFailures));
	}

	/**
	 * Why translating a mod failed, for a bug report: each exception of the cause chain with its first frames (the
	 * report has no other copy: core doesn't log).
	 */
	static List<String> causeLines(String modId, Throwable cause) {
		List<String> lines = new ArrayList<>();
		lines.add(modId + ":");
		int depth = 0;
		for (Throwable t = cause; t != null && depth < 5; t = t.getCause() == t ? null : t.getCause(), depth++) {
			lines.add((depth == 0 ? "  " : "  Caused by: ") + t);
			StackTraceElement[] frames = t.getStackTrace();
			for (int i = 0; i < Math.min(frames.length, 12); i++) lines.add("    at " + frames[i]);
			if (frames.length > 12) lines.add("    ... " + (frames.length - 12) + " more");
		}
		return lines;
	}

	/**
	 * The report section of the mixins (or their failing injectors) left out of loaded mods: NeoForge's code differs
	 * where they hook in, and the mod loads without them (LEAVE-1).
	 */
	static final String LEFT_OUT_SECTION = "Mixins left out (NeoForge's code differs where they hook in)";

	private void reportLoaded(TranslatedMod t, Long millis, PackageGuard.Decision decision, List<String> linkageIssues) {
		Candidate c = t.candidate();
		long leftOut = t.findings().stream().filter(f -> f.severity() == Severity.LEFT_OUT).map(f -> f.config() + " " + f.mixin()).distinct().count();
		String detail = (millis == null ? "cached" : "translated in " + millis + " ms")
				+ (t.accessTransformers() > 0 ? ", " + t.accessTransformers() + " access transformer(s)" : "")
				+ (t.injectedInterfaces().isEmpty() ? "" : ", " + t.injectedInterfaces().size() + " injected interface(s)")
				+ (leftOut == 0 ? "" : ", " + leftOut + " mixin(s) left out in whole or in part, so the features they hook in for don't work (see "
						+ LEFT_OUT_SECTION + ")");
		report.mod(new ModRow(c.id(), c.version(), c.origin(), Status.LOADED, detail, ""));
		for (PreflightFinding f : t.findings()) {
			String line = c.id() + ": " + f + (f.suggestion().isEmpty() ? "" : " - " + f.suggestion());
			report.line(f.severity() == Severity.LEFT_OUT ? LEFT_OUT_SECTION : "Mixin pre-flight warnings", line);
		}
		for (String n : t.notes()) report.line("Translation notes", c.id() + ": " + n);
		FabricApiPlan.Module planned = plan.module(c.id());
		if (planned != null && !planned.skipMixins().isEmpty()) {
			report.line("Fabric API mixins skipped (conflict with NeoForge)", c.id() + ": " + planned.skipMixins()
					+ (planned.reason().isEmpty() ? "" : " - " + planned.reason()));
		}
		if (decision != null) decision.notes().forEach(n -> report.line("Package guard", c.id() + " " + n));
		if (linkageIssues != null) linkageIssues.forEach(i -> report.line("Linkage warnings (code that will fail if it runs)", c.id() + ": " + i));
	}

	/** User overrides, plus the mod's own hints and the plan's known-conflicting mixins for pass-through Fabric API modules. */
	static FabricatedConfig.ModOverride effectiveOverride(FabricatedConfig config, FabricApiPlan plan, Candidate c, List<AppliedPatch> patches) {
		FabricatedConfig.ModOverride user = config.override(c.id());
		FabricApiPlan.Module module = plan.module(c.id());
		Set<String> skip = new TreeSet<>(user.skipMixins());
		if (module != null) skip.addAll(module.skipMixins());
		skip.addAll(c.metadata().compatHints().skipMixins());
		for (AppliedPatch p : patches) skip.addAll(p.patch().skipMixins());
		if (skip.equals(user.skipMixins())) return user;
		return new FabricatedConfig.ModOverride(user.disabled(), Set.copyOf(skip), user.skipEntrypoints(), user.aliases(), user.ignorePreflight(),
				user.ignoreHints(), user.loadUnsupported());
	}

	/**
	 * A built-in mod's files: Fabric Loader gives every mod a root, and mods take the first without checking (OneConfig).
	 * Minecraft's are the game's, Java's its home, the others those of the mod that provides them on NeoForge.
	 */
	private List<Path> builtinFiles(String id) {
		return switch (id) {
			case "minecraft" -> in.gameFiles();
			case "java" -> List.of(Path.of(System.getProperty("java.home")));
			case "mixinextras" -> neoMods.containsKey("neoforge") ? neoMods.get("neoforge").files() : List.of();
			default -> in.fabricatedFiles();
		};
	}

	private static void addBuiltin(FabricLoaderRuntime runtime, String id, String version, String name, List<String> aliases,
			Map<String, CustomValue> customValues, List<Path> files) {
		FabricModMetadata md = FabricModMetadata.builtin(id, parseVersion(version), name, "", customValues);
		ModOrigin origin = new ModContainerImpl.Origin(files.isEmpty() ? ModOrigin.Kind.UNKNOWN : ModOrigin.Kind.PATH, files, null, null);
		runtime.addMod(new ModContainerImpl(md, files, origin, null, List.of(), runtime), aliases);
	}

	private static Version parseVersion(String version) {
		try {
			return VersionParser.parse(version == null || version.isEmpty() ? "0" : version, false);
		} catch (VersionParsingException e) {
			throw new IllegalArgumentException("Unparseable version " + version, e);
		}
	}
}
