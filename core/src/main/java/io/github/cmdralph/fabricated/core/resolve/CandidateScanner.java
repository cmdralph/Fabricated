package io.github.cmdralph.fabricated.core.resolve;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import io.github.cmdralph.fabricated.core.archive.ArchiveException;
import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.archive.SafeArchive;
import io.github.cmdralph.fabricated.core.cache.TranslationCache;
import io.github.cmdralph.fabricated.core.metadata.FabricModJsonParser;
import io.github.cmdralph.fabricated.core.metadata.FabricModMetadata;
import io.github.cmdralph.fabricated.core.metadata.MetadataException;

/**
 * Turns jar files into {@link Candidate}s: validates each archive, parses {@code fabric.mod.json},
 * and recursively extracts nested jars (Fabric jar-in-jar) into the content-addressed cache. When
 * several copies of the same mod id are found, the highest version wins, as in Fabric Loader.
 */
public final class CandidateScanner {
	public static final String FABRIC_MOD_JSON = "fabric.mod.json";

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
		List<Candidate> all = new ArrayList<>();
		List<Rejected> rejected = new ArrayList<>();
		List<String> notes = new ArrayList<>();
		for (Path jar : topLevelJars) {
			scanJar(jar, null, jar.getFileName().toString(), null, 0, new NestedBudget(), all, rejected, notes);
		}
		return new Result(List.copyOf(select(all, notes).values()), List.copyOf(rejected), List.copyOf(notes));
	}

	/**
	 * Picks one candidate per mod id, the highest version, as Fabric Loader does. A nested jar only competes when the
	 * jar containing it was picked: the libraries an older, unused copy of a mod bundles are not loaded.
	 */
	private static Map<String, Candidate> select(List<Candidate> all, List<String> notes) {
		Map<String, Candidate> chosen = Map.of();
		// Choosing a nested copy of a mod can make that copy's own nested jars eligible, so repeat until stable.
		for (int round = 0; round < 8; round++) {
			Map<String, Candidate> next = new LinkedHashMap<>();
			for (Candidate c : all) {
				if (c.parentId() != null && !containedInChosen(c, chosen)) continue;
				Candidate existing = next.get(c.id());
				if (existing == null || c.metadata().getVersion().compareTo(existing.metadata().getVersion()) > 0) next.put(c.id(), c);
			}
			if (next.equals(chosen)) break;
			chosen = next;
		}
		for (Candidate c : all) {
			Candidate winner = chosen.get(c.id());
			if (winner == null || winner == c || winner.sha256().equals(c.sha256())) continue;
			if (c.parentId() != null && !containedInChosen(c, chosen)) continue;
			notes.add(c.metadata().getVersion().compareTo(winner.metadata().getVersion()) < 0
					? "Using " + winner + " instead of older " + c
					: "Ignoring " + c + "; " + winner + " is the same or newer");
		}
		return chosen;
	}

	private static boolean containedInChosen(Candidate nested, Map<String, Candidate> chosen) {
		Candidate parent = chosen.get(nested.parentId());
		return parent != null && nested.origin().startsWith(parent.origin() + "!/") && nested.depth() == parent.depth() + 1;
	}

	/**
	 * What a top-level jar and everything nested in it may extract to the cache together: the archive limits cap each
	 * jar on its own, so without this a jar nesting jars that nest jars could fill the disk.
	 */
	private static final class NestedBudget {
		static final int MAX_JARS = 1024;
		long bytes;
		int jars;
	}

	/**
	 * @param knownSha the jar's SHA-256 when already known (a nested jar's file name), else null
	 * @return the candidate for this jar (added to {@code all} with its nested jars), or null if unusable
	 */
	private Candidate scanJar(Path jar, String knownSha, String origin, String parentId, int depth, NestedBudget budget, List<Candidate> all,
			List<Rejected> rejected, List<String> notes) {
		FabricModMetadata metadata;
		// Each nested jar is stored as soon as it is read and scanned after this archive is closed, so only one is in
		// memory at a time.
		List<Path> nestedPaths = new ArrayList<>();
		List<String> nestedNames = new ArrayList<>();
		Set<String> packages = new TreeSet<>();
		String sha;
		try (SafeArchive archive = SafeArchive.open(jar, limits)) {
			sha = knownSha != null ? knownSha : cache.sha256(jar);
			String json = archive.readText(FABRIC_MOD_JSON);
			if (json == null) {
				rejected.add(new Rejected(origin, "no fabric.mod.json"));
				return null;
			}
			metadata = FabricModJsonParser.parse(json);
			for (String name : archive.classEntryNames()) {
				int slash = name.lastIndexOf('/');
				packages.add(slash < 0 ? "" : name.substring(0, slash));
			}
			if (PlatformLibraries.PROVIDED_IDS.contains(metadata.getId())) {
				notes.add(origin + " bundles " + metadata.getId() + ", which NeoForge already provides; the bundled copy is not loaded");
				return null;
			}
			if (depth < limits.maxNestingDepth()) {
				for (String nested : metadata.nestedJars()) {
					String nestedOrigin = origin + "!/" + nested;
					try {
						// Extracted on an earlier launch from this very jar: no need to decompress it again.
						Path known = cache.nested(sha, nested).orElse(null);
						byte[] data = known == null ? archive.read(nested) : null;
						if (known == null && data == null) {
							notes.add(metadata.getId() + " declares nested jar " + nested + " which is missing; ignored");
							continue;
						}
						budget.bytes += known != null ? Files.size(known) : data.length;
						if (++budget.jars > NestedBudget.MAX_JARS || budget.bytes > limits.maxTotalUncompressedBytes()) {
							rejected.add(new Rejected(nestedOrigin, "nested jars of " + origin.split("!/", 2)[0] + " exceed the limit of "
									+ NestedBudget.MAX_JARS + " jars or " + (limits.maxTotalUncompressedBytes() >> 20) + " MiB in all"));
							continue;
						}
						nestedPaths.add(known != null ? known : cache.storeNested(data, sha, nested));
						nestedNames.add(nested);
					} catch (IOException e) {
						rejected.add(new Rejected(nestedOrigin, "could not extract: " + e.getMessage()));
					}
				}
			} else if (!metadata.nestedJars().isEmpty()) {
				notes.add(metadata.getId() + " nests jars deeper than the limit of " + limits.maxNestingDepth() + "; they were ignored");
			}
		} catch (ArchiveException e) {
			rejected.add(new Rejected(origin, "unsafe or corrupt archive: " + e.getMessage()));
			return null;
		} catch (MetadataException e) {
			rejected.add(new Rejected(origin, e.getMessage()));
			return null;
		} catch (IOException e) {
			rejected.add(new Rejected(origin, "could not read: " + e.getMessage()));
			return null;
		}

		String id = metadata.getId();
		List<String> children = new ArrayList<>();
		for (int i = 0; i < nestedPaths.size(); i++) {
			Path nestedJar = nestedPaths.get(i);
			Candidate child = scanJar(nestedJar, TranslationCache.nestedSha(nestedJar), origin + "!/" + nestedNames.get(i), id, depth + 1, budget, all,
					rejected, notes);
			if (child != null) children.add(child.id());
		}

		Candidate candidate = new Candidate(jar, sha, metadata, origin, parentId, depth, List.copyOf(children), Set.copyOf(packages));
		all.add(candidate);
		return candidate;
	}

}
