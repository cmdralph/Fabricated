package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.core.registries.BuiltInRegistries;

/** Vanilla's freeze of the built-in registries, run after Fabric mods' main entrypoints ({@code BuiltInRegistriesFreeze}, CORPUS-32). */
@Mixin(BuiltInRegistries.class)
public interface BuiltInRegistriesInvoker {
	@Invoker("freeze")
	static void fabricated$freeze() {
		throw new AssertionError();
	}
}
