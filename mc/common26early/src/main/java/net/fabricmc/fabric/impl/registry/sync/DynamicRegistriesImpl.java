package net.fabricmc.fabric.impl.registry.sync;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
	private DynamicRegistriesImpl() {
	}

	/** The data-pack registries plus the dimension registry, as upstream. */
	public static @Unmodifiable List<RegistryDataLoader.RegistryData<?>> getWorldRegistries() {
		List<RegistryDataLoader.RegistryData<?>> all = new ArrayList<>(NeoForgeRegistryInternals.worldRegistries());
		all.addAll(RegistryDataLoader.DIMENSION_REGISTRIES);
		return List.copyOf(all);
	}

	public static @Unmodifiable List<RegistryDataLoader.RegistryData<?>> getBootstrappingRegistries() {
		return NeoForgeRegistryInternals.worldRegistries();
	}

	public static <T> RegistryDataLoader.RegistryData<T> register(ResourceKey<? extends Registry<T>> key, Codec<T> serverCodec) {
		Objects.requireNonNull(key, "Registry key cannot be null");
		Objects.requireNonNull(serverCodec, "Server codec cannot be null");
		checkNew(key);
		var entry = new RegistryDataLoader.RegistryData<>(key, serverCodec, RegistryValidator.none());
		NeoForgeRegistryInternals.addWorldRegistry(entry);
		return entry;
	}

	public static <T> void addSyncedRegistry(ResourceKey<? extends Registry<T>> key, Codec<T> clientCodec, DynamicRegistries.SyncOption... options) {
		Objects.requireNonNull(key, "Registry key cannot be null");
		Objects.requireNonNull(clientCodec, "Client codec cannot be null");
		Objects.requireNonNull(options, "Options cannot be null");
		NeoForgeRegistryInternals.addSyncedRegistry(new RegistryDataLoader.RegistryData<>(key, clientCodec, RegistryValidator.none()));
	}

	private static void checkNew(ResourceKey<? extends Registry<?>> key) {
		if (NeoForgeRegistryInternals.isDataPackRegistry(key)) {
			throw new IllegalArgumentException("Dynamic registry " + key + " has already been registered!");
		}
	}
}
