package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
 * start to the start of the platform's method, which runs once where vanilla's did; one at its end too, where the
 * platform's returns where vanilla's does. Its handler takes
 * vanilla's parameters from a bridge working each out from the platform's method's as vanilla's call did: the value
 * the platform's call passes too, or a field getter called on one ({@link #derive}). The other injectors there (Porting
 * Lib's, which port the platform's own processing) have nothing of vanilla's to apply to and stay out.
 * <p>
 * Where that method calls vanilla's at several places, each with a call of the platform's method in its place and the
 * platform's method called nowhere else, the platform's method still runs once where vanilla's did, at each of them
 * (STRUCT-2): vanilla's {@code FireBlock.tick} tries to set each of the six blocks around a fire alight with its private
 * {@code checkBurnOut(level, pos, chance, random, age)}, which Forge 47 replaced with {@code tryCatchFire}, taking the
 * side the fire comes from too. An {@code @Inject} at the method's start, or at its end where the platform's returns as
 * vanilla's does, moves there; a handler taking vanilla's parameters only where every call works each out the same way.
 * One at a call stays out. Forge 52 to 66 and NeoForge keep the name ({@code checkBurnOut} taking the side), where the
 * bridges for a method given more parameters take the hooks by name (SIG-1, SIG-2).
 * <p>
 * An {@code @Inject} at a call or a field access inside vanilla's method moves too where the platform's method has each
 * instruction the point picks there, in its place ({@link PatchAlignment}), and no other: Moonlight's hooks before and
 * after {@code checkBurnOut}'s {@code removeBlock}, which Forge's {@code tryCatchFire} makes as vanilla's does. Its handler
 * may end with MixinExtras {@code @Share}s, which see nothing of the method's code; it moves only with every other
 * injector of its mixin into that method, so that a mod's hooks there apply together or not at all.
 */
final class ReplacedMethods extends PreflightPart {
	private final Map<String, Optional<Replaced>> replacedCache = new HashMap<>();
	/** The mixin last decided ({@link #moving}), its target, and its handlers that move. */
	private ClassNode decidedMixin;
	private String decidedTarget;
	private Set<String> decided;

	ReplacedMethods(PreflightClasses classes) {
		super(classes);
	}

	/**
	 * Vanilla's private method, the method making its calls ({@code vanillaCaller}, and the platform's {@code caller}),
	 * and the platform's private method ({@code method}) called by {@code calls}, each in the place of vanilla's call at
	 * its index in {@code vanillaCalls}.
	 */
	record Replaced(MethodNode vanilla, MethodNode vanillaCaller, List<MethodInsnNode> vanillaCalls, MethodNode caller, List<MethodInsnNode> calls,
			MethodNode method, PatchAlignment alignment) {
		/** The platform's method as messages name it. */
		String named() {
			return method.name + method.desc;
		}

		/** Whether vanilla's method is called at one place (STRUCT-1) rather than at several (STRUCT-2). */
		boolean oneCall() {
			return calls.size() == 1;
		}

		/** Where the platform's method is called, as messages say it: "which placeInWorld calls in its place". */
		String where() {
			return "which " + caller.name + " calls in its place" + (oneCall() ? "" : " at each of its " + calls.size() + " calls");
		}

		/** The finding a move there names. */
		String finding() {
			return oneCall() ? "STRUCT-1" : "STRUCT-2";
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
		if (made == null || made.size() != 1) return null;
		String key = made.keySet().iterator().next();
		if (!key.startsWith(vanilla.name + ".")) return null;
		MethodNode vanillaCaller = null;
		for (MethodNode m : vanilla.methods) {
			if ((vanilla.name + "." + m.name + m.desc).equals(key)) vanillaCaller = m;
		}
		MethodNode caller = vanillaCaller == null ? null : PreflightAsm.declared(target, vanillaCaller.name, vanillaCaller.desc);
		if (caller == null || caller.instructions == null) return null;
		List<MethodInsnNode> vanillaCalls = new ArrayList<>();
		for (AbstractInsnNode insn : vanillaCaller.instructions) {
			if (insn instanceof MethodInsnNode c && c.owner.equals(vanilla.name) && c.name.equals(name) && c.desc.equals(desc)) vanillaCalls.add(c);
		}
		PatchAlignment alignment = vanillaCalls.size() == made.get(key).size() ? PatchAlignment.of(vanillaCaller, caller) : null;
		if (alignment == null) return null;
		List<MethodInsnNode> calls = new ArrayList<>();
		MethodNode method = null;
		for (MethodInsnNode vanillaCall : vanillaCalls) {
			MethodInsnNode call = callInPlace(vanillaCaller.instructions.indexOf(vanillaCall), alignment, vanilla, target, caller);
			MethodNode m = call == null ? null : PreflightAsm.declared(target, call.name, call.desc);
			// The same method in the place of each of vanilla's calls, each call after the one before: one in the place of each.
			if (m == null || method != null && m != method
					|| !calls.isEmpty() && caller.instructions.indexOf(call) <= caller.instructions.indexOf(calls.get(calls.size() - 1))) return null;
			calls.add(call);
			method = m;
		}
		// One keeping vanilla's name at several calls is vanilla's given more parameters (Forge 52 to 66's and NeoForge's
		// checkBurnOut, taking the side the fire comes from), whose hooks the bridges by name take (SIG-1, SIG-2).
		if (method != null && calls.size() > 1 && method.name.equals(name)) return null;
		if (method == null || (method.access & Opcodes.ACC_PRIVATE) == 0 || (method.access & Opcodes.ACC_STATIC) != (before.access & Opcodes.ACC_STATIC)
				|| !Type.getReturnType(method.desc).equals(Type.getReturnType(before.desc))
				|| !made.equals(OverloadRepairs.callsByMethod(target, method, method, this::gameTarget))) return null;
		return new Replaced(before, vanillaCaller, List.copyOf(vanillaCalls), caller, List.copyOf(calls), method, alignment);
	}

	/**
	 * The platform caller's one call of a method its class declares and vanilla's doesn't among what its patch put in the
	 * place of vanilla's instruction at {@code at} ({@link PatchAlignment#replacementOf}; where that can't be told, the
	 * region around it that both alignments bound); null where it has none or more than one.
	 */
	private static MethodInsnNode callInPlace(int at, PatchAlignment alignment, ClassNode vanilla, ClassNode target, MethodNode caller) {
		int[] range = alignment.replacementOf(at);
		int[] region = range == null ? alignment.uncertainRegion(at) : null;
		if (region != null) range = new int[] {region[2], region[3]};
		if (range == null) return null;
		MethodInsnNode call = null;
		for (int i = range[0]; i < range[1]; i++) {
			if (!(caller.instructions.get(i) instanceof MethodInsnNode c) || !c.owner.equals(target.name) || PreflightAsm.declared(vanilla, c.name, c.desc) != null
					|| PreflightAsm.declared(target, c.name, c.desc) == null) continue;
			if (call != null) return null;
			call = c;
		}
		return call;
	}

	/**
	 * Points an {@code @Inject}'s {@code INVOKE} points at vanilla's one call of a method the platform replaced
	 * ({@link #replaced}) at the platform's call in its place: its handler sees none of the call's arguments. Returns a
	 * description of each move.
	 */
	List<String> callRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx) {
		List<String> moved = new ArrayList<>();
		List<AnnotationNode> calls = movableCalls(mixin, handler, injector, target, ctx);
		if (calls.isEmpty() || !moving(mixin, target, ctx).contains(handler.name + handler.desc)) return moved;
		for (AnnotationNode at : calls) {
			Replaced replaced = calledAt(at, target, ctx);
			String raw = (String) Annotations.value(at, "target");
			Annotations.put(at, "target", "L" + target.name + ";" + replaced.named());
			moved.add("the call " + raw + ", which " + loader + "'s " + target.name.replace('/', '.') + " makes no more: it replaced that private"
					+ " method with " + replaced.named() + ", which it calls in its place; the injection point is pointed there (STRUCT-1)");
		}
		return moved;
	}

	/**
	 * Points an {@code @Inject} at the start or the end of a method the platform replaced ({@link #replaced}), or at a call
	 * or field access in it that the platform's method has in its place, at the platform's method, which runs once where
	 * vanilla's did, at each of vanilla's calls. A handler taking vanilla's parameters gets them from a bridge taking the
	 * platform's ({@link #derive}). Returns a description of the move, or nothing.
	 */
	List<String> methodRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx, List<MethodNode> bridges) {
		Replaced replaced = replacedTarget(mixin, handler, injector, target, ctx);
		List<Derived> derived = replaced == null ? null : bridged(handler, replaced);
		if (derived == null || !moving(mixin, target, ctx).contains(handler.name + handler.desc)) return List.of();
		if (!derived.isEmpty()) {
			bridges.add(bridge(mixin.name, handler, injector, replaced.method(), derived, trailingShares(handler),
					HandlerBridges.freeName(mixin, bridges, handler.name + "$fabricated")));
		}
		@SuppressWarnings("unchecked")
		List<Object> selectors = (List<Object>) Annotations.value(injector, "method");
		selectors.set(0, replaced.named());
		return List.of(replaced.vanilla().name + replaced.vanilla().desc + ", a private method " + loader + " replaced with " + replaced.named()
				+ ", " + replaced.where() + "; the injector is pointed there"
				+ (derived.isEmpty() ? "" : ", its handler taking vanilla's parameters as that method's give them") + " (" + replaced.finding() + ")");
	}

	/** An {@code @Inject} whose handler sees nothing of the code around its point: no captured locals or sugar, no slice. */
	private static boolean movable(ClassNode mixin, MethodNode handler, AnnotationNode injector) {
		if (!injector.desc.equals(MixinNames.INJECT) || (mixin.access & Opcodes.ACC_INTERFACE) != 0 || Annotations.value(injector, "slice") != null
				|| Annotations.value(injector, "locals") != null) return false;
		Type[] args = Type.getArgumentTypes(handler.desc);
		return args.length > 0 && PreflightAsm.isCallbackInfo(args[args.length - 1]);
	}

	/** The points {@link #callRepairs} would move, where the handler is among those moving ({@link #moving}). */
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

	/**
	 * The replaced method whose one call an {@code INVOKE} point names (its first, without a shift by a count), or null:
	 * of several calls, the ones a point picks (by its ordinal, or all of them) are counted in vanilla's code (STRUCT-2).
	 */
	private Replaced calledAt(AnnotationNode at, ClassNode target, Context ctx) {
		String point = InjectionPoints.pointOf(at);
		if (!point.equals("INVOKE") || !(Annotations.value(at, "target") instanceof String raw) || Annotations.value(at, "by") != null
				|| Annotations.value(at, "ordinal") instanceof Integer ordinal && ordinal != 0) return null;
		MemberRef ref = MemberRef.parse(ctx.resolve(raw));
		if (ref.fuzzy() || !target.name.equals(ref.owner()) || ref.name() == null || ref.desc() == null) return null;
		Replaced replaced = replaced(target, ref.name(), ref.desc());
		return replaced == null || !replaced.oneCall() ? null : replaced;
	}

	/**
	 * The replaced method an {@code @Inject} at its start or end (no ordinal or shift), or at a call or field access in it
	 * ({@link #inPlace}), targets, or null; its handler takes the callback info alone or vanilla's parameters before it,
	 * then nothing but {@code @Share}s. One at its end only where the platform's method returns as vanilla's does
	 * ({@link #returnsAlike}).
	 */
	private Replaced replacedTarget(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx) {
		int shares = trailingShares(handler);
		if (shares < 0 || !injector.desc.equals(MixinNames.INJECT) || (mixin.access & Opcodes.ACC_INTERFACE) != 0 || Annotations.value(injector, "slice") != null
				|| Annotations.value(injector, "locals") != null || !(Annotations.value(injector, "method") instanceof List<?> selectors) || selectors.size() != 1
				|| !(selectors.get(0) instanceof String raw)) return null;
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		MemberRef ref = MemberRef.parse(ctx.resolve(raw));
		if (ats.isEmpty() || ref.fuzzy() || ref.name() == null) return null;
		Replaced replaced = replaced(target, ref.name(), ref.desc());
		if (replaced == null) return null;
		for (AnnotationNode at : ats) {
			if (!inPlace(at, replaced, ctx)) return null;
		}
		int own = Type.getArgumentTypes(handler.desc).length - 1 - shares;
		Type[] params = Type.getArgumentTypes(replaced.vanilla().desc);
		if (own == 0) return replaced;
		if (own != params.length) return null;
		for (int i = 0; i < own; i++) {
			if (!Type.getArgumentTypes(handler.desc)[i].equals(params[i])) return null;
		}
		return replaced;
	}

	/**
	 * Whether an {@code @At} picks in the platform's method what it picks in vanilla's: its start; its returns or its end
	 * where the platform's method returns as vanilla's does ({@link #returnsAlike}); or, at a call or a field access, an
	 * instruction in the place of each vanilla's has there ({@link PatchAlignment}) and no other, before or after it.
	 */
	private static boolean inPlace(AnnotationNode at, Replaced replaced, Context ctx) {
		String point = InjectionPoints.pointOf(at);
		Object shift = Annotations.value(at, "shift");
		if (point.equals("HEAD") || point.equals("RETURN") || point.equals("TAIL")) {
			return Annotations.value(at, "ordinal") == null && shift == null && (point.equals("HEAD") || returnsAlike(replaced.vanilla(), replaced.method()));
		}
		if (!point.equals("INVOKE") && !point.equals("FIELD") || !(Annotations.value(at, "target") instanceof String raw) || Annotations.value(at, "by") != null
				|| Annotations.value(at, "args") != null || Annotations.value(at, "slice") != null
				|| shift != null && !(shift instanceof String[] s && s.length == 2 && (s[1].equals("BEFORE") || s[1].equals("AFTER")))) return false;
		String resolved = ctx.resolve(raw);
		int ordinal = InjectionPoints.ordinalOf(at);
		int opcode = InjectionPoints.opcodeOf(at);
		List<Integer> was = InjectionPoints.indexes(point, resolved, ordinal, opcode, InjectionPoints.Range.ALL, replaced.vanilla());
		List<Integer> now = InjectionPoints.indexes(point, resolved, ordinal, opcode, InjectionPoints.Range.ALL, replaced.method());
		PatchAlignment alignment = was == null || now == null || was.isEmpty() || was.size() != now.size() ? null
				: PatchAlignment.of(replaced.vanilla(), replaced.method());
		if (alignment == null) return false;
		for (int k = 0; k < was.size(); k++) {
			if (alignment.placeOf(was.get(k)) != now.get(k)) return false;
		}
		return true;
	}

	/** Whether an injector has a point other than a method's start or end. */
	private static boolean inside(AnnotationNode injector) {
		return Annotations.list(Annotations.value(injector, "at")).stream()
				.anyMatch(at -> !List.of("HEAD", "RETURN", "TAIL").contains(InjectionPoints.pointOf(at)));
	}

	/**
	 * How many MixinExtras {@code @Share}s a handler takes after its callback info, or -1 where it takes anything else
	 * there, has another annotated parameter (its callback info's included), takes no callback info last, or shares under
	 * a namespace, which other mixin classes' handlers may share under too, unseen from this one.
	 */
	private static int trailingShares(MethodNode handler) {
		Type[] args = Type.getArgumentTypes(handler.desc);
		int shares = 0;
		while (shares < args.length && Annotations.ofParameter(handler, args.length - 1 - shares).stream()
				.anyMatch(a -> a.desc.equals(MixinNames.SHARE) && Annotations.value(a, "namespace") == null)) shares++;
		int info = args.length - 1 - shares;
		if (info < 0 || !PreflightAsm.isCallbackInfo(args[info])) return -1;
		for (int i = 0; i < args.length; i++) {
			if (Annotations.ofParameter(handler, i).size() != (i > info ? 1 : 0)) return -1;
		}
		return shares;
	}

	/**
	 * Whether the platform's method returns where vanilla's does: as many returns, each in the place of one of vanilla's
	 * ({@link PatchAlignment}), so that a hook at its returns, or at its last, runs where it ran. Forge 47's
	 * {@code HopperBlockEntity.ejectItems} taking the hopper returns first where Forge's insert hook took the items, which
	 * vanilla's doesn't; Forge's {@code addEntitiesToWorld} returns once at its end, as vanilla's {@code placeEntities}.
	 */
	private static boolean returnsAlike(MethodNode vanilla, MethodNode platform) {
		List<Integer> was = PreflightAsm.returns(vanilla);
		List<Integer> now = PreflightAsm.returns(platform);
		PatchAlignment alignment = was.size() == now.size() ? PatchAlignment.of(vanilla, platform) : null;
		if (alignment == null) return false;
		for (int k = 0; k < was.size(); k++) {
			if (alignment.placeOf(was.get(k)) != now.get(k)) return false;
		}
		return true;
	}

	/**
	 * How the handler gets vanilla's parameters: none where it takes the callback info (and {@code @Share}s) alone; null
	 * where they can't be worked out.
	 */
	private List<Derived> bridged(MethodNode handler, Replaced replaced) {
		int shares = trailingShares(handler);
		return shares < 0 ? null : Type.getArgumentTypes(handler.desc).length - shares == 1 ? List.of() : derive(replaced);
	}

	/** An injector of the mixin being decided: the replaced method it would move into, or null where it would move at a call, or not at all. */
	private record Hook(MethodNode handler, AnnotationNode injector, Replaced into) {
		String key() {
			return handler.name + handler.desc;
		}
	}

	/**
	 * The handlers of a mixin (name and descriptor) whose injectors move: to the platform's call in the place of vanilla's
	 * one of a replaced method ({@link #callRepairs}), or into the platform's method ({@link #methodRepairs}). Decided once
	 * for the mixin, as its first such handler is reached and before any moves, with every condition applied to each:
	 * taking a handler out may take out those that only move with it, until none goes.
	 * <ul>
	 * <li>The repairs reach it: it is none of a {@code @Group}'s alternatives.</li>
	 * <li>It keeps no state with other injectors of its mixin, or each of those ({@link MixinPreflight#stateGroups}, the
	 * whole mixin where it has no group) moves too, or applies where it is. One moved alone would read what the others
	 * never set (HOOK-2's jGui).</li>
	 * <li>One inside a replaced method, or sharing values there, moves only with every other injector of its mixin that
	 * is, or may be, into that method ({@link #mayBeInto}): a mod's hooks there would otherwise apply in part (Moonlight's
	 * two around the block's removal, the one keeping the block for the other).</li>
	 * </ul>
	 * A handler judged to move here that an earlier repair of its own has since pointed elsewhere stays where that put it.
	 */
	private Set<String> moving(ClassNode mixin, ClassNode target, Context ctx) {
		if (decidedMixin == mixin && target.name.equals(decidedTarget)) return decided;
		List<Hook> hooks = new ArrayList<>();
		Set<String> moving = new HashSet<>();
		for (MethodNode handler : mixin.methods) {
			AnnotationNode injector = Annotations.all(handler).stream().filter(a -> PreflightAsm.isInjector(a.desc)).findFirst().orElse(null);
			// A handler without its injector has moved onto another repair's bridge already.
			if (injector == null) continue;
			Replaced into = replacedTarget(mixin, handler, injector, target, ctx);
			if (into != null && bridged(handler, into) == null) into = null;
			Hook hook = new Hook(handler, injector, into);
			hooks.add(hook);
			if (!PreflightAsm.hasAlternatives(mixin, handler) && (into != null || !movableCalls(mixin, handler, injector, target, ctx).isEmpty())) moving.add(hook.key());
		}
		Set<String> independent = independentOf(mixin);
		Map<String, MixinPreflight.StateGroup> groups = stateGroupsOf(mixin);
		for (boolean changed = true; changed;) {
			changed = false;
			for (Hook hook : hooks) {
				if (!moving.contains(hook.key())) continue;
				boolean stays = false;
				MixinPreflight.StateGroup group = groups.get(hook.key());
				boolean together = hook.into() != null && (inside(hook.injector()) || trailingShares(hook.handler()) > 0);
				for (Hook other : hooks) {
					if (other == hook) continue;
					boolean moves = moving.contains(other.key());
					stays |= !independent.contains(hook.key()) && (group == null || group.handlers().contains(other.key())) && !moves
							&& !appliesWhereItIs(other.injector(), target, ctx);
					stays |= together && !(moves && other.into() == hook.into()) && mayBeInto(other.injector(), hook.into().vanilla(), ctx);
				}
				if (stays) {
					moving.remove(hook.key());
					changed = true;
				}
			}
		}
		decidedMixin = mixin;
		decidedTarget = target.name;
		decided = moving;
		return moving;
	}

	/**
	 * Whether an injector selects vanilla's method, or may: by its name, alone or with its descriptor (vanilla's first of
	 * that name where it has several, whichever that is), by a pattern, or by a {@code @Desc}, which aren't read here.
	 */
	private static boolean mayBeInto(AnnotationNode injector, MethodNode vanilla, Context ctx) {
		if (Annotations.value(injector, "target") != null || !(Annotations.value(injector, "method") instanceof List<?> selectors)) return true;
		for (Object selector : selectors) {
			MemberRef ref = selector instanceof String raw ? MemberRef.parse(ctx.resolve(raw)) : null;
			if (ref == null || ref.fuzzy() || ref.name() == null || ref.matchesMethod(vanilla.name, vanilla.desc)) return true;
		}
		return false;
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
		/** Whether the other works the parameter out the same way: from the same parameter, through the same getters. */
		boolean alike(Derived other) {
			if (parameter != other.parameter || getters.size() != other.getters.size()) return false;
			for (int k = 0; k < getters.size(); k++) {
				MethodInsnNode a = getters.get(k);
				MethodInsnNode b = other.getters.get(k);
				if (a.getOpcode() != b.getOpcode() || !a.owner.equals(b.owner) || !a.name.equals(b.name) || !a.desc.equals(b.desc)) return false;
			}
			return true;
		}
	}

	/**
	 * How a bridge taking the platform's method's parameters works out each of vanilla's: as vanilla's call worked it out,
	 * from the value the platform's call passes too, through the field getters vanilla's call reads ({@link #fieldGetter})
	 * on it. Null where one can't be. Forge 47's {@code addEntitiesToWorld(level, pos, settings)}: the mirror is
	 * {@code settings.getMirror()}, the box {@code settings.getBoundingBox()}, which vanilla read into a local. Where
	 * vanilla's method is called at several places (STRUCT-2), the bridge serves each of the platform's calls: null unless
	 * each call works each parameter out the same way.
	 */
	private List<Derived> derive(Replaced replaced) {
		List<Derived> derived = null;
		for (int k = 0; k < replaced.calls().size(); k++) {
			List<Derived> at = derive(replaced, replaced.vanillaCalls().get(k), replaced.calls().get(k));
			if (at == null) return null;
			if (derived != null) {
				for (int p = 0; p < at.size(); p++) {
					if (!at.get(p).alike(derived.get(p))) return null;
				}
			}
			derived = at;
		}
		return derived;
	}

	/** {@link #derive(Replaced)} at one of vanilla's calls and the platform's in its place. */
	private List<Derived> derive(Replaced replaced, MethodInsnNode vanillaCall, MethodInsnNode call) {
		List<Value> wanted = arguments(replaced.vanillaCaller(), vanillaCall);
		List<Value> given = arguments(replaced.caller(), call);
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
	 * no-argument instance call on a value ({@code getter} on {@code of}), or the instruction that pushed it, with the
	 * values of an arithmetic one's {@code operands}; or none of those ({@link #UNKNOWN}).
	 */
	private record Value(int slot, MethodInsnNode getter, Value of, int insn, List<Value> operands) {
		static final Value UNKNOWN = new Value(-1, null, null, -1, List.of());

		Value(int slot, MethodInsnNode getter, Value of, int insn) {
			this(slot, getter, of, insn, List.of());
		}
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
	 * stored value), a no-argument instance call, a constant or a static field, or arithmetic on two of those (vanilla's
	 * {@code FireBlock.tick} passes {@code 300 + k}); or the instruction that pushed it.
	 */
	private static Parsed parse(MethodNode method, int end, int depth) {
		while (end >= 0 && method.instructions.get(end).getOpcode() < 0) end--;
		if (end < 0 || depth > 4) return null;
		AbstractInsnNode insn = method.instructions.get(end);
		int op = insn.getOpcode();
		// IADD to LXOR, but the negations, which take one operand.
		if (op >= Opcodes.IADD && op <= Opcodes.LXOR && (op < Opcodes.INEG || op > Opcodes.DNEG)) {
			Parsed right = parse(method, end - 1, depth + 1);
			Parsed left = right == null || right.start() < 0 ? null : parse(method, right.start() - 1, depth + 1);
			if (left == null || left.start() < 0) return new Parsed(new Value(-1, null, null, end), -1);
			return new Parsed(new Value(-1, null, null, end, List.of(left.value(), right.value())), left.start());
		}
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
	 * or instructions in each other's place ({@link PatchAlignment}), or the same constant or static field; arithmetic
	 * only on the same operands.
	 */
	private static boolean same(Value vanilla, Value platform, PatchAlignment alignment) {
		if (vanilla.getter() != null || platform.getter() != null) {
			return vanilla.getter() != null && platform.getter() != null && vanilla.getter().owner.equals(platform.getter().owner)
					&& vanilla.getter().name.equals(platform.getter().name) && vanilla.getter().desc.equals(platform.getter().desc)
					&& same(vanilla.of(), platform.of(), alignment);
		}
		if (vanilla.slot() >= 0 || platform.slot() >= 0) return vanilla.slot() == platform.slot();
		if (vanilla.insn() < 0 || platform.insn() < 0 || alignment.placeOf(vanilla.insn()) != platform.insn()
				|| vanilla.operands().size() != platform.operands().size()) return false;
		for (int k = 0; k < vanilla.operands().size(); k++) {
			if (!same(vanilla.operands().get(k), platform.operands().get(k), alignment)) return false;
		}
		return true;
	}

	/**
	 * A handler for the injector at the platform's method: it takes that method's parameters, the callback info and the
	 * handler's {@code shares} last ones, and calls {@code handler} with vanilla's parameters as {@code derived} works them
	 * out, the callback info and those. The injector annotation, and the {@code @Share}s', move to it; {@code handler}
	 * stays a plain method of the mixin.
	 */
	private static MethodNode bridge(String mixin, MethodNode handler, AnnotationNode injector, MethodNode method, List<Derived> derived, int shares,
			String name) {
		Type[] own = Type.getArgumentTypes(handler.desc);
		List<Type> params = new ArrayList<>(List.of(Type.getArgumentTypes(method.desc)));
		params.addAll(List.of(own).subList(own.length - 1 - shares, own.length));
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
		int first = params.size() - 1 - shares;
		for (int k = first; k < params.size(); k++) {
			bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, HandlerBridges.slotOf(handler, params, k)));
		}
		HandlerBridges.moveSugar(handler, own.length - shares, bridge, params.size() - shares, shares);
		HandlerBridges.callHandler(bridge, mixin, handler, Math.max(stack + 1 + shares, peak));
		HandlerBridges.moveInjector(handler, injector, bridge);
		return bridge;
	}
}
