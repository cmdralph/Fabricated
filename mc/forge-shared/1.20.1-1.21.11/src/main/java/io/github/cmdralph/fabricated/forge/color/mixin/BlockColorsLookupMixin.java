package io.github.cmdralph.fabricated.forge.color.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The block colour lookup fabric-rendering-v1's {@code ColorProviderRegistry.BLOCK.get} calls on {@link BlockColors}
 * (its {@code ColorMapperHolder.get}, erased). Upstream's own method reads vanilla's {@code IdMapper} by block id, which
 * Forge replaced with a map by registry holder under the same name, so the Fabric API plan leaves that method and its
 * shadow out, and this one reads Forge's map as Forge's own lookups do (COLOR-1). Adds a method only.
 */
@Mixin(BlockColors.class)
public abstract class BlockColorsLookupMixin {
	@Shadow
	@Final
	private Map<Holder.Reference<Block>, BlockColor> blockColors;

	public Object get(Object block) {
		return ForgeRegistries.BLOCKS.getDelegate((Block) block).map(blockColors::get).orElse(null);
	}
}
