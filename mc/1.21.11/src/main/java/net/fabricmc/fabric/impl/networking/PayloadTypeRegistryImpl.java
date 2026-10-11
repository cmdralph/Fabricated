package net.fabricmc.fabric.impl.networking;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntSupplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Fabricated implementation of Fabric's payload type registry. Fabric mods register their payload types here
 * during initialisation; Fabricated hands every registered type to NeoForge's payload registrar
 * ({@code RegisterPayloadHandlersEvent}), which runs after all mod initialisers. NeoForge splits large payloads
 * itself, so the {@code registerLarge} size limits need no extra handling.
 *
 * @param <B> buffer type of the phase
 */
public final class PayloadTypeRegistryImpl<B extends FriendlyByteBuf> implements PayloadTypeRegistry<B> {
	public static final PayloadTypeRegistryImpl<FriendlyByteBuf> CONFIGURATION_C2S = new PayloadTypeRegistryImpl<>("configuration C2S");
	public static final PayloadTypeRegistryImpl<FriendlyByteBuf> CONFIGURATION_S2C = new PayloadTypeRegistryImpl<>("configuration S2C");
	public static final PayloadTypeRegistryImpl<RegistryFriendlyByteBuf> PLAY_C2S = new PayloadTypeRegistryImpl<>("play C2S");
	public static final PayloadTypeRegistryImpl<RegistryFriendlyByteBuf> PLAY_S2C = new PayloadTypeRegistryImpl<>("play S2C");

	private final String description;
	private final Map<Identifier, CustomPacketPayload.TypeAndCodec<? super B, ? extends CustomPacketPayload>> types = new LinkedHashMap<>();
	private volatile boolean locked;

	private PayloadTypeRegistryImpl(String description) {
		this.description = description;
	}

	@Override
	public <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> register(CustomPacketPayload.Type<T> type, StreamCodec<? super B, T> codec) {
		Objects.requireNonNull(type, "Type cannot be null");
		Objects.requireNonNull(codec, "Codec cannot be null");
		synchronized (this.types) {
			if (this.locked) {
				throw new IllegalStateException("Cannot register " + this.description + " payload " + type.id()
						+ " after NeoForge locked its network registry; register payload types during mod initialisation");
			}
			if (this.types.containsKey(type.id())) {
				throw new IllegalArgumentException("Packet type " + type.id() + " is already registered (" + this.description + ")");
			}
			@SuppressWarnings("unchecked") // StreamCodec<? super B, T> reads and writes any B
			StreamCodec<B, T> exact = (StreamCodec<B, T>) codec;
			CustomPacketPayload.TypeAndCodec<B, T> typeAndCodec = new CustomPacketPayload.TypeAndCodec<>(type, exact);
			this.types.put(type.id(), typeAndCodec);
			return typeAndCodec;
		}
	}

	@Override
	public <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> registerLarge(CustomPacketPayload.Type<T> type, StreamCodec<? super B, T> codec, int maxPacketSize) {
		return register(type, codec);
	}

	@Override
	public <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> registerLarge(CustomPacketPayload.Type<T> type, StreamCodec<? super B, T> codec, IntSupplier maxPacketSizeSupplier) {
		return register(type, codec);
	}

	public CustomPacketPayload.@Nullable TypeAndCodec<? super B, ? extends CustomPacketPayload> get(Identifier id) {
		synchronized (this.types) {
			return this.types.get(id);
		}
	}

	/** Locks the registry and returns everything registered, in registration order. */
	public Collection<CustomPacketPayload.TypeAndCodec<? super B, ? extends CustomPacketPayload>> lock() {
		synchronized (this.types) {
			this.locked = true;
			return List.copyOf(this.types.values());
		}
	}

	/** Ids registered so far. */
	public java.util.Set<Identifier> ids() {
		synchronized (this.types) {
			return java.util.Set.copyOf(this.types.keySet());
		}
	}

	public String description() {
		return this.description;
	}
}
