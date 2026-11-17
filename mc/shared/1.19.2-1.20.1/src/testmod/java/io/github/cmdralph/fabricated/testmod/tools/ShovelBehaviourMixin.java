package io.github.cmdralph.fabricated.testmod.tools;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;

import io.github.cmdralph.fabricated.testmod.VanillaShovelTest;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * FrozenLib's kind of hook of the shovel's {@code useOn} (its shovel behaviours, Wilder Wild's among them), which NeoForge
 * and Forge make through the block state's {@code getToolModifiedState} and Forge's level's {@code isEmptyBlock}, and
 * Fabricated puts back for the blocks and levels keeping their defaults (SEAM-13, {@link VanillaShovelTest}): as the use
 * starts, a behaviour of the test mod's own is chosen for the block; the hook of the first null check (the path's) sets its
 * state; the hook of the air check above the path stops the path, and that of vanilla's second {@code getBlock} (the
 * campfire check) stops the dousing. A further hook counts vanilla's lookup of the path. Targeted by its class, whose
 * methods the annotation processor maps to the game's names on Forge 43 and 47 (from 1.21.1 by name, as 26.3 has no
 * {@code ShovelItem}).
 */
@Mixin(ShovelItem.class)
public abstract class ShovelBehaviourMixin {
	@ModifyExpressionValue(method = "useOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/context/UseOnContext;getClickedFace()Lnet/minecraft/core/Direction;",
			ordinal = 0))
	private Direction fabricatedTest$start(Direction face, @Local BlockState state, @Share("behaviour") LocalBooleanRef behaviour) {
		behaviour.set(VanillaShovelTest.behaviour(state));
		return behaviour.get() ? Direction.UP : face;
	}

	@Inject(method = "useOn", at = @At(value = "JUMP", opcode = Opcodes.IFNULL, ordinal = 0))
	private void fabricatedTest$behaviour(CallbackInfoReturnable<?> info, @Local(ordinal = 2) LocalRef<BlockState> updated, @Share("behaviour") LocalBooleanRef behaviour) {
		if (behaviour.get()) updated.set(VanillaShovelTest.behaviourState());
	}

	@ModifyExpressionValue(method = "useOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;isAir()Z", ordinal = 0))
	private boolean fabricatedTest$noPath(boolean air, @Share("behaviour") LocalBooleanRef behaviour) {
		return !behaviour.get() && air;
	}

	@ModifyExpressionValue(method = "useOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getBlock()Lnet/minecraft/world/level/block/Block;",
			ordinal = 1))
	private Block fabricatedTest$noDousing(Block block, @Share("behaviour") LocalBooleanRef behaviour) {
		VanillaShovelTest.campfireChecked();
		return behaviour.get() ? Blocks.AIR : block;
	}

	@ModifyExpressionValue(method = "useOn", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
	private Object fabricatedTest$lookup(Object path) {
		VanillaShovelTest.lookedUp();
		return path;
	}
}
