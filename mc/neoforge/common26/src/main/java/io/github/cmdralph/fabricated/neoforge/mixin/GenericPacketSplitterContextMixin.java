package io.github.cmdralph.fabricated.neoforge.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import io.netty.channel.ChannelHandlerContext;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.fabric.api.networking.v1.context.PacketContextProvider;
import net.fabricmc.fabric.impl.networking.context.PacketContextImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.neoforged.neoforge.network.filters.GenericPacketSplitter;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.payload.SplitPacketPayload;

/**
 * NeoForge's packet splitter codes packets outside vanilla's encoder and decoder: it encodes each outgoing packet
 * first, to measure it, and decodes the packets it reassembles. Both run with the connection's {@link PacketContext}
 * bound, as {@code PacketEncoderContextMixin} and {@code PacketDecoderContextMixin} code the rest (NET-7): Polymer's
 * ingredient codec read no context while the splitter measured Fabric's recipe sync, and the connection closed.
 */
@Mixin(value = GenericPacketSplitter.class, priority = 500)
abstract class GenericPacketSplitterContextMixin {
	@Unique
	private PacketContext fabricated$context;

	@WrapMethod(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Ljava/util/List;)V")
	private void fabricated$encodeWithPacketContext(ChannelHandlerContext ctx, Packet<?> packet, List<Object> out, Operation<Void> original) {
		PacketContext context = fabricated$contextOf(ctx.pipeline().get(Connection.class));
		if (context == null) {
			original.call(ctx, packet, out);
		} else {
			ScopedValue.where(PacketContextImpl.VALUE, context).run(() -> original.call(ctx, packet, out));
		}
	}

	/**
	 * The splitter's payload handler, which decodes a packet once its last part arrives. Its signature is NeoForge's
	 * {@code IPayloadHandler}'s; the decoding method's return type differs between NeoForge builds.
	 */
	@WrapMethod(method = "handle")
	private static void fabricated$decodeWithPacketContext(SplitPacketPayload payload, IPayloadContext payloadContext, Operation<Void> original) {
		if (payloadContext.connection() instanceof PacketContextProvider provider) {
			ScopedValue.where(PacketContextImpl.VALUE, provider.getPacketContext()).run(() -> original.call(payload, payloadContext));
		} else {
			original.call(payload, payloadContext);
		}
	}

	/** The connection's context; a splitter belongs to one connection's pipeline. */
	@Unique
	private PacketContext fabricated$contextOf(Connection connection) {
		PacketContext context = fabricated$context;
		if (context == null && connection instanceof PacketContextProvider provider) {
			fabricated$context = context = provider.getPacketContext();
		}
		return context;
	}
}
