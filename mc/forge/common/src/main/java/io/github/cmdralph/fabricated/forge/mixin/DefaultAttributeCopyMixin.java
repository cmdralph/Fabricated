package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

/**
 * fabric-object-builder-api-v1's copy of an entity type's default attributes, which {@code FabricDefaultAttributeRegistry.MODIFY}'s
 * listeners change (26.x, passed through; see {@link io.github.cmdralph.fabricated.forge.objectbuilder.DefaultAttributeModify}).
 * Upstream fills a new builder through an accessor of its entries, which Forge keeps in a map of its own: pre-flight
 * found the accessor's type wrong, and the plan leaves it out. Forge's copy constructor does the same. Pseudo: Mixin
 * skips it without a word when there is no such class (without Fabric API, and before 26.x).
 */
@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.object.builder.FabricDefaultAttributeRegistryImpl", remap = false)
abstract class DefaultAttributeCopyMixin {
	@Inject(method = "createFromExistingSupplier", at = @At("HEAD"), cancellable = true)
	private static void fabricated$copyWithForge(AttributeSupplier supplier, CallbackInfoReturnable<AttributeSupplier.Builder> cir) {
		cir.setReturnValue(new AttributeSupplier.Builder(supplier));
	}
}
