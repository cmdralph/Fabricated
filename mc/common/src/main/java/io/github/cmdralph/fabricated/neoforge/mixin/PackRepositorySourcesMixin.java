package io.github.cmdralph.fabricated.neoforge.mixin;

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
 * NeoForge keeps a pack repository's sources in a mutable set and adds its pack finders to it ({@code addPackFinder});
 * vanilla's set is immutable, so Fabric mods adding a source replace it with another immutable one (FrozenLib's resource
 * pack API, PACK-1), which NeoForge's next addition then throws on. The set is made mutable again first, with the mod's
 * source in it.
 */
@Mixin(PackRepository.class)
abstract class PackRepositorySourcesMixin {
	@Shadow
	@Final
	@Mutable
	private Set<RepositorySource> sources;

	@Inject(method = "addPackFinder", at = @At("HEAD"))
	private void fabricated$keepSourcesMutable(RepositorySource finder, CallbackInfo ci) {
		if (!(sources instanceof LinkedHashSet)) sources = new LinkedHashSet<>(sources);
	}
}
