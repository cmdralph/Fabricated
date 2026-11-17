package io.github.cmdralph.fabricated.testmod.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.testmod.TestMod;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * Registers a reload listener the vanilla way from {@code Minecraft}'s constructor, before NeoForge's reload listener
 * event, as Fabric mods do from their own mixins: NeoForge's event refuses listeners it can't name, so Fabricated adds
 * it through the event. Logs when the listener runs (the first resource load).
 */
@Mixin(Minecraft.class)
public abstract class MinecraftEarlyReloadListenerMixin {
	@Inject(method = "<init>", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/packs/resources/ReloadableResourceManager;registerReloadListener(Lnet/minecraft/server/packs/resources/PreparableReloadListener;)V",
			ordinal = 0))
	private void fabricatedTest$registerEarly(CallbackInfo ci) {
		if (((Minecraft) (Object) this).getResourceManager() instanceof ReloadableResourceManager manager) {
			manager.registerReloadListener((ResourceManagerReloadListener) resources -> TestMod.LOGGER.info("FABRICATED-TEST early reload listener OK"));
		}
	}
}
