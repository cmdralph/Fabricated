package io.github.cmdralph.fabricated.testmod;

import java.util.function.Consumer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationNetworkHandler;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.ConfigurationTask;

/**
 * 1.21.11 part of the CI test mod: a Fabric configuration task, the way {@code fabric-data-attachment-api-v1}
 * syncs attachments. The server sends a request and waits; the client answers with a different payload type under
 * the same id, and the server's handler finishes the task from the network thread. If this breaks, the player
 * never gets past configuration.
 */
public final class ConfigurationTest implements ModInitializer {
	private static final Identifier ID = Identifier.fromNamespaceAndPath("fabricated_testmod", "config_sync");

	/** Clientbound: asks the client for its answer. */
	public record Request() implements CustomPacketPayload {
		public static final Type<Request> TYPE = new Type<>(ID);
		public static final StreamCodec<FriendlyByteBuf, Request> CODEC = StreamCodec.unit(new Request());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Serverbound answer, under the same id as {@link Request} but with its own codec. */
	public record Reply(int value) implements CustomPacketPayload {
		public static final Type<Reply> TYPE = new Type<>(ID);
		public static final StreamCodec<FriendlyByteBuf, Reply> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Reply::value, Reply::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private record Task() implements ConfigurationTask {
		static final ConfigurationTask.Type KEY = new ConfigurationTask.Type(ID.toString());

		@Override
		public void start(Consumer<Packet<?>> sender) {
			sender.accept(ServerConfigurationNetworking.createS2CPacket(new Request()));
		}

		@Override
		public ConfigurationTask.Type type() {
			return KEY;
		}
	}

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.configurationS2C().register(Request.TYPE, Request.CODEC);
		PayloadTypeRegistry.configurationC2S().register(Reply.TYPE, Reply.CODEC);
		ServerConfigurationConnectionEvents.CONFIGURE.register((listener, server) -> {
			if (ServerConfigurationNetworking.canSend(listener, Request.TYPE)) {
				((FabricServerConfigurationNetworkHandler) listener).addTask(new Task());
			} else {
				TestMod.LOGGER.error("FABRICATED-TEST configuration task: the client can't receive the request");
			}
		});
		ServerConfigurationNetworking.registerGlobalReceiver(Reply.TYPE, (payload, context) -> {
			if (payload.value() == 7) TestMod.LOGGER.info("FABRICATED-TEST configuration task OK");
			((FabricServerConfigurationNetworkHandler) context.networkHandler()).completeTask(Task.KEY);
		});
	}
}
