package io.github.cmdralph.fabricated.forge;

import net.minecraft.client.Minecraft;

/** Client-only hooks. Only referenced after checking that the physical side is the client. */
final class ClientSupport {
	private ClientSupport() {
	}

	/** Fabric's {@code getGameInstance()} on the client is the {@link Minecraft} instance. */
	static Object gameInstance() {
		return Minecraft.getInstance();
	}
}
