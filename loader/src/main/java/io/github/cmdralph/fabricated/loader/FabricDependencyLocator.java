package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.core.Pipeline;
import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.archive.SafeArchive;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.cache.TranslationCache;
import io.github.cmdralph.fabricated.core.config.FabricatedConfig;
import io.github.cmdralph.fabricated.core.json.JsonException;
import io.github.cmdralph.fabricated.core.report.Report;
import io.github.cmdralph.fabricated.core.resolve.FabricApiPlan;
import io.github.cmdralph.fabricated.core.translate.LinkageAnalysis;
import io.github.cmdralph.fabricated.core.translate.PackageGuard;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.jarmoduleinfo.JarModuleInfo;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IDependencyLocator;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

/**
 * Runs Fabricated's discovery pipeline once all NeoForge mods are known, then hands every translated
 * Fabric mod to NeoForge as an ordinary mod file: the (remapped) jar overlaid with the generated
 * metadata, with split-package conflicts filtered out. Anything that goes wrong here disables Fabric
 * support for this launch with a warning; it never stops the game.
 */
public final class FabricDependencyLocator implements IDependencyLocator {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final String GAME_MOD_ID = "fabricated";
	private static final String GAME_MOD_RESOURCE = "game-mod.jar";
	private static final String MESSAGE = "§e[Fabricated]§r {0}";

	@Override
	public void scanMods(List<IModFile> loadedMods, IDiscoveryPipeline pipeline) {
		if (FabricatedState.get() != null) return;
		long start = System.nanoTime();
		try {
			run(loadedMods, pipeline);
			LOGGER.info("Fabricated discovery finished in {} ms", (System.nanoTime() - start) / 1_000_000);
		} catch (Throwable t) {
			if (t instanceof VirtualMachineError vme && !(t instanceof StackOverflowError)) throw vme;
			LOGGER.error("Fabricated failed during discovery; Fabric mods will not be loaded this launch", t);
			pipeline.addIssue(ModLoadingIssue.warning(MESSAGE,
					"Fabric mod support failed to start and is disabled for this launch: " + t + ". See logs/latest.log.").withCause(t));
		}
	}

	private void run(List<IModFile> loadedMods, IDiscoveryPipeline pipeline) throws IOException, JsonException {
		FabricatedInfo info = FabricatedInfo.get();
		FMLLoader fml = FMLLoader.getCurrent();
		String mcVersion = fml.getVersionInfo().mcVersion();
		String neoVersion = fml.getVersionInfo().neoForgeVersion();
		List<Path> fabricJars = FabricCandidateLocator.claimed();

		if (!info.supports(mcVersion)) {
			pipeline.addIssue(ModLoadingIssue.warning(MESSAGE, "This Fabricated build is for Minecraft " + info.minecraftVersion()
					+ " but the game is " + mcVersion + ". Fabric mods are not loaded; install the matching Fabricated build."));
			return;
		}

		Path gameDir = fml.getGameDir();
		Path logsDir = gameDir.resolve("logs");
		boolean client = fml.getDist().isClient();

		IModFile minecraft = find(loadedMods, "minecraft")
				.orElseThrow(() -> new IllegalStateException("NeoForge did not provide the minecraft mod file"));
		JarContents mcContents = minecraft.getContents();
		ClassBytesSource gameClasses = name -> mcContents.readFile(name + ".class");

		FabricatedConfig config = FabricatedConfig.load(FMLPaths.CONFIGDIR.get());
		TranslationCache cache = new TranslationCache(gameDir, config.cacheEnabled(), config.verifyIntegrity());
		// Fabricated's own in-game mod (native Fabric API implementation and NeoForge event bridges).
		boolean devGameMod = find(loadedMods, GAME_MOD_ID).isPresent();
		Path gameModJar = devGameMod ? null : extractGameMod(cache);

		// Classes visible to NeoForge's non-transforming loaders (JDK and libraries such as DataFixerUpper or
		// Brigadier). Mixins targeting them never apply, so pre-flight reports them instead of letting the mod
		// fail later with a ClassCastException or a missing behaviour.
		ClassLoader platformLoader = FabricDependencyLocator.class.getClassLoader();
		ClassBytesSource untransformable = name -> {
			if (mcContents.containsFile(name + ".class")) return null;
			try (var stream = platformLoader.getResourceAsStream(name + ".class")) {
				return stream == null ? null : stream.readAllBytes();
			}
		};

		Set<String> platformPackages = new HashSet<>();
		for (IModFile file : loadedMods) {
			for (String pkg : JarModuleInfo.scanModulePackages(file.getContents())) platformPackages.add(pkg.replace('.', '/'));
		}
		for (Module module : ModuleLayer.boot().modules()) {
			ModuleDescriptor d = module.getDescriptor();
			if (d != null) d.packages().forEach(p -> platformPackages.add(p.replace('.', '/')));
		}
		ClassBytesSource nativeApi = ClassBytesSource.EMPTY;
		// Interfaces the in-game mod's mixins add to game classes (Fabric API's injected interfaces).
		Supplier<Map<String, List<String>>> nativeApiInterfaces = Map::of;
		// Closed once the pipeline is done with it, however it ends.
		SafeArchive archive = gameModJar == null ? null : SafeArchive.open(gameModJar, ArchiveLimits.DEFAULTS);
		FabricApiPlan plan;
		Pipeline.Result result;
		try {
			if (archive != null) {
				for (String name : archive.classEntryNames()) {
					int slash = name.lastIndexOf('/');
					if (slash > 0) platformPackages.add(name.substring(0, slash));
				}
				// Only needed when some linkage result isn't cached.
				nativeApiInterfaces = () -> {
					Map<String, List<String>> interfaces = new HashMap<>();
					try {
						for (String name : archive.classEntryNames()) {
							if (!name.contains("/mixin/")) continue;
							byte[] data = archive.read(name);
							if (data != null) LinkageAnalysis.mixinInterfaces(data, interfaces);
						}
					} catch (IOException e) {
						LOGGER.warn("Could not read Fabricated's own mixins: {}", e.getMessage());
					}
					return interfaces;
				};
				nativeApi = name -> archive.read(name + ".class");
			} else {
				IModFile dev = find(loadedMods, GAME_MOD_ID).orElseThrow();
				JarContents devContents = dev.getContents();
				nativeApi = name -> devContents.readFile(name + ".class");
			}

			List<Pipeline.NeoForgeMod> neoForgeMods = new ArrayList<>();
			for (IModFile file : loadedMods) {
				for (IModInfo mod : file.getModInfos()) {
					if (mod.getModId().equals("minecraft")) continue;
					neoForgeMods.add(new Pipeline.NeoForgeMod(mod.getModId(), mod.getVersion().toString(), mod.getDisplayName()));
				}
			}

			byte[] planJson = FabricatedInfo.readOptional("fabric-api-plan.json");
			if (planJson == null) throw new IllegalStateException("This Fabricated build is missing its fabric-api-plan.json");
			plan = FabricApiPlan.parse(new String(planJson, StandardCharsets.UTF_8));
			Pipeline.Input input = new Pipeline.Input(gameDir, FMLPaths.CONFIGDIR.get(), config, cache, fabricJars, mcVersion, neoVersion,
					client, info.namespace(), runtimeMixinVersion(info), info.mixinLowest(), info.fabricatedVersion(), !fml.isProduction(), gameClasses, untransformable,
					platformPackages, plan, nativeApi, nativeApiInterfaces, neoForgeMods,
					(cache, mojangPath) -> IntermediaryMappings.load(cache, mcVersion, client, mojangPath),
					fml.getProgramArgs().getArguments());

			result = Pipeline.run(input);
		} finally {
			if (archive != null) archive.close();
		}
		result.runtime().install();
		FabricatedState state = new FabricatedState(result, logsDir);
		FabricatedState.set(state);

		ModFileDiscoveryAttributes attributes = ModFileDiscoveryAttributes.DEFAULT.withDependencyLocator(this);
		for (Pipeline.LoadedMod mod : result.mods()) {
			Set<String> excluded = mod.excludedPackages();
			JarContents.PathFilter filter = excluded.isEmpty() ? null : path -> !PackageGuard.isExcluded(path, excluded);
			JarContents contents = JarContents.ofFilteredPaths(List.of(
					new JarContents.FilteredPath(mod.translated().jar(), filter),
					new JarContents.FilteredPath(mod.translated().overlay())));
			Optional<IModFile> added = pipeline.addJarContent(contents, attributes, IncompatibleFileReporting.ERROR);
			if (added.isEmpty()) {
				result.report().line("NeoForge handoff", mod.translated().candidate().id() + " was not accepted by NeoForge; see logs/latest.log");
			}
		}
		if (gameModJar != null && (!result.mods().isEmpty() || !fabricJars.isEmpty())) {
			pipeline.addPath(gameModJar, attributes, IncompatibleFileReporting.ERROR);
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
		state.writeReport();
		cache.finish(config.maxUnusedLaunches());
	}

	private static Optional<IModFile> find(List<IModFile> files, String modId) {
		for (IModFile file : files) {
			for (IModInfo info : file.getModInfos()) {
				if (info.getModId().equals(modId)) return Optional.of(file);
			}
		}
		return Optional.empty();
	}

	/** Copies the bundled in-game mod into the cache (content-addressed, so it is written once). */
	private static Path extractGameMod(TranslationCache cache) throws IOException {
		byte[] data = FabricatedInfo.readOptional(GAME_MOD_RESOURCE);
		if (data == null) throw new IOException("The Fabricated jar is missing " + GAME_MOD_RESOURCE);
		Path path = cache.storeNested(data);
		if (!Files.isRegularFile(path)) throw new IOException("Could not extract " + GAME_MOD_RESOURCE);
		return path;
	}

	/** The running Mixin version, read the same way NeoForge reads it. */
	static String runtimeMixinVersion(FabricatedInfo info) {
		try {
			Class<?> fabricUtil = Class.forName("org.spongepowered.asm.mixin.FabricUtil");
			Optional<String> fromModule = Optional.ofNullable(fabricUtil.getModule().getDescriptor())
					.flatMap(ModuleDescriptor::version).map(ModuleDescriptor.Version::toString);
			if (fromModule.isPresent()) return fromModule.get();
			String fromPackage = fabricUtil.getPackage().getImplementationVersion();
			if (fromPackage != null) return fromPackage;
		} catch (ClassNotFoundException | LinkageError e) {
			LOGGER.debug("Could not determine the running Mixin version", e);
		}
		return info.mixinFallback();
	}

	@Override
	public int getPriority() {
		// Run after NeoForge's own dependency locators so every NeoForge mod is known.
		return LOWEST_SYSTEM_PRIORITY;
	}

	@Override
	public String toString() {
		return "fabricated";
	}
}
