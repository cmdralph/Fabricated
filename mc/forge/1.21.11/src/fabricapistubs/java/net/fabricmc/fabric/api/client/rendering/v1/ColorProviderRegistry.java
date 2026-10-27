package net.fabricmc.fabric.api.client.rendering.v1;

import net.minecraft.client.color.block.BlockColor;
import net.minecraft.world.level.block.Block;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 interface that the forge test mod calls, in Mojang's names.
 */
public interface ColorProviderRegistry<T, Provider> {
	ColorProviderRegistry<Block, BlockColor> BLOCK = null;

	Provider get(T object);
}
