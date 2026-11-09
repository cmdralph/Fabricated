package io.github.cmdralph.fabricated.forge.block.late.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.forge.block.BlockApiCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;

/**
 * Fabric's {@code FluidFlowEvents.ALLOW} as a liquid block is placed and as its neighbour changes (26.2+), where Forge
 * asks its fluid interactions in place of vanilla's {@code shouldSpreadLiquid}, which upstream's hook asks the event in
 * and which Forge no longer calls: a refusal keeps the liquid from interacting and from spreading from that update, as
 * vanilla's check answering no does ({@link BlockApiCompat}).
 */
@Mixin(LiquidBlock.class)
abstract class LiquidBlockFlowMixin {
	@WrapOperation(method = {"onPlace", "neighborChanged"}, at = @At(value = "INVOKE",
			target = "Lnet/minecraftforge/fluids/FluidInteractionRegistry;canInteract(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z"),
			require = 2)
	private boolean fabricated$allowFlow(Level level, BlockPos pos, Operation<Boolean> canInteract) {
		return !BlockApiCompat.allowFlow(level, pos) || canInteract.call(level, pos);
	}
}
