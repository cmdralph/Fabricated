package io.github.cmdralph.fabricated.neoforge.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import io.github.cmdralph.fabricated.neoforge.networking.NetworkAddons;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;

/** Holds Fabric's networking state for a server-side connection phase (see {@link NetworkAddons}). */
@Mixin({ServerCommonPacketListenerImpl.class, ServerLoginPacketListenerImpl.class})
public abstract class NetworkAddonHolderMixin implements NetworkAddons.Holder {
	@Unique
	private volatile @Nullable Object fabricated$networkAddon;

	@Override
	public @Nullable Object fabricated$networkAddon() {
		return fabricated$networkAddon;
	}

	@Override
	public void fabricated$setNetworkAddon(Object addon) {
		fabricated$networkAddon = addon;
	}
}
