package io.github.cmdralph.fabricated.neoforge.mixin.client;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import io.github.cmdralph.fabricated.neoforge.networking.NetworkAddons;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;

/** Holds Fabric's networking state for a client-side connection phase (see {@link NetworkAddons}). */
@Mixin({ClientCommonPacketListenerImpl.class, ClientHandshakePacketListenerImpl.class})
public abstract class ClientNetworkAddonHolderMixin implements NetworkAddons.Holder {
	@Unique
	private @Nullable Object fabricated$networkAddon;

	@Override
	public @Nullable Object fabricated$networkAddon() {
		return fabricated$networkAddon;
	}

	@Override
	public void fabricated$setNetworkAddon(Object addon) {
		fabricated$networkAddon = addon;
	}
}
