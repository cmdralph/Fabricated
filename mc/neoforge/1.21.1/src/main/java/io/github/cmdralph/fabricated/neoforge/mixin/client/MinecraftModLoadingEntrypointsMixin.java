package io.github.cmdralph.fabricated.neoforge.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.neoforge.EntrypointBridge;
import net.minecraft.client.Minecraft;

/**
 * Runs Fabric's main and client entrypoints right after NeoForge 21.1 has loaded the mods, in {@code Minecraft}'s
 * constructor. Later versions load them before the constructor, so {@code MinecraftEntrypointsMixin} runs the
 * entrypoints where Fabric Loader does, at the game thread; on NeoForge 21.1 that point comes before the mods exist, and
 * the constructor's end comes after the first resource reload has started, whose common setup closes registration
 * meanwhile (REG-7). Here registration is over and that setup not begun, as when later versions run them.
 */
@Mixin(Minecraft.class)
abstract class MinecraftModLoadingEntrypointsMixin {
	@Inject(method = "<init>", at = @At(value = "INVOKE",
			target = "Lnet/neoforged/neoforge/client/loading/ClientModLoader;begin(Lnet/minecraft/client/Minecraft;Lnet/minecraft/server/packs/repository/PackRepository;Lnet/minecraft/server/packs/resources/ReloadableResourceManager;)V",
			shift = At.Shift.AFTER, remap = false))
	private void fabricated$runEntrypointsAfterModLoading(CallbackInfo ci) {
		EntrypointBridge.runClientEntrypoints();
	}
}
