package io.github.cmdralph.fabricated.forgetest.mixin;

import java.util.function.BooleanSupplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import io.github.cmdralph.fabricated.forgetest.ForgeTestMod;
import net.minecraft.server.MinecraftServer;

/**
 * Proves MixinExtras runs a Fabric mod's injector on Forge 47 and 52, which ship none (the in-game mod nests its Forge
 * build): a {@code @WrapMethod} around {@code tickServer} logs after the first server tick. The config asks for
 * MixinExtras 0.5.0, which pre-flight checks against the version discovery found.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerWrapMixin {
	@Unique
	private static boolean fabricatedForgeTest$wrapped;

	@WrapMethod(method = "tickServer")
	private void fabricatedForgeTest$wrapTick(BooleanSupplier hasTimeLeft, Operation<Void> original) {
		original.call(hasTimeLeft);
		if (!fabricatedForgeTest$wrapped) {
			fabricatedForgeTest$wrapped = true;
			ForgeTestMod.LOGGER.info("FABRICATED-TEST MixinExtras OK");
		}
	}
}
