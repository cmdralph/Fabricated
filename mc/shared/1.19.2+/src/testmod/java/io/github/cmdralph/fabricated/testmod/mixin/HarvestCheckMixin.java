package io.github.cmdralph.fabricated.testmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.testmod.VanillaHarvestTest;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Apoli's and Porting Lib's hook of vanilla's harvest check in the server's {@code destroyBlock}, which NeoForge and Forge
 * make through the block state's {@code canHarvestBlock} and Fabricated puts back for the blocks keeping its default
 * (SEAM-4, {@link VanillaHarvestTest}): a {@code @WrapOperation} of the {@code hasCorrectToolForDrops} call, which lets a
 * bare hand harvest stone, as an Origins power lets a player harvest what vanilla's check refuses.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class HarvestCheckMixin {
	@WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerPlayer;hasCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
	private boolean fabricatedTest$harvest(ServerPlayer player, BlockState state, Operation<Boolean> original) {
		return VanillaHarvestTest.harvesting(state, original.call(player, state));
	}
}
