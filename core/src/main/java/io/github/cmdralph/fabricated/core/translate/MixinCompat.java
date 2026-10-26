package io.github.cmdralph.fabricated.core.translate;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.cmdralph.fabricated.fabricloader.version.VersionParser;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.version.VersionInterval;

/**
 * Computes the Mixin behaviour version a Fabric mod expects, so NeoForge applies its mixins with the
 * same compatibility semantics Fabric Loader would.
 *
 * <p>Fabric Loader derives the level from the lowest {@code fabricloader} version a mod accepts. NeoForge
 * exposes the same knob as {@code [[mixins]] behaviorVersion} and accepts values between its default
 * (0.16.5) and the running Mixin version, so the result is clamped into that range.
 */
public final class MixinCompat {
	private record Row(Version minLoader, String behavior) {
		Row(String minLoader, String behavior) {
			this(parse(minLoader), behavior);
		}
	}

	/** Minimum loader version → Mixin behaviour version, newest first (from Fabric Loader 0.20.1). */
	private static final List<Row> TABLE = List.of(
			new Row("0.20.0", "0.17.5"),
			new Row("0.19.4", "0.17.4"),
			new Row("0.19.0", "0.17.1"),
			new Row("0.18.4", "0.17.0"),
			new Row("0.17.3", "0.16.5"),
			new Row("0.16.0", "0.14.0"),
			new Row("0.12.0-", "0.10.0"));
	private static final String FABRIC_FALLBACK = "0.9.2";
	private static final Version ENUM_EXTENSIONS = parse("0.17.1");
	private static final Version MISSING_TARGETS_FATAL = parse("0.17.4");
	/** The MixinExtras release NeoForge 21.11 ships, and the last of those after it that only fixed bugs (MIXEXT-2). */
	private static final Version BEFORE_FIX_RELEASES = parse("0.5.3");
	private static final Version LAST_FIX_RELEASE = parse("0.5.5");
	/**
	 * The last compatibility level ({@code JAVA_n}) of SpongePowered's own Mixin releases, from their CompatibilityLevel
	 * enums (LEVEL-1): Forge 47 runs 0.8.5, Forge 61 to 66 run 0.8.7.
	 */
	private static final Map<String, Integer> UPSTREAM_LAST_LEVEL = Map.of("0.8.5", 18, "0.8.6", 21, "0.8.7", 21);
	private static final Pattern LEVEL = Pattern.compile("JAVA_(\\d{1,3})");

	private MixinCompat() {
	}

	/** Behaviour version Fabric Loader would pick for a mod with these dependencies. */
	public static String fabricBehaviorVersion(Iterable<ModDependency> dependencies) {
		List<VersionInterval> required = Collections.singletonList(VersionInterval.INFINITE);
		for (ModDependency dep : dependencies) {
			if (!dep.getModId().equals("fabricloader") && !dep.getModId().equals("fabric-loader")) continue;
			if (dep.getKind() == ModDependency.Kind.DEPENDS) {
				required = VersionInterval.and(required, dep.getVersionIntervals());
			} else if (dep.getKind() == ModDependency.Kind.BREAKS) {
				required = VersionInterval.and(required, VersionInterval.not(dep.getVersionIntervals()));
			}
		}
		if (required.isEmpty() || required.get(0).getMin() == null) return FABRIC_FALLBACK;
		Version min = required.get(0).getMin();
		for (Row row : TABLE) {
			if (min.compareTo(row.minLoader()) >= 0) return row.behavior();
		}
		return FABRIC_FALLBACK;
	}

	/**
	 * Clamps a behaviour version into NeoForge's accepted range.
	 *
	 * @param runtimeMixinVersion the running Mixin version, e.g. {@code 0.17.4+mixin.0.8.7}
	 * @param lowest              the lowest behaviour version this FML accepts (its default: 0.16.5 on FML 10,
	 *                            0.17.1 on FML 11 and 12)
	 */
	public static String clampForNeoForge(String behaviorVersion, String runtimeMixinVersion, String lowestBehavior) {
		Version requested = parse(behaviorVersion);
		Version lowest = parse(lowestBehavior);
		String runtimeCore = runtimeMixinVersion.contains("+")
				? runtimeMixinVersion.substring(0, runtimeMixinVersion.indexOf('+'))
				: runtimeMixinVersion;
		Version highest = parse(runtimeCore);
		if (requested.compareTo(lowest) < 0) return lowestBehavior;
		if (requested.compareTo(highest) > 0) return runtimeCore;
		return behaviorVersion;
	}

	/** Whether Mixin, at this behaviour version, rejects a mixin whose target class is missing (from 0.17.4; before, it skips it). */
	public static boolean missingTargetsFatal(String behaviorVersion) {
		return parse(behaviorVersion).compareTo(MISSING_TARGETS_FATAL) >= 0;
	}

	/** Whether the running Mixin (e.g. {@code 0.17.4+mixin.0.8.7}) applies enum extensions, which came with 0.17.1. */
	public static boolean supportsEnumExtensions(String runtimeMixinVersion) {
		int plus = runtimeMixinVersion.indexOf('+');
		return parse(plus >= 0 ? runtimeMixinVersion.substring(0, plus) : runtimeMixinVersion).compareTo(ENUM_EXTENSIONS) >= 0;
	}

	/**
	 * The last compatibility level ({@code JAVA_n}) the running Mixin knows, or 0 when it isn't known here. Mixin stops the
	 * game reading a config that declares a level it doesn't know (LEVEL-1). SpongePowered's own releases, which Forge runs
	 * (a plain version, e.g. {@code 0.8.7}), are known up to 0.8.7; Fabric's Mixin, which NeoForge runs (e.g.
	 * {@code 0.17.4+mixin.0.8.7}), isn't limited here.
	 */
	public static int lastCompatibilityLevel(String runtimeMixinVersion) {
		Integer level = runtimeMixinVersion == null ? null : UPSTREAM_LAST_LEVEL.get(runtimeMixinVersion.trim());
		return level == null ? 0 : level;
	}

	/**
	 * The Java release of a mixin config's {@code compatibilityLevel} as Mixin reads it ({@code JAVA_n}, case and
	 * surrounding blanks ignored), or 0 when it sets none or another value.
	 */
	public static int compatibilityLevel(Object declared) {
		if (!(declared instanceof String s)) return 0;
		Matcher m = LEVEL.matcher(s.trim().toUpperCase(Locale.ROOT));
		return m.matches() ? Integer.parseInt(m.group(1)) : 0;
	}

	/**
	 * Whether a mixin config's {@code mixinextras.minVersion} asks for a newer MixinExtras than the running one, which
	 * MixinExtras answers by stopping the game as it reads the config. False when either isn't a version.
	 */
	public static boolean needsNewerMixinExtras(String required, String running) {
		try {
			return VersionParser.parse(required, false).compareTo(VersionParser.parse(running, false)) > 0;
		} catch (VersionParsingException e) {
			return false;
		}
	}

	/**
	 * Whether the MixinExtras releases after the running one, up to the {@code required} one, only fixed bugs that leave a
	 * config's mixins running as they would with it, with one exception the caller checks: a {@code @Redirect} whose
	 * {@code at} is an array of several points (MIXEXT-2). From their commits: 0.5.4 keeps {@code @Local} contexts per
	 * Fabric compatibility level, in a cache every config on the running MixinExtras shares whatever it asks for; 0.5.5
	 * reads a {@code @Redirect}'s {@code at} written as an array, as mods built against Fabric's Mixin 0.17.4 have it
	 * (MIX-1 makes an array of one a single point, which 0.5.3 reads). Releases after 0.5.5 are not known here.
	 */
	public static boolean onlyFixesUpTo(String required, String running) {
		try {
			return VersionParser.parse(running, false).compareTo(BEFORE_FIX_RELEASES) >= 0
					&& VersionParser.parse(required, false).compareTo(LAST_FIX_RELEASE) <= 0;
		} catch (VersionParsingException e) {
			return false;
		}
	}

	private static Version parse(String s) {
		try {
			Version v = VersionParser.parse(s, false);
			if (!(v instanceof SemanticVersion)) throw new IllegalArgumentException("Not a semantic version: " + s);
			return v;
		} catch (VersionParsingException e) {
			throw new IllegalArgumentException("Invalid version " + s, e);
		}
	}
}
