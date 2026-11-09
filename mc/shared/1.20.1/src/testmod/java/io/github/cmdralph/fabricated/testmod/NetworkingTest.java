package io.github.cmdralph.fabricated.testmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 1.20.1 part of the CI test mod: fabric-networking-api-v1, whose packets are written to a channel's buffer, as on later
 * versions their payloads are encoded.
 */
public final class NetworkingTest implements ModInitializer {
	/** Whether Fabricated's networking is the one running, not another mod's (Forgified Fabric API's). */
	static final boolean OWN = !TestMod.providedByAnotherMod("fabric-networking-api-v1");
	static final String ANOTHER_MODS = "fabric-networking-api-v1 is another mod's";

	/** A tiny packet sent in both directions. */
	public record Ping(int value) implements FabricPacket {
		public static final PacketType<Ping> TYPE = PacketType.create(TestCompat.id("fabricated_testmod", "ping"), buf -> new Ping(buf.readVarInt()));

		@Override
		public void write(FriendlyByteBuf buf) {
			buf.writeVarInt(value);
		}

		@Override
		public PacketType<?> getType() {
			return TYPE;
		}
	}

	/** A packet on a channel in the minecraft namespace, as BetterEnd registers its ritual update (NET-4). */
	public record VanillaPing(int value) implements FabricPacket {
		public static final PacketType<VanillaPing> TYPE = PacketType.create(TestCompat.id("minecraft", "fabricated_testmod_ping"), buf -> new VanillaPing(buf.readVarInt()));

		@Override
		public void write(FriendlyByteBuf buf) {
			buf.writeVarInt(value);
		}

		@Override
		public PacketType<?> getType() {
			return TYPE;
		}
	}

	@Override
	public void onInitialize() {
		ServerPlayNetworking.registerGlobalReceiver(Ping.TYPE, (packet, player, sender) -> {
			if (packet.value() == MenuTest.OPEN_REQUEST) MenuTest.open(player);
			else sender.sendPacket(new Ping(packet.value() + 1));
		});
		if (OWN) {
			ServerPlayNetworking.registerGlobalReceiver(VanillaPing.TYPE, (packet, player, sender) -> sender.sendPacket(new VanillaPing(packet.value() + 1)));
		}
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> TestMod.LOGGER.info("FABRICATED-TEST networking join event"));
		if (ServerPlayNetworking.getGlobalReceivers().contains(Ping.TYPE.getId())) {
			TestMod.LOGGER.info("FABRICATED-TEST networking registration OK");
		}
	}
}
