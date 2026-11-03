package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import io.github.cmdralph.fabricated.neoforge.networking.MeasuredPackets;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;

/**
 * Vanilla's encoder writes the bytes NeoForge's packet splitter measured for a packet carrying a Fabric payload, in
 * place of encoding it a second time, so its codec runs once, as on Fabric ({@link MeasuredPackets}, NET-10). The rest
 * of the encoder (its profiling, its handling of a protocol's last packet) runs as it does for any packet.
 */
@Mixin(value = PacketEncoder.class, priority = 900)
abstract class PacketEncoderMeasuredMixin {
	@WrapOperation(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Lio/netty/buffer/ByteBuf;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/network/codec/StreamCodec;encode(Ljava/lang/Object;Ljava/lang/Object;)V"))
	private void fabricated$writeMeasuredBytes(StreamCodec<?, ?> codec, Object output, Object packet, Operation<Void> original,
			@Local(argsOnly = true) ChannelHandlerContext ctx) {
		ByteBuf measured = MeasuredPackets.carriesFabricPayload(packet) ? MeasuredPackets.take(ctx, (Packet<?>) packet) : null;
		if (measured == null) {
			original.call(codec, output, packet);
			return;
		}
		try {
			((ByteBuf) output).writeBytes(measured);
		} finally {
			measured.release();
		}
	}
}
