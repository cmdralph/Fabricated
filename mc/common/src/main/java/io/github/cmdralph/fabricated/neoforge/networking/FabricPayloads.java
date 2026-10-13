package io.github.cmdralph.fabricated.neoforge.networking;

import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Fabric's rule for payloads the other side can't receive: they are not sent (NeoForge's is to throw). */
public final class FabricPayloads {
	private FabricPayloads() {
	}

	/** Whether {@code id} is a Fabric mod's payload type that the connection behind {@code listener} has no channel for. */
	public static boolean unsendable(Identifier id, ICommonPacketListener listener, boolean clientbound) {
		boolean fabric = clientbound
				? PayloadTypeRegistryImpl.CLIENTBOUND_PLAY.get(id) != null || PayloadTypeRegistryImpl.CLIENTBOUND_CONFIGURATION.get(id) != null
				: PayloadTypeRegistryImpl.SERVERBOUND_PLAY.get(id) != null || PayloadTypeRegistryImpl.SERVERBOUND_CONFIGURATION.get(id) != null;
		return fabric && !NetworkRegistry.hasChannel(listener, id);
	}
}
