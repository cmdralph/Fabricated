package io.github.cmdralph.fabricated.neoforge;

import io.github.cmdralph.fabricated.loader.FabricatedState;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only hooks. Only referenced after checking that the physical side is the client. */
final class ClientSupport {
	private ClientSupport() {
	}

	static void init(FabricatedState state) {
		NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, event -> FabricatedCommands.register(event.getDispatcher(), state, true));
	}

	/** Fabric's {@code getGameInstance()} on the client is the {@link Minecraft} instance. */
	static Object gameInstance() {
		return Minecraft.getInstance();
	}
}
