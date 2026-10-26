package io.github.cmdralph.fabricated.testmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.fabric.api.networking.v1.context.PacketContextProvider;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 26.x part of the CI test mod: exercises Fabricated's native fabric-networking-api-v1. */
public final class NetworkingTest implements ModInitializer {
	private static boolean lateRegistered;
	private static volatile boolean contextEncodeReported;

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

	/** A payload in the minecraft namespace, which NeoForge keeps for vanilla's own, as BetterEnd registers its ritual update (NET-4). */
	public record VanillaPing(int value) implements CustomPacketPayload {
		public static final Type<VanillaPing> TYPE = new Type<>(Identifier.fromNamespaceAndPath("minecraft", "fabricated_testmod_ping"));
		public static final StreamCodec<RegistryFriendlyByteBuf, VanillaPing> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, VanillaPing::value, VanillaPing::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * A payload whose codec reads Fabric's packet context, as Polymer's do (NET-6, NET-7), and large enough for
	 * NeoForge's packet splitter to send it in parts. The client's decoder records whether it saw the context.
	 */
	public record ContextPing(byte[] data, boolean decodedWithContext) implements CustomPacketPayload {
		public static final int SIZE = 3 << 20;
		public static final Type<ContextPing> TYPE = new Type<>(Identifier.fromNamespaceAndPath("fabricated_testmod", "context_ping"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ContextPing> CODEC = StreamCodec.of((buf, ping) -> {
			if (!contextEncodeReported) {
				// The first encoding is the splitter's, which measures the packet before vanilla's encoder.
				contextEncodeReported = true;
				if (hasContext(true)) TestMod.LOGGER.info("FABRICATED-TEST packet context while encoding OK");
				else TestMod.LOGGER.error("FABRICATED-TEST packet context while encoding FAILED: {}", describe(PacketContext.get()));
			}
			buf.writeByteArray(ping.data());
		}, buf -> {
			if (!hasContext(false)) TestMod.LOGGER.error("FABRICATED-TEST packet context while decoding FAILED: {}", describe(PacketContext.get()));
			return new ContextPing(buf.readByteArray(), hasContext(false));
		});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}

		/** What of Fabric API's values the context holds, for a failure's message. */
		static String describe(PacketContext context) {
			if (context == null) return "no context";
			return "registries " + (context.get(PacketContext.REGISTRY_ACCESS) != null) + ", profile " + (context.get(PacketContext.GAME_PROFILE) != null)
					+ ", server " + (context.get(PacketContext.SERVER_INSTANCE) != null) + ", connection " + context.get(PacketContext.CONNECTION);
		}

		/** Whether the context holds what Fabric API puts in it: the registries, the profile and, on the server, the server. */
		private static boolean hasContext(boolean server) {
			PacketContext context = PacketContext.get();
			return context != null && context.get(PacketContext.REGISTRY_ACCESS) != null && context.get(PacketContext.GAME_PROFILE) != null
					&& (!server || context.get(PacketContext.SERVER_INSTANCE) != null);
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
		PayloadTypeRegistry.serverboundPlay().register(VanillaPing.TYPE, VanillaPing.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(VanillaPing.TYPE, VanillaPing.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(VanillaPing.TYPE, (payload, context) -> context.responseSender().sendPacket(new VanillaPing(payload.value() + 1)));
		PayloadTypeRegistry.clientboundPlay().register(ContextPing.TYPE, ContextPing.CODEC);
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
			TestMod.LOGGER.info("FABRICATED-TEST networking join event (packet context: {})", ContextPing.describe(((PacketContextProvider) listener).getPacketContext()));
			sender.sendPacket(new ContextPing(new byte[ContextPing.SIZE], false));
		});
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
