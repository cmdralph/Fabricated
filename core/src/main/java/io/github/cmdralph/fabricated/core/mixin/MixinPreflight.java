package io.github.cmdralph.fabricated.core.mixin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding.Severity;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
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
 * target method or injection point does not exist (inside its {@code @Slice}, computed as Mixin does), or a
 * target class that NeoForge loads without mixin support (the JDK, libraries such as DataFixerUpper), where
 * the mixin would silently never apply. Selectors and injection points it cannot evaluate (wildcards,
 * slice bounds other than member references or {@code HEAD}, custom points, targets in other mods) are
 * never reported as failures. When the config declares a plugin, which may disable mixins at runtime, failures are
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
	private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
	private static final String COERCE = "Lorg/spongepowered/asm/mixin/injection/Coerce;";
	private static final String UNIQUE = "Lorg/spongepowered/asm/mixin/Unique;";
	private static final String INTRINSIC = "Lorg/spongepowered/asm/mixin/Intrinsic;";
	private static final String DEFINITION = "Lcom/llamalad7/mixinextras/expression/Definition;";
	private static final String DEFINITIONS = "Lcom/llamalad7/mixinextras/expression/Definitions;";
	private static final Set<String> GAME_PREFIXES = Set.of("net/minecraft/", "com/mojang/blaze3d/");

	private final ClassIndex game;
	private final ClassBytesSource modClasses;
	private final ClassBytesSource untransformable;
	private final boolean clientSide;
	private final Map<String, Optional<ClassNode>> targetCache = new HashMap<>();

	/**
	 * @param game            runtime game classes (Minecraft as patched by NeoForge)
	 * @param modClasses      the mod's own classes (already remapped to runtime names)
	 * @param untransformable classes that exist at runtime but are loaded outside NeoForge's transforming class
	 *                        loader (the JDK and libraries such as DataFixerUpper); mixins into them never apply
	 * @param clientSide      whether the physical side is the client
	 */
	public MixinPreflight(ClassIndex game, ClassBytesSource modClasses, ClassBytesSource untransformable, boolean clientSide) {
		this.game = game;
		this.modClasses = modClasses;
		this.untransformable = untransformable;
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
				ctx.classLevel("Mixin class " + ctx.mixinDotted() + " listed in " + configName + " is missing from the mod jar",
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
			new ClassReader(data).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
			return node;
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	private record Context(String configName, String mixinDotted, MixinConfigFile config, Refmap refmap, boolean hasPlugin,
			List<PreflightFinding> out) {
		String resolve(String selector) {
			return refmap.resolve(mixinDotted.replace('.', '/'), selector);
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
		AnnotationNode mixinAnn = Annotations.find(mixin.invisibleAnnotations, mixin.visibleAnnotations, MIXIN);
		if (mixinAnn == null) {
			ctx.classLevel("Class " + ctx.mixinDotted() + " is listed as a mixin but has no @Mixin annotation", "Report this to the mod author.");
			return;
		}
		boolean pseudo = Annotations.find(mixin.invisibleAnnotations, mixin.visibleAnnotations, PSEUDO) != null;

		List<String> targets = new ArrayList<>();
		Object value = Annotations.value(mixinAnn, "value");
		if (value instanceof List<?> types) {
			for (Object t : types) {
				if (t instanceof Type type) targets.add(type.getInternalName());
			}
		}
		Object targetStrings = Annotations.value(mixinAnn, "targets");
		if (targetStrings instanceof List<?> names) {
			for (Object n : names) {
				if (n instanceof String s) targets.add(ctx.resolve(s).replace('.', '/'));
			}
		}

		for (String target : targets) {
			boolean isGame = GAME_PREFIXES.stream().anyMatch(target::startsWith);
			ClassNode targetNode = isGame ? targetCache.computeIfAbsent(target, t -> Optional.ofNullable(game.readNode(t))).orElse(null) : null;
			if (targetNode == null) {
				if (pseudo) continue;
				if (isGame) {
					ctx.classLevel("Target class " + target.replace('/', '.') + " does not exist in this Minecraft/NeoForge version",
							"The mod was likely built for a different Minecraft version, or NeoForge removed this class.");
				} else if (isUntransformable(target)) {
					ctx.classLevel("Target class " + target.replace('/', '.') + " is a library class that NeoForge loads without mixin support, so this mixin would silently never apply",
							"Ask the mod author for a NeoForge version, or skip this mixin in config/fabricated.json if the feature is optional.");
				} else {
					ctx.info("Target " + target.replace('/', '.') + " is not a Minecraft class and was not verified");
				}
				continue;
			}
			checkMembers(mixin, targetNode, ctx);
		}
	}

	private boolean isUntransformable(String target) {
		try {
			return untransformable.read(target) != null;
		} catch (IOException e) {
			return false;
		}
	}

	private void checkMembers(ClassNode mixin, ClassNode target, Context ctx) {
		String targetDotted = target.name.replace('/', '.');
		for (FieldNode field : mixin.fields) {
			AnnotationNode shadow = Annotations.find(field, SHADOW);
			if (shadow == null) continue;
			String name = ctx.resolve(field.name);
			MemberRef ref = MemberRef.parse(name);
			String fieldName = ref.nameOr(field.name);
			String desc = ref.fieldDescOr(field.desc);
			if (game.resolveField(target.name, fieldName + ":" + desc) == ClassIndex.Resolution.MISSING
					&& !aliasExists(shadow, target, desc, true)) {
				ctx.classLevel("@Shadow field " + fieldName + " (" + desc + ") does not exist in " + targetDotted,
						"NeoForge or this Minecraft version changed " + targetDotted + "; the mod needs an update.");
			}
		}

		for (MethodNode method : mixin.methods) {
			AnnotationNode shadow = Annotations.find(method, SHADOW);
			if (shadow != null) {
				String prefix = Annotations.value(shadow, "prefix") instanceof String p ? p : "shadow$";
				String name = method.name.startsWith(prefix) ? method.name.substring(prefix.length()) : method.name;
				MemberRef ref = MemberRef.parse(ctx.resolve(name));
				String methodName = ref.nameOr(name);
				String desc = ref.methodDescOr(method.desc);
				if (game.resolveMethod(target.name, methodName + desc) == ClassIndex.Resolution.MISSING
						&& !aliasExists(shadow, target, desc, false)) {
					ctx.classLevel("@Shadow method " + methodName + desc + " does not exist in " + targetDotted,
							"NeoForge or this Minecraft version changed " + targetDotted + "; the mod needs an update.");
				}
				continue;
			}

			if (Annotations.find(method, OVERWRITE) != null) {
				MemberRef ref = MemberRef.parse(ctx.resolve(method.name));
				String name = ref.nameOr(method.name);
				String desc = ref.methodDescOr(method.desc);
				if (!declares(target, name, desc)) {
					ctx.classLevel("@Overwrite target " + name + desc + " does not exist in " + targetDotted,
							"The overwritten method was changed by NeoForge or this Minecraft version.");
				}
				continue;
			}

			AnnotationNode accessor = Annotations.find(method, ACCESSOR);
			AnnotationNode invoker = accessor == null ? Annotations.find(method, INVOKER) : null;
			if (accessor != null || invoker != null) {
				// Mixin generates an instance accessor method in the target; one that already exists there (for
				// example one NeoForge added) makes Mixin throw when the target class loads. A static accessor or
				// invoker adds nothing to the target (Mixin rewrites the interface's own method), so a same-named
				// method there is harmless: typically the very method the invoker calls.
				if ((method.access & Opcodes.ACC_STATIC) == 0 && declares(target, method.name, method.desc)) {
					ctx.classLevel((accessor != null ? "@Accessor " : "@Invoker ") + method.name + method.desc + " already exists in "
									+ targetDotted + " (added by NeoForge or this Minecraft version), so Mixin cannot generate it",
							"The mod needs an update for NeoForge; until then it cannot load.");
				} else if (accessor != null) {
					checkAccessor(method, accessor, target, ctx);
				} else {
					checkInvoker(method, invoker, target, ctx);
				}
				continue;
			}

			boolean injector = false;
			for (AnnotationNode ann : allAnnotations(method)) {
				if (isInjector(ann.desc)) {
					injector = true;
					checkInjector(method, ann, target, ctx);
				}
			}
			if (!injector) checkMerged(method, target, ctx);
		}
	}

	/**
	 * A plain mixin method is merged into the target. If the target already declares it, Mixin replaces the
	 * target method only when overwrites are allowed without {@code @Overwrite}; with
	 * {@code overwrites.requireAnnotations} it throws instead. Typical cause: NeoForge added the method
	 * (for example {@code hashCode}) to a class that has none in vanilla.
	 */
	private static void checkMerged(MethodNode method, ClassNode target, Context ctx) {
		if ((method.access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_ABSTRACT)) != 0 || method.name.startsWith("<")) return;
		if (Annotations.find(method, UNIQUE) != null
				|| Annotations.find(method, INTRINSIC) != null) return;
		if (!declares(target, method.name, method.desc)) return;
		String where = method.name + method.desc + " already exists in " + target.name.replace('/', '.');
		if (ctx.config().overwritesRequireAnnotations()) {
			ctx.classLevel(where + " and the config requires @Overwrite to replace it, so Mixin rejects this mixin",
					"NeoForge added this method; the mod needs an update for NeoForge.");
		} else {
			ctx.out().add(new PreflightFinding(Severity.WARN, ctx.configName(), ctx.mixinDotted(),
					where + "; the mixin silently replaces it, which may change NeoForge's behaviour", ""));
		}
	}

	private boolean aliasExists(AnnotationNode shadow, ClassNode target, String desc, boolean field) {
		if (!(Annotations.value(shadow, "aliases") instanceof List<?> aliases)) return false;
		for (Object a : aliases) {
			if (!(a instanceof String alias)) continue;
			ClassIndex.Resolution r = field
					? game.resolveField(target.name, alias + ":" + desc)
					: game.resolveMethod(target.name, alias + desc);
			if (r != ClassIndex.Resolution.MISSING) return true;
		}
		return false;
	}

	private static void checkAccessor(MethodNode method, AnnotationNode ann, ClassNode target, Context ctx) {
		String explicit = Annotations.value(ann, "value") instanceof String s && !s.isEmpty() ? s : null;
		String name = explicit != null ? explicit : inferAccessorName(method.name);
		if (name == null) return;
		MemberRef ref = MemberRef.parse(ctx.resolve(name));
		String fieldName = ref.nameOr(name);
		String desc = ref.fieldDescOr(accessorFieldDesc(method));
		// As Mixin's AccessorInfo: the field type must match exactly; the name must match, or match ignoring case
		// with no other such field. Without a known type, fall back to the name alone.
		int caseInsensitive = 0;
		for (FieldNode f : target.fields) {
			if (desc != null && !f.desc.equals(desc)) continue;
			if (f.name.equals(fieldName)) return;
			if (f.name.equalsIgnoreCase(fieldName)) caseInsensitive++;
		}
		if (caseInsensitive == 1) return;
		boolean nameExists = target.fields.stream().anyMatch(f -> f.name.equals(fieldName));
		ctx.classLevel(nameExists
						? "@Accessor target field " + fieldName + " in " + target.name.replace('/', '.') + " has a different type than " + desc
						: "@Accessor target field " + fieldName + " does not exist in " + target.name.replace('/', '.'),
				nameExists ? "NeoForge or this Minecraft version changed the field's type." : "The field was renamed or removed by NeoForge or this Minecraft version.");
	}

	/** The field type an accessor reads or writes: a getter's return type or a setter's only parameter. */
	private static String accessorFieldDesc(MethodNode method) {
		Type[] args = Type.getArgumentTypes(method.desc);
		Type ret = Type.getReturnType(method.desc);
		if (args.length == 0 && ret.getSort() != Type.VOID) return ret.getDescriptor();
		if (args.length == 1 && ret.getSort() == Type.VOID) return args[0].getDescriptor();
		return null;
	}

	private static void checkInvoker(MethodNode method, AnnotationNode ann, ClassNode target, Context ctx) {
		String explicit = Annotations.value(ann, "value") instanceof String s && !s.isEmpty() ? s : null;
		String name = explicit != null ? explicit : inferInvokerName(method.name);
		if (name == null || name.equals("<init>")) return;
		MemberRef ref = MemberRef.parse(ctx.resolve(name));
		String methodName = ref.nameOr(name);
		String desc = ref.methodDescOr(method.desc);
		if (declares(target, methodName, desc)) return;
		ctx.classLevel("@Invoker target method " + methodName + desc + " does not exist in " + target.name.replace('/', '.'),
				"The method was renamed or removed by NeoForge or this Minecraft version.");
	}

	static String inferAccessorName(String method) {
		return nameAfterPrefix(method, "get", "is", "set");
	}

	static String inferInvokerName(String method) {
		String name = nameAfterPrefix(method, "call", "invoke");
		if (name != null) return name;
		if (method.startsWith("new") || method.startsWith("create")) return "<init>";
		return null;
	}

	/** {@code getFooBar} -> {@code fooBar} for the first matching prefix followed by an upper-case letter. */
	private static String nameAfterPrefix(String method, String... prefixes) {
		for (String prefix : prefixes) {
			if (method.length() > prefix.length() && method.startsWith(prefix) && Character.isUpperCase(method.charAt(prefix.length()))) {
				return decapitalize(method.substring(prefix.length()));
			}
		}
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

	private static void checkInjector(MethodNode handler, AnnotationNode ann, ClassNode target, Context ctx) {
		if (!(Annotations.value(ann, "method") instanceof List<?> selectors) || selectors.isEmpty()) return;
		int require = Annotations.value(ann, "require") instanceof Integer r && r >= 0 ? r : ctx.config().defaultRequire();
		List<AnnotationNode> slices = annotationList(Annotations.value(ann, "slice"));
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
		if (ann.desc.equals(INJECT) && !anyFuzzy && !acceptsCallbackSignature(handler, ann, matched)) {
			ctx.classLevel(injectorName + " handler " + handler.name + Type.getMethodDescriptor(Type.getReturnType(handler.desc), handlerArgs(handler).toArray(Type[]::new))
							+ " does not match the parameters of " + matched.getFirst().name + matched.getFirst().desc + " in " + target.name.replace('/', '.'),
					"NeoForge's build of Minecraft declares this method with different parameters (often a lambda whose captured values are ordered differently); the mod needs an update or this mixin must be skipped.");
			return;
		}

		for (AnnotationNode at : atAnnotations(ann)) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			String rawTarget = Annotations.value(at, "target") instanceof String t ? t : null;
			int ordinal = Annotations.value(at, "ordinal") instanceof Integer i ? i : -1;
			int opcode = Annotations.value(at, "opcode") instanceof Integer i ? i : -1;
			AnnotationNode slice = sliceFor(at, slices);
			Verdict verdict = Verdict.UNKNOWN;
			for (MethodNode m : matched) {
				Range range = slice == null ? Range.ALL : sliceRange(slice, m, ctx);
				Verdict v = point.equals("MIXINEXTRAS:EXPRESSION") ? evaluateExpression(handler, m, ctx)
						: evaluate(point, rawTarget == null ? null : ctx.resolve(rawTarget), ordinal, opcode, range, m);
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

	/**
	 * Mixin accepts an {@code @Inject} handler whose parameters, after MixinExtras sugar parameters are removed,
	 * are either just the callback info or the target's parameters followed by the callback info; anything else
	 * is an {@code InvalidInjectionException} when the mixin applies. Handlers that capture locals or use
	 * {@code @Coerce} are not judged.
	 */
	private static boolean acceptsCallbackSignature(MethodNode handler, AnnotationNode inject, List<MethodNode> targets) {
		if (Annotations.value(inject, "locals") != null) return true;
		List<Type> args = handlerArgs(handler);
		if (args == null) return true;
		if (args.isEmpty() || !isCallbackInfo(args.getLast())) return true; // unusual shape: leave it to Mixin
		if (args.size() == 1) return true;
		List<Type> wanted = args.subList(0, args.size() - 1);
		for (MethodNode target : targets) {
			if (List.of(Type.getArgumentTypes(target.desc)).equals(wanted)) return true;
		}
		return false;
	}

	/** The handler's parameter types without MixinExtras sugar parameters; null if a parameter uses {@code @Coerce}. */
	private static List<Type> handlerArgs(MethodNode handler) {
		Type[] types = Type.getArgumentTypes(handler.desc);
		List<Type> args = new ArrayList<>();
		for (int i = 0; i < types.length; i++) {
			boolean sugar = false;
			for (AnnotationNode a : parameterAnnotations(handler, i)) {
				if (a.desc.equals(COERCE)) return null;
				if (a.desc.startsWith("Lcom/llamalad7/mixinextras/sugar/")) sugar = true;
			}
			if (!sugar) args.add(types[i]);
		}
		return args;
	}

	private static List<AnnotationNode> parameterAnnotations(MethodNode method, int index) {
		List<AnnotationNode> all = new ArrayList<>();
		if (method.visibleParameterAnnotations != null && index < method.visibleParameterAnnotations.length
				&& method.visibleParameterAnnotations[index] != null) all.addAll(method.visibleParameterAnnotations[index]);
		if (method.invisibleParameterAnnotations != null && index < method.invisibleParameterAnnotations.length
				&& method.invisibleParameterAnnotations[index] != null) all.addAll(method.invisibleParameterAnnotations[index]);
		return all;
	}

	private static boolean isCallbackInfo(Type type) {
		return type.getDescriptor().equals("Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;")
				|| type.getDescriptor().equals("Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;");
	}

	private enum Verdict {
		FOUND, MISSING, UNKNOWN
	}

	/**
	 * Instruction index range an injection point is searched in (inclusive). {@link #ALL} is the whole method;
	 * {@link #UNEVALUATED} means a slice exists that pre-flight cannot compute, so only "the instruction occurs
	 * nowhere in the method" can be concluded.
	 */
	private record Range(int start, int end) {
		static final Range ALL = new Range(0, Integer.MAX_VALUE);
		static final Range UNEVALUATED = new Range(-1, -1);
	}

	private static List<AnnotationNode> annotationList(Object value) {
		List<AnnotationNode> ret = new ArrayList<>();
		if (value instanceof AnnotationNode a) ret.add(a);
		if (value instanceof List<?> list) {
			for (Object o : list) {
				if (o instanceof AnnotationNode a) ret.add(a);
			}
		}
		return ret;
	}

	/** The {@code @Slice} an {@code @At} uses (matched by id, as Mixin does), or {@code null} for none. */
	private static AnnotationNode sliceFor(AnnotationNode at, List<AnnotationNode> slices) {
		if (slices.isEmpty()) return null;
		String id = Annotations.value(at, "slice") instanceof String s ? s : "";
		for (AnnotationNode slice : slices) {
			String sliceId = Annotations.value(slice, "id") instanceof String s ? s : "";
			if (sliceId.equals(id)) return slice;
		}
		return null;
	}

	/**
	 * Mirrors Mixin's {@code MethodSlice.getSlice}: {@code from} defaults to the first instruction and falls back
	 * to it when its point is not found; {@code to} defaults to the last instruction and falls back to the
	 * start. Points select their first match (or the {@code ordinal}-th). Only member-reference points
	 * ({@code INVOKE}, {@code FIELD}, {@code NEW}) and {@code HEAD} are evaluated.
	 */
	private static Range sliceRange(AnnotationNode slice, MethodNode method, Context ctx) {
		int max = method.instructions.size() - 1;
		int start = slicePoint(annotationOrNull(Annotations.value(slice, "from")), method, ctx, 0, 0);
		if (start == UNEVALUATED_INDEX) return Range.UNEVALUATED;
		int end = slicePoint(annotationOrNull(Annotations.value(slice, "to")), method, ctx, max, start);
		if (end == UNEVALUATED_INDEX || start > end) return Range.UNEVALUATED;
		return new Range(start, end);
	}

	private static final int UNEVALUATED_INDEX = Integer.MIN_VALUE;

	private static AnnotationNode annotationOrNull(Object value) {
		return value instanceof AnnotationNode a ? a : null;
	}

	private static int slicePoint(AnnotationNode at, MethodNode method, Context ctx, int defaultValue, int failValue) {
		if (at == null) return defaultValue;
		String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
		if (point.equals("HEAD")) return 0;
		if (!point.equals("INVOKE") && !point.equals("INVOKE_ASSIGN") && !point.equals("FIELD") && !point.equals("NEW")) return UNEVALUATED_INDEX;
		String rawTarget = Annotations.value(at, "target") instanceof String t ? t : null;
		if (rawTarget == null || rawTarget.contains(":LAST") || rawTarget.contains(":ONE") || rawTarget.contains(":FIRST")) return UNEVALUATED_INDEX;
		MemberRef ref = MemberRef.parse(ctx.resolve(rawTarget));
		if (ref.fuzzy()) return UNEVALUATED_INDEX;
		int ordinal = Annotations.value(at, "ordinal") instanceof Integer i ? i : -1;
		int opcode = Annotations.value(at, "opcode") instanceof Integer i ? i : -1;
		int seen = 0;
		for (int i = 0; i < method.instructions.size(); i++) {
			AbstractInsnNode insn = method.instructions.get(i);
			if (opcode >= 0 && insn.getOpcode() != opcode) continue;
			if (!matchesInsn(point, ref, insn)) continue;
			if (ordinal < 0 || seen == ordinal) return i;
			seen++;
		}
		return failValue;
	}

	private static Verdict evaluate(String point, String target, int ordinal, int opcode, Range range, MethodNode method) {
		switch (point) {
			case "HEAD", "RETURN", "TAIL" -> {
				return method.instructions == null || method.instructions.size() == 0 ? Verdict.MISSING : Verdict.FOUND;
			}
			case "INVOKE", "INVOKE_ASSIGN", "INVOKE_STRING", "FIELD", "NEW" -> {
				if (target == null) return Verdict.UNKNOWN;
				MemberRef ref = MemberRef.parse(target);
				if (ref.fuzzy()) return Verdict.UNKNOWN;
				int countInMethod = 0;
				int countInRange = 0;
				for (int i = 0; i < method.instructions.size(); i++) {
					AbstractInsnNode insn = method.instructions.get(i);
					if (opcode >= 0 && insn.getOpcode() != opcode) continue;
					if (!matchesInsn(point, ref, insn)) continue;
					countInMethod++;
					if (i >= range.start() && i <= range.end()) countInRange++;
				}
				if (countInMethod == 0) return Verdict.MISSING;
				if (range == Range.UNEVALUATED) return Verdict.FOUND;
				if (countInRange == 0) return Verdict.MISSING;
				if (ordinal >= 0 && countInRange <= ordinal) return Verdict.MISSING;
				return Verdict.FOUND;
			}
			default -> {
				return Verdict.UNKNOWN;
			}
		}
	}

	/**
	 * A MixinExtras expression can only match when what each of the handler's {@code @Definition}s names (a type, a
	 * method or a field) occurs in the target method. The expression itself is not evaluated.
	 */
	private static Verdict evaluateExpression(MethodNode handler, MethodNode method, Context ctx) {
		List<AnnotationNode> definitions = new ArrayList<>();
		for (AnnotationNode ann : allAnnotations(handler)) {
			if (ann.desc.equals(DEFINITION)) definitions.add(ann);
			else if (ann.desc.equals(DEFINITIONS)) definitions.addAll(annotationList(Annotations.value(ann, "value")));
		}
		if (definitions.isEmpty() || method.instructions == null) return Verdict.UNKNOWN;
		for (AnnotationNode definition : definitions) {
			if (!definitionOccurs(definition, method, ctx)) return Verdict.MISSING;
		}
		return Verdict.FOUND;
	}

	private static boolean definitionOccurs(AnnotationNode definition, MethodNode method, Context ctx) {
		List<AbstractInsnNode> insns = List.of(method.instructions.toArray());
		boolean judged = false;
		if (Annotations.value(definition, "type") instanceof List<?> types) {
			for (Object o : types) {
				if (!(o instanceof Type type) || (type.getSort() != Type.OBJECT && type.getSort() != Type.ARRAY)) return true;
				judged = true;
				if (insns.stream().anyMatch(insn -> references(insn, type))) return true;
			}
		}
		for (String kind : List.of("method", "field")) {
			if (!(Annotations.value(definition, kind) instanceof List<?> refs)) continue;
			for (Object o : refs) {
				if (!(o instanceof String raw)) continue;
				MemberRef ref = MemberRef.parse(ctx.resolve(raw));
				if (ref.fuzzy() || ref.name() == null) return true;
				judged = true;
				String point = kind.equals("method") ? "INVOKE" : "FIELD";
				if (insns.stream().anyMatch(insn -> matchesInsn(point, ref, insn))) return true;
			}
		}
		return !judged;
	}

	/** Whether {@code insn} uses {@code type}: creates, casts or checks it, calls or reads a member of it, or passes it. */
	private static boolean references(AbstractInsnNode insn, Type type) {
		String internal = type.getInternalName();
		String desc = type.getDescriptor();
		return switch (insn) {
			case TypeInsnNode t -> t.desc.equals(internal);
			case MethodInsnNode m -> m.owner.equals(internal) || m.desc.contains(desc);
			case FieldInsnNode f -> f.owner.equals(internal) || f.desc.contains(desc);
			case InvokeDynamicInsnNode d -> d.desc.contains(desc);
			case LdcInsnNode l -> type.equals(l.cst);
			default -> false;
		};
	}

	private static boolean matchesInsn(String point, MemberRef ref, AbstractInsnNode insn) {
		switch (point) {
			case "INVOKE", "INVOKE_ASSIGN", "INVOKE_STRING" -> {
				if (!(insn instanceof MethodInsnNode m)) return false;
				return (ref.owner() == null || ref.owner().equals(m.owner)) && ref.matchesMethod(m.name, m.desc);
			}
			case "FIELD" -> {
				if (!(insn instanceof FieldInsnNode f)) return false;
				return (ref.owner() == null || ref.owner().equals(f.owner))
						&& ref.name() != null && ref.name().equals(f.name)
						&& (ref.desc() == null || ref.desc().equals(f.desc));
			}
			case "NEW" -> {
				String cls = newTarget(ref);
				String ctorDesc = ref.methodDescOr(null);
				if (ctorDesc != null) {
					// A constructor descriptor selects one constructor, as Mixin's BeforeNew does: count the
					// INVOKESPECIAL <init> with those arguments rather than any NEW of the class.
					String initDesc = ctorDesc.substring(0, ctorDesc.indexOf(')') + 1) + "V";
					return insn instanceof MethodInsnNode m && m.getOpcode() == Opcodes.INVOKESPECIAL
							&& m.name.equals("<init>") && m.desc.equals(initDesc) && (cls == null || cls.equals(m.owner));
				}
				if (!(insn instanceof TypeInsnNode t) || t.getOpcode() != Opcodes.NEW) return false;
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
		String ctorDesc = ref.methodDescOr(null);
		if (ctorDesc != null) {
			Type ret = Type.getReturnType(ctorDesc);
			return ret.getSort() == Type.OBJECT ? ret.getInternalName() : null;
		}
		if (ref.name() != null && ref.owner() == null) return ref.name();
		return ref.owner();
	}

	private static List<AnnotationNode> atAnnotations(AnnotationNode injector) {
		return annotationList(Annotations.value(injector, "at"));
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

	private static String simpleName(String desc) {
		String internal = desc.substring(1, desc.length() - 1);
		return internal.substring(internal.lastIndexOf('/') + 1);
	}
}
