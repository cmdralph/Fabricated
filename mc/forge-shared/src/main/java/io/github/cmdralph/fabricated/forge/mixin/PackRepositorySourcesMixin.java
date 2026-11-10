package io.github.cmdralph.fabricated.forge.mixin;

import java.util.LinkedHashSet;
import java.util.Set;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;

/**
 * Forge keeps a pack repository's sources in a mutable set and adds pack finders to it ({@code addPackFinder}: Forge
 * 47's {@code AddPackFindersEvent} for every mod, Forge 52's and 61 to 66's {@code ResourcePackLoader.loadResourcePacks}
 * for the mods' packs, which the client's mod loading calls in {@code Minecraft}'s constructor); vanilla's set is
 * immutable, so Fabric mods adding a source replace it with another immutable one (FrozenLib's resource pack API, PACK-1,
 * as the client's repository is made, after Fabric API's resource loader made the set mutable: PACK-11), which the next
 * addition then throws on and the game stops. The set is made mutable again first, with the mod's source in it, as on
 * NeoForge.
 */
@Mixin(PackRepository.class)
abstract class PackRepositorySourcesMixin {
	@Shadow
	@Final
	@Mutable
	private Set<RepositorySource> sources;

	@Inject(method = "addPackFinder", at = @At("HEAD"), remap = false)
	private void fabricated$keepSourcesMutable(RepositorySource finder, CallbackInfo ci) {
		if (!(sources instanceof LinkedHashSet)) sources = new LinkedHashSet<>(sources);
	}
}
