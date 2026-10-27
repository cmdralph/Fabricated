package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cpw.mods.jarhandling.SecureJar;
import io.github.cmdralph.fabricated.api.internal.ApiHolder;
import io.github.cmdralph.fabricated.core.Pipeline;
import io.github.cmdralph.fabricated.core.api.FabricatedApiImpl;
import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.archive.SafeArchive;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.InstalledClasses;
import io.github.cmdralph.fabricated.core.cache.TranslationCache;
import io.github.cmdralph.fabricated.core.config.FabricatedConfig;
import io.github.cmdralph.fabricated.core.json.JsonException;
import io.github.cmdralph.fabricated.core.metadata.ModIds;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding;
import io.github.cmdralph.fabricated.core.remap.MappingException;
import io.github.cmdralph.fabricated.core.remap.OfficialClasses;
import io.github.cmdralph.fabricated.core.report.Report;
import io.github.cmdralph.fabricated.core.resolve.FabricApiPlan;
import io.github.cmdralph.fabricated.core.resolve.PlatformLibraries;
import io.github.cmdralph.fabricated.core.translate.LinkageAnalysis;
import io.github.cmdralph.fabricated.core.translate.PackageGuard;
import io.github.cmdralph.fabricated.core.translate.PlatformDescriptor;
import io.github.cmdralph.fabricated.core.translate.TranslatedMod;
import io.github.cmdralph.fabricated.core.translate.TranslationEnvironment.Namespace;
import io.github.cmdralph.fabricated.fabricloader.version.VersionParser;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.moddiscovery.AbstractModProvider;
import net.minecraftforge.fml.loading.moddiscovery.ModFileParser;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.IDependencyLocator;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.ModFileFactory;

/**
 * Fabricated's dependency locator on Forge (docs/PORTING.md §4.1), which Forge runs after every mod locator with the
 * files found so far, before its own jar-in-jar locator. It runs Fabricated's discovery pipeline over the Fabric jars in
 * the mods folder and hands Forge every translated Fabric mod as a mod file, the (remapped) jar overlaid with the
 * generated descriptor ({@link PlatformDescriptor#FORGE}), and Fabricated's in-game mod nested in its jar. Forge's only
 * error path here stops the game, so anything that goes wrong is logged and Fabric support is off for the launch; the
 * Fabric mods that weren't loaded are in the log, Fabricated's report and Forge's loading warnings (which the in-game mod
 * adds, as Forge reads them after loading: on the server in its log, on the client on its warnings screen).
 * In a development workspace, where the in-game mod is a mod of its own, it isn't handed over again.
 */
public final class FabricDependencyLocator extends AbstractModProvider implements IDependencyLocator {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final Pattern VERSION_IN_NAME = Pattern.compile("\\d+\\.\\d+\\.\\d+");

	@Override
	public String name() {
		return ModIds.FABRICATED;
	}

	@Override
	public List<IModFile> scanMods(Iterable<IModFile> loadedMods) {
		if (FabricatedState.get() != null) return List.of();
		List<IModFile> handed = new ArrayList<>();
		long start = System.nanoTime();
		try {
			run(loadedMods, handed);
			LOGGER.info("Fabricated discovery finished in {} ms", (System.nanoTime() - start) / 1_000_000);
		} catch (Throwable t) {
			if (t instanceof VirtualMachineError vme && !(t instanceof StackOverflowError)) throw vme;
			// The state is published just before the first Fabric mod is handed to Forge.
			if (FabricatedState.get() != null) {
				LOGGER.error("Fabricated failed after handing Fabric mods to Forge; some may not work this launch", t);
				return handed;
			}
			LOGGER.error("Fabricated failed during discovery; Fabric mods will not be loaded this launch", t);
			return List.of();
		}
		return handed;
	}

	private void run(Iterable<IModFile> loadedMods, List<IModFile> handed) throws IOException, JsonException {
		FabricatedInfo info = FabricatedInfo.get();
		String mcVersion = FMLLoader.versionInfo().mcVersion();
		String forgeVersion = FMLLoader.versionInfo().forgeVersion();
		if (!info.supports(mcVersion)) {
			LOGGER.warn("This Fabricated build is for Minecraft {} but the game is {}. Fabric mods are not loaded; install the matching Fabricated build.",
					info.minecraftVersion(), mcVersion);
			return;
		}

		Path gameDir = FMLPaths.GAMEDIR.get();
		Path logsDir = gameDir.resolve("logs");
		boolean client = FMLLoader.getDist().isClient();
		List<Path> fabricJars = FabricJars.in(FMLPaths.MODSDIR.get());

		IModFile minecraft = find(loadedMods, "minecraft")
				.orElseThrow(() -> new IllegalStateException("Forge did not provide the minecraft mod file"));
		SecureJar mcJar = minecraft.getSecureJar();
		// Forge's own classes are the platform's too: the extension interfaces it gives game classes (whose default methods
		// an interface a Fabric mod adds may collide with, CORPUS-11) are in its jar, when it is a file of its own.
		SecureJar forgeJar = find(loadedMods, "forge").filter(file -> file != minecraft).map(IModFile::getSecureJar).orElse(null);
		ClassBytesSource gameClasses = name -> {
			byte[] data = ForgeMods.read(mcJar, name + ".class");
			return data != null || forgeJar == null ? data : ForgeMods.read(forgeJar, name + ".class");
		};

		FabricatedConfig config = FabricatedConfig.load(FMLPaths.CONFIGDIR.get());
		TranslationCache cache = new TranslationCache(gameDir, config.cacheEnabled(), config.verifyIntegrity());
		// Fabricated's own in-game mod: loaded from the development workspace, or extracted from this jar when there is
		// anything Fabric to load.
		Optional<IModFile> devGameMod = find(loadedMods, ModIds.FABRICATED);
		Path gameModJar = devGameMod.isPresent() || fabricJars.isEmpty() ? null : NestedJars.extract(NestedJars.GAME_MOD, cache);
		List<Path> fabricatedFiles = gameModJar != null ? List.of(gameModJar) : devGameMod.map(file -> List.of(file.getFilePath())).orElse(List.of());

		// Classes visible to Forge's non-transforming layers (the JDK, and libraries such as DataFixerUpper or Brigadier).
		// Mixins targeting them never apply, so pre-flight reports them instead of letting the mod fail later.
		ClassLoader platformLoader = FabricDependencyLocator.class.getClassLoader();
		ClassBytesSource untransformable = name -> {
			if (ForgeMods.contains(mcJar, name + ".class")) return null;
			try (var stream = platformLoader.getResourceAsStream(name + ".class")) {
				return stream == null ? null : stream.readAllBytes();
			}
		};

		// Packages other modules own, which Fabric mods mustn't split (only needed when there are Fabric mods): the mod
		// files', and those of Forge's service and boot layers, which the game layer reads first.
		Set<String> platformPackages = new HashSet<>();
		if (!fabricJars.isEmpty()) {
			for (IModFile file : loadedMods) {
				for (String pkg : file.getSecureJar().getPackages()) platformPackages.add(pkg.replace('.', '/'));
			}
			for (ModuleLayer layer : layersOf(FabricDependencyLocator.class)) {
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
		FabricApiPlan plan;
		Pipeline.Result result;
		// Vanilla's game jar, which pre-flight reads to tell where Forge's patches moved an injection point (VANILLA-1).
		List<SafeArchive> vanillaJars = new ArrayList<>();
		// Closed once the pipeline is done with it, however it ends.
		try (SafeArchive archive = gameModJar == null ? null : SafeArchive.open(gameModJar, ArchiveLimits.DEFAULTS)) {
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
							if (data != null) LinkageAnalysis.mixinAdditions(data, interfaces);
						}
					} catch (IOException e) {
						LOGGER.warn("Could not read Fabricated's own mixins: {}", e.getMessage());
					}
					return interfaces;
				};
				nativeApi = name -> archive.read(name + ".class");
				fabricApi = List.of(new InstalledClasses.Jar("Fabric API", name -> archive.contains(name + ".class"), nativeApi));
			} else if (devGameMod.isPresent()) {
				SecureJar devJar = devGameMod.get().getSecureJar();
				nativeApi = name -> ForgeMods.read(devJar, name + ".class");
				fabricApi = List.of(new InstalledClasses.Jar("Fabric API", name -> ForgeMods.contains(devJar, name + ".class"), nativeApi));
			}

			// Fabric mixins may target the classes of Fabricated's Fabric API and of the Forge mods (an addon's mod).
			ForgeMods.Found forgeMods = ForgeMods.of(loadedMods, !fabricJars.isEmpty(), fabricApi);

			byte[] planJson = FabricatedInfo.readOptional("fabric-api-plan.json");
			if (planJson == null) throw new IllegalStateException("This Fabricated build is missing its fabric-api-plan.json");
			plan = FabricApiPlan.parse(new String(planJson, StandardCharsets.UTF_8));
			// Forge's installer leaves vanilla in Mojang's names among the libraries (ForgeLibraries); else NeoForge's layout.
			Optional<Path> installedVanilla = fabricJars.isEmpty() ? Optional.empty()
					: ForgeLibraries.vanillaInMojangNames(minecraft.getFilePath(), client, mcVersion, info.namespace() == Namespace.INTERMEDIARY);
			Pipeline.VanillaGame vanilla = fabricJars.isEmpty() ? Pipeline.VanillaGame.NONE
					: installedVanilla.isPresent() ? vanillaGame(installedVanilla.get(), vanillaJars)
					: VanillaGameJar.find(minecraft.getFilePath(), mcVersion, client, cache, vanillaJars);
			if (installedVanilla.isEmpty() && info.namespace() == Namespace.INTERMEDIARY && vanilla.classes() != ClassBytesSource.EMPTY) {
				// 1.21.11's game jar is obfuscated: read in Mojang's names through the mappings Forge's installer left (VANILLA-2).
				vanilla = new Pipeline.VanillaGame(new OfficialClasses(vanilla.classes(), () -> {
					try {
						return Files.newBufferedReader(IntermediaryMappings.locateMojangMappings(mcVersion, client, config.mojangMappingsPath(),
								minecraft.getFilePath()), StandardCharsets.UTF_8);
					} catch (MappingException e) {
						throw new IOException(e.getMessage(), e);
					}
				}), vanilla.location() + ", in Mojang's names through its mappings");
			}
			// Where a locator could read the game's program arguments is open (docs/PORTING.md §4.1, question 10).
			Pipeline.Input input = new Pipeline.Input(gameDir, FMLPaths.CONFIGDIR.get(), config, cache, fabricJars, mcVersion, forgeVersion,
					client, info.namespace(), runtimeMixinVersion(info), mixinExtrasVersion(loadedMods), info.mixinLowest(), info.fabricatedVersion(),
					info.build(), !FMLLoader.isProduction(), gameClasses, List.of(minecraft.getFilePath()), vanilla, fabricatedFiles, untransformable,
					platformPackages, plan, nativeApi, nativeApiInterfaces, forgeMods.mods(), forgeMods.classes(),
					(mappingsCache, mojangPath) -> IntermediaryMappings.load(mappingsCache, mcVersion, client, mojangPath, minecraft.getFilePath()),
					new String[0], PlatformDescriptor.FORGE);

			result = Pipeline.run(input);
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

		Set<String> refused = new HashSet<>();
		for (Pipeline.LoadedMod mod : result.mods()) {
			TranslatedMod translated = mod.translated();
			String id = translated.candidate().id();
			try {
				handed.add(modFile(translated, mod.packages()));
			} catch (RuntimeException e) {
				// Fabric code mustn't see it as loaded, or run its entrypoints, when Forge won't load its classes.
				LOGGER.error("Forge did not accept the translated Fabric mod {}", id, e);
				refused.add(id);
				result.runtime().removeMod(id);
				result.report().disable(id, "Forge did not accept the translated mod: " + e.getMessage(), "See logs/latest.log for Forge's reason.");
			}
		}
		if (!refused.isEmpty()) {
			// Nor may its interfaces be injected into game classes: they would fail to load, with the class missing.
			state = new FabricatedState(result.without(refused), logsDir, api);
			FabricatedState.set(state);
		}
		if (addGameMod) {
			var gameMod = createMod(gameModJar);
			if (gameMod.file() != null) {
				handed.add(gameMod.file());
			} else {
				LOGGER.error("Forge refused Fabricated's in-game mod; Fabric entrypoints will not run this launch", gameMod.ex());
			}
		}

		int quietFabricApi = 0;
		List<String> notLoaded = new ArrayList<>();
		for (Report.ModRow row : result.report().mods()) {
			if (row.status() == Report.Status.DISABLED || row.status() == Report.Status.REJECTED) {
				// A disabled Fabric API module isn't the player's mod: any mod that needs it is disabled too, with its own
				// line naming the module.
				if (plan.isFabricApiModule(row.id())) {
					quietFabricApi++;
					continue;
				}
				String text = "Fabric mod " + row.id() + (row.version().isEmpty() ? "" : " " + row.version()) + " was not loaded: " + row.detail()
						+ (row.suggestion().isEmpty() ? "" : " Fix: " + row.suggestion());
				// Logged here too: Forge's client shows its loading warnings on a screen without logging them.
				LOGGER.warn("{}", text);
				notLoaded.add(text);
			}
		}
		// Fabricated's in-game mod adds them to Forge's loading warnings.
		ForgeLoaderHooks.notLoaded(notLoaded);
		if (quietFabricApi > 0) {
			LOGGER.info("{} Fabric API module(s) can't run on Forge yet and were skipped; see logs/{}", quietFabricApi, Report.FILE_NAME);
		}
		// The player's mods that loaded without some of their mixins (LEAVE-1).
		List<String> partial = new ArrayList<>();
		for (Pipeline.LoadedMod mod : result.mods()) {
			String id = mod.translated().candidate().id();
			if (!plan.isFabricApiModule(id) && mod.translated().findings().stream().anyMatch(f -> f.severity() == PreflightFinding.Severity.LEFT_OUT)) {
				partial.add(id);
			}
		}
		if (!partial.isEmpty()) {
			LOGGER.warn("{} Fabric mod(s) loaded without some of their mixins, as Forge's code differs where they hook in, so the features"
					+ " those hook in for don't work: {}. See logs/{}", partial.size(), String.join(", ", partial), Report.FILE_NAME);
		}
		LOGGER.info("Fabricated handed Forge {} translated Fabric mod(s){}", result.mods().size() - refused.size(), addGameMod ? " and its in-game mod" : "");
		// The pipeline reported the cache's notes so far; saving its index may add one.
		int reported = cache.notes().size();
		cache.finish(config.maxUnusedLaunches());
		for (String note : cache.notes().subList(reported, cache.notes().size())) result.report().line("Cache", note);
		state.writeReport();
	}

	/**
	 * A translated mod as a Forge mod file: its jar with the overlay last, whose files win, and which gives the module its
	 * manifest (Mixin reads the {@code MixinConfigs} the descriptor puts there) and the file its path. Other mods' part of
	 * a package split with this one come first (one module has the whole package), and the files the package guard
	 * leaves out are hidden, as is the jar-in-jar list Forge would act on ({@link FabricJars#hiddenFromForge}).
	 */
	private IModFile modFile(TranslatedMod translated, PackageGuard.Decision packages) {
		List<Path> paths = new ArrayList<>();
		Map<String, Set<String>> borrowed = new HashMap<>();
		packages.borrowed().forEach((jar, files) -> {
			Path path = jar.toAbsolutePath().normalize();
			paths.add(path);
			borrowed.put(baseName(path), files);
		});
		paths.add(translated.jar().toAbsolutePath().normalize());
		paths.add(translated.overlay().toAbsolutePath().normalize());
		// The union file system's filter gets a path relative to one of the jars (a directory's ending in '/') and that jar.
		BiPredicate<String, String> filter = (path, base) -> {
			if (FabricJars.hiddenFromForge(path)) return false;
			Set<String> files = borrowed.get(base);
			if (files != null) return path.endsWith("/") || files.contains(path);
			return !packages.hides(path);
		};
		String id = translated.neoForgeId();
		String version = translated.candidate().metadata().getVersion().getFriendlyString();
		SecureJar jar = SecureJar.from(secureJar -> new FabricJarMetadata(id, version, secureJar), filter, paths.toArray(Path[]::new));
		IModFile file = ModFileFactory.FACTORY.build(jar, this, ModFileParser::modsTomlParser);
		if (file.getModInfos().isEmpty()) throw new IllegalStateException("its generated mods.toml declares no mod");
		return file;
	}

	/** How the union file system names a jar to its filter: its path, '/'-separated, without a leading '/'. */
	private static String baseName(Path jar) {
		String name = jar.toString().replace('\\', '/');
		return name.length() > 1 && name.startsWith("/") ? name.substring(1) : name;
	}

	/** Vanilla's classes from the game jar Forge's installer left in Mojang's names, its archive added to {@code opened}. */
	private static Pipeline.VanillaGame vanillaGame(Path jar, List<SafeArchive> opened) {
		try {
			SafeArchive archive = SafeArchive.open(jar, ArchiveLimits.DEFAULTS);
			opened.add(archive);
			if (!archive.contains("net/minecraft/server/MinecraftServer.class")) return new Pipeline.VanillaGame(ClassBytesSource.EMPTY, "not found: " + jar + " isn't a game jar");
			return new Pipeline.VanillaGame(name -> archive.read(name + ".class"), jar + " (Forge's installed game, in Mojang's names)");
		} catch (IOException e) {
			return new Pipeline.VanillaGame(ClassBytesSource.EMPTY, "not found: " + jar + " can't be read: " + e.getMessage());
		}
	}

	/** The module layers a class's layer reads, itself included; the boot layer when the class is in none. */
	private static Set<ModuleLayer> layersOf(Class<?> cls) {
		ModuleLayer own = cls.getModule().getLayer();
		Set<ModuleLayer> layers = new LinkedHashSet<>();
		Deque<ModuleLayer> pending = new ArrayDeque<>(List.of(own == null ? ModuleLayer.boot() : own));
		while (!pending.isEmpty()) {
			ModuleLayer layer = pending.pop();
			if (layers.add(layer)) pending.addAll(layer.parents());
		}
		return layers;
	}

	private static Optional<IModFile> find(Iterable<IModFile> files, String modId) {
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

	/** The running Mixin version: Forge runs upstream Mixin, whose module or package gives it. */
	static String runtimeMixinVersion(FabricatedInfo info) {
		String version = classVersion("org.spongepowered.asm.launch.MixinBootstrap");
		return version != null ? version : info.mixinFallback();
	}

	/** The version of the module, else the package, a class on Forge's own layers is in; null when unknown. */
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
	 * The MixinExtras version Forge runs: the newest that a discovered file nests (Forge nests {@code mixinextras-forge}
	 * in its own jar, and its jar-in-jar locator adds the newest nested copy once a file declares mixin configs) or is
	 * (its mod metadata, else its manifest, else its file name); else from its classes' module; null when not found.
	 */
	static String mixinExtrasVersion(Iterable<IModFile> files) {
		String newest = null;
		for (IModFile file : files) {
			SecureJar jar = file.getSecureJar();
			String version = null;
			try {
				byte[] jarJar = ForgeMods.read(jar, "META-INF/jarjar/metadata.json");
				if (jarJar != null) version = PlatformLibraries.nestedMixinExtras(new String(jarJar, StandardCharsets.UTF_8));
			} catch (IOException | JsonException e) {
				LOGGER.debug("Could not read the nested jars of {}", file.getFileName(), e);
			}
			if (version == null && ForgeMods.contains(jar, "com/llamalad7/mixinextras/MixinExtrasBootstrap.class")) version = ownVersion(file);
			if (version != null && (newest == null || newer(version, newest))) newest = version;
		}
		if (newest != null) return newest;
		// Not a discovered file: a library on Forge's own layers.
		return classVersion("com.llamalad7.mixinextras.MixinExtrasBootstrap");
	}

	private static String ownVersion(IModFile file) {
		for (IModInfo mod : file.getModInfos()) {
			if (mod.getModId().startsWith("mixinextras")) return mod.getVersion().toString();
		}
		String fromManifest = file.getSecureJar().moduleDataProvider().getManifest().getMainAttributes().getValue("Implementation-Version");
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
	public String toString() {
		return ModIds.FABRICATED;
	}
}
