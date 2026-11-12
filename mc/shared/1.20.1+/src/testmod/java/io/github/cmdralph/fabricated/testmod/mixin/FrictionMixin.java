package io.github.cmdralph.fabricated.testmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.testmod.VanillaFrictionTest;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;

/**
 * FrozenLib's and Artifacts' kind of hook of vanilla's friction, which NeoForge and Forge ask through the block state's
 * {@code getFriction(level, pos, entity)} and Fabricated puts back for the blocks keeping its default (SEAM-8,
 * {@link VanillaFrictionTest}): a {@code @WrapOperation} of the {@code getFriction} call in an item's movement, which
 * makes packed ice rough while the check asks it to.
 */
@Mixin(ItemEntity.class)
public abstract class FrictionMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getFriction()F"))
	private float fabricatedTest$friction(Block block, Operation<Float> original) {
		return VanillaFrictionTest.friction(block, original.call(block));
	}
}
