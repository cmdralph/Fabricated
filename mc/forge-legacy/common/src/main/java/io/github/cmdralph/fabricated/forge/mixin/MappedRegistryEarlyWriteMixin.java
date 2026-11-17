package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.forge.FabricatedMod;
import io.github.cmdralph.fabricated.forge.MainEntrypoints;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * A write to a frozen built-in registry (the root, or a vanilla registry in it) before Fabric mods' main entrypoints run
 * (REG-3). Forge 47 opens the root only while it makes its own registries ({@code NewRegistryEvent.fill}) and freezes it
 * again before it processes its data pack registry event, which loads {@code RegistryDataLoader}; vanilla's registries
 * are frozen from the bootstrap (by vanilla's freeze, or by Fabricated's in its place) until Forge opens them for its
 * registration events. A mod written for Fabric (Wover) adds its registries and entries in vanilla's from that class's
 * initializer, as Fabric API leaves the registries open until the main entrypoints have run. Such a registry is opened
 * for the write: the root is frozen again after the Fabric initializers ({@link FabricatedMod}), a vanilla registry with
 * the rest once mods have loaded, as Forge opens it for its registration events anyway. Forge's own registries take
 * their writes through Forge's wrapper, not this. A frozen registry being written is a write that fails otherwise, so the
 * checks only run for one.
 */
@Mixin(MappedRegistry.class)
abstract class MappedRegistryEarlyWriteMixin {
	@Shadow
	private boolean frozen;

	@Inject(method = "validateWrite(Lnet/minecraft/resources/ResourceKey;)V", at = @At("HEAD"))
	private void fabricated$openForEarlyWrite(CallbackInfo ci) {
		if (!frozen || !MainEntrypoints.pending()) return;
		MappedRegistry<?> self = (MappedRegistry<?>) (Object) this;
		if (self == BuiltInRegistries.REGISTRY || BuiltInRegistries.REGISTRY.get(self.key().location()) == self) frozen = false;
	}
}
