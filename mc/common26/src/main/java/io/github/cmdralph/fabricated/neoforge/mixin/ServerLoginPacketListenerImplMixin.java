package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.fabric.api.networking.v1.context.PacketContextProvider;
import net.fabricmc.fabric.impl.networking.context.PacketContextImpl;
import net.fabricmc.fabric.mixin.networking.accessor.ServerLoginPacketListenerImplAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;

/**
 * Login listeners expose their connection's packet context, and fill it as Fabric API does (NET-7): the server and its
 * registries once the listener exists, the player's profile once login succeeds. Polymer reads the registries from the
 * context to send its blocks and items. Injects at the end of the constructor and at the start of
 * {@code finishLoginAndWaitForClient} only.
 */
@Mixin(ServerLoginPacketListenerImpl.class)
abstract class ServerLoginPacketListenerImplMixin implements PacketContextProvider {
	@Override
	public PacketContext getPacketContext() {
		return ((PacketContextProvider) ((ServerLoginPacketListenerImplAccessor) this).getConnection()).getPacketContext();
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void fabricated$fillPacketContext(CallbackInfo ci) {
		MinecraftServer server = ((ServerLoginPacketListenerImplAccessor) this).getServer();
		getPacketContext().set(PacketContextImpl.SERVER_INSTANCE, server);
		getPacketContext().set(PacketContextImpl.REGISTRY_ACCESS, server.registryAccess());
	}

	@Inject(method = "finishLoginAndWaitForClient", at = @At("HEAD"))
	private void fabricated$storeGameProfile(GameProfile profile, CallbackInfo ci) {
		getPacketContext().set(PacketContextImpl.GAME_PROFILE, profile);
	}
}
