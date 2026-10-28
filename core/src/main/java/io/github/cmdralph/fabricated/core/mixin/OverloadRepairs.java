package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.function.Predicate;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.mixin.InjectionPoints.Verdict;
import io.github.cmdralph.fabricated.core.mixin.MixinPreflight.Context;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Injector repairs for overloads: a selector pinned to the overload its handler takes, an injector moved to the
 * overload NeoForge turned the method into, and a constant NeoForge split into another method.
 */
final class OverloadRepairs extends PreflightPart {
	OverloadRepairs(PreflightClasses classes) {
		super(classes);
	}

	/**
	 * Pins a descriptor-less {@code @Inject} or {@code @WrapMethod} selector to the one overload whose parameters the
	 * handler takes, when Mixin would pick another (the first of that name); returns the pinned methods.
	 */
	static List<String> overloadByParameters(MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx) {
		boolean inject = injector.desc.equals(MixinNames.INJECT);
		if (!inject && !injector.desc.equals(MixinNames.WRAP_METHOD) || Annotations.value(injector, "locals") != null
				|| !(Annotations.value(injector, "method") instanceof List<?> selectors)) return List.of();
		List<Type> args = PreflightAsm.handlerArgs(handler);
		if (args == null || args.size() < 2
				|| !(inject ? PreflightAsm.isCallbackInfo(args.get(args.size() - 1)) : args.get(args.size() - 1).getDescriptor().equals(MixinNames.OPERATION))) return List.of();
		List<Type> wanted = args.subList(0, args.size() - 1);
		List<String> pinned = new ArrayList<>();
		for (int i = 0; i < selectors.size(); i++) {
			if (!(selectors.get(i) instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy() || ref.name() == null || ref.desc() != null) continue;
			MethodNode first = null;
			MethodNode fitting = null;
			int fits = 0;
			for (MethodNode m : target.methods) {
				if (!m.name.equals(ref.name())) continue;
				if (first == null) first = m;
				if (List.of(Type.getArgumentTypes(m.desc)).equals(wanted)) {
					fitting = m;
					fits++;
				}
			}
			if (fits != 1 || fitting == first) continue;
			@SuppressWarnings("unchecked")
			List<Object> values = (List<Object>) selectors;
			values.set(i, fitting.name + fitting.desc);
			pinned.add(fitting.name + fitting.desc);
		}
		return pinned;
	}

	/**
	 * Points the selectors of an injector whose injection points are missing from the method Mixin picks, but all occur in
	 * the one overload of the same name that method calls (NeoForge's way of keeping vanilla's signature while moving the
	 * code), at that overload; returns a description of each move. An {@code @Inject} whose points are there but whose
	 * captured locals aren't (the method only calls the overload now) moves the same way. A handler that takes the
	 * method's parameters gets them from a bridge taking the overload's, when the method only passes its own parameters
	 * on with one value it works out ({@link #insertedParameter}, as {@code MappedRegistry.register} with an id). Its
	 * {@code @Local}s must find their locals in the overload ({@link #localsInOverload}).
	 */
	List<String> overloadRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx,
			List<MethodNode> bridges) {
		if (!(Annotations.value(injector, "method") instanceof List<?> selectors)) return List.of();
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		if (ats.isEmpty() || Annotations.value(injector, "locals") != null) return List.of();
		// A variable modifier moves only where it picks its local by type alone, among the locals, which the other method's
		// code decides as the method's did: Fabric API's before-bake model hook, whose ModelBakerImpl.bake Forge 47 split
		// off into an overload taking the sprite getter (MODEL-3). By its slot, its place, its name or among the
		// parameters, it could pick another value there.
		if (injector.desc.equals(MixinNames.MODIFY_VARIABLE) && (Annotations.value(injector, "index") != null || Annotations.value(injector, "ordinal") != null
				|| Annotations.value(injector, "name") != null || Boolean.TRUE.equals(Annotations.value(injector, "argsOnly")))) return List.of();
		// An @Inject handler takes the callback info only, or (bridged below) the target's parameters before it.
		boolean takesParameters = false;
		if (injector.desc.equals(MixinNames.INJECT)) {
			List<Type> args = PreflightAsm.handlerArgs(handler);
			if (args == null || args.isEmpty() || !PreflightAsm.isCallbackInfo(args.get(args.size() - 1))) return List.of();
			takesParameters = args.size() > 1;
		}
		boolean inject = injector.desc.equals(MixinNames.INJECT);
		List<AnnotationNode> slices = Annotations.list(Annotations.value(injector, "slice"));
		List<String> moved = new ArrayList<>();
		for (int i = 0; i < selectors.size(); i++) {
			if (!(selectors.get(i) instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy() || ref.name() == null) continue;
			MethodNode chosen = null;
			for (MethodNode m : target.methods) {
				if (ref.matchesMethod(m.name, m.desc)) {
					chosen = m;
					break;
				}
			}
			if (chosen == null) continue;
			MethodNode overload;
			String how;
			Verdict inChosen = InjectionPoints.pointsIn(ats, slices, chosen, ctx);
			boolean localsGone = inject && inChosen == Verdict.FOUND && MixinPreflight.missingLocal(handler, List.of(chosen)) != null;
			if (inChosen == Verdict.MISSING || localsGone) {
				overload = calledOverload(chosen, target);
				MethodNode replacement = overload == null ? replacementOf(chosen, target) : null;
				if (replacement != null) overload = replacement;
				boolean split = overload == null && inChosen == Verdict.MISSING;
				if (split) overload = splitOff(chosen, target, ats, slices, ctx);
				if (overload == null || (overload.access & Opcodes.ACC_STATIC) != (chosen.access & Opcodes.ACC_STATIC)
						|| !split && !Type.getReturnType(overload.desc).equals(Type.getReturnType(chosen.desc))
						|| InjectionPoints.pointsIn(ats, slices, overload, ctx) != Verdict.FOUND) continue;
				// Its callback info must suit the method it moves to: a returnable one only one returning the same. A handler
				// sharing the mixin's state with others that may stay moves only with them, which this doesn't do (HOOK-2's).
				if (split && (inject && !Type.getReturnType(overload.desc).equals(Type.getReturnType(chosen.desc))
						&& PreflightAsm.last(PreflightAsm.handlerArgs(handler)).getDescriptor().equals(MixinNames.CALLBACK_INFO_RETURNABLE)
						|| !independentOf(mixin).contains(handler.name + handler.desc))) continue;
				// A handler taking the method's parameters takes the other's as they are, when they are the same.
				boolean sameParameters = Arrays.equals(Type.getArgumentTypes(chosen.desc), Type.getArgumentTypes(overload.desc));
				int captured = sameParameters ? -1 : inject ? (takesParameters ? 0 : -1) : capturedFrom(handler, chosen);
				int inserted = replacement != null ? CallRepairs.insertedArgument(chosen.desc, overload.desc) : insertedParameter(chosen, overload);
				List<Runnable> locals = LocalRepairs.localsFit(handler, overload) ? List.of() : localsInOverload(handler, overload, inserted, ats, ctx);
				if (locals == null || captured >= 0 && (inserted < 0 || selectors.size() != 1 || (mixin.access & Opcodes.ACC_INTERFACE) != 0)) continue;
				locals.forEach(Runnable::run);
				if (captured >= 0) {
					bridges.add(HandlerBridges.insertedParameterBridge(mixin.name, handler, injector, captured, Type.getArgumentTypes(chosen.desc).length,
							overload, inserted, HandlerBridges.freeName(mixin, bridges, handler.name + "$fabricated")));
				}
				how = localsGone ? ", which " + loader + " reduced to a call to " : replacement != null ? ", which " + loader + " deprecated for "
						: split ? ", the code of which there " + loader + " split off into " : ", whose code " + loader + " moved into ";
			} else {
				// The injection points exist, but in a constructor nothing calls any more: its end is the end of the one it calls.
				overload = ConstructorRepairs.keptConstructorDelegate(chosen, target);
				if (overload == null || !slices.isEmpty() || !ConstructorRepairs.atEndOnly(ats) || !LocalRepairs.localsFit(handler, overload)) continue;
				if (takesParameters) {
					// The handler gets the constructor's own parameters from a bridge taking all of the other one's.
					if (selectors.size() != 1 || (mixin.access & Opcodes.ACC_INTERFACE) != 0 || !ConstructorRepairs.forwardsItsParameters(chosen, overload)
							|| !ConstructorRepairs.takesExactly(handler, chosen)) continue;
					bridges.add(HandlerBridges.bridge(mixin.name, handler, injector, chosen, overload, HandlerBridges.freeName(mixin, bridges, handler.name + "$fabricated")));
				}
				how = ", which " + loader + " keeps only to call ";
			}
			@SuppressWarnings("unchecked")
			List<Object> values = (List<Object>) selectors;
			values.set(i, overload.name + overload.desc);
			moved.add(chosen.name + chosen.desc + how + overload.name + overload.desc + "; the injector is pointed there");
		}
		return moved;
	}

	/**
	 * Points a {@code @ModifyConstant} whose constants are in none of the methods it targets at the code NeoForge's
	 * patches split off from one ({@link #splitOff}), when they all are there: Carpet's update flag in
	 * {@code Level.setBlock}, now in {@code markAndNotifyBlock} (SPLIT-2). Only a handler taking the constant alone moves
	 * (others take the method's parameters too), and one sharing none of its mixin's state with its other injectors.
	 * Returns a description of each move.
	 */
	List<String> constantSplitRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx) {
		if (!injector.desc.equals(MixinNames.MODIFY_CONSTANT) || !(Annotations.value(injector, "method") instanceof List<?> selectors)
				|| Annotations.value(injector, "slice") != null || PreflightAsm.handlerArgs(handler) == null || PreflightAsm.handlerArgs(handler).size() != 1) return List.of();
		List<AnnotationNode> constants = Annotations.list(Annotations.value(injector, "constant"));
		if (constants.isEmpty() || constants.stream().anyMatch(c -> Annotations.value(c, "ordinal") != null || Annotations.value(c, "slice") != null
				|| InjectionPoints.constantDescription(c) == null)) return List.of();
		List<String> moved = new ArrayList<>();
		for (int i = 0; i < selectors.size(); i++) {
			if (!(selectors.get(i) instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy() || ref.name() == null) continue;
			MethodNode chosen = null;
			for (MethodNode m : target.methods) {
				if (ref.matchesMethod(m.name, m.desc)) {
					chosen = m;
					break;
				}
			}
			MethodNode from = chosen;
			if (from == null || constants.stream().anyMatch(c -> InjectionPoints.constantVerdict(c, from) != Verdict.MISSING)) continue;
			MethodNode split = splitOff(from, target, m -> constants.stream().allMatch(c -> InjectionPoints.constantVerdict(c, m) == Verdict.FOUND));
			if (split == null || (split.access & Opcodes.ACC_STATIC) != (from.access & Opcodes.ACC_STATIC)
					|| !independentOf(mixin).contains(handler.name + handler.desc)) continue;
			@SuppressWarnings("unchecked")
			List<Object> values = (List<Object>) selectors;
			values.set(i, split.name + split.desc);
			moved.add(from.name + from.desc + ", the code of which there " + loader + " split off into " + split.name + split.desc
					+ "; the injector is pointed there");
		}
		return moved;
	}

	/**
	 * The one method of the class {@code method} calls in which the injection points are, when none of them is anywhere
	 * in {@code method} (no ordinal, no slice) and nothing else in the class calls it: code NeoForge's patches split off
	 * into a method of their own, as {@code Level.setBlock}'s neighbour updates into {@code markAndNotifyBlock}
	 * (SPLIT-1). Null when none or several are.
	 */
	private static MethodNode splitOff(MethodNode method, ClassNode target, List<AnnotationNode> ats, List<AnnotationNode> slices, Context ctx) {
		if (!slices.isEmpty() || ats.stream().anyMatch(at -> Annotations.value(at, "ordinal") != null)) return null;
		return splitOff(method, target, callee -> InjectionPoints.pointsIn(ats, slices, callee, ctx) == Verdict.FOUND);
	}

	/**
	 * The one method of the class {@code method} calls that {@code holds} the injection points, when nothing else in the
	 * class calls it but methods that only pass their parameters on to it: NeoForge's way of keeping an entry point that
	 * skips its event, as {@code PersistentEntitySectionManager.addNewEntityWithoutEvent} next to {@code addEntity}, whose
	 * code it split off into {@code addEntityWithoutEvent} (SPLIT-2). Such a method is how this game build gets to the code
	 * a vanilla method of the class did. Null when none or several are.
	 */
	private static MethodNode splitOff(MethodNode method, ClassNode target, Predicate<MethodNode> holds) {
		MethodNode found = null;
		for (AbstractInsnNode insn : method.instructions) {
			if (!(insn instanceof MethodInsnNode call) || !call.owner.equals(target.name)) continue;
			MethodNode callee = PreflightAsm.declared(target, call.name, call.desc);
			if (callee == null || callee == method || callee == found || callee.name.startsWith("<") || !holds.test(callee)) continue;
			if (found != null) return null;
			found = callee;
		}
		if (found == null) return null;
		int calls = 0;
		for (MethodNode m : target.methods) {
			if (m != method && onlyCalls(m, target, found)) continue;
			for (AbstractInsnNode insn : m.instructions) {
				if (insn instanceof MethodInsnNode call && call.owner.equals(target.name) && call.name.equals(found.name) && call.desc.equals(found.desc)) calls++;
			}
		}
		return calls == 1 ? found : null;
	}

	/** Whether {@code method} does nothing but call {@code callee} of its class, with its parameters and constants, and return. */
	private static boolean onlyCalls(MethodNode method, ClassNode target, MethodNode callee) {
		boolean called = false;
		for (AbstractInsnNode insn : method.instructions) {
			if (insn.getOpcode() < 0) continue; // labels, line numbers, frames
			if (called) return insn.getOpcode() >= Opcodes.IRETURN && insn.getOpcode() <= Opcodes.RETURN;
			if (insn instanceof MethodInsnNode call) {
				if (!call.owner.equals(target.name) || !call.name.equals(callee.name) || !call.desc.equals(callee.desc)) return false;
				called = true;
			} else if (!(insn instanceof VarInsnNode load && load.getOpcode() >= Opcodes.ILOAD && load.getOpcode() <= Opcodes.ALOAD)
					&& !(insn.getOpcode() >= Opcodes.ACONST_NULL && insn.getOpcode() <= Opcodes.LDC)) {
				return false;
			}
		}
		return false;
	}

	/**
	 * The method NeoForge's build replaced {@code method} with: the one method of the class it calls taking its parameters
	 * and one more, when NeoForge marks {@code method} deprecated, which vanilla's methods never are (DEPRECATED-1).
	 * {@code BoneMealItem.growCrop}'s code is in {@code applyBonemeal} there, which also takes the player. Null otherwise.
	 */
	static MethodNode replacementOf(MethodNode method, ClassNode target) {
		if ((method.access & Opcodes.ACC_DEPRECATED) == 0 && Annotations.find(method, "Ljava/lang/Deprecated;") == null) return null;
		MethodNode found = null;
		for (AbstractInsnNode insn : method.instructions) {
			if (!(insn instanceof MethodInsnNode call) || !call.owner.equals(target.name) || CallRepairs.insertedArgument(method.desc, call.desc) < 0) continue;
			MethodNode callee = PreflightAsm.declared(target, call.name, call.desc);
			if (callee == null || callee == found || (callee.access & Opcodes.ACC_STATIC) != (method.access & Opcodes.ACC_STATIC)) continue;
			if (found != null) return null;
			found = callee;
		}
		return found;
	}

	/** The one other overload of the same name {@code method} calls, or null (none, or several). */
	private static MethodNode calledOverload(MethodNode method, ClassNode target) {
		MethodNode overload = null;
		for (AbstractInsnNode insn : method.instructions) {
			if (!(insn instanceof MethodInsnNode call) || !call.owner.equals(target.name) || !call.name.equals(method.name)
					|| call.desc.equals(method.desc)) continue;
			MethodNode candidate = PreflightAsm.declared(target, call.name, call.desc);
			if (candidate == null || candidate == overload) continue;
			if (overload != null) return null; // several overloads: ambiguous
			overload = candidate;
		}
		return overload;
	}

	/**
	 * The index of the one parameter {@code overload} has that {@code method} doesn't, when {@code method} only calls
	 * {@code overload} with its own parameters in order and a value it works out for that one (no stores or jumps), and
	 * returns the result: NeoForge's way of adding a parameter while keeping vanilla's signature, as
	 * {@code MappedRegistry.register(key, value, info)} calling {@code register(this.byId.size(), key, value, info)}. -1
	 * otherwise.
	 */
	static int insertedParameter(MethodNode method, MethodNode overload) {
		Type[] own = Type.getArgumentTypes(method.desc);
		Type[] all = Type.getArgumentTypes(overload.desc);
		boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
		if (all.length != own.length + 1 || (overload.access & Opcodes.ACC_STATIC) != (method.access & Opcodes.ACC_STATIC)) return -1;
		List<AbstractInsnNode> code = new ArrayList<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (insn.getOpcode() >= 0) code.add(insn); // not labels, line numbers or frames
		}
		if (code.size() < 3 || !(code.get(code.size() - 2) instanceof MethodInsnNode call) || !call.name.equals(overload.name)
				|| !call.desc.equals(overload.desc) || code.get(code.size() - 1).getOpcode() != Type.getReturnType(method.desc).getOpcode(Opcodes.IRETURN)) return -1;
		int[] slots = new int[own.length];
		int slot = isStatic ? 0 : 1;
		for (int i = 0; i < own.length; i++) {
			slots[i] = slot;
			slot += own[i].getSize();
		}
		int start = 0;
		if (!isStatic) {
			if (!(code.get(0) instanceof VarInsnNode self) || self.getOpcode() != Opcodes.ALOAD || self.var != 0) return -1;
			start = 1;
		}
		int k = 0;
		while (k < own.length && isLoad(code.get(start), own[k], slots[k])) {
			start++;
			k++;
		}
		int end = code.size() - 2;
		int j = own.length;
		while (j > k && end > start && isLoad(code.get(end - 1), own[j - 1], slots[j - 1])) {
			end--;
			j--;
		}
		if (j != k || start == end) return -1;
		for (int i = start; i < end; i++) {
			int op = code.get(i).getOpcode();
			boolean stores = op >= Opcodes.ISTORE && op <= Opcodes.SASTORE || op == Opcodes.PUTFIELD || op == Opcodes.PUTSTATIC || op == Opcodes.IINC;
			if (stores || code.get(i) instanceof JumpInsnNode || code.get(i) instanceof TableSwitchInsnNode || code.get(i) instanceof LookupSwitchInsnNode
					|| op >= Opcodes.IRETURN && op <= Opcodes.RETURN || op == Opcodes.ATHROW) return -1;
		}
		for (int i = 0; i < own.length; i++) {
			if (!own[i].equals(all[i < k ? i : i + 1])) return -1;
		}
		return k;
	}

	private static boolean isLoad(AbstractInsnNode insn, Type type, int slot) {
		return insn instanceof VarInsnNode load && load.getOpcode() == type.getOpcode(Opcodes.ILOAD) && load.var == slot;
	}

	/**
	 * Where a handler of an injector other than {@code @Inject} takes {@code method}'s parameters (all of them, as Mixin
	 * requires), after its own and before any MixinExtras sugar; -1 when it doesn't take them.
	 */
	private static int capturedFrom(MethodNode handler, MethodNode method) {
		List<Type> args = PreflightAsm.handlerArgs(handler);
		List<Type> params = List.of(Type.getArgumentTypes(method.desc));
		if (args == null || params.isEmpty() || args.size() <= params.size()) return -1;
		return args.subList(args.size() - params.size(), args.size()).equals(params) ? args.size() - params.size() : -1;
	}

	/**
	 * How the handler's {@code @Local}s find their locals in {@code overload}, an injector moved there from the method
	 * that only calls it: by name, when the overload has a local of that name; by type, when one local of that type
	 * besides the parameter the method inserted ({@code inserted}) is in scope at each instruction the injection point
	 * picks ({@link #pointPositions}), which the {@code @Local} then names by its ordinal (MixinExtras counts the
	 * parameters too): architectury's explosion hook takes the entities' list in {@code ServerExplosion.hurtEntities},
	 * whose overload takes the exploded blocks' list too; or, as {@link LocalRepairs#localsFit} allows, a parameter. The changes to
	 * make, or null when one can't be found that way.
	 */
	private static List<Runnable> localsInOverload(MethodNode handler, MethodNode overload, int inserted, List<AnnotationNode> ats, Context ctx) {
		Type[] types = Type.getArgumentTypes(handler.desc);
		Type[] params = Type.getArgumentTypes(overload.desc);
		boolean isStatic = (overload.access & Opcodes.ACC_STATIC) != 0;
		int insertedSlot = -1;
		int slot = isStatic ? 0 : 1;
		for (int i = 0; i < params.length; i++) {
			if (i == inserted) insertedSlot = slot;
			slot += params[i].getSize();
		}
		List<Runnable> changes = new ArrayList<>();
		for (int i = 0; i < types.length; i++) {
			for (AnnotationNode a : Annotations.ofParameter(handler, i)) {
				if (!a.desc.equals(MixinNames.LOCAL)) continue;
				Type type = types[i];
				if (Boolean.TRUE.equals(Annotations.value(a, "argsOnly"))) {
					if (List.of(params).stream().filter(type::equals).count() != 1) return null;
				} else if (Annotations.value(a, "name") instanceof List<?> names && !names.isEmpty()) {
					if (overload.localVariables == null || overload.localVariables.stream().noneMatch(lv -> names.contains(lv.name))) return null;
				} else if (Annotations.value(a, "index") != null || Annotations.value(a, "ordinal") != null
						|| type.getDescriptor().startsWith("Lcom/llamalad7/mixinextras/sugar/ref/")) {
					return null;
				} else {
					List<Integer> positions = pointPositions(ats, overload, ctx);
					int ordinal = positions == null ? -1 : ordinalAt(overload, type, insertedSlot, positions);
					if (ordinal < 0) return null;
					changes.add(() -> {
						a.values = a.values == null ? new ArrayList<>() : a.values;
						a.values.add("ordinal");
						a.values.add(ordinal);
					});
				}
			}
		}
		return changes;
	}

	/**
	 * The instructions of {@code method}, by index, at which an injector's points inject: each return, for points at the
	 * end; for one point naming a call, field access or construction (not shifted after it), each instruction it
	 * matches, or its ordinal-th. Null for any other point, or when it matches none.
	 */
	private static List<Integer> pointPositions(List<AnnotationNode> ats, MethodNode method, Context ctx) {
		List<Integer> positions = new ArrayList<>();
		if (ConstructorRepairs.atEndOnly(ats)) {
			for (int i = 0; i < method.instructions.size(); i++) {
				int op = method.instructions.get(i).getOpcode();
				if (op >= Opcodes.IRETURN && op <= Opcodes.RETURN) positions.add(i);
			}
			return positions.isEmpty() ? null : positions;
		}
		if (ats.size() != 1) return null;
		AnnotationNode at = ats.get(0);
		String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
		if (!List.of("INVOKE", "FIELD", "NEW").contains(point) || !(Annotations.value(at, "target") instanceof String raw)
				|| Annotations.value(at, "shift") != null || Annotations.value(at, "by") != null) return null;
		MemberRef ref = MemberRef.parse(ctx.resolve(raw));
		if (ref.fuzzy()) return null;
		int opcode = Annotations.value(at, "opcode") instanceof Integer o ? o : -1;
		for (int i = 0; i < method.instructions.size(); i++) {
			AbstractInsnNode insn = method.instructions.get(i);
			if ((opcode < 0 || insn.getOpcode() == opcode) && InjectionPoints.matchesInsn(point, ref, insn)) positions.add(i);
		}
		int ordinal = Annotations.value(at, "ordinal") instanceof Integer n ? n : -1;
		if (ordinal >= 0) return ordinal < positions.size() ? List.of(positions.get(ordinal)) : null;
		return positions.isEmpty() ? null : positions;
	}

	/**
	 * The ordinal, among the locals of {@code type} in scope (parameters first, by slot), of the one besides the parameter
	 * at {@code insertedSlot} that is in scope at each of {@code positions} in {@code method}; -1 when there is no such
	 * single local (or no local variable table).
	 */
	private static int ordinalAt(MethodNode method, Type type, int insertedSlot, List<Integer> positions) {
		if (method.localVariables == null || method.localVariables.isEmpty()) return -1;
		int found = -1;
		for (int i : positions) {
			TreeSet<Integer> inScope = new TreeSet<>();
			for (LocalVariableNode lv : method.localVariables) {
				if (lv.desc.equals(type.getDescriptor()) && method.instructions.indexOf(lv.start) <= i && i < method.instructions.indexOf(lv.end)) {
					inScope.add(lv.index);
				}
			}
			TreeSet<Integer> others = new TreeSet<>(inScope);
			others.remove(insertedSlot);
			if (others.size() != 1) return -1;
			int ordinal = inScope.headSet(others.first()).size();
			if (found >= 0 && found != ordinal) return -1;
			found = ordinal;
		}
		return found;
	}
}
