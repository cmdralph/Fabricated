package io.github.cmdralph.fabricated.neoforge.mixin.client;

import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.world.level.block.Block;

/**
 * The block color lookup {@code fabric-rendering-v1}'s {@code ColorProviderRegistry.BLOCK.get} calls on
 * {@link BlockColors} (its {@code ColorMapperHolder.get}, erased). Upstream's own method reads vanilla's id-indexed
 * {@code IdMapper}, which NeoForge replaced with a map by block, so the Fabric API plan leaves that method out and this
 * one reads NeoForge's map. Adds a method only.
 */
@Mixin(BlockColors.class)
public abstract class BlockColorsLookupMixin {
	@Shadow
	@Final
	private Map<Block, BlockColor> blockColors;

	public Object get(Object block) {
		return blockColors.get(block);
	}
}
