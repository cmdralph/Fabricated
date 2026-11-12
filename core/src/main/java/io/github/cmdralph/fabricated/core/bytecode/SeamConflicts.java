package io.github.cmdralph.fabricated.core.bytecode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.core.archive.Hashing;
import io.github.cmdralph.fabricated.core.cache.TranslationCache;
import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.json.JsonException;

/**
 * The native mods' mixins that keep a class's seams from being put back (SEAM-1, {@link VanillaSeams}): putting vanilla's
 * method back moves the platform's calls where vanilla called it into that method ({@link VanillaSeams.Rewrite}), so a
 * native mod's mixin hooking those calls, or a place in the methods holding them that the move shifts, would find
 * nothing there, and Mixin would stop the game where it requires them. Moonlight's NeoForge build (3.8.3 for 1.21.1)
 * wraps the resolver's {@code isStickyBlock} and {@code canStickTo} calls in a required config. Such a class is left as the
 * platform has it, as it loads and in pre-flight's view, so the Fabric mods' hooks of its seams find nothing, as before
 * SEAM-1, and the report names the native mod.
 * <p>
 * Read from the mixins the installed native mods' configs list ({@link InstalledClasses#nativeMixins}), their annotations
 * only. A mixin into the class conflicts where any of its injectors (Mixin's and MixinExtras', expressions among them):
 * names a moved call in any string of its annotations, or uses a wildcard target; selects methods by a pattern of names
 * (Mixin's {@code *} alone, a regex) or a name pre-flight can't find in the class, whatever it hooks there, a name with
 * a quantifier ({@code tick*}) or a descriptor annotation ({@code @Desc}) selecting that name's methods; or selects a method holding moved calls with a point the move may shift (any but the method's start
 * and returns, a call, a field access, a construction or a constant, or one shifted by instructions); or where it
 * overwrites a method holding moved calls. Its annotations' strings are read as its config's refmap maps them, as
 * Mixin reads them (Forge 47's mods name the game's methods by Mojang's names, which their refmaps map to the SRG names
 * the game runs with), and as written. A mixin that can't be read, or whose refmap fails to read, conflicts too:
 * pre-flight can't tell what it hooks.
 * <p>
 * Where vanilla's calls are put back for the blocks keeping an extension's default (SEAM-3), a mixin conflicts too where
 * any of its injectors selects a method no longer reached for those blocks (the platform's private {@code removeBlock}),
 * or selects a method holding put-back calls (or one it can't tell) and names a member the rewrite adds there (vanilla's
 * calls, the fields they read); and a mixin into the extension's interfaces where an injector or an overwrite selects
 * the extension's method, whose default those blocks no longer reach.
 */
public final class SeamConflicts {
	private static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
	private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
	private static final String MODIFY_CONSTANT = "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;";
	private static final List<String> INJECTORS = List.of("Lorg/spongepowered/asm/mixin/injection/", "Lcom/llamalad7/mixinextras/");
	/** The points a moved call can't shift: they pick by what an instruction is, not by its place. */
	private static final Set<String> FIXED_POINTS = Set.of("HEAD", "RETURN", "TAIL", "INVOKE", "INVOKE_ASSIGN", "INVOKE_STRING", "FIELD", "NEW", "CONSTANT");

	private SeamConflicts() {
	}

	/**
	 * What keeps a class's seams from being put back: a native mod's mixin, or what pre-flight couldn't read to tell.
	 *
	 * @param owner    the mod, as the installed jar names it; empty where the class itself failed to read
	 * @param mixin    the mixin class, by internal name; or the path of the file listing the mod's mixins that failed to
	 *                 read (it has a dot, a class's internal name none); or the class, where it failed to read
	 * @param readable whether pre-flight could read the mixin (it conflicts unread otherwise)
	 */
	public record Conflict(String owner, String mixin, boolean readable) {
		/** Where the class failed to read as the platform has it: what the native mods' mixins hook there can't be told. */
		static Conflict unreadClass(String name) {
			return new Conflict("", name, false);
		}

		/** As a report says it: {@code NeoForge mod moonlight's mixin PistonStructureResolverMixin hooks ...}. */
		public String describe(String loader) {
			return describe(loader, "vanilla's method");
		}

		/** As {@link #describe(String)}, of what goes back: {@code vanilla's method} (SEAM-1) or {@code vanilla's calls} (SEAM-3). */
		public String describe(String loader, String what) {
			if (owner.isEmpty()) {
				return "the class failed to read as " + loader + " has it, so whether a native mod's mixin hooks the platform's code that putting"
						+ " " + what + " back would move can't be told";
			}
			if (mixin.contains(".")) {
				return loader + " mod " + owner + "'s " + mixin + ", which lists its mixins, failed to read, so its mixins may hook the platform's code"
						+ " that putting " + what + " back would move";
			}
			String simple = mixin.substring(mixin.lastIndexOf('/') + 1);
			return loader + " mod " + owner + "'s mixin " + simple + (readable ? " hooks the platform's code that putting " + what + " back would move"
					: " can't be read, so it may hook the platform's code that putting " + what + " back would move");
		}
	}

	/**
	 * The classes whose seams aren't put back, with what keeps each, and whether every read the decision took succeeded:
	 * one that didn't is as conservative (a class is kept where a read it needed failed) but may differ at the next
	 * launch, so it isn't cached.
	 */
	public record Decision(Map<String, Conflict> kept, boolean clean) {
		public Decision {
			kept = Collections.unmodifiableMap(new TreeMap<>(kept));
		}
	}

	/** {@link #find(ClassNode, InstalledClasses)}'s answer, and whether every read it took succeeded. */
	private record Found(Conflict conflict, boolean clean) {
	}

	/**
	 * The native mixin keeping {@code platformClass}'s seams from being put back, or null where none does (or nothing would
	 * be put back): the first, in the order the jars list their mixins. A mixin that fails to read keeps them, and so does a
	 * file listing mixins that failed to read, where no mixin read does.
	 *
	 * @param platformClass the class as the platform has it, before any seam is put back
	 */
	public static Conflict find(ClassNode platformClass, InstalledClasses installed) {
		return find(platformClass.name, () -> platformClass, installed.nativeListing()).conflict();
	}

	/**
	 * {@link #find(ClassNode, InstalledClasses)} of the class {@code internal}, read ({@code platformClass}, null where it
	 * doesn't parse) and its rewrite worked out only once a native mixin may concern it: most launches have none.
	 */
	private static Found find(String internal, Supplier<ClassNode> platformClass, InstalledClasses.NativeListing listing) {
		VanillaSeams.Rewrite[] worked = {null};
		Supplier<VanillaSeams.Rewrite> rewrite = () -> {
			if (worked[0] == null) {
				ClassNode node = platformClass.get();
				worked[0] = node == null ? VanillaSeams.Rewrite.NONE : VanillaSeams.rewriteOf(node);
			}
			return worked[0];
		};
		Set<String> concerned = VanillaSeams.concerned(internal);
		for (InstalledClasses.ListedMixin listed : listing.mixins()) {
			byte[] data;
			try {
				data = listed.classes().read(listed.mixin());
			} catch (IOException | RuntimeException e) {
				// A read error, which the next launch may not have.
				return kept(rewrite, new Conflict(listed.owner(), listed.mixin(), false), false);
			}
			// A class the config lists and the jar hasn't, or one that doesn't parse, is so at every launch.
			if (data == null) return kept(rewrite, new Conflict(listed.owner(), listed.mixin(), false), true);
			// Only a mixin naming the class, or another whose methods' calls may change, can target it (its @Mixin's class or
			// string): the others aren't parsed.
			String text = new String(data, StandardCharsets.ISO_8859_1);
			if (concerned.stream().noneMatch(name -> names(text, name))) continue;
			if (rewrite.get().isEmpty()) return new Found(null, true);
			ClassNode mixin = new ClassNode();
			try {
				new ClassReader(data).accept(mixin, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
			} catch (RuntimeException e) {
				return new Found(new Conflict(listed.owner(), listed.mixin(), false), true);
			}
			// What its annotations' strings are read as: through its config's refmap, Forge 47's mods' development names
			// the game's SRG names.
			Map<String, String> mapped;
			try {
				mapped = listed.refmap() == null ? Map.of() : listed.refmap().of(listed.mixin());
			} catch (IOException | RuntimeException e) {
				return kept(rewrite, new Conflict(listed.owner(), listed.mixin(), false), false);
			}
			List<String> targets = InstalledClasses.mixinTargets(mixin);
			if (targets.contains(internal) && hooks(mixin, platformClass.get(), rewrite.get(), mapped)
					|| rewrite.get().others().entrySet().stream().anyMatch(d -> targets.contains(d.getKey()) && hooksMethod(mixin, d.getValue(), mapped))) {
				return new Found(new Conflict(listed.owner(), listed.mixin(), true), true);
			}
		}
		if (!listing.complete()) {
			InstalledClasses.UnreadList unread = listing.unread().get(0);
			return kept(rewrite, new Conflict(unread.owner(), unread.path(), false), false);
		}
		return new Found(null, true);
	}

	/** The conflict, where something would be put back; no conflict where nothing would. */
	private static Found kept(Supplier<VanillaSeams.Rewrite> rewrite, Conflict conflict, boolean clean) {
		return rewrite.get().isEmpty() ? new Found(null, true) : new Found(conflict, clean);
	}

	/**
	 * Whether a class file's text names the class: in a descriptor, or as a whole name (internal or dotted) in a string, as
	 * a {@code @Mixin}'s class or its {@code targets} do; not where the name runs on into another's ({@code LevelReader}).
	 */
	private static boolean names(String text, String internal) {
		if (text.contains("L" + internal + ";")) return true;
		for (String name : List.of(internal, internal.replace('/', '.'))) {
			for (int at = text.indexOf(name); at >= 0; at = text.indexOf(name, at + 1)) {
				int end = at + name.length();
				if (end == text.length() || !continuesName(text.charAt(end))) return true;
			}
		}
		return false;
	}

	/**
	 * Whether a character of a class file's text, read byte for byte, goes on with a name: an ASCII letter or digit, '_' or
	 * '$', or a byte of a character past ASCII. Not {@link Character#isJavaIdentifierPart}, which takes the control
	 * characters for part of a name, and with them the tag of the constant after a string ({@code targets}' class names).
	 */
	private static boolean continuesName(char c) {
		return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_' || c == '$' || c >= 0x80;
	}

	/** The classes of the game whose seams native mixins keep from being put back, with the mixin keeping each. */
	public static Map<String, Conflict> of(ClassBytesSource game, InstalledClasses installed) {
		return decide(game, installed).kept();
	}

	/**
	 * {@link #of(ClassBytesSource, InstalledClasses)}, and whether every read it took succeeded: the native jars' lists of
	 * their mixins, the mixins, and the seam classes as the platform has them. A seam class that fails to read is kept
	 * (an error from the platform's transformers among them), as is one where a mixin or a list failed to read.
	 */
	public static Decision decide(ClassBytesSource game, InstalledClasses installed) {
		InstalledClasses.NativeListing listing = installed.nativeListing();
		boolean clean = listing.complete();
		Map<String, Conflict> kept = new TreeMap<>();
		for (String name : VanillaSeams.CLASSES) {
			byte[] data;
			try {
				data = game.read(name);
			} catch (IOException | RuntimeException e) {
				kept.put(name, Conflict.unreadClass(name));
				clean = false;
				continue;
			}
			if (data == null) continue;
			ClassNode[] node = {null};
			Found found = find(name, () -> node[0] != null ? node[0] : (node[0] = parse(data)), listing);
			clean &= found.clean();
			if (found.conflict() != null) kept.put(name, found.conflict());
		}
		return new Decision(kept, clean);
	}

	/** A class as a tree; null where absent, or where it doesn't parse (its rewrite as it loads fails the same way). */
	private static ClassNode parse(byte[] data) {
		if (data == null) return null;
		try {
			ClassNode node = new ClassNode();
			new ClassReader(data).accept(node, 0);
			return node;
		} catch (RuntimeException | StackOverflowError e) {
			return null;
		}
	}

	/**
	 * {@link #of(ClassBytesSource, InstalledClasses)}, kept in {@code cache} from one launch to the next: reading every
	 * listed native mixin takes about 140 ms for 5,000 of them, at each launch with Fabric mods. Keyed by what the decision
	 * reads: the native jars ({@link InstalledClasses#nativeFingerprint}: their files' paths, sizes and times),
	 * the seam classes as the platform has them, and {@code environment} (Fabricated's build, the game's versions, the
	 * side). Decided afresh where a native jar listing mixins has no fingerprint, where a seam class fails to read, and
	 * where the cached file can't be read or is damaged (then rewritten); stored as the cache stores its blobs,
	 * atomically, its content's hash checked on read. Only a decision whose every read succeeded is stored
	 * ({@link Decision#clean}): one a read error made would hold for every later launch, the jars' stamps unchanged.
	 */
	public static Map<String, Conflict> of(ClassBytesSource game, InstalledClasses installed, TranslationCache cache, String environment) {
		String jars = installed.nativeFingerprint();
		if (cache == null || jars == null) return of(game, installed);
		StringBuilder classes = new StringBuilder();
		for (String name : new TreeSet<>(VanillaSeams.CLASSES)) {
			byte[] data;
			try {
				data = game.read(name);
			} catch (IOException | RuntimeException e) {
				// Not cached: the decision reads it again, and keeps the class where it fails again.
				return of(game, installed);
			}
			classes.append(name).append('=').append(data == null ? "none" : Hashing.sha256(data)).append('\n');
		}
		String blob = "seams-" + Hashing.sha256(environment + "|" + jars + "|" + classes) + ".json";
		Map<String, Conflict> cached = cached(cache, blob);
		if (cached != null) return cached;
		Decision decision = decide(game, installed);
		if (!decision.clean()) return decision.kept();
		Map<String, Object> json = new TreeMap<>();
		decision.kept().forEach((owner, c) -> json.put(owner, Map.of("owner", c.owner(), "mixin", c.mixin(), "readable", c.readable())));
		try {
			cache.storeBlob(blob, Json.write(Map.of("kept", json), false).getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			// Not cached: decided again next launch (caching is an optimisation only).
		}
		return decision.kept();
	}

	/** The decision a blob holds; null where it is absent, damaged or not one. */
	private static Map<String, Conflict> cached(TranslationCache cache, String blob) {
		try {
			Optional<byte[]> data = cache.readBlob(blob);
			if (data.isEmpty() || !(Json.parse(new String(data.get(), StandardCharsets.UTF_8)) instanceof Map<?, ?> root)
					|| !(root.get("kept") instanceof Map<?, ?> kept)) return null;
			Map<String, Conflict> found = new TreeMap<>();
			for (Map.Entry<?, ?> e : kept.entrySet()) {
				if (!(e.getKey() instanceof String owner) || !VanillaSeams.names(owner) || !(e.getValue() instanceof Map<?, ?> c)
						|| !(c.get("owner") instanceof String mod) || !(c.get("mixin") instanceof String mixin) || !(c.get("readable") instanceof Boolean readable)) {
					return null;
				}
				found.put(owner, new Conflict(mod, mixin, readable));
			}
			return found;
		} catch (IOException | JsonException | RuntimeException e) {
			return null;
		}
	}

	/** The decision as a cache key's part: a translation recorded where seams were put back is reused only while it holds. */
	public static String key(Map<String, Conflict> kept) {
		return kept.isEmpty() ? "seams put back" : kept.entrySet().stream().map(e -> e.getKey() + " kept by " + e.getValue().owner() + " "
				+ e.getValue().mixin() + (e.getValue().readable() ? "" : " unread")).collect(Collectors.joining(", ", "seams kept: ", ""));
	}

	/**
	 * Whether the mixin, which targets {@code target}, hooks what the rewrite moves or shifts; {@code mapped}, what its
	 * refmap reads its annotations' strings as.
	 */
	private static boolean hooks(ClassNode mixin, ClassNode target, VanillaSeams.Rewrite rewrite, Map<String, String> mapped) {
		for (MethodNode method : mixin.methods) {
			for (AnnotationNode ann : annotations(method)) {
				if (ann.desc.equals(OVERWRITE) && (rewrite.holders().contains(method.name + method.desc) || rewrite.bypassed().contains(method.name + method.desc))) return true;
				if (INJECTORS.stream().noneMatch(ann.desc::startsWith)) continue;
				if (names(ann, rewrite.movedCalls(), mapped)) return true;
				Selected selected = selected(ann, target, rewrite, mapped);
				if (selected == Selected.UNKNOWN || selected == Selected.BYPASSED || selected == Selected.HOLDER && shiftable(ann)) return true;
				if (selected == Selected.HOLDER && names(ann, rewrite.added(), mapped)) return true;
				if (selected == Selected.HOLDER && rewrite.constants() && (ann.desc.equals(MODIFY_CONSTANT) || points(ann, "CONSTANT"))) return true;
			}
		}
		return false;
	}

	/**
	 * Whether the mixin, which targets a class whose method of one of the {@code names} is called otherwise (an extension's
	 * default, bypassed; Forge's {@code Level.removeBlock}, newly called), hooks it.
	 */
	private static boolean hooksMethod(ClassNode mixin, Set<String> names, Map<String, String> mapped) {
		for (MethodNode method : mixin.methods) {
			for (AnnotationNode ann : annotations(method)) {
				if (ann.desc.equals(OVERWRITE) && names.contains(method.name)) return true;
				if (INJECTORS.stream().noneMatch(ann.desc::startsWith) || ann.desc.startsWith("Lcom/llamalad7/mixinextras/expression/")) continue;
				for (Selector selector : selectors(ann, mapped)) {
					if (selector == null || names.contains(selector.name())) return true;
				}
			}
		}
		return false;
	}

	private static List<AnnotationNode> annotations(MethodNode method) {
		List<AnnotationNode> annotations = new ArrayList<>();
		if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
		if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
		return annotations;
	}

	/**
	 * Whether a string among the annotation's values, as written or as its refmap reads it ({@code mapped}), names one of
	 * the members ({@code owner;name(desc)} or {@code owner;name:desc}), or is a wildcard target of an {@code @At}.
	 */
	private static boolean names(Object value, Set<String> members, Map<String, String> mapped) {
		if (value instanceof AnnotationNode ann) {
			for (int i = 0; ann.values != null && i + 1 < ann.values.size(); i += 2) {
				if (names(ann.values.get(i + 1), members, mapped)) return true;
				if (ann.desc.equals(AT) && "target".equals(ann.values.get(i)) && ann.values.get(i + 1) instanceof String target && fuzzy(target)) return true;
			}
			return false;
		}
		if (value instanceof List<?> list) return list.stream().anyMatch(v -> names(v, members, mapped));
		if (!(value instanceof String text)) return false;
		String read = mapped.get(text);
		return namesMember(text, members) || read != null && namesMember(read, members);
	}

	/** Whether the string names one of the members ({@link #names}). */
	private static boolean namesMember(String text, Set<String> members) {
		for (String moved : members) {
			String member = moved.substring(moved.indexOf(';') + 1);
			int end = member.indexOf('(') >= 0 ? member.indexOf('(') : member.indexOf(':');
			String name = end < 0 ? member : member.substring(0, end);
			char open = member.indexOf('(') >= 0 ? '(' : ':';
			if (text.equals(name) || text.startsWith(member) || text.startsWith(name + open) || text.contains(";" + name + open) || text.contains("." + name + open)
					|| text.endsWith(";" + name) || text.endsWith("." + name)) return true;
		}
		return false;
	}

	/**
	 * What an injector's {@code method} selects in the class: no method holding moved calls, one, one no longer reached
	 * where vanilla's calls are made instead, or what can't be told.
	 */
	private enum Selected {
		OTHER, HOLDER, BYPASSED, UNKNOWN
	}

	/**
	 * What the injector's {@code method} selects: methods holding moved calls by name and, where given, descriptor; what
	 * can't be told by a wildcard, a name the class doesn't have or a descriptor annotation ({@code target}). An injector
	 * selecting none (MixinExtras' {@code @Definition}, whose {@code method} names a member it matches) selects nothing.
	 */
	private static Selected selected(AnnotationNode ann, ClassNode target, VanillaSeams.Rewrite rewrite, Map<String, String> mapped) {
		if (ann.desc.startsWith("Lcom/llamalad7/mixinextras/expression/")) return Selected.OTHER;
		Selected selected = Selected.OTHER;
		for (Selector selector : selectors(ann, mapped)) {
			Selected one = selector == null ? Selected.UNKNOWN : selects(selector, target, rewrite);
			if (one == Selected.UNKNOWN || one == Selected.BYPASSED) return one;
			if (one == Selected.HOLDER) selected = one;
		}
		return selected;
	}

	private static Selected selects(Selector selector, ClassNode target, VanillaSeams.Rewrite rewrite) {
		boolean found = false;
		for (MethodNode m : target.methods) {
			if (!m.name.equals(selector.name()) || selector.desc() != null && !m.desc.equals(selector.desc())) continue;
			found = true;
			if (rewrite.bypassed().contains(m.name + m.desc)) return Selected.BYPASSED;
			if (rewrite.holders().contains(m.name + m.desc)) return Selected.HOLDER;
		}
		return found ? Selected.OTHER : Selected.UNKNOWN;
	}

	/** Whether one of the annotation's points (its own, its slices') may pick another instruction once calls move. */
	private static boolean shiftable(Object value) {
		if (value instanceof AnnotationNode ann) {
			if (ann.desc.equals(AT)) {
				String point = value(ann, "value") instanceof String v ? v : "";
				if (!FIXED_POINTS.contains(point)) return true;
				if (value(ann, "shift") instanceof String[] shift && shift.length == 2 && shift[1].equals("BY")) return true;
				if (value(ann, "by") instanceof Integer by && by != 0) return true;
			}
			for (int i = 0; ann.values != null && i + 1 < ann.values.size(); i += 2) {
				if (shiftable(ann.values.get(i + 1))) return true;
			}
			return false;
		}
		return value instanceof List<?> list && list.stream().anyMatch(SeamConflicts::shiftable);
	}

	/** Whether one of the annotation's points (its own, its slices') is of the kind. */
	private static boolean points(Object value, String kind) {
		if (value instanceof AnnotationNode ann) {
			if (ann.desc.equals(AT) && value(ann, "value") instanceof String point && point.equalsIgnoreCase(kind)) return true;
			for (int i = 0; ann.values != null && i + 1 < ann.values.size(); i += 2) {
				if (points(ann.values.get(i + 1), kind)) return true;
			}
			return false;
		}
		return value instanceof List<?> list && list.stream().anyMatch(v -> points(v, kind));
	}

	/** Whether a selector or target picks members by a pattern: Mixin's {@code *} quantifier, a {@code {n}} one, a regex. */
	private static boolean fuzzy(String selector) {
		return selector.contains("*") || selector.contains("{") || selector.startsWith("/") && selector.endsWith("/") && selector.length() > 1;
	}

	/**
	 * A method an injector selects, by name, and by descriptor where it gives one (null where it doesn't): any of the
	 * class's methods of that name, and of that descriptor where given.
	 */
	private record Selector(String name, String desc) {
	}

	/**
	 * The methods an injector selects, by its {@code method} strings, as its refmap reads them ({@code mapped}), and its
	 * {@code target} descriptor annotations ({@code @Desc}), each null where it picks them by a pattern of names or can't
	 * be read.
	 */
	private static List<Selector> selectors(AnnotationNode ann, Map<String, String> mapped) {
		List<Selector> selectors = new ArrayList<>();
		Object methods = value(ann, "method");
		for (Object o : methods instanceof List<?> list ? list : methods instanceof String one ? List.of(one) : List.of()) {
			selectors.add(o instanceof String selector ? selector(mapped.getOrDefault(selector, selector)) : null);
		}
		if (value(ann, "target") instanceof List<?> descs) {
			// @Desc's value is the method's name; its arguments, which narrow it, are left aside: any method of the name.
			for (Object o : descs) {
				selectors.add(o instanceof AnnotationNode desc && value(desc, "value") instanceof String name && !name.isEmpty() && plainName(name)
						? new Selector(name, null) : null);
			}
		}
		return selectors;
	}

	/**
	 * A {@code method} string as Mixin reads it: an owner before the name ({@code Lpkg/Owner;} or {@code pkg.Owner.}),
	 * then the name, a quantifier saying how many it may match ({@code *}, {@code +}, {@code {n}}, {@code {n,m}}) and the
	 * descriptor. A quantifier alone, or with an empty name ({@code *}), matches any name; so does a regex
	 * ({@code /.../}, {@code name=/.../}) or a tail ({@code ->}): null for those.
	 */
	static Selector selector(String text) {
		String member = text.replaceAll("\\s", "");
		if (member.isEmpty() || member.startsWith("/") || member.matches("(?s).*(?:owner|name|desc)=/.*") || member.contains("->")) return null;
		int paren = member.indexOf('(');
		String name = paren < 0 ? member : member.substring(0, paren);
		String desc = paren < 0 ? null : member.substring(paren);
		if (name.startsWith("L") && name.contains(";")) {
			name = name.substring(name.indexOf(';') + 1);
		} else if (name.lastIndexOf('.') >= 0) {
			name = name.substring(name.lastIndexOf('.') + 1);
		}
		int quantifier = name.endsWith("*") || name.endsWith("+") ? name.length() - 1 : name.indexOf('{');
		if (quantifier >= 0) name = name.substring(0, quantifier);
		if (name.isEmpty() || !plainName(name)) return null;
		// A pattern in the descriptor narrows nothing that can be told: any of the name's methods.
		return new Selector(name, desc == null || fuzzy(desc) ? null : desc);
	}

	/** Whether a name is one method's name, with nothing of a pattern in it. */
	private static boolean plainName(String name) {
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (c == '*' || c == '+' || c == '{' || c == '}' || c == '/' || c == '(' || c == ')' || c == ';' || c == '=' || c == '.') return false;
		}
		return true;
	}

	private static Object value(AnnotationNode ann, String name) {
		for (int i = 0; ann.values != null && i + 1 < ann.values.size(); i += 2) {
			if (name.equals(ann.values.get(i))) return ann.values.get(i + 1);
		}
		return null;
	}
}
