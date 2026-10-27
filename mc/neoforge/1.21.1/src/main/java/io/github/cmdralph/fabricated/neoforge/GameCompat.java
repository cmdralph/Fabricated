package io.github.cmdralph.fabricated.neoforge;

import java.util.stream.Stream;

import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Game methods Minecraft names differently from one version to another, for the code NeoForge's versions share
 * (mc/neoforge/common). These are 1.21.1's names.
 */
public final class GameCompat {
	private GameCompat() {
	}

	/** A key's id within its registry. */
	public static ResourceLocation location(ResourceKey<?> key) {
		return key.location();
	}

	/** A registry's entries. */
	public static <T> Stream<Holder.Reference<T>> elements(Registry<T> registry) {
		return registry.holders();
	}

	/** A registry's entry with this id, or null. */
	public static <T> T value(Registry<T> registry, ResourceLocation id) {
		return registry.get(id);
	}

	/** Opens a frozen registry for writing again, keeping its tags. */
	public static void unfreeze(MappedRegistry<?> registry) {
		registry.unfreeze();
	}

	/**
	 * Binds a registry's tags as vanilla's bootstrap freeze does before it freezes the registry: nothing to do on 1.21.1,
	 * whose registries bind their tags only when tags load.
	 */
	public static void bindTagsForFreeze(MappedRegistry<?> registry) {
	}
}
