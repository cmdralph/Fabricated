package net.fabricmc.fabric.impl.networking;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Fabricated implementation of Fabric's payload type registry. Fabric mods register their payload types here,
 * mostly during initialisation; Fabricated hands every type registered by then to NeoForge's payload registrar
 * ({@code RegisterPayloadHandlersEvent}), and each one registered later, which upstream accepts too, to whoever
 * {@link #lock} names (NET-3). NeoForge splits large payloads itself, so the {@code registerLarge} size limits need no
 * extra handling.
 *
 * @param <B> buffer type of the phase
 */
public final class PayloadTypeRegistryImpl<B extends FriendlyByteBuf> implements PayloadTypeRegistry<B> {
	public static final PayloadTypeRegistryImpl<FriendlyByteBuf> SERVERBOUND_CONFIGURATION = new PayloadTypeRegistryImpl<>("serverbound configuration");
	public static final PayloadTypeRegistryImpl<FriendlyByteBuf> CLIENTBOUND_CONFIGURATION = new PayloadTypeRegistryImpl<>("clientbound configuration");
	public static final PayloadTypeRegistryImpl<RegistryFriendlyByteBuf> SERVERBOUND_PLAY = new PayloadTypeRegistryImpl<>("serverbound play");
	public static final PayloadTypeRegistryImpl<RegistryFriendlyByteBuf> CLIENTBOUND_PLAY = new PayloadTypeRegistryImpl<>("clientbound play");

	private final String description;
	private final Map<Identifier, CustomPacketPayload.TypeAndCodec<? super B, ? extends CustomPacketPayload>> types = new LinkedHashMap<>();
	/** Told the id of each type registered once locked. */
	private @Nullable Consumer<Identifier> late;
	/** Everything registered, once locked: lookups (on every payload sent) then take no lock. */
	private volatile @Nullable Map<Identifier, CustomPacketPayload.TypeAndCodec<? super B, ? extends CustomPacketPayload>> frozen;

	private PayloadTypeRegistryImpl(String description) {
		this.description = description;
	}

	@Override
	public <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> register(CustomPacketPayload.Type<T> type, StreamCodec<? super B, T> codec) {
		Objects.requireNonNull(type, "Type cannot be null");
		Objects.requireNonNull(codec, "Codec cannot be null");
		CustomPacketPayload.TypeAndCodec<B, T> typeAndCodec;
		Consumer<Identifier> late;
		synchronized (this.types) {
			if (this.types.containsKey(type.id())) {
				throw new IllegalArgumentException("Packet type " + type.id() + " is already registered (" + this.description + ")");
			}
			@SuppressWarnings("unchecked") // StreamCodec<? super B, T> reads and writes any B
			StreamCodec<B, T> exact = (StreamCodec<B, T>) codec;
			typeAndCodec = new CustomPacketPayload.TypeAndCodec<>(type, exact);
			this.types.put(type.id(), typeAndCodec);
			if (this.frozen != null) this.frozen = Map.copyOf(this.types);
			late = this.late;
		}
		if (late != null) late.accept(type.id());
		return typeAndCodec;
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
		Map<Identifier, CustomPacketPayload.TypeAndCodec<? super B, ? extends CustomPacketPayload>> registered = this.frozen;
		if (registered != null) return registered.get(id);
		synchronized (this.types) {
			return this.types.get(id);
		}
	}

	/**
	 * Returns everything registered, in registration order; {@code late} is told the id of each type registered from
	 * then on.
	 */
	public Collection<CustomPacketPayload.TypeAndCodec<? super B, ? extends CustomPacketPayload>> lock(Consumer<Identifier> late) {
		synchronized (this.types) {
			this.late = late;
			this.frozen = Map.copyOf(this.types);
			return List.copyOf(this.types.values());
		}
	}

	/** Ids registered so far. */
	public java.util.Set<Identifier> ids() {
		Map<Identifier, CustomPacketPayload.TypeAndCodec<? super B, ? extends CustomPacketPayload>> registered = this.frozen;
		if (registered != null) return registered.keySet();
		synchronized (this.types) {
			return java.util.Set.copyOf(this.types.keySet());
		}
	}
}
