package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import io.github.cmdralph.fabricated.neoforge.registry.BuiltInRegistriesFreeze;
import net.neoforged.neoforge.registries.GameData;

/**
 * NeoForge's first freeze after Fabric mods' main entrypoints runs vanilla's {@code BuiltInRegistries.freeze} first, if
 * the bootstrap's waits for them ({@link BuiltInRegistriesFreeze}, CORPUS-32).
 */
@Mixin(GameData.class)
abstract class GameDataFreezeMixin {
	@WrapMethod(method = "freezeData")
	private static void fabricated$freezeBuiltInRegistries(Operation<Void> original) {
		BuiltInRegistriesFreeze.freezeData(() -> original.call());
	}
}
