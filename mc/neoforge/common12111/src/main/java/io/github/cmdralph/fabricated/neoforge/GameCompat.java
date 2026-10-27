package io.github.cmdralph.fabricated.neoforge;

import java.util.stream.Stream;

import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;

/**
 * Game methods Minecraft names differently from one version to another, for the code NeoForge's versions share
 * (mc/neoforge/common). These are 1.21.11's and later's names; 1.21.1 has its own.
 */
public final class GameCompat {
	private GameCompat() {
	}

	/** A key's id within its registry. */
	public static Identifier location(ResourceKey<?> key) {
		return key.identifier();
	}

	/** A registry's entries. */
	public static <T> Stream<Holder.Reference<T>> elements(Registry<T> registry) {
		return registry.listElements();
	}

	/** A registry's entry with this id, or null. */
	public static <T> T value(Registry<T> registry, Identifier id) {
		return registry.getValue(id);
	}

	/** Opens a frozen registry for writing again, keeping its tags. */
	public static void unfreeze(MappedRegistry<?> registry) {
		registry.unfreeze(false);
	}

	/** Binds a registry's tags as vanilla's bootstrap freeze does before it freezes the registry: to nothing yet. */
	public static void bindTagsForFreeze(MappedRegistry<?> registry) {
		registry.bindAllTagsToEmpty();
	}

	/** The type of the registries Fabric's resource conditions are evaluated with: the ops' own lookup. */
	public static final Class<?> CONDITION_REGISTRIES = RegistryOps.RegistryInfoLookup.class;

	/** The registries a decode with these ops sees, as Fabric's resource conditions take them. */
	public static Object conditionRegistries(RegistryOps<?> ops) {
		return ops.lookupProvider;
	}
}
