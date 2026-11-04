package io.github.cmdralph.fabricated.forge.entity.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.ResultConsumer;
import com.mojang.brigadier.context.CommandContext;

import io.github.cmdralph.fabricated.forge.entity.EffectCommandContext;
import net.minecraft.commands.execution.tasks.ExecuteCommand;

/**
 * Gives Fabric API's effect events the command that runs ({@link EffectCommandContext}) at vanilla's call of Brigadier's
 * {@code ContextChain.runExecutable}, where upstream's hook into that library class can't apply on Forge.
 */
@Mixin(ExecuteCommand.class)
abstract class ExecuteCommandEffectMixin {
	@WrapOperation(method = "execute", at = @At(value = "INVOKE", remap = false,
			target = "Lcom/mojang/brigadier/context/ContextChain;runExecutable(Lcom/mojang/brigadier/context/CommandContext;Ljava/lang/Object;Lcom/mojang/brigadier/ResultConsumer;Z)I"))
	private int fabricated$runExecutable(CommandContext<?> executable, Object source, ResultConsumer<?> resultConsumer, boolean forked,
			Operation<Integer> runExecutable) {
		return EffectCommandContext.run(executable, () -> runExecutable.call(executable, source, resultConsumer, forked));
	}
}
