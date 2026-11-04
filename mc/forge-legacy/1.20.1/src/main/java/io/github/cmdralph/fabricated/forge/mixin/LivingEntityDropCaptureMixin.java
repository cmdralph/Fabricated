package io.github.cmdralph.fabricated.forge.mixin;

import java.util.ArrayList;
import java.util.Collection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * Keeps a dying entity's drops captured until every mod's hook at the return of {@code dropAllDeathLoot} has run
 * (CORPUS-105). Porting Lib ports Forge's drop capture to Fabric. Its hook at the head of the method starts a capture,
 * and its hook at the return takes the drops, fires its own drops event with them and adds them to the level. On Forge
 * its capture is Forge's: Porting Lib adds {@code captureDrops} to {@code Entity} under Forge's name, and Mixin keeps
 * Forge's method. Forge 47's method takes the drops itself before the return, for its {@code LivingDropsEvent}, so
 * Porting Lib's hook found none and threw as any mob died.
 *
 * <p>Forge's event still fires first. Then its drops stay captured, or an empty capture where a listener cancelled the
 * event, and the hooks at the return run: a mod's port like Porting Lib's takes them there. This mixin's hook comes last,
 * as it applies after the class's other mixins, and adds what is still captured to the level, as Forge's method did.
 */
@Mixin(value = LivingEntity.class, priority = Integer.MAX_VALUE)
abstract class LivingEntityDropCaptureMixin {
	@WrapOperation(method = "dropAllDeathLoot", at = @At(value = "INVOKE",
			target = "Lnet/minecraftforge/common/ForgeHooks;onLivingDrops(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;Ljava/util/Collection;IZ)Z",
			remap = false))
	private boolean fabricated$keepDropsCaptured(LivingEntity entity, DamageSource source, Collection<ItemEntity> drops,
			int lootingLevel, boolean recentlyHit, Operation<Boolean> onLivingDrops) {
		boolean cancelled = onLivingDrops.call(entity, source, drops, lootingLevel, recentlyHit);
		entity.captureDrops(cancelled ? new ArrayList<>() : drops);
		return true;
	}

	@Inject(method = "dropAllDeathLoot", at = @At("RETURN"))
	private void fabricated$addCapturedDrops(DamageSource source, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		Collection<ItemEntity> drops = self.captureDrops(null);
		if (drops != null) drops.forEach(self.level()::addFreshEntity);
	}
}
