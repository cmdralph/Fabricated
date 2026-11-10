package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.mixin.InjectionPoints.Range;
import io.github.cmdralph.fabricated.core.mixin.InjectionPoints.Verdict;
import io.github.cmdralph.fabricated.core.mixin.MixinPreflight.Context;
import io.github.cmdralph.fabricated.core.mixin.MixinPreflight.ConstructorInjects;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Injector repairs for constructors: injection points the running Mixin takes in a constructor, injectors at the end
 * of a constructor NeoForge kept for vanilla's signature, and injectors into a constructor the platform keeps only to
 * call the one it moved the code into.
 */
final class ConstructorRepairs extends PreflightPart {
	ConstructorRepairs(PreflightClasses classes) {
		super(classes);
	}

	/**
	 * The injectors Mixin refuses with a handler that isn't static at a constructor's instruction up to its call to the
	 * delegate constructor, where the instance doesn't exist yet ({@code Injector.checkTargetForNode}, the same in
	 * SpongePowered's Mixin and Fabric's), with the points pre-flight finds for each: a call, and for a redirect a field
	 * access too (a redirected construction takes another path).
	 */
	private static final Map<String, Set<String>> STATIC_BEFORE_DELEGATE = Map.of(
			"Lorg/spongepowered/asm/mixin/injection/ModifyArg;", Set.of("INVOKE"),
			"Lorg/spongepowered/asm/mixin/injection/ModifyArgs;", Set.of("INVOKE"),
			MixinNames.REDIRECT, Set.of("INVOKE", "FIELD"));

	/**
	 * The point of an injector whose handler isn't static that picks, in a constructor of {@code owner}, an instruction up
	 * to its call to the delegate constructor ({@code super(...)} or {@code this(...)}), which Mixin refuses ("handler before
	 * this() invocation must be static", {@link #STATIC_BEFORE_DELEGATE}) wherever it is required (CTOR-3: Fabric API's
	 * shader program hook modifies vanilla's {@code ResourceLocation} construction in {@code ShaderInstance}'s constructor,
	 * which Forge 47's patch moved into the arguments of a {@code this(...)} call). Null when there is none.
	 */
	static String beforeDelegateRejected(MethodNode handler, AnnotationNode injector, ClassNode owner, List<MethodNode> matched, Context ctx) {
		Set<String> points = STATIC_BEFORE_DELEGATE.get(injector.desc);
		if (points == null || (handler.access & Opcodes.ACC_STATIC) != 0 || matched.stream().noneMatch(m -> m.name.equals("<init>"))) return null;
		List<AnnotationNode> slices = Annotations.list(Annotations.value(injector, "slice"));
		for (AnnotationNode at : Annotations.list(Annotations.value(injector, "at"))) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			if (!points.contains(point)) continue;
			String target = Annotations.value(at, "target") instanceof String t ? ctx.resolve(t) : null;
			int ordinal = Annotations.value(at, "ordinal") instanceof Integer i ? i : -1;
			int opcode = Annotations.value(at, "opcode") instanceof Integer i ? i : -1;
			AnnotationNode slice = InjectionPoints.sliceFor(at, slices);
			for (MethodNode m : matched) {
				if (!m.name.equals("<init>")) continue;
				int delegate = delegateCall(m, owner);
				List<Integer> picked = delegate < 0 ? null
						: InjectionPoints.indexes(point, target, ordinal, opcode, slice == null ? Range.ALL : InjectionPoints.sliceRange(slice, m, ctx), m);
				if (picked != null && picked.stream().anyMatch(i -> i <= delegate)) return point;
			}
		}
		return null;
	}

	/** Points an {@code @Inject} into a constructor may take anywhere on SpongePowered's Mixin: its returns (CTOR-2). */
	static final Set<String> RETURN_POINTS = Set.of("RETURN", "TAIL");

	/** Points whose instructions pre-flight finds, so it can tell whether they follow the delegate constructor call. */
	private static final Set<String> PLACED_POINTS = Set.of("INVOKE", "INVOKE_ASSIGN", "INVOKE_STRING", "FIELD", "NEW");

	/**
	 * Has the running Mixin take an {@code @Inject} into a constructor at a point other than a return, as Fabric's Mixin
	 * takes it (its points are {@code unsafe} by default), where every instruction the point picks in every constructor
	 * selected, shifted as the point is, follows the call to the delegate constructor ({@code super(...)} or
	 * {@code this(...)}). SpongePowered's 0.8.6 and 0.8.7 take it marked {@code unsafe} (CTOR-2: fabric-command-api-v2's
	 * hook into {@code Commands}' constructor, which stopped Forge 61's server). 0.8.5, which Forge 47 runs, has no
	 * {@code unsafe}: the point becomes Fabricated's {@link ConstructorPoint}, which finds what it stands for and which
	 * Mixin takes anywhere in a constructor (CORPUS-103: Dripstone Fluid Lib registers its particles' providers in
	 * {@code Minecraft}'s constructor, just after it makes the particle engine). A cancellable one stays as it is there:
	 * 0.8.5 would let it return before the constructor's end, which the later releases and Fabric's Mixin refuse. Returns
	 * a description of each point changed.
	 * <p>
	 * On 0.8.6 and 0.8.7, whose constructor checks are Fabric's but whose points aren't {@code unsafe} by default (CTOR-7),
	 * two points pre-flight doesn't place are marked too, as Fabric's Mixin takes them: {@code CTOR_HEAD}, which finds an
	 * instruction after that call (and which these releases refuse to parse without {@code unsafe}: FrozenLib's shelf hook
	 * into {@code PistonMovingBlockEntity}'s constructor, ImmediatelyFast's into {@code FontTexture}'s); and {@code HEAD}
	 * with a static handler, which Mixin takes before that call (Wover's into
	 * {@code MultiNoiseBiomeSourceParameterList}'s). An instance handler at {@code HEAD} stays: Fabric's Mixin refuses it
	 * too. {@link MixinRepairs} runs this again on an injector a later repair pointed at another constructor (FrozenLib's
	 * advancement hook, which the overload repair moves into the constructor Forge's {@code ServerAdvancementManager}
	 * moved its code into).
	 */
	List<String> constructorPointRepairs(MethodNode handler, AnnotationNode injector, List<ClassNode> targets, Context ctx) {
		if (constructorInjects == ConstructorInjects.AFTER_DELEGATE || !injector.desc.equals(MixinNames.INJECT)) return List.of();
		boolean returnsOnly = constructorInjects == ConstructorInjects.RETURNS_ONLY;
		if (returnsOnly && Boolean.TRUE.equals(Annotations.value(injector, "cancellable"))) return List.of();
		List<AnnotationNode> slices = Annotations.list(Annotations.value(injector, "slice"));
		List<String> changed = new ArrayList<>();
		for (AnnotationNode at : Annotations.list(Annotations.value(injector, "at"))) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			Integer shift = shift(at);
			if (Boolean.TRUE.equals(Annotations.value(at, "unsafe"))) continue;
			if (!returnsOnly && (point.equals("CTOR_HEAD") || point.equals("HEAD") && (handler.access & Opcodes.ACC_STATIC) != 0)) {
				String where = constructorsDelegating(injector, targets, ctx);
				if (where == null) continue;
				Annotations.put(at, "unsafe", true);
				changed.add("the constructor of " + where + " at @At(\"" + point + "\")" + (point.equals("HEAD") ? ", before its super() call, with a static handler" : "")
						+ ", which " + loader + "'s Mixin takes only at a point marked unsafe; it is so marked, as Fabric's Mixin takes it");
				continue;
			}
			if (!PLACED_POINTS.contains(point) || shift == null) continue;
			String target = Annotations.value(at, "target") instanceof String t ? ctx.resolve(t) : null;
			int ordinal = Annotations.value(at, "ordinal") instanceof Integer i ? i : -1;
			int opcode = Annotations.value(at, "opcode") instanceof Integer i ? i : -1;
			AnnotationNode slice = InjectionPoints.sliceFor(at, slices);
			boolean picked = false;
			boolean afterDelegate = true;
			String where = null;
			for (ClassNode t : targets) {
				for (MethodNode m : PreflightAsm.selectedMethods(injector, t, ctx)) {
					if (!m.name.equals("<init>")) continue;
					int delegate = delegateCall(m, t);
					Range range = slice == null ? Range.ALL : InjectionPoints.sliceRange(slice, m, ctx);
					List<Integer> found = InjectionPoints.indexes(point, target, ordinal, opcode, range, m);
					if (delegate < 0 || found == null) {
						afterDelegate = false;
						continue;
					}
					for (int i : found) {
						picked = true;
						// Where Mixin injects: the instruction the shift moves the point to, inside the method.
						afterDelegate &= i + shift > delegate && i + shift < m.instructions.size();
					}
					where = t.name.replace('/', '.');
				}
			}
			if (!picked || !afterDelegate) continue;
			String described = "the constructor of " + where + " at @At(\"" + point + "\"" + (target != null ? ", target=\"" + target + "\"" : "")
					+ ") after its super() call, which " + loader + "'s Mixin takes only at ";
			if (returnsOnly) {
				toConstructorPoint(at, point, shift);
				changed.add(described + "a return; Fabricated's injection point stands for it there, which " + loader
						+ "'s Mixin takes anywhere after that call, as Fabric's Mixin takes it");
			} else {
				Annotations.put(at, "unsafe", true);
				changed.add(described + "a point marked unsafe; it is so marked, as Fabric's Mixin takes it");
			}
		}
		return changed;
	}

	/**
	 * Points an {@code @Inject} into a constructor that the platform keeps only to call another one of its class
	 * ({@code this(...)} and nothing after it), at points that are now only in that one, at that one, where the platform
	 * hands constructors' arguments on ({@link ConstructorHandoffs}): its handler, taking the first constructor's
	 * parameters, gets the arguments that one was called with through a bridge taking the other's
	 * ({@link HandlerBridges#handedOffBridge}). Forge 47 keeps {@code ShaderInstance}'s constructor taking a shader's name
	 * as {@code this(provider, new ResourceLocation(name), format)}, where Iris hooks the shader's JSON parse with a handler
	 * taking the name (IRIS-1); the other constructor's arguments can't give it back. Where the running Mixin takes such a
	 * point in a constructor only so, the point is then marked as {@link #constructorPointRepairs} marks it. The handler
	 * takes the parameters and the callback info alone, and isn't static nor cancellable; the mixin is a class. Returns a
	 * description of the move, or nothing.
	 */
	List<String> handoffRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx, List<MethodNode> bridges) {
		if (!handsOffConstructorArguments || !injector.desc.equals(MixinNames.INJECT) || (handler.access & Opcodes.ACC_STATIC) != 0
				|| (mixin.access & Opcodes.ACC_INTERFACE) != 0 || Annotations.value(injector, "locals") != null
				|| Boolean.TRUE.equals(Annotations.value(injector, "cancellable")) || !Annotations.list(Annotations.value(injector, "slice")).isEmpty()
				|| !(Annotations.value(injector, "method") instanceof List<?> selectors) || selectors.size() != 1
				|| !(selectors.get(0) instanceof String raw)) return List.of();
		MemberRef ref = MemberRef.parse(ctx.resolve(raw));
		if (ref.fuzzy() || !"<init>".equals(ref.name())) return List.of();
		MethodNode constructor = target.methods.stream().filter(m -> ref.matchesMethod(m.name, m.desc)).findFirst().orElse(null);
		MethodNode delegate = constructor == null ? null : thisCall(constructor, target);
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		if (delegate == null || ats.isEmpty() || !takesExactly(handler, constructor)
				|| InjectionPoints.pointsIn(ats, List.of(), constructor, ctx) != Verdict.MISSING
				|| InjectionPoints.pointsIn(ats, List.of(), delegate, ctx) != Verdict.FOUND) return List.of();
		bridges.add(HandlerBridges.handedOffBridge(mixin.name, handler, injector, constructor, delegate, ConstructorHandoffs.field(mixin, constructor.desc),
				HandlerBridges.freeName(mixin, bridges, handler.name + "$fabricated")));
		@SuppressWarnings("unchecked")
		List<Object> values = (List<Object>) selectors;
		values.set(0, delegate.name + delegate.desc);
		String moved = constructor.name + constructor.desc + ", which " + loader + " keeps only to call " + delegate.name + delegate.desc
				+ "; the injector is pointed there, and its handler is given the arguments the first was called with (a construction through"
				+ " the other alone, which Fabric doesn't have, doesn't call it)";
		for (String marked : constructorPointRepairs(handler, injector, List.of(target), ctx)) moved += "; it injects into " + marked;
		return List.of(moved);
	}

	/**
	 * The classes whose constructors the injector selects, where it selects some and each has a call to its delegate
	 * constructor ({@link #delegateCall}); null otherwise.
	 */
	private static String constructorsDelegating(AnnotationNode injector, List<ClassNode> targets, Context ctx) {
		Set<String> where = new java.util.TreeSet<>();
		for (ClassNode t : targets) {
			for (MethodNode m : PreflightAsm.selectedMethods(injector, t, ctx)) {
				if (!m.name.equals("<init>")) continue;
				if (delegateCall(m, t) < 0) return null;
				where.add(t.name.replace('/', '.'));
			}
		}
		return where.isEmpty() ? null : String.join(", ", where);
	}

	/** How many instructions Mixin moves a point by ({@code shift}, with {@code by}); null for a shift it doesn't name. */
	private static Integer shift(AnnotationNode at) {
		Object shift = Annotations.value(at, "shift");
		String name = shift instanceof String[] e && e.length == 2 ? e[1] : shift == null ? "NONE" : null;
		if (name == null) return null;
		return switch (name) {
			case "NONE" -> 0;
			case "BEFORE" -> -1;
			case "AFTER" -> 1;
			case "BY" -> Annotations.value(at, "by") instanceof Integer by ? by : 0;
			default -> null;
		};
	}

	/**
	 * Makes {@code at}, a {@code point} that Mixin moves by {@code shift}, Fabricated's {@link ConstructorPoint} for it:
	 * the point and its shift become its arguments ({@link ConstructorPoint#POINT}, {@link ConstructorPoint#SHIFT}),
	 * which finds the same instructions, as its target, ordinal, opcode, slice and own arguments stay.
	 */
	private static void toConstructorPoint(AnnotationNode at, String point, int shift) {
		List<Object> values = new ArrayList<>();
		List<String> args = new ArrayList<>();
		for (int i = 0; at.values != null && i < at.values.size(); i += 2) {
			String key = (String) at.values.get(i);
			Object value = at.values.get(i + 1);
			if (key.equals("shift") || key.equals("by")) continue;
			if (key.equals("args")) {
				for (Object arg : (List<?>) value) args.add((String) arg);
				continue;
			}
			values.add(key);
			values.add(key.equals("value") ? ConstructorPoint.AT : value);
		}
		args.add(ConstructorPoint.POINT + "=" + point);
		args.add(ConstructorPoint.SHIFT + "=" + shift);
		values.add("args");
		values.add(args);
		at.values = values;
	}

	/**
	 * The index of a constructor's call to its delegate constructor ({@code super(...)} or {@code this(...)}): the first
	 * {@code INVOKESPECIAL <init>} of its class or superclass that doesn't initialise an instance the constructor makes
	 * itself ({@code new}); -1 when there is none.
	 */
	static int delegateCall(MethodNode constructor, ClassNode owner) {
		Map<String, Integer> made = new HashMap<>();
		for (int i = 0; i < constructor.instructions.size(); i++) {
			AbstractInsnNode insn = constructor.instructions.get(i);
			if (insn instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW) {
				made.merge(type.desc, 1, Integer::sum);
			} else if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL && call.name.equals("<init>")) {
				Integer pending = made.get(call.owner);
				if (pending != null && pending > 0) {
					made.put(call.owner, pending - 1);
				} else if (call.owner.equals(owner.name) || call.owner.equals(owner.superName)) {
					return i;
				}
			}
		}
		return -1;
	}

	/**
	 * The constructor {@code constructor} hands everything to, when it is vanilla's signature as NeoForge keeps it: its
	 * body is {@code this(...)} (vanilla's arguments plus NeoForge's defaults) and nothing after it, and it is marked
	 * deprecated, or passes its own parameters followed by constants for parameters of NeoForge's types (as
	 * {@code BakedQuad}'s on 26.x, a record NeoForge added components to), or for parameters of a constructor vanilla's
	 * class doesn't have ({@code vanilla}, when known: 1.21.1's {@code BakedQuad}, whose vanilla constructor NeoForge
	 * keeps as {@code this(..., true)} for its ambient occlusion flag, CTOR-5). The game's own code calls the other
	 * constructor, so an injection at the end of this one would never run, while the end of the other one is the same
	 * moment for both. Null for any other constructor: vanilla's own delegating constructors (an injection there is
	 * meant for that one only) are never deprecated, pass no NeoForge types and call a constructor vanilla has.
	 */
	static MethodNode keptConstructorDelegate(MethodNode constructor, ClassNode target, ClassNode vanilla) {
		if (!constructor.name.equals("<init>")) return null;
		MethodNode delegate = thisCall(constructor, target);
		if (delegate == null) return null;
		if (PreflightAsm.deprecated(constructor)) return delegate;
		return platformAdded(constructor, delegate, vanilla) && forwardsItsParameters(constructor, delegate) ? delegate : null;
	}

	/**
	 * Whether the platform added {@code delegate}, a method or constructor of the class taking {@code method}'s
	 * parameters and more: one of those it adds is of the platform's types, or vanilla's class ({@code vanilla}, when
	 * known) declares {@code method} and not {@code delegate}.
	 */
	static boolean platformAdded(MethodNode method, MethodNode delegate, ClassNode vanilla) {
		Type[] all = Type.getArgumentTypes(delegate.desc);
		for (int i = Type.getArgumentTypes(method.desc).length; i < all.length; i++) {
			if (all[i].getSort() == Type.OBJECT && MixinNames.isPlatformCode(all[i].getInternalName())) return true;
		}
		return vanilla != null && PreflightAsm.declared(vanilla, method.name, method.desc) != null
				&& PreflightAsm.declared(vanilla, delegate.name, delegate.desc) == null;
	}

	/** The constructor of {@code target} that {@code constructor}'s body, {@code this(...)} and nothing after it, calls; or null. */
	private static MethodNode thisCall(MethodNode constructor, ClassNode target) {
		MethodNode delegate = null;
		for (AbstractInsnNode insn : constructor.instructions) {
			if (insn.getOpcode() < 0) continue; // labels, line numbers, frames
			if (delegate != null) {
				if (insn.getOpcode() != Opcodes.RETURN) return null; // does more after this(...)
			} else if (insn instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW && type.desc.equals(target.name)) {
				return null; // makes another instance: its constructor call is not this(...)
			} else if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL && call.name.equals("<init>")
					&& call.owner.equals(target.name)) {
				delegate = PreflightAsm.declared(target, call.name, call.desc);
				if (delegate == null) return null;
			}
		}
		return delegate;
	}

	/**
	 * Whether {@code method}, a constructor or a method, only calls {@code delegate} of its class with its parameters in
	 * order, then defaults, and returns what it returns: the other one gets exactly what it got, plus defaults. A default
	 * is a constant, a static field or what a static method returns without arguments: NeoForge keeps
	 * {@code ClientLanguage}'s vanilla constructor as {@code this(storage, rtl, Map.of())} (every version), so owo-lib's
	 * hook at its end never ran and left owo's text map unset (CTOR-6).
	 */
	static boolean forwardsItsParameters(MethodNode method, MethodNode delegate) {
		Type[] own = Type.getArgumentTypes(method.desc);
		Type[] all = Type.getArgumentTypes(delegate.desc);
		boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
		if (all.length <= own.length || isStatic != ((delegate.access & Opcodes.ACC_STATIC) != 0)
				|| !Type.getReturnType(method.desc).equals(Type.getReturnType(delegate.desc))) return false;
		for (int i = 0; i < own.length; i++) {
			if (!own[i].equals(all[i])) return false;
		}
		List<AbstractInsnNode> code = new ArrayList<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (insn.getOpcode() >= 0) code.add(insn); // not labels, line numbers or frames
		}
		int first = isStatic ? 0 : 1;
		if (code.size() != first + all.length + 2) return false;
		if (!isStatic && (!(code.get(0) instanceof VarInsnNode self) || self.getOpcode() != Opcodes.ALOAD || self.var != 0)) return false;
		int slot = first;
		for (int i = 0; i < own.length; i++) {
			if (!(code.get(first + i) instanceof VarInsnNode load) || load.getOpcode() != own[i].getOpcode(Opcodes.ILOAD) || load.var != slot) return false;
			slot += own[i].getSize();
		}
		for (int i = first + own.length; i < code.size() - 2; i++) {
			int op = code.get(i).getOpcode();
			boolean constant = op >= Opcodes.ACONST_NULL && op <= Opcodes.DCONST_1 || op == Opcodes.BIPUSH || op == Opcodes.SIPUSH
					|| op == Opcodes.LDC || op == Opcodes.GETSTATIC
					|| op == Opcodes.INVOKESTATIC && Type.getArgumentTypes(((MethodInsnNode) code.get(i)).desc).length == 0;
			if (!constant) return false;
		}
		return code.get(code.size() - 2) instanceof MethodInsnNode call && (call.getOpcode() == Opcodes.INVOKESTATIC) == isStatic
				&& call.name.equals(delegate.name) && call.desc.equals(delegate.desc)
				&& code.get(code.size() - 1).getOpcode() == Type.getReturnType(method.desc).getOpcode(Opcodes.IRETURN);
	}

	/** Whether {@code handler} takes {@code method}'s parameters and a callback info, and nothing else (no captured locals or sugar). */
	static boolean takesExactly(MethodNode handler, MethodNode method) {
		Type[] handlerTypes = Type.getArgumentTypes(handler.desc);
		Type[] params = Type.getArgumentTypes(method.desc);
		if (handlerTypes.length != params.length + 1 || !PreflightAsm.isCallbackInfo(handlerTypes[params.length])) return false;
		for (int i = 0; i < params.length; i++) {
			if (!handlerTypes[i].equals(params[i]) || !Annotations.ofParameter(handler, i).isEmpty()) return false;
		}
		return true;
	}

	/** Whether every injection point is the method's end ({@code RETURN} or {@code TAIL}). */
	static boolean atEndOnly(List<AnnotationNode> ats) {
		for (AnnotationNode at : ats) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			if (!point.equals("RETURN") && !point.equals("TAIL")) return false;
		}
		return true;
	}
}
