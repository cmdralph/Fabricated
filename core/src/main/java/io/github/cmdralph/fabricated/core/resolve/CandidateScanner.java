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
import io.github.cmdralph.fabricated.core.metadata.FabricModMetadata.MixinEntry;
import io.github.cmdralph.fabricated.core.metadata.MetadataException;

/**
 * Turns jar files into {@link Candidate}s: validates each archive, parses {@code fabric.mod.json},
 * and recursively extracts nested jars (Fabric jar-in-jar) into the content-addressed cache. When
 * several copies of the same mod id are found, the highest version wins, as in Fabric Loader.
 *
 * <p>A mixin config a mod declares but a jar nested in it holds is applied by that nested jar: Fabric's Mixin finds a
 * config in any jar on the class path, while a NeoForge mod file declares its own.
 */
public final class CandidateScanner {
	public static final String FABRIC_MOD_JSON = "fabric.mod.json";

	private final TranslationCache cache;
	private final ArchiveLimits limits;
	private final String loader;

	public CandidateScanner(TranslationCache cache, ArchiveLimits limits) {
		this(cache, limits, "NeoForge");
	}

	/** @param loader the platform's loader, as notes name it ({@code NeoForge}, {@code Forge}) */
	public CandidateScanner(TranslationCache cache, ArchiveLimits limits, String loader) {
		this.cache = cache;
		this.limits = limits;
		this.loader = loader;
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
		NestedBudget launch = NestedBudget.forLaunch(limits);
		for (Path jar : topLevelJars) {
			String origin = jar.getFileName().toString();
			scanJar(jar, null, origin, null, List.of(), 0, launch.forTopLevelJar(origin, limits), all, rejected, notes);
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
	 * What jars nested in mods may extract to the cache. The archive limits cap each jar on its own, so a top-level jar's
	 * nested jars, whatever their depth, share one budget, and all the top-level jars of a launch share a larger one:
	 * without them, a jar nesting jars that nest jars, or many such jars, could fill the disk (SEC-5). A nested jar left
	 * out takes nothing from either.
	 */
	private static final class NestedBudget {
		/** The nested jars one top-level jar may hold. */
		static final int MAX_JARS = 1024;
		/** How many top-level jars' budgets a launch holds in all: several times a large pack's nested jars, and bounded. */
		static final int LAUNCH_SHARES = 8;

		private final String scope;
		private final NestedBudget launch;
		private final int maxJars;
		private final long maxBytes;
		private int jars;
		private long bytes;

		private NestedBudget(String scope, NestedBudget launch, int maxJars, long maxBytes) {
			this.scope = scope;
			this.launch = launch;
			this.maxJars = maxJars;
			this.maxBytes = maxBytes;
		}

		static NestedBudget forLaunch(ArchiveLimits limits) {
			long perJar = limits.maxTotalUncompressedBytes();
			return new NestedBudget("this launch's mods", null, MAX_JARS * LAUNCH_SHARES,
					perJar <= Long.MAX_VALUE / LAUNCH_SHARES ? perJar * LAUNCH_SHARES : Long.MAX_VALUE);
		}

		NestedBudget forTopLevelJar(String origin, ArchiveLimits limits) {
			return new NestedBudget(origin, this, MAX_JARS, limits.maxTotalUncompressedBytes());
		}

		/** Takes a nested jar of {@code size} bytes from this budget and the launch's, or says which limit it exceeds. */
		String take(long size) {
			for (NestedBudget b = this; b != null; b = b.launch) {
				if (b.jars >= b.maxJars || size > b.maxBytes - b.bytes) {
					return "nested jars of " + b.scope + " exceed the limit of " + b.maxJars + " jars or " + (b.maxBytes >> 20) + " MiB in all";
				}
			}
			for (NestedBudget b = this; b != null; b = b.launch) {
				b.jars++;
				b.bytes += size;
			}
			return null;
		}
	}

	/**
	 * @param knownSha      the jar's SHA-256 when already known (a nested jar's file name), else null
	 * @param parentsMixins the mixin configs the containing mod declares but doesn't hold: this jar applies those it has
	 * @return the candidate for this jar (added to {@code all} with its nested jars), or null if unusable
	 */
	private Candidate scanJar(Path jar, String knownSha, String origin, String parentId, List<MixinEntry> parentsMixins, int depth, NestedBudget budget,
			List<Candidate> all, List<Rejected> rejected, List<String> notes) {
		FabricModMetadata metadata;
		// Each nested jar is stored as soon as it is read and scanned after this archive is closed, so only one is in
		// memory at a time.
		List<Path> nestedPaths = new ArrayList<>();
		List<String> nestedNames = new ArrayList<>();
		Set<String> packages = new TreeSet<>();
		// The mixin configs it declares that it doesn't hold.
		List<MixinEntry> elsewhere = new ArrayList<>();
		String sha;
		try (SafeArchive archive = SafeArchive.open(jar, limits)) {
			sha = knownSha != null ? knownSha : cache.sha256(jar);
			String json = archive.readText(FABRIC_MOD_JSON);
			if (json == null) {
				rejected.add(new Rejected(origin, "no fabric.mod.json"));
				return null;
			}
			metadata = FabricModJsonParser.parse(json);
			// The mixin configs it declares but doesn't hold, and those of its parent it holds (a list from its
			// compatibility hints replaces the declared one, and is left as it is).
			if (metadata.compatHints().mixins() == null) {
				List<MixinEntry> mixins = new ArrayList<>(metadata.mixins());
				for (MixinEntry entry : metadata.mixins()) {
					if (!archive.contains(entry.config())) elsewhere.add(entry);
				}
				for (MixinEntry entry : parentsMixins) {
					boolean declared = mixins.stream().anyMatch(m -> m.config().equals(entry.config()));
					if (!declared && archive.contains(entry.config())) mixins.add(entry);
				}
				if (mixins.size() > metadata.mixins().size()) metadata = metadata.withMixins(mixins);
			}
			for (String name : archive.classEntryNames()) {
				int slash = name.lastIndexOf('/');
				packages.add(slash < 0 ? "" : name.substring(0, slash));
			}
			if (PlatformLibraries.PROVIDED_IDS.contains(metadata.getId())) {
				notes.add(origin + " bundles " + metadata.getId() + ", which " + loader + " already provides; the bundled copy is not loaded");
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
						String exceeded = budget.take(known != null ? Files.size(known) : data.length);
						if (exceeded != null) {
							rejected.add(new Rejected(nestedOrigin, exceeded));
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
		} catch (RuntimeException | StackOverflowError e) {
			// Its metadata is untrusted: whatever reading it throws rejects this jar, not every Fabric mod.
			rejected.add(new Rejected(origin, "unreadable metadata: " + e));
			return null;
		}

		String id = metadata.getId();
		List<String> children = new ArrayList<>();
		Set<String> applied = new TreeSet<>();
		for (int i = 0; i < nestedPaths.size(); i++) {
			Path nestedJar = nestedPaths.get(i);
			Candidate child = scanJar(nestedJar, TranslationCache.nestedSha(nestedJar), origin + "!/" + nestedNames.get(i), id, elsewhere, depth + 1,
					budget, all, rejected, notes);
			if (child == null) continue;
			children.add(child.id());
			for (MixinEntry entry : elsewhere) {
				boolean has = child.metadata().compatHints().mixinsOr(child.metadata().mixins()).stream().anyMatch(m -> m.config().equals(entry.config()));
				if (!has || !applied.add(entry.config())) continue;
				notes.add(id + "'s mixin config " + entry.config() + " is in its nested jar " + child.id() + ", which applies it");
			}
		}
		if (!applied.isEmpty()) {
			metadata = metadata.withMixins(metadata.mixins().stream().filter(m -> !applied.contains(m.config())).toList());
		}

		Candidate candidate = new Candidate(jar, sha, metadata, origin, parentId, depth, List.copyOf(children), Set.copyOf(packages));
		all.add(candidate);
		return candidate;
	}

}
