package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.fabric.api.networking.v1.context.PacketContextProvider;
import net.minecraft.server.level.ServerPlayer;

/** Players expose their connection's packet context, as in Fabric API. */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin implements PacketContextProvider {
	@Override
	public PacketContext getPacketContext() {
		return ((PacketContextProvider) ((ServerPlayer) (Object) this).connection).getPacketContext();
	}
}
