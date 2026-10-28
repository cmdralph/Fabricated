package io.github.cmdralph.fabricated.loader;

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
import net.neoforged.fml.loading.LoadingModList;

/**
 * Fabricated's transformation service on NeoForge 21.1, whose FML 4 runs on ModLauncher and has no class processors:
 * ModLauncher loads it from the Fabricated jar in the mods folder (its service file) into the service layer, next to the
 * locators, and asks it for transformers once FML's mod scan, and so Fabricated's discovery, is done. It gives one that
 * applies {@link FabricClassTransforms#beforeMixin} to the classes discovery named, as the Forge platforms' does.
 * ModLauncher's transformers run before Mixin, a launch plugin; default bridges, which must come after it, are not added,
 * as no transformer from the mods folder runs after Mixin.
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

	/** The game's module layer, once ModLauncher has built it. */
	private Optional<ModuleLayer> gameLayer() {
		IEnvironment env = environment;
		return env == null ? Optional.empty() : env.findModuleLayerManager().flatMap(m -> m.getLayer(IModuleLayerManager.Layer.GAME));
	}

	@Override
	public List<? extends ITransformer<?>> transformers() {
		FabricatedState state = FabricatedState.get();
		if (state == null) return List.of();
		// With loading errors FML loads only Minecraft and NeoForge, to show them: no Fabric mod is in the game, and an
		// interface injected into a game class would fail it as it loads, a crash in NeoForge's place (LOADERR-1).
		LoadingModList mods = LoadingModList.get();
		if (mods != null && mods.hasErrors()) {
			LOGGER.warn("NeoForge reports {} loading issue(s) with errors and loads only Minecraft and NeoForge to show them, so Fabricated"
					+ " leaves the game's classes alone; see NeoForge's errors above", mods.getModLoadingIssues().size());
			return List.of();
		}
		if (!state.defaultBridges().isEmpty()) {
			LOGGER.warn("{} class(es) need default method bridges for the interfaces Fabric mods add, which Fabricated can't add on NeoForge 21.1"
					+ " yet: {}", state.defaultBridges().size(), String.join(", ", new TreeSet<>(state.defaultBridges().keySet())));
		}
		Set<String> classes = FabricClassTransforms.classesBeforeMixin(state);
		if (classes.isEmpty()) return List.of();
		LOGGER.info("Fabricated transforms {} class(es) for Fabric mods before Mixin", classes.size());
		return List.of(new FabricClassTransformer(classes, this::gameLayer));
	}
}
