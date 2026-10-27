package net.fabricmc.fabric.api.lookup.v1.item;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;

/**
 * Compile-time stand-in: the members of Fabric API's 1.20.1 class that Fabricated's in-game mod calls, in Mojang's
 * names.
 */
public interface ItemApiLookup<A, C> {
	@Nullable
	A find(ItemStack itemStack, C context);

	void registerFallback(ItemApiProvider<A, C> fallbackProvider);

	@FunctionalInterface
	interface ItemApiProvider<A, C> {
		@Nullable
		A find(ItemStack itemStack, C context);
	}
}
