package io.github.cmdralph.fabricated.testmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.testmod.VanillaHarvestTest;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Apoli's hook of vanilla's harvest check in {@code BlockBehaviour.getDestroyProgress}, which NeoForge makes through
 * {@code EventHooks.doPlayerHarvestCheck} and Forge through {@code ForgeHooks.isCorrectToolForDrops}, and Fabricated puts
 * back (SEAM-4, {@link VanillaHarvestTest}): a {@code @WrapOperation} of the {@code hasCorrectToolForDrops} call, which
 * lets a bare hand mine stone as fast as the right tool while the check asks it to.
 */
@Mixin(BlockBehaviour.class)
public abstract class DestroyProgressMixin {
	@WrapOperation(method = "getDestroyProgress", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Player;hasCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
	private boolean fabricatedTest$mining(Player player, BlockState state, Operation<Boolean> original) {
		return VanillaHarvestTest.mining(state, original.call(player, state));
	}
}
