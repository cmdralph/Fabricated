package io.github.cmdralph.fabricated.fabricloader.version;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
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
	private static final Pattern DOT_SEPARATED_ID = Pattern.compile("|[-0-9A-Za-z]+(\\.[-0-9A-Za-z]+)*");

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
		if (prereleasePart != null && !DOT_SEPARATED_ID.matcher(prereleasePart).matches()) {
			throw new VersionParsingException("Invalid prerelease string '" + prereleasePart + "'!");
		}
		if (buildPart != null && !DOT_SEPARATED_ID.matcher(buildPart).matches()) {
			throw new VersionParsingException("Invalid build string '" + buildPart + "'!");
		}
		if (rest.isEmpty()) {
			throw new VersionParsingException("Did not provide version numbers!");
		}
		if (rest.startsWith(".") || rest.endsWith(".")) {
			throw new VersionParsingException("Version string must not start or end with a dot: '" + version + "'");
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

	static int comparePrerelease(String a, String b) {
		String[] partsA = a.split("\\.", -1);
		String[] partsB = b.split("\\.", -1);
		for (int i = 0; i < Math.min(partsA.length, partsB.length); i++) {
			String pa = partsA[i];
			String pb = partsB[i];
			if (pa.equals(pb)) continue;
			boolean numA = !pa.isEmpty() && pa.chars().allMatch(Character::isDigit);
			boolean numB = !pb.isEmpty() && pb.chars().allMatch(Character::isDigit);
			if (numA && numB) {
				String ta = stripLeadingZeros(pa);
				String tb = stripLeadingZeros(pb);
				if (ta.length() != tb.length()) return Integer.compare(ta.length(), tb.length());
				int cmp = ta.compareTo(tb);
				if (cmp != 0) return cmp;
			} else if (numA) {
				return -1;
			} else if (numB) {
				return 1;
			} else {
				int cmp = pa.compareTo(pb);
				if (cmp != 0) return cmp;
			}
		}
		return Integer.compare(partsA.length, partsB.length);
	}

	private static String stripLeadingZeros(String s) {
		int i = 0;
		while (i < s.length() - 1 && s.charAt(i) == '0') i++;
		return s.substring(i);
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
}
