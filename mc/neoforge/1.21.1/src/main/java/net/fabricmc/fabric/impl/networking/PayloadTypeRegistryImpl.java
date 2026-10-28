package net.fabricmc.fabric.impl.networking;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Fabricated implementation of Fabric's payload type registry. Fabric mods register their payload types here,
 * mostly during initialisation; Fabricated hands every type registered by then to NeoForge's payload registrar
 * ({@code RegisterPayloadHandlersEvent}), and each one registered later, which upstream accepts too, to whoever
 * {@link #lock} names (NET-3).
 *
 * @param <B> buffer type of the phase
 */
public final class PayloadTypeRegistryImpl<B extends FriendlyByteBuf> implements PayloadTypeRegistry<B> {
	public static final PayloadTypeRegistryImpl<FriendlyByteBuf> CONFIGURATION_C2S = new PayloadTypeRegistryImpl<>("configuration C2S");
	public static final PayloadTypeRegistryImpl<FriendlyByteBuf> CONFIGURATION_S2C = new PayloadTypeRegistryImpl<>("configuration S2C");
	public static final PayloadTypeRegistryImpl<RegistryFriendlyByteBuf> PLAY_C2S = new PayloadTypeRegistryImpl<>("play C2S");
	public static final PayloadTypeRegistryImpl<RegistryFriendlyByteBuf> PLAY_S2C = new PayloadTypeRegistryImpl<>("play S2C");

	private final String description;
	private final Map<ResourceLocation, CustomPacketPayload.TypeAndCodec<? super B, ? extends CustomPacketPayload>> types = new LinkedHashMap<>();
	/** Told the id of each type registered once locked. */
	private @Nullable Consumer<ResourceLocation> late;
	/** Everything registered, once locked: lookups (on every payload sent) then take no lock. */
	private volatile @Nullable Map<ResourceLocation, CustomPacketPayload.TypeAndCodec<? super B, ? extends CustomPacketPayload>> frozen;

	private PayloadTypeRegistryImpl(String description) {
		this.description = description;
	}

	@Override
	public <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> register(CustomPacketPayload.Type<T> type, StreamCodec<? super B, T> codec) {
		Objects.requireNonNull(type, "Type cannot be null");
		Objects.requireNonNull(codec, "Codec cannot be null");
		CustomPacketPayload.TypeAndCodec<B, T> typeAndCodec;
		Consumer<ResourceLocation> late;
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

	public CustomPacketPayload.@Nullable TypeAndCodec<? super B, ? extends CustomPacketPayload> get(ResourceLocation id) {
		Map<ResourceLocation, CustomPacketPayload.TypeAndCodec<? super B, ? extends CustomPacketPayload>> registered = this.frozen;
		if (registered != null) return registered.get(id);
		synchronized (this.types) {
			return this.types.get(id);
		}
	}

	/**
	 * Returns everything registered, in registration order; {@code late} is told the id of each type registered from
	 * then on.
	 */
	public Collection<CustomPacketPayload.TypeAndCodec<? super B, ? extends CustomPacketPayload>> lock(Consumer<ResourceLocation> late) {
		synchronized (this.types) {
			this.late = late;
			this.frozen = Map.copyOf(this.types);
			return List.copyOf(this.types.values());
		}
	}

	/** Ids registered so far. */
	public java.util.Set<ResourceLocation> ids() {
		Map<ResourceLocation, CustomPacketPayload.TypeAndCodec<? super B, ? extends CustomPacketPayload>> registered = this.frozen;
		if (registered != null) return registered.keySet();
		synchronized (this.types) {
			return java.util.Set.copyOf(this.types.keySet());
		}
	}
}
