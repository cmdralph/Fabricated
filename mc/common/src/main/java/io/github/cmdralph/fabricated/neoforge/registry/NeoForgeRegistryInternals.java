package io.github.cmdralph.fabricated.neoforge.registry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.BaseMappedRegistry;
import net.neoforged.neoforge.registries.DataPackRegistriesHooks;

/**
 * The two NeoForge internals Fabric's registry API needs and NeoForge has no public API for, reached by reflection
 * so that a NeoForge build without them degrades (with a clear error where they're used) instead of failing to boot:
 * <ul>
 *   <li>{@code BaseMappedRegistry.setSync}: NeoForge's flag for syncing a registry's ids to clients, which
 *       {@code RegistryBuilder} sets for registries created through NeoForge.</li>
 *   <li>{@code DataPackRegistriesHooks}' lists of data-pack registries. NeoForge fills them from
 *       {@code DataPackRegistryEvent.NewRegistry}, which fires before Fabric initializers run, so Fabric's
 *       {@code DynamicRegistries} adds to them directly. The field names differ between NeoForge versions.</li>
 * </ul>
 */
public final class NeoForgeRegistryInternals {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final @Nullable Method SET_SYNC = method(BaseMappedRegistry.class, "setSync", boolean.class);
	private static final @Nullable List<RegistryDataLoader.RegistryData<?>> WORLD = list("WORLD_REGISTRIES", "DATA_PACK_REGISTRIES");
	private static final @Nullable Collection<ResourceKey<? extends Registry<?>>> WORLD_KEYS = keys("WORLD_REGISTRY_KEYS");
	private static final @Nullable List<RegistryDataLoader.RegistryData<?>> SYNCED = list("SYNCED_REGISTRIES", "NETWORKABLE_REGISTRIES");
	private static final @Nullable Collection<ResourceKey<? extends Registry<?>>> SYNCED_KEYS = keys("SYNCED_REGISTRY_KEYS", "SYNCED_CUSTOM_REGISTRIES");
	private static final @Nullable List<RegistryDataLoader.RegistryData<?>> RELOADABLE = list("RELOADABLE_REGISTRIES");
	private static final @Nullable Collection<ResourceKey<? extends Registry<?>>> RELOADABLE_KEYS = keys("RELOADABLE_REGISTRY_KEYS");

	private NeoForgeRegistryInternals() {
	}

	/** @return whether NeoForge now syncs the registry */
	static boolean setSync(Registry<?> registry) {
		if (SET_SYNC == null || !(registry instanceof BaseMappedRegistry<?>)) return false;
		try {
			SET_SYNC.invoke(registry, true);
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.warn("Could not mark registry {} as synced", registry.key().identifier(), e);
			return false;
		}
	}

	/** The data-pack (world) registries NeoForge loads, Fabric-added ones included. */
	public static List<RegistryDataLoader.RegistryData<?>> worldRegistries() {
		return WORLD == null ? List.of() : List.copyOf(WORLD);
	}

	/** The reloadable registries NeoForge loads (NeoForge versions that have them). */
	public static List<RegistryDataLoader.RegistryData<?>> reloadableRegistries() {
		return RELOADABLE == null ? List.of() : List.copyOf(RELOADABLE);
	}

	public static synchronized boolean isDataPackRegistry(ResourceKey<? extends Registry<?>> key) {
		return contains(WORLD, key) || contains(RELOADABLE, key);
	}

	public static synchronized void addWorldRegistry(RegistryDataLoader.RegistryData<?> data) {
		add(WORLD, WORLD_KEYS, data, "data-pack registries");
	}

	public static synchronized void addReloadableRegistry(RegistryDataLoader.RegistryData<?> data) {
		add(RELOADABLE, RELOADABLE_KEYS, data, "reloadable registries");
	}

	/** Makes NeoForge send the registry's entries to clients, decoded there with {@code data}'s codec. */
	public static synchronized void addSyncedRegistry(RegistryDataLoader.RegistryData<?> data) {
		add(SYNCED, SYNCED_KEYS, data, "synced data-pack registries");
	}

	private static boolean contains(@Nullable List<RegistryDataLoader.RegistryData<?>> list, ResourceKey<? extends Registry<?>> key) {
		if (list == null) return false;
		for (RegistryDataLoader.RegistryData<?> data : list) {
			if (data.key().equals(key)) return true;
		}
		return false;
	}

	private static void add(@Nullable List<RegistryDataLoader.RegistryData<?>> list, @Nullable Collection<ResourceKey<? extends Registry<?>>> keys,
			RegistryDataLoader.RegistryData<?> data, String what) {
		if (list == null) {
			throw new UnsupportedOperationException("This NeoForge version does not let Fabricated add " + what + " (registry " + data.key().identifier() + ")");
		}
		list.add(data);
		if (keys != null) keys.add(data.key());
	}

	private static @Nullable Method method(Class<?> owner, String name, Class<?>... parameters) {
		try {
			Method method = owner.getDeclaredMethod(name, parameters);
			method.setAccessible(true);
			return method;
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.warn("NeoForge internal {}.{} is not available: {}", owner.getSimpleName(), name, e.toString());
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private static <C> @Nullable C field(Class<C> type, String... names) {
		for (String name : names) {
			try {
				Field field = DataPackRegistriesHooks.class.getDeclaredField(name);
				field.setAccessible(true);
				Object value = field.get(null);
				if (type.isInstance(value)) return (C) value;
			} catch (NoSuchFieldException e) {
				// Try the next name: NeoForge renamed these fields between versions.
			} catch (ReflectiveOperationException | RuntimeException e) {
				LOGGER.warn("NeoForge internal DataPackRegistriesHooks.{} is not accessible: {}", name, e.toString());
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private static @Nullable List<RegistryDataLoader.RegistryData<?>> list(String... names) {
		return field(List.class, names);
	}

	@SuppressWarnings("unchecked")
	private static @Nullable Collection<ResourceKey<? extends Registry<?>>> keys(String... names) {
		return field(Collection.class, names);
	}
}
