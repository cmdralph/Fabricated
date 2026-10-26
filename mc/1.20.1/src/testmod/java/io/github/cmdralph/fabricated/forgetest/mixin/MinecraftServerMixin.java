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
 * Proves Forge's Mixin applies a translated Fabric mod's mixins (from the overlay's MixinConfigs) in the names Forge 47
 * runs the game with: logs after the first server tick. The method is named in Intermediary's names, as a Fabric mod for
 * 1.20.1 names it (its refmap would), {@code method_3748} being {@code tickServer}; Fabricated remaps it to SRG's
 * {@code m_5705_}. {@code MinecraftServer} keeps its name in every namespace.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
	@Unique
	private static boolean fabricatedForgeTest$logged;

	@Inject(method = "method_3748", at = @At("TAIL"))
	private void fabricatedForgeTest$afterTick(CallbackInfo ci) {
		if (!fabricatedForgeTest$logged) {
			fabricatedForgeTest$logged = true;
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST mixin OK");
			if ((Object) this instanceof ForgeTestMarker) ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST injected interface OK");
		}
	}
}
