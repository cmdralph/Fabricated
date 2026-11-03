package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import io.github.cmdralph.fabricated.core.metadata.ModIds;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.forgespi.locating.IModFile;

/**
 * Fabricated's transformation service on Forge 61 to 66 and Forge 47, whose ModLauncher 10.0 has the same API and asks
 * for transformers after the scan too (docs/PORTING.md §4.1, F3 step 4). ModLauncher loads it from the Fabricated jar
 * in the mods folder (its service file) into the service layer, next to the locators, and asks it for transformers once
 * FML's mod scan, and so Fabricated's discovery, is done: it gives one that applies
 * {@link FabricClassTransforms#beforeMixin} to the classes discovery named (Fabric mods' classes with {@code @Environment}
 * members, and the targets of {@code inject-interface} directives), and declares their default bridges. ModLauncher's
 * transformers run before Mixin, a launch plugin, and no transformer from the mods folder runs after it: where Mixin
 * applies mixins to a class, Fabricated's Mixin extension, which the game mod registers, takes the bridges out first and
 * declares them after (CORPUS-63, as on NeoForge 21.1, {@link FabricClassTransformer}). Where the running Mixin puts
 * the classes it generates in no module, it adds a module of the game layer for them ({@link #completeScan}).
 */
public final class FabricTransformationService implements ITransformationService {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	/** The packages Mixin generates classes into: its synthetic package, and that of {@code @ModifyArgs}' argument bundles. */
	static final Set<String> MIXIN_SYNTHETIC_PACKAGES = Set.of("org.spongepowered.asm.synthetic", "org.spongepowered.asm.synthetic.args");
	private static final String MIXIN_SYNTHETIC_MODULE = "fabricated.mixin.synthetic";
	private volatile IEnvironment environment;

	@Override
	public String name() {
		return ModIds.FABRICATED;
	}

	@Override
	public void initialize(IEnvironment environment) {
		this.environment = environment;
	}

	@Override
	public void onLoad(IEnvironment env, Set<String> otherServices) {
	}

	/**
	 * How many loading errors Forge's mod list holds: Forge 66's {@code LoadingModList} answers statically, Forge 47 to
	 * 65's instance does. 0 when it can't be read.
	 */
	private static int loadingErrors() {
		try {
			Method getErrors = LoadingModList.class.getMethod("getErrors");
			Object list = Modifier.isStatic(getErrors.getModifiers()) ? null : LoadingModList.class.getMethod("get").invoke(null);
			if (list == null && !Modifier.isStatic(getErrors.getModifiers())) return 0;
			return getErrors.invoke(list) instanceof List<?> errors ? errors.size() : 0;
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.debug("Could not read Forge's loading errors", e);
			return 0;
		}
	}

	/**
	 * A module of the game layer for the packages Mixin generates classes into, where the running Mixin adds none
	 * (CORPUS-68): Forge 47's Mixin 0.8.5 doesn't, so a game class naming the class Mixin generates for a
	 * {@code @ModifyArgs} ({@code Args$N}) found no module of the layer holding its package and stopped the game
	 * ({@code NoClassDefFoundError} as the target loaded), while Mixin 0.8.7 (Forge 61 to 66) adds a module of its own for
	 * them, as here. The module has no files: ModLauncher's class loader offers a class it has no bytes for to the launch
	 * plugins, and Mixin's generates it. Only when Fabric mods are loaded, and not when a mod file already holds one of the
	 * packages, as two modules of a layer can't.
	 */
	@Override
	public List<Resource> completeScan(IModuleLayerManager layerManager) {
		if (FabricatedState.get() == null || mixinAddsSyntheticModule() || modFileHolds(MIXIN_SYNTHETIC_PACKAGES)) return List.of();
		try {
			// An empty directory of Fabricated's, and only its directories at that: the module has nothing but its packages.
			Path root = Files.createDirectories(FMLPaths.GAMEDIR.get().resolve(".fabricated").resolve("mixin-synthetic"));
			SecureJar jar = SecureJar.from(j -> new SyntheticPackages(), (path, base) -> path.endsWith("/"), root);
			LOGGER.info("Fabricated adds the module {} to the game layer for the classes Mixin generates ({}), which this Mixin puts in no module",
					MIXIN_SYNTHETIC_MODULE, String.join(", ", new TreeSet<>(MIXIN_SYNTHETIC_PACKAGES)));
			return List.of(new Resource(IModuleLayerManager.Layer.GAME, List.of(jar)));
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Fabricated could not add a module for the classes Mixin generates; a mod's @ModifyArgs fails as its target class loads", e);
			return List.of();
		}
	}

	/** The module of Mixin's generated classes: its packages, and nothing else. */
	private static final class SyntheticPackages implements JarMetadata {
		@Override
		public String name() {
			return MIXIN_SYNTHETIC_MODULE;
		}

		@Override
		public String version() {
			return null;
		}

		@Override
		public ModuleDescriptor descriptor() {
			return ModuleDescriptor.newAutomaticModule(MIXIN_SYNTHETIC_MODULE).packages(MIXIN_SYNTHETIC_PACKAGES).build();
		}
	}

	/**
	 * Whether the running Mixin adds the module for its generated classes itself: its transformation service declares
	 * {@code completeScan} from Mixin 0.8.6. True when that can't be told, which leaves it to Mixin.
	 */
	private static boolean mixinAddsSyntheticModule() {
		try {
			Class.forName("org.spongepowered.asm.launch.MixinTransformationService").getDeclaredMethod("completeScan", IModuleLayerManager.class);
			return true;
		} catch (NoSuchMethodException e) {
			return false;
		} catch (ClassNotFoundException | LinkageError | RuntimeException e) {
			LOGGER.debug("Could not tell whether Mixin adds a module for the classes it generates", e);
			return true;
		}
	}

	/**
	 * Whether a mod file of Forge's mod list holds one of the packages. Forge 66's {@code LoadingModList} answers
	 * statically, Forge 47 to 65's instance does; false when it can't be read.
	 */
	private static boolean modFileHolds(Set<String> packages) {
		try {
			Method getModFiles = LoadingModList.class.getMethod("getModFiles");
			Object list = Modifier.isStatic(getModFiles.getModifiers()) ? null : LoadingModList.class.getMethod("get").invoke(null);
			if (list == null && !Modifier.isStatic(getModFiles.getModifiers())) return false;
			if (!(getModFiles.invoke(list) instanceof List<?> infos)) return false;
			for (Object info : infos) {
				if (info.getClass().getMethod("getFile").invoke(info) instanceof IModFile file
						&& !Collections.disjoint(file.getSecureJar().getPackages(), packages)) {
					LOGGER.info("{} holds a package Mixin generates classes into, so Fabricated adds no module for them", file.getFileName());
					return true;
				}
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.debug("Could not read Forge's mod files", e);
		}
		return false;
	}

	/** The game's module layer, once ModLauncher has built it. */
	private Optional<ModuleLayer> gameLayer() {
		IEnvironment env = environment;
		return env == null ? Optional.empty() : env.findModuleLayerManager().flatMap(m -> m.getLayer(IModuleLayerManager.Layer.GAME));
	}

	@Override
	@SuppressWarnings("rawtypes")
	public List<ITransformer> transformers() {
		FabricatedState state = FabricatedState.get();
		if (state == null) return List.of();
		// With loading errors (a mod file Forge refused, a missing dependency) Forge loads only Minecraft and itself, to
		// show them: no Fabric mod is in the game, and an interface injected into a game class would fail it as it loads,
		// a crash in Forge's place (LOADERR-1).
		int errors = loadingErrors();
		if (errors > 0) {
			LOGGER.warn("Forge reports {} loading error(s) and loads only Minecraft and Forge to show them, so Fabricated leaves the game's"
					+ " classes alone; see Forge's errors above", errors);
			return List.of();
		}
		Set<String> classes = new TreeSet<>(FabricClassTransforms.classesBeforeMixin(state));
		classes.addAll(state.defaultBridges().keySet());
		if (classes.isEmpty()) return List.of();
		LOGGER.info("Fabricated transforms {} class(es) for Fabric mods before Mixin", classes.size());
		return List.of(new FabricClassTransformer(classes, this::gameLayer));
	}
}
