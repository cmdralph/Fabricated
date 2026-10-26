package io.github.cmdralph.fabricated.loader;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import io.github.cmdralph.fabricated.core.metadata.ModIds;

/**
 * Fabricated's transformation service on Forge (the forge branch's, F3 step 4: Forge 47's ModLauncher 10.0 has the same
 * API and asks for transformers after the scan too; docs/PORTING.md §4.1). ModLauncher loads it from the
 * Fabricated jar in the mods folder (its service file) into the service layer, next to the locators, and asks it for
 * transformers once FML's mod scan, and so Fabricated's discovery, is done: it gives one that applies
 * {@link FabricClassTransforms#beforeMixin} to the classes discovery named (Fabric mods' classes with {@code @Environment}
 * members, and the targets of {@code inject-interface} directives). ModLauncher's transformers run before Mixin, a launch
 * plugin. Default bridges, which must come after Mixin, are not added: no transformer from the mods folder runs after it.
 */
public final class FabricTransformationService implements ITransformationService {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");

	@Override
	public String name() {
		return ModIds.FABRICATED;
	}

	@Override
	public void initialize(IEnvironment environment) {
	}

	@Override
	public void onLoad(IEnvironment env, Set<String> otherServices) {
	}

	@Override
	@SuppressWarnings("rawtypes")
	public List<ITransformer> transformers() {
		FabricatedState state = FabricatedState.get();
		if (state == null) return List.of();
		if (!state.defaultBridges().isEmpty()) {
			LOGGER.warn("{} class(es) need default method bridges for the interfaces Fabric mods add, which Fabricated can't add on Forge yet: {}",
					state.defaultBridges().size(), String.join(", ", new TreeSet<>(state.defaultBridges().keySet())));
		}
		Set<String> classes = new TreeSet<>(state.environmentClasses());
		classes.addAll(state.injectedInterfaces().keySet());
		if (classes.isEmpty()) return List.of();
		LOGGER.info("Fabricated transforms {} class(es) for Fabric mods before Mixin", classes.size());
		return List.of(new FabricClassTransformer(classes));
	}
}
