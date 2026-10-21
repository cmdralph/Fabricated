package io.github.cmdralph.fabricated.fabricloader.version;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

/**
 * Semantic version as understood by Fabric Loader: dot-separated numeric components, an optional
 * pre-release key after {@code -} and optional build metadata after {@code +}.
 *
 * <p>Clean-room implementation of the documented Fabric semantics. When parsed for predicates
 * ({@code storeX = true}) the components {@code x}, {@code X} and {@code *} are stored as
 * {@link SemanticVersion#COMPONENT_WILDCARD}.
 */
public final class SemanticVersionImpl implements SemanticVersion {
	private static final Pattern UNSIGNED_INTEGER = Pattern.compile("0|[1-9][0-9]*");

	private final int[] components;
	private final String prerelease;
	private final String build;
	private final String friendlyName;

	public SemanticVersionImpl(String version, boolean storeX) throws VersionParsingException {
		Objects.requireNonNull(version, "version");
		String rest = version;
		String buildPart = null;
		int buildDelim = rest.indexOf('+');
		if (buildDelim >= 0) {
			buildPart = rest.substring(buildDelim + 1);
			rest = rest.substring(0, buildDelim);
		}
		String prereleasePart = null;
		int dashDelim = rest.indexOf('-');
		if (dashDelim >= 0) {
			prereleasePart = rest.substring(dashDelim + 1);
			rest = rest.substring(0, dashDelim);
		}
		if (prereleasePart != null && !dotSeparatedIds(prereleasePart)) {
			throw new VersionParsingException("Invalid prerelease string '" + prereleasePart + "'!");
		}
		if (buildPart != null && !dotSeparatedIds(buildPart)) {
			throw new VersionParsingException("Invalid build string '" + buildPart + "'!");
		}
		if (rest.isEmpty()) {
			throw new VersionParsingException("Did not provide version numbers!");
		}
		String[] parts = rest.split("\\.", -1);
		int[] parsed = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			String part = parts[i];
			if (storeX && (part.equals("x") || part.equals("X") || part.equals("*"))) {
				if (prereleasePart != null) {
					throw new VersionParsingException("Pre-release versions are not allowed to use X-ranges!");
				}
				parsed[i] = COMPONENT_WILDCARD;
				continue;
			}
			if (i > 0 && parsed[i - 1] == COMPONENT_WILDCARD) {
				throw new VersionParsingException("Interjacent wildcard (1.x.2) are disallowed!");
			}
			if (part.trim().isEmpty()) {
				throw new VersionParsingException("Missing version number component!");
			}
			if (!part.chars().allMatch(c -> c >= '0' && c <= '9')) {
				throw new VersionParsingException("Could not parse version number component '" + part + "'!");
			}
			try {
				parsed[i] = Integer.parseInt(part);
			} catch (NumberFormatException e) {
				throw new VersionParsingException("Could not parse version number component '" + part + "'!", e);
			}
		}
		if (storeX && parsed.length == 1 && parsed[0] == COMPONENT_WILDCARD) {
			throw new VersionParsingException("Versions of form 'x' or 'X' not allowed!");
		}
		// As Fabric: extra trailing wildcards are dropped (1.x.x is 1.x).
		int firstWildcard = parsed.length;
		while (firstWildcard > 1 && parsed[firstWildcard - 1] == COMPONENT_WILDCARD) firstWildcard--;
		if (firstWildcard + 1 < parsed.length) parsed = Arrays.copyOf(parsed, firstWildcard + 1);
		this.components = parsed;
		this.prerelease = prereleasePart;
		this.build = buildPart;
		this.friendlyName = buildFriendlyName();
	}

	/**
	 * Creates a version from already-validated parts. An empty (non-null) pre-release string
	 * produces the lowest possible version for the given components, which is what upper bounds
	 * of {@code ~} and {@code ^} ranges use so that pre-releases of the next version are excluded.
	 */
	public SemanticVersionImpl(int[] components, String prerelease, String build) {
		if (components.length == 0) {
			throw new IllegalArgumentException("Invalid components " + Arrays.toString(components));
		}
		this.components = components.clone();
		this.prerelease = prerelease;
		this.build = build;
		this.friendlyName = buildFriendlyName();
	}

	private String buildFriendlyName() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < components.length; i++) {
			if (i > 0) sb.append('.');
			sb.append(components[i] == COMPONENT_WILDCARD ? "x" : Integer.toString(components[i]));
		}
		if (prerelease != null) sb.append('-').append(prerelease);
		if (build != null) sb.append('+').append(build);
		return sb.toString();
	}

	@Override
	public int getVersionComponentCount() {
		return components.length;
	}

	@Override
	public int getVersionComponent(int pos) {
		if (pos < 0) throw new IndexOutOfBoundsException("Tried to access negative version number component!");
		if (pos >= components.length) {
			return components[components.length - 1] == COMPONENT_WILDCARD ? COMPONENT_WILDCARD : 0;
		}
		return components[pos];
	}

	@Override
	public Optional<String> getPrereleaseKey() {
		return Optional.ofNullable(prerelease);
	}

	@Override
	public Optional<String> getBuildKey() {
		return Optional.ofNullable(build);
	}

	@Override
	public boolean hasWildcard() {
		for (int c : components) {
			if (c == COMPONENT_WILDCARD) return true;
		}
		return false;
	}

	@Override
	public String getFriendlyString() {
		return friendlyName;
	}

	@Override
	public int compareTo(Version other) {
		if (!(other instanceof SemanticVersion o)) {
			return getFriendlyString().compareTo(other.getFriendlyString());
		}
		int count = Math.max(getVersionComponentCount(), o.getVersionComponentCount());
		for (int i = 0; i < count; i++) {
			int first = getVersionComponent(i);
			int second = o.getVersionComponent(i);
			if (first == COMPONENT_WILDCARD || second == COMPONENT_WILDCARD) continue;
			int cmp = Integer.compare(first, second);
			if (cmp != 0) return cmp;
		}
		Optional<String> preA = getPrereleaseKey();
		Optional<String> preB = o.getPrereleaseKey();
		if (preA.isPresent() && preB.isPresent()) {
			return comparePrerelease(preA.get(), preB.get());
		} else if (preA.isPresent()) {
			return o.hasWildcard() ? 0 : -1;
		} else if (preB.isPresent()) {
			return hasWildcard() ? 0 : 1;
		}
		return 0;
	}

	/**
	 * As Fabric: dot-separated parts, compared numerically when both are unsigned integers without leading zeros
	 * (shorter first), numbers before other parts, others in ASCII order; a key that runs out of parts first is lower.
	 */
	static int comparePrerelease(String a, String b) {
		StringTokenizer partsA = new StringTokenizer(a, ".");
		StringTokenizer partsB = new StringTokenizer(b, ".");
		while (partsA.hasMoreTokens()) {
			if (!partsB.hasMoreTokens()) return 1;
			String pa = partsA.nextToken();
			String pb = partsB.nextToken();
			boolean numA = UNSIGNED_INTEGER.matcher(pa).matches();
			boolean numB = UNSIGNED_INTEGER.matcher(pb).matches();
			if (numA != numB) return numA ? -1 : 1;
			if (numA && pa.length() != pb.length()) return Integer.compare(pa.length(), pb.length());
			int cmp = pa.compareTo(pb);
			if (cmp != 0) return cmp;
		}
		return partsB.hasMoreTokens() ? -1 : 0;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof SemanticVersionImpl other)) return false;
		return Arrays.equals(components, other.components)
				&& Objects.equals(prerelease, other.prerelease)
				&& Objects.equals(build, other.build);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(components) * 73 + Objects.hashCode(prerelease) * 11 + Objects.hashCode(build);
	}

	@Override
	public String toString() {
		return friendlyName;
	}

	/**
	 * Whether {@code s} is empty or identifiers of {@code [-0-9A-Za-z]} separated by dots. Checked part by part: a
	 * pattern repeating a group recurses once per part, so a version with thousands of them overflowed the stack.
	 */
	private static boolean dotSeparatedIds(String s) {
		if (s.isEmpty()) return true;
		for (String part : s.split("\\.", -1)) {
			if (part.isEmpty()) return false;
			for (int i = 0; i < part.length(); i++) {
				char c = part.charAt(i);
				if (!(c == '-' || c >= '0' && c <= '9' || c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z')) return false;
			}
		}
		return true;
	}
}
