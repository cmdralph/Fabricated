package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding.Severity;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * Static verification of a mixin config against the runtime game bytecode, before any mod code runs.
 *
 * <p>The verifier only reports what it can prove. A finding of severity {@link Severity#FAIL} means the
 * mixin would make Mixin throw during class loading: a missing target class (non-{@code @Pseudo}) or
 * missing {@code @Shadow}/{@code @Overwrite}/{@code @Accessor}/{@code @Invoker} target in a
 * {@code required} config, or an injector whose effective {@code require} is at least 1 and whose
 * target method or injection point does not exist. Selectors and injection points it cannot evaluate
 * (wildcards, slices with ordinals, custom points, targets in other mods) are never reported as
 * failures. When the config declares a plugin, which may disable mixins at runtime, failures are
 * downgraded to warnings.
 */
public final class MixinPreflight {
	private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
	private static final String PSEUDO = "Lorg/spongepowered/asm/mixin/Pseudo;";
	private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
	private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
	private static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
	private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
	private static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
	private static final Set<String> GAME_PREFIXES = Set.of("net/minecraft/", "com/mojang/blaze3d/");

	private final ClassIndex game;
	private final ClassBytesSource modClasses;
	private final boolean clientSide;
	private final java.util.Map<String, java.util.Optional<ClassNode>> targetCache = new java.util.HashMap<>();

	/**
	 * @param game       runtime game classes (Minecraft as patched by NeoForge)
	 * @param modClasses the mod's own classes (already remapped to runtime names)
	 * @param clientSide whether the physical side is the client
	 */
	public MixinPreflight(ClassIndex game, ClassBytesSource modClasses, boolean clientSide) {
		this.game = game;
		this.modClasses = modClasses;
		this.clientSide = clientSide;
	}

	/**
	 * @param configName file name of the config (for messages)
	 * @param skip       mixin names (relative to the package) the user disabled
	 */
	public List<PreflightFinding> check(String configName, MixinConfigFile config, Refmap refmap, Set<String> skip) {
		List<PreflightFinding> out = new ArrayList<>();
		boolean hasPlugin = config.plugin() != null && !config.plugin().isBlank();
		for (String mixin : config.mixinsFor(clientSide)) {
			if (skip.contains(mixin)) continue;
			String internal = config.internalName(mixin);
			Context ctx = new Context(configName, internal.replace('/', '.'), config, refmap, hasPlugin, out);
			ClassNode node = readMixin(internal);
			if (node == null) {
				ctx.classLevel("Mixin class " + ctx.mixinDotted + " listed in " + configName + " is missing from the mod jar",
						"The mod jar is incomplete or corrupted; re-download it.");
				continue;
			}
			checkMixin(node, ctx);
		}
		if (hasPlugin && out.stream().anyMatch(f -> f.severity() == Severity.WARN)) {
			out.add(new PreflightFinding(Severity.INFO, configName, null,
					"This config uses a mixin plugin (" + config.plugin() + ") that may disable mixins at runtime, so problems are reported as warnings only.", ""));
		}
		return out;
	}

	private ClassNode readMixin(String internal) {
		try {
			byte[] data = modClasses.read(internal);
			if (data == null) return null;
			ClassNode node = new ClassNode();
			new org.objectweb.asm.ClassReader(data).accept(node, org.objectweb.asm.ClassReader.SKIP_CODE | org.objectweb.asm.ClassReader.SKIP_FRAMES);
			return node;
		} catch (java.io.IOException | RuntimeException e) {
			return null;
		}
	}

	private final class Context {
		final String configName;
		final String mixinDotted;
		final String mixinInternal;
		final MixinConfigFile config;
		final Refmap refmap;
		final boolean hasPlugin;
		final List<PreflightFinding> out;

		Context(String configName, String mixinDotted, MixinConfigFile config, Refmap refmap, boolean hasPlugin, List<PreflightFinding> out) {
			this.configName = configName;
			this.mixinDotted = mixinDotted;
			this.mixinInternal = mixinDotted.replace('.', '/');
			this.config = config;
			this.refmap = refmap;
			this.hasPlugin = hasPlugin;
			this.out = out;
		}

		String resolve(String selector) {
			return refmap.resolve(mixinInternal, selector);
		}

		/** A problem that makes Mixin reject the whole mixin; fatal when the config is required. */
		void classLevel(String message, String suggestion) {
			Severity s = config.required() && !hasPlugin ? Severity.FAIL : Severity.WARN;
			out.add(new PreflightFinding(s, configName, mixinDotted, message, suggestion));
		}

		void injector(int require, String message, String suggestion) {
			Severity s = require > 0 && !hasPlugin ? Severity.FAIL : Severity.WARN;
			out.add(new PreflightFinding(s, configName, mixinDotted, message, suggestion));
		}

		void info(String message) {
			out.add(new PreflightFinding(Severity.INFO, configName, mixinDotted, message, ""));
		}
	}

	private void checkMixin(ClassNode mixin, Context ctx) {
		AnnotationNode mixinAnn = find(mixin.invisibleAnnotations, MIXIN);
		if (mixinAnn == null) mixinAnn = find(mixin.visibleAnnotations, MIXIN);
		if (mixinAnn == null) {
			ctx.classLevel("Class " + ctx.mixinDotted + " is listed as a mixin but has no @Mixin annotation", "Report this to the mod author.");
			return;
		}
		boolean pseudo = find(mixin.invisibleAnnotations, PSEUDO) != null || find(mixin.visibleAnnotations, PSEUDO) != null;

		List<String> targets = new ArrayList<>();
		Object value = attr(mixinAnn, "value");
		if (value instanceof List<?> types) {
			for (Object t : types) {
				if (t instanceof Type type) targets.add(type.getInternalName());
			}
		}
		Object targetStrings = attr(mixinAnn, "targets");
		if (targetStrings instanceof List<?> names) {
			for (Object n : names) {
				if (n instanceof String s) targets.add(ctx.resolve(s).replace('.', '/'));
			}
		}

		for (String target : targets) {
			boolean isGame = GAME_PREFIXES.stream().anyMatch(target::startsWith);
			ClassNode targetNode = isGame ? targetCache.computeIfAbsent(target, t -> java.util.Optional.ofNullable(game.readNode(t))).orElse(null) : null;
			if (targetNode == null) {
				if (pseudo) continue;
				if (isGame) {
					ctx.classLevel("Target class " + target.replace('/', '.') + " does not exist in this Minecraft/NeoForge version",
							"The mod was likely built for a different Minecraft version, or NeoForge removed this class.");
				} else {
					ctx.info("Target " + target.replace('/', '.') + " is not a Minecraft class and was not verified");
				}
				continue;
			}
			checkMembers(mixin, targetNode, ctx);
		}
	}

	private void checkMembers(ClassNode mixin, ClassNode target, Context ctx) {
		String targetDotted = target.name.replace('/', '.');
		for (FieldNode field : mixin.fields) {
			AnnotationNode shadow = annotation(field.visibleAnnotations, field.invisibleAnnotations, SHADOW);
			if (shadow == null) continue;
			String name = ctx.resolve(field.name);
			MemberRef ref = MemberRef.parse(name);
			String fieldName = ref.name() != null ? ref.name() : field.name;
			String desc = ref.desc() != null && !ref.desc().startsWith("(") ? ref.desc() : field.desc;
			if (game.resolveField(target.name, fieldName + ":" + desc) == ClassIndex.Resolution.MISSING
					&& !aliasExists(shadow, target, desc, true)) {
				ctx.classLevel("@Shadow field " + fieldName + " (" + desc + ") does not exist in " + targetDotted,
						"NeoForge or this Minecraft version changed " + targetDotted + "; the mod needs an update.");
			}
		}

		for (MethodNode method : mixin.methods) {
			AnnotationNode shadow = annotation(method.visibleAnnotations, method.invisibleAnnotations, SHADOW);
			if (shadow != null) {
				String prefix = attr(shadow, "prefix") instanceof String p ? p : "shadow$";
				String name = method.name.startsWith(prefix) ? method.name.substring(prefix.length()) : method.name;
				MemberRef ref = MemberRef.parse(ctx.resolve(name));
				String methodName = ref.name() != null ? ref.name() : name;
				String desc = ref.desc() != null && ref.desc().startsWith("(") ? ref.desc() : method.desc;
				if (game.resolveMethod(target.name, methodName + desc) == ClassIndex.Resolution.MISSING
						&& !aliasExists(shadow, target, desc, false)) {
					ctx.classLevel("@Shadow method " + methodName + desc + " does not exist in " + targetDotted,
							"NeoForge or this Minecraft version changed " + targetDotted + "; the mod needs an update.");
				}
				continue;
			}

			if (annotation(method.visibleAnnotations, method.invisibleAnnotations, OVERWRITE) != null) {
				MemberRef ref = MemberRef.parse(ctx.resolve(method.name));
				String name = ref.name() != null ? ref.name() : method.name;
				String desc = ref.desc() != null && ref.desc().startsWith("(") ? ref.desc() : method.desc;
				if (!declares(target, name, desc)) {
					ctx.classLevel("@Overwrite target " + name + desc + " does not exist in " + targetDotted,
							"The overwritten method was changed by NeoForge or this Minecraft version.");
				}
				continue;
			}

			AnnotationNode accessor = annotation(method.visibleAnnotations, method.invisibleAnnotations, ACCESSOR);
			if (accessor != null) {
				checkAccessor(method, accessor, target, ctx);
				continue;
			}
			AnnotationNode invoker = annotation(method.visibleAnnotations, method.invisibleAnnotations, INVOKER);
			if (invoker != null) {
				checkInvoker(method, invoker, target, ctx);
				continue;
			}

			for (AnnotationNode ann : allAnnotations(method)) {
				if (isInjector(ann.desc)) checkInjector(ann, target, ctx);
			}
		}
	}

	private boolean aliasExists(AnnotationNode shadow, ClassNode target, String desc, boolean field) {
		if (!(attr(shadow, "aliases") instanceof List<?> aliases)) return false;
		for (Object a : aliases) {
			if (!(a instanceof String alias)) continue;
			ClassIndex.Resolution r = field
					? game.resolveField(target.name, alias + ":" + desc)
					: game.resolveMethod(target.name, alias + desc);
			if (r != ClassIndex.Resolution.MISSING) return true;
		}
		return false;
	}

	private void checkAccessor(MethodNode method, AnnotationNode ann, ClassNode target, Context ctx) {
		String explicit = attr(ann, "value") instanceof String s && !s.isEmpty() ? s : null;
		String name = explicit != null ? explicit : inferAccessorName(method.name);
		if (name == null) return;
		MemberRef ref = MemberRef.parse(ctx.resolve(name));
		String fieldName = ref.name() != null ? ref.name() : name;
		// Mixin matches accessor targets by name and accepts erased/compatible types, so match by name.
		for (FieldNode f : target.fields) {
			if (f.name.equals(fieldName)) return;
		}
		ctx.classLevel("@Accessor target field " + fieldName + " does not exist in " + target.name.replace('/', '.'),
				"The field was renamed or removed by NeoForge or this Minecraft version.");
	}

	private void checkInvoker(MethodNode method, AnnotationNode ann, ClassNode target, Context ctx) {
		String explicit = attr(ann, "value") instanceof String s && !s.isEmpty() ? s : null;
		String name = explicit != null ? explicit : inferInvokerName(method.name);
		if (name == null || name.equals("<init>")) return;
		MemberRef ref = MemberRef.parse(ctx.resolve(name));
		String methodName = ref.name() != null ? ref.name() : name;
		String desc = ref.desc() != null && ref.desc().startsWith("(") ? ref.desc() : method.desc;
		for (MethodNode m : target.methods) {
			if (m.name.equals(methodName) && m.desc.equals(desc)) return;
		}
		ctx.classLevel("@Invoker target method " + methodName + desc + " does not exist in " + target.name.replace('/', '.'),
				"The method was renamed or removed by NeoForge or this Minecraft version.");
	}

	static String inferAccessorName(String method) {
		for (String prefix : List.of("get", "is", "set")) {
			if (method.length() > prefix.length() && method.startsWith(prefix) && Character.isUpperCase(method.charAt(prefix.length()))) {
				return decapitalize(method.substring(prefix.length()));
			}
		}
		return null;
	}

	static String inferInvokerName(String method) {
		for (String prefix : List.of("call", "invoke")) {
			if (method.length() > prefix.length() && method.startsWith(prefix) && Character.isUpperCase(method.charAt(prefix.length()))) {
				return decapitalize(method.substring(prefix.length()));
			}
		}
		if (method.startsWith("new") || method.startsWith("create")) return "<init>";
		return null;
	}

	private static String decapitalize(String s) {
		// Mixin keeps all-caps names (e.g. "getURL" -> "URL") as they are.
		if (s.length() > 1 && Character.isUpperCase(s.charAt(1))) return s;
		return Character.toLowerCase(s.charAt(0)) + s.substring(1);
	}

	private static boolean isInjector(String desc) {
		return desc.startsWith("Lorg/spongepowered/asm/mixin/injection/") && !desc.equals(AT)
				&& !desc.startsWith("Lorg/spongepowered/asm/mixin/injection/Slice") && !desc.startsWith("Lorg/spongepowered/asm/mixin/injection/Desc")
				&& !desc.startsWith("Lorg/spongepowered/asm/mixin/injection/Constant") && !desc.startsWith("Lorg/spongepowered/asm/mixin/injection/Coerce")
				|| desc.startsWith("Lcom/llamalad7/mixinextras/injector/");
	}

	private void checkInjector(AnnotationNode ann, ClassNode target, Context ctx) {
		if (!(attr(ann, "method") instanceof List<?> selectors) || selectors.isEmpty()) return;
		int require = attr(ann, "require") instanceof Integer r && r >= 0 ? r : ctx.config.defaultRequire();
		boolean sliced = attr(ann, "slice") != null;
		String injectorName = "@" + simpleName(ann.desc);

		List<MethodNode> matched = new ArrayList<>();
		boolean anyFuzzy = false;
		String lastSelector = null;
		for (Object o : selectors) {
			if (!(o instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy() || ref.name() == null) {
				anyFuzzy = true;
				continue;
			}
			lastSelector = ref.name() + (ref.desc() != null ? ref.desc() : "");
			for (MethodNode m : target.methods) {
				if (ref.matchesMethod(m.name, m.desc)) matched.add(m);
			}
		}
		if (matched.isEmpty()) {
			if (!anyFuzzy && lastSelector != null) {
				ctx.injector(require, injectorName + " targets method " + lastSelector
								+ ", which does not exist in " + target.name.replace('/', '.'),
						"The method was renamed or removed by NeoForge or this Minecraft version.");
			}
			return;
		}

		for (AnnotationNode at : atAnnotations(ann)) {
			String point = attr(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			String rawTarget = attr(at, "target") instanceof String t ? t : null;
			int ordinal = attr(at, "ordinal") instanceof Integer i ? i : -1;
			int opcode = attr(at, "opcode") instanceof Integer i ? i : -1;
			Verdict verdict = Verdict.UNKNOWN;
			for (MethodNode m : matched) {
				Verdict v = evaluate(point, rawTarget == null ? null : ctx.resolve(rawTarget), ordinal, opcode, sliced, m);
				if (v == Verdict.FOUND) {
					verdict = Verdict.FOUND;
					break;
				}
				if (v == Verdict.MISSING && verdict == Verdict.UNKNOWN) verdict = Verdict.MISSING;
			}
			if (verdict == Verdict.MISSING) {
				ctx.injector(require, injectorName + " in " + matched.getFirst().name + " expects @At(\"" + point + "\""
								+ (rawTarget != null ? ", target=\"" + ctx.resolve(rawTarget) + "\"" : "")
								+ (ordinal >= 0 ? ", ordinal=" + ordinal : "") + "), which does not occur in that method",
						"NeoForge or this Minecraft version changed the method body; the mod needs an update or this mixin must be skipped.");
				return;
			}
		}
	}

	private enum Verdict {
		FOUND, MISSING, UNKNOWN
	}

	private Verdict evaluate(String point, String target, int ordinal, int opcode, boolean sliced, MethodNode method) {
		switch (point) {
			case "HEAD", "RETURN", "TAIL" -> {
				return method.instructions == null || method.instructions.size() == 0 ? Verdict.MISSING : Verdict.FOUND;
			}
			case "INVOKE", "INVOKE_ASSIGN", "INVOKE_STRING", "FIELD", "NEW" -> {
				if (target == null) return Verdict.UNKNOWN;
				MemberRef ref = MemberRef.parse(target);
				if (ref.fuzzy()) return Verdict.UNKNOWN;
				int count = 0;
				for (AbstractInsnNode insn : method.instructions) {
					if (opcode >= 0 && insn.getOpcode() != opcode) continue;
					if (matchesInsn(point, ref, insn)) count++;
				}
				if (count == 0) return Verdict.MISSING;
				if (ordinal >= 0 && !sliced && count <= ordinal) return Verdict.MISSING;
				return Verdict.FOUND;
			}
			default -> {
				return Verdict.UNKNOWN;
			}
		}
	}

	private static boolean matchesInsn(String point, MemberRef ref, AbstractInsnNode insn) {
		switch (point) {
			case "INVOKE", "INVOKE_ASSIGN", "INVOKE_STRING" -> {
				if (!(insn instanceof MethodInsnNode m)) return false;
				return (ref.owner() == null || ref.owner().equals(m.owner))
						&& ref.name() != null && ref.name().equals(m.name)
						&& (ref.desc() == null || ref.desc().equals(m.desc) || ref.desc().endsWith(")") && m.desc.startsWith(ref.desc()));
			}
			case "FIELD" -> {
				if (!(insn instanceof FieldInsnNode f)) return false;
				return (ref.owner() == null || ref.owner().equals(f.owner))
						&& ref.name() != null && ref.name().equals(f.name)
						&& (ref.desc() == null || ref.desc().equals(f.desc));
			}
			case "NEW" -> {
				if (!(insn instanceof TypeInsnNode t) || t.getOpcode() != org.objectweb.asm.Opcodes.NEW) return false;
				String cls = newTarget(ref);
				return cls == null || cls.equals(t.desc);
			}
			default -> {
				return false;
			}
		}
	}

	/** Class created by a NEW selector: {@code Lpkg/C;}, {@code pkg/C} or a constructor descriptor. */
	private static String newTarget(MemberRef ref) {
		if (ref.owner() != null && ref.name() == null) return ref.owner();
		if (ref.desc() != null && ref.desc().startsWith("(")) {
			Type ret = Type.getReturnType(ref.desc());
			return ret.getSort() == Type.OBJECT ? ret.getInternalName() : null;
		}
		if (ref.name() != null && ref.owner() == null) return ref.name();
		return ref.owner();
	}

	private static List<AnnotationNode> atAnnotations(AnnotationNode injector) {
		List<AnnotationNode> ret = new ArrayList<>();
		Object at = attr(injector, "at");
		if (at instanceof AnnotationNode a) ret.add(a);
		if (at instanceof List<?> list) {
			for (Object o : list) {
				if (o instanceof AnnotationNode a) ret.add(a);
			}
		}
		return ret;
	}

	private static boolean declares(ClassNode target, String name, String desc) {
		for (MethodNode m : target.methods) {
			if (m.name.equals(name) && m.desc.equals(desc)) return true;
		}
		return false;
	}

	private static List<AnnotationNode> allAnnotations(MethodNode method) {
		List<AnnotationNode> ret = new ArrayList<>();
		if (method.visibleAnnotations != null) ret.addAll(method.visibleAnnotations);
		if (method.invisibleAnnotations != null) ret.addAll(method.invisibleAnnotations);
		return ret;
	}

	private static AnnotationNode annotation(List<AnnotationNode> visible, List<AnnotationNode> invisible, String desc) {
		AnnotationNode a = find(visible, desc);
		return a != null ? a : find(invisible, desc);
	}

	private static AnnotationNode find(List<AnnotationNode> list, String desc) {
		if (list == null) return null;
		for (AnnotationNode a : list) {
			if (a.desc.equals(desc)) return a;
		}
		return null;
	}

	private static Object attr(AnnotationNode ann, String key) {
		if (ann.values == null) return null;
		for (int i = 0; i + 1 < ann.values.size(); i += 2) {
			if (key.equals(ann.values.get(i))) return ann.values.get(i + 1);
		}
		return null;
	}

	private static String simpleName(String desc) {
		String internal = desc.substring(1, desc.length() - 1);
		return internal.substring(internal.lastIndexOf('/') + 1);
	}
}
