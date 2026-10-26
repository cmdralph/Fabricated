package io.github.cmdralph.fabricated.neoforge.mixin.client;

import org.spongepowered.asm.mixin.Mixin;

import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.fabric.api.networking.v1.context.PacketContextProvider;
import net.minecraft.client.player.LocalPlayer;

/** The local player exposes its connection's packet context, as in Fabric API. */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin implements PacketContextProvider {
	@Override
	public PacketContext getPacketContext() {
		return ((PacketContextProvider) ((LocalPlayer) (Object) this).connection).getPacketContext();
	}
}
