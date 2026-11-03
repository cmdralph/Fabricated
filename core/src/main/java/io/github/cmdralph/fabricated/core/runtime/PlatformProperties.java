package io.github.cmdralph.fabricated.core.runtime;

import java.util.Arrays;
import java.util.Properties;

/**
 * The system properties the platform sets that Fabric mods can't read as Fabric gives them.
 *
 * <p>FML's service provider (NeoForge 21.1's, and Forge 47's) sets {@code forge.logging.marker.<name>} to {@code ACCEPT}
 * for each name in {@code forge.logging.markers}, which a launch outside development doesn't set: the empty list's one
 * name is empty, so {@code forge.logging.marker.} is set, its name ending in a dot. No logging configuration reads it (the
 * markers' lookups name their marker). A Fabric mod reading every system property as a tree of dotted paths stops on its
 * empty last part: Ledger's configuration, through konf, failed its server initializer on every NeoForge 21.1 server
 * ({@code InvalidPathException: "forge.logging.marker." is not a valid path}, CORPUS-60).
 */
public final class PlatformProperties {
	/** The property FML sets for the empty marker name. */
	public static final String EMPTY_LOG_MARKER = "forge.logging.marker.";
	private static final String LOG_MARKERS = "forge.logging.markers";
	private static final String ACCEPT = "ACCEPT";

	private PlatformProperties() {
	}

	/**
	 * Removes {@link #EMPTY_LOG_MARKER} where it is FML's: set to {@code ACCEPT} with no marker list, or one naming an empty
	 * marker. Whether it was removed.
	 */
	public static boolean dropEmptyLogMarker(Properties properties) {
		if (!ACCEPT.equals(properties.getProperty(EMPTY_LOG_MARKER))) return false;
		String markers = properties.getProperty(LOG_MARKERS, "");
		// As FML splits it: "".split(",") is the one empty name; a list with names ("REGISTRIES") has none, unless it
		// has an empty one between them (",,") or first (",SCAN").
		if (!Arrays.asList(markers.split(",")).contains("")) return false;
		properties.remove(EMPTY_LOG_MARKER);
		return true;
	}

	/** {@link #dropEmptyLogMarker(Properties)} for the JVM's system properties; never throws. */
	public static boolean dropEmptyLogMarker() {
		try {
			return dropEmptyLogMarker(System.getProperties());
		} catch (SecurityException e) {
			return false;
		}
	}
}
