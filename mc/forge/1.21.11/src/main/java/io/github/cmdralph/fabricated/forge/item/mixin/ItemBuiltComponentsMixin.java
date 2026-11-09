package io.github.cmdralph.fabricated.forge.item.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.Item;

/**
 * Forge 61 builds an item's components once, the first time they are asked (its components with what Forge's gather
 * event adds), and keeps them. Fabric API's {@code DefaultItemComponentEvents.MODIFY} replaces an item's components as
 * the built-in registries freeze, after the mods' initializers, by which time something may have asked them already, so
 * the item kept the components it had before. The built components are made again once the item's own changed.
 */
@Mixin(Item.class)
abstract class ItemBuiltComponentsMixin {
	@Shadow
	@Final
	private DataComponentMap components;
	@Shadow
	private @Nullable DataComponentMap builtComponents;
	@Unique
	private @Nullable DataComponentMap fabricated$builtFrom;

	@Inject(method = "components()Lnet/minecraft/core/component/DataComponentMap;", at = @At("HEAD"))
	private void fabricated$buildAgainOnceChanged(CallbackInfoReturnable<DataComponentMap> cir) {
		if (fabricated$builtFrom != components) {
			builtComponents = null;
			fabricated$builtFrom = components;
		}
	}
}
