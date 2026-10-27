package io.github.cmdralph.fabricated.neoforge.registry;

import io.github.cmdralph.fabricated.neoforge.EntrypointBridge;
import io.github.cmdralph.fabricated.neoforge.mixin.BuiltInRegistriesInvoker;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Runs the built-in registries' freeze ({@code BuiltInRegistries.freeze}) after Fabric mods' main entrypoints, as on
 * Fabric, where Fabric API moves vanilla's bootstrap freeze after them (CORPUS-32). Fabric mods mix into it there: Wover
 * makes its item definitions from it, which BetterEnd's main entrypoint subscribes to first. NeoForge freezes the
 * registries during the bootstrap, before any mod is constructed, and again as its registration events end, or on a
 * client as the main and client entrypoints end ({@link EntrypointBridge}).
 *
 * <p>While the main entrypoints are still to run, the bootstrap freezes the registries as {@code freeze} does, without
 * running it, so NeoForge finds them frozen as it expects. {@code freeze} runs in NeoForge's first freeze after the
 * entrypoints ({@code GameData.freezeData}), over the registries NeoForge opened for them; NeoForge's own freeze then
 * doesn't bind the tags of a registry it froze again, which it already bound.
 */
public final class BuiltInRegistriesFreeze {
	/** Set while the bootstrap's {@code freeze} waits for the main entrypoints. */
	private static volatile boolean deferred;
	/** Set while {@code freeze} runs in NeoForge's freeze. */
	private static volatile boolean runningLate;

	private BuiltInRegistriesFreeze() {
	}

	/**
	 * Called for vanilla's bootstrap's {@code freeze}: while the main entrypoints are still to run, freezes the
	 * registries as it does, without running it, and returns true; otherwise returns false, for vanilla's.
	 */
	public static boolean deferBootstrapFreeze() {
		if (!EntrypointBridge.mainEntrypointsPending()) return false;
		((MappedRegistry<?>) BuiltInRegistries.REGISTRY).freeze();
		for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
			MappedRegistry<?> mapped = (MappedRegistry<?>) registry;
			mapped.bindAllTagsToEmpty();
			mapped.freeze();
		}
		deferred = true;
		return true;
	}

	/** Runs NeoForge's freeze, after {@code freeze} if it waits and the main entrypoints have run. */
	public static void freezeData(Runnable neoForge) {
		if (!deferred || EntrypointBridge.mainEntrypointsPending()) {
			neoForge.run();
			return;
		}
		deferred = false;
		runningLate = true;
		try {
			BuiltInRegistriesInvoker.fabricated$freeze();
			neoForge.run();
		} finally {
			runningLate = false;
		}
	}

	/** Whether NeoForge's freeze leaves a registry's tags as they are: those {@code freeze} bound as it froze it. */
	public static boolean tagsBound(boolean frozen) {
		return frozen && runningLate;
	}
}
