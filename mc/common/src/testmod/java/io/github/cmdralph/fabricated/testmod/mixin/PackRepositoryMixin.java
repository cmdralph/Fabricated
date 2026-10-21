package io.github.cmdralph.fabricated.testmod.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.google.common.collect.ImmutableSet;

import io.github.cmdralph.fabricated.testmod.TestMod;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;

/**
 * Leaves a pack repository's sources in an immutable set, as vanilla makes them and as FrozenLib's resource pack API
 * leaves them when it adds one (PACK-1); NeoForge adds its own pack finders to the set afterwards.
 */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
	@Shadow
	@Final
	@Mutable
	private Set<RepositorySource> sources;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void fabricatedTest$immutableSources(CallbackInfo ci) {
		sources = ImmutableSet.copyOf(sources);
		TestMod.immutablePackSources = true;
	}
}
