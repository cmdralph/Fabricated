package io.github.cmdralph.fabricated.core.report;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The block Fabricated writes when the game stops on a throwable a Fabric mod is involved in, in the log and the crash
 * report, and in its report for a mixin that failed in the last launch: the Fabric mod (its name, id, version and jar),
 * its mixin with the class Mixin applied it to or its class in the stack trace, the loader and the versions, and what
 * Fabricated does about it.
 *
 * <p>A mod is named only where that is certain: a mixin from Mixin's own throwables or error handlers, whose mod its
 * config tells; a class whose package one loaded Fabric mod alone has; a handler Mixin merged into another class, by
 * the mod its name carries where the platform's Mixin puts it there. Where none is, there is no block. The mod Mixin
 * named comes first, then the mods of the frames, the deepest cause's first. It runs as the game stops, so it reads a
 * bounded part of what it is given, and its output is bounded: a few mods, each value cut short, file names only.
 */
public final class DiagnosticsBlock {
	public static final String HEADER = "---- Fabricated: a Fabric mod was involved ----";
	/** The most mods a block names. */
	static final int MAX_MODS = 3;
	/** The longest value of a line (past its label). */
	static final int MAX_VALUE = 160;
	/** The most stack frames read, over all the throwables. */
	static final int MAX_FRAMES = 4096;
	/** The most throwables read: a throwable, its causes and suppressed ones, each once. */
	private static final int MAX_THROWABLES = 64;
	/** Where values start: past the longest label ({@code Fabric mod:}) and two spaces. */
	private static final int LABEL_WIDTH = 13;
	private static final String REPORT = "logs/" + Report.FILE_NAME;
	/**
	 * A handler Mixin merged into a target, as Fabric's Mixin names it: {@code <prefix>$<unique id>$<mod id>$<name>}
	 * ({@code handler$zdp000$examplemod$onTick}).
	 */
	private static final Pattern HANDLER = Pattern.compile("[a-zA-Z]{1,40}\\$[a-z0-9]{3,12}\\$([a-z][a-z0-9_]{1,63})\\$.+");

	/** What Fabricated does for a mixin it records, which the next launch leaves out. */
	public static final String LEFT_OUT = "leaves this mixin out from the next launch (" + REPORT + " says why)";
	/** What Fabricated does for a mixin it could not record. */
	public static final String NOT_RECORDED = "could not record this mixin to leave it out (the log says why); " + REPORT
			+ " says how it loaded the mod";
	/** What Fabricated does for a mod's class in the stack trace. */
	public static final String UNCHANGED = "changes nothing for this; " + REPORT + " says how it loaded the mod";

	/**
	 * The launch: the loader and its build, Minecraft's and Fabricated's versions. Fabricated's is its release, without
	 * the {@code +mc<minecraft>} its jar's version carries: Minecraft's is beside it.
	 */
	public record Launch(String loader, String loaderVersion, String minecraftVersion, String fabricatedVersion) {
		public Launch {
			String suffix = "+mc" + minecraftVersion;
			if (fabricatedVersion != null && minecraftVersion != null && fabricatedVersion.endsWith(suffix) && fabricatedVersion.length() > suffix.length()) {
				fabricatedVersion = fabricatedVersion.substring(0, fabricatedVersion.length() - suffix.length());
			}
		}
	}

	/** What the block says of a Fabric mod: its id, name and version, and its jar's file name (the parent's for a nested one). */
	public record Mod(String id, String name, String version, String jar) {
	}

	/**
	 * A loaded Fabric mod and what tells its code and mixins apart.
	 *
	 * @param platformId   its id on the platform, which Mixin names its mixins and handlers by
	 * @param packages     the packages of its own classes (internal names), which it loads
	 * @param mixinConfigs the names of its mixin configs
	 */
	public record Owner(Mod mod, String platformId, Set<String> packages, Set<String> mixinConfigs) {
	}

	/** One mod the block names: with its mixin, and the class Mixin applied it to (or empty), or with a stack frame. */
	public record Entry(Mod mod, String mixin, String target, String frame) {
	}

	private final Map<String, Mod> byPlatformId = new HashMap<>();
	/** A package's or config's one owner; a package or config two mods have names none. */
	private final Map<String, Mod> byPackage = new HashMap<>();
	private final Map<String, Mod> byConfig = new HashMap<>();
	private final boolean handlersNameMods;

	/**
	 * @param handlersNameMods whether the platform's Mixin puts the mod's id in the names of the handlers it merges
	 *                         (Fabric's fork, which NeoForge runs), so a frame of one tells its mod
	 */
	public DiagnosticsBlock(Collection<Owner> mods, boolean handlersNameMods) {
		this.handlersNameMods = handlersNameMods;
		Set<String> sharedPackages = new HashSet<>();
		Set<String> sharedConfigs = new HashSet<>();
		for (Owner o : mods) {
			byPlatformId.put(o.platformId(), o.mod());
			for (String p : o.packages()) owner(byPackage, p, o.mod(), sharedPackages);
			for (String c : o.mixinConfigs()) owner(byConfig, c, o.mod(), sharedConfigs);
		}
	}

	/** Indexes {@code key}'s owner, unless another mod has it too ({@code shared}: the keys two mods have). */
	private static void owner(Map<String, Mod> index, String key, Mod mod, Set<String> shared) {
		if (shared.contains(key)) return;
		Mod had = index.putIfAbsent(key, mod);
		if (had != null && had != mod) {
			index.remove(key);
			shared.add(key);
		}
	}

	/**
	 * The loaded Fabric mod a mixin is of: the one with the platform id Mixin gave it, else the one that alone has its
	 * config; null for any other mod's.
	 */
	public Mod modOf(String config, String platformModId) {
		if (platformModId != null) return byPlatformId.get(platformModId);
		return config == null ? null : byConfig.get(config);
	}

	/**
	 * The mods a throwable and those given with it involve: {@code first} (the mixin Mixin named, or null), then the
	 * mod of each stack frame in a Fabric mod's code, the deepest cause's frames first, each mod once, at most
	 * {@link #MAX_MODS}.
	 */
	public List<Entry> entries(Throwable thrown, List<Throwable> more, Entry first) {
		List<Entry> entries = new ArrayList<>();
		if (first != null) entries.add(first);
		int read = 0;
		for (Throwable t : throwables(thrown, more)) {
			StackTraceElement[] frames = t.getStackTrace();
			for (int i = 0; i < frames.length && read < MAX_FRAMES && entries.size() < MAX_MODS; i++, read++) {
				Mod mod = owner(frames[i]);
				if (mod != null && entries.stream().noneMatch(e -> e.mod() == mod)) entries.add(new Entry(mod, null, null, frame(frames[i])));
			}
		}
		return entries;
	}

	/** The loaded Fabric mod whose code a frame runs, or null where none is certain. */
	Mod owner(StackTraceElement frame) {
		String className = frame.getClassName();
		int dot = className.lastIndexOf('.');
		Mod mod = dot < 0 ? null : byPackage.get(className.substring(0, dot).replace('.', '/'));
		if (mod != null || !handlersNameMods) return mod;
		Matcher m = HANDLER.matcher(frame.getMethodName());
		return m.matches() ? byPlatformId.get(m.group(1)) : null;
	}

	/**
	 * Each throwable once, at most {@link #MAX_THROWABLES}: of {@code thrown}, then of each of {@code more}, its causes
	 * from the deepest, then their suppressed throwables.
	 */
	static List<Throwable> throwables(Throwable thrown, List<Throwable> more) {
		List<Throwable> out = new ArrayList<>();
		Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		List<Throwable> roots = new ArrayList<>();
		if (thrown != null) roots.add(thrown);
		if (more != null) more.stream().filter(Objects::nonNull).forEach(roots::add);
		for (int r = 0; r < roots.size() && out.size() < MAX_THROWABLES; r++) {
			List<Throwable> causes = new ArrayList<>();
			for (Throwable t = roots.get(r); t != null && causes.size() < MAX_THROWABLES && seen.add(t); t = t.getCause()) causes.add(t);
			for (int i = causes.size() - 1; i >= 0 && out.size() < MAX_THROWABLES; i--) out.add(causes.get(i));
			for (Throwable cause : causes) {
				for (Throwable s : cause.getSuppressed()) {
					if (roots.size() < MAX_THROWABLES && s != null && !seen.contains(s)) roots.add(s);
				}
			}
		}
		return out;
	}

	/** A frame as Java prints it, without its class loader and module. */
	static String frame(StackTraceElement frame) {
		String file = frame.getFileName();
		return frame.getClassName() + "." + frame.getMethodName() + "("
				+ (frame.isNativeMethod() ? "Native Method" : file == null ? "Unknown Source" : frame.getLineNumber() >= 0 ? file + ":" + frame.getLineNumber() : file)
				+ ")";
	}

	/**
	 * The block: a line naming each mod with its mixin or its class, the launch, then what Fabricated does
	 * ({@link #LEFT_OUT}, {@link #NOT_RECORDED}, {@link #UNCHANGED} or another), its lines separated by {@code \n}; null
	 * without an entry.
	 */
	public static String render(List<Entry> entries, Launch launch, String outcome) {
		if (entries == null || entries.isEmpty()) return null;
		StringBuilder sb = new StringBuilder(HEADER);
		for (Entry e : entries.subList(0, Math.min(entries.size(), MAX_MODS))) {
			Mod mod = e.mod();
			line(sb, "Fabric mod:", value(mod.name()) + " (" + value(mod.id()) + ") " + value(mod.version()) + ", " + value(mod.jar()));
			if (e.mixin() != null) {
				line(sb, "Mixin:", value(e.mixin()) + (e.target() == null || e.target().isEmpty() ? "" : ", into " + value(e.target())));
			} else {
				line(sb, "Class:", value(e.frame()));
			}
		}
		line(sb, "Loader:", value(launch.loader()) + " " + value(launch.loaderVersion()) + ", Minecraft " + value(launch.minecraftVersion())
				+ ", Fabricated " + value(launch.fabricatedVersion()));
		// Fabricated's own text, whole.
		line(sb, "Fabricated:", outcome);
		return sb.toString();
	}

	private static void line(StringBuilder sb, String label, String value) {
		sb.append('\n').append(label).append(" ".repeat(Math.max(1, LABEL_WIDTH - label.length()))).append(value);
	}

	/** Mod-supplied text on one line, at most {@link #MAX_VALUE} characters. */
	static String value(String text) {
		if (text == null || text.isBlank()) return "?";
		String flat = text.replaceAll("[\\p{Cntrl}\\s\\u2028\\u2029]+", " ").strip();
		return flat.length() <= MAX_VALUE ? flat : flat.substring(0, MAX_VALUE - 3) + "...";
	}
}
