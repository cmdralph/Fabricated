package net.fabricmc.fabric.impl.registry.sync;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.Unmodifiable;

import com.mojang.serialization.Codec;

import io.github.cmdralph.fabricated.neoforge.registry.NeoForgeRegistryInternals;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.RegistryValidator;

/**
 * Fabricated implementation of Fabric's dynamic registries: Fabric registries are added to NeoForge's own data-pack
 * registry lists, which NeoForge uses to load them from data packs and, when synced, to send them to clients.
 * {@code SKIP_WHEN_EMPTY} has no effect: NeoForge decides itself how to sync an empty registry.
 */
public final class DynamicRegistriesImpl {
	/** The registries mods added through {@code DynamicRegistries}, as upstream (its data generation reads them). */
	public static final Set<ResourceKey<? extends Registry<?>>> FABRIC_DYNAMIC_REGISTRY_KEYS = new HashSet<>();

	private DynamicRegistriesImpl() {
	}

	/** World, dimension and reloadable registries, as upstream. */
	public static @Unmodifiable List<RegistryDataLoader.RegistryData<?>> getAllDynamicRegistries() {
		List<RegistryDataLoader.RegistryData<?>> all = new ArrayList<>(NeoForgeRegistryInternals.worldRegistries());
		all.addAll(RegistryDataLoader.DIMENSION_REGISTRIES);
		all.addAll(NeoForgeRegistryInternals.reloadableRegistries());
		return List.copyOf(all);
	}

	public static @Unmodifiable List<RegistryDataLoader.RegistryData<?>> getWorldRegistries() {
		return NeoForgeRegistryInternals.worldRegistries();
	}

	public static @Unmodifiable List<RegistryDataLoader.RegistryData<?>> getReloadableRegistries() {
		return NeoForgeRegistryInternals.reloadableRegistries();
	}

	public static <T> void register(ResourceKey<? extends Registry<T>> key, Codec<T> serverCodec) {
		Objects.requireNonNull(key, "Registry key cannot be null");
		Objects.requireNonNull(serverCodec, "Server codec cannot be null");
		checkNew(key);
		FABRIC_DYNAMIC_REGISTRY_KEYS.add(key);
		NeoForgeRegistryInternals.addWorldRegistry(new RegistryDataLoader.RegistryData<>(key, serverCodec, RegistryValidator.none()));
	}

	public static <T> void addSyncedRegistry(ResourceKey<? extends Registry<T>> key, Codec<T> clientCodec, DynamicRegistries.SyncOption... options) {
		Objects.requireNonNull(key, "Registry key cannot be null");
		Objects.requireNonNull(clientCodec, "Client codec cannot be null");
		Objects.requireNonNull(options, "Options cannot be null");
		NeoForgeRegistryInternals.addSyncedRegistry(new RegistryDataLoader.RegistryData<>(key, clientCodec, RegistryValidator.none()));
	}

	public static <T> void registerReloadable(ResourceKey<? extends Registry<T>> key, Codec<T> serverCodec) {
		Objects.requireNonNull(key, "Registry key cannot be null");
		Objects.requireNonNull(serverCodec, "Server codec cannot be null");
		checkNew(key);
		FABRIC_DYNAMIC_REGISTRY_KEYS.add(key);
		NeoForgeRegistryInternals.addReloadableRegistry(new RegistryDataLoader.RegistryData<>(key, serverCodec, RegistryValidator.none()));
	}

	private static void checkNew(ResourceKey<? extends Registry<?>> key) {
		if (NeoForgeRegistryInternals.isDataPackRegistry(key)) {
			throw new IllegalArgumentException("Dynamic registry " + key + " has already been registered!");
		}
	}
}
