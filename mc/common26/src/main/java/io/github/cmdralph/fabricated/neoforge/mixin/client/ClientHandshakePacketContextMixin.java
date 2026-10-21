package io.github.cmdralph.fabricated.neoforge.mixin.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.fabricmc.fabric.api.networking.v1.context.PacketContextProvider;
import net.fabricmc.fabric.impl.networking.context.PacketContextImpl;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.login.ClientboundLoginFinishedPacket;

/** The client's side of filling the packet context (NET-7): the player's profile once login succeeds, as in Fabric API. */
@Mixin(ClientHandshakePacketListenerImpl.class)
abstract class ClientHandshakePacketContextMixin {
	@Shadow
	@Final
	private Connection connection;

	@Inject(method = "handleLoginFinished", at = @At("HEAD"))
	private void fabricated$storeGameProfile(ClientboundLoginFinishedPacket packet, CallbackInfo ci) {
		((PacketContextProvider) connection).getPacketContext().set(PacketContextImpl.GAME_PROFILE, packet.gameProfile());
	}
}
