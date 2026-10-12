package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.fabricmc.fabric.api.entity.event.v1.effect.FabricMobEffect;
import net.minecraft.world.effect.MobEffect;

/**
 * Makes every mob effect Fabric's {@link FabricMobEffect}, whose methods all have empty defaults; Fabric mods override
 * them in their effects and Fabricated calls them from NeoForge's effect events. No Minecraft code is changed.
 */
@Mixin(MobEffect.class)
abstract class MobEffectMixin implements FabricMobEffect {
}
