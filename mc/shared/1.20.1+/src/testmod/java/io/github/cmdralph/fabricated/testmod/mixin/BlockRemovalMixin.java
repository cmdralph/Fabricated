package io.github.cmdralph.fabricated.testmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.testmod.VanillaBreakTest;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayerGameMode;

/**
 * Wilder Wild's and FrozenLib's hook of vanilla's removal in {@code destroyBlock}, which NeoForge and Forge make through
 * the block state's {@code onDestroyedByPlayer} and Fabricated puts back for the blocks keeping its default (SEAM-3,
 * {@link VanillaBreakTest}): a {@code @WrapOperation} of the {@code removeBlock} call, which leaves something in the block's
 * place, as Wilder Wild leaves snow where a snowlogged block breaks.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class BlockRemovalMixin {
	@WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
	private boolean fabricatedTest$removeBlock(ServerLevel level, BlockPos pos, boolean moving, Operation<Boolean> original) {
		Boolean left = VanillaBreakTest.removing(level, pos);
		return left != null ? left : original.call(level, pos, moving);
	}
}
