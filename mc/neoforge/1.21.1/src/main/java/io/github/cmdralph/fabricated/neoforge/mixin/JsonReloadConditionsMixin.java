package io.github.cmdralph.fabricated.neoforge.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.neoforge.resource.FabricResourceConditions;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.resource.ContextAwareReloadListener;

/**
 * A JSON data loader's files are decided by their Fabric conditions first, at the start of applying them, before other
 * mods' hooks there (COND-1, {@link FabricResourceConditions#decideFirst}): upstream's hook at that point decides them
 * the same a moment later, and finds no conditions left on the files that load. Priority 500: Mixin applies the hooks
 * of a method's start in priority order, the first applied running first, and mods' come at the default 1000.
 *
 * <p>The registries are those upstream gives the recipe and advancement loaders, which NeoForge 21.1 builds with the
 * lookup it hands every server data loader ({@code ReloadableServerResources}); upstream gives the others none.
 */
@Mixin(value = SimplePreparableReloadListener.class, priority = 500)
abstract class JsonReloadConditionsMixin extends ContextAwareReloadListener {
	@Inject(method = "lambda$reload$1", at = @At("HEAD"))
	private void fabricated$decideFabricConditionsFirst(ResourceManager manager, ProfilerFiller profiler, Object prepared, CallbackInfo ci) {
		Object self = this;
		if (!(self instanceof SimpleJsonResourceReloadListener loader) || !(prepared instanceof Map<?, ?> data)) return;
		FabricResourceConditions.decideFirst(data, ((SimpleJsonResourceReloadListenerAccessor) loader).fabricated$directory(),
				self instanceof RecipeManager || self instanceof ServerAdvancementManager ? getRegistryLookup() : null);
	}
}
