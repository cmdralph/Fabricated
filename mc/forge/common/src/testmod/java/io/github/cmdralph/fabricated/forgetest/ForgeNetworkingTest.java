package io.github.cmdralph.fabricated.forgetest;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The forge test mod's part for fabric-networking-api-v1 (with Fabric API's jar only), which Fabricated passes through on
 * Forge: the client sends a {@link Ping} as it joins, the server answers with the value plus one, and the client logs
 * the answer ({@link ForgeNetworkingClient}). The payload types are registered per version ({@link ForgePayloadTypes}).
 */
public final class ForgeNetworkingTest {
	/** The value the client sends; the server answers with the next one. */
	static final int SENT = 41;

	/** A tiny payload sent in both directions. */
	public record Ping(int value) implements CustomPacketPayload {
		public static final Type<Ping> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ForgeTestMod.MOD_ID, "ping"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Ping> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Ping::value, Ping::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private ForgeNetworkingTest() {
	}

	/** From the main entrypoint, as Fabric mods register their payloads and receivers; only with Fabric API's networking module. */
	public static void register() {
		ForgePayloadTypes.register();
		ServerPlayNetworking.registerGlobalReceiver(Ping.TYPE, (payload, context) -> context.responseSender().sendPacket(new Ping(payload.value() + 1)));
	}
}
