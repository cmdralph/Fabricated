package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.bytecode.VanillaSeams;
import io.github.cmdralph.fabricated.core.mixin.MixinPreflight.Context;
import io.github.cmdralph.fabricated.core.mixin.PreflightClasses.Code;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Injector repairs for calls the game makes differently: through Fabric Loader's hooks, a subtype, a NeoForge hook
 * that took the call over, or an overload taking one more argument.
 */
final class CallRepairs extends PreflightPart {
	/** The mixin and target last decided ({@link #movingTogether}), and its handlers that move to a hook together. */
	private ClassNode togetherMixin;
	private String togetherTarget;
	private Set<String> together;

	CallRepairs(PreflightClasses classes) {
		super(classes);
	}

	/**
	 * Points an {@code @Inject}'s {@code INVOKE} points at one of Fabric Loader's hooks ({@link MixinNames#LOADER_HOOKS}) at the call
	 * this game build makes at that step, when the methods it targets make one of those calls once, and not the hook's.
	 * Shifted as the mod shifts it: after the hook is after every mod's entrypoints, on both. Returns a description of each
	 * move.
	 */
	static List<String> loaderHookRepairs(AnnotationNode injector, ClassNode target, Context ctx) {
		if (!injector.desc.equals(MixinNames.INJECT) || Annotations.value(injector, "slice") != null) return List.of();
		List<MethodNode> methods = PreflightAsm.selectedMethods(injector, target, ctx);
		List<String> moved = new ArrayList<>();
		for (AnnotationNode at : Annotations.list(Annotations.value(injector, "at"))) {
			String point = InjectionPoints.pointOf(at);
			if (!point.equals("INVOKE") || !(Annotations.value(at, "target") instanceof String raw)
					|| Annotations.value(at, "ordinal") instanceof Integer ordinal && ordinal > 0) continue;
			String resolved = ctx.resolve(raw);
			MemberRef hook = MemberRef.parse(resolved);
			String step = null;
			for (String candidate : MixinNames.LOADER_HOOKS.getOrDefault(resolved, List.of())) {
				if (calls(methods, MemberRef.parse(candidate)) == 1) step = candidate;
			}
			if (step == null || calls(methods, hook) != 0) continue;
			MemberRef call = MemberRef.parse(step);
			Annotations.put(at, "target", step);
			moved.add(hook.owner().replace('/', '.') + "." + hook.name() + ", which Fabric Loader adds where it runs mods' entrypoints;"
					+ " the injection point is pointed at " + call.owner().replace('/', '.') + "." + call.name() + ", where they run here");
		}
		return moved;
	}

	/** How many calls to {@code ref} (owner, name and descriptor) the methods make. */
	private static int calls(List<MethodNode> methods, MemberRef ref) {
		int n = 0;
		for (MethodNode method : methods) {
			for (AbstractInsnNode insn : method.instructions) {
				if (insn instanceof MethodInsnNode call && call.owner.equals(ref.owner()) && call.name.equals(ref.name()) && call.desc.equals(ref.desc())) n++;
			}
		}
		return n;
	}

	/**
	 * Points the {@code INVOKE} points of an {@code @Inject} or {@code @ModifyExpressionValue} at a call this game build
	 * makes through a subtype of the class they name, when the named call is in none of the methods the injector
	 * targets: the same method, inherited. (NeoForge wraps vanilla's {@code Decoder} in a conditional {@code Codec} and
	 * calls {@code parse} on that.) Mixin matches a call's owner exactly. Neither injector's handler takes the call's
	 * receiver, so its type changing doesn't matter; the arguments and the result are the same. Only one subtype may
	 * make the call. Returns a description of each move.
	 * <p>
	 * A {@code @Redirect}, {@code @WrapWithCondition} or {@code @WrapOperation} at an instance call takes the receiver,
	 * as the named class: its point moves where its handler's receiver can take the subtype as Mixin's {@code @Coerce}
	 * takes it ({@link HandlerBridges#coerceParameter}), a value of the subtype passed for the supertype, the call
	 * dispatched as before. Only where the handler's original call, made by a {@code @WrapOperation}'s operation, is made
	 * on the receiver it was given ({@link HandlerBridges#passesOwnReceiver}): the operation casts it back to the
	 * subtype. NeoForge 21.1's and Forge's {@code KeyboardHandler.charTyped} lambdas call the screen's {@code charTyped},
	 * where vanilla's call a {@code GuiEventListener}'s, which owo-lib's braid layer wraps (LAMBDA-14).
	 * <p>
	 * Only where vanilla's methods, as the mod's selectors pick them ({@code declared}), make the named call, which Fabric's
	 * Mixin finds there (VANILLA-1; a wrapper only where vanilla's class tells): where vanilla's too makes it through the
	 * subtype, the injector finds nothing on Fabric either, and a mod's own injector for the subtype's call applies there.
	 */
	List<String> subtypeCallRepairs(MethodNode handler, AnnotationNode injector, List<?> declared, ClassNode target, Context ctx) {
		boolean pointOnly = injector.desc.equals(MixinNames.INJECT) || injector.desc.equals(MixinNames.MODIFY_EXPRESSION_VALUE);
		boolean wrapper = injector.desc.equals(MixinNames.REDIRECT) || injector.desc.equals(MixinNames.WRAP_OPERATION)
				|| MixinNames.WRAP_WITH_CONDITION.contains(injector.desc);
		if (!pointOnly && !wrapper || Annotations.value(injector, "slice") != null) return List.of();
		List<MethodNode> methods = PreflightAsm.selectedMethods(injector, target, ctx);
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		if (wrapper && ats.size() != 1) return List.of();
		List<String> moved = new ArrayList<>();
		for (AnnotationNode at : ats) {
			String point = InjectionPoints.pointOf(at);
			if (!point.equals("INVOKE") && !point.equals("INVOKE_ASSIGN") || !(Annotations.value(at, "target") instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy() || ref.owner() == null || ref.name() == null || ref.desc() == null) continue;
			String subtype = null;
			int calls = 0;
			boolean named = false;
			boolean ambiguous = false;
			for (MethodNode method : methods) {
				for (AbstractInsnNode insn : method.instructions) {
					if (!(insn instanceof MethodInsnNode call) || !ref.matchesMethod(call.name, call.desc)) continue;
					if (call.owner.equals(ref.owner())) {
						named = true;
					} else if (isSubtype(call.owner, ref.owner())) {
						ambiguous |= subtype != null && !subtype.equals(call.owner) || wrapper && call.getOpcode() == Opcodes.INVOKESTATIC;
						subtype = call.owner;
						calls++;
					}
				}
			}
			int ordinal = InjectionPoints.ordinalOf(at);
			if (named || subtype == null || ambiguous || ordinal >= calls) continue;
			Boolean inVanilla = vanillaMakes(injector, declared, at, target, ctx);
			if (Boolean.FALSE.equals(inVanilla) || wrapper && inVanilla == null) continue;
			if (wrapper && !takesReceiverAs(handler, injector, ref)) continue;
			String retargeted = "L" + subtype + ";" + ref.name() + ref.desc();
			Annotations.put(at, "target", retargeted);
			if (wrapper) HandlerBridges.coerceParameter(handler, 0);
			moved.add(ref.owner().replace('/', '.') + "." + ref.name() + ", which this game build calls through "
					+ subtype.replace('/', '.') + " (the same method); the injection point is pointed there"
					+ (wrapper ? ", the handler taking the receiver as it is written for, a " + ref.owner().replace('/', '.') : ""));
		}
		return moved;
	}

	/**
	 * Whether vanilla's version of the methods an injector's selectors, as the mod declares them, pick in the target makes
	 * the call {@code at} names, which Fabric's Mixin then finds there (VANILLA-1). Null where vanilla's class isn't at hand,
	 * the selectors pick nothing in it, or a point can't be told.
	 */
	private Boolean vanillaMakes(AnnotationNode injector, List<?> declared, AnnotationNode at, ClassNode target, Context ctx) {
		ClassNode original = vanillaClass(target.name);
		if (original == null || declared.isEmpty()) return null;
		AnnotationNode asDeclared = new AnnotationNode(injector.desc);
		asDeclared.values = new ArrayList<>(List.of("method", new ArrayList<>(declared)));
		List<MethodNode> methods = PreflightAsm.selectedMethods(asDeclared, original, ctx);
		if (methods.isEmpty()) return null;
		boolean found = true;
		for (MethodNode method : methods) {
			InjectionPoints.Verdict verdict = method.instructions == null ? InjectionPoints.Verdict.UNKNOWN
					: InjectionPoints.pointsIn(List.of(at), List.of(), method, ctx);
			if (verdict == InjectionPoints.Verdict.MISSING) return false;
			found &= verdict == InjectionPoints.Verdict.FOUND;
		}
		return found ? Boolean.TRUE : null;
	}

	/**
	 * Whether a wrapper's handler takes a call on {@code ref}'s owner as Fabric's Mixin passes it: the receiver as that
	 * class first, then the call's arguments (a {@code @WrapOperation}'s with its {@code Operation} after them, which makes
	 * the call on the receiver it was given, {@link HandlerBridges#passesOwnReceiver}).
	 */
	private static boolean takesReceiverAs(MethodNode handler, AnnotationNode injector, MemberRef ref) {
		Type[] own = Type.getArgumentTypes(handler.desc);
		Type[] args = Type.getArgumentTypes(ref.desc());
		if (own.length < args.length + 1 || !own[0].equals(Type.getObjectType(ref.owner()))) return false;
		for (int i = 0; i < args.length; i++) {
			if (!own[i + 1].equals(args[i])) return false;
		}
		if (!injector.desc.equals(MixinNames.WRAP_OPERATION)) return true;
		return own.length > args.length + 1 && own[args.length + 1].getDescriptor().equals(MixinNames.OPERATION)
				&& HandlerBridges.passesOwnReceiver(handler, 0, args.length + 1);
	}

	/**
	 * An injector's {@code INVOKE} and {@code INVOKE_ASSIGN} points picking by ordinal 0 vanilla's one call of a method
	 * Fabricated puts back in each of the target method's places making the platform's extension call instead (SEAM-3):
	 * NeoForge's and Forge's server make it in the creative branch and in the survival one, so ordinal 0 would pick the
	 * creative branch's alone. The ordinal is dropped, and the point picks the call in each, each vanilla's one on its
	 * path; not where the injector allows fewer injections, or vanilla's code makes the call more than once. Returns the
	 * calls, as the mod names them.
	 */
	List<String> putBackOrdinalRepairs(AnnotationNode injector, ClassNode target, Context ctx) {
		if (!VanillaSeams.putsBackCalls(target.name) || Annotations.value(injector, "slice") != null) return List.of();
		List<MethodNode> methods = PreflightAsm.selectedMethods(injector, target, ctx);
		if (methods.size() != 1) return List.of();
		MethodNode method = methods.get(0);
		ClassNode original = vanillaClass(target.name);
		MethodNode before = original == null ? null : PreflightAsm.declared(original, method.name, method.desc);
		List<VanillaSeams.Restored> putBack = VanillaSeams.restoredIn(target).stream().filter(r -> (method.name + method.desc).equals(r.in())).toList();
		List<String> dropped = new ArrayList<>();
		for (AnnotationNode at : Annotations.list(Annotations.value(injector, "at"))) {
			String point = InjectionPoints.pointOf(at);
			if (!point.equals("INVOKE") && !point.equals("INVOKE_ASSIGN") || !Integer.valueOf(0).equals(Annotations.value(at, "ordinal"))
					|| !(Annotations.value(at, "target") instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			int places = ref.fuzzy() || ref.owner() == null || ref.name() == null || ref.desc() == null ? 0
					: VanillaSeams.putBackPlaces(method, ref.owner(), ref.name(), ref.desc());
			if (places < 2 || Annotations.value(injector, "allow") instanceof Integer allow && allow >= 0 && allow < places
					|| putBack.stream().noneMatch(r -> r.owner().equals(ref.owner()) && r.name().equals(ref.name()) && r.desc().equals(ref.desc()))
					// Every call of it here is one put back, and vanilla's code makes one.
					|| PreflightAsm.matching(method, point, ref, -1).size() != places
					|| before != null && PreflightAsm.matching(before, point, ref, -1).size() != 1) continue;
			Annotations.remove(at, "ordinal");
			dropped.add(raw);
		}
		return dropped;
	}

	/**
	 * Reads an injector's points that pick one of a target method's calls, field accesses or constructions by ordinal
	 * against vanilla's version of the method, when there is one (VANILLA-1). NeoForge's patches add and replace such
	 * instructions, so this game build's ordinal-th may not be the one the mod was written for, or there may be fewer.
	 * Vanilla's instructions aligned with this game build's ({@link PatchAlignment}) tell where vanilla's went: where it
	 * is still there, the point's ordinal becomes its ordinal here; where NeoForge's patch put a call of an overload taking
	 * one more argument in its place (its camera passes a roll along that way; where the patch repeats instructions on
	 * both sides of it, found among those whose places the alignments don't agree on), the point is pointed at that call, with
	 * its handler bridged as {@link #insertedArgumentRepairs} does. An {@code @Inject}'s point without an ordinal, which
	 * takes every match, is pinned to vanilla's where vanilla's code has one and the patch adds others on the same path
	 * ({@link #onOnePath}): the hook would run at each, more than once where vanilla runs it once (VANILLA-4: Forge 52's
	 * {@code Gui} constructor reads {@code layers} again to resolve Forge's layers, and Apoli's hook at that read added its
	 * HUD twice). A {@code RETURN} point picked by ordinal, where the patch adds returns and each of vanilla's keeps one
	 * of its own, is pointed at the return in the place of vanilla's (VANILLA-5: Forge's {@code destroyBlock} returns
	 * where the held item's {@code onBlockStartBreak} says so, before vanilla's creative and survival returns, which
	 * Apoli's break action picks); where the patch merges a return of vanilla's with another, a hook there would run on
	 * both paths, and the point stays. A point inside a slice, or a target method vanilla doesn't declare, is left as it
	 * is. Returns a description of each change.
	 */
	List<String> vanillaPointRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx,
			List<MethodNode> bridges) {
		if (Annotations.value(injector, "slice") != null || (mixin.access & Opcodes.ACC_INTERFACE) != 0) return List.of();
		ClassNode original = vanillaClass(target.name);
		List<MethodNode> methods = original == null ? List.of() : PreflightAsm.selectedMethods(injector, target, ctx);
		if (methods.size() != 1) return List.of();
		MethodNode patched = methods.get(0);
		MethodNode before = PreflightAsm.declared(original, patched.name, patched.desc);
		if (before == null) return List.of();
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		PatchAlignment alignment = null;
		List<String> moved = new ArrayList<>();
		for (AnnotationNode at : ats) {
			String point = InjectionPoints.pointOf(at);
			if (point.equals("RETURN") && Annotations.value(at, "ordinal") instanceof Integer ordinal) {
				List<Integer> inVanilla = PreflightAsm.returns(before), here = PreflightAsm.returns(patched);
				// An ordinal below 0 picks every return, as Mixin reads it.
				if (ordinal < 0 || ordinal >= inVanilla.size() || here.size() <= inVanilla.size()) continue;
				if (alignment == null) alignment = PatchAlignment.of(before, patched);
				if (alignment == null) return moved;
				int picked = inVanilla.get(ordinal);
				int place = alignment.placeOf(picked);
				int now = here.indexOf(place);
				if (now < 0 || now == ordinal) continue;
				// Only where no other return of vanilla's is gone or may share that one: a return the patch merges with
				// another runs on its path too. Where the alignments disagree on a return (the same instructions as
				// another's), either answer may be the patch's.
				boolean merged = false;
				for (int other : inVanilla) {
					if (other != picked) merged |= alignment.placeOf(other) == -1 || alignment.mayPlace(other, place);
				}
				if (merged) continue;
				Annotations.put(at, "ordinal", now);
				moved.add("the return with ordinal " + ordinal + " in vanilla's code, which has ordinal " + now + " in this game build, as " + loader
						+ "'s patches add returns before it; the injection point is pointed there");
				continue;
			}
			if (!List.of("INVOKE", "FIELD", "NEW").contains(point) || !(Annotations.value(at, "target") instanceof String raw)) continue;
			int ordinal = InjectionPoints.ordinalOf(at);
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy()) continue;
			int opcode = InjectionPoints.opcodeOf(at);
			List<Integer> inVanilla = PreflightAsm.matching(before, point, ref, opcode);
			List<Integer> here = PreflightAsm.matching(patched, point, ref, opcode);
			boolean every = ordinal < 0;
			if (every ? !injector.desc.equals(MixinNames.INJECT) || inVanilla.size() != 1 || here.size() < 2 : ordinal >= inVanilla.size()) continue;
			if (alignment == null) alignment = PatchAlignment.of(before, patched);
			if (alignment == null) return moved;
			String what = (point.equals("INVOKE") ? "the call " : point.equals("FIELD") ? "the access " : "the construction ") + raw;
			if (every) {
				int place = alignment.placeOf(inVanilla.get(0));
				int now = here.indexOf(place);
				if (now < 0 || !here.stream().allMatch(i -> onOnePath(patched, Math.min(i, place), Math.max(i, place)))) continue;
				Annotations.put(at, "ordinal", now);
				moved.add(what + ", which vanilla's code makes once and this game build " + here.size() + " times on one path, as " + loader
						+ "'s patch adds the others: the hook would run at each; the injection point is pinned to vanilla's (ordinal " + now + " here)");
				continue;
			}
			what += " with ordinal " + ordinal + " in vanilla's code, which ";
			int place = alignment.placeOf(inVanilla.get(ordinal));
			if (place == PatchAlignment.UNCERTAIN) continue;
			if (place >= 0) {
				int now = here.indexOf(place);
				if (now < 0 || now == ordinal) continue;
				Annotations.put(at, "ordinal", now);
				moved.add(what + "has ordinal " + now + " in this game build, as " + loader + "'s patches add or replace others; the injection point is pointed there");
				continue;
			}
			boolean pointOnly = injector.desc.equals(MixinNames.INJECT) || injector.desc.equals(MixinNames.MODIFY_EXPRESSION_VALUE);
			if (!point.equals("INVOKE") || ref.owner() == null || ref.name() == null || ref.desc() == null || !pointOnly && ats.size() != 1
					|| !pointOnly && !MixinNames.WRAP_WITH_CONDITION.contains(injector.desc) && !injector.desc.equals(MixinNames.REDIRECT) && !injector.desc.equals(MixinNames.WRAP_OPERATION)) continue;
			ClassNode owner = gameTarget(ref.owner());
			int vanillaCall = inVanilla.get(ordinal);
			MethodInsnNode replacement = replacingOverload(patched, alignment.replacementOf(vanillaCall), ref, owner);
			if (replacement == null) replacement = uncertainReplacement(patched, alignment.uncertainRegion(vanillaCall), inVanilla, ref, owner);
			if (replacement == null) continue;
			int inserted = insertedArgument(owner, ref.name(), ref.desc(), replacement.desc);
			if (!pointOnly && !bridgeTo(mixin, handler, injector, ref.owner(), ref.desc(), replacement.desc, inserted, replacement.getOpcode() == Opcodes.INVOKESTATIC,
					List.of(patched), bridges)) continue;
			int now = 0;
			for (AbstractInsnNode insn = patched.instructions.getFirst(); insn != replacement; insn = insn.getNext()) {
				if (insn instanceof MethodInsnNode call && call.owner.equals(replacement.owner) && call.name.equals(replacement.name) && call.desc.equals(replacement.desc)) now++;
			}
			Annotations.put(at, "target", "L" + replacement.owner + ";" + replacement.name + replacement.desc);
			Annotations.put(at, "ordinal", now);
			moved.add(what + loader + "'s patch replaced with a call of " + replacement.name + replacement.desc + ", taking one more argument (ordinal " + now
					+ " here); the injection point is pointed there" + (pointOnly ? "" : ", through a bridge handler"));
		}
		return moved;
	}

	/**
	 * Where the two alignments place nothing alike beside vanilla's call (the patch repeats instructions on both sides of
	 * what it put there), the one call of an overload taking one more argument among the patched instructions of the
	 * {@code region} ({@link PatchAlignment#uncertainRegion}), when vanilla's call is the only one of the named method
	 * among its own instructions there ({@code inVanilla}, the indices of its calls) and the patched ones make none of it.
	 * Null otherwise.
	 */
	private static MethodInsnNode uncertainReplacement(MethodNode patched, int[] region, List<Integer> inVanilla, MemberRef ref, ClassNode owner) {
		if (region == null || inVanilla.stream().filter(i -> i >= region[0] && i < region[1]).count() != 1) return null;
		for (int i = region[2]; i < region[3]; i++) {
			if (patched.instructions.get(i) instanceof MethodInsnNode call && call.owner.equals(ref.owner()) && call.name.equals(ref.name())
					&& call.desc.equals(ref.desc())) return null;
		}
		return replacingOverload(patched, new int[] {region[2], region[3]}, ref, owner);
	}

	/**
	 * Whether a pass through {@code method} that runs one of its instructions {@code from} and {@code to} runs the other:
	 * nothing between them jumps, switches, returns or throws, and no jump, switch or exception handler leads into them.
	 */
	private static boolean onOnePath(MethodNode method, int from, int to) {
		Set<LabelNode> entered = PreflightAsm.jumpTargets(method);
		for (int i = from + 1; i < to; i++) {
			AbstractInsnNode insn = method.instructions.get(i);
			int op = insn.getOpcode();
			if (Initialisers.branches(insn) || PreflightAsm.isReturn(op) || op == Opcodes.ATHROW || entered.contains(insn)) return false;
		}
		return true;
	}

	/**
	 * The one call, among the patched instructions {@code range} ({@code [from, to)}, null for none), of an overload of the
	 * named method taking one more argument (as {@code owner}, the named method's class, tells it); null when there is none
	 * or more than one.
	 */
	private static MethodInsnNode replacingOverload(MethodNode patched, int[] range, MemberRef ref, ClassNode owner) {
		if (range == null) return null;
		MethodInsnNode found = null;
		for (int i = range[0]; i < range[1]; i++) {
			if (!(patched.instructions.get(i) instanceof MethodInsnNode call) || !call.owner.equals(ref.owner()) || !call.name.equals(ref.name())
					|| insertedArgument(owner, ref.name(), ref.desc(), call.desc) < 0) continue;
			if (found != null) return null;
			found = call;
		}
		return found;
	}

	/**
	 * Points an {@code @Inject}'s {@code INVOKE} point at a call the platform's patch moved out of the target method into
	 * code the method calls, at the place vanilla's code made the call (VANILLA-1, CORPUS-72). Forge 47's patch of
	 * {@code ServerPlayerGameMode.destroyBlock} takes out vanilla's {@code playerWillDestroy}, {@code removeBlock} and
	 * {@code destroy} calls and calls a {@code removeBlock} of its own in the creative and the survival branch instead,
	 * which reaches {@code playerWillDestroy} through its extensions' {@code onDestroyedByPlayer}. Magna's hook at that call
	 * cancels the break before the survival branch damages the tool; at the calls of the patch's method it would run after
	 * that, and at vanilla's place it runs where it did.
	 *
	 * <p>Vanilla's place is found between calls the two versions each make exactly once, the last before vanilla's call
	 * and the first after it: an alignment of all instructions can't tell it there, as the patch adds statements looking
	 * like vanilla's ({@link #vanillasPlace}). The point moves to that next call when the patched code between the two
	 * does what vanilla's did before its call, and the method still makes the call through methods the patch added or the
	 * platform's code ({@link #movedInto}). Only an {@code @Inject} capturing no locals and taking nothing after its
	 * {@code CallbackInfo} moves: it sees nothing of the call, and the locals there may not be vanilla's. A point with a
	 * {@code shift} stays: after the call, at its old place, is before the call here. Returns a description of each move.
	 */
	List<String> movedCallRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx) {
		if (!injector.desc.equals(MixinNames.INJECT) || Annotations.value(injector, "slice") != null || Annotations.value(injector, "locals") != null
				|| (mixin.access & Opcodes.ACC_INTERFACE) != 0 || !takesOnlyItsCallback(handler)) return List.of();
		ClassNode original = vanillaClass(target.name);
		List<MethodNode> methods = original == null ? List.of() : PreflightAsm.selectedMethods(injector, target, ctx);
		if (methods.size() != 1) return List.of();
		MethodNode patched = methods.get(0);
		MethodNode before = PreflightAsm.declared(original, patched.name, patched.desc);
		if (before == null || before.instructions == null || patched.instructions == null) return List.of();
		List<String> moved = new ArrayList<>();
		for (AnnotationNode at : Annotations.list(Annotations.value(injector, "at"))) {
			String point = InjectionPoints.pointOf(at);
			if (!point.equals("INVOKE") || !(Annotations.value(at, "target") instanceof String raw) || Annotations.value(at, "shift") != null
					|| Annotations.value(at, "by") != null) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy() || ref.owner() == null || ref.name() == null || ref.desc() == null || !PreflightAsm.matching(patched, point, ref, -1).isEmpty()) continue;
			List<Integer> inVanilla = PreflightAsm.matching(before, point, ref, -1);
			int ordinal = InjectionPoints.ordinalOf(at);
			if (ordinal < 0 ? inVanilla.size() != 1 : ordinal >= inVanilla.size()) continue;
			MethodInsnNode next = vanillasPlace(before, patched, inVanilla.get(Math.max(ordinal, 0)));
			Code into = next == null ? null : movedInto(patched, ref, 3);
			// A handler sharing the mixin's state with other injectors moves only with them, as for HOOK-3.
			if (into == null || !independentOf(mixin).contains(handler.name + handler.desc)) continue;
			Annotations.put(at, "target", "L" + next.owner + ";" + next.name + next.desc);
			if (ordinal >= 0) Annotations.put(at, "ordinal", 0);
			moved.add("the call " + raw + " in vanilla's code, which " + loader + "'s patch moved out of this method into "
					+ into.owner().replace('/', '.') + "." + into.method().name + into.method().desc + ", which it calls instead;"
					+ " the injection point is pointed at the place vanilla made it, before the call of "
					+ next.owner.replace('/', '.') + "." + next.name + next.desc + " after it");
		}
		return moved;
	}

	/** Mixin's {@code @ModifyArg}. */
	private static final String MODIFY_ARG = "Lorg/spongepowered/asm/mixin/injection/ModifyArg;";

	/**
	 * Points a {@code @ModifyArg} at a call vanilla's method makes, which the platform's patch makes inside a hook of its
	 * own instead, at the hook's call, and at the hook's parameter the hook passes on as that argument (OVERLOAD-4).
	 * NeoForge 21.1 and Forge 52 drop the selected stack in {@code ServerPlayer.drop(boolean)} through
	 * {@code onPlayerTossEvent(player, stack, true)}, which drops it with {@code player.drop(stack, false, includeName)}
	 * and then fires the toss event; Apoli's hook modifying the stack vanilla's call drops found no call there.
	 * <p>
	 * The value the hook's call gets there must be the one the call inside gets, which an analysis of the stack tells
	 * ({@link OverloadRepairs#pushedBy}):
	 * <ul>
	 * <li>vanilla's call (VANILLA-1), a static one or one the object's class decides (not {@code super}'s or another
	 * {@code invokespecial}, which names one class's code where the hook's call would run an override's), is gone from
	 * the method, and in its place, as an alignment of the two methods' code
	 * tells ({@link PatchAlignment#replacementOf}), the patch calls one static method of the platform's (in its classes),
	 * nowhere else; with the point's ordinal, the call in the place of vanilla's with that ordinal;</li>
	 * <li>that hook makes the call once, on every way through it (no jump, switch, return, throw or try block, and no
	 * label a jump leads to, from its start to the call): the call of vanilla's method, or, on the same object, of the
	 * method it overrides;</li>
	 * <li>the argument is a plain load of one of the hook's parameters, of the argument's type, which the hook loads
	 * nowhere else and never stores into, so the value the handler returns is the one the call gets, and nothing else
	 * of the hook's sees it;</li>
	 * <li>the call's receiver, where it has one, is a parameter of the hook the hook never stores into, which the method
	 * passes {@code this}, as vanilla's call was made on {@code this}.</li>
	 * </ul>
	 * The handler takes the argument alone (Mixin's single argument form), and MixinExtras' {@code @Share} or
	 * {@code @Cancellable} sugar, which don't depend on the code around the point: it stays in the method, at the place
	 * of vanilla's call, and runs before the hook's code ahead of the call (Forge's and NeoForge's capture of the drops).
	 * A point with a slice, a shift or an opcode isn't judged. Returns a description of each move.
	 */
	List<String> hookArgumentRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx) {
		if (!injector.desc.equals(MODIFY_ARG) || Annotations.value(injector, "slice") != null || (mixin.access & Opcodes.ACC_INTERFACE) != 0) return List.of();
		// The single argument form: the argument alone, then sugar that doesn't read the method's locals.
		Type value = Type.getReturnType(handler.desc);
		Type[] params = Type.getArgumentTypes(handler.desc);
		List<Type> args = new ArrayList<>();
		for (int i = 0; i < params.length; i++) {
			boolean sugar = false;
			for (AnnotationNode a : Annotations.ofParameter(handler, i)) {
				if (!a.desc.equals(MixinNames.SHARE) && !a.desc.equals(MixinNames.CANCELLABLE)) return List.of();
				sugar = true;
			}
			if (!sugar) args.add(params[i]);
		}
		if (value.getSort() == Type.VOID || !args.equals(List.of(value))) return List.of();
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		if (ats.size() != 1) return List.of();
		AnnotationNode at = ats.get(0);
		String point = InjectionPoints.pointOf(at);
		if (!point.equals("INVOKE") || !(Annotations.value(at, "target") instanceof String raw) || Annotations.value(at, "shift") != null
				|| Annotations.value(at, "by") != null || Annotations.value(at, "opcode") != null || Annotations.value(at, "slice") != null) return List.of();
		MemberRef ref = MemberRef.parse(ctx.resolve(raw));
		if (ref.fuzzy() || ref.owner() == null || ref.name() == null || ref.desc() == null || ref.name().startsWith("<")) return List.of();
		ClassNode original = vanillaClass(target.name);
		List<MethodNode> methods = original == null ? List.of() : PreflightAsm.selectedMethods(injector, target, ctx);
		if (methods.size() != 1) return List.of();
		MethodNode patched = methods.get(0);
		MethodNode before = PreflightAsm.declared(original, patched.name, patched.desc);
		if (before == null || (before.access & Opcodes.ACC_STATIC) != (patched.access & Opcodes.ACC_STATIC) || !PreflightAsm.matching(patched, point, ref, -1).isEmpty()) {
			return List.of();
		}
		// The argument the handler modifies: by its index, or the call's one argument of its type, as Mixin finds it.
		Type[] callArgs = Type.getArgumentTypes(ref.desc());
		int argument = Annotations.value(injector, "index") instanceof Integer n ? n : -1;
		if (argument < 0) {
			for (int i = 0; i < callArgs.length; i++) {
				if (!callArgs[i].equals(value)) continue;
				if (argument >= 0) return List.of();
				argument = i;
			}
		}
		if (argument < 0 || argument >= callArgs.length || !callArgs[argument].equals(value)) return List.of();
		List<Integer> inVanilla = PreflightAsm.matching(before, point, ref, -1);
		int ordinal = InjectionPoints.ordinalOf(at);
		if (inVanilla.isEmpty() || ordinal >= inVanilla.size()) return List.of();
		PatchAlignment alignment = PatchAlignment.of(before, patched);
		if (alignment == null) return List.of();
		boolean staticCall = before.instructions.get(inVanilla.get(Math.max(ordinal, 0))).getOpcode() == Opcodes.INVOKESTATIC;
		// The hook's call in the place of each of vanilla's calls the point picks.
		MethodInsnNode hook = null;
		int[] passed = null;
		List<MethodInsnNode> placed = new ArrayList<>();
		for (int k = 0; k < inVanilla.size(); k++) {
			if (ordinal >= 0 && k != ordinal) continue;
			int vanillaCall = inVanilla.get(k);
			// A call of the method itself, as the hook's is: super.drop(...) (invokespecial) names the superclass's code, which
			// the hook's call on the object wouldn't run where its class overrides it.
			int made = before.instructions.get(vanillaCall).getOpcode();
			if (staticCall ? made != Opcodes.INVOKESTATIC : made != Opcodes.INVOKEVIRTUAL && made != Opcodes.INVOKEINTERFACE) return List.of();
			int[] range = alignment.replacementOf(vanillaCall);
			if (alignment.placeOf(vanillaCall) != -1 || range == null) return List.of();
			MethodInsnNode found = null;
			for (int i = range[0]; i < range[1]; i++) {
				if (!(patched.instructions.get(i) instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESTATIC || !MixinNames.isPlatformCode(call.owner)) continue;
				if (found != null) return List.of();
				found = call;
			}
			if (found == null || hook != null && !(hook.owner.equals(found.owner) && hook.name.equals(found.name) && hook.desc.equals(found.desc))) return List.of();
			if (hook == null) {
				passed = passedThrough(found, ref, argument, staticCall);
				if (passed == null) return List.of();
			}
			if (!staticCall && !(receivedAsThis(before, vanillaCall, 0) && receivedAsThis(patched, patched.instructions.indexOf(found), passed[1]))) return List.of();
			hook = found;
			placed.add(found);
		}
		// The hook's calls in the method: those, and with no ordinal no others, where the hook would modify more.
		List<MethodInsnNode> hookCalls = new ArrayList<>();
		for (AbstractInsnNode insn : patched.instructions) {
			if (insn instanceof MethodInsnNode call && call.owner.equals(hook.owner) && call.name.equals(hook.name) && call.desc.equals(hook.desc)) hookCalls.add(call);
		}
		if (ordinal < 0 ? !hookCalls.equals(placed) : !hookCalls.contains(placed.get(0))) return List.of();
		Annotations.put(at, "target", "L" + hook.owner + ";" + hook.name + hook.desc);
		if (ordinal >= 0) Annotations.put(at, "ordinal", hookCalls.indexOf(placed.get(0)));
		// The hook's parameter, which Mixin finds by its type too where the hook takes one of that type alone.
		int parameter = passed[0];
		Type[] hookArgs = Type.getArgumentTypes(hook.desc);
		long ofType = java.util.Arrays.stream(hookArgs).filter(value::equals).count();
		if (Annotations.value(injector, "index") != null || ofType != 1) Annotations.put(injector, "index", parameter);
		return List.of("the call " + ctx.resolve(raw) + " in vanilla's code, which " + loader + "'s patch makes inside its hook " + hook.owner.replace('/', '.') + "." + hook.name
				+ hook.desc + ", called in its place, which passes its parameter " + parameter + " on to it unchanged as that argument; the injection point is"
				+ " pointed at the hook's call, at that parameter");
	}

	/**
	 * The parameters of {@code hookCall}'s static hook, the platform's, that it passes on, unchanged, to the one call of
	 * {@code ref} it makes, on every way through it, before anything else of its own could leave it or loop
	 * ({@link #hookArgumentRepairs}): the one it passes as argument {@code argument}, which it loads there alone, and the
	 * call's receiver (-1 for a static call). Null otherwise.
	 */
	private int[] passedThrough(MethodInsnNode hookCall, MemberRef ref, int argument, boolean staticCall) {
		Code code = declaration(hookCall.owner, hookCall.name, hookCall.desc);
		if (code == null || !code.owner().equals(hookCall.owner) || (code.method().access & Opcodes.ACC_STATIC) == 0) return null;
		MethodNode hook = code.method();
		MethodInsnNode made = null;
		for (AbstractInsnNode insn : hook.instructions) {
			if (!(insn instanceof MethodInsnNode call) || !call.name.equals(ref.name()) || !call.desc.equals(ref.desc())) continue;
			// Vanilla's method, or, called on the same object, the one it overrides.
			boolean same = staticCall ? call.getOpcode() == Opcodes.INVOKESTATIC && call.owner.equals(ref.owner())
					: (call.getOpcode() == Opcodes.INVOKEVIRTUAL || call.getOpcode() == Opcodes.INVOKEINTERFACE)
							&& (call.owner.equals(ref.owner()) || isSubtype(ref.owner(), call.owner));
			if (!same) continue;
			if (made != null) return null;
			made = call;
		}
		if (made == null || !reachedOnEveryWay(hook, made)) return null;
		AbstractInsnNode[] pushed = OverloadRepairs.pushedBy(hook, made);
		if (pushed == null) return null;
		int offset = staticCall ? 0 : 1;
		int parameter = parameterLoaded(hook, pushed[offset + argument], Type.getArgumentTypes(ref.desc())[argument]);
		int receiver = staticCall ? -1 : parameterLoaded(hook, pushed[0], null);
		if (parameter < 0 || !staticCall && receiver < 0) return null;
		int slot = HandlerBridges.slotOf(hook, List.of(Type.getArgumentTypes(hook.desc)), parameter);
		int loads = 0;
		for (AbstractInsnNode insn : hook.instructions) {
			if (insn instanceof VarInsnNode var && var.var == slot && var.getOpcode() >= Opcodes.ILOAD && var.getOpcode() <= Opcodes.ALOAD) loads++;
		}
		return loads == 1 ? new int[] {parameter, receiver} : null;
	}

	/**
	 * The index of the parameter of {@code method} that {@code insn} loads, of {@code type} (any reference type where it
	 * is null), when the method never stores into its slot; -1 otherwise.
	 */
	private static int parameterLoaded(MethodNode method, AbstractInsnNode insn, Type type) {
		if (!(insn instanceof VarInsnNode load) || load.getOpcode() < Opcodes.ILOAD || load.getOpcode() > Opcodes.ALOAD) return -1;
		Type[] params = Type.getArgumentTypes(method.desc);
		int slot = (method.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
		for (int i = 0; i < params.length; i++) {
			if (slot == load.var) {
				boolean fits = type == null ? params[i].getSort() == Type.OBJECT || params[i].getSort() == Type.ARRAY : params[i].equals(type);
				return fits && load.getOpcode() == params[i].getOpcode(Opcodes.ILOAD) && !storesInto(method, slot) ? i : -1;
			}
			slot += params[i].getSize();
		}
		return -1;
	}

	/** Whether {@code method} stores into, or increments, the local variable in {@code slot}. */
	private static boolean storesInto(MethodNode method, int slot) {
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof VarInsnNode var && var.getOpcode() >= Opcodes.ISTORE && var.getOpcode() <= Opcodes.ASTORE && var.var == slot
					|| insn instanceof org.objectweb.asm.tree.IincInsnNode inc && inc.var == slot) return true;
		}
		return false;
	}

	/**
	 * Whether every way through {@code method} reaches {@code call}, once: from the method's start to it, no instruction
	 * jumps, switches, returns or throws, no try block starts, and no label one of the method's jumps, switches or
	 * exception handlers leads to comes.
	 */
	private static boolean reachedOnEveryWay(MethodNode method, MethodInsnNode call) {
		Set<LabelNode> entered = PreflightAsm.jumpTargets(method);
		Set<LabelNode> tried = new HashSet<>();
		for (TryCatchBlockNode block : method.tryCatchBlocks) tried.add(block.start);
		for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn instanceof LabelNode label && (entered.contains(label) || tried.contains(label))) return false;
			if (insn == call) return true;
			int op = insn.getOpcode();
			if (insn instanceof JumpInsnNode || insn instanceof TableSwitchInsnNode || insn instanceof LookupSwitchInsnNode
					|| PreflightAsm.isReturn(op) || op == Opcodes.ATHROW) return false;
		}
		return false;
	}

	/**
	 * Whether the value {@code method}'s call at {@code index} takes as its {@code value}-th (its receiver first) is
	 * {@code this}: a load of slot 0 in an instance method that never stores into it ({@link OverloadRepairs#pushedBy}).
	 */
	private static boolean receivedAsThis(MethodNode method, int index, int value) {
		if ((method.access & Opcodes.ACC_STATIC) != 0 || !(method.instructions.get(index) instanceof MethodInsnNode call) || storesInto(method, 0)) return false;
		AbstractInsnNode[] pushed = OverloadRepairs.pushedBy(method, call);
		return pushed != null && value < pushed.length && pushed[value] instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD && load.var == 0;
	}

	/** Whether an injector handler's last parameter is its {@code CallbackInfo}: it takes no locals or sugar after it. */
	private static boolean takesOnlyItsCallback(MethodNode handler) {
		Type[] params = Type.getArgumentTypes(handler.desc);
		return params.length > 0 && PreflightAsm.isCallbackInfo(params[params.length - 1]);
	}

	/**
	 * The call, in the patched method, that vanilla's call at {@code vanillaCall} came right before: the first call after
	 * it that both versions make exactly once (the same owner, name and descriptor, as Mixin matches calls), when the last
	 * such call before it (or the method's start) is before that one in the patched method too, and the patched
	 * instructions between those two do what vanilla's did between the last and its call, apart from loading values
	 * ({@link #steps}). Null otherwise.
	 */
	private static MethodInsnNode vanillasPlace(MethodNode vanilla, MethodNode patched, int vanillaCall) {
		Map<String, Integer> inVanilla = madeOnce(vanilla);
		Map<String, Integer> inPatched = madeOnce(patched);
		int vanillaFrom = 0;
		int patchedFrom = 0;
		for (int i = vanillaCall - 1; i >= 0; i--) {
			Integer there = anchor(vanilla.instructions.get(i), i, inVanilla, inPatched);
			if (there != null) {
				vanillaFrom = i + 1;
				patchedFrom = there + 1;
				break;
			}
		}
		for (int i = vanillaCall + 1; i < vanilla.instructions.size(); i++) {
			Integer there = anchor(vanilla.instructions.get(i), i, inVanilla, inPatched);
			if (there == null) continue;
			if (there < patchedFrom) return null;
			List<String> was = steps(vanilla, vanillaFrom, vanillaCall);
			return was != null && was.equals(steps(patched, patchedFrom, there)) ? (MethodInsnNode) patched.instructions.get(there) : null;
		}
		return null;
	}

	/** The index in the patched method of a call vanilla's makes once at {@code index}, when the patched one makes it once too. */
	private static Integer anchor(AbstractInsnNode insn, int index, Map<String, Integer> inVanilla, Map<String, Integer> inPatched) {
		if (!(insn instanceof MethodInsnNode call)) return null;
		String key = call.owner + "." + call.name + call.desc;
		Integer own = inVanilla.get(key);
		return own != null && own == index ? inPatched.get(key) : null;
	}

	/** The calls a method makes exactly once (owner, name and descriptor), with their indices. */
	private static Map<String, Integer> madeOnce(MethodNode method) {
		Map<String, Integer> once = new HashMap<>();
		Set<String> more = new HashSet<>();
		for (int i = 0; i < method.instructions.size(); i++) {
			if (!(method.instructions.get(i) instanceof MethodInsnNode call)) continue;
			String key = call.owner + "." + call.name + call.desc;
			if (more.contains(key) || once.put(key, i) != null) {
				once.remove(key);
				more.add(key);
			}
		}
		return once;
	}

	/**
	 * What a method's instructions {@code [from, to)} do apart from loading values (constants, locals and fields): their
	 * other instructions in order, as {@link PatchAlignment#token}s, a jump's with the number of those before its label.
	 * Null when a jump leaves the part or one from elsewhere enters it, or the part holds a switch or a try block's start,
	 * end or handler: control flow two versions would have to be compared on further.
	 */
	private static List<String> steps(MethodNode method, int from, int to) {
		Map<LabelNode, Integer> labels = new HashMap<>();
		List<AbstractInsnNode> steps = new ArrayList<>();
		for (int i = from; i < to; i++) {
			AbstractInsnNode insn = method.instructions.get(i);
			if (insn instanceof LabelNode label) labels.put(label, steps.size());
			else if (insn instanceof TableSwitchInsnNode || insn instanceof LookupSwitchInsnNode) return null;
			else if (insn.getOpcode() >= 0 && !loadsValue(insn)) steps.add(insn);
		}
		if (method.tryCatchBlocks != null) {
			for (TryCatchBlockNode block : method.tryCatchBlocks) {
				if (labels.containsKey(block.start) || labels.containsKey(block.end) || labels.containsKey(block.handler)) return null;
			}
		}
		for (int i = 0; i < method.instructions.size(); i++) {
			AbstractInsnNode insn = method.instructions.get(i);
			boolean inside = i >= from && i < to;
			if (insn instanceof JumpInsnNode jump && labels.containsKey(jump.label) != inside) return null;
			if (insn instanceof TableSwitchInsnNode table && (labels.containsKey(table.dflt) || table.labels.stream().anyMatch(labels::containsKey))) return null;
			if (insn instanceof LookupSwitchInsnNode lookup && (labels.containsKey(lookup.dflt) || lookup.labels.stream().anyMatch(labels::containsKey))) return null;
		}
		List<String> tokens = new ArrayList<>();
		for (AbstractInsnNode step : steps) {
			tokens.add(PatchAlignment.token(step) + (step instanceof JumpInsnNode jump ? " to " + labels.get(jump.label) : ""));
		}
		return tokens;
	}

	/** Whether an instruction only pushes a value: a constant, a local variable or a field. */
	private static boolean loadsValue(AbstractInsnNode insn) {
		int op = insn.getOpcode();
		return op >= Opcodes.ACONST_NULL && op <= Opcodes.ALOAD || op == Opcodes.GETFIELD || op == Opcodes.GETSTATIC;
	}

	/**
	 * The first method {@code method} calls through which it still makes the named call: one the platform's patch added
	 * to a class of the game (vanilla's version of the class doesn't declare it) or the platform's own code (its classes,
	 * or a default method of its extensions), which makes the call itself or, to {@code depth} methods in all, through
	 * more such methods. Null when none does: the patch took the call out, rather than moving it.
	 */
	private Code movedInto(MethodNode method, MemberRef ref, int depth) {
		for (AbstractInsnNode insn : method.instructions) {
			if (!(insn instanceof MethodInsnNode call)) continue;
			Code code = declaration(call.owner, call.name, call.desc);
			if (code != null && isPatchCode(code) && reaches(code, ref, depth - 1, new HashSet<>())) return code;
		}
		return null;
	}

	/** Whether the code makes the named call, itself or through {@code depth} more methods of the patch's ({@link #movedInto}). */
	private boolean reaches(Code code, MemberRef ref, int depth, Set<String> seen) {
		if (!seen.add(code.owner() + "." + code.method().name + code.method().desc)) return false;
		for (AbstractInsnNode insn : code.method().instructions) {
			if (!(insn instanceof MethodInsnNode call)) continue;
			if (call.owner.equals(ref.owner()) && call.name.equals(ref.name()) && call.desc.equals(ref.desc())) return true;
			if (depth <= 0) continue;
			Code next = declaration(call.owner, call.name, call.desc);
			if (next != null && isPatchCode(next) && reaches(next, ref, depth - 1, seen)) return true;
		}
		return false;
	}

	/** Whether code is the platform's: in its own classes, or a method its patch added to a class of the game. */
	private boolean isPatchCode(Code code) {
		if (MixinNames.isPlatformCode(code.owner())) return true;
		ClassNode original = vanillaClass(code.owner());
		return original != null && PreflightAsm.declared(original, code.method().name, code.method().desc) == null;
	}

	/**
	 * Gives a {@code @Redirect}, {@code @WrapWithCondition} or {@code @WrapOperation} handler written for a call of
	 * {@code owner} with {@code desc} a bridge taking the {@code overload} its point moves to, which has one more argument
	 * at {@code inserted}, and moves the injector onto it. Whether it could.
	 */
	private static boolean bridgeTo(ClassNode mixin, MethodNode handler, AnnotationNode injector, String owner, String desc, String overload, int inserted,
			boolean staticCall, List<MethodNode> methods, List<MethodNode> bridges) {
		return bridgeTo(mixin, handler, injector, owner, desc, overload, inserted, 1, staticCall, methods, bridges);
	}

	/** {@link #bridgeTo}, for an overload taking {@code count} more arguments from {@code inserted} on. */
	private static boolean bridgeTo(ClassNode mixin, MethodNode handler, AnnotationNode injector, String owner, String desc, String overload, int inserted,
			int count, boolean staticCall, List<MethodNode> methods, List<MethodNode> bridges) {
		Type ownerType = Type.getObjectType(owner);
		List<Type> method = sharedParameters(methods);
		MethodNode bridge = injector.desc.equals(MixinNames.WRAP_OPERATION)
				? HandlerBridges.wrapperBridge(mixin, handler, ownerType, desc, overload, inserted, count, staticCall, method, bridges)
				: HandlerBridges.callBridge(mixin, handler, ownerType, desc, overload, inserted, count, staticCall,
						MixinNames.WRAP_WITH_CONDITION.contains(injector.desc) ? Type.BOOLEAN_TYPE : Type.getReturnType(desc), method, bridges);
		if (bridge == null) return false;
		HandlerBridges.moveInjector(handler, injector, bridge);
		bridges.add(bridge);
		return true;
	}

	/** The parameters {@code methods} start with alike: those a handler may take after its own, as Mixin passes them. */
	static List<Type> sharedParameters(List<MethodNode> methods) {
		List<Type> shared = null;
		for (MethodNode method : methods) {
			List<Type> params = List.of(Type.getArgumentTypes(method.desc));
			if (shared == null) {
				shared = params;
				continue;
			}
			int same = 0;
			while (same < shared.size() && same < params.size() && shared.get(same).equals(params.get(same))) same++;
			shared = shared.subList(0, same);
		}
		return shared == null ? List.of() : shared;
	}

	/**
	 * Points the {@code INVOKE} and {@code NEW} points of an injector at the call this game build makes instead of the named one, an
	 * overload of the same method taking one more argument (NeoForge's patches pass an item stack or a level along that
	 * way), when the named call is in none of the methods the injector targets and that overload is the only one there.
	 * The handler of an {@code @Inject} or {@code @ModifyExpressionValue} doesn't see the arguments, so its {@code INVOKE}
	 * point also moves to an overload that takes more after them ({@link #appendsArguments}). One of a
	 * {@code @Redirect}, {@code @WrapWithCondition} or {@code @WrapOperation} that takes exactly the call's receiver and
	 * arguments (one {@code @Coerce}d to a supertype too) and the {@code Operation}, then any of the target method's first
	 * parameters and MixinExtras sugar ({@link HandlerBridges#sugarAfter}), gets a bridge taking the overload's, which calls
	 * it without the added argument; for {@code @WrapOperation}, with an {@code Operation} that puts it back when the
	 * original call is made.
	 * A {@code NEW} point selecting a constructor by its descriptor is moved the same way, to a constructor taking one
	 * more argument (NeoForge's patches give records components): its handlers take the arguments, as a static call's.
	 * An {@code INVOKE} point at a call NeoForge's patches moved into a hook of theirs moves to the hook's call
	 * ({@link #hookCall}), its handlers bridged the same way when the hook takes one more argument, when the handler shares
	 * none of the mixin's own state with its other injectors ({@link MixinPreflight#independentHandlers}) (HOOK-2).
	 * Returns a description of each move.
	 */
	List<String> insertedArgumentRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, List<?> declared, ClassNode target, Context ctx,
			List<MethodNode> bridges) {
		// Only the point moves for these: their handlers don't see the call's arguments.
		boolean pointOnly = injector.desc.equals(MixinNames.INJECT) || injector.desc.equals(MixinNames.MODIFY_EXPRESSION_VALUE);
		boolean condition = MixinNames.WRAP_WITH_CONDITION.contains(injector.desc);
		if (!pointOnly && !condition && !injector.desc.equals(MixinNames.REDIRECT) && !injector.desc.equals(MixinNames.WRAP_OPERATION) || Annotations.value(injector, "slice") != null
				|| (mixin.access & Opcodes.ACC_INTERFACE) != 0) return List.of();
		// A bare wildcard's handler sees no call either: Mixin scans every method for its points (WILD-1).
		List<MethodNode> wildcard = pointOnly ? PreflightAsm.wildcardMethods(injector, handler, target, ctx) : null;
		List<MethodNode> methods = wildcard != null ? wildcard : PreflightAsm.selectedMethods(injector, target, ctx);
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		// A wrapper's other points, each finding nothing here or in vanilla's methods, are for other platforms' code (SpruceUI
		// names NeoForge's drawScreen beside vanilla's call): it is the one left (SCREEN-3).
		if (!pointOnly && ats.size() > 1) ats = livePoints(ats, methods, target, ctx);
		if (methods.isEmpty() || !pointOnly && ats.size() != 1) return List.of();
		List<String> moved = new ArrayList<>();
		for (AnnotationNode at : ats) {
			String point = InjectionPoints.pointOf(at);
			boolean constructor = point.equals("NEW");
			if (!point.equals("INVOKE") && !constructor || !(Annotations.value(at, "target") instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			// A call is owner.name(desc). A constructor is selected by a descriptor (args)Lowner; which its handlers are written
			// for as for a static factory: compared, and bridged, in that form.
			String owner;
			String name;
			String desc;
			if (constructor) {
				desc = ref.methodDescOr(null);
				if (desc == null || Type.getReturnType(desc).getSort() != Type.OBJECT || condition) continue;
				owner = Type.getReturnType(desc).getInternalName();
				name = "<init>";
			} else {
				if (ref.fuzzy() || ref.owner() == null || ref.name() == null || ref.desc() == null) continue;
				owner = ref.owner();
				name = ref.name();
				desc = ref.desc();
			}
			Overloads found = overloadsCalled(methods, owner, name, desc, constructor, pointOnly);
			String overload = found.overload();
			boolean named = found.named();
			boolean ambiguous = found.ambiguous();
			boolean staticCall = found.staticCall();
			int calls = found.calls();
			// The call itself, when it is in none of the methods: in a hook of NeoForge's that took it over, a static method
			// taking its receiver first, handled as a static call of that.
			MethodInsnNode hook = named || overload != null || constructor ? null : hookCall(methods, owner, name, desc, pointOnly);
			// The call of a method NeoForge deprecated, made to its replacement instead (DEPRECATED-1).
			MethodInsnNode replacement = named || overload != null || constructor || hook != null ? null : replacementCall(methods, owner, name, desc);
			// An @Inject sees none of the call's arguments: any call of NeoForge's that leads to it will do (HOOK-3).
			MethodInsnNode leading = hook != null || replacement != null || named || overload != null || constructor || !injector.desc.equals(MixinNames.INJECT) ? null
					: callLeadingTo(methods, owner, name, desc);
			// A handler sharing the mixin's state with others moves only with them, all to the same hook (SCREEN-3): jGui
			// (ReplayMod) keeps the screen's graphics from a @ModifyArg at the same call for its @Inject after it, which stays.
			String key = handler.name + handler.desc;
			if ((hook != null || leading != null) && !independentOf(mixin).contains(key) && !(hook != null && movingTogether(mixin, target, ctx).contains(key))) continue;
			if (leading != null) {
				int ordinal = InjectionPoints.ordinalOf(at);
				if (ordinal >= countCalls(methods, leading)) continue;
				Annotations.put(at, "target", "L" + leading.owner + ";" + leading.name + leading.desc);
				moved.add(owner.replace('/', '.') + "." + name + desc + ", which this game build makes through " + loader + "'s "
						+ leading.owner.replace('/', '.') + "." + leading.name + leading.desc + "; the injection point is pointed at that call");
				continue;
			}
			String callOwner = owner;
			String callName = name;
			String original = desc;
			// A hook taking the receiver as the subtype it makes the call on (LAMBDA-14).
			String narrowed = null;
			if (hook != null) {
				callOwner = hook.owner;
				callName = hook.name;
				boolean instance = !isStaticCall(owner, name, desc);
				String receiver = instance ? Type.getArgumentTypes(hook.desc)[0].getInternalName() : owner;
				if (instance && !receiver.equals(owner) && isSubtype(receiver, owner)) narrowed = receiver;
				desc = staticForm(narrowed != null ? narrowed : owner, desc, !instance);
				overload = hook.desc;
				staticCall = true;
				calls = countCalls(methods, hook);
			} else if (replacement != null) {
				callOwner = replacement.owner;
				callName = replacement.name;
				overload = replacement.desc;
				staticCall = replacement.getOpcode() == Opcodes.INVOKESTATIC;
				calls = countCalls(methods, replacement);
			}
			int ordinal = InjectionPoints.ordinalOf(at);
			if (named || overload == null || ambiguous || ordinal >= calls) continue;
			int[] block = hook != null ? insertedArguments(desc, overload) : null;
			int inserted = hook != null ? (block == null ? -1 : block[0])
					: replacement == null && !constructor ? insertedArgumentOf(owner, name, desc, overload) : insertedArgument(desc, overload);
			int count = block == null ? 1 : block[1];
			// A wrapper's handler takes the receiver as the call's class, which Mixin takes for the subtype as it coerces it, where
			// the original call it makes is made on that receiver, and where vanilla's methods make the call the mod names (VANILLA-1).
			// Not a redirect's: it replaces the hook's call, with the platform's events around it, and makes the call itself, on any
			// value of the call's class, which the hook can't take (narrowedHookHint says so).
			boolean coerced = false;
			if (narrowed != null && !pointOnly) {
				if (injector.desc.equals(MixinNames.REDIRECT) || !Boolean.TRUE.equals(vanillaMakes(injector, declared, at, target, ctx))
						|| !takesReceiverAs(handler, injector, new MemberRef(owner, name, original, false))) continue;
				coerced = HandlerBridges.coerceParameter(handler, 0);
			}
			// A hook taking just the call's receiver and arguments takes the handlers as they are.
			if (!pointOnly && !overload.equals(desc) && !bridgeTo(mixin, handler, injector, narrowed != null ? narrowed : owner, desc, overload, inserted, count,
					staticCall, methods, bridges)) {
				if (coerced) HandlerBridges.dropCoerce(handler, 0);
				continue;
			}
			Annotations.put(at, "target", constructor ? overload : "L" + callOwner + ";" + callName + overload);
			String via = (pointOnly || overload.equals(desc) ? "" : ", through a bridge handler")
					+ (narrowed != null && !pointOnly ? ", which takes the receiver as it is written for, a " + owner.replace('/', '.') : "");
			if (hook != null) {
				// A redirect replaces the hook's call, and with it the platform's events around the call: where its handler makes
				// the call itself, it makes it through the hook, which fires them, as the platform's code does (CORPUS-61).
				boolean routed = injector.desc.equals(MixinNames.REDIRECT) && overload.equals(desc) && routeThroughHook(handler, owner, name, original, hook) > 0;
				moved.add(owner.replace('/', '.') + "." + name + original + ", which this game build makes inside " + loader + "'s hook "
						+ callOwner.replace('/', '.') + "." + callName + overload + "; the injection point is pointed at the hook's call" + via
						+ (routed ? ", and the handler's own call of it goes through the hook, so " + loader + "'s events around it still fire" : ""));
				continue;
			}
			if (replacement != null) {
				moved.add(owner.replace('/', '.') + "." + name + desc + ", which " + loader + " deprecated: this game build calls its replacement "
						+ callOwner.replace('/', '.') + "." + callName + overload + " instead; the injection point is pointed there" + via);
				continue;
			}
			int extra = Type.getArgumentTypes(overload).length - Type.getArgumentTypes(desc).length;
			String more = extra == 1 ? "one more argument" : extra + " more arguments";
			moved.add(constructor
					? "new " + owner.replace('/', '.') + desc.substring(0, desc.indexOf(')') + 1) + ", which this game build makes with a constructor taking " + more + ", "
							+ overload + "; the injection point is pointed there" + via
					: owner.replace('/', '.') + "." + name + desc + ", which this game build makes through an overload with " + more + ", " + name + overload
							+ "; the injection point is pointed there" + via);
		}
		return moved;
	}

	/**
	 * The points of {@code ats} that find something in {@code methods} or in vanilla's version of them (VANILLA-1): each
	 * other finds nothing on Fabric either, being for another platform's code or another version's. All of them where
	 * vanilla's class isn't at hand, or a point can't be told.
	 */
	private List<AnnotationNode> livePoints(List<AnnotationNode> ats, List<MethodNode> methods, ClassNode target, Context ctx) {
		ClassNode original = vanillaClass(target.name);
		if (original == null) return ats;
		List<MethodNode> vanillas = new ArrayList<>();
		for (MethodNode method : methods) {
			MethodNode was = PreflightAsm.declared(original, method.name, method.desc);
			if (was != null) vanillas.add(was);
		}
		if (vanillas.size() != methods.size()) return ats;
		List<MethodNode> both = new ArrayList<>(methods);
		both.addAll(vanillas);
		List<AnnotationNode> live = new ArrayList<>();
		for (AnnotationNode at : ats) {
			if (both.stream().anyMatch(m -> InjectionPoints.pointsIn(List.of(at), List.of(), m, ctx) != InjectionPoints.Verdict.MISSING)) live.add(at);
		}
		return live;
	}

	/**
	 * The handlers (name and descriptor) of a mixin's injectors that keep state together and all move to the hook of one
	 * call the platform's patch moved into a hook of its own ({@link #hookCall}, HOOK-2): every other member of a
	 * handler's state group ({@link MixinPreflight#stateGroups}) has its points at that call, in the same methods, and the
	 * hook takes it as it takes the handler (an {@code @Inject} or {@code @ModifyExpressionValue}, which see none of the
	 * call's values, or a wrapper the hook takes as it is or through a bridge), or applies nowhere, on vanilla's classes
	 * as on this build's ({@link #appliesNowhere}: a mod's alternative for code no build here has). The state they keep
	 * is then kept around the hook's call as around vanilla's. A member of a {@code @Group} moves only as the group's one
	 * alternative that applies ({@link #soleAlternative}), as the repairs take it. Essential's draw screen event records
	 * the screen's arguments with a condition on the screen's render call and posts its event after the call, which
	 * NeoForge and Forge 47 to 64 make inside their {@code drawScreen} hook (its OptiFine alternatives find nothing).
	 * Decided once for the mixin, as its first handler reaches this, before any moves.
	 */
	private Set<String> movingTogether(ClassNode mixin, ClassNode target, Context ctx) {
		if (togetherMixin == mixin && target.name.equals(togetherTarget)) return together;
		Map<String, String> hooked = new HashMap<>();
		Set<String> dead = new HashSet<>();
		ClassNode original = vanillaClass(target.name);
		for (MethodNode handler : mixin.methods) {
			AnnotationNode injector = Annotations.all(handler).stream().filter(a -> PreflightAsm.isInjector(a.desc)).findFirst().orElse(null);
			if (injector == null) continue;
			String key = handler.name + handler.desc;
			if (original != null && appliesNowhere(injector, original, ctx) && appliesNowhere(injector, target, ctx)) {
				dead.add(key);
				continue;
			}
			boolean grouped = PreflightAsm.hasAlternatives(mixin, handler);
			String call = grouped && !soleAlternative(mixin, handler, injector, List.of(target), ctx) ? null : hookedCall(handler, injector, target, ctx);
			if (call != null) hooked.put(key, call);
		}
		Map<String, MixinPreflight.StateGroup> groups = stateGroupsOf(mixin);
		Set<String> moving = new HashSet<>();
		hooked.forEach((key, call) -> {
			MixinPreflight.StateGroup group = groups.get(key);
			if (group != null && group.handlers().stream().allMatch(other -> other.equals(key) || dead.contains(other) || call.equals(hooked.get(other)))) {
				moving.add(key);
			}
		});
		togetherMixin = mixin;
		togetherTarget = target.name;
		together = moving;
		return moving;
	}

	/**
	 * The call an injector's {@code INVOKE} points name (each the same one), with the methods it selects, where that call is
	 * in none of them but inside the platform's hook there ({@link #hookCall}), which takes the injector as it is or, for a
	 * wrapper, through a bridge for one more argument, at a call the hook's ordinal picks; null otherwise. A hook taking
	 * the receiver as a subtype isn't taken here.
	 */
	private String hookedCall(MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx) {
		boolean pointOnly = injector.desc.equals(MixinNames.INJECT) || injector.desc.equals(MixinNames.MODIFY_EXPRESSION_VALUE);
		boolean wrapper = injector.desc.equals(MixinNames.REDIRECT) || injector.desc.equals(MixinNames.WRAP_OPERATION)
				|| MixinNames.WRAP_WITH_CONDITION.contains(injector.desc);
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		List<MethodNode> methods = PreflightAsm.selectedMethods(injector, target, ctx);
		if (!pointOnly && !wrapper || Annotations.value(injector, "slice") != null || ats.isEmpty() || wrapper && ats.size() != 1 || methods.isEmpty()) return null;
		String named = null;
		for (AnnotationNode at : ats) {
			if (!InjectionPoints.pointOf(at).equals("INVOKE") || !(Annotations.value(at, "target") instanceof String raw)) return null;
			String resolved = ctx.resolve(raw);
			if (named != null && !named.equals(resolved)) return null;
			named = resolved;
		}
		MemberRef ref = MemberRef.parse(named);
		if (ref.fuzzy() || ref.owner() == null || ref.name() == null || ref.desc() == null) return null;
		Overloads found = overloadsCalled(methods, ref.owner(), ref.name(), ref.desc(), false, pointOnly);
		MethodInsnNode hook = found.named() || found.overload() != null ? null : hookCall(methods, ref.owner(), ref.name(), ref.desc(), pointOnly);
		if (hook == null) return null;
		boolean isStatic = isStaticCall(ref.owner(), ref.name(), ref.desc());
		if (!isStatic && !Type.getArgumentTypes(hook.desc)[0].getInternalName().equals(ref.owner())) return null;
		for (AnnotationNode at : ats) {
			if (InjectionPoints.ordinalOf(at) >= countCalls(methods, hook)) return null;
		}
		String form = staticForm(ref.owner(), ref.desc(), isStatic);
		if (wrapper && !hook.desc.equals(form)) {
			int inserted = insertedArgument(form, hook.desc);
			List<Type> shared = sharedParameters(methods);
			if (inserted < 0 || !(injector.desc.equals(MixinNames.WRAP_OPERATION)
					? HandlerBridges.sugarAfter(handler, withOperation(HandlerBridges.callParameters(Type.getObjectType(ref.owner()), form, true)), shared) != null
					: bridgeable(handler, injector, ref.owner(), form, inserted, true, shared))) return null;
		}
		StringBuilder in = new StringBuilder(named);
		for (MethodNode method : methods) in.append(' ').append(method.name).append(method.desc);
		return in.toString();
	}

	private static List<Type> withOperation(List<Type> params) {
		params.add(Type.getType(MixinNames.OPERATION));
		return params;
	}

	/**
	 * What {@code methods} make of calls of {@code owner.name}: whether one is the named call ({@code desc}), and the
	 * overload taking one more argument ({@link #insertedArgumentOf}) they make instead, how often, and whether more than
	 * one such overload is made. With {@code pointOnly}, any overload taking more after the call's arguments
	 * ({@link #appendsArguments}) counts. A constructor's calls are compared in the form {@code (args)Lowner;}.
	 */
	private Overloads overloadsCalled(List<MethodNode> methods, String owner, String name, String desc, boolean constructor, boolean pointOnly) {
		String overload = null;
		boolean named = false;
		boolean ambiguous = false;
		boolean staticCall = constructor;
		int calls = 0;
		for (MethodNode method : methods) {
			for (AbstractInsnNode insn : method.instructions) {
				if (!(insn instanceof MethodInsnNode call) || !call.owner.equals(owner) || !call.name.equals(name)) continue;
				String callDesc = constructor ? call.desc.substring(0, call.desc.indexOf(')') + 1) + "L" + owner + ";" : call.desc;
				if (callDesc.equals(desc)) {
					named = true;
				} else if ((constructor ? insertedArgument(desc, callDesc) : insertedArgumentOf(owner, name, desc, callDesc)) >= 0
						|| pointOnly && !constructor && appendsArguments(desc, callDesc)) {
					ambiguous |= overload != null && !overload.equals(callDesc);
					overload = callDesc;
					staticCall |= call.getOpcode() == Opcodes.INVOKESTATIC;
					calls++;
				}
			}
		}
		return new Overloads(named, overload, ambiguous, staticCall, calls);
	}

	/** What {@link #overloadsCalled} found. */
	private record Overloads(boolean named, String overload, boolean ambiguous, boolean staticCall, int calls) {
	}

	/**
	 * {@link #insertedArgument(ClassNode, String, String, String)} for {@code owner}'s method, the class read only where
	 * the types can't tell: {@code Language.loadFromJson(stream, output)}, which NeoForge passes on to an overload taking
	 * a second {@code BiConsumer}, for components.
	 */
	private int insertedArgumentOf(String owner, String name, String desc, String overload) {
		int byTypes = insertedArgument(desc, overload);
		if (byTypes >= 0 || Type.getArgumentTypes(overload).length != Type.getArgumentTypes(desc).length + 1
				|| !Type.getReturnType(desc).equals(Type.getReturnType(overload))) return byTypes;
		return insertedArgument(gameTarget(owner), name, desc, overload);
	}

	/**
	 * Copies of the injector's points as {@link #insertedArgumentRepairs} would point them in {@code method}: each
	 * {@code INVOKE} point at a call {@code method} makes only through one overload taking one more argument, at that
	 * overload's call, when the handler can be bridged to it. {@link OverloadRepairs#overloadRepairs} moves an injector to
	 * an overload in which its points are found so; the later step makes the move and the bridge. Null when a point can't
	 * be pointed so, or none needs to be.
	 */
	List<AnnotationNode> pointsThroughOverloads(ClassNode mixin, MethodNode handler, AnnotationNode injector, MethodNode method, Context ctx) {
		boolean pointOnly = injector.desc.equals(MixinNames.INJECT) || injector.desc.equals(MixinNames.MODIFY_EXPRESSION_VALUE);
		boolean condition = MixinNames.WRAP_WITH_CONDITION.contains(injector.desc);
		if (!pointOnly && !condition && !injector.desc.equals(MixinNames.REDIRECT) && !injector.desc.equals(MixinNames.WRAP_OPERATION) || Annotations.value(injector, "slice") != null
				|| (mixin.access & Opcodes.ACC_INTERFACE) != 0) return null;
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		if (ats.isEmpty() || !pointOnly && ats.size() != 1) return null;
		List<AnnotationNode> copies = new ArrayList<>();
		boolean pointed = false;
		for (AnnotationNode at : ats) {
			AnnotationNode copy = new AnnotationNode(at.desc);
			at.accept(copy);
			copies.add(copy);
			String point = InjectionPoints.pointOf(at);
			if (!point.equals("INVOKE") || !(Annotations.value(at, "target") instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy() || ref.owner() == null || ref.name() == null || ref.desc() == null) continue;
			Overloads found = overloadsCalled(List.of(method), ref.owner(), ref.name(), ref.desc(), false, pointOnly);
			if (found.named() || found.overload() == null) continue;
			int ordinal = InjectionPoints.ordinalOf(at);
			if (found.ambiguous() || ordinal >= found.calls()
					|| !pointOnly && !bridgeable(handler, injector, ref.owner(), ref.desc(), insertedArgumentOf(ref.owner(), ref.name(), ref.desc(), found.overload()),
							found.staticCall(), List.of(Type.getArgumentTypes(method.desc)))) return null;
			Annotations.put(copy, "target", "L" + ref.owner() + ";" + ref.name() + found.overload());
			pointed = true;
		}
		return pointed ? copies : null;
	}

	/** Whether {@link #bridgeTo} can bridge the handler to the overload, with one more argument at {@code inserted}. */
	private static boolean bridgeable(MethodNode handler, AnnotationNode injector, String owner, String desc, int inserted, boolean staticCall,
			List<Type> method) {
		if (inserted < 0) return false;
		List<Type> call = HandlerBridges.callParameters(Type.getObjectType(owner), desc, staticCall);
		if (injector.desc.equals(MixinNames.WRAP_OPERATION)) {
			call.add(Type.getType(MixinNames.OPERATION));
			return HandlerBridges.sugarAfter(handler, call, method) != null;
		}
		Type result = MixinNames.WRAP_WITH_CONDITION.contains(injector.desc) ? Type.BOOLEAN_TYPE : Type.getReturnType(desc);
		return HandlerBridges.sugarAfter(handler, call, method) != null && Type.getReturnType(handler.desc).equals(result);
	}

	/**
	 * The call, in {@code methods}, of the replacement ({@link OverloadRepairs#replacementOf}) of {@code owner.name(desc)}, a method
	 * NeoForge's build deprecated: the call that build makes instead, as {@code BoneMealItem.useOn} calls
	 * {@code applyBonemeal} where vanilla's calls {@code growCrop} (Wilder Wild's bone meal hook; DEPRECATED-1). Null
	 * otherwise.
	 */
	private MethodInsnNode replacementCall(List<MethodNode> methods, String owner, String name, String desc) {
		Code named = declaration(owner, name, desc);
		MethodNode replacement = named == null ? null : OverloadRepairs.replacementOf(named.method(), codeOf(named.owner()));
		if (replacement == null) return null;
		for (MethodNode method : methods) {
			for (AbstractInsnNode insn : method.instructions) {
				if (insn instanceof MethodInsnNode call && (call.owner.equals(owner) || call.owner.equals(named.owner()))
						&& call.name.equals(replacement.name) && call.desc.equals(replacement.desc)) return call;
			}
		}
		return null;
	}

	/**
	 * The one call, in {@code methods}, of a static method of the platform's (NeoForge's or Forge's,
	 * {@link MixinNames#isPlatformCode}) that takes the named call's receiver (unless the call is static) and arguments,
	 * with at most one more, returns what the call returns, and makes the call itself, or through a static method of its
	 * class: a hook of the platform's patches that took the call over to fire events around it, as
	 * {@code ClientHooks.drawScreen} for {@code Screen.renderWithTooltipAndSubtitles}. Null when there is none, or more
	 * than one such hook (several calls of the same one are fine).
	 *
	 * <p>With {@code anyReceiver}, for a handler that sees none of the call's arguments, also a hook taking the receiver
	 * as a supertype of the call's owner and making the call on that supertype, which reaches the owner's method: the
	 * platforms' code transformers point every mob's {@code finalizeSpawn} call in their listed classes at
	 * {@code EventHooks.finalizeMobSpawn} (NeoForge) or {@code ForgeEventFactory.onFinalizeSpawn} (Forge), which take a
	 * {@code Mob} (Friends &amp; Foes' hook at a {@code PatrollingMonster}'s, CORPUS-61, CORPUS-78).
	 */
	private MethodInsnNode hookCall(List<MethodNode> methods, String owner, String name, String desc, boolean anyReceiver) {
		boolean isStatic = isStaticCall(owner, name, desc);
		String form = staticForm(owner, desc, isStatic);
		MethodInsnNode found = null;
		for (MethodNode method : methods) {
			for (AbstractInsnNode insn : method.instructions) {
				if (!(insn instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESTATIC || !MixinNames.isPlatformCode(call.owner)) continue;
				String receiver = owner;
				if (!call.desc.equals(form) && insertedArguments(form, call.desc) == null) {
					receiver = isStatic ? null : subtypeReceiver(call.desc, owner, desc);
					if (receiver == null && anyReceiver && !isStatic) receiver = supertypeReceiver(call.desc, owner, desc);
					if (receiver == null) continue;
				}
				if (found != null && found.owner.equals(call.owner) && found.name.equals(call.name) && found.desc.equals(call.desc)) continue;
				if (!makesCall(call.owner, call.name, call.desc, receiver, name, desc, 2)) continue;
				if (found != null) return null;
				found = call;
			}
		}
		return found;
	}

	/**
	 * Why a {@code @Redirect} of an instance call this build makes only inside the platform's hook, which takes the receiver
	 * as a subtype of the call's class ({@link #subtypeReceiver}), stays out, as {@link #insertedArgumentRepairs} leaves it:
	 * the redirect would replace the hook's call, with the platform's events around it, and its handler makes the call
	 * itself on any value of the call's class, which the hook can't take (Forge 52's {@code ForgeHooksClient.onScreenCharTyped}
	 * takes a {@code Screen}, where vanilla calls a {@code GuiEventListener}'s {@code charTyped}; LAMBDA-14). Null for any
	 * other injector or call.
	 */
	String narrowedHookHint(AnnotationNode injector, String resolved, List<MethodNode> methods) {
		if (!injector.desc.equals(MixinNames.REDIRECT) || resolved == null) return null;
		MemberRef ref = MemberRef.parse(resolved);
		if (ref.fuzzy() || ref.owner() == null || ref.name() == null || ref.desc() == null || isStaticCall(ref.owner(), ref.name(), ref.desc())) return null;
		Overloads found = overloadsCalled(methods, ref.owner(), ref.name(), ref.desc(), false, false);
		MethodInsnNode hook = found.named() || found.overload() != null ? null : hookCall(methods, ref.owner(), ref.name(), ref.desc(), false);
		Type[] taken = hook == null ? new Type[0] : Type.getArgumentTypes(hook.desc);
		if (taken.length == 0 || taken[0].getSort() != Type.OBJECT) return null;
		String receiver = taken[0].getInternalName();
		if (receiver.equals(ref.owner()) || !isSubtype(receiver, ref.owner())) return null;
		return loader + " makes the call inside its hook " + hook.owner.replace('/', '.') + "." + hook.name + hook.desc + ", which takes the receiver as a "
				+ receiver.replace('/', '.') + ": a redirect would replace the hook's call, with " + loader + "'s events around it, and its handler makes the"
				+ " call itself on any " + ref.owner().replace('/', '.') + ", which the hook can't take; the mod needs an update or this mixin must be skipped.";
	}

	/**
	 * The type a static hook's descriptor takes first, when that is a subtype of the call's {@code owner} and the rest is
	 * the call's arguments, with at most two more ({@link #insertedArguments}); null otherwise. Forge 52's
	 * {@code ForgeHooksClient.onScreenCharTyped(Screen, char, int)} fires its events around the screen's
	 * {@code charTyped}, where vanilla calls a {@code GuiEventListener}'s (LAMBDA-14).
	 */
	private String subtypeReceiver(String hookDesc, String owner, String desc) {
		Type[] args = Type.getArgumentTypes(hookDesc);
		if (args.length == 0 || args[0].getSort() != Type.OBJECT) return null;
		String receiver = args[0].getInternalName();
		if (receiver.equals(owner) || !isSubtype(receiver, owner)) return null;
		String form = staticForm(receiver, desc, false);
		return hookDesc.equals(form) || insertedArguments(form, hookDesc) != null ? receiver : null;
	}

	/**
	 * The type a static hook's descriptor takes first, when that is a supertype of the call's {@code owner} and the rest
	 * is the call's descriptor; null otherwise.
	 */
	private String supertypeReceiver(String hookDesc, String owner, String desc) {
		Type[] args = Type.getArgumentTypes(hookDesc);
		if (args.length == 0 || args[0].getSort() != Type.OBJECT) return null;
		String receiver = args[0].getInternalName();
		if (receiver.equals(owner) || !hookDesc.equals(staticForm(receiver, desc, false)) || !isSubtype(owner, receiver)) return null;
		return receiver;
	}

	/**
	 * Points the handler's own instance calls of {@code owner.name(desc)} at the hook, which takes the receiver first and
	 * the same arguments, so it leaves the stack as the call did. Returns how many it pointed.
	 */
	private static int routeThroughHook(MethodNode handler, String owner, String name, String desc, MethodInsnNode hook) {
		if (handler.instructions == null) return 0;
		int routed = 0;
		for (AbstractInsnNode insn = handler.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (!(insn instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKEVIRTUAL && call.getOpcode() != Opcodes.INVOKEINTERFACE
					|| !call.owner.equals(owner) || !call.name.equals(name) || !call.desc.equals(desc)) continue;
			MethodInsnNode through = new MethodInsnNode(Opcodes.INVOKESTATIC, hook.owner, hook.name, hook.desc, false);
			handler.instructions.set(call, through);
			insn = through;
			routed++;
		}
		return routed;
	}

	/** The descriptor of a call as a static method taking the receiver first, unless the call is static already. */
	private static String staticForm(String owner, String desc, boolean isStatic) {
		return isStatic ? desc : "(L" + owner + ";" + desc.substring(1);
	}

	private static int countCalls(List<MethodNode> methods, MethodInsnNode target) {
		int calls = 0;
		for (MethodNode method : methods) {
			for (AbstractInsnNode insn : method.instructions) {
				if (insn instanceof MethodInsnNode call && call.owner.equals(target.owner) && call.name.equals(target.name) && call.desc.equals(target.desc)) calls++;
			}
		}
		return calls;
	}

	/**
	 * {@link #insertedArgument(String, String)}, or, where the types can't tell (one more float among floats), what the
	 * named method of {@code owner} tells when it only forwards to the overload: NeoForge keeps a method it gave a
	 * parameter, deprecated, passing its own parameters in order and a default for the new one
	 * ({@code Camera.setRotation(yRot, xRot)} calls {@code setRotation(yRot, xRot, 0F)}). -1 when neither tells.
	 */
	static int insertedArgument(ClassNode owner, String name, String desc, String overload) {
		int byTypes = insertedArgument(desc, overload);
		if (byTypes >= 0 || owner == null || Type.getArgumentTypes(overload).length != Type.getArgumentTypes(desc).length + 1) return byTypes;
		MethodNode old = PreflightAsm.declared(owner, name, desc);
		if (old == null || old.instructions == null) return -1;
		boolean isStatic = (old.access & Opcodes.ACC_STATIC) != 0;
		// What each value pushed before the call is: the old method's parameter it loads, or -1 for anything else.
		Map<Integer, Integer> parameterAt = new HashMap<>();
		int slot = isStatic ? 0 : 1;
		Type[] params = Type.getArgumentTypes(desc);
		for (int i = 0; i < params.length; i++) {
			parameterAt.put(slot, i);
			slot += params[i].getSize();
		}
		List<Integer> pushed = new ArrayList<>();
		for (AbstractInsnNode insn : old.instructions) {
			if (insn.getOpcode() < 0) continue; // labels, line numbers, frames
			if (insn instanceof MethodInsnNode call) {
				if (!call.owner.equals(owner.name) || !call.name.equals(name) || !call.desc.equals(overload)) return -1;
				if (!isStatic && (pushed.isEmpty() || pushed.remove(0) != MixinNames.THIS)) return -1;
				if (pushed.size() != params.length + 1) return -1;
				int inserted = -1;
				for (int k = 0, own = 0; k < pushed.size(); k++) {
					if (pushed.get(k) == own) {
						own++;
					} else if (inserted < 0 && pushed.get(k) == -1) {
						inserted = k;
					} else {
						return -1;
					}
				}
				return inserted;
			}
			if (insn instanceof VarInsnNode load && insn.getOpcode() >= Opcodes.ILOAD && insn.getOpcode() <= Opcodes.ALOAD) {
				pushed.add(!isStatic && load.var == 0 && insn.getOpcode() == Opcodes.ALOAD ? MixinNames.THIS : parameterAt.getOrDefault(load.var, -2));
			} else if (insn.getOpcode() >= Opcodes.ACONST_NULL && insn.getOpcode() <= Opcodes.LDC
					|| insn instanceof InvokeDynamicInsnNode made && Type.getArgumentTypes(made.desc).length == 0) {
				pushed.add(-1); // a constant, or a value made from none of the method's (a lambda capturing nothing)
			} else {
				return -1; // more than forwarding
			}
		}
		return -1;
	}

	/**
	 * The index of the one parameter {@code overload} adds to {@code desc}'s, with the same result, or -1: when it adds
	 * none or several, or when which one is the added can't be told (next to another of its type).
	 */
	static int insertedArgument(String desc, String overload) {
		Type[] own = Type.getArgumentTypes(desc);
		Type[] all = Type.getArgumentTypes(overload);
		if (all.length != own.length + 1 || !Type.getReturnType(desc).equals(Type.getReturnType(overload))) return -1;
		int found = -1;
		for (int added = 0; added < all.length; added++) {
			boolean fits = true;
			for (int i = 0, j = 0; i < all.length && fits; i++) {
				if (i != added) fits = all[i].equals(own[j++]);
			}
			if (!fits) continue;
			if (found >= 0) return -1;
			found = added;
		}
		return found;
	}

	/**
	 * Where {@code overload} takes one or two more parameters than {@code desc}, next to each other, with the same result:
	 * their first index and their count, or null where it takes none or more, or where they can't be told (next to others
	 * of their type). Forge 61 to 66's {@code ForgeEventFactoryClient.onScreenMouseClicked(screen, x, y, event, double)}
	 * takes the mouse's position after the screen, for its events around the screen's {@code mouseClicked(event, double)}
	 * (LAMBDA-14).
	 */
	static int[] insertedArguments(String desc, String overload) {
		int one = insertedArgument(desc, overload);
		if (one >= 0) return new int[] {one, 1};
		Type[] own = Type.getArgumentTypes(desc);
		Type[] all = Type.getArgumentTypes(overload);
		if (all.length != own.length + 2 || !Type.getReturnType(desc).equals(Type.getReturnType(overload))) return null;
		int found = -1;
		for (int added = 0; added + 1 < all.length; added++) {
			boolean fits = true;
			for (int i = 0, j = 0; i < all.length && fits; i++) {
				if (i != added && i != added + 1) fits = all[i].equals(own[j++]);
			}
			if (!fits) continue;
			if (found >= 0) return null;
			found = added;
		}
		return found < 0 ? null : new int[] {found, 2};
	}

	/**
	 * Whether {@code overload} takes {@code desc}'s parameters and more after them, with the same result: what NeoForge's
	 * patches call to pass more along, as its {@code CustomPacketPayload.codec} with the protocol and the direction
	 * (Polymer's codec hooks, PRE-13). Only a point that sees no argument (an {@code @Inject}'s, a
	 * {@code @ModifyExpressionValue}'s) moves to any such overload.
	 */
	static boolean appendsArguments(String desc, String overload) {
		Type[] own = Type.getArgumentTypes(desc);
		Type[] all = Type.getArgumentTypes(overload);
		if (all.length <= own.length || !Type.getReturnType(desc).equals(Type.getReturnType(overload))) return false;
		for (int i = 0; i < own.length; i++) {
			if (!all[i].equals(own[i])) return false;
		}
		return true;
	}
}
