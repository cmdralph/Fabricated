package io.github.cmdralph.fabricated.forgetest.mixin;

import org.spongepowered.asm.mixin.Mixin;

import io.github.cmdralph.fabricated.forgetest.ForgeDefaultBridgeTest;
import net.minecraft.class_1309;

/**
 * Adds the test mod's {@link ForgeDefaultBridgeTest.OwnSelf} to {@code LivingEntity} ({@code class_1309}), whose default
 * {@code self()} is a second one there beside Forge's (see {@link ForgeDefaultBridgeTest}).
 */
@Mixin(class_1309.class)
public abstract class LivingEntitySelfMixin implements ForgeDefaultBridgeTest.OwnSelf {
}
