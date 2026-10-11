package io.github.cmdralph.fabricated.core.translate;

import java.util.Collections;
import java.util.List;

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
	/** Minimum loader version → Mixin behaviour version, newest first (from Fabric Loader 0.19.5). */
	private static final String[][] TABLE = {
			{"0.19.4", "0.17.4"},
			{"0.19.0", "0.17.1"},
			{"0.18.4", "0.17.0"},
			{"0.17.3", "0.16.5"},
			{"0.16.0", "0.14.0"},
			{"0.12.0-", "0.10.0"},
	};
	private static final String FABRIC_FALLBACK = "0.9.2";

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
		if (required.isEmpty() || required.getFirst().getMin() == null) return FABRIC_FALLBACK;
		Version min = required.getFirst().getMin();
		for (String[] row : TABLE) {
			if (min.compareTo(parse(row[0])) >= 0) return row[1];
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
