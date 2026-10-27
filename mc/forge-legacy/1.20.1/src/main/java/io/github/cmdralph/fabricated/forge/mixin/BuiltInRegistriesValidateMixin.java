package io.github.cmdralph.fabricated.forge.mixin;

import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.registries.RegistryManager;

/**
 * Vanilla's check of the built-in registries ({@code BuiltInRegistries.validate}) passes over the empty registries that
 * Forge manages (REG-6). The check runs from vanilla's bootstrap, before Forge creates its own registries; Fabric API
 * moves it, with the bootstrap's freeze, to when the server or client starts, where on Forge it also sees those
 * registries, and an empty one, such as {@code forge:entity_data_serializers} with no mod adding to it, is normal there:
 * vanilla's check logged an error for each. Every other registry is checked as before.
 */
@Mixin(BuiltInRegistries.class)
abstract class BuiltInRegistriesValidateMixin {
	@ModifyArg(method = "validate", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Registry;forEach(Ljava/util/function/Consumer;)V", remap = false))
	private static Consumer<Registry<?>> fabricated$passOverEmptyForgeRegistries(Consumer<Registry<?>> check) {
		return registry -> {
			if (registry.keySet().isEmpty() && RegistryManager.ACTIVE.getRegistry(registry.key().location()) != null) return;
			check.accept(registry);
		};
	}
}
