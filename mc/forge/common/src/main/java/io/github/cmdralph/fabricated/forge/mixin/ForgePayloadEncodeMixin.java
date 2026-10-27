package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraftforge.network.ForgePayload;

/**
 * A Forge payload is written as Forge's own codec writes it, whatever codec its id has. Fabric API's networking picks a
 * payload's codec by its id, and registers its own for {@code minecraft:register}, under which Forge's channel list goes
 * too, so that codec cast Forge's payload to Fabric's and the packet failed: with the Fabric API jar, every Forge 64 to
 * 66 client lost its connection to the world it joined (NET-8).
 */
@Mixin(targets = "net.minecraft.network.protocol.common.custom.CustomPacketPayload$1")
abstract class ForgePayloadEncodeMixin {
	@WrapOperation(method = "writeCap", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/codec/StreamCodec;encode(Ljava/lang/Object;Ljava/lang/Object;)V"))
	private void fabricated$writeForgePayload(StreamCodec<?, ?> codec, Object output, Object payload, Operation<Void> encode) {
		if (payload instanceof ForgePayload forge) forge.encoder().accept((FriendlyByteBuf) output);
		else encode.call(codec, output, payload);
	}
}
