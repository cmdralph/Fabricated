package io.github.cmdralph.fabricated.forge;

import java.lang.invoke.MethodHandle;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.cmdralph.fabricated.core.metadata.ModIds;
import io.github.cmdralph.fabricated.core.runtime.EntrypointRunner;
import io.github.cmdralph.fabricated.core.runtime.FabricLoaderRuntime;
import io.github.cmdralph.fabricated.loader.FabricatedState;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModContainer;

/**
 * Fabric mods' config screens (their {@code modmenu} entrypoint) behind the config button of Forge's mod list, which
 * Forge 47 to 66 alike read from a mod's {@link ConfigScreenHandler.ConfigScreenFactory} extension, as NeoForge's
 * bridge puts them behind NeoForge's. A screen a mod registers with Forge itself wins. Client only.
 */
final class ModMenuBridge {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final MethodHandle CONTAINER_BY_ID = containerById();

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
			} catch (Throwable e) {
				// The constructor's own failure is wrapped; the JVM's is never the mod's.
				VirtualMachineError fatal = EntrypointRunner.fatalCause(e);
				if (fatal != null) throw fatal;
				LOGGER.warn("Could not read the config screen of Fabric mod {} ({})", modId, declared.spec().value(), e);
			}
		}
	}

	private static void register(FabricatedState state, String fabricId, ConfigScreenFactory<?> factory) {
		String forgeId = ModIds.toNeoForgeId(fabricId);
		if (factory == null || !state.isFabricMod(forgeId)) return;
		container(forgeId).ifPresent(container -> {
			if (container.getCustomExtension(ConfigScreenHandler.ConfigScreenFactory.class).isPresent()) return;
			container.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
					() -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> factory.create(parent)));
		});
	}

	/** A mod's container in Forge's mod list. */
	@SuppressWarnings("unchecked")
	private static Optional<? extends ModContainer> container(String modId) {
		try {
			return (Optional<? extends ModContainer>) CONTAINER_BY_ID.invokeExact(modId);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable e) {
			throw new IllegalStateException(e);
		}
	}

	/** {@code ModList.getModContainerById} ({@link ForgeModList#containerById}). */
	private static MethodHandle containerById() {
		try {
			return ForgeModList.containerById();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Forge's ModList has no public getModContainerById", e);
		}
	}
}
