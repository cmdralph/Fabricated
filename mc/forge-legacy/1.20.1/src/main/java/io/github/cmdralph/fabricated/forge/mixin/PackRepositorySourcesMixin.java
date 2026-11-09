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
 * Forge 47 keeps a pack repository's sources in a mutable set and adds pack finders to it ({@code addPackFinder}, which
 * its {@code AddPackFindersEvent} calls for every mod); vanilla's set is immutable, so Fabric mods adding a source replace
 * it with another immutable one (FrozenLib's resource pack API, PACK-1), which the next addition then throws on and the
 * game stops (Forgified Fabric API's resource loader adds one so). The set is made mutable again first, with the mod's
 * source in it, as on NeoForge.
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
