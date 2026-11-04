package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.mixin.MixinPreflight.Context;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Injector repairs for code of the game the platform never runs, where a mixin applies and its hook never runs (HUD-4).
 *
 * <p>Forge's patches keep vanilla's code of a method whose work they hand to their own behind an always-true condition,
 * {@code if (Boolean.valueOf(true)) { ...; return; }}, which javac compiles as a branch it can't tell is always taken:
 * Forge 61 to 66's HUD method ({@code Gui.render}, on 26.1.2 {@code Gui.extractRenderState}, on 26.2 and 26.3
 * {@code Hud.extractRenderState}) draws Forge's layers there and returns before vanilla's calls, which Forge's layers
 * make themselves. A hook at the method's end ({@code TAIL}, the last return) or at one of those calls never ran:
 * malilib's HUD hook, which MiniHUD, Tweakeroo and Litematica draw from, is at the end. {@link #endRepairs} and
 * {@link #callRepairs} point such hooks where the work is done now.
 *
 * <p>Forge 47's GUI ({@code ForgeGui}) overrides vanilla's HUD method and never calls it, so no hook in it runs, at its
 * start either. Fabricated's own mixins add a seam for the start and one for the end of such a method to its class
 * ({@link MixinNames#SEAM_START}, {@link MixinNames#SEAM_END}), which they call where the platform's override starts and
 * ends; {@link #seamRepairs} points hooks at the method's start and end at them.
 */
final class SkippedCode extends PreflightPart {
	private static final String BOOLEAN = "java/lang/Boolean";

	SkippedCode(PreflightClasses classes) {
		super(classes);
	}

	/**
	 * Points an {@code @Inject} at the end of a method ({@code TAIL}) that the platform's patch returns before, handing
	 * its work to the platform's code ({@link #bypasses}), at the method's returns ({@code RETURN}): the patch's, where the
	 * work ends now, and the last, where it still ends when the patch's condition around vanilla's code isn't met. Only
	 * where every other return still reached is a bypass's, so the hook runs once per call, as at vanilla's end; where
	 * every point of the injector is the end; and for a handler that takes nothing after its {@code CallbackInfo} and
	 * captures no locals, which differ there. A slice goes: at the end, it only told Mixin where to look for it. Returns a
	 * description of the move.
	 */
	List<String> endRepairs(MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx) {
		if (!injector.desc.equals(MixinNames.INJECT) || Annotations.value(injector, "locals") != null || !takesOnlyItsCallback(handler)) return List.of();
		List<MethodNode> methods = PreflightAsm.selectedMethods(injector, target, ctx);
		if (methods.size() != 1 || methods.get(0).instructions == null) return List.of();
		MethodNode method = methods.get(0);
		List<Bypass> bypasses = bypasses(method);
		if (bypasses.isEmpty() || !returnsOnlyAtTheEndOrBypasses(method, bypasses)) return List.of();
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		if (ats.isEmpty() || ats.stream().anyMatch(at -> !"TAIL".equals(point(at)) || Annotations.value(at, "shift") != null || Annotations.value(at, "by") != null)) {
			return List.of();
		}
		for (AnnotationNode at : ats) Annotations.put(at, "value", "RETURN");
		remove(injector, "slice");
		return List.of("the end of " + method.name + method.desc + ", which " + loader + "'s patch returns before (it hands the method's work to "
				+ named(bypasses.get(0).handedTo()) + " and returns, skipping vanilla's code); the injection point is pointed at the method's"
				+ " returns, where its work ends here");
	}

	/**
	 * Points an {@code @Inject} at a call of one of the target's own methods in code the platform's patch skips
	 * ({@link #bypasses}) at the start of the called method ({@code HEAD}), or its returns where the point is shifted
	 * after the call: the platform's code the patch hands the work to makes that call itself (Forge's layers call each
	 * of vanilla's HUD methods). Only where the method is called nowhere else in the target, so the hook runs where
	 * vanilla's call was made and nowhere more; for an injector with that one point and one target method, which isn't
	 * cancellable (cancelling at the call skipped the rest of the target method, at the called method's start only that
	 * method), captures no locals, and takes nothing after its {@code CallbackInfo}: none of the target method's
	 * parameters, or all of them where the called method takes the same, which vanilla's call passes it in order.
	 * Returns a description of each move.
	 */
	List<String> callRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx) {
		if (!injector.desc.equals(MixinNames.INJECT) || (mixin.access & Opcodes.ACC_INTERFACE) != 0 || Annotations.value(injector, "slice") != null
				|| Annotations.value(injector, "locals") != null || Boolean.TRUE.equals(Annotations.value(injector, "cancellable"))
				|| Annotations.value(injector, "target") != null || !takesOnlyItsCallback(handler)) return List.of();
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		if (ats.size() != 1 || !(Annotations.value(injector, "method") instanceof List<?> selectors) || selectors.size() != 1) return List.of();
		List<MethodNode> methods = PreflightAsm.selectedMethods(injector, target, ctx);
		if (methods.size() != 1 || methods.get(0).instructions == null) return List.of();
		MethodNode method = methods.get(0);
		AnnotationNode at = ats.get(0);
		Object shift = Annotations.value(at, "shift");
		String shifted = shift instanceof String[] s && s.length == 2 ? s[1] : null;
		if (!"INVOKE".equals(point(at)) || !(Annotations.value(at, "target") instanceof String raw) || Annotations.value(at, "by") != null
				|| shift != null && !"BEFORE".equals(shifted) && !"AFTER".equals(shifted)
				|| Annotations.value(at, "ordinal") instanceof Integer ordinal && ordinal > 0) return List.of();
		MemberRef ref = MemberRef.parse(ctx.resolve(raw));
		if (ref.fuzzy() || !target.name.equals(ref.owner()) || ref.name() == null || ref.desc() == null) return List.of();
		MethodNode called = PreflightAsm.declared(target, ref.name(), ref.desc());
		if (called == null || (called.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT)) != 0 || called == method) return List.of();
		List<Bypass> bypasses = bypasses(method);
		List<Integer> calls = PreflightAsm.matching(method, "INVOKE", ref, -1);
		if (bypasses.isEmpty() || calls.size() != 1 || reachable(method).get(calls.get(0))) return List.of();
		// The handler sees the called method's parameters at its start: all of the target method's, passed on in order.
		if (PreflightAsm.handlerArgs(handler).size() > 1
				&& (!called.desc.substring(0, called.desc.indexOf(')')).equals(method.desc.substring(0, method.desc.indexOf(')')))
						|| !passesItsParameters(method, (MethodInsnNode) method.instructions.get(calls.get(0))))) return List.of();
		if (calledElsewhere(target, method, ref)) return List.of();
		MethodInsnNode handedTo = null;
		for (Bypass bypass : bypasses) {
			if (makesCall(bypass.handedTo(), target.name, ref)) handedTo = bypass.handedTo();
		}
		if (handedTo == null) return List.of();
		boolean after = "AFTER".equals(shifted);
		Annotations.put(injector, "method", new ArrayList<>(List.of(called.name + called.desc)));
		Annotations.put(at, "value", after ? "RETURN" : "HEAD");
		for (String key : List.of("target", "shift", "ordinal")) remove(at, key);
		return List.of("the call " + raw + " in code of " + method.name + method.desc + " that " + loader + "'s patch skips (it hands the method's work to "
				+ named(handedTo) + " and returns), which " + handedTo.owner.replace('/', '.') + " makes itself; the injection point is pointed at the "
				+ (after ? "returns" : "start") + " of " + called.name + called.desc);
	}

	/**
	 * Points an {@code @Inject} at the start ({@code HEAD}) or the end ({@code RETURN}, {@code TAIL}) of a method the
	 * platform never calls at the seam Fabricated's own mixins add for it ({@link MixinNames#SEAM_START},
	 * {@link MixinNames#SEAM_END}), which takes the same parameters: they call the start seam where the platform's
	 * override of the method starts, and go on only when its hooks didn't cancel it, as the method would have; and the
	 * end seam where it returns. Only where every point of the injector is the method's start, or its end, and for a
	 * handler capturing no locals and taking nothing after its {@code CallbackInfo}; a slice goes, as the seam has none
	 * of the method's code. Returns a description of the move.
	 */
	static List<String> seamRepairs(MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx) {
		if (!injector.desc.equals(MixinNames.INJECT) || Annotations.value(injector, "locals") != null || !takesOnlyItsCallback(handler)
				|| Annotations.value(injector, "target") != null || !(Annotations.value(injector, "method") instanceof List<?> selectors)
				|| selectors.size() != 1) return List.of();
		List<MethodNode> methods = PreflightAsm.selectedMethods(injector, target, ctx);
		if (methods.size() != 1) return List.of();
		MethodNode method = methods.get(0);
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		if (ats.isEmpty()) return List.of();
		boolean start = ats.stream().allMatch(at -> "HEAD".equals(point(at)));
		boolean end = ats.stream().allMatch(at -> "TAIL".equals(point(at)) || "RETURN".equals(point(at)));
		if (!start && !end || ats.stream().anyMatch(at -> Annotations.value(at, "shift") != null || Annotations.value(at, "by") != null)) return List.of();
		MethodNode seam = PreflightAsm.declared(target, (start ? MixinNames.SEAM_START : MixinNames.SEAM_END) + method.name, method.desc);
		if (seam == null) return List.of();
		Annotations.put(injector, "method", new ArrayList<>(List.of(seam.name + seam.desc)));
		for (AnnotationNode at : ats) Annotations.put(at, "value", start ? "HEAD" : "RETURN");
		remove(injector, "slice");
		return List.of("the " + (start ? "start" : "end") + " of " + method.name + method.desc + ", which " + ctx.loader() + " never calls (a"
				+ " subclass of its overrides it); the injection point is pointed at Fabricated's seam " + seam.name + ", which runs where that"
				+ " override " + (start ? "starts" : "ends"));
	}

	/**
	 * Where a method's patched code returns before vanilla's: each always-true condition's body ({@code ICONST_1},
	 * {@code Boolean.valueOf}, {@code booleanValue}, {@code IFEQ}) that runs straight to a return, and the platform's
	 * static method it hands the work to there.
	 */
	record Bypass(int returned, MethodInsnNode handedTo) {
	}

	static List<Bypass> bypasses(MethodNode method) {
		List<Bypass> found = new ArrayList<>();
		if (method.instructions == null) return found;
		AbstractInsnNode[] insns = method.instructions.toArray();
		for (int i = 0; i < insns.length; i++) {
			if (!alwaysTrue(insns, i)) continue;
			MethodInsnNode handedTo = null;
			for (int k = i + 1; k < insns.length; k++) {
				AbstractInsnNode insn = insns[k];
				int op = insn.getOpcode();
				if (op >= Opcodes.IRETURN && op <= Opcodes.RETURN) {
					if (handedTo != null) found.add(new Bypass(k, handedTo));
					break;
				}
				if (insn instanceof JumpInsnNode || insn instanceof TableSwitchInsnNode || insn instanceof LookupSwitchInsnNode || op == Opcodes.ATHROW) break;
				if (insn instanceof MethodInsnNode call && op == Opcodes.INVOKESTATIC && MixinNames.isPlatformCode(call.owner)) handedTo = call;
			}
		}
		return found;
	}

	/** Whether the instruction at {@code i} is the jump of {@code if (Boolean.valueOf(true))}, which is never taken. */
	private static boolean alwaysTrue(AbstractInsnNode[] insns, int i) {
		if (insns[i].getOpcode() != Opcodes.IFEQ) return false;
		int[] before = new int[3];
		int found = 0;
		for (int k = i - 1; k >= 0 && found < 3; k--) {
			if (insns[k].getOpcode() >= 0) before[found++] = k;
		}
		if (found < 3) return false;
		return insns[before[2]].getOpcode() == Opcodes.ICONST_1
				&& insns[before[1]] instanceof MethodInsnNode valueOf && valueOf.getOpcode() == Opcodes.INVOKESTATIC && BOOLEAN.equals(valueOf.owner)
				&& "valueOf".equals(valueOf.name) && "(Z)Ljava/lang/Boolean;".equals(valueOf.desc)
				&& insns[before[0]] instanceof MethodInsnNode value && value.getOpcode() == Opcodes.INVOKEVIRTUAL && BOOLEAN.equals(value.owner)
				&& "booleanValue".equals(value.name) && "()Z".equals(value.desc);
	}

	/**
	 * The instructions of the method that run, by index: those reached from its start where an always-true condition's
	 * jump ({@link #alwaysTrue}) is never taken, and the handlers of the try blocks covering one.
	 */
	static BitSet reachable(MethodNode method) {
		AbstractInsnNode[] insns = method.instructions.toArray();
		BitSet reached = new BitSet(insns.length);
		Deque<Integer> work = new ArrayDeque<>();
		work.add(0);
		boolean grew = true;
		while (grew) {
			while (!work.isEmpty()) {
				int i = work.pop();
				if (i >= insns.length || reached.get(i)) continue;
				reached.set(i);
				AbstractInsnNode insn = insns[i];
				int op = insn.getOpcode();
				if (insn instanceof JumpInsnNode jump) {
					if (!alwaysTrue(insns, i)) work.push(method.instructions.indexOf(jump.label));
					if (op != Opcodes.GOTO) work.push(i + 1);
				} else if (insn instanceof TableSwitchInsnNode table) {
					work.push(method.instructions.indexOf(table.dflt));
					table.labels.forEach(label -> work.push(method.instructions.indexOf(label)));
				} else if (insn instanceof LookupSwitchInsnNode lookup) {
					work.push(method.instructions.indexOf(lookup.dflt));
					lookup.labels.forEach(label -> work.push(method.instructions.indexOf(label)));
				} else if (!(op >= Opcodes.IRETURN && op <= Opcodes.RETURN) && op != Opcodes.ATHROW) {
					work.push(i + 1);
				}
			}
			grew = false;
			for (TryCatchBlockNode block : method.tryCatchBlocks) {
				int handler = method.instructions.indexOf(block.handler);
				if (reached.get(handler)) continue;
				int from = method.instructions.indexOf(block.start);
				int to = method.instructions.indexOf(block.end);
				int covered = reached.nextSetBit(from);
				if (covered >= 0 && covered < to) {
					work.push(handler);
					grew = true;
				}
			}
		}
		return reached;
	}

	/** Whether every return of the method that runs is its last instruction returning or the return of a bypass. */
	private static boolean returnsOnlyAtTheEndOrBypasses(MethodNode method, List<Bypass> bypasses) {
		BitSet reached = reachable(method);
		int last = -1;
		for (int i = 0; i < method.instructions.size(); i++) {
			int op = method.instructions.get(i).getOpcode();
			if (op >= Opcodes.IRETURN && op <= Opcodes.RETURN) last = i;
		}
		for (int i = reached.nextSetBit(0); i >= 0; i = reached.nextSetBit(i + 1)) {
			int op = method.instructions.get(i).getOpcode();
			if (!(op >= Opcodes.IRETURN && op <= Opcodes.RETURN) || i == last) continue;
			int at = i;
			if (bypasses.stream().noneMatch(b -> b.returned() == at)) return false;
		}
		return true;
	}

	/** Whether the call passes the method's own parameters, in order, to a method of the method's own instance. */
	private static boolean passesItsParameters(MethodNode method, MethodInsnNode call) {
		Type[] params = Type.getArgumentTypes(method.desc);
		AbstractInsnNode insn = call.getPrevious();
		int slot = 1;
		for (Type param : params) slot += param.getSize();
		for (int p = params.length - 1; p >= -1; p--) {
			while (insn != null && insn.getOpcode() < 0) insn = insn.getPrevious();
			if (p >= 0) slot -= params[p].getSize();
			int wanted = p < 0 ? 0 : slot;
			int load = p < 0 ? Opcodes.ALOAD : params[p].getOpcode(Opcodes.ILOAD);
			if (!(insn instanceof VarInsnNode var) || var.getOpcode() != load || var.var != wanted) return false;
			insn = insn.getPrevious();
		}
		return true;
	}

	/** Whether the target calls the method anywhere but in the code of {@code method} that never runs. */
	private static boolean calledElsewhere(ClassNode target, MethodNode method, MemberRef ref) {
		for (MethodNode m : target.methods) {
			if (m.instructions == null) continue;
			BitSet reached = m == method ? reachable(m) : null;
			for (int i = 0; i < m.instructions.size(); i++) {
				if (InjectionPoints.matchesInsn("INVOKE", ref, m.instructions.get(i)) && (reached == null || reached.get(i))) return true;
				if (m.instructions.get(i) instanceof InvokeDynamicInsnNode indy && handles(indy, ref.owner(), ref)) return true;
			}
		}
		return false;
	}

	/** Whether the platform's class the bypass hands the work to calls {@code ref} on {@code owner}, or makes a method reference of it. */
	private boolean makesCall(MethodInsnNode handedTo, String owner, MemberRef ref) {
		ClassNode platform = codeOf(handedTo.owner);
		if (platform == null) return false;
		for (MethodNode m : platform.methods) {
			if (m.instructions == null) continue;
			for (AbstractInsnNode insn : m.instructions) {
				if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(ref.name()) && call.desc.equals(ref.desc())) return true;
				if (insn instanceof InvokeDynamicInsnNode indy && handles(indy, owner, ref)) return true;
			}
		}
		return false;
	}

	/** Whether the invokedynamic takes a handle of {@code ref} on {@code owner} (a method reference or a lambda's target). */
	private static boolean handles(InvokeDynamicInsnNode indy, String owner, MemberRef ref) {
		for (Object arg : indy.bsmArgs) {
			if (arg instanceof Handle h && h.getOwner().equals(owner) && h.getName().equals(ref.name()) && h.getDesc().equals(ref.desc())) return true;
		}
		return false;
	}

	/** Whether an injector handler's last parameter is its {@code CallbackInfo}: it takes no locals or sugar after it. */
	private static boolean takesOnlyItsCallback(MethodNode handler) {
		Type[] params = Type.getArgumentTypes(handler.desc);
		return params.length > 0 && PreflightAsm.isCallbackInfo(params[params.length - 1]) && PreflightAsm.handlerArgs(handler) != null
				&& PreflightAsm.handlerArgs(handler).size() == params.length;
	}

	private static String point(AnnotationNode at) {
		return Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
	}

	private static String named(MethodInsnNode call) {
		return call.owner.replace('/', '.') + "." + call.name;
	}

	private static void remove(AnnotationNode ann, String key) {
		if (ann.values == null) return;
		for (int i = 0; i + 1 < ann.values.size(); i += 2) {
			if (key.equals(ann.values.get(i))) {
				ann.values.remove(i + 1);
				ann.values.remove(i);
				return;
			}
		}
	}
}
