package io.github.cmdralph.fabricated.forge.entity.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.forge.entity.SleepEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Runs Fabric's bed occupation and sleeping direction events at Forge's calls ({@link SleepEvents}): its
 * {@code setBedOccupied} as an entity lies down ({@code startSleeping}) and wakes up (the lambda of
 * {@code stopSleeping}, whose number differs between versions), and its answer to {@code getBedOrientation}.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntitySleepMixin {
	@WrapOperation(method = {"startSleeping(Lnet/minecraft/core/BlockPos;)V", "/^lambda\\$stopSleeping\\$\\d+$/"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;setBedOccupied(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;Z)V"),
			require = 2)
	private void fabricated$setBedOccupied(BlockState state, Level level, BlockPos pos, LivingEntity sleeper, boolean occupied,
			Operation<Void> setBedOccupied) {
		SleepEvents.setBedOccupied(state, level, pos, sleeper, occupied, () -> setBedOccupied.call(state, level, pos, sleeper, occupied));
	}

	@ModifyReturnValue(method = "getBedOrientation()Lnet/minecraft/core/Direction;", at = @At("RETURN"))
	private Direction fabricated$modifySleepingDirection(Direction direction) {
		return SleepEvents.sleepingDirection((LivingEntity) (Object) this, direction);
	}
}
