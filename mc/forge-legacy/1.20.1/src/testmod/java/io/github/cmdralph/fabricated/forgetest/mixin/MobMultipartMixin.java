package io.github.cmdralph.fabricated.forgetest.mixin;

import org.spongepowered.asm.mixin.Mixin;

import io.github.cmdralph.fabricated.forgetest.ForgeDefaultBridgeTest;
import net.minecraft.class_1308;

/**
 * Adds the test mod's {@link ForgeDefaultBridgeTest.OwnMultipart} to {@code Mob} ({@code class_1308}), whose default
 * {@code isMultipartEntity()} is a second one there beside the one {@code Mob} has through {@code Entity}'s Forge
 * extension (see {@link ForgeDefaultBridgeTest}).
 */
@Mixin(class_1308.class)
public abstract class MobMultipartMixin implements ForgeDefaultBridgeTest.OwnMultipart {
}
