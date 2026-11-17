package io.github.cmdralph.fabricated.testmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.19.2 part of the CI test mod: fabric-networking-api-v1, whose packets Fabric API 0.77 sends as a channel's buffer,
 * read where it arrives, off the game's thread.
 */
public final class NetworkingTest implements ModInitializer {
	/** Whether Fabricated's networking is the one running, not another mod's (Forgified Fabric API's). */
	static final boolean OWN = !TestMod.providedByAnotherMod("fabric-networking-api-v1");
	static final String ANOTHER_MODS = "fabric-networking-api-v1 is another mod's";
	/** The channel of a tiny packet sent in both directions, a number. */
	static final ResourceLocation PING = TestCompat.id("fabricated_testmod", "ping");
	/** A channel in the minecraft namespace, as BetterEnd registers its ritual update (NET-4). */
	static final ResourceLocation VANILLA_PING = TestCompat.id("minecraft", "fabricated_testmod_ping");

	/** The buffer of a ping. */
	static FriendlyByteBuf ping(int value) {
		FriendlyByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(value);
		return buf;
	}

	@Override
	public void onInitialize() {
		ServerPlayNetworking.registerGlobalReceiver(PING, (server, player, handler, buf, sender) -> {
			int value = buf.readVarInt();
			server.execute(() -> {
				if (value == MenuTest.OPEN_REQUEST) MenuTest.open(player);
				else sender.sendPacket(PING, ping(value + 1));
			});
		});
		if (OWN) {
			ServerPlayNetworking.registerGlobalReceiver(VANILLA_PING, (server, player, handler, buf, sender) -> sender.sendPacket(VANILLA_PING, ping(buf.readVarInt() + 1)));
		}
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> TestMod.LOGGER.info("FABRICATED-TEST networking join event"));
		if (ServerPlayNetworking.getGlobalReceivers().contains(PING)) {
			TestMod.LOGGER.info("FABRICATED-TEST networking registration OK");
		}
	}
}
