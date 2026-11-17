package io.github.cmdralph.fabricated.testmod.mixin;

import org.spongepowered.asm.mixin.Mixin;

import io.github.cmdralph.fabricated.testmod.DefaultBridgeTest;
import net.minecraft.world.entity.LivingEntity;

/**
 * Adds the test mod's {@link DefaultBridgeTest.OwnSelf} to {@code LivingEntity}, whose default {@code self()} is a second
 * one there beside NeoForge's (see {@link DefaultBridgeTest}).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySelfMixin implements DefaultBridgeTest.OwnSelf {
}
