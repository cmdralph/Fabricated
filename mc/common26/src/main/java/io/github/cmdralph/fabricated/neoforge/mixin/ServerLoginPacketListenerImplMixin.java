package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.fabric.api.networking.v1.context.PacketContextProvider;
import net.fabricmc.fabric.mixin.networking.accessor.ServerLoginPacketListenerImplAccessor;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;

/** Login listeners expose their connection's packet context, as in Fabric API. */
@Mixin(ServerLoginPacketListenerImpl.class)
abstract class ServerLoginPacketListenerImplMixin implements PacketContextProvider {
	@Override
	public PacketContext getPacketContext() {
		return ((PacketContextProvider) ((ServerLoginPacketListenerImplAccessor) this).getConnection()).getPacketContext();
	}
}
