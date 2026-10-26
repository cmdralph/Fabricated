package net.fabricmc.fabric.impl.event.interaction;

import org.jspecify.annotations.Nullable;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * The packet listener of Fabric's {@code FakePlayer} on 1.21.11 (Fabricated's version of upstream's class): drops
 * every packet. As NeoForge's own fake player connection, it ignores handshake listeners.
 */
public final class FakePlayerNetworkHandler extends ServerGamePacketListenerImpl {
	private static final Connection FAKE_CONNECTION = new FakeConnection();

	public FakePlayerNetworkHandler(ServerPlayer player) {
		super(player.level().getServer(), FAKE_CONNECTION, player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
	}

	@Override
	public void send(Packet<?> packet, @Nullable ChannelFutureListener listener) {
	}

	private static final class FakeConnection extends Connection {
		private FakeConnection() {
			super(PacketFlow.CLIENTBOUND);
		}

		@Override
		public void setListenerForServerboundHandshake(PacketListener listener) {
		}
	}
}
