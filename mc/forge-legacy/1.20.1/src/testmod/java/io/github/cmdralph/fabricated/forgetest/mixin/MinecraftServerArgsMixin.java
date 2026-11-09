package io.github.cmdralph.fabricated.forgetest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import io.github.cmdralph.fabricated.forgetest.ForgeTestMod;
import net.minecraft.server.MinecraftServer;

/**
 * Proves a Fabric mod's {@code @ModifyArgs} runs on Forge 47 (CORPUS-68): Mixin generates a subclass of {@link Args} for
 * each such injector, in {@code org.spongepowered.asm.synthetic.args}, and Forge 47's Mixin 0.8.5 puts that package in
 * no module, so the game's class loader can't find the class unless Fabricated adds one. The injector modifies the
 * arguments of {@code tickChildren} ({@code method_3813}) where {@code tickServer} ({@code method_3748}) calls it, and
 * logs after the first server tick.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerArgsMixin {
	@Unique
	private static boolean fabricatedForgeTest$modified;

	@ModifyArgs(method = "method_3748", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/MinecraftServer;method_3813(Ljava/util/function/BooleanSupplier;)V"))
	private void fabricatedForgeTest$tickChildrenArgs(Args args) {
		args.set(0, args.get(0));
		if (!fabricatedForgeTest$modified) {
			fabricatedForgeTest$modified = true;
			ForgeTestMod.LOGGER.info("FABRICATED-TEST @ModifyArgs OK");
		}
	}
}
