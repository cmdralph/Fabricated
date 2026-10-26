package io.github.cmdralph.fabricated.neoforge.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.fabric.api.networking.v1.context.PacketContextProvider;
import net.fabricmc.fabric.impl.networking.context.PacketContextImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketDecoder;

/**
 * Decodes a connection's packets with its {@link PacketContext} set, as upstream's networking module does, for codecs
 * reading {@code PacketContext.get()} (Polymer's, NET-6). The connection is the pipeline's handler of its type.
 */
@Mixin(value = PacketDecoder.class, priority = 500)
abstract class PacketDecoderContextMixin {
	@Unique
	private PacketContext fabricated$context;

	@WrapMethod(method = "decode")
	private void fabricated$withPacketContext(ChannelHandlerContext ctx, ByteBuf input, List<Object> out, Operation<Void> original) {
		PacketContext context = fabricated$context;
		if (context == null && ctx.pipeline().get(Connection.class) instanceof PacketContextProvider provider) {
			fabricated$context = context = provider.getPacketContext();
		}
		if (context == null) {
			original.call(ctx, input, out);
		} else {
			ScopedValue.where(PacketContextImpl.VALUE, context).run(() -> original.call(ctx, input, out));
		}
	}
}
