package io.github.cmdralph.fabricated.core.metadata;

import java.util.Set;

/** Conversion between Fabric and NeoForge mod ids. */
public final class ModIds {
	/** Fabricated's own mod id, on NeoForge and in Fabric's API. */
	public static final String FABRICATED = "fabricated";
	/** Ids owned by the platform that a Fabric mod may not take. */
	public static final Set<String> RESERVED = Set.of("minecraft", "neoforge", "java", "fabricloader", "fabric_loader", FABRICATED, "mcp", "forge");

	private ModIds() {
	}

	/**
	 * Converts a valid Fabric id to a NeoForge id, which is then valid too: Fabric additionally allows {@code -}, which
	 * becomes {@code _}; the original id stays available as an alias for Fabric API lookups.
	 */
	public static String toNeoForgeId(String fabricId) {
		return fabricId.replace('-', '_');
	}
}
