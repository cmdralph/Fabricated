package io.github.cmdralph.fabricated.core.translate;

import java.util.Set;
import java.util.regex.Pattern;

/** Conversion between Fabric and NeoForge mod ids. */
public final class ModIds {
	/** NeoForge's mod id rule. */
	public static final Pattern NEOFORGE_ID = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");

	/** Ids owned by the platform that a Fabric mod may not take. */
	public static final Set<String> RESERVED = Set.of("minecraft", "neoforge", "java", "fabricloader", "fabric_loader", "fabricated", "mcp", "forge");

	private ModIds() {
	}

	/**
	 * Converts a valid Fabric id to a NeoForge id. Fabric additionally allows {@code -}, which becomes
	 * {@code _}; the original id stays available as an alias for Fabric API lookups.
	 */
	public static String toNeoForgeId(String fabricId) {
		return fabricId.replace('-', '_');
	}

	public static boolean isValidNeoForgeId(String id) {
		return NEOFORGE_ID.matcher(id).matches();
	}
}
