package io.github.cmdralph.fabricated.neoforge.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.flag.FeatureFlagSet;

/**
 * Fires Fabric's {@code TAGS_LOADED} after a data load binds its tags, where upstream does (1.21.11): NeoForge's event
 * there carries a lookup provider, not the registries Fabric's event takes. The client's case comes from NeoForge's
 * event ({@code LifecycleBridge}).
 */
@Mixin(ReloadableServerResources.class)
abstract class ReloadableServerResourcesTagsLoadedMixin {
	@Unique
	private RegistryAccess fabricated$registries;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void fabricated$keepRegistries(LayeredRegistryAccess<RegistryLayer> registries, HolderLookup.Provider lookup,
			FeatureFlagSet features, Commands.CommandSelection commands, List<?> postponedTags, PermissionSet permissions, CallbackInfo ci) {
		this.fabricated$registries = registries.compositeAccess();
	}

	@Inject(method = "updateStaticRegistryTags", at = @At("TAIL"))
	private void fabricated$tagsLoaded(CallbackInfo ci) {
		CommonLifecycleEvents.TAGS_LOADED.invoker().onTagsLoaded(this.fabricated$registries, false);
	}
}
