package io.github.cmdralph.fabricated.forge;

/**
 * A registry's unregistered intrusive holders, left out of a freeze's check while Fabric mods' main entrypoints are
 * still to run (CORPUS-31): added to {@code MappedRegistry} by its mixin, and called for Forge's own registries too,
 * whose {@code freeze} replaces vanilla's.
 */
public interface PendingHolders {
	/** At a freeze's start: takes the holders out of the check while the main entrypoints are still to run. */
	void fabricated$keepPendingHolders();

	/** At a freeze's end: puts them back for their registration. */
	void fabricated$restorePendingHolders();
}
