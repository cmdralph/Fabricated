package io.github.cmdralph.fabricated.core.resolve;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.cmdralph.fabricated.core.archive.ArchiveException;
import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.archive.SafeArchive;
import io.github.cmdralph.fabricated.core.cache.TranslationCache;
import io.github.cmdralph.fabricated.core.metadata.FabricModJsonParser;
import io.github.cmdralph.fabricated.core.metadata.FabricModMetadata;
import io.github.cmdralph.fabricated.core.metadata.MetadataException;
import net.fabricmc.loader.api.metadata.CustomValue;

/**
 * Turns jar files into {@link Candidate}s: validates each archive, parses {@code fabric.mod.json},
 * and recursively extracts nested jars (Fabric jar-in-jar) into the content-addressed cache. When
 * several copies of the same mod id are found, the highest version wins, as in Fabric Loader.
 */
public final class CandidateScanner {
	public static final String FABRIC_MOD_JSON = "fabric.mod.json";
	public static final String NEOFORGE_MODS_TOML = "META-INF/neoforge.mods.toml";

	private final TranslationCache cache;
	private final ArchiveLimits limits;

	public CandidateScanner(TranslationCache cache, ArchiveLimits limits) {
		this.cache = cache;
		this.limits = limits;
	}

	/** A jar that could not be used, with a human-readable reason. */
	public record Rejected(String origin, String reason) {
	}

	/** Scan output: unique candidates (highest version per id), rejections, and informational notes. */
	public record Result(List<Candidate> candidates, List<Rejected> rejected, List<String> notes) {
	}

	public Result scan(List<Path> topLevelJars) {
		Map<String, Candidate> byId = new LinkedHashMap<>();
		List<Rejected> rejected = new ArrayList<>();
		List<String> notes = new ArrayList<>();
		for (Path jar : topLevelJars) {
			scanJar(jar, jar.getFileName().toString(), null, 0, byId, rejected, notes);
		}
		return new Result(List.copyOf(byId.values()), List.copyOf(rejected), List.copyOf(notes));
	}

	private void scanJar(Path jar, String origin, String parentId, int depth, Map<String, Candidate> byId,
			List<Rejected> rejected, List<String> notes) {
		FabricModMetadata metadata;
		List<byte[]> nestedData = new ArrayList<>();
		List<String> nestedNames = new ArrayList<>();
		String sha;
		try (SafeArchive archive = SafeArchive.open(jar, limits)) {
			String json = archive.readText(FABRIC_MOD_JSON);
			if (json == null) {
				rejected.add(new Rejected(origin, "no fabric.mod.json"));
				return;
			}
			metadata = FabricModJsonParser.parse(json);
			if (depth < limits.maxNestingDepth()) {
				for (String nested : metadata.nestedJars()) {
					byte[] data = archive.read(nested);
					if (data == null) {
						notes.add(metadata.getId() + " declares nested jar " + nested + " which is missing; ignored");
						continue;
					}
					nestedData.add(data);
					nestedNames.add(nested);
				}
			} else if (!metadata.nestedJars().isEmpty()) {
				notes.add(metadata.getId() + " nests jars deeper than the limit of " + limits.maxNestingDepth() + "; they were ignored");
			}
			sha = cache.sha256(jar);
		} catch (ArchiveException e) {
			rejected.add(new Rejected(origin, "unsafe or corrupt archive: " + e.getMessage()));
			return;
		} catch (MetadataException e) {
			rejected.add(new Rejected(origin, e.getMessage()));
			return;
		} catch (IOException e) {
			rejected.add(new Rejected(origin, "could not read: " + e.getMessage()));
			return;
		}

		String id = metadata.getId();
		if (PlatformLibraries.PROVIDED_IDS.contains(id)) {
			notes.add(origin + " bundles " + id + ", which NeoForge already provides; the bundled copy is not loaded");
			return;
		}

		List<String> children = new ArrayList<>();
		for (int i = 0; i < nestedData.size(); i++) {
			try {
				Path extracted = cache.storeNested(nestedData.get(i));
				String childOrigin = origin + "!/" + nestedNames.get(i);
				scanJar(extracted, childOrigin, id, depth + 1, byId, rejected, notes);
				Candidate child = findByPath(byId, extracted);
				if (child != null) children.add(child.id());
			} catch (IOException e) {
				rejected.add(new Rejected(origin + "!/" + nestedNames.get(i), "could not extract: " + e.getMessage()));
			}
		}

		CustomValue generated = metadata.getCustomValue("fabric-loom:generated");
		boolean generatedLibrary = generated != null && generated.getType() == CustomValue.CvType.BOOLEAN && generated.getAsBoolean();
		Candidate candidate = new Candidate(jar, sha, metadata, origin, parentId, depth, generatedLibrary, List.copyOf(children));

		Candidate existing = byId.get(id);
		if (existing == null) {
			byId.put(id, candidate);
		} else if (metadata.getVersion().compareTo(existing.metadata().getVersion()) > 0) {
			notes.add("Using " + candidate + " instead of older " + existing);
			byId.put(id, candidate);
		} else if (!existing.sha256().equals(sha)) {
			notes.add("Ignoring " + candidate + "; " + existing + " is the same or newer");
		}
	}

	private static Candidate findByPath(Map<String, Candidate> byId, Path path) {
		for (Candidate c : byId.values()) {
			if (c.path().equals(path)) return c;
		}
		return null;
	}
}
