package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
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
			for (int i = 0; i + 1 < at.values.size(); i += 2) {
				if ("target".equals(at.values.get(i))) at.values.set(i + 1, step);
			}
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
	 */
	List<String> subtypeCallRepairs(AnnotationNode injector, ClassNode target, Context ctx) {
		if (!injector.desc.equals(MixinNames.INJECT) && !injector.desc.equals(MixinNames.MODIFY_EXPRESSION_VALUE) || Annotations.value(injector, "slice") != null) return List.of();
		List<MethodNode> methods = PreflightAsm.selectedMethods(injector, target, ctx);
		List<String> moved = new ArrayList<>();
		for (AnnotationNode at : Annotations.list(Annotations.value(injector, "at"))) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
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
						ambiguous |= subtype != null && !subtype.equals(call.owner);
						subtype = call.owner;
						calls++;
					}
				}
			}
			int ordinal = Annotations.value(at, "ordinal") instanceof Integer n ? n : -1;
			if (named || subtype == null || ambiguous || ordinal >= calls) continue;
			String retargeted = "L" + subtype + ";" + ref.name() + ref.desc();
			for (int i = 0; i + 1 < at.values.size(); i += 2) {
				if ("target".equals(at.values.get(i))) at.values.set(i + 1, retargeted);
			}
			moved.add(ref.owner().replace('/', '.') + "." + ref.name() + ", which this game build calls through "
					+ subtype.replace('/', '.') + " (the same method); the injection point is pointed there");
		}
		return moved;
	}

	/**
	 * An injector's {@code INVOKE} points picking by ordinal 0 vanilla's one call of a method Fabricated puts back in each
	 * of the target method's places making the platform's extension call instead (SEAM-3): NeoForge's and Forge's server
	 * make it in the creative branch and in the survival one, so ordinal 0 would pick the creative branch's alone. The
	 * ordinal is dropped, and the point picks the call in each, each vanilla's one on its path; not where the injector
	 * allows fewer injections, or vanilla's code makes the call more than once. Returns the calls, as the mod names them.
	 */
	List<String> putBackOrdinalRepairs(AnnotationNode injector, ClassNode target, Context ctx) {
		if (Annotations.value(injector, "slice") != null) return List.of();
		List<MethodNode> methods = PreflightAsm.selectedMethods(injector, target, ctx);
		if (methods.size() != 1) return List.of();
		MethodNode method = methods.get(0);
		int places = VanillaSeams.putBackPlaces(method);
		if (places < 2 || Annotations.value(injector, "allow") instanceof Integer allow && allow >= 0 && allow < places) return List.of();
		ClassNode original = vanillaClass(target.name);
		MethodNode before = original == null ? null : PreflightAsm.declared(original, method.name, method.desc);
		List<VanillaSeams.Restored> putBack = VanillaSeams.restoredIn(target).stream().filter(r -> (method.name + method.desc).equals(r.in())).toList();
		List<String> dropped = new ArrayList<>();
		for (AnnotationNode at : Annotations.list(Annotations.value(injector, "at"))) {
			if (!(Annotations.value(at, "value") instanceof String point) || !point.equalsIgnoreCase("INVOKE") || !Integer.valueOf(0).equals(Annotations.value(at, "ordinal"))
					|| !(Annotations.value(at, "target") instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy() || ref.owner() == null || ref.name() == null || ref.desc() == null
					|| putBack.stream().noneMatch(r -> r.owner().equals(ref.owner()) && r.name().equals(ref.name()) && r.desc().equals(ref.desc()))
					// Every call of it here is one put back, and vanilla's code makes one.
					|| PreflightAsm.matching(method, "INVOKE", ref, -1).size() != places
					|| before != null && PreflightAsm.matching(before, "INVOKE", ref, -1).size() != 1) continue;
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
	 * HUD twice). A point inside a slice, or a target method vanilla doesn't declare, is left as it is. Returns a
	 * description of each change.
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
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			if (!List.of("INVOKE", "FIELD", "NEW").contains(point) || !(Annotations.value(at, "target") instanceof String raw)) continue;
			int ordinal = Annotations.value(at, "ordinal") instanceof Integer n ? n : -1;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy()) continue;
			int opcode = Annotations.value(at, "opcode") instanceof Integer o ? o : -1;
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
		Set<AbstractInsnNode> entered = new HashSet<>();
		for (TryCatchBlockNode block : method.tryCatchBlocks) entered.add(block.handler);
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof JumpInsnNode jump) entered.add(jump.label);
			if (insn instanceof TableSwitchInsnNode table) {
				entered.add(table.dflt);
				entered.addAll(table.labels);
			}
			if (insn instanceof LookupSwitchInsnNode lookup) {
				entered.add(lookup.dflt);
				entered.addAll(lookup.labels);
			}
		}
		for (int i = from + 1; i < to; i++) {
			AbstractInsnNode insn = method.instructions.get(i);
			int op = insn.getOpcode();
			if (Initialisers.branches(insn) || op >= Opcodes.IRETURN && op <= Opcodes.RETURN || op == Opcodes.ATHROW || entered.contains(insn)) return false;
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
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			if (!point.equals("INVOKE") || !(Annotations.value(at, "target") instanceof String raw) || Annotations.value(at, "shift") != null
					|| Annotations.value(at, "by") != null) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy() || ref.owner() == null || ref.name() == null || ref.desc() == null || !PreflightAsm.matching(patched, point, ref, -1).isEmpty()) continue;
			List<Integer> inVanilla = PreflightAsm.matching(before, point, ref, -1);
			int ordinal = Annotations.value(at, "ordinal") instanceof Integer n ? n : -1;
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
		Type ownerType = Type.getObjectType(owner);
		List<Type> method = sharedParameters(methods);
		MethodNode bridge = injector.desc.equals(MixinNames.WRAP_OPERATION)
				? HandlerBridges.wrapperBridge(mixin, handler, ownerType, desc, overload, inserted, staticCall, method, bridges)
				: HandlerBridges.callBridge(mixin, handler, ownerType, desc, overload, inserted, staticCall,
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
	List<String> insertedArgumentRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx,
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
		if (methods.isEmpty() || !pointOnly && ats.size() != 1) return List.of();
		List<String> moved = new ArrayList<>();
		for (AnnotationNode at : ats) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
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
			// A handler sharing the mixin's state with others moves only with them, which this doesn't do: jGui (ReplayMod)
			// keeps the screen's graphics from a @ModifyArg at the same call for its @Inject after it.
			if ((hook != null || leading != null) && !independentOf(mixin).contains(handler.name + handler.desc)) continue;
			if (leading != null) {
				int ordinal = Annotations.value(at, "ordinal") instanceof Integer n ? n : -1;
				if (ordinal >= countCalls(methods, leading)) continue;
				for (int i = 0; i + 1 < at.values.size(); i += 2) {
					if ("target".equals(at.values.get(i))) at.values.set(i + 1, "L" + leading.owner + ";" + leading.name + leading.desc);
				}
				moved.add(owner.replace('/', '.') + "." + name + desc + ", which this game build makes through " + loader + "'s "
						+ leading.owner.replace('/', '.') + "." + leading.name + leading.desc + "; the injection point is pointed at that call");
				continue;
			}
			String callOwner = owner;
			String callName = name;
			String original = desc;
			if (hook != null) {
				callOwner = hook.owner;
				callName = hook.name;
				desc = staticForm(owner, desc, isStaticCall(owner, name, desc));
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
			int ordinal = Annotations.value(at, "ordinal") instanceof Integer n ? n : -1;
			if (named || overload == null || ambiguous || ordinal >= calls) continue;
			int inserted = hook == null && replacement == null && !constructor ? insertedArgumentOf(owner, name, desc, overload) : insertedArgument(desc, overload);
			// A hook taking just the call's receiver and arguments takes the handlers as they are.
			if (!pointOnly && !overload.equals(desc) && !bridgeTo(mixin, handler, injector, owner, desc, overload, inserted, staticCall, methods, bridges)) continue;
			for (int i = 0; i + 1 < at.values.size(); i += 2) {
				if ("target".equals(at.values.get(i))) at.values.set(i + 1, constructor ? overload : "L" + callOwner + ";" + callName + overload);
			}
			String via = pointOnly || overload.equals(desc) ? "" : ", through a bridge handler";
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
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			if (!point.equals("INVOKE") || !(Annotations.value(at, "target") instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy() || ref.owner() == null || ref.name() == null || ref.desc() == null) continue;
			Overloads found = overloadsCalled(List.of(method), ref.owner(), ref.name(), ref.desc(), false, pointOnly);
			if (found.named() || found.overload() == null) continue;
			int ordinal = Annotations.value(at, "ordinal") instanceof Integer n ? n : -1;
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
				if (!call.desc.equals(form) && insertedArgument(form, call.desc) < 0) {
					receiver = anyReceiver && !isStatic ? supertypeReceiver(call.desc, owner, desc) : null;
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
