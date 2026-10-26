package io.github.cmdralph.fabricated.testmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.testmod.HiddenDefaultTest;
import net.minecraft.world.entity.LivingEntity;

/**
 * Gives {@code LivingEntity} a helper as Fabric API's {@code fabric-entity-events-v1} does: a private {@code self()} with
 * the name and type of the default method {@code LivingEntity} inherits from the platform's extension interface. Under
 * that name it would hide the default from every mob (HIDE-1, see {@link HiddenDefaultTest}).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Unique
	private LivingEntity self() {
		return (LivingEntity) (Object) this;
	}

	/** Uses the helper, which the constructor reaches when it sets the entity's health. */
	@Inject(method = "getMaxHealth", at = @At("HEAD"))
	private void fabricatedTest$useSelf(CallbackInfoReturnable<Float> cir) {
		if (!HiddenDefaultTest.helperRan && self() == (Object) this) HiddenDefaultTest.helperRan = true;
	}
}
