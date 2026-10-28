package net.fabricmc.fabric.api.client.rendering.v1;

import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

/**
 * Compile-time stand-in: the members of Fabric API 0.116's interface that the test mod calls, in Mojang's names.
 */
public interface ColorProviderRegistry<T, Provider> {
	ColorProviderRegistry<ItemLike, ItemColor> ITEM = null;
	ColorProviderRegistry<Block, BlockColor> BLOCK = null;

	@SuppressWarnings("unchecked")
	void register(Provider provider, T... objects);

	Provider get(T object);
}
