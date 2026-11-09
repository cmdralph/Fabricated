package io.github.cmdralph.fabricated.core.translate;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.cmdralph.fabricated.core.mixin.MixinPreflight.ConstructorInjects;
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
	private static final Version LOCAL_TYPES_UNREAD = parse("0.17.0");
	/** The MixinExtras release NeoForge 21.11 ships, and the last of those after it that only fixed bugs (MIXEXT-2). */
	private static final Version BEFORE_FIX_RELEASES = parse("0.5.3");
	private static final Version LAST_FIX_RELEASE = parse("0.5.5");
	/**
	 * What SpongePowered's own Mixin releases take, from their sources (Forge 47 runs 0.8.5, Forge 61 to 66 run 0.8.7):
	 * the last compatibility level ({@code JAVA_n}) of their CompatibilityLevel enums (LEVEL-1), whether they apply
	 * injectors in interface mixins (INTERFACE-1), which came with 0.8.6, where they take an {@code @Inject} into a
	 * constructor (CTOR-2: {@code Injector.checkTargetForNode}, with {@code InjectionPointData}'s {@code unsafe} from 0.8.6),
	 * and whether they know a method's arguments by their names where a {@code @ModifyVariable} takes arguments only (ARGS-1:
	 * {@code LocalVariableDiscriminator.Context} names them {@code arg} and their slot; Fabric's Mixin takes their names
	 * from 0.17.0), and whether they refuse an {@code @Inject} whose handler is static where its target isn't (LAMBDA-6:
	 * {@code CallbackInjector.sanityCheck} has the modifiers match exactly on 0.8.5; from 0.8.6, as in Fabric's Mixin, it
	 * refuses only a handler that isn't static on a static target). All three rebind a detached mixin's calls to an
	 * interface's super method (SUPER-1: {@code MixinTargetContext.updateStaticBinding}, which Fabric's Mixin stopped doing
	 * for interfaces in 0.15.0), and none sees the fields other mixins add to the same target as it prepares a mixin
	 * ({@code TargetClassContext.findAliasedField} looks in the target's own fields; Fabric's Mixin also looks in the
	 * fields of the mixins prepared before, its {@code mixinFields}, from 0.12.0): a {@code @Shadow} of one isn't found
	 * (CORPUS-74), and a {@code @Unique} field of the same name isn't renamed (CORPUS-95). All three also reuse an earlier
	 * injector's locals for a long or double without checking its second slot (PRE-22: {@code Target.allocateArgMapLocal},
	 * read in 0.8.5 and 0.8.7; Fabric's checks it, read in 0.16.4 and 0.17.4). From 0.8.6 they give a config a mod adds by
	 * its name alone ({@code Mixins.addConfiguration(String)}) no environment (LOADER-6).
	 */
	private record Upstream(int lastLevel, boolean interfaceInjectors, ConstructorInjects constructorInjects, boolean argumentNames,
			boolean exactStaticInjects, boolean rebindsInterfaceSuperCalls, boolean seesMixinFields, boolean overlapsWideArguments,
			boolean addedConfigsWithoutEnvironment) {
	}

	private static final Map<String, Upstream> UPSTREAM = Map.of(
			"0.8.5", new Upstream(18, false, ConstructorInjects.RETURNS_ONLY, false, true, true, false, true, false),
			"0.8.6", new Upstream(21, true, ConstructorInjects.UNSAFE_POINTS, false, false, true, false, true, true),
			"0.8.7", new Upstream(21, true, ConstructorInjects.UNSAFE_POINTS, false, false, true, false, true, true));
	private static final Pattern LEVEL = Pattern.compile("JAVA_(\\d{1,3})");
	private static final Pattern MINECRAFT = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

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
	 * Whether the running Mixin looks up the class of every local variable in the debug tables of a mixin's methods as it
	 * applies the mixin, and stops on one it can't find (LVT-1: {@code MixinTargetContext.transformLVT} through
	 * {@code ClassInfo.forName}, read in the sources of SpongePowered's 0.8.5 to 0.8.7, which Forge runs, and of Fabric's
	 * before 0.17.0; from 0.17.0 Fabric's asks first whether the class is a mixin, and looks up nothing else). False for a
	 * version not known here.
	 */
	public static boolean readsLocalTypes(String runtimeMixinVersion) {
		if (runtimeMixinVersion == null) return false;
		int plus = runtimeMixinVersion.indexOf('+');
		try {
			return parse((plus >= 0 ? runtimeMixinVersion.substring(0, plus) : runtimeMixinVersion).trim()).compareTo(LOCAL_TYPES_UNREAD) < 0;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	/**
	 * The last compatibility level ({@code JAVA_n}) the running Mixin knows, or 0 when it isn't known here. Mixin stops the
	 * game reading a config that declares a level it doesn't know (LEVEL-1). SpongePowered's own releases, which Forge runs
	 * (a plain version, e.g. {@code 0.8.7}), are known up to 0.8.7; Fabric's Mixin, which NeoForge runs (e.g.
	 * {@code 0.17.4+mixin.0.8.7}), isn't limited here.
	 */
	public static int lastCompatibilityLevel(String runtimeMixinVersion) {
		Upstream upstream = upstream(runtimeMixinVersion);
		return upstream == null ? 0 : upstream.lastLevel();
	}

	/**
	 * The Java release a Minecraft version runs on, at the least: 17 up to 1.20.4, 21 from 1.20.5 on, 25 on 26.x; 0 for a
	 * version not known here.
	 */
	public static int minecraftJava(String minecraftVersion) {
		if (minecraftVersion == null) return 0;
		Matcher m = MINECRAFT.matcher(minecraftVersion);
		if (!m.matches()) return 0;
		int major = Integer.parseInt(m.group(1)), minor = Integer.parseInt(m.group(2));
		int patch = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
		if (major >= 26) return 25;
		if (major != 1 || minor < 18) return 0;
		return minor > 20 || minor == 20 && patch >= 5 ? 21 : 17;
	}

	/**
	 * The compatibility level a mixin config declaring {@code declared} ({@code JAVA_n}) can load at: Mixin stops the game
	 * reading a level it doesn't know (LEVEL-1) or one above the running Java (LEVEL-2), so the level is lowered to the
	 * last the running Mixin knows and to the Java the Minecraft version runs on. Mixin checks the language features a
	 * mixin uses against the level, and only logs a mixin class compiled for a newer Java. {@code declared} when it can
	 * load as it is, or when neither bound is known here.
	 */
	public static int loadableLevel(int declared, String runtimeMixinVersion, String minecraftVersion) {
		int bound = lastCompatibilityLevel(runtimeMixinVersion), java = minecraftJava(minecraftVersion);
		if (bound == 0 || java != 0 && java < bound) bound = java;
		return bound == 0 ? declared : Math.min(declared, bound);
	}

	/**
	 * Whether the running Mixin applies injectors in interface mixins and takes their non-public methods (INTERFACE-1).
	 * SpongePowered's 0.8.5, which Forge 47 runs, drops such a mixin whole, logging an error; from 0.8.6 Mixin applies
	 * them, as Fabric's does. True for a version not known here.
	 */
	public static boolean interfaceInjectors(String runtimeMixinVersion) {
		Upstream upstream = upstream(runtimeMixinVersion);
		return upstream == null || upstream.interfaceInjectors();
	}

	/** Where the running Mixin takes an {@code @Inject} into a constructor; {@link ConstructorInjects#AFTER_DELEGATE} for a version not known here. */
	public static ConstructorInjects constructorInjects(String runtimeMixinVersion) {
		Upstream upstream = upstream(runtimeMixinVersion);
		return upstream == null ? ConstructorInjects.AFTER_DELEGATE : upstream.constructorInjects();
	}

	/**
	 * Whether the running Mixin knows a method's arguments by their names where a {@code @ModifyVariable} takes arguments
	 * only (ARGS-1): SpongePowered's names them {@code arg} and their slot. True for a version not known here.
	 */
	public static boolean argumentNames(String runtimeMixinVersion) {
		Upstream upstream = upstream(runtimeMixinVersion);
		return upstream == null || upstream.argumentNames();
	}

	/**
	 * Whether the running Mixin refuses an {@code @Inject} whose handler is static where its target isn't (LAMBDA-6):
	 * SpongePowered's 0.8.5, which Forge 47 runs, does. False for a version not known here.
	 */
	public static boolean exactStaticInjects(String runtimeMixinVersion) {
		Upstream upstream = upstream(runtimeMixinVersion);
		return upstream != null && upstream.exactStaticInjects();
	}

	/**
	 * Whether the running Mixin rebinds a detached mixin's calls to an interface's super method to the target class,
	 * keeping their interface reference (SUPER-1): SpongePowered's releases do. False for a version not known here.
	 */
	public static boolean rebindsInterfaceSuperCalls(String runtimeMixinVersion) {
		Upstream upstream = upstream(runtimeMixinVersion);
		return upstream != null && upstream.rebindsInterfaceSuperCalls();
	}

	/**
	 * Whether the running Mixin sees the fields other mixins add to the same target as it prepares a mixin, as Fabric's
	 * does from 0.12.0. SpongePowered's releases, which Forge runs, see only the target's own fields: they stop the game on
	 * a {@code @Shadow} of a field another mixin adds (CORPUS-74), and merge two mixins' {@code @Unique} fields of the same
	 * name into one (CORPUS-95). True for a version not known here.
	 */
	public static boolean seesMixinFields(String runtimeMixinVersion) {
		Upstream upstream = upstream(runtimeMixinVersion);
		return upstream == null || upstream.seesMixinFields();
	}

	/**
	 * Whether the running Mixin may store a long or double over another local where it reuses the locals it stored an
	 * earlier injector's values in for an injector's values (PRE-22): SpongePowered's releases do, as
	 * {@code Target.allocateArgMapLocal} takes the slot after a reused local for the value's second half unchecked. False
	 * for a version not known here.
	 */
	public static boolean overlapsWideArguments(String runtimeMixinVersion) {
		Upstream upstream = upstream(runtimeMixinVersion);
		return upstream != null && upstream.overlapsWideArguments();
	}

	/**
	 * Whether the running Mixin gives a config a mod adds by its name alone ({@code Mixins.addConfiguration(String)}) no
	 * environment, which stops the game as Mixin reads the config (LOADER-6): SpongePowered's 0.8.6 and 0.8.7, which
	 * Forge 52 and 61 to 66 run. Its 0.8.5 and Fabric's Mixin give the default environment. False for a version not
	 * known here.
	 */
	public static boolean addedConfigsWithoutEnvironment(String runtimeMixinVersion) {
		Upstream upstream = upstream(runtimeMixinVersion);
		return upstream != null && upstream.addedConfigsWithoutEnvironment();
	}

	/**
	 * SpongePowered's release the running Mixin is, by its version without build metadata (Forge 47 reports
	 * {@code 0.8.5+Jenkins-b310.git-155314e6...}), or null. Fabric's Mixin carries the upstream release it is based on in
	 * its metadata ({@code 0.17.4+mixin.0.8.7}), which isn't the release that runs.
	 */
	private static Upstream upstream(String runtimeMixinVersion) {
		if (runtimeMixinVersion == null) return null;
		String version = runtimeMixinVersion.trim();
		int plus = version.indexOf('+');
		if (plus >= 0) {
			if (version.startsWith("mixin.", plus + 1)) return null;
			version = version.substring(0, plus);
		}
		return UPSTREAM.get(version);
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
