package io.github.cmdralph.fabricated.forgetest;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.class_2540;
import net.minecraft.class_2960;

/**
 * The forge-legacy test mod's part for fabric-networking-api-v1 (with Fabric API's jar only), which Fabricated passes
 * through on Forge 47: the client sends a ping on the test mod's channel as it joins, the server answers with the value
 * plus one, and the client logs the answer ({@link ForgeNetworkingClient}). 1.20.1's API sends a channel's buffers
 * ({@code class_2540} is {@code FriendlyByteBuf}, {@code method_10804} {@code writeVarInt}, {@code method_10816}
 * {@code readVarInt}).
 */
public final class ForgeNetworkingTest {
	static final class_2960 PING = new class_2960(ForgeTestMod.MOD_ID, "ping");
	/** The value the client sends; the server answers with the next one. */
	static final int SENT = 41;

	private ForgeNetworkingTest() {
	}

	/** From the main entrypoint; only with Fabric API's networking module. */
	static void register() {
		ServerPlayNetworking.registerGlobalReceiver(PING, (server, player, handler, buf, responseSender) -> {
			class_2540 answer = PacketByteBufs.create();
			answer.method_10804(buf.method_10816() + 1);
			responseSender.sendPacket(PING, answer);
		});
	}
}
