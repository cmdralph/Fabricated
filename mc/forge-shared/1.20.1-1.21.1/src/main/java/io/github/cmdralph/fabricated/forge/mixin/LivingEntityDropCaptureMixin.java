package io.github.cmdralph.fabricated.forge.mixin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * Keeps a dying entity's drops captured until every mod's hook at the return of {@code dropAllDeathLoot} has run
 * (CORPUS-105). Porting Lib ports Forge's drop capture to Fabric. Its hook at the head of the method starts a capture,
 * and its hook at the return takes the drops, fires its own drops event with them and adds them to the level. On Forge
 * its capture is Forge's: Porting Lib adds {@code captureDrops} to {@code Entity} under Forge's name, and Mixin keeps
 * Forge's method. Forge 47's and 52's method takes the drops itself before the return, for its {@code LivingDropsEvent},
 * so Porting Lib's hook found none and threw as any mob died.
 *
 * <p>Forge's event still fires first, the capture taken as Forge took it. Then its drops are captured again, or an empty
 * capture where a listener cancelled the event, and the hooks at the return run: a mod's port like Porting Lib's takes
 * them there. This mixin's hook comes last, as it applies after the class's other mixins, and adds what is still
 * captured to the level, as Forge's method did. Forge 47 asks its event from {@code ForgeHooks}, Forge 52 from
 * {@code ForgeEventFactory}, which also takes no looting level: the empty capture follows either call. The hooks take
 * none of the method's parameters, which 1.21.1 added the level to.
 */
@Mixin(value = LivingEntity.class, priority = Integer.MAX_VALUE)
abstract class LivingEntityDropCaptureMixin {
	@Inject(method = "dropAllDeathLoot", at = {
			@At(value = "INVOKE", target = "Lnet/minecraftforge/common/ForgeHooks;onLivingDrops(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;Ljava/util/Collection;IZ)Z",
					shift = At.Shift.AFTER, remap = false),
			@At(value = "INVOKE", target = "Lnet/minecraftforge/event/ForgeEventFactory;onLivingDrops(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;Ljava/util/Collection;Z)Z",
					shift = At.Shift.AFTER, remap = false)})
	private void fabricated$captureAfterForgesEvent(CallbackInfo ci) {
		((LivingEntity) (Object) this).captureDrops(new ArrayList<>());
	}

	/** Where no listener cancelled Forge's event, the drops its method would add to the level are captured instead. */
	@WrapWithCondition(method = "dropAllDeathLoot", at = @At(value = "INVOKE", target = "Ljava/util/Collection;forEach(Ljava/util/function/Consumer;)V", remap = false))
	private boolean fabricated$keepDropsCaptured(Collection<ItemEntity> drops, Consumer<? super ItemEntity> addToLevel) {
		((LivingEntity) (Object) this).captureDrops(drops);
		return false;
	}

	@Inject(method = "dropAllDeathLoot", at = @At("RETURN"))
	private void fabricated$addCapturedDrops(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		Collection<ItemEntity> drops = self.captureDrops(null);
		if (drops != null) drops.forEach(self.level()::addFreshEntity);
	}
}
