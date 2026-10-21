package io.github.cmdralph.fabricated.testmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 26.x part of the CI test mod: exercises Fabricated's native fabric-networking-api-v1. */
public final class NetworkingTest implements ModInitializer {
	private static boolean lateRegistered;

	/** A tiny payload sent in both directions. */
	public record Ping(int value) implements CustomPacketPayload {
		public static final Type<Ping> TYPE = new Type<>(Identifier.fromNamespaceAndPath("fabricated_testmod", "ping"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Ping> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Ping::value, Ping::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A payload whose type is registered as the server starts, after NeoForge's registration event (NET-3). */
	public record LatePing(int value) implements CustomPacketPayload {
		public static final Type<LatePing> TYPE = new Type<>(Identifier.fromNamespaceAndPath("fabricated_testmod", "late_ping"));
		public static final StreamCodec<RegistryFriendlyByteBuf, LatePing> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, LatePing::value, LatePing::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.serverboundPlay().register(Ping.TYPE, Ping.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Ping.TYPE, Ping.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Ping.TYPE, (payload, context) -> {
			if (payload.value() == MenuTest.OPEN_REQUEST) MenuTest.open(context.player());
			else context.responseSender().sendPacket(new Ping(payload.value() + 1));
		});
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> TestMod.LOGGER.info("FABRICATED-TEST networking join event"));
		if (ServerPlayNetworking.getGlobalReceivers().contains(Ping.TYPE.id())) {
			TestMod.LOGGER.info("FABRICATED-TEST networking registration OK");
		}
		// As Ledger registers its payload types: as the server starts, which upstream accepts.
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			if (lateRegistered) return; // a second world in the same game
			lateRegistered = true;
			PayloadTypeRegistry.serverboundPlay().register(LatePing.TYPE, LatePing.CODEC);
			PayloadTypeRegistry.clientboundPlay().register(LatePing.TYPE, LatePing.CODEC);
			ServerPlayNetworking.registerGlobalReceiver(LatePing.TYPE, (payload, context) -> context.responseSender().sendPacket(new LatePing(payload.value() + 1)));
			TestMod.LOGGER.info("FABRICATED-TEST late payload type registration OK");
		});
	}
}
