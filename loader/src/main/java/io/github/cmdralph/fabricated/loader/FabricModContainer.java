package io.github.cmdralph.fabricated.loader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.neoforgespi.language.IModInfo;

/**
 * NeoForge container for one Fabric mod. Fabric mods have no constructor-based entrypoint and do not
 * subscribe to NeoForge's mod bus, so the container only provides an (empty) mod bus and tells the
 * Fabric Loader runtime which class loader holds the mod's classes.
 */
public final class FabricModContainer extends ModContainer {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private final IEventBus eventBus;

	FabricModContainer(IModInfo info, ModuleLayer layer, FabricatedState state) {
		super(info);
		this.eventBus = BusBuilder.builder()
				.setExceptionHandler((bus, event, listeners, index, throwable) ->
						LOGGER.error("Exception in mod bus listener of Fabric mod {} for {}", info.getModId(), event, throwable))
				.markerType(IModBusEvent.class)
				.allowPerPhasePost()
				.build();
		String moduleName = info.getOwningFile().getFile().getId();
		ClassLoader loader = layer.findModule(moduleName).map(Module::getClassLoader)
				.orElseThrow(() -> new IllegalStateException("Module " + moduleName + " for Fabric mod " + info.getModId() + " is not in the game layer"));
		state.fabricContainer(info.getModId()).ifPresent(container -> state.runtime().setClassLoader(container, loader));
	}

	@Override
	public IEventBus getEventBus() {
		return eventBus;
	}
}
