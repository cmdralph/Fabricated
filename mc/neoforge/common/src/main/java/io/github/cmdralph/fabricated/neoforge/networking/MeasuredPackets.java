package io.github.cmdralph.fabricated.neoforge.networking;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Packets carrying a Fabric payload are encoded once, as on Fabric (NET-10). NeoForge's packet splitter encodes each
 * outgoing packet to measure it, then hands the packet itself to vanilla's encoder, which encodes it again. A Fabric
 * mod's codec may change what it encodes: Cardinal Components API writes a sync payload's buffer with
 * {@code writeBytes(ByteBuf)}, which reads the buffer to its end, so the second encoding wrote an empty buffer and
 * the client failed to read every component sync (Trinkets on the 1.21.1 corpus client: "Failed to process a
 * synchronized task of the payload: cardinal-components:entity_sync", an {@code EOFException}).
 *
 * <p>The splitter keeps the bytes it measured for such a packet ({@code GenericPacketSplitterMeasureMixin}), and
 * vanilla's encoder writes them in its place ({@code PacketEncoderMeasuredMixin}). Both run on the connection's event
 * loop, one right after the other, for the same packet object; what is kept belongs to the connection's channel.
 * Other packets are coded as NeoForge codes them.
 */
public final class MeasuredPackets {
	private static final AttributeKey<Measured> MEASURED = AttributeKey.valueOf("fabricated:measured_packet");

	private record Measured(Packet<?> packet, ByteBuf bytes) {}

	private MeasuredPackets() {}

	/** Whether {@code packet} is a custom payload packet carrying a payload a Fabric mod registered for its direction. */
	public static boolean carriesFabricPayload(Object packet) {
		if (packet instanceof ClientboundCustomPayloadPacket clientbound) {
			CustomPacketPayload payload = clientbound.payload();
			return FabricPayloads.CLIENTBOUND_PLAY.get(payload.type().id()) != null || FabricPayloads.CLIENTBOUND_CONFIGURATION.get(payload.type().id()) != null;
		}
		if (packet instanceof ServerboundCustomPayloadPacket serverbound) {
			CustomPacketPayload payload = serverbound.payload();
			return FabricPayloads.SERVERBOUND_PLAY.get(payload.type().id()) != null || FabricPayloads.SERVERBOUND_CONFIGURATION.get(payload.type().id()) != null;
		}
		return false;
	}

	/**
	 * Keeps the bytes the splitter measured for {@code packet} (all of {@code measured}'s readable bytes, as vanilla's
	 * encoder would write them), retained until the encoder takes them or the splitter drops them.
	 */
	public static void keep(ChannelHandlerContext ctx, Packet<?> packet, ByteBuf measured) {
		Measured previous = ctx.channel().attr(MEASURED).getAndSet(new Measured(packet, measured.retainedSlice()));
		if (previous != null) previous.bytes().release();
	}

	/** The bytes measured for {@code packet}, which the caller writes and then releases; null if it wasn't measured. */
	public static ByteBuf take(ChannelHandlerContext ctx, Packet<?> packet) {
		Attribute<Measured> attribute = ctx.channel().attr(MEASURED);
		Measured measured = attribute.get();
		if (measured == null || measured.packet() != packet || !attribute.compareAndSet(measured, null)) return null;
		return measured.bytes();
	}

	/** Releases what was kept for {@code packet} when the splitter doesn't hand it on (it sent it in parts, or failed). */
	public static void drop(ChannelHandlerContext ctx, Packet<?> packet) {
		ByteBuf bytes = take(ctx, packet);
		if (bytes != null) bytes.release();
	}
}
