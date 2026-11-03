package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.json.JsonException;

/**
 * Another jar's copy of Fabric Loader's API, on Forge (PROVIDER-2): Forgified Fabric API for 1.20.1 nests Forgified
 * Fabric Loader through Forge's jar-in-jar, with the same packages as Fabricated's own copy, and Java can't put two
 * modules exporting one package in the game's layer: Forge stops as it starts, with a {@code ResolutionException}
 * naming neither mod. Where this build's in-game mod nests an empty jar in that copy's place ({@link Placeholder}, on
 * Forge 47), Forge's jar-in-jar adds the empty jar instead and leaves the copy out, so the jar nesting it loads. Any
 * other copy is refused, naming the jar ({@link ConflictingMods}). Only the JDK and core's JSON reader are used here.
 */
final class FabricLoaderCopies {
	/** Fabric Loader's packages (internal form, with a trailing '/'), as NeoForge's reader leaves them out. */
	static final List<String> PACKAGES = List.of("net/fabricmc/loader/", "net/fabricmc/api/");
	/** Where Forge's jar-in-jar keeps the jars it nests. */
	static final String JAR_IN_JAR = "META-INF/jarjar/";
	/** The list of the jars Forge's jar-in-jar nests: each one's identifier, version range and path. */
	static final String JAR_IN_JAR_METADATA = JAR_IN_JAR + "metadata.json";
	/** How deep nested jars are read: Forge's jar-in-jar nests jars in jars, and a copy is rarely deeper than one. */
	static final int MAX_DEPTH = 3;
	/** The most entries read in one jar and the jars it nests, so a crafted archive can't keep discovery reading. */
	static final int MAX_ENTRIES = 200_000;
	/** The build's file (in Fabricated's resources) naming the copy its in-game mod nests an empty jar in place of. */
	static final String PLACEHOLDER_INFO = "loader-placeholder.json";

	private FabricLoaderCopies() {
	}

	/**
	 * What a jar holds of Fabric Loader's API.
	 *
	 * @param copy     "" for its own entries, the path of the nested jar Forge's jar-in-jar would add, or null for none
	 * @param replaced the nested jars with a copy that Forge's jar-in-jar leaves out for the in-game mod's empty jar
	 */
	record Found(String copy, List<String> replaced) {
		static final Found NONE = new Found(null, List.of());
	}

	/**
	 * The empty jar Fabricated's in-game mod nests through Forge's jar-in-jar in place of another jar's copy of Fabric
	 * Loader, under the copy's identifier (Forgified Fabric Loader's, {@code dev.su5ed.sinytra:fabric-loader}) at a
	 * version above any other: Forge's jar-in-jar adds one jar per identifier, the newest version every range naming it
	 * admits, so it adds the empty jar and none of the copies, as Sinytra Connector's does. The build names it in
	 * {@link #PLACEHOLDER_INFO} (mc/platform-forge-legacy.gradle, whose jar check checks the two agree).
	 */
	record Placeholder(String group, String artifact, String version) {
		/** A bare version, which Maven reads as a preference that admits any version. */
		private static final Pattern BARE = Pattern.compile("[^\\[\\](),\\s]+");
		/** A range with a lower bound only: {@code [v,)} or {@code (v,)}. */
		private static final Pattern LOWER_BOUND = Pattern.compile("([\\[(])\\s*([^\\[\\](),\\s]+)\\s*,\\s*\\)");
		/** A version's leading numbers. */
		private static final Pattern NUMBERS = Pattern.compile("(\\d{1,9}(?:\\.\\d{1,9})*).*");

		/** This build's, or null where its in-game mod nests none (Forge 61 to 66, for which no mod nests a copy). */
		static Placeholder ofThisBuild() throws IOException, JsonException {
			byte[] info = FabricatedInfo.readOptional(PLACEHOLDER_INFO);
			return info == null ? null : parse(new String(info, StandardCharsets.UTF_8));
		}

		static Placeholder parse(String json) throws JsonException {
			Map<String, Object> info = Json.parseObject(json);
			if (!(info.get("group") instanceof String group) || !(info.get("artifact") instanceof String artifact)
					|| !(info.get("version") instanceof String version) || numbers(version) == null) {
				throw new JsonException("Not a placeholder: " + json);
			}
			return new Placeholder(group, artifact, version);
		}

		/**
		 * The nested jars Forge's jar-in-jar leaves out for this one, by the jar's metadata: those listed under this
		 * identifier with a range this version satisfies, so that Forge adds this one. With another range, an upper bound
		 * among them, no version would satisfy all, so such a jar isn't counted.
		 */
		Set<String> replacedIn(ZipFile jar) throws IOException {
			ZipEntry entry = jar.getEntry(JAR_IN_JAR_METADATA);
			if (entry == null || entry.getSize() > Json.DEFAULT_MAX_LENGTH) return Set.of();
			Set<String> replaced = new HashSet<>();
			try (InputStream in = jar.getInputStream(entry)) {
				String text = new String(in.readNBytes(Json.DEFAULT_MAX_LENGTH), StandardCharsets.UTF_8);
				for (Object listed : Json.asList(Json.parseObject(text).get("jars"))) {
					if (listed instanceof Map<?, ?> nested && nested.get("path") instanceof String path
							&& nested.get("identifier") instanceof Map<?, ?> id && group.equals(id.get("group")) && artifact.equals(id.get("artifact"))
							&& nested.get("version") instanceof Map<?, ?> version && version.get("range") instanceof String range && admits(range)) {
						replaced.add(path);
					}
				}
			} catch (JsonException e) {
				// Unreadable metadata, which Forge's jar-in-jar fails on itself: nothing counts as replaced.
				return Set.of();
			}
			return replaced;
		}

		/**
		 * Whether a range of Forge's jar-in-jar (in Maven's syntax) admits this version: a bare version, or a lower bound
		 * only whose leading numbers are below this version's (or that is this version, included). Anything else is read
		 * as not admitting it.
		 */
		boolean admits(String range) {
			String spec = range.trim();
			if (BARE.matcher(spec).matches()) return true;
			Matcher bound = LOWER_BOUND.matcher(spec);
			if (!bound.matches()) return false;
			String lower = bound.group(2);
			if (lower.equals(version)) return bound.group(1).equals("[");
			List<Long> below = numbers(lower);
			List<Long> ours = numbers(version);
			if (below == null || ours == null) return false;
			for (int i = 0; i < Math.max(below.size(), ours.size()); i++) {
				long a = i < below.size() ? below.get(i) : 0;
				long b = i < ours.size() ? ours.get(i) : 0;
				if (a != b) return a < b;
			}
			// The same numbers with a qualifier of its own: read as not below.
			return false;
		}

		private static List<Long> numbers(String version) {
			Matcher m = NUMBERS.matcher(version.trim());
			if (!m.matches()) return null;
			List<Long> numbers = new ArrayList<>();
			for (String part : m.group(1).split("\\.")) numbers.add(Long.parseLong(part));
			return numbers;
		}
	}

	/** Whether a path of a jar is in Fabric Loader's packages. */
	static boolean owned(String path) {
		for (String prefix : PACKAGES) {
			if (path.startsWith(prefix)) return true;
		}
		return false;
	}

	/**
	 * Where a jar (not a Fabricated build) has a copy of Fabric Loader's API ({@link Found}); a nested copy the
	 * placeholder replaces counts as none.
	 *
	 * @param placeholder the in-game mod's empty jar in a copy's place, or null where it nests none
	 */
	static Found copyIn(ZipFile jar, Placeholder placeholder) throws IOException {
		int[] budget = {MAX_ENTRIES};
		Enumeration<? extends ZipEntry> entries = jar.entries();
		while (entries.hasMoreElements() && budget[0]-- > 0) {
			if (owned(entries.nextElement().getName())) return new Found("", List.of());
		}
		Set<String> replaceable = placeholder == null ? Set.of() : placeholder.replacedIn(jar);
		List<String> replaced = new ArrayList<>();
		entries = jar.entries();
		while (entries.hasMoreElements()) {
			ZipEntry entry = entries.nextElement();
			if (entry.isDirectory() || !entry.getName().startsWith(JAR_IN_JAR) || !entry.getName().endsWith(".jar")) continue;
			boolean copy;
			try (InputStream in = jar.getInputStream(entry)) {
				copy = nestedCopy(in, 1, budget);
			}
			if (copy && replaceable.contains(entry.getName())) {
				replaced.add(entry.getName());
			} else if (copy) {
				return new Found(entry.getName(), List.copyOf(replaced));
			}
			if (budget[0] <= 0) break;
		}
		return replaced.isEmpty() ? Found.NONE : new Found(null, List.copyOf(replaced));
	}

	/** Whether a nested jar, or a jar it nests in turn, has a copy, within the depth and the entries left. */
	private static boolean nestedCopy(InputStream in, int depth, int[] budget) throws IOException {
		ZipInputStream zip = new ZipInputStream(in);
		for (ZipEntry entry; budget[0]-- > 0 && (entry = zip.getNextEntry()) != null; ) {
			String name = entry.getName();
			if (owned(name)) return true;
			if (depth < MAX_DEPTH && !entry.isDirectory() && name.startsWith(JAR_IN_JAR) && name.endsWith(".jar")
					&& nestedCopy(zip, depth + 1, budget)) return true;
		}
		return false;
	}
}
