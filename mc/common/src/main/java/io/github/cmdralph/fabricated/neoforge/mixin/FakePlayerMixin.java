package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.neoforged.neoforge.common.extensions.IPlayerExtension;

/**
 * Makes Fabric's fake players fake to NeoForge too, so NeoForge's own handling of fake players applies: survival game
 * mode by default and (on 26.2 and later) a no-op advancement tracker. Only adds a method to Fabric's class.
 */
@Mixin(FakePlayer.class)
abstract class FakePlayerMixin implements IPlayerExtension {
	@Override
	public boolean isFakePlayer() {
		return true;
	}
}
