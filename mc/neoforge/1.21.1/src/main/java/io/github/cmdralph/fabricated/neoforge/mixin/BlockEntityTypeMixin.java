package io.github.cmdralph.fabricated.neoforge.mixin;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Makes every block entity type Fabric's {@link FabricBlockEntityType}. Adding a block replaces the set of valid
 * blocks with a larger copy, as NeoForge's own {@code BlockEntityTypeAddBlocksEvent} does. No Minecraft code is changed.
 */
@Mixin(BlockEntityType.class)
abstract class BlockEntityTypeMixin implements FabricBlockEntityType {
	@Shadow
	@Final
	@Mutable
	private Set<Block> validBlocks;

	@Override
	public void addSupportedBlock(Block block) {
		Objects.requireNonNull(block, "block");
		synchronized (this) {
			Set<Block> blocks = new HashSet<>(validBlocks);
			blocks.add(block);
			validBlocks = blocks;
		}
	}
}
