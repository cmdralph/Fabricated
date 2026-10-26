package io.github.cmdralph.fabricated.neoforge.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.fabricmc.fabric.api.block.v1.FabricBlock;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Makes block properties Fabric's {@link FabricBlock.FabricProperties} (26.x): reads the id set by {@code setId}. */
@Mixin(BlockBehaviour.Properties.class)
abstract class BlockBehaviourPropertiesMixin implements FabricBlock.FabricProperties {
	@Shadow
	private @Nullable ResourceKey<Block> id;

	@Override
	public @Nullable ResourceKey<Block> blockId() {
		return this.id;
	}
}
