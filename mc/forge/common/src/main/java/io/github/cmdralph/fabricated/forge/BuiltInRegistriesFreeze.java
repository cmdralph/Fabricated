package io.github.cmdralph.fabricated.forge;

import io.github.cmdralph.fabricated.forge.mixin.BuiltInRegistriesInvoker;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Runs the built-in registries' freeze ({@code BuiltInRegistries.freeze}) after Fabric mods' main entrypoints, as on
 * Fabric, where Fabric API moves vanilla's bootstrap freeze after them (CORPUS-32). Fabric mods mix into it there: Wover
 * makes its item definitions from it, which BetterEnd's main entrypoint subscribes to first. Forge freezes the registries
 * during the bootstrap, before any mod is constructed, and again once mods have loaded ({@code GameData.freezeData}).
 *
 * <p>While the main entrypoints are still to run, the bootstrap freezes the registries as {@code freeze} does, without
 * running it, so Forge finds them frozen as it expects. {@code freeze} runs as Forge's freeze after the entrypoints
 * starts, over the registries Forge opened for its registration events, and Forge's freeze then takes them frozen.
 */
public final class BuiltInRegistriesFreeze {
	/** Set while the bootstrap's {@code freeze} waits for the main entrypoints. */
	private static volatile boolean deferred;

	private BuiltInRegistriesFreeze() {
	}

	/**
	 * Called for vanilla's bootstrap's {@code freeze}: while the main entrypoints are still to run, freezes the
	 * registries as it does, without running it, and returns true; otherwise returns false, for vanilla's.
	 */
	public static boolean deferBootstrapFreeze() {
		if (!MainEntrypoints.pending()) return false;
		((MappedRegistry<?>) BuiltInRegistries.REGISTRY).freeze();
		for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
			MappedRegistry<?> mapped = (MappedRegistry<?>) registry;
			mapped.bindAllTagsToEmpty();
			mapped.freeze();
		}
		deferred = true;
		return true;
	}

	/** Called as Forge's freeze starts: runs {@code freeze} if it waits and the main entrypoints have run. */
	public static void beforeFreezeData() {
		if (!deferred || MainEntrypoints.pending()) return;
		deferred = false;
		BuiltInRegistriesInvoker.fabricated$freeze();
	}
}
