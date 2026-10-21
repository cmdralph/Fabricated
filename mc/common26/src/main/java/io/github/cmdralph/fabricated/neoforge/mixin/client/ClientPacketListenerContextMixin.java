package io.github.cmdralph.fabricated.neoforge.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.fabricmc.fabric.api.networking.v1.context.PacketContextProvider;
import net.fabricmc.fabric.impl.networking.context.PacketContextImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * The client's registries go into the packet context once configuration finishes (NET-7). Fabric API stores them as
 * the configuration listener creates the play listener; the play listener is created with them and before the play
 * protocol is set up, so its constructor's end is the same point. Injects there only.
 */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerContextMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void fabricated$storeRegistries(CallbackInfo ci) {
		ClientPacketListener self = (ClientPacketListener) (Object) this;
		((PacketContextProvider) self.getConnection()).getPacketContext().set(PacketContextImpl.REGISTRY_ACCESS, self.registryAccess());
	}
}
