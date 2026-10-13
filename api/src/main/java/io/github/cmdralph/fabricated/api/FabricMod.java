package io.github.cmdralph.fabricated.api;

/**
 * A Fabric mod as Fabricated saw it.
 *
 * @param id         Fabric mod id
 * @param neoForgeId the id it has on NeoForge ({@code -} becomes {@code _})
 * @param version    version string from its {@code fabric.mod.json}
 * @param name       display name (the id when it has none)
 * @param source     the jar it came from: {@code file.jar}, or {@code outer.jar!/META-INF/jars/inner.jar}
 * @param status     the outcome
 * @param reason     why it was not loaded, or details of how it was; empty when there are none
 * @param suggestion what the player can do about it; empty when there is nothing
 * @since API 1.0
 */
public record FabricMod(String id, String neoForgeId, String version, String name, String source, Status status, String reason,
		String suggestion) {
	/** The outcome for a Fabric mod. */
	public enum Status {
		/** Translated, handed to NeoForge, and its initializers (if they ran yet) did not fail. */
		LOADED,
		/** Loaded, but one of its initializers threw; its classes are present but it may not work. */
		FAILED,
		/** Not loaded: a missing dependency, a mixin that cannot apply, a package clash, and so on. */
		DISABLED,
		/** Not loaded by design: disabled in the config, for the other side, or replaced by a NeoForge build or Fabricated. */
		SKIPPED
	}

	public boolean isLoaded() {
		return status == Status.LOADED;
	}
}
