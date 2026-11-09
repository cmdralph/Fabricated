package io.github.cmdralph.fabricated.forgetest.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.forgetest.ForgeDropCaptureTest;

/**
 * Stands in for Porting Lib's hook at the return of {@code LivingEntity.dropAllDeathLoot}, which
 * takes the drops Forge's capture holds and adds them to the level (see {@link ForgeDropCaptureTest}). Its priority puts
 * it after Porting Lib's, at the default one, where both are installed: Porting Lib's then takes the drops.
 */
@Mixin(value = LivingEntity.class, priority = 1500)
public abstract class LivingEntityCapturedDropsMixin {
	@Inject(method = "dropAllDeathLoot", at = @At("RETURN"))
	private void fabricatedForgeTest$takeCapturedDrops(DamageSource source, CallbackInfo ci) {
		ForgeDropCaptureTest.takeCapturedDrops((LivingEntity) (Object) this);
	}
}
