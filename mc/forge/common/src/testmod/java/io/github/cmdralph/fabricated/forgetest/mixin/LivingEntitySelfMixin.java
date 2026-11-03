package io.github.cmdralph.fabricated.forgetest.mixin;

import org.spongepowered.asm.mixin.Mixin;

import io.github.cmdralph.fabricated.forgetest.ForgeDefaultBridgeTest;
import net.minecraft.world.entity.LivingEntity;

/**
 * Adds the test mod's {@link ForgeDefaultBridgeTest.OwnSelf} to {@code LivingEntity}, whose default {@code self()} is a
 * second one there beside Forge's (see {@link ForgeDefaultBridgeTest}).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySelfMixin implements ForgeDefaultBridgeTest.OwnSelf {
}
