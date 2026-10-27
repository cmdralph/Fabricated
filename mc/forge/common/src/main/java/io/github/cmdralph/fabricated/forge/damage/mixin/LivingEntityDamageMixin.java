package io.github.cmdralph.fabricated.forge.damage.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.forge.damage.DamageEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Fires Fabric's after-damage event ({@link DamageEvents}) at the end of Forge 61's and 64's {@code hurtServer}, where
 * upstream's hook does on vanilla's; only those versions' projects name this mixin's config. The damage the entity was
 * dealt is the amount {@code applyItemBlocking} is asked about (vanilla keeps a copy of it), and a shield blocked some
 * when that call answers more than nothing; both are kept on the entity from that call to the end of the method. The
 * method is wrapped so that a call nested in it, on the same entity, leaves the outer call's values as they were.
 *
 * <p>No MixinExtras sugar ({@code @Local}, {@code @Share}): without Fabric API installed, Forge's MixinExtras can't
 * load the Mixin class it needs, as {@code ReloadableServerRegistriesMixin} notes.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityDamageMixin {
	/** The damage the current {@code hurtServer} call dealt before blocking, and whether a shield blocked any. */
	@Unique
	private float fabricated$damageDealt;
	@Unique
	private boolean fabricated$blocked;

	@WrapMethod(method = "hurtServer")
	private boolean fabricated$keepOuterDamage(ServerLevel level, DamageSource source, float damage, Operation<Boolean> hurtServer) {
		float damageDealt = this.fabricated$damageDealt;
		boolean blocked = this.fabricated$blocked;
		try {
			return hurtServer.call(level, source, damage);
		} finally {
			this.fabricated$damageDealt = damageDealt;
			this.fabricated$blocked = blocked;
		}
	}

	@WrapOperation(method = "hurtServer", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;applyItemBlocking(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)F"))
	private float fabricated$noteDamageDealt(LivingEntity entity, ServerLevel level, DamageSource source, float damage, Operation<Float> applyItemBlocking) {
		float damageBlocked = applyItemBlocking.call(entity, level, source, damage);
		this.fabricated$damageDealt = damage;
		this.fabricated$blocked = damageBlocked > 0.0F;
		return damageBlocked;
	}

	@Inject(method = "hurtServer", at = @At("TAIL"))
	private void fabricated$afterDamage(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		DamageEvents.afterDamage((LivingEntity) (Object) this, source, this.fabricated$damageDealt, damage, this.fabricated$blocked);
	}
}
