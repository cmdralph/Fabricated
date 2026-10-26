package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.fabric.api.networking.v1.context.PacketContextProvider;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;

/** Server configuration and play listeners expose their connection's packet context, as in Fabric API. */
@Mixin(ServerCommonPacketListenerImpl.class)
abstract class ServerCommonPacketListenerImplMixin implements PacketContextProvider {
	@Override
	public PacketContext getPacketContext() {
		return ((PacketContextProvider) ((ServerCommonPacketListenerImpl) (Object) this).getConnection()).getPacketContext();
	}
}
