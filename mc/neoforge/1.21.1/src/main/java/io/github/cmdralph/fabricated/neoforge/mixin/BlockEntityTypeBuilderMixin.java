package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.mojang.datafixers.types.Type;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Makes {@link BlockEntityType.Builder} Fabric's {@link FabricBlockEntityType.Builder} (1.21.1, the last version with
 * the builder). As upstream, Fabric's {@code build()} builds without a data fixer type.
 */
@Mixin(BlockEntityType.Builder.class)
abstract class BlockEntityTypeBuilderMixin<T extends BlockEntity> implements FabricBlockEntityType.Builder<T> {
	@Shadow
	public abstract BlockEntityType<T> build(Type<?> dataType);

	@Override
	public BlockEntityType<T> build() {
		return build(null);
	}
}
