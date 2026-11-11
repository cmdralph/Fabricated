package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.mixin.InjectionPoints.Verdict;
import io.github.cmdralph.fabricated.core.mixin.MixinPreflight.Context;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Injector repairs for a private method of vanilla's that the platform's class no longer declares, where the one method
 * calling it calls a private method of the platform's in that call's place, and nowhere else (STRUCT-1). Vanilla's
 * {@code StructureTemplate.placeInWorld} places a template's entities with {@code placeEntities(level, pos, mirror,
 * rotation, pivot, box, finalize)}, which Forge 47, Forge 52 and NeoForge replaced with {@code addEntitiesToWorld(level,
 * pos, settings)}, and Forge 61 to 66 with a {@code placeEntities} taking the settings in the pivot's place: they run
 * the structure processors on each entity and read the rest from the settings. YUNG's API and Friends and Foes port
 * that to Fabric with hooks there: an {@code @Inject} at the call moves to the platform's call, and one at the method's
 * start or end to the start or end of the platform's method, which runs once where vanilla's did. Its handler takes
 * vanilla's parameters from a bridge working each out from the platform's method's as vanilla's call did: the value
 * the platform's call passes too, or a field getter called on one ({@link #derive}). The other injectors there (Porting
 * Lib's, which port the platform's own processing) have nothing of vanilla's to apply to and stay out.
 */
final class ReplacedMethods extends PreflightPart {
	private final Map<String, Optional<Replaced>> replacedCache = new HashMap<>();

	ReplacedMethods(PreflightClasses classes) {
		super(classes);
	}

	/**
	 * Vanilla's private method, the method making its one call ({@code vanillaCaller}, and the platform's {@code caller}),
	 * and the platform's private method ({@code method}) called once, by {@code call}, in that call's place.
	 */
	record Replaced(MethodNode vanilla, MethodNode vanillaCaller, MethodInsnNode vanillaCall, MethodNode caller, MethodInsnNode call,
			MethodNode method, PatchAlignment alignment) {
		/** The platform's method as messages name it. */
		String named() {
			return method.name + method.desc;
		}
	}

	/**
	 * The platform's method that replaced vanilla's {@code name desc} of {@code target}, or null where there is none. Without
	 * a descriptor (a selector naming the method alone), vanilla's one method of that name.
	 */
	Replaced replaced(ClassNode target, String name, String desc) {
		if (desc == null) {
			ClassNode vanilla = vanillaClass(target.name);
			List<MethodNode> named = vanilla == null ? List.of() : vanilla.methods.stream().filter(m -> m.name.equals(name)).toList();
			if (named.size() != 1) return null;
			desc = named.get(0).desc;
		}
		String described = desc;
		return replacedCache.computeIfAbsent(target.name + "." + name + desc, k -> Optional.ofNullable(find(target, name, described))).orElse(null);
	}

	private Replaced find(ClassNode target, String name, String desc) {
		ClassNode vanilla = vanillaClass(target.name);
		if (vanilla == null || name.startsWith("<") || PreflightAsm.declared(target, name, desc) != null) return null;
		MethodNode before = PreflightAsm.declared(vanilla, name, desc);
		if (before == null || (before.access & Opcodes.ACC_PRIVATE) == 0) return null;
		Map<String, List<Integer>> made = OverloadRepairs.callsByMethod(vanilla, before, before, this::vanillaClass);
		if (made == null || made.size() != 1 || made.values().iterator().next().size() != 1) return null;
		String key = made.keySet().iterator().next();
		if (!key.startsWith(vanilla.name + ".")) return null;
		MethodNode vanillaCaller = null;
		for (MethodNode m : vanilla.methods) {
			if ((vanilla.name + "." + m.name + m.desc).equals(key)) vanillaCaller = m;
		}
		MethodNode caller = vanillaCaller == null ? null : PreflightAsm.declared(target, vanillaCaller.name, vanillaCaller.desc);
		if (caller == null || caller.instructions == null) return null;
		MethodInsnNode vanillaCall = null;
		for (AbstractInsnNode insn : vanillaCaller.instructions) {
			if (insn instanceof MethodInsnNode c && c.owner.equals(vanilla.name) && c.name.equals(name) && c.desc.equals(desc)) vanillaCall = c;
		}
		PatchAlignment alignment = vanillaCall == null ? null : PatchAlignment.of(vanillaCaller, caller);
		if (alignment == null) return null;
		int at = vanillaCaller.instructions.indexOf(vanillaCall);
		int[] range = alignment.replacementOf(at);
		int[] region = range == null ? alignment.uncertainRegion(at) : null;
		if (region != null) range = new int[] {region[2], region[3]};
		if (range == null) return null;
		MethodInsnNode call = null;
		MethodNode method = null;
		for (int i = range[0]; i < range[1]; i++) {
			if (!(caller.instructions.get(i) instanceof MethodInsnNode c) || !c.owner.equals(target.name) || PreflightAsm.declared(vanilla, c.name, c.desc) != null) continue;
			MethodNode m = PreflightAsm.declared(target, c.name, c.desc);
			if (m == null) continue;
			if (call != null) return null;
			call = c;
			method = m;
		}
		if (method == null || (method.access & Opcodes.ACC_PRIVATE) == 0 || (method.access & Opcodes.ACC_STATIC) != (before.access & Opcodes.ACC_STATIC)
				|| !Type.getReturnType(method.desc).equals(Type.getReturnType(before.desc))
				|| !made.equals(OverloadRepairs.callsByMethod(target, method, method, this::gameTarget))) return null;
		return new Replaced(before, vanillaCaller, vanillaCall, caller, call, method, alignment);
	}

	/**
	 * Points an {@code @Inject}'s {@code INVOKE} points at vanilla's call of a method the platform replaced
	 * ({@link #replaced}) at the platform's call in its place: its handler sees none of the call's arguments. Returns a
	 * description of each move.
	 */
	List<String> callRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx) {
		List<String> moved = new ArrayList<>();
		for (AnnotationNode at : movableCalls(mixin, handler, injector, target, ctx)) {
			if (!movesWithItsState(mixin, handler, target, ctx)) break;
			Replaced replaced = calledAt(at, target, ctx);
			String raw = (String) Annotations.value(at, "target");
			Annotations.put(at, "target", "L" + target.name + ";" + replaced.named());
			moved.add("the call " + raw + ", which " + loader + "'s " + target.name.replace('/', '.') + " makes no more: it replaced that private"
					+ " method with " + replaced.named() + ", which it calls in its place; the injection point is pointed there (STRUCT-1)");
		}
		return moved;
	}

	/**
	 * Points an {@code @Inject} at the start or the end of a method the platform replaced ({@link #replaced}) at the
	 * platform's method, which runs once where vanilla's did. A handler taking vanilla's parameters gets them from a bridge
	 * taking the platform's ({@link #derive}). Returns a description of the move, or nothing.
	 */
	List<String> methodRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx, List<MethodNode> bridges) {
		Replaced replaced = replacedTarget(mixin, handler, injector, target, ctx);
		List<Derived> derived = replaced == null ? null : bridged(handler, replaced);
		if (derived == null || !movesWithItsState(mixin, handler, target, ctx)) return List.of();
		if (Type.getArgumentTypes(handler.desc).length > 1) {
			bridges.add(bridge(mixin.name, handler, injector, replaced.method(), derived, HandlerBridges.freeName(mixin, bridges, handler.name + "$fabricated")));
		}
		@SuppressWarnings("unchecked")
		List<Object> selectors = (List<Object>) Annotations.value(injector, "method");
		selectors.set(0, replaced.named());
		return List.of(replaced.vanilla().name + replaced.vanilla().desc + ", a private method " + loader + " replaced with " + replaced.named()
				+ ", which " + replaced.caller().name + " calls in its place; the injector is pointed there"
				+ (derived.isEmpty() ? "" : ", its handler taking vanilla's parameters as that method's give them") + " (STRUCT-1)");
	}

	/** An {@code @Inject} whose handler sees nothing of the code around its point: no captured locals or sugar, no slice. */
	private static boolean movable(ClassNode mixin, MethodNode handler, AnnotationNode injector) {
		if (!injector.desc.equals(MixinNames.INJECT) || (mixin.access & Opcodes.ACC_INTERFACE) != 0 || Annotations.value(injector, "slice") != null
				|| Annotations.value(injector, "locals") != null) return false;
		Type[] args = Type.getArgumentTypes(handler.desc);
		return args.length > 0 && PreflightAsm.isCallbackInfo(args[args.length - 1]);
	}

	/** The points {@link #callRepairs} would move (before checking the injectors keeping state with this one). */
	private List<AnnotationNode> movableCalls(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx) {
		if (!movable(mixin, handler, injector)) return List.of();
		List<MethodNode> methods = PreflightAsm.selectedMethods(injector, target, ctx);
		if (methods.size() != 1) return List.of();
		List<AnnotationNode> movable = new ArrayList<>();
		for (AnnotationNode at : Annotations.list(Annotations.value(injector, "at"))) {
			Replaced replaced = calledAt(at, target, ctx);
			MethodNode method = methods.get(0);
			if (replaced != null && replaced.caller().name.equals(method.name) && replaced.caller().desc.equals(method.desc)) movable.add(at);
		}
		return movable;
	}

	/** The replaced method whose call an {@code INVOKE} point names (its first, without a shift by a count), or null. */
	private Replaced calledAt(AnnotationNode at, ClassNode target, Context ctx) {
		String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
		if (!point.equals("INVOKE") || !(Annotations.value(at, "target") instanceof String raw) || Annotations.value(at, "by") != null
				|| Annotations.value(at, "ordinal") instanceof Integer ordinal && ordinal != 0) return null;
		MemberRef ref = MemberRef.parse(ctx.resolve(raw));
		if (ref.fuzzy() || !target.name.equals(ref.owner()) || ref.name() == null || ref.desc() == null) return null;
		return replaced(target, ref.name(), ref.desc());
	}

	/**
	 * The replaced method an {@code @Inject} at its start or end (no ordinal or shift) targets, or null; its handler takes
	 * the callback info alone or vanilla's parameters before it.
	 */
	private Replaced replacedTarget(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx) {
		if (!movable(mixin, handler, injector) || !(Annotations.value(injector, "method") instanceof List<?> selectors) || selectors.size() != 1
				|| !(selectors.get(0) instanceof String raw)) return null;
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		for (AnnotationNode at : ats) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			if (!List.of("HEAD", "RETURN", "TAIL").contains(point) || Annotations.value(at, "ordinal") != null || Annotations.value(at, "shift") != null) return null;
		}
		MemberRef ref = MemberRef.parse(ctx.resolve(raw));
		if (ats.isEmpty() || ref.fuzzy() || ref.name() == null) return null;
		Replaced replaced = replaced(target, ref.name(), ref.desc());
		boolean takesParameters = Type.getArgumentTypes(handler.desc).length > 1;
		return replaced == null || takesParameters && !ConstructorRepairs.takesExactly(handler, replaced.vanilla()) ? null : replaced;
	}

	/** How the handler gets vanilla's parameters: none where it takes the callback info alone; null where they can't be worked out. */
	private List<Derived> bridged(MethodNode handler, Replaced replaced) {
		return Type.getArgumentTypes(handler.desc).length == 1 ? List.of() : derive(replaced);
	}

	/**
	 * Whether a handler can move: it keeps no state with other injectors of its mixin, or each of those
	 * ({@link MixinPreflight#stateGroups}, the whole mixin where it has no group) moves here too, or applies where it is.
	 * One moved alone would read what the others never set (HOOK-2's jGui).
	 */
	private boolean movesWithItsState(ClassNode mixin, MethodNode handler, ClassNode target, Context ctx) {
		if (independentOf(mixin).contains(handler.name + handler.desc)) return true;
		MixinPreflight.StateGroup group = stateGroupsOf(mixin).get(handler.name + handler.desc);
		for (MethodNode other : mixin.methods) {
			AnnotationNode injector = Annotations.all(other).stream().filter(a -> PreflightAsm.isInjector(a.desc)).findFirst().orElse(null);
			// A handler without its injector has moved onto a bridge already.
			if (other == handler || injector == null || group != null && !group.handlers().contains(other.name + other.desc)) continue;
			boolean moves = !movableCalls(mixin, other, injector, target, ctx).isEmpty() || replacedTarget(mixin, other, injector, target, ctx) != null
					&& bridged(other, replacedTarget(mixin, other, injector, target, ctx)) != null;
			if (!moves && !appliesWhereItIs(injector, target, ctx)) return false;
		}
		return true;
	}

	/** Whether every method an injector selects has its points (and it selects one). */
	private static boolean appliesWhereItIs(AnnotationNode injector, ClassNode target, Context ctx) {
		List<MethodNode> methods = PreflightAsm.selectedMethods(injector, target, ctx);
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		List<AnnotationNode> slices = Annotations.list(Annotations.value(injector, "slice"));
		return !methods.isEmpty() && methods.stream().allMatch(m -> InjectionPoints.pointsIn(ats, slices, m, ctx) == Verdict.FOUND);
	}

	/** How a bridge works one of vanilla's parameters out: the platform's method's parameter, then the getters called on it. */
	private record Derived(int parameter, List<MethodInsnNode> getters) {
	}

	/**
	 * How a bridge taking the platform's method's parameters works out each of vanilla's: as vanilla's call worked it out,
	 * from the value the platform's call passes too, through the field getters vanilla's call reads ({@link #fieldGetter})
	 * on it. Null where one can't be. Forge 47's {@code addEntitiesToWorld(level, pos, settings)}: the mirror is
	 * {@code settings.getMirror()}, the box {@code settings.getBoundingBox()}, which vanilla read into a local.
	 */
	private List<Derived> derive(Replaced replaced) {
		List<Value> wanted = arguments(replaced.vanillaCaller(), replaced.vanillaCall());
		List<Value> given = arguments(replaced.caller(), replaced.call());
		boolean instance = (replaced.vanilla().access & Opcodes.ACC_STATIC) == 0;
		if (wanted == null || given == null || instance && (wanted.get(0).slot() != 0 || given.get(0).slot() != 0)) return null;
		int skip = instance ? 1 : 0;
		Type[] own = Type.getArgumentTypes(replaced.vanilla().desc);
		Type[] params = Type.getArgumentTypes(replaced.method().desc);
		List<Derived> derived = new ArrayList<>();
		for (int k = 0; k < own.length; k++) {
			Derived d = derived(wanted.get(k + skip), given.subList(skip, given.size()), replaced.alignment());
			if (d == null) return null;
			Type type = d.getters().isEmpty() ? params[d.parameter()] : Type.getReturnType(d.getters().get(d.getters().size() - 1).desc);
			if (!type.equals(own[k]) && !(type.getSort() == Type.OBJECT && own[k].getSort() == Type.OBJECT && isSubtype(type.getInternalName(), own[k].getInternalName()))) return null;
			derived.add(d);
		}
		return derived;
	}

	private Derived derived(Value value, List<Value> given, PatchAlignment alignment) {
		for (int j = 0; j < given.size(); j++) {
			if (same(value, given.get(j), alignment)) return new Derived(j, List.of());
		}
		Derived of = value.getter() == null || !fieldGetter(value.getter()) ? null : derived(value.of(), given, alignment);
		if (of == null) return null;
		List<MethodInsnNode> getters = new ArrayList<>(of.getters());
		getters.add(value.getter());
		return new Derived(of.parameter(), getters);
	}

	/** Whether a call runs a public method that returns a field of the instance and does nothing else. */
	private boolean fieldGetter(MethodInsnNode call) {
		PreflightClasses.Code code = declaration(call.owner, call.name, call.desc);
		if (code == null || (code.method().access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) != Opcodes.ACC_PUBLIC) return false;
		List<AbstractInsnNode> real = new ArrayList<>();
		for (AbstractInsnNode insn : code.method().instructions) {
			if (insn.getOpcode() >= 0) real.add(insn);
		}
		return real.size() == 3 && real.get(0) instanceof VarInsnNode self && self.getOpcode() == Opcodes.ALOAD && self.var == 0
				&& real.get(1) instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
				&& real.get(2).getOpcode() == Type.getReturnType(call.desc).getOpcode(Opcodes.IRETURN);
	}

	/**
	 * A value code works out, as far as it is followed: a parameter of the method that it never stores to (by slot), a
	 * no-argument instance call on a value ({@code getter} on {@code of}), or the instruction that pushed it; or none of
	 * those ({@link #UNKNOWN}).
	 */
	private record Value(int slot, MethodInsnNode getter, Value of, int insn) {
		static final Value UNKNOWN = new Value(-1, null, null, -1);
	}

	/** A value and the index of the first instruction of the code pushing it, -1 where that isn't followed. */
	private record Parsed(Value value, int start) {
	}

	/** The values a call passes, its receiver first (unless static); null where its code isn't followed that far. */
	private static List<Value> arguments(MethodNode method, MethodInsnNode call) {
		int count = Type.getArgumentTypes(call.desc).length + (call.getOpcode() == Opcodes.INVOKESTATIC ? 0 : 1);
		Value[] values = new Value[count];
		int end = method.instructions.indexOf(call) - 1;
		for (int k = count - 1; k >= 0; k--) {
			Parsed parsed = parse(method, end, 0);
			if (parsed == null || parsed.start() < 0 && k > 0) return null;
			values[k] = parsed.value();
			end = parsed.start() - 1;
		}
		return List.of(values);
	}

	/**
	 * The value the code ending at or before {@code end} pushes: a load of a parameter, or of a local stored once (its
	 * stored value), a no-argument instance call, a constant or a static field; or the instruction that pushed it.
	 */
	private static Parsed parse(MethodNode method, int end, int depth) {
		while (end >= 0 && method.instructions.get(end).getOpcode() < 0) end--;
		if (end < 0 || depth > 4) return null;
		AbstractInsnNode insn = method.instructions.get(end);
		int op = insn.getOpcode();
		if (insn instanceof VarInsnNode load && op >= Opcodes.ILOAD && op <= Opcodes.ALOAD) {
			List<Integer> stores = storesTo(method, load.var);
			if (load.var < parameterSlots(method)) return new Parsed(stores.isEmpty() ? new Value(load.var, null, null, -1) : Value.UNKNOWN, end);
			Parsed stored = stores.size() == 1 ? parse(method, stores.get(0) - 1, depth + 1) : null;
			return new Parsed(stored == null ? Value.UNKNOWN : stored.value(), end);
		}
		if (insn instanceof MethodInsnNode call && op != Opcodes.INVOKESTATIC && op != Opcodes.INVOKESPECIAL && call.desc.startsWith("()")
				&& Type.getReturnType(call.desc) != Type.VOID_TYPE) {
			Parsed of = parse(method, end - 1, depth);
			return of == null || of.start() < 0 ? new Parsed(new Value(-1, null, null, end), -1) : new Parsed(new Value(-1, call, of.value(), -1), of.start());
		}
		boolean constant = op >= Opcodes.ACONST_NULL && op <= Opcodes.LDC || op == Opcodes.GETSTATIC;
		return new Parsed(new Value(-1, null, null, end), constant ? end : -1);
	}

	/** The indices of the instructions storing to a local variable slot. */
	private static List<Integer> storesTo(MethodNode method, int slot) {
		List<Integer> stores = new ArrayList<>();
		for (int i = 0; i < method.instructions.size(); i++) {
			AbstractInsnNode insn = method.instructions.get(i);
			if (insn instanceof VarInsnNode store && store.getOpcode() >= Opcodes.ISTORE && store.getOpcode() <= Opcodes.ASTORE && store.var == slot
					|| insn instanceof IincInsnNode inc && inc.var == slot) stores.add(i);
		}
		return stores;
	}

	private static int parameterSlots(MethodNode method) {
		int slots = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
		for (Type type : Type.getArgumentTypes(method.desc)) slots += type.getSize();
		return slots;
	}

	/**
	 * Whether a value of vanilla's code is one of the platform's: the same parameter, the same getter on the same value,
	 * or instructions in each other's place ({@link PatchAlignment}), or the same constant or static field.
	 */
	private static boolean same(Value vanilla, Value platform, PatchAlignment alignment) {
		if (vanilla.getter() != null || platform.getter() != null) {
			return vanilla.getter() != null && platform.getter() != null && vanilla.getter().owner.equals(platform.getter().owner)
					&& vanilla.getter().name.equals(platform.getter().name) && vanilla.getter().desc.equals(platform.getter().desc)
					&& same(vanilla.of(), platform.of(), alignment);
		}
		if (vanilla.slot() >= 0 || platform.slot() >= 0) return vanilla.slot() == platform.slot();
		return vanilla.insn() >= 0 && platform.insn() >= 0 && alignment.placeOf(vanilla.insn()) == platform.insn();
	}

	/**
	 * A handler for the injector at the platform's method: it takes that method's parameters and the callback info, and
	 * calls {@code handler} with vanilla's parameters as {@code derived} works them out, and the callback info. The injector
	 * annotation moves to it; {@code handler} stays a plain method of the mixin.
	 */
	private static MethodNode bridge(String mixin, MethodNode handler, AnnotationNode injector, MethodNode method, List<Derived> derived, String name) {
		Type[] own = Type.getArgumentTypes(handler.desc);
		List<Type> params = new ArrayList<>(List.of(Type.getArgumentTypes(method.desc)));
		params.add(own[own.length - 1]);
		MethodNode bridge = HandlerBridges.bridgeMethod(handler, name, params, Type.VOID_TYPE);
		int stack = 0;
		int peak = 0;
		if ((handler.access & Opcodes.ACC_STATIC) == 0) {
			bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
			stack++;
		}
		for (int k = 0; k < derived.size(); k++) {
			Derived d = derived.get(k);
			Type from = params.get(d.parameter());
			bridge.instructions.add(new VarInsnNode(from.getOpcode(Opcodes.ILOAD), HandlerBridges.slotOf(handler, params, d.parameter())));
			peak = Math.max(peak, stack + from.getSize());
			for (MethodInsnNode getter : d.getters()) {
				bridge.instructions.add(new MethodInsnNode(getter.getOpcode(), getter.owner, getter.name, getter.desc, getter.itf));
				peak = Math.max(peak, stack + Type.getReturnType(getter.desc).getSize());
			}
			stack += own[k].getSize();
		}
		bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, HandlerBridges.slotOf(handler, params, params.size() - 1)));
		HandlerBridges.callHandler(bridge, mixin, handler, Math.max(stack + 1, peak));
		HandlerBridges.moveInjector(handler, injector, bridge);
		return bridge;
	}
}
