package io.github.cmdralph.fabricated.forgetest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.forgetest.ForgeTestMarker;
import io.github.cmdralph.fabricated.forgetest.ForgeTestMod;
import net.minecraft.server.MinecraftServer;

/**
 * Proves Forge's Mixin applies a translated Fabric mod's mixins (from the overlay's MixinConfigs): logs after the first
 * server tick, and whether the server has the interface the test mod's class tweaker injects.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
	@Unique
	private static boolean fabricatedForgeTest$logged;

	@Inject(method = "tickServer", at = @At("TAIL"))
	private void fabricatedForgeTest$afterTick(CallbackInfo ci) {
		if (!fabricatedForgeTest$logged) {
			fabricatedForgeTest$logged = true;
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST mixin OK");
			if ((Object) this instanceof ForgeTestMarker) ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST injected interface OK");
		}
	}
}
