package io.github.cmdralph.fabricated.neoforge.registry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.List;
import java.util.Set;

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
 *       {@code DynamicRegistries} adds to them directly. The field names differ between NeoForge versions, and later
 *       26.3 builds keep each list with the set of its keys in a record.</li>
 * </ul>
 */
public final class NeoForgeRegistryInternals {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final @Nullable Method SET_SYNC = method(BaseMappedRegistry.class, "setSync", boolean.class);
	private static final @Nullable Registries WORLD = registries(List.of("WORLD_REGISTRIES", "DATA_PACK_REGISTRIES"), "WORLD_REGISTRY_KEYS");
	private static final @Nullable Registries SYNCED = registries(List.of("SYNCED_REGISTRIES", "NETWORKABLE_REGISTRIES"),
			"SYNCED_REGISTRY_KEYS", "SYNCED_CUSTOM_REGISTRIES");
	private static final @Nullable Registries RELOADABLE = registries(List.of("RELOADABLE_REGISTRIES"), "RELOADABLE_REGISTRY_KEYS");

	/** One of NeoForge's lists of data-pack registries, and the set of their keys when NeoForge keeps one. */
	private record Registries(List<RegistryDataLoader.RegistryData<?>> data, @Nullable Collection<ResourceKey<? extends Registry<?>>> keys) {
	}

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
		return WORLD == null ? List.of() : List.copyOf(WORLD.data());
	}

	/** The reloadable registries NeoForge loads (NeoForge versions that have them). */
	public static List<RegistryDataLoader.RegistryData<?>> reloadableRegistries() {
		return RELOADABLE == null ? List.of() : List.copyOf(RELOADABLE.data());
	}

	public static synchronized boolean isDataPackRegistry(ResourceKey<? extends Registry<?>> key) {
		return contains(WORLD, key) || contains(RELOADABLE, key);
	}

	public static synchronized void addWorldRegistry(RegistryDataLoader.RegistryData<?> data) {
		add(WORLD, data, "data-pack registries");
	}

	public static synchronized void addReloadableRegistry(RegistryDataLoader.RegistryData<?> data) {
		add(RELOADABLE, data, "reloadable registries");
	}

	/** Makes NeoForge send the registry's entries to clients, decoded there with {@code data}'s codec. */
	public static synchronized void addSyncedRegistry(RegistryDataLoader.RegistryData<?> data) {
		add(SYNCED, data, "synced data-pack registries");
	}

	private static boolean contains(@Nullable Registries registries, ResourceKey<? extends Registry<?>> key) {
		if (registries == null) return false;
		for (RegistryDataLoader.RegistryData<?> data : registries.data()) {
			if (data.key().equals(key)) return true;
		}
		return false;
	}

	private static void add(@Nullable Registries registries, RegistryDataLoader.RegistryData<?> data, String what) {
		if (registries == null) {
			throw new UnsupportedOperationException("This NeoForge version does not let Fabricated add " + what + " (registry " + data.key().identifier() + ")");
		}
		registries.data().add(data);
		if (registries.keys() != null) registries.keys().add(data.key());
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

	/**
	 * The first of {@code names} that NeoForge has: a list (with its keys in one of {@code keyNames}), or a record of a
	 * list and a set of keys.
	 */
	@SuppressWarnings("unchecked")
	private static @Nullable Registries registries(List<String> names, String... keyNames) {
		for (String name : names) {
			Object value = staticField(name);
			if (value == null) continue;
			if (value instanceof List<?> list) {
				Object keys = null;
				for (int i = 0; i < keyNames.length && !(keys instanceof Set<?>); i++) keys = staticField(keyNames[i]);
				return new Registries((List<RegistryDataLoader.RegistryData<?>>) list, keys instanceof Set<?> set ? (Set<ResourceKey<? extends Registry<?>>>) set : null);
			}
			if (value instanceof Record group) {
				Object list = component(group, List.class);
				Object keys = component(group, Set.class);
				if (list != null) {
					return new Registries((List<RegistryDataLoader.RegistryData<?>>) list, (Set<ResourceKey<? extends Registry<?>>>) keys);
				}
			}
			LOGGER.warn("NeoForge internal DataPackRegistriesHooks.{} is a {}, which Fabricated does not know", name, value.getClass().getName());
		}
		return null;
	}

	private static @Nullable Object staticField(String name) {
		try {
			Field field = DataPackRegistriesHooks.class.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(null);
		} catch (NoSuchFieldException e) {
			// NeoForge renamed these fields between versions; the caller tries the next name.
			return null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.warn("NeoForge internal DataPackRegistriesHooks.{} is not accessible: {}", name, e.toString());
			return null;
		}
	}

	/** The value of the record's component declared as {@code type}, or null. */
	private static @Nullable Object component(Record record, Class<?> type) {
		for (RecordComponent component : record.getClass().getRecordComponents()) {
			if (component.getType() != type) continue;
			try {
				Method accessor = component.getAccessor();
				accessor.setAccessible(true);
				return accessor.invoke(record);
			} catch (ReflectiveOperationException | RuntimeException e) {
				LOGGER.warn("NeoForge internal {}.{} is not accessible: {}", record.getClass().getSimpleName(), component.getName(), e.toString());
				return null;
			}
		}
		return null;
	}
}
