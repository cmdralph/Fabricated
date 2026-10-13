package io.github.cmdralph.fabricated.api;

import java.util.List;
import java.util.Optional;

/**
 * What Fabricated did this launch, and when Fabric mods' initializers have run. Thread-safe. Implemented by Fabricated
 * only; obtain it from {@link Fabricated#get()}.
 *
 * @since API 1.0
 */
public interface FabricatedApi {
	/** Fabricated's version, for example {@code 0.2.0+mc26.2}. */
	String fabricatedVersion();

	/** The API version this runtime implements, for example {@code 1.0.0}. */
	String apiVersion();

	/** The Fabric API release this version's module plan targets, for example {@code 0.161.0+26.2}. */
	String fabricApiVersion();

	/**
	 * Every Fabric mod Fabricated considered (top-level and nested), in report order, with its outcome. Jars that are
	 * not usable Fabric mods are not listed (see {@link #renderReport()}).
	 */
	List<FabricMod> fabricMods();

	/** A Fabric mod by Fabric id ({@code cloth-config}) or NeoForge id ({@code cloth_config}). */
	Optional<FabricMod> fabricMod(String id);

	/** Whether the Fabric mod is loaded and its initializers did not fail. */
	default boolean isFabricModLoaded(String id) {
		return fabricMod(id).map(FabricMod::isLoaded).orElse(false);
	}

	/** Every module of Fabric API on this Minecraft version, with how Fabricated provides it. */
	List<FabricApiModule> fabricApiModules();

	/** A Fabric API module by id, for example {@code fabric-transfer-api-v1}. */
	Optional<FabricApiModule> fabricApiModule(String id);

	/** Whether the Fabric mods' {@code main} and {@code client}/{@code server} initializers have all run. */
	boolean entrypointsComplete();

	/**
	 * Runs {@code action} once the Fabric mods' {@code main} and {@code client}/{@code server} initializers have run
	 * (during NeoForge's registration, on the mod loading thread), or right away on the calling thread when they already
	 * have. A waiting action that throws is logged and reported as a loading warning, and does not stop the others.
	 */
	void afterEntrypoints(Runnable action);

	/** The text of {@code logs/fabric-bridge-report.txt} as it is now. */
	String renderReport();
}
