package io.github.cmdralph.fabricated.neoforge.mixin;

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
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.protocol.Packet;

/** The encoding side of {@code PacketDecoderContextMixin} (NET-6). */
@Mixin(value = PacketEncoder.class, priority = 500)
abstract class PacketEncoderContextMixin {
	@Unique
	private PacketContext fabricated$context;

	@WrapMethod(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Lio/netty/buffer/ByteBuf;)V")
	private void fabricated$withPacketContext(ChannelHandlerContext ctx, Packet<?> packet, ByteBuf output, Operation<Void> original) {
		PacketContext context = fabricated$context;
		if (context == null && ctx.pipeline().get(Connection.class) instanceof PacketContextProvider provider) {
			fabricated$context = context = provider.getPacketContext();
		}
		if (context == null) {
			original.call(ctx, packet, output);
		} else {
			ScopedValue.where(PacketContextImpl.VALUE, context).run(() -> original.call(ctx, packet, output));
		}
	}
}
