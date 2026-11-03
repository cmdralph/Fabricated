package io.github.cmdralph.fabricated.forge;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * Objects made before what they hold is registered (CORPUS-73). Fabric lets a mod make its blocks, fluids, items and
 * sounds first and register them after, in any order, and vanilla's constructors keep the object they are given.
 * Forge's patches of some of them look that object's delegate up instead, as the bucket, liquid block or music disc is
 * made, or as a block item's block is asked for, and throw when it has none yet: Lootr's block items and TechReborn's
 * fluids then failed on Forge 47, and what a failed mod left half registered stopped Forge's snapshot of the
 * registries. Fabricated's mixins on those patches ask for the delegate only if the object has one, and keep the
 * object itself otherwise, as Forge's own mob bucket, and its liquid block from Forge 61, keep theirs.
 */
public final class EarlyRegistryObjects {
	private EarlyRegistryObjects() {
	}

	/** The object's delegate in the registry, as Forge's {@code getDelegateOrThrow} gives it, or null if it isn't registered yet. */
	@SuppressWarnings("unchecked")
	public static @Nullable Holder.Reference<?> delegate(IForgeRegistry<?> registry, Object value) {
		return ((IForgeRegistry<Object>) registry).getDelegate(value).orElse(null);
	}

	/** A supplier of the object itself, for one made before it was registered. */
	public static <T> Supplier<T> itself(T value) {
		return () -> value;
	}
}
