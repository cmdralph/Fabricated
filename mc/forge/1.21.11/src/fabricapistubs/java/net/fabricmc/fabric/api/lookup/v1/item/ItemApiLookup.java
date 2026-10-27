package net.fabricmc.fabric.api.lookup.v1.item;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 class that Fabricated's in-game mod and the forge test mod
 * call, in Mojang's names.
 */
public interface ItemApiLookup<A, C> {
	@Nullable
	A find(ItemStack itemStack, C context);

	void registerForItems(ItemApiProvider<A, C> provider, ItemLike... items);

	void registerFallback(ItemApiProvider<A, C> fallbackProvider);

	@FunctionalInterface
	interface ItemApiProvider<A, C> {
		@Nullable
		A find(ItemStack itemStack, C context);
	}
}
