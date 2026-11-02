package io.github.cmdralph.fabricated.neoforge.mixin.client;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.neoforge.resource.DefaultResourcePacks;
import net.minecraft.client.Options;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * Adds the built-in resource packs Fabric mods enable by default to the player's selection the first time each is
 * offered, just before the client applies that selection to its pack repository (see {@link DefaultResourcePacks}).
 * Upstream adds them as the options load, from its own list of packs; here the repository has found them by then, and
 * mods' initializers, which register them, have run on every version.
 */
@Mixin(Options.class)
abstract class OptionsDefaultPacksMixin {
	@Shadow
	public List<String> resourcePacks;

	@Inject(method = "loadSelectedResourcePacks(Lnet/minecraft/server/packs/repository/PackRepository;)V", at = @At("HEAD"))
	private void fabricated$defaultEnabledPacks(PackRepository repository, CallbackInfo ci) {
		resourcePacks = DefaultResourcePacks.withNew(resourcePacks, repository);
	}
}
