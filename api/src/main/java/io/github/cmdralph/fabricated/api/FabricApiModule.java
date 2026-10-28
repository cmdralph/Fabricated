package io.github.cmdralph.fabricated.api;

/**
 * How one Fabric API module is available on this Minecraft version.
 *
 * @param id      module id, for example {@code fabric-networking-api-v1}
 * @param status  how it is provided
 * @param version its version when available, else empty
 * @param reason  why it is unavailable, or a note on how it is provided; empty when there is none
 * @since API 1.0
 */
public record FabricApiModule(String id, Status status, String version, String reason) {
	/** How a Fabric API module is provided. */
	public enum Status {
		/** Implemented by Fabricated on NeoForge's own events and APIs. */
		NATIVE,
		/** Loaded from the player's Fabric API jar. */
		LOADED,
		/** In the player's Fabric API jar, but it cannot load on NeoForge yet. */
		DISABLED,
		/** Would load from Fabric API, but no Fabric API jar provides it. */
		NOT_INSTALLED,
		/** Never available with Fabricated (it would replace parts of the game NeoForge owns). */
		UNSUPPORTED,
		/**
		 * Provided by another installed mod (Forgified Fabric API, or a mod bundling the module, as Sodium does); only one
		 * copy of a module can load, so Fabricated's own stands down.
		 *
		 * @since API 1.1
		 */
		PROVIDED_BY_MOD
	}

	public boolean isAvailable() {
		return status == Status.NATIVE || status == Status.LOADED || status == Status.PROVIDED_BY_MOD;
	}
}
