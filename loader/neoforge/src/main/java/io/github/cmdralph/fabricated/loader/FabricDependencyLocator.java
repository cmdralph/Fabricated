package io.github.cmdralph.fabricated.loader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.api.internal.ApiHolder;
import io.github.cmdralph.fabricated.core.Pipeline;
import io.github.cmdralph.fabricated.core.api.FabricatedApiImpl;
import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.archive.DeterministicJarWriter;
import io.github.cmdralph.fabricated.core.archive.Hashing;
import io.github.cmdralph.fabricated.core.archive.SafeArchive;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.InstalledClasses;
import io.github.cmdralph.fabricated.core.cache.TranslationCache;
import io.github.cmdralph.fabricated.core.config.FabricatedConfig;
import io.github.cmdralph.fabricated.core.json.JsonException;
import io.github.cmdralph.fabricated.core.metadata.ModIds;
import io.github.cmdralph.fabricated.core.mixin.MixinConfigFile;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding;
import io.github.cmdralph.fabricated.core.remap.MappingException;
import io.github.cmdralph.fabricated.core.remap.OfficialClasses;
import io.github.cmdralph.fabricated.core.report.Report;
import io.github.cmdralph.fabricated.core.resolve.FabricApiPlan;
import io.github.cmdralph.fabricated.core.resolve.NativeModules;
import io.github.cmdralph.fabricated.core.resolve.NestedNeoForgeJars;
import io.github.cmdralph.fabricated.core.resolve.PlatformLibraries;
import io.github.cmdralph.fabricated.core.translate.LinkageAnalysis;
import io.github.cmdralph.fabricated.core.translate.PackageGuard;
import io.github.cmdralph.fabricated.core.translate.PlatformDescriptor;
import io.github.cmdralph.fabricated.core.translate.TranslationEnvironment.Namespace;
import io.github.cmdralph.fabricated.fabricloader.version.VersionParser;
import net.fabricmc.loader.api.VersionParsingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IDependencyLocator;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

/**
 * Runs Fabricated's discovery pipeline after NeoForge's own dependency locators (the mods those add, such as jar-in-jar
 * ones, are read by {@link NeoForgeMods}), then hands every translated Fabric mod to NeoForge as an ordinary mod file:
 * the (remapped) jar overlaid with the generated metadata, with split-package conflicts filtered out. Anything that
 * goes wrong here disables Fabric support for this launch with a warning; apart from {@code strictDependencies}, and
 * Sinytra Connector or a second Fabricated jar in the mods folder ({@link FabricCandidateLocator#refusal}), it never
 * stops the game.
 */
public final class FabricDependencyLocator implements IDependencyLocator {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final String GAME_MOD_RESOURCE = "game-mod.jar";
	private static final String MESSAGE = "§e[Fabricated]§r {0}";
	private static final Pattern VERSION_IN_NAME = Pattern.compile("\\d+\\.\\d+\\.\\d+");

	@Override
	public void scanMods(List<IModFile> loadedMods, IDiscoveryPipeline pipeline) {
		if (FabricatedState.get() != null) return;
		long start = System.nanoTime();
		try {
			run(loadedMods, pipeline);
			LOGGER.info("Fabricated discovery finished in {} ms", (System.nanoTime() - start) / 1_000_000);
		} catch (Throwable t) {
			if (t instanceof VirtualMachineError vme && !(t instanceof StackOverflowError)) throw vme;
			// The state is published just before the first Fabric mod is handed to NeoForge.
			if (FabricatedState.get() != null) {
				LOGGER.error("Fabricated failed after handing Fabric mods to NeoForge; some may not work this launch", t);
				pipeline.addIssue(ModLoadingIssue.warning(MESSAGE,
						"Fabric mod support failed partway: Fabric mods may load without everything they need this launch: " + t
								+ ". See logs/latest.log.").withCause(t));
				return;
			}
			LOGGER.error("Fabricated failed during discovery; Fabric mods will not be loaded this launch", t);
			pipeline.addIssue(ModLoadingIssue.warning(MESSAGE,
					"Fabric mod support failed to start and is disabled for this launch: " + t + ". See logs/latest.log.").withCause(t));
		}
	}

	private void run(List<IModFile> loadedMods, IDiscoveryPipeline pipeline) throws IOException, JsonException {
		String refusal = FabricCandidateLocator.refusal();
		if (refusal != null) {
			pipeline.addIssue(ModLoadingIssue.error(MESSAGE, refusal));
			return;
		}
		FabricatedInfo info = FabricatedInfo.get();
		String mcVersion = Fml.minecraftVersion();
		String neoVersion = Fml.neoForgeVersion();
		List<Path> fabricJars = FabricCandidateLocator.claimed();

		if (!info.supports(mcVersion)) {
			pipeline.addIssue(ModLoadingIssue.warning(MESSAGE, "This Fabricated build is for Minecraft " + info.minecraftVersion()
					+ " but the game is " + mcVersion + ". Fabric mods are not loaded; install the matching Fabricated build."));
			return;
		}

		Path gameDir = Fml.gameDir();
		Path logsDir = gameDir.resolve("logs");
		boolean client = Fml.client();

		IModFile minecraft = find(loadedMods, "minecraft")
				.orElseThrow(() -> new IllegalStateException("NeoForge did not provide the minecraft mod file"));
		// NeoForge's own classes are the platform's too: the extension interfaces it gives game classes (whose default
		// methods an interface a Fabric mod adds may collide with, CORPUS-11) are in its jar, not the game's.
		IModFile neoForge = find(loadedMods, "neoforge").orElse(null);
		ClassBytesSource gameClasses = name -> {
			byte[] data = Fml.read(minecraft, name + ".class");
			return data != null || neoForge == null ? data : Fml.read(neoForge, name + ".class");
		};

		FabricatedConfig config = FabricatedConfig.load(FMLPaths.CONFIGDIR.get());
		TranslationCache cache = new TranslationCache(gameDir, config.cacheEnabled(), config.verifyIntegrity());
		// Fabricated's own in-game mod (native Fabric API implementation and NeoForge event bridges): loaded from the
		// development workspace, or extracted from this jar when there is anything Fabric to load.
		Optional<IModFile> devGameMod = find(loadedMods, ModIds.FABRICATED);
		Path gameModJar = devGameMod.isPresent() || fabricJars.isEmpty() ? null : extractGameMod(cache);
		List<Path> fabricatedFiles = gameModJar != null ? List.of(gameModJar) : devGameMod.map(file -> List.of(file.getFilePath())).orElse(List.of());

		// Classes visible to NeoForge's non-transforming loaders (JDK and libraries such as DataFixerUpper or
		// Brigadier). Mixins targeting them never apply, so pre-flight reports them instead of letting the mod
		// fail later with a ClassCastException or a missing behaviour.
		ClassLoader platformLoader = FabricDependencyLocator.class.getClassLoader();
		ClassBytesSource untransformable = name -> {
			if (Fml.contains(minecraft, name + ".class")) return null;
			try (var stream = platformLoader.getResourceAsStream(name + ".class")) {
				return stream == null ? null : stream.readAllBytes();
			}
		};

		// Packages other mods own, which Fabric mods mustn't split (only needed when there are Fabric mods).
		Set<String> platformPackages = new HashSet<>();
		if (!fabricJars.isEmpty()) {
			for (IModFile file : loadedMods) {
				for (String pkg : Fml.packages(file)) platformPackages.add(pkg.replace('.', '/'));
			}
			for (ModuleLayer layer : Fml.platformLayers()) {
				for (Module module : layer.modules()) {
					ModuleDescriptor d = module.getDescriptor();
					if (d != null) d.packages().forEach(p -> platformPackages.add(p.replace('.', '/')));
				}
			}
		}
		ClassBytesSource nativeApi = ClassBytesSource.EMPTY;
		List<InstalledClasses.Jar> fabricApi = List.of();
		// Interfaces the in-game mod's mixins add to game classes (Fabric API's injected interfaces).
		Supplier<Map<String, List<String>>> nativeApiInterfaces = Map::of;
		// The packages of the native modules other mods provide, left out of the in-game mod (filled below, before the
		// pipeline reads any class): pre-flight then sees the provider's classes, as the game will.
		Set<String> standingDown = new HashSet<>();
		NativeModules.StandDown standDown = null;
		Overlay gameModOverlay = null;
		FabricApiPlan plan;
		Pipeline.Result result;
		// Vanilla's game jar, which pre-flight reads to tell where NeoForge's patches moved an injection point (VANILLA-1),
		// only where its names are the game's.
		List<SafeArchive> vanillaJars = new ArrayList<>();
		// Closed once the pipeline is done with it, however it ends.
		try (SafeArchive archive = gameModJar == null ? null : SafeArchive.open(gameModJar, ArchiveLimits.DEFAULTS)) {
			if (archive != null) {
				// Only needed when some linkage result isn't cached.
				nativeApiInterfaces = () -> {
					Map<String, List<String>> interfaces = new HashMap<>();
					try {
						for (String name : archive.classEntryNames()) {
							if (!name.contains("/mixin/")) continue;
							byte[] data = archive.read(name);
							if (data != null) LinkageAnalysis.mixinAdditions(data, interfaces);
						}
					} catch (IOException e) {
						LOGGER.warn("Could not read Fabricated's own mixins: {}", e.getMessage());
					}
					return interfaces;
				};
				nativeApi = name -> standingDown.contains(packageOf(name)) ? null : archive.read(name + ".class");
				fabricApi = List.of(new InstalledClasses.Jar("Fabric API", name -> !standingDown.contains(packageOf(name)) && archive.contains(name + ".class"),
						nativeApi));
			} else if (devGameMod.isPresent()) {
				IModFile devFile = devGameMod.get();
				nativeApi = name -> Fml.read(devFile, name + ".class");
				fabricApi = List.of(new InstalledClasses.Jar("Fabric API", name -> Fml.contains(devFile, name + ".class"), nativeApi));
			}

			// Fabric mixins may target the classes of Fabricated's Fabric API and of the NeoForge mods (an addon's mod).
			NeoForgeMods.Found neoForgeMods = NeoForgeMods.of(loadedMods, !fabricJars.isEmpty(), fabricApi);

			byte[] planJson = FabricatedInfo.readOptional("fabric-api-plan.json");
			if (planJson == null) throw new IllegalStateException("This Fabricated build is missing its fabric-api-plan.json");
			plan = FabricApiPlan.parse(new String(planJson, StandardCharsets.UTF_8));

			// The provider policy: a Fabric API module another mod brings is that mod's, and Fabricated's own part of it
			// stands down, as only one copy of its packages can load (core's NativeModules).
			List<String> providerNotes = new ArrayList<>();
			List<Pipeline.NeoForgeMod> neoMods = neoForgeMods.mods();
			if (archive != null) {
				byte[] index = FabricatedInfo.readOptional(NativeModules.INDEX);
				NativeModules nativeModules = index == null ? NativeModules.NONE : NativeModules.parse(new String(index, StandardCharsets.UTF_8),
						archive.contains(NativeModules.MIXIN_MODULES) ? archive.readText(NativeModules.MIXIN_MODULES) : null);
				neoMods = providingByPackages(neoForgeMods, nativeModules, providerNotes);
				Map<String, Pipeline.NeoForgeMod> provided = Pipeline.providedElsewhere(plan, neoMods);
				Map<String, MixinConfigFile> configs = new LinkedHashMap<>();
				for (String name : archive.entryNames()) {
					if (name.indexOf('/') < 0 && name.endsWith(".mixins.json")) configs.put(name, MixinConfigFile.parse(archive.readText(name)));
				}
				standDown = nativeModules.standDown(provided.keySet(), configs);
				standingDown.addAll(standDown.packages());
				standDown.unmapped().forEach(m -> providerNotes.add("Fabricated's mixin " + m + " says no module it serves; it stays"));
				if (!standDown.isEmpty()) gameModOverlay = gameModOverlay(archive, configs, standDown, cache);
				for (String name : archive.classEntryNames()) {
					String pkg = packageOf(name);
					if (!pkg.isEmpty() && !standingDown.contains(pkg)) platformPackages.add(pkg);
				}
			} else if (devGameMod.isPresent() && !Pipeline.providedElsewhere(plan, neoMods).isEmpty()) {
				providerNotes.add("Other mods provide Fabric API modules, but the in-game mod of a development workspace can't leave its own copies out");
			}
			// What NeoForge's jar-in-jar locator adds isn't among the files this locator is handed on FML 10 and later: the
			// nested mods, and the nested libraries, which FML may load above the game's loader (Kotlin for Forge's Kotlin,
			// in its plugin loader, which is no module layer).
			if (!fabricJars.isEmpty()) {
				neoForgeMods.providers().forEach(p -> platformPackages.addAll(p.packages()));
				platformPackages.addAll(neoForgeMods.libraryPackages());
			}

			Pipeline.VanillaGame vanilla = fabricJars.isEmpty() ? Pipeline.VanillaGame.NONE
					: VanillaGameJar.find(minecraft.getFilePath(), mcVersion, client, cache, vanillaJars);
			if (info.namespace() == Namespace.INTERMEDIARY && vanilla.classes() != ClassBytesSource.EMPTY) {
				// 1.21.11's game jar is obfuscated: read in Mojang's names through the mappings NeoForge's installer left (VANILLA-2).
				vanilla = new Pipeline.VanillaGame(new OfficialClasses(vanilla.classes(), () -> {
					try {
						return Files.newBufferedReader(IntermediaryMappings.locateMojangMappings(mcVersion, client, config.mojangMappingsPath()),
								StandardCharsets.UTF_8);
					} catch (MappingException e) {
						throw new IOException(e.getMessage(), e);
					}
				}), vanilla.location() + ", in Mojang's names through its mappings");
			}
			Pipeline.Input input = new Pipeline.Input(gameDir, FMLPaths.CONFIGDIR.get(), config, cache, fabricJars, mcVersion, neoVersion,
					client, info.namespace(), runtimeMixinVersion(info), mixinExtrasVersion(loadedMods), info.mixinLowest(), info.fabricatedVersion(), info.build(), !Fml.production(), gameClasses, List.of(minecraft.getFilePath()), vanilla, fabricatedFiles, untransformable,
					platformPackages, plan, nativeApi, nativeApiInterfaces, neoMods, neoForgeMods.classes(),
					(mappingsCache, mojangPath) -> IntermediaryMappings.load(mappingsCache, mcVersion, client, mojangPath),
					Fml.programArgs(), PlatformDescriptor.NEOFORGE);

			result = Pipeline.run(input);
			FabricLoaderApiCopies.notes().forEach(n -> result.report().line("Fabric Loader API", n));
			providerNotes.forEach(n -> result.report().line("Fabric API providers", n));
			if (standDown != null && !standDown.isEmpty()) {
				result.report().line("Fabric API providers", "Fabricated's in-game mod leaves out " + standDown.packages().size()
						+ " package(s) of the modules other mods provide, the mixin configs " + standDown.mixinConfigs() + " and the mixins " + standDown.mixins());
			}
		} finally {
			closeAll(vanillaJars);
		}
		result.runtime().install();
		// Fabric entrypoints run when Fabricated's in-game mod loads: always in development, else when there is anything Fabric.
		boolean addGameMod = gameModJar != null && (!result.mods().isEmpty() || !fabricJars.isEmpty());
		FabricatedApiImpl api = new FabricatedApiImpl(info.fabricatedVersion(), plan, result.report(), result.runtime(), devGameMod.isPresent() || addGameMod);
		FabricatedState state = new FabricatedState(result, logsDir, api);
		FabricatedState.set(state);
		ApiHolder.install(api);

		ModFileDiscoveryAttributes attributes = ModFileDiscoveryAttributes.DEFAULT.withDependencyLocator(this);
		Set<String> refused = new HashSet<>();
		for (Pipeline.LoadedMod mod : result.mods()) {
			PackageGuard.Decision packages = mod.packages();
			Predicate<String> filter = packages.excludedPackages().isEmpty() && packages.hiddenFiles().isEmpty() ? null : path -> !packages.hides(path);
			List<FilteredPath> paths = new ArrayList<>(List.of(
					new FilteredPath(mod.translated().jar(), filter),
					new FilteredPath(mod.translated().overlay(), filter)));
			// Other mods' part of a package split with this one: one module has the whole package.
			packages.borrowed().forEach((jar, files) -> paths.add(new FilteredPath(jar, files::contains)));
			Optional<IModFile> added = Fml.addModFile(pipeline, paths, attributes);
			if (added.isEmpty()) {
				// Fabric code mustn't see it as loaded, or run its entrypoints, when NeoForge won't load its classes.
				String id = mod.translated().candidate().id();
				refused.add(id);
				result.runtime().removeMod(id);
				result.report().disable(id, "NeoForge did not accept the translated mod", "See logs/latest.log for NeoForge's reason.");
			}
		}
		if (!refused.isEmpty()) {
			// Nor may its interfaces be injected into game classes: they would fail to load, with the class missing.
			state = new FabricatedState(result.without(refused), logsDir, api);
			FabricatedState.set(state);
		}
		if (addGameMod && gameModOverlay == null) {
			pipeline.addPath(gameModJar, attributes, IncompatibleFileReporting.ERROR);
		} else if (addGameMod) {
			// Without the packages, mixin configs and mixins of the modules other mods provide: the overlay holds the
			// mods.toml and mixin configs that no longer name them.
			Set<String> leftOut = standingDown;
			Set<String> replaced = gameModOverlay.files();
			Predicate<String> keeps = path -> !replaced.contains(path) && !leftOut.contains(packageOf(path));
			if (Fml.addModFile(pipeline, List.of(new FilteredPath(gameModJar, keeps), new FilteredPath(gameModOverlay.path(), null)), attributes).isEmpty()) {
				LOGGER.error("NeoForge did not accept Fabricated's in-game mod; see the lines above");
			}
		}

		int quietFabricApi = 0;
		for (Report.ModRow row : result.report().mods()) {
			if (row.status() == Report.Status.DISABLED || row.status() == Report.Status.REJECTED) {
				// A disabled Fabric API module is expected (see COMPATIBILITY.md) and isn't the player's mod: any mod
				// that needs it is disabled too, with its own warning naming the module. Reporting each module to
				// NeoForge would show a warnings screen on every client launch, so they go to the report only.
				if (plan.isFabricApiModule(row.id())) {
					quietFabricApi++;
					continue;
				}
				String text = "Fabric mod " + row.id() + (row.version().isEmpty() ? "" : " " + row.version()) + " was not loaded: "
						+ row.detail() + (row.suggestion().isEmpty() ? "" : " Fix: " + row.suggestion());
				// strictDependencies: unmet dependencies stop the game on NeoForge's error screen instead of being skipped.
				boolean hardStop = result.config().strictDependencies() && result.dependencyFailures().contains(row.id());
				pipeline.addIssue(hardStop ? ModLoadingIssue.error(MESSAGE, text) : ModLoadingIssue.warning(MESSAGE, text));
			}
		}
		if (quietFabricApi > 0) {
			LOGGER.info("{} Fabric API module(s) can't run on NeoForge yet and were skipped; see logs/fabric-bridge-report.txt", quietFabricApi);
		}
		// The player's mods that loaded without some of their mixins (LEAVE-1): in the log, as a warnings screen on every
		// launch would be worse than the features those mixins hook in for.
		List<String> partial = new ArrayList<>();
		for (Pipeline.LoadedMod mod : result.mods()) {
			String id = mod.translated().candidate().id();
			if (!plan.isFabricApiModule(id) && mod.translated().findings().stream().anyMatch(f -> f.severity() == PreflightFinding.Severity.LEFT_OUT)) {
				partial.add(id);
			}
		}
		if (!partial.isEmpty()) {
			LOGGER.warn("{} Fabric mod(s) loaded without some of their mixins, as NeoForge's code differs where they hook in, so the features"
					+ " those hook in for don't work: {}. See logs/{}", partial.size(), String.join(", ", partial), Report.FILE_NAME);
		}
		// The pipeline reported the cache's notes so far; saving its index may add one.
		int reported = cache.notes().size();
		cache.finish(config.maxUnusedLaunches());
		for (String note : cache.notes().subList(reported, cache.notes().size())) result.report().line("Cache", note);
		state.writeReport();
	}

	/** The package (internal form) of a class's internal name or of a file's path; empty for the root. */
	private static String packageOf(String name) {
		int slash = name.lastIndexOf('/');
		return slash > 0 ? name.substring(0, slash) : "";
	}

	/**
	 * The NeoForge mods, each also providing the native modules whose packages its jar has under another id (a mod
	 * bundling a Fabric API module): only one copy of a package can load, so that mod provides the module.
	 */
	private static List<Pipeline.NeoForgeMod> providingByPackages(NeoForgeMods.Found found, NativeModules modules, List<String> notes) {
		Map<Pipeline.NeoForgeMod, Set<String>> extra = new IdentityHashMap<>();
		for (NeoForgeMods.Provider provider : found.providers()) {
			for (String pkg : new TreeSet<>(provider.packages())) {
				String module = modules.owner(pkg);
				if (module == null || ModIds.toNeoForgeId(module).equals(provider.mod().id()) || provider.mod().provides().contains(module)) continue;
				if (extra.computeIfAbsent(provider.mod(), k -> new LinkedHashSet<>()).add(module)) {
					notes.add(provider.origin() + " has the package " + pkg + " of " + module + ": " + provider.mod().displayName() + " provides that module");
				}
			}
		}
		if (extra.isEmpty()) return found.mods();
		List<Pipeline.NeoForgeMod> mods = new ArrayList<>();
		for (Pipeline.NeoForgeMod mod : found.mods()) {
			Set<String> more = extra.get(mod);
			if (more == null) {
				mods.add(mod);
				continue;
			}
			Set<String> ids = new LinkedHashSet<>(mod.provides());
			ids.addAll(more);
			Map<String, Object> properties = new LinkedHashMap<>(mod.properties());
			properties.put(Pipeline.NeoForgeMod.PROVIDES, List.copyOf(ids));
			mods.add(new Pipeline.NeoForgeMod(mod.id(), mod.version(), mod.displayName(), properties, mod.files()));
		}
		return mods;
	}

	/**
	 * The files of the in-game mod that change when parts of it stand down, in a jar served before its own.
	 *
	 * @param files the paths it holds, which the in-game mod's own jar no longer serves
	 */
	private record Overlay(Path path, Set<String> files) {
	}

	/** The in-game mod's mods.toml without the mixin configs that stand down, and its other configs without the mixins that do. */
	private static Overlay gameModOverlay(SafeArchive archive, Map<String, MixinConfigFile> configs, NativeModules.StandDown standDown,
			TranslationCache cache) throws IOException {
		Map<String, byte[]> files = new TreeMap<>();
		String modsToml = NestedNeoForgeJars.MODS_TOML;
		files.put(modsToml, NativeModules.withoutMixinConfigs(archive.readText(modsToml), standDown.mixinConfigs()).getBytes(StandardCharsets.UTF_8));
		standDown.mixins().forEach((config, mixins) -> files.put(config, configs.get(config).withoutMixins(mixins).getBytes(StandardCharsets.UTF_8)));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		DeterministicJarWriter.write(out, files);
		return new Overlay(cache.storeNested(out.toByteArray()), Set.copyOf(files.keySet()));
	}

	private static Optional<IModFile> find(List<IModFile> files, String modId) {
		for (IModFile file : files) {
			for (IModInfo info : file.getModInfos()) {
				if (info.getModId().equals(modId)) return Optional.of(file);
			}
		}
		return Optional.empty();
	}

	private static void closeAll(List<SafeArchive> archives) {
		for (SafeArchive archive : archives) {
			try {
				archive.close();
			} catch (IOException e) {
				// Read only; nothing to lose.
			}
		}
	}

	/**
	 * Copies the bundled in-game mod into the cache (an entry keyed by its hash, so it is written once), as
	 * {@code fabricated.jar}: NeoForge names a mod by its file in its lists (a crash report's), as the translated mods'
	 * {@code <modid>.jar}.
	 */
	private static Path extractGameMod(TranslationCache cache) throws IOException {
		byte[] data = FabricatedInfo.readOptional(GAME_MOD_RESOURCE);
		if (data == null) throw new IOException("The Fabricated jar is missing " + GAME_MOD_RESOURCE);
		String name = TranslationCache.entryName(ModIds.FABRICATED, Hashing.sha256(data));
		String fileName = ModIds.FABRICATED + ".jar";
		TranslationCache.Entry entry = cache.lookup(name, fileName).orElse(null);
		if (entry == null) entry = cache.store(name, Map.of(fileName, data), Map.of());
		Path path = entry.file(fileName);
		if (!Files.isRegularFile(path)) throw new IOException("Could not extract " + GAME_MOD_RESOURCE);
		return path;
	}

	/** The running Mixin version, read the same way NeoForge reads it. */
	static String runtimeMixinVersion(FabricatedInfo info) {
		String version = classVersion("org.spongepowered.asm.mixin.FabricUtil");
		return version != null ? version : info.mixinFallback();
	}

	/** The version of the module, else the package, a class on NeoForge's own layers is in; null when unknown. */
	private static String classVersion(String className) {
		try {
			Class<?> cls = Class.forName(className);
			return Optional.ofNullable(cls.getModule().getDescriptor()).flatMap(ModuleDescriptor::version).map(ModuleDescriptor.Version::toString)
					.orElse(cls.getPackage().getImplementationVersion());
		} catch (ClassNotFoundException | LinkageError e) {
			LOGGER.debug("Could not determine the version of {}", className, e);
			return null;
		}
	}

	/**
	 * The MixinExtras version NeoForge runs: the newest that a discovered file nests (NeoForge nests it in its own jar,
	 * and loads the newest nested copy) or is (its mod metadata, else its manifest, else its file name); else from its
	 * classes' module; null when not found.
	 */
	static String mixinExtrasVersion(List<IModFile> files) {
		String newest = null;
		for (IModFile file : files) {
			String version = null;
			try {
				byte[] jarJar = Fml.read(file, "META-INF/jarjar/metadata.json");
				if (jarJar != null) version = PlatformLibraries.nestedMixinExtras(new String(jarJar, StandardCharsets.UTF_8));
			} catch (IOException | JsonException e) {
				LOGGER.debug("Could not read the nested jars of {}", file.getFileName(), e);
			}
			if (version == null && Fml.contains(file, "com/llamalad7/mixinextras/MixinExtrasBootstrap.class")) version = ownVersion(file);
			if (version != null && (newest == null || newer(version, newest))) newest = version;
		}
		if (newest != null) return newest;
		// Not a discovered file: a library on NeoForge's own layers.
		return classVersion("com.llamalad7.mixinextras.MixinExtrasBootstrap");
	}

	private static String ownVersion(IModFile file) {
		for (IModInfo mod : file.getModInfos()) {
			if (mod.getModId().startsWith("mixinextras")) return mod.getVersion().toString();
		}
		String fromManifest = Fml.manifest(file).getMainAttributes().getValue("Implementation-Version");
		if (fromManifest != null) return fromManifest;
		Matcher fromName = VERSION_IN_NAME.matcher(file.getFileName());
		return fromName.find() ? fromName.group() : null;
	}

	private static boolean newer(String a, String b) {
		try {
			return VersionParser.parse(a, false).compareTo(VersionParser.parse(b, false)) > 0;
		} catch (VersionParsingException e) {
			return false;
		}
	}

	@Override
	public int getPriority() {
		// After NeoForge's own dependency locators. Each is handed the same mod files, without the ones the others add:
		// the mods nested in those files are read by NeoForgeMods.
		return LOWEST_SYSTEM_PRIORITY;
	}

	@Override
	public String toString() {
		return "fabricated";
	}
}
