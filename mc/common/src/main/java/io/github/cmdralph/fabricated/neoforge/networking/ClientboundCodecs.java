package io.github.cmdralph.fabricated.neoforge.networking;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * Clientbound codecs of Fabric payload ids that use a different codec in each direction. Fabric registers a codec
 * per direction, NeoForge only one per id (the serverbound one is registered with NeoForge); the clientbound one is
 * returned from NeoForge's codec lookup by {@code NetworkRegistryMixin}, which knows the packet's direction.
 * Filled once while payloads are registered with NeoForge, read on network threads afterwards.
 */
public final class ClientboundCodecs {
	private static volatile Map<ConnectionProtocol, Map<Identifier, StreamCodec<?, ?>>> codecs = Map.of();

	private ClientboundCodecs() {
	}

	public static synchronized void put(ConnectionProtocol protocol, Identifier id, StreamCodec<?, ?> codec) {
		Map<ConnectionProtocol, Map<Identifier, StreamCodec<?, ?>>> copy = new EnumMap<>(ConnectionProtocol.class);
		codecs.forEach((p, byId) -> copy.put(p, new HashMap<>(byId)));
		copy.computeIfAbsent(protocol, p -> new HashMap<>()).put(id, codec);
		codecs = copy;
	}

	public static @Nullable StreamCodec<?, ?> get(ConnectionProtocol protocol, Identifier id) {
		Map<Identifier, StreamCodec<?, ?>> byId = codecs.get(protocol);
		return byId == null ? null : byId.get(id);
	}
}
