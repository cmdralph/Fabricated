package io.github.cmdralph.fabricated.forge;

import io.github.cmdralph.fabricated.loader.FabricatedState;
import net.minecraft.client.Minecraft;

/** Client-only hooks. Only referenced after checking that the physical side is the client. */
final class ClientSupport {
	private ClientSupport() {
	}

	static void init(FabricatedState state) {
		// Fabric mods' config screens, once their initializers ran (a modmenu entrypoint may use what they set up).
		state.api().afterEntrypoints(() -> ModMenuBridge.register(state));
	}

	/** Fabric's {@code getGameInstance()} on the client is the {@link Minecraft} instance. */
	static Object gameInstance() {
		return Minecraft.getInstance();
	}
}
