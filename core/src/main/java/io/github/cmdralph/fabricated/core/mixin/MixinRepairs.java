package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding.Severity;
import io.github.cmdralph.fabricated.core.mixin.MixinPreflight.Context;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Mixins that Mixin would reject but that can be pointed at what the game really has, before pre-flight checks them
 * ({@link MixinPreflight#repairs}). The injector repairs run in one order ({@link #injectorRepairs}), each from its
 * family's class: lambdas ({@link LambdaRepairs}), overloads ({@link OverloadRepairs}), calls ({@link CallRepairs}),
 * constructors ({@link ConstructorRepairs}), locals and arguments ({@link LocalRepairs}); the class-level and member
 * repairs (injection points made single, anonymous classes, accessors and shadows of captured variables, accessors of
 * methods the game declares) are here.
 */
final class MixinRepairs extends PreflightPart {
	private final MixinPreflight checker;
	private final LambdaRepairs lambdas = new LambdaRepairs(classes);
	private final OverloadRepairs overloads = new OverloadRepairs(classes);
	private final CallRepairs calls = new CallRepairs(classes);
	private final ConstructorRepairs constructors = new ConstructorRepairs(classes);
	private final LocalRepairs locals = new LocalRepairs(classes);

	/** @param checker the run's checker, which judges whether a mixin fits a target */
	MixinRepairs(PreflightClasses classes, MixinPreflight checker) {
		super(classes);
		this.checker = checker;
	}

	/** {@link MixinPreflight#repairs}: the mixin classes rewritten, by class file name; each repair reported. */
	Map<String, byte[]> repairs(String configName, MixinConfigFile config, Refmap refmap, Set<String> skip,
			List<PreflightFinding> out) {
		Map<String, byte[]> repaired = new TreeMap<>();
		for (String mixin : config.mixinsFor(clientSide)) {
			if (skip.contains(mixin)) continue;
			String internal = config.internalName(mixin);
			ClassNode node = modClasses.readNode(internal, 0);
			if (node == null) continue;
			Context ctx = new Context(configName, internal.replace('/', '.'), config, refmap, false, false, false, Set.of(), out, null, loader);
			independent.put(node, MixinPreflight.independentHandlers(node));
			boolean changed = singleInjectionPoints(node);
			for (String moved : anonymousTargetRepairs(node, ctx)) {
				changed = true;
				out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "Target class " + moved, ""));
			}
			List<ClassNode> targets = new ArrayList<>();
			for (String target : MixinPreflight.targetsOf(node, ctx)) {
				ClassNode targetNode = gameTarget(target);
				if (targetNode == null) {
					targets = List.of();
					break;
				}
				targets.add(targetNode);
			}
			if (targets.isEmpty()) {
				// Not a game class's: only the injection points are made single.
				if (changed) repaired.put(internal + ".class", write(node));
				continue;
			}
			List<MethodNode> bridges = new ArrayList<>();
			for (MethodNode method : node.methods) {
				for (AnnotationNode ann : Annotations.all(method)) {
					if (PreflightAsm.isInjector(ann.desc) && repairInjector(new RepairSite(node, method, ann, targets, ctx, bridges), out)) changed = true;
				}
				if ((node.access & Opcodes.ACC_INTERFACE) == 0 || (method.access & Opcodes.ACC_STATIC) != 0) continue;
				AnnotationNode accessor = Annotations.find(method, MixinNames.ACCESSOR);
				String kind = accessor != null ? "@Accessor" : Annotations.find(method, MixinNames.INVOKER) != null ? "@Invoker" : null;
				if (kind == null) continue;
				if (targets.stream().allMatch(t -> declaresInstance(t, method.name, method.desc))) {
					if (method.visibleAnnotations != null) method.visibleAnnotations.removeIf(MixinRepairs::isAccessorAnnotation);
					if (method.invisibleAnnotations != null) method.invisibleAnnotations.removeIf(MixinRepairs::isAccessorAnnotation);
					if (method.visibleAnnotations == null) method.visibleAnnotations = new ArrayList<>();
					method.visibleAnnotations.add(new AnnotationNode(MixinNames.SHADOW));
					method.access |= Opcodes.ACC_SYNTHETIC;
					changed = true;
					boolean madePublic = false;
					for (ClassNode t : targets) {
						if (declaresPublicInstance(t, method.name, method.desc)) continue;
						widened.add("public " + t.name.replace('/', '.') + " " + method.name + method.desc);
						madePublic = true;
					}
					out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), kind + " " + method.name + method.desc
							+ " already exists " + (madePublic ? "" : "publicly ") + "in " + targets.get(0).name.replace('/', '.')
							+ " (added by " + loader + " or this Minecraft version); the mixin uses that method instead of generating one"
							+ (madePublic ? ", made public for it" : ""), ""));
				} else if (accessor != null) {
					String field = capturedFieldRepair(method, accessor, targets, ctx);
					if (field == null) continue;
					changed = true;
					out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@Accessor " + method.name + method.desc
							+ " targets the captured variable field " + field + " in " + targets.get(0).name.replace('/', '.')
							+ ", which this game build names differently", ""));
				}
			}
			for (FieldNode field : node.fields) {
				String captured = capturedShadowRepair(node, field, targets, ctx);
				if (captured == null) continue;
				changed = true;
				out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@Shadow field " + captured + " is a captured variable field of "
						+ targets.get(0).name.replace('/', '.') + ", which this game build names " + field.name + "; the mixin uses that", ""));
			}
			node.methods.addAll(bridges);
			if (changed) repaired.put(internal + ".class", write(node));
		}
		return repaired;
	}

	/**
	 * One injector the repairs look at: the mixin, the injector's handler and annotation, the mixin's targets, the
	 * mixin's context, and the bridges repairs make for it (added to the mixin once all its injectors are done).
	 */
	record RepairSite(ClassNode mixin, MethodNode handler, AnnotationNode injector, List<ClassNode> targets, Context ctx,
			List<MethodNode> bridges) {
		/** The mixin's one target, for the steps that only run where it has one. */
		ClassNode target() {
			return targets.get(0);
		}

		/** The injector as the report names it ({@code @Inject handler}). */
		String named() {
			return "@" + PreflightAsm.simpleName(injector.desc) + " " + handler.name;
		}

		/** Whether a repair moved the injector off its handler (onto a bridge, or wrapped it): it is done with then. */
		boolean moved() {
			return !Annotations.all(handler).contains(injector);
		}
	}

	/** A repair of one injector: what it moved, each named for the report, or nothing where it changed nothing. */
	@FunctionalInterface
	interface InjectorRepair {
		List<String> repair(RepairSite site);
	}

	/** The injectors a repair step looks at. */
	private enum RepairScope {
		ANY,
		/**
		 * Not one of a group's alternatives: each is the mod's alternative for code that differs, and pointing one
		 * elsewhere could make more of the group apply than it allows, or another may already name this build's code.
		 */
		UNGROUPED,
		/** A mixin with one target. */
		ONE_TARGET,
		ONE_TARGET_UNGROUPED;

		boolean covers(RepairSite site) {
			boolean ungrouped = this == ANY || this == ONE_TARGET || !PreflightAsm.grouped(site.handler());
			return ungrouped && (this == ANY || this == UNGROUPED || site.targets().size() == 1);
		}
	}

	/**
	 * A step of {@link #injectorRepairs}: a repair, the injectors it looks at and how the report names each move; or,
	 * without a repair, a stop once an earlier step has moved the injector off its handler.
	 */
	private record RepairStep(RepairScope scope, Severity severity, InjectorRepair repair, BiFunction<RepairSite, String, String> message) {
		static RepairStep warn(RepairScope scope, InjectorRepair repair, BiFunction<RepairSite, String, String> message) {
			return new RepairStep(scope, Severity.WARN, repair, message);
		}

		static RepairStep stopIfMoved() {
			return new RepairStep(RepairScope.ANY, Severity.INFO, null, null);
		}
	}

	/**
	 * The injector repairs, in the order they run. A step sees what the earlier ones made of the injector; once one has
	 * moved it off its handler, the next stop ends the injector's repairs. A new repair goes where what it repairs can
	 * only be seen once the earlier ones are done, with a stop after it if it can move the injector.
	 */
	private final List<RepairStep> injectorRepairs = List.of(
			RepairStep.warn(RepairScope.UNGROUPED, s -> lambdas.vanillaLambdaRepairs(s.injector(), s.targets(), s.ctx()),
					(s, lambda) -> s.named() + " targets " + lambda + ", which this game build numbers differently"),
			RepairStep.warn(RepairScope.ANY, s -> LambdaRepairs.lambdaRepairs(s.handler(), s.injector(), s.targets(), s.ctx()),
					(s, lambda) -> s.named() + " targets " + lambda + ", which this game build numbers differently"),
			RepairStep.warn(RepairScope.ANY, s -> locals.argumentNameRepairs(s.handler(), s.injector(), s.targets(), s.ctx()),
					(s, named) -> "@ModifyVariable " + s.handler().name + " takes " + named),
			RepairStep.warn(RepairScope.ANY, s -> constructors.constructorPointRepairs(s.handler(), s.injector(), s.targets(), s.ctx()),
					(s, marked) -> "@Inject " + s.handler().name + " injects into " + marked),
			RepairStep.warn(RepairScope.ANY, s -> constructors.constructorCallWrappers(s.mixin(), s.handler(), s.injector(), s.targets(), s.ctx(), s.bridges()),
					(s, wrapped) -> "@Inject " + s.handler().name + " injects into " + wrapped),
			// Wrapped, the handler is a plain method of the mixin.
			RepairStep.stopIfMoved(),
			new RepairStep(RepairScope.ANY, Severity.INFO, s -> LocalRepairs.softenLocalCapture(s.injector()) ? List.of("") : List.of(),
					(s, none) -> "@Inject " + s.handler().name + " captures local variables; if " + s.ctx().loader()
							+ "'s code has other locals there, it is skipped instead of crashing"),
			// A group's alternative for Fabric Loader's hook too: the other alternatives are for other loaders' hooks.
			RepairStep.warn(RepairScope.ONE_TARGET, s -> CallRepairs.loaderHookRepairs(s.injector(), s.target(), s.ctx()),
					(s, moved) -> s.named() + " expects a call to " + moved),
			RepairStep.warn(RepairScope.ONE_TARGET_UNGROUPED, s -> OverloadRepairs.overloadByParameters(s.handler(), s.injector(), s.target(), s.ctx()),
					(s, pinned) -> s.named() + " targets " + pinned + ", which this game build declares after another overload; it is pinned to that one"),
			RepairStep.warn(RepairScope.ONE_TARGET_UNGROUPED, s -> calls.vanillaPointRepairs(s.mixin(), s.handler(), s.injector(), s.target(), s.ctx(), s.bridges()),
					(s, moved) -> s.named() + " expects " + moved),
			RepairStep.stopIfMoved(),
			RepairStep.warn(RepairScope.ONE_TARGET_UNGROUPED, s -> locals.vanillaLocalRepairs(s.handler(), s.injector(), s.target(), s.ctx()),
					(s, local) -> s.named() + " captures " + local),
			RepairStep.warn(RepairScope.ONE_TARGET_UNGROUPED, s -> LocalRepairs.namedLocalRepairs(s.handler(), s.injector(), s.target(), s.ctx()),
					(s, local) -> s.named() + " captures " + local),
			RepairStep.warn(RepairScope.ONE_TARGET_UNGROUPED, s -> overloads.overloadRepairs(s.mixin(), s.handler(), s.injector(), s.target(), s.ctx(), s.bridges()),
					(s, moved) -> s.named() + " targets " + moved),
			// A repair that bridged the handler moved the injector onto the bridge.
			RepairStep.stopIfMoved(),
			RepairStep.warn(RepairScope.ONE_TARGET_UNGROUPED, s -> overloads.constantSplitRepairs(s.mixin(), s.handler(), s.injector(), s.target(), s.ctx()),
					(s, moved) -> "@ModifyConstant " + s.handler().name + " targets " + moved),
			RepairStep.warn(RepairScope.ONE_TARGET_UNGROUPED, s -> calls.subtypeCallRepairs(s.injector(), s.target(), s.ctx()),
					(s, moved) -> s.named() + " expects a call to " + moved),
			RepairStep.warn(RepairScope.ONE_TARGET_UNGROUPED, s -> LambdaRepairs.lambdaCallRepairs(s.injector(), s.handler(), s.target(), s.ctx()),
					(s, lambda) -> s.named() + " expects a call " + lambda),
			RepairStep.warn(RepairScope.ONE_TARGET_UNGROUPED, s -> calls.insertedArgumentRepairs(s.mixin(), s.handler(), s.injector(), s.target(), s.ctx(), s.bridges()),
					(s, moved) -> s.named() + " expects a call to " + moved),
			RepairStep.stopIfMoved(),
			// Where the parameters can't be matched by type (one the method has twice), in order, around one added.
			RepairStep.warn(RepairScope.ONE_TARGET_UNGROUPED, s -> {
				List<String> captured = lambdas.capturedValueRepairs(s.mixin(), s.handler(), s.injector(), s.target(), s.ctx(), s.bridges());
				return captured.isEmpty() ? LambdaRepairs.addedParameterRepairs(s.mixin(), s.handler(), s.injector(), s.target(), s.ctx(), s.bridges()) : captured;
			}, (s, lambda) -> s.named() + " targets " + lambda));

	/** Runs {@link #injectorRepairs} on one injector, reporting each move; returns whether any changed the mixin. */
	private boolean repairInjector(RepairSite site, List<PreflightFinding> out) {
		boolean changed = false;
		for (RepairStep step : injectorRepairs) {
			if (step.repair() == null) {
				if (site.moved()) break;
				continue;
			}
			if (!step.scope().covers(site)) continue;
			for (String item : step.repair().repair(site)) {
				changed = true;
				out.add(new PreflightFinding(step.severity(), site.ctx().configName(), site.ctx().mixinDotted(), step.message().apply(site, item), ""));
			}
		}
		return changed;
	}

	private static byte[] write(ClassNode node) {
		ClassWriter writer = new ClassWriter(0);
		node.accept(writer);
		return writer.toByteArray();
	}

	/**
	 * Gives the injectors whose {@code at} Fabric's Mixin declares as an array, and the Mixin NeoForge 1.21.11 runs as a
	 * single injection point ({@code @Redirect}, {@code @ModifyArg}, {@code @ModifyArgs}, {@code @ModifyVariable}), that
	 * single point where the array holds one, as both read it. MixinExtras 0.5.3, which NeoForge 1.21.11 runs, reads a
	 * {@code @Redirect}'s {@code at} as one annotation and crashed on Friends and Foes' (run 382). Returns whether it
	 * changed anything.
	 */
	static boolean singleInjectionPoints(ClassNode mixin) {
		boolean changed = false;
		for (MethodNode method : mixin.methods) {
			for (AnnotationNode ann : Annotations.all(method)) {
				if (!MixinNames.SINGLE_POINT_INJECTORS.contains(ann.desc) || ann.values == null) continue;
				for (int i = 0; i + 1 < ann.values.size(); i += 2) {
					if ("at".equals(ann.values.get(i)) && ann.values.get(i + 1) instanceof List<?> points && points.size() == 1) {
						ann.values.set(i + 1, points.get(0));
						changed = true;
					}
				}
			}
		}
		return changed;
	}

	/**
	 * Points an {@code @Accessor} of a missing captured variable field ({@code val$name}) at the only captured field of
	 * the same type that every target has under one other name, and returns the original field name; or returns
	 * {@code null} and changes nothing.
	 */
	private static String capturedFieldRepair(MethodNode method, AnnotationNode accessor, List<ClassNode> targets, Context ctx) {
		String explicit = Annotations.value(accessor, "value") instanceof String s && !s.isEmpty() ? s : null;
		if (explicit == null) return null;
		MemberRef ref = MemberRef.parse(ctx.resolve(explicit));
		String name = ref.nameOr(explicit);
		String desc = ref.fieldDescOr(MixinPreflight.accessorFieldDesc(method));
		if (!name.startsWith(MixinNames.CAPTURED_PREFIX) || desc == null) return null;
		String renamed = null;
		for (ClassNode target : targets) {
			String only = null;
			for (FieldNode f : target.fields) {
				if (f.name.equals(name)) return null; // present: nothing to repair
				if ((f.access & Opcodes.ACC_STATIC) != 0 || !f.desc.equals(desc)) continue;
				if (!f.name.startsWith(MixinNames.CAPTURED_PREFIX) && (f.access & Opcodes.ACC_SYNTHETIC) == 0) continue;
				if (only != null) return null; // ambiguous
				only = f.name;
			}
			if (only == null || (renamed != null && !renamed.equals(only))) return null;
			renamed = only;
		}
		if (renamed == null) return null;
		for (int i = 0; i + 1 < accessor.values.size(); i += 2) {
			if ("value".equals(accessor.values.get(i))) accessor.values.set(i + 1, renamed);
		}
		return name;
	}

	/**
	 * Renames a {@code @Shadow} field of a missing captured variable field ({@code val$name}) after the only captured field
	 * of the same type that every target has under one other name, as {@link #capturedFieldRepair} does for accessors
	 * (NeoForge's 1.21.11 build names them after the decompiled variables), with the mixin's own uses of it; returns its
	 * original name, or {@code null} and changes nothing.
	 */
	private static String capturedShadowRepair(ClassNode mixin, FieldNode field, List<ClassNode> targets, Context ctx) {
		if (Annotations.find(field, MixinNames.SHADOW) == null || (field.access & Opcodes.ACC_STATIC) != 0) return null;
		String name = MemberRef.parse(ctx.resolve(field.name)).nameOr(field.name);
		if (!name.equals(field.name) || !name.startsWith(MixinNames.CAPTURED_PREFIX)) return null;
		String renamed = null;
		for (ClassNode target : targets) {
			String only = null;
			for (FieldNode f : target.fields) {
				if (f.name.equals(name)) return null; // present: nothing to repair
				if ((f.access & Opcodes.ACC_STATIC) != 0 || !f.desc.equals(field.desc)) continue;
				if (!f.name.startsWith(MixinNames.CAPTURED_PREFIX) && (f.access & Opcodes.ACC_SYNTHETIC) == 0) continue;
				if (only != null) return null; // ambiguous
				only = f.name;
			}
			if (only == null || (renamed != null && !renamed.equals(only))) return null;
			renamed = only;
		}
		if (renamed == null) return null;
		for (MethodNode method : mixin.methods) {
			for (AbstractInsnNode insn : method.instructions) {
				if (insn instanceof FieldInsnNode use && use.owner.equals(mixin.name) && use.name.equals(name) && use.desc.equals(field.desc)) {
					use.name = renamed;
				}
			}
		}
		field.name = renamed;
		return name;
	}

	private static boolean isAccessorAnnotation(AnnotationNode annotation) {
		return annotation.desc.equals(MixinNames.ACCESSOR) || annotation.desc.equals(MixinNames.INVOKER);
	}

	private static boolean declaresPublicInstance(ClassNode target, String name, String desc) {
		MethodNode m = PreflightAsm.declared(target, name, desc);
		return m != null && (m.access & Opcodes.ACC_PUBLIC) != 0 && (m.access & Opcodes.ACC_STATIC) == 0;
	}

	private static boolean declaresInstance(ClassNode target, String name, String desc) {
		MethodNode m = PreflightAsm.declared(target, name, desc);
		return m != null && (m.access & Opcodes.ACC_STATIC) == 0;
	}

	/**
	 * Points a mixin at the anonymous class of the same game class that fits it, when the one it names doesn't: NeoForge's
	 * build of the game, recompiled from decompiled source, numbers some anonymous classes otherwise than Mojang's, which
	 * mods are built against (Polymer's hooks into {@code ByteBufCodecs}' and {@code ItemStack}'s stream codecs, No Chat
	 * Reports' into the JSON one, Ledger's and malilib's into {@code ChestBlock}'s; ANON-1). A class fits when pre-flight
	 * finds nothing but notes in the mixin against it: its selectors, shadows and injection points are all there, a shadow
	 * of a captured value perhaps under another name ({@link #capturedRenames}). Only a single fitting class is taken.
	 * Returns a description of each move.
	 */
	private List<String> anonymousTargetRepairs(ClassNode mixin, Context ctx) {
		AnnotationNode mixinAnn = Annotations.find(mixin.invisibleAnnotations, mixin.visibleAnnotations, Annotations.MIXIN);
		if (mixinAnn == null || (mixin.access & Opcodes.ACC_INTERFACE) != 0 || !(Annotations.value(mixinAnn, "targets") instanceof List<?> names)) {
			return List.of();
		}
		List<String> moved = new ArrayList<>();
		for (int i = 0; i < names.size(); i++) {
			if (!(names.get(i) instanceof String raw)) continue;
			String target = ctx.resolve(raw).replace('.', '/');
			int dollar = target.lastIndexOf('$');
			if (!PreflightAsm.isGameClass(target) || dollar < 0 || !target.substring(dollar + 1).matches("\\d+")) continue;
			ClassNode named = gameTarget(target);
			if (named != null && fits(mixin, named, ctx)) continue;
			ClassNode original = vanillaClass(target);
			Map<String, String> namedRenames = named == null ? null : capturedRenames(mixin, named, original);
			if (namedRenames != null && !namedRenames.isEmpty() && fits(renamed(mixin, namedRenames), named, ctx)) {
				renameShadows(mixin, namedRenames);
				moved.add(target.replace('/', '.') + " names the values it captures otherwise in this game build " + namedRenames
						+ "; the mixin's shadows of them are renamed");
				continue;
			}
			ClassNode outer = gameTarget(target.substring(0, dollar));
			if (outer == null) continue;
			ClassNode match = null;
			Map<String, String> matchRenames = Map.of();
			for (InnerClassNode inner : outer.innerClasses) {
				// Anonymous classes have neither a simple name nor an outer class in the attribute.
				if (inner.innerName != null || inner.outerName != null || inner.name.equals(target)
						|| !inner.name.startsWith(outer.name + "$") || inner.name.indexOf('$', outer.name.length() + 1) >= 0) continue;
				ClassNode candidate = gameTarget(inner.name);
				Map<String, String> renames = candidate == null ? null : capturedRenames(mixin, candidate, original);
				if (renames == null || !fits(renames.isEmpty() ? mixin : renamed(mixin, renames), candidate, ctx)) continue;
				if (match != null) {
					match = null;
					break;
				}
				match = candidate;
				matchRenames = renames;
			}
			if (match == null) continue;
			renameShadows(mixin, matchRenames);
			@SuppressWarnings("unchecked")
			List<Object> values = (List<Object>) names;
			values.set(i, raw.indexOf('/') >= 0 ? match.name : match.name.replace('/', '.'));
			moved.add(target.replace('/', '.') + " doesn't fit the mixin in this game build, whose anonymous classes are numbered differently; "
					+ match.name.replace('/', '.') + " does" + (matchRenames.isEmpty() ? "" : ", naming the values it captures otherwise " + matchRenames)
					+ ", and the mixin is pointed there");
		}
		return moved;
	}

	/**
	 * New names for the mixin's shadows of values an anonymous class captures ({@code val$name}) that {@code target} has
	 * under another name: the one captured value of the same type there (CAPTURE-1), or, of several, the one in the place
	 * the shadow's has among vanilla's of that type in {@code original}, the class the mixin names in vanilla's code
	 * ({@link #inVanillasOrder}, CAPTURE-2). NeoForge's 1.21.11 build names them after its own parameter names
	 * (recompiled from decompiled source; inferred from Polymer's {@code val$registryKey} missing there). A shadow in SRG's
	 * names is one of them where vanilla's class declares it synthetic, and takes a synthetic field of its type: Forge
	 * 47's build, recompiled too, names them as javac does (Fabric API's interaction hook shadows {@code f_143670_} of
	 * {@code ServerGamePacketListenerImpl$1}, there {@code val$entity}; CAPTURE-3). Empty when every shadow is there, null
	 * when one's new name can't be told (none of its type, several that vanilla's class doesn't tell apart, or one
	 * another shadow has).
	 */
	private static Map<String, String> capturedRenames(ClassNode mixin, ClassNode target, ClassNode original) {
		Map<String, String> renames = new TreeMap<>();
		Set<String> kept = new HashSet<>();
		for (FieldNode field : mixin.fields) {
			if (Annotations.find(field, MixinNames.SHADOW) == null) continue;
			// SRG names a captured value, and the outer instance, as it does any field (f_143670_), where Mojang's names
			// are javac's (val$entity, this$0): vanilla's class marks it synthetic (CAPTURE-3).
			boolean javacNamed = field.name.startsWith("val$");
			if (!javacNamed && !synthetic(original, field.desc).contains(field.name)) continue;
			if (target.fields.stream().anyMatch(f -> f.name.equals(field.name) && f.desc.equals(field.desc))) {
				kept.add(field.name);
				continue;
			}
			List<String> sameType = javacNamed ? captured(target, field.desc) : synthetic(target, field.desc);
			String renamed = sameType.size() == 1 ? sameType.get(0) : inVanillasOrder(field.name, field.desc, javacNamed, sameType, target, original);
			if (renamed == null) return null;
			renames.put(field.name, renamed);
		}
		Set<String> to = new HashSet<>(renames.values());
		return to.size() == renames.size() && Collections.disjoint(to, kept) ? renames : null;
	}

	/** The values an anonymous class captures of one type ({@code val$} fields), in the order it declares them. */
	private static List<String> captured(ClassNode type, String desc) {
		return type.fields.stream().filter(f -> f.name.startsWith("val$") && f.desc.equals(desc)).map(f -> f.name).toList();
	}

	/**
	 * A class's synthetic instance fields of one type, in the order it declares them: an anonymous class's captured values
	 * and outer instance, whatever their names. Empty without the class.
	 */
	private static List<String> synthetic(ClassNode type, String desc) {
		if (type == null) return List.of();
		return type.fields.stream().filter(f -> (f.access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_STATIC)) == Opcodes.ACC_SYNTHETIC && f.desc.equals(desc))
				.map(f -> f.name).toList();
	}

	/**
	 * Which of {@code target}'s captured values of a type is vanilla's {@code name}, when it captures several (Ledger's and
	 * malilib's hooks into {@code ChestBlock}'s menu provider shadow the two chest block entities it captures): the one in
	 * the place {@code name} has among {@code original}'s of the type. The order javac declares captured values in follows
	 * the class's code, which a recompiled build keeps; as that is inferred, not read, the place is taken only when
	 * {@code original} captures as many of the type and every method both classes declare reads them in the same order,
	 * matched by place, with at least one such read. Null otherwise, or without vanilla's class. {@code javacNamed}: the
	 * values go by javac's names ({@code val$name}) in both classes, else by vanilla's synthetic fields under SRG's.
	 */
	private static String inVanillasOrder(String name, String desc, boolean javacNamed, List<String> sameType, ClassNode target, ClassNode original) {
		if (original == null || sameType.size() < 2) return null;
		List<String> originals = javacNamed ? captured(original, desc) : synthetic(original, desc);
		int place = originals.indexOf(name);
		if (place < 0 || originals.size() != sameType.size()) return null;
		Map<String, String> byPlace = new HashMap<>();
		for (int i = 0; i < originals.size(); i++) byPlace.put(originals.get(i), sameType.get(i));
		boolean compared = false;
		for (MethodNode method : original.methods) {
			if (method.name.equals("<init>") || method.instructions == null) continue;
			MethodNode same = target.methods.stream().filter(m -> m.name.equals(method.name) && m.desc.equals(method.desc)).findFirst().orElse(null);
			if (same == null) continue;
			List<String> expected = reads(method, original.name, byPlace.keySet()).stream().map(byPlace::get).toList();
			if (!expected.equals(reads(same, target.name, byPlace.values()))) return null;
			compared |= !expected.isEmpty();
		}
		return compared ? sameType.get(place) : null;
	}

	/** The reads of {@code fields} of {@code owner} in a method, in order. */
	private static List<String> reads(MethodNode method, String owner, Collection<String> fields) {
		List<String> reads = new ArrayList<>();
		if (method.instructions == null) return reads;
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof FieldInsnNode read && read.getOpcode() == Opcodes.GETFIELD && read.owner.equals(owner) && fields.contains(read.name)) {
				reads.add(read.name);
			}
		}
		return reads;
	}

	/** A copy of {@code mixin} with its shadows renamed. */
	private static ClassNode renamed(ClassNode mixin, Map<String, String> renames) {
		ClassNode copy = new ClassNode();
		mixin.accept(copy);
		renameShadows(copy, renames);
		return copy;
	}

	/** Renames the mixin's shadow fields, and its code's uses of them. */
	private static void renameShadows(ClassNode mixin, Map<String, String> renames) {
		if (renames.isEmpty()) return;
		for (FieldNode field : mixin.fields) {
			if (Annotations.find(field, MixinNames.SHADOW) != null) field.name = renames.getOrDefault(field.name, field.name);
		}
		for (MethodNode method : mixin.methods) {
			if (method.instructions == null) continue;
			for (AbstractInsnNode insn : method.instructions) {
				if (insn instanceof FieldInsnNode use && use.owner.equals(mixin.name)) use.name = renames.getOrDefault(use.name, use.name);
			}
		}
	}

	/** Whether pre-flight finds nothing but notes in {@code mixin} against {@code target}. */
	private boolean fits(ClassNode mixin, ClassNode target, Context ctx) {
		List<PreflightFinding> found = new ArrayList<>();
		checker.checkMembers(mixin, target, new Context(ctx.configName(), ctx.mixinDotted(), ctx.config(), ctx.refmap(), false, false, false, Set.of(), found, null, loader));
		return found.stream().allMatch(f -> f.severity() == Severity.INFO);
	}
}
