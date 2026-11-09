package io.github.cmdralph.fabricated.testmod.mixin;

import org.spongepowered.asm.mixin.Mixin;

import io.github.cmdralph.fabricated.testmod.DefaultBridgeTest;
import net.minecraft.world.entity.Mob;

/**
 * Adds the test mod's {@link DefaultBridgeTest.OwnMultipart} to {@code Mob}, whose default {@code isMultipartEntity()} is
 * a second one there beside the one {@code Mob} has through {@code Entity}'s NeoForge extension (see
 * {@link DefaultBridgeTest}).
 */
@Mixin(Mob.class)
public abstract class MobMultipartMixin implements DefaultBridgeTest.OwnMultipart {
}
