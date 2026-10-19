package io.github.cmdralph.fabricated.neoforge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.cmdralph.fabricated.core.metadata.ModIds;
import io.github.cmdralph.fabricated.core.runtime.FabricLoaderRuntime;
import io.github.cmdralph.fabricated.loader.FabricatedState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Fabric mods' config screens (their {@code modmenu} entrypoint) behind the config button of NeoForge's mod list.
 * A screen a mod registers with NeoForge itself wins. Client only.
 */
final class ModMenuBridge {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");

	private ModMenuBridge() {
	}

	static void register(FabricatedState state) {
		FabricLoaderRuntime runtime = state.runtime();
		for (FabricLoaderRuntime.Declared declared : runtime.declared("modmenu")) {
			String modId = declared.mod().getMetadata().getId();
			try {
				ModMenuApi api = runtime.instantiate(declared, ModMenuApi.class);
				register(state, modId, api.getModConfigScreenFactory());
				api.getProvidedConfigScreenFactories().forEach((id, factory) -> register(state, id, factory));
			} catch (VirtualMachineError e) {
				throw e;
			} catch (Throwable e) {
				LOGGER.warn("Could not read the config screen of Fabric mod {} ({})", modId, declared.spec().value(), e);
			}
		}
	}

	private static void register(FabricatedState state, String fabricId, ConfigScreenFactory<?> factory) {
		String neoId = ModIds.toNeoForgeId(fabricId);
		if (factory == null || !state.isFabricMod(neoId)) return;
		ModList.get().getModContainerById(neoId).ifPresent(container -> {
			if (container.getCustomExtension(IConfigScreenFactory.class).isPresent()) return;
			container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> factory.create(parent));
		});
	}
}
