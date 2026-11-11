package io.github.cmdralph.fabricated.core.bytecode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

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
 * names a moved call in any string of its annotations, or uses a wildcard target; selects methods by a wildcard, a
 * name pre-flight can't find in the class (a development name a refmap maps, say) or a descriptor annotation, whatever
 * it hooks there; or selects a method holding moved calls with a point the move may shift (any but the method's start
 * and returns, a call, a field access, a construction or a constant, or one shifted by instructions); or where it
 * overwrites a method holding moved calls. A mixin that can't be read conflicts too: pre-flight can't tell what it
 * hooks.
 */
public final class SeamConflicts {
	private static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
	private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
	private static final List<String> INJECTORS = List.of("Lorg/spongepowered/asm/mixin/injection/", "Lcom/llamalad7/mixinextras/");
	/** The points a moved call can't shift: they pick by what an instruction is, not by its place. */
	private static final Set<String> FIXED_POINTS = Set.of("HEAD", "RETURN", "TAIL", "INVOKE", "INVOKE_ASSIGN", "INVOKE_STRING", "FIELD", "NEW", "CONSTANT");

	private SeamConflicts() {
	}

	/**
	 * A native mod's mixin keeping a class's seams from being put back.
	 *
	 * @param owner    the mod, as the installed jar names it
	 * @param mixin    the mixin class, by internal name
	 * @param readable whether pre-flight could read it (it conflicts unread otherwise)
	 */
	public record Conflict(String owner, String mixin, boolean readable) {
		/** As a report says it: {@code NeoForge mod moonlight's mixin PistonStructureResolverMixin hooks ...}. */
		public String describe(String loader) {
			String simple = mixin.substring(mixin.lastIndexOf('/') + 1);
			return loader + " mod " + owner + "'s mixin " + simple + (readable ? " hooks the platform's code that putting vanilla's method back would move"
					: " can't be read, so it may hook the platform's code that putting vanilla's method back would move");
		}
	}

	/**
	 * The native mixin keeping {@code platformClass}'s seams from being put back, or null where none does (or nothing would
	 * be put back): the first, in the order the jars list their mixins.
	 *
	 * @param platformClass the class as the platform has it, before any seam is put back
	 */
	public static Conflict find(ClassNode platformClass, InstalledClasses installed) {
		VanillaSeams.Rewrite rewrite = VanillaSeams.rewriteOf(platformClass);
		if (rewrite.isEmpty()) return null;
		for (InstalledClasses.ListedMixin listed : installed.nativeMixins()) {
			ClassNode mixin = listed.classes().readNode(listed.mixin(), ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
			if (mixin == null) return new Conflict(listed.owner(), listed.mixin(), false);
			if (InstalledClasses.mixinTargets(mixin).contains(platformClass.name) && hooks(mixin, platformClass, rewrite)) {
				return new Conflict(listed.owner(), listed.mixin(), true);
			}
		}
		return null;
	}

	/** The classes of the game whose seams native mixins keep from being put back, with the mixin keeping each. */
	public static Map<String, Conflict> of(ClassBytesSource game, InstalledClasses installed) {
		Map<String, Conflict> kept = new TreeMap<>();
		for (String name : VanillaSeams.CLASSES) {
			ClassNode node = game.readNode(name, 0);
			Conflict conflict = node == null ? null : find(node, installed);
			if (conflict != null) kept.put(name, conflict);
		}
		return kept;
	}

	/** The decision as a cache key's part: a translation recorded where seams were put back is reused only while it holds. */
	public static String key(Map<String, Conflict> kept) {
		return kept.isEmpty() ? "seams put back" : kept.entrySet().stream().map(e -> e.getKey() + " kept by " + e.getValue().owner() + " "
				+ e.getValue().mixin() + (e.getValue().readable() ? "" : " unread")).collect(Collectors.joining(", ", "seams kept: ", ""));
	}

	/** Whether the mixin, which targets {@code target}, hooks what the rewrite moves or shifts. */
	private static boolean hooks(ClassNode mixin, ClassNode target, VanillaSeams.Rewrite rewrite) {
		for (MethodNode method : mixin.methods) {
			List<AnnotationNode> annotations = new ArrayList<>();
			if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
			if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
			for (AnnotationNode ann : annotations) {
				if (ann.desc.equals(OVERWRITE) && rewrite.holders().contains(method.name + method.desc)) return true;
				if (INJECTORS.stream().noneMatch(ann.desc::startsWith)) continue;
				if (namesMoved(ann, rewrite)) return true;
				Selected selected = selected(ann, target, rewrite);
				if (selected == Selected.UNKNOWN || selected == Selected.HOLDER && shiftable(ann)) return true;
			}
		}
		return false;
	}

	/** Whether a string among the annotation's values names a moved call, or is a wildcard target of an {@code @At}. */
	private static boolean namesMoved(Object value, VanillaSeams.Rewrite rewrite) {
		if (value instanceof AnnotationNode ann) {
			for (int i = 0; ann.values != null && i + 1 < ann.values.size(); i += 2) {
				if (namesMoved(ann.values.get(i + 1), rewrite)) return true;
				if (ann.desc.equals(AT) && "target".equals(ann.values.get(i)) && ann.values.get(i + 1) instanceof String target && fuzzy(target)) return true;
			}
			return false;
		}
		if (value instanceof List<?> list) return list.stream().anyMatch(v -> namesMoved(v, rewrite));
		if (!(value instanceof String text)) return false;
		for (String moved : rewrite.movedCalls()) {
			String member = moved.substring(moved.indexOf(';') + 1);
			String name = member.substring(0, member.indexOf('('));
			if (text.equals(name) || text.startsWith(member) || text.startsWith(name + "(") || text.contains(";" + name + "(") || text.contains("." + name + "(")
					|| text.endsWith(";" + name) || text.endsWith("." + name)) return true;
		}
		return false;
	}

	/** What an injector's {@code method} selects in the class: no method holding moved calls, one, or what can't be told. */
	private enum Selected {
		OTHER, HOLDER, UNKNOWN
	}

	/**
	 * What the injector's {@code method} selects: methods holding moved calls by name and, where given, descriptor; what
	 * can't be told by a wildcard, a name the class doesn't have or a descriptor annotation ({@code target}). An injector
	 * selecting none (MixinExtras' {@code @Definition}, whose {@code method} names a member it matches) selects nothing.
	 */
	private static Selected selected(AnnotationNode ann, ClassNode target, VanillaSeams.Rewrite rewrite) {
		if (ann.desc.startsWith("Lcom/llamalad7/mixinextras/expression/")) return Selected.OTHER;
		if (value(ann, "target") instanceof List<?> descs && !descs.isEmpty()) return Selected.UNKNOWN;
		Object methods = value(ann, "method");
		List<?> selectors = methods instanceof List<?> list ? list : methods instanceof String one ? List.of(one) : List.of();
		Selected selected = Selected.OTHER;
		for (Object o : selectors) {
			Selected one = o instanceof String selector ? selects(selector, target, rewrite) : Selected.UNKNOWN;
			if (one == Selected.UNKNOWN) return one;
			if (one == Selected.HOLDER) selected = one;
		}
		return selected;
	}

	private static Selected selects(String selector, ClassNode target, VanillaSeams.Rewrite rewrite) {
		if (fuzzy(selector)) return Selected.UNKNOWN;
		String member = selector.startsWith("L") && selector.contains(";") ? selector.substring(selector.indexOf(';') + 1) : selector;
		int paren = member.indexOf('(');
		String name = paren < 0 ? member : member.substring(0, paren);
		String desc = paren < 0 ? null : member.substring(paren);
		boolean found = false;
		for (MethodNode m : target.methods) {
			if (!m.name.equals(name) || desc != null && !m.desc.equals(desc)) continue;
			found = true;
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

	/** Whether a selector or target picks members by a pattern: Mixin's {@code *} quantifier, a {@code {n}} one, a regex. */
	private static boolean fuzzy(String selector) {
		return selector.contains("*") || selector.contains("{") || selector.startsWith("/") && selector.endsWith("/") && selector.length() > 1;
	}

	private static Object value(AnnotationNode ann, String name) {
		for (int i = 0; ann.values != null && i + 1 < ann.values.size(); i += 2) {
			if (name.equals(ann.values.get(i))) return ann.values.get(i + 1);
		}
		return null;
	}
}
