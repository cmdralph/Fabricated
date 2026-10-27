package io.github.cmdralph.fabricated.neoforge;

import java.util.stream.Stream;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
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
}
