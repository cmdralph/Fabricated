package net.fabricmc.fabric.impl.object.builder;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * The block entity type Fabric's {@code FabricBlockEntityTypeBuilder} builds. NeoForge's constructor already takes
 * whether only operators may set the block entity's data; an explicit {@code false} keeps vanilla's answer, which is
 * {@code false} for every modded type.
 */
public class ExtendedBlockEntityType<T extends BlockEntity> extends BlockEntityType<T> {
	public ExtendedBlockEntityType(BlockEntitySupplier<? extends T> factory, Set<Block> blocks, @Nullable Boolean canPotentiallyExecuteCommands) {
		super(factory, blocks, Boolean.TRUE.equals(canPotentiallyExecuteCommands));
	}
}
