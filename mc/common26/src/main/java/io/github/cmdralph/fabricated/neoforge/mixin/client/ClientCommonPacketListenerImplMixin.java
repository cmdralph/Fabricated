package io.github.cmdralph.fabricated.neoforge.mixin.client;

import org.spongepowered.asm.mixin.Mixin;

import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.fabric.api.networking.v1.context.PacketContextProvider;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;

/** Client configuration and play listeners expose their connection's packet context, as in Fabric API. */
@Mixin(ClientCommonPacketListenerImpl.class)
abstract class ClientCommonPacketListenerImplMixin implements PacketContextProvider {
	@Override
	public PacketContext getPacketContext() {
		return ((PacketContextProvider) ((ClientCommonPacketListenerImpl) (Object) this).getConnection()).getPacketContext();
	}
}
