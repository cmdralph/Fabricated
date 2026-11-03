package io.github.cmdralph.fabricated.forgetest.mixin;

import org.spongepowered.asm.mixin.Mixin;

import io.github.cmdralph.fabricated.forgetest.ForgeDefaultBridgeTest;
import net.minecraft.world.entity.Mob;

/**
 * Adds the test mod's {@link ForgeDefaultBridgeTest.OwnMultipart} to {@code Mob}, whose default
 * {@code isMultipartEntity()} is a second one there beside the one {@code Mob} has through {@code Entity}'s Forge
 * extension (see {@link ForgeDefaultBridgeTest}).
 */
@Mixin(Mob.class)
public abstract class MobMultipartMixin implements ForgeDefaultBridgeTest.OwnMultipart {
}
