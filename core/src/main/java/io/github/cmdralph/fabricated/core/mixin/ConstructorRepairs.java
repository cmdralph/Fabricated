package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.mixin.InjectionPoints.Range;
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
 * Injector repairs for constructors: injection points the running Mixin takes in a constructor, and injectors at the
 * end of a constructor NeoForge kept for vanilla's signature.
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
	 * Marks {@code unsafe} the points of an {@code @Inject} into a constructor that SpongePowered's Mixin takes only so
	 * marked (CTOR-2): any but a return, where Fabric's Mixin takes any point after the call to the delegate constructor
	 * ({@code super(...)} or {@code this(...)}). A point is marked when every instruction it picks in every constructor
	 * selected follows that call, which gives Fabric's behaviour: fabric-command-api-v2's hook into {@code Commands}'
	 * constructor, which stopped Forge 61's server. Returns a description of each point marked.
	 */
	List<String> constructorPointRepairs(MethodNode handler, AnnotationNode injector, List<ClassNode> targets, Context ctx) {
		if (constructorInjects != ConstructorInjects.UNSAFE_POINTS || !injector.desc.equals(MixinNames.INJECT)) return List.of();
		List<AnnotationNode> slices = Annotations.list(Annotations.value(injector, "slice"));
		List<String> marked = new ArrayList<>();
		for (AnnotationNode at : Annotations.list(Annotations.value(injector, "at"))) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			if (!PLACED_POINTS.contains(point) || Boolean.TRUE.equals(Annotations.value(at, "unsafe"))) continue;
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
					List<Integer> at2 = InjectionPoints.indexes(point, target, ordinal, opcode, range, m);
					if (delegate < 0 || at2 == null) {
						afterDelegate = false;
						continue;
					}
					for (int i : at2) {
						picked = true;
						afterDelegate &= i > delegate;
					}
					where = t.name.replace('/', '.');
				}
			}
			if (!picked || !afterDelegate) continue;
			Annotations.put(at, "unsafe", true);
			marked.add("the constructor of " + where + " at @At(\"" + point + "\"" + (target != null ? ", target=\"" + target + "\"" : "")
					+ ") after its super() call, which " + loader + "'s Mixin takes only at a point marked unsafe; it is so marked, as Fabric's Mixin takes it");
		}
		return marked;
	}

	/**
	 * Turns an {@code @Inject} at a call in a constructor into a MixinExtras {@code @WrapOperation} of that call, where
	 * the running Mixin takes an {@code @Inject} into a constructor only at a return (CTOR-4: SpongePowered's 0.8.5, which
	 * Forge 47 runs; Fabricated's in-game mod carries MixinExtras there). The wrapper calls the handler with the
	 * constructor's parameters and a callback info of its own just before the call, or just after it ({@code shift =
	 * AFTER}), as Fabric's Mixin runs the handler: fabric-command-api-v2's hook into {@code Commands}' constructor, which
	 * registers Fabric mods' commands, and model loading's into {@code ModelBakery}'s. Only where every call the point picks
	 * in every constructor selected follows the call to the delegate constructor and is the same plain call (not a
	 * {@code super} call or a construction), and the handler takes the constructor's parameters (or none) and the callback
	 * info, and nothing else: not cancellable, no locals, no sugar, no shift but {@code AFTER}. Returns a description of
	 * the injector wrapped.
	 */
	List<String> constructorCallWrappers(ClassNode mixin, MethodNode handler, AnnotationNode injector, List<ClassNode> targets, Context ctx,
			List<MethodNode> bridges) {
		if (constructorInjects != ConstructorInjects.RETURNS_ONLY || !injector.desc.equals(MixinNames.INJECT)
				|| Boolean.TRUE.equals(Annotations.value(injector, "cancellable")) || Annotations.value(injector, "locals") != null
				|| Annotations.value(injector, "target") != null || Annotations.value(injector, "constraints") != null
				|| !Type.getReturnType(handler.desc).equals(Type.VOID_TYPE)) return List.of();
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		if (ats.size() != 1) return List.of();
		AnnotationNode at = ats.get(0);
		String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
		Object shift = Annotations.value(at, "shift");
		String shiftName = shift instanceof String[] e && e.length == 2 ? e[1] : shift == null ? "NONE" : null;
		// BEFORE puts the point an instruction earlier, among the call's arguments: a wrapper can't run the handler there.
		if (!point.equals("INVOKE") || Annotations.value(at, "by") != null || !"NONE".equals(shiftName) && !"AFTER".equals(shiftName)) return List.of();
		Type[] own = Type.getArgumentTypes(handler.desc);
		if (own.length == 0 || !own[own.length - 1].getDescriptor().equals(CALLBACK_INFO)) return List.of();
		for (int i = 0; i < own.length; i++) {
			if (!Annotations.ofParameter(handler, i).isEmpty()) return List.of();
		}
		List<Type> constructorParams = List.of(own).subList(0, own.length - 1);
		String target = Annotations.value(at, "target") instanceof String t ? ctx.resolve(t) : null;
		int ordinal = Annotations.value(at, "ordinal") instanceof Integer i ? i : -1;
		int opcode = Annotations.value(at, "opcode") instanceof Integer i ? i : -1;
		AnnotationNode slice = InjectionPoints.sliceFor(at, Annotations.list(Annotations.value(injector, "slice")));
		MethodInsnNode call = null;
		String where = null;
		for (ClassNode t : targets) {
			for (MethodNode m : PreflightAsm.selectedMethods(injector, t, ctx)) {
				if (!m.name.equals("<init>")) return List.of();
				if (!constructorParams.isEmpty() && !constructorParams.equals(List.of(Type.getArgumentTypes(m.desc)))) return List.of();
				int delegate = delegateCall(m, t);
				List<Integer> picked = delegate < 0 ? null
						: InjectionPoints.indexes(point, target, ordinal, opcode, slice == null ? Range.ALL : InjectionPoints.sliceRange(slice, m, ctx), m);
				if (picked == null) return List.of();
				for (int index : picked) {
					if (index <= delegate || !(m.instructions.get(index) instanceof MethodInsnNode insn) || insn.getOpcode() == Opcodes.INVOKESPECIAL) return List.of();
					if (call != null && (call.getOpcode() == Opcodes.INVOKESTATIC) != (insn.getOpcode() == Opcodes.INVOKESTATIC)
							|| call != null && !(call.owner.equals(insn.owner) && call.desc.equals(insn.desc))) return List.of();
					call = insn;
				}
				where = t.name.replace('/', '.');
			}
		}
		if (call == null) return List.of();
		boolean staticCall = call.getOpcode() == Opcodes.INVOKESTATIC;
		MethodNode bridge = HandlerBridges.constructorCallWrapper(mixin, handler, Type.getObjectType(call.owner), call.desc, staticCall,
				constructorParams, "AFTER".equals(shiftName), bridges);
		AnnotationNode wrapper = new AnnotationNode(MixinNames.WRAP_OPERATION);
		for (String key : List.of("method", "slice", "remap", "require", "expect", "allow")) {
			Object value = Annotations.value(injector, key);
			if (value != null) Annotations.put(wrapper, key, value);
		}
		AnnotationNode wrappedAt = new AnnotationNode(at.desc);
		for (int i = 0; at.values != null && i < at.values.size(); i += 2) {
			if (at.values.get(i).equals("shift")) continue;
			Annotations.put(wrappedAt, (String) at.values.get(i), at.values.get(i + 1));
		}
		Annotations.put(wrapper, "at", new ArrayList<>(List.of(wrappedAt)));
		HandlerBridges.replaceInjector(handler, injector, wrapper, bridge);
		bridges.add(bridge);
		return List.of("the constructor of " + where + " at @At(\"INVOKE\", target=\"" + target + "\"), which " + loader
				+ "'s Mixin takes only at a return; it wraps that call (MixinExtras' @WrapOperation) and runs the handler "
				+ ("AFTER".equals(shiftName) ? "after" : "before") + " it, as Fabric's Mixin does");
	}

	private static final String CALLBACK_INFO = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";

	/**
	 * The index of a constructor's call to its delegate constructor ({@code super(...)} or {@code this(...)}): the first
	 * {@code INVOKESPECIAL <init>} of its class or superclass that doesn't initialise an instance the constructor makes
	 * itself ({@code new}); -1 when there is none.
	 */
	private static int delegateCall(MethodNode constructor, ClassNode owner) {
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
	 * deprecated or passes its own parameters followed by constants for parameters of NeoForge's types (as
	 * {@code BakedQuad}'s, a record NeoForge added components to). The game's own code calls the other constructor, so
	 * an injection at the end of this one would never run, while the end of the other one is the same moment for both.
	 * Null for any other constructor: vanilla's own delegating constructors (an injection there is meant for that one
	 * only) are never deprecated and pass no NeoForge types.
	 */
	static MethodNode keptConstructorDelegate(MethodNode constructor, ClassNode target) {
		if (!constructor.name.equals("<init>")) return null;
		MethodNode delegate = thisCall(constructor, target);
		if (delegate == null) return null;
		boolean deprecated = (constructor.access & Opcodes.ACC_DEPRECATED) != 0 || Annotations.find(constructor, "Ljava/lang/Deprecated;") != null;
		if (deprecated) return delegate;
		Type[] all = Type.getArgumentTypes(delegate.desc);
		int own = Type.getArgumentTypes(constructor.desc).length;
		boolean neoForgeAdded = false;
		for (int i = own; i < all.length; i++) neoForgeAdded |= all[i].getSort() == Type.OBJECT && all[i].getInternalName().startsWith("net/neoforged/");
		return neoForgeAdded && forwardsItsParameters(constructor, delegate) ? delegate : null;
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
	 * Whether {@code constructor} is {@code this(its parameters in order, then constants)}: the other constructor gets
	 * exactly what it got, plus defaults.
	 */
	static boolean forwardsItsParameters(MethodNode constructor, MethodNode delegate) {
		Type[] own = Type.getArgumentTypes(constructor.desc);
		Type[] all = Type.getArgumentTypes(delegate.desc);
		if (all.length <= own.length) return false;
		for (int i = 0; i < own.length; i++) {
			if (!own[i].equals(all[i])) return false;
		}
		List<AbstractInsnNode> code = new ArrayList<>();
		for (AbstractInsnNode insn : constructor.instructions) {
			if (insn.getOpcode() >= 0) code.add(insn); // not labels, line numbers or frames
		}
		if (code.size() != own.length + (all.length - own.length) + 3) return false;
		if (!(code.get(0) instanceof VarInsnNode self) || self.getOpcode() != Opcodes.ALOAD || self.var != 0) return false;
		int slot = 1;
		for (int i = 0; i < own.length; i++) {
			if (!(code.get(1 + i) instanceof VarInsnNode load) || load.getOpcode() != own[i].getOpcode(Opcodes.ILOAD) || load.var != slot) return false;
			slot += own[i].getSize();
		}
		for (int i = 1 + own.length; i < code.size() - 2; i++) {
			int op = code.get(i).getOpcode();
			boolean constant = op >= Opcodes.ACONST_NULL && op <= Opcodes.DCONST_1 || op == Opcodes.BIPUSH || op == Opcodes.SIPUSH
					|| op == Opcodes.LDC || op == Opcodes.GETSTATIC;
			if (!constant) return false;
		}
		return code.get(code.size() - 2) instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL && call.desc.equals(delegate.desc)
				&& code.get(code.size() - 1).getOpcode() == Opcodes.RETURN;
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
