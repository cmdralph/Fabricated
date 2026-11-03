package io.github.cmdralph.fabricated.neoforge.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import io.github.cmdralph.fabricated.neoforge.networking.MeasuredPackets;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.neoforged.neoforge.network.filters.GenericPacketSplitter;

/**
 * NeoForge's packet splitter measures each outgoing packet by encoding it, then hands the packet itself to vanilla's
 * encoder. For a packet carrying a Fabric payload it keeps the bytes it measured, which vanilla's encoder writes in
 * place of encoding the packet a second time ({@link MeasuredPackets}, NET-10).
 */
@Mixin(value = GenericPacketSplitter.class, priority = 900)
abstract class GenericPacketSplitterMeasureMixin {
	@WrapOperation(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Ljava/util/List;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/network/codec/StreamCodec;encode(Ljava/lang/Object;Ljava/lang/Object;)V"))
	private void fabricated$keepMeasuredBytes(StreamCodec<?, ?> codec, Object buffer, Object packet, Operation<Void> original,
			@Local(argsOnly = true) ChannelHandlerContext ctx) {
		original.call(codec, buffer, packet);
		if (MeasuredPackets.carriesFabricPayload(packet)) MeasuredPackets.keep(ctx, (Packet<?>) packet, (ByteBuf) buffer);
	}

	/** What was kept for a packet the splitter doesn't hand on whole (sent in parts, or failed to encode) is released. */
	@WrapMethod(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Ljava/util/List;)V")
	private void fabricated$dropWhatIsNotHandedOn(ChannelHandlerContext ctx, Packet<?> packet, List<Object> out, Operation<Void> original) {
		boolean handedOn = false;
		try {
			original.call(ctx, packet, out);
			handedOn = out.size() == 1 && out.get(0) == packet;
		} finally {
			if (!handedOn) MeasuredPackets.drop(ctx, packet);
		}
	}
}
