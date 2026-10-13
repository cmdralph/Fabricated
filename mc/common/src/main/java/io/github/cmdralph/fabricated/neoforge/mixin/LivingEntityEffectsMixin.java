package io.github.cmdralph.fabricated.neoforge.mixin;

import java.util.Collection;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.neoforge.entity.EntityEventsBridge;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Fabric's mob effect AFTER_ADD and AFTER_REMOVE, where upstream fires them (see {@link EntityEventsBridge}). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityEffectsMixin {
	@Inject(method = "onEffectAdded", at = @At("RETURN"))
	private void fabricated$afterEffectAdded(MobEffectInstance instance, @Nullable Entity source, CallbackInfo ci) {
		EntityEventsBridge.afterEffectAdded((LivingEntity) (Object) this, instance);
	}

	@Inject(method = "onEffectsRemoved", at = @At("RETURN"))
	private void fabricated$afterEffectsRemoved(Collection<MobEffectInstance> instances, CallbackInfo ci) {
		EntityEventsBridge.afterEffectsRemoved((LivingEntity) (Object) this, instances);
	}
}
