package io.github.cmdralph.fabricated.core.resolve;

import java.nio.file.Path;
import java.util.List;

import io.github.cmdralph.fabricated.core.metadata.FabricModMetadata;

/**
 * A Fabric jar found during discovery, either directly in the mods folder or nested inside another
 * Fabric jar.
 *
 * @param path           file on disk (nested jars are extracted into the cache first)
 * @param sha256         content hash of {@code path}
 * @param metadata       parsed {@code fabric.mod.json}
 * @param origin         where the jar came from, for messages (the top-level file name, or "parent!/path")
 * @param parentId       id of the containing mod, or {@code null} for top-level jars
 * @param depth          0 for top-level jars
 * @param nestedChildren ids of mods nested directly inside this one
 */
public record Candidate(Path path, String sha256, FabricModMetadata metadata, String origin, String parentId, int depth,
		List<String> nestedChildren) {

	public String id() {
		return metadata.getId();
	}

	public String version() {
		return metadata.getVersion().getFriendlyString();
	}

	@Override
	public String toString() {
		return id() + " " + version() + " (" + origin + ")";
	}
}
