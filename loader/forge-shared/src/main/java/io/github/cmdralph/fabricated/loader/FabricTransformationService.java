package io.github.cmdralph.fabricated.loader;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import io.github.cmdralph.fabricated.core.metadata.ModIds;
import net.minecraftforge.fml.loading.LoadingModList;

/**
 * Fabricated's transformation service on Forge 61 to 66 and Forge 47, whose ModLauncher 10.0 has the same API and asks
 * for transformers after the scan too (docs/PORTING.md §4.1, F3 step 4). ModLauncher loads it from the Fabricated jar
 * in the mods folder (its service file) into the service layer, next to the locators, and asks it for transformers once
 * FML's mod scan, and so Fabricated's discovery, is done: it gives one that applies
 * {@link FabricClassTransforms#beforeMixin} to the classes discovery named (Fabric mods' classes with {@code @Environment}
 * members, and the targets of {@code inject-interface} directives). ModLauncher's transformers run before Mixin, a launch
 * plugin. Default bridges, which must come after Mixin, are not added: no transformer from the mods folder runs after it.
 */
public final class FabricTransformationService implements ITransformationService {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
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
		if (!state.defaultBridges().isEmpty()) {
			LOGGER.warn("{} class(es) need default method bridges for the interfaces Fabric mods add, which Fabricated can't add on Forge yet: {}",
					state.defaultBridges().size(), String.join(", ", new TreeSet<>(state.defaultBridges().keySet())));
		}
		Set<String> classes = FabricClassTransforms.classesBeforeMixin(state);
		if (classes.isEmpty()) return List.of();
		LOGGER.info("Fabricated transforms {} class(es) for Fabric mods before Mixin", classes.size());
		return List.of(new FabricClassTransformer(classes, this::gameLayer));
	}
}
