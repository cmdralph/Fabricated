package io.github.cmdralph.fabricated.core.bytecode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Vanilla's methods a platform took out of a game class, asking its own extension methods where vanilla called them, put
 * back so that Fabric mods' hooks of them apply (SEAM-1). Each {@link Seam} is a row: the vanilla method, and the
 * platform's code at the places vanilla calls it, calls of the method's parameters joined by {@code &&}. Where a class
 * lacks the method and has that code, the method is declared again with the platform's code as its body, and each place
 * calls it instead, so every check answers as the platform's code did. A method the class still declares is left alone
 * (NeoForge 26.2 and 26.3 keep the stickiness ones, their bodies asking the extensions).
 *
 * <p>Piston stickiness: vanilla's {@code PistonStructureResolver} asks its private static {@code isSticky(state)} and
 * {@code canStickToEachOther(state1, state2)}; NeoForge 21.1, 21.11 and 26.1.2 and Forge 47 to 66 ask
 * {@code state.isStickyBlock()} and {@code state1.canStickTo(state2) && state2.canStickTo(state1)} there instead (read in
 * their classes). NeoForge 26.1.2's {@code addBlockLine} asks {@code state1.canStickTo(state1)} for its second check, which
 * its other check and NeoForge 26.2 ask the other way: that form is restored as vanilla's call too, so a class rewritten
 * (one a Fabric mod's mixin hooks a seam of) asks both ways there, as NeoForge 26.2 does; elsewhere it asks as NeoForge
 * 26.1.2 does.
 *
 * <p>The same rewrite gives pre-flight its view of the game ({@link #over}) and the game its classes at load (before
 * Mixin, {@link #restoreWhole}), so a hook pre-flight passes finds what it hooks; at load, only the classes in which a
 * Fabric mod's mixin member names or shadows a seam are rewritten (Lithium's and Carpet's other mixins of the resolver,
 * which hook none, leave it as the platform has it). A rewritten class no longer makes the platform's calls where vanilla
 * called the seam, so a native mod's mixin hooking them there finds none. Each change keeps
 * the stack as it was at every instruction: a call takes the operands of the platform's first call and leaves its
 * result, and the platform's later calls, which only loaded locals, go.
 */
public final class VanillaSeams {
	/**
	 * The invisible annotation on a method declared again. The JVM and Mixin never read it, and no class of that name exists:
	 * pre-flight reads it to tell the hooks of such methods ({@link #restoredIn}).
	 */
	public static final String MARKER = "Lio/github/cmdralph/fabricated/core/bytecode/RestoredSeam;";
	private static final String BLOCK_STATE = "net/minecraft/world/level/block/state/BlockState";
	private static final String RESOLVER = "net/minecraft/world/level/block/piston/PistonStructureResolver";
	/** A method's name where the game runs with SRG's member names (Forge 47): {@code m_<number>_}. */
	private static final Pattern SRG_METHOD = Pattern.compile("m_\\d+_");

	/**
	 * A call the platform makes where vanilla calls a seam: an instance method, its receiver and arguments given by the
	 * indexes of the seam's parameters.
	 */
	record Call(String owner, String name, String desc, int... operands) {
	}

	/**
	 * A vanilla method a platform took out.
	 *
	 * @param owner   the class, by internal name
	 * @param name    its name, in Mojang's names
	 * @param srgName its name in SRG's, where the game runs with them (Forge 47)
	 * @param desc    its descriptor (static, returning a boolean)
	 * @param forms   the platform's code where vanilla calls it, each the calls joined by {@code &&}: the first is its body
	 */
	record Seam(String owner, String name, String srgName, String desc, List<List<Call>> forms) {
		/** The platform's answer, as a report says it: {@code a.canStickTo(b) && b.canStickTo(a)}. */
		String answer() {
			return forms.get(0).stream().map(c -> (char) ('a' + c.operands()[0]) + "." + c.name() + "("
					+ Arrays.stream(c.operands()).skip(1).mapToObj(o -> String.valueOf((char) ('a' + o))).collect(Collectors.joining(", ")) + ")")
					.collect(Collectors.joining(" && "));
		}
	}

	private static Call blockState(String name, String desc, int... operands) {
		return new Call(BLOCK_STATE, name, desc, operands);
	}

	private static final String STATE = "L" + BLOCK_STATE + ";";
	/** The table, a row per vanilla method. */
	static final List<Seam> SEAMS = List.of(
			new Seam(RESOLVER, "isSticky", "m_155937_", "(" + STATE + ")Z", List.of(List.of(blockState("isStickyBlock", "()Z", 0)))),
			new Seam(RESOLVER, "canStickToEachOther", "m_155939_", "(" + STATE + STATE + ")Z", List.of(
					List.of(blockState("canStickTo", "(" + STATE + ")Z", 0, 1), blockState("canStickTo", "(" + STATE + ")Z", 1, 0)),
					// NeoForge 26.1.2's addBlockLine.
					List.of(blockState("canStickTo", "(" + STATE + ")Z", 0, 1), blockState("canStickTo", "(" + STATE + ")Z", 0, 0)))));

	/** The classes the table names, by internal name. */
	public static final Set<String> CLASSES = SEAMS.stream().map(Seam::owner).collect(Collectors.toUnmodifiableSet());

	private VanillaSeams() {
	}

	/** Whether the table names the class (internal name): a class whose seams {@link #restore} may put back. */
	public static boolean names(String internalName) {
		return CLASSES.contains(internalName);
	}

	/**
	 * Puts back the class's seams it lacks and has the platform's code for, each marked with {@link #MARKER}. Returns the
	 * methods declared (name and descriptor); none when the class is unchanged.
	 */
	public static List<String> restore(ClassNode node) {
		List<String> restored = new ArrayList<>();
		if (!names(node.name)) return restored;
		boolean srg = node.methods.stream().anyMatch(m -> SRG_METHOD.matcher(m.name).matches());
		// The class's own methods: a seam's body makes the platform's calls.
		List<MethodNode> own = List.copyOf(node.methods);
		for (Seam seam : SEAMS) {
			if (!seam.owner().equals(node.name)) continue;
			String name = srg ? seam.srgName() : seam.name();
			if (node.methods.stream().anyMatch(m -> m.desc.equals(seam.desc()) && (m.name.equals(seam.name()) || m.name.equals(seam.srgName())))) continue;
			int calls = 0;
			for (MethodNode method : own) calls += pointAt(method, seam, name);
			if (calls == 0) continue;
			node.methods.add(body(seam, name));
			restored.add(name + seam.desc());
		}
		return restored;
	}

	/**
	 * What {@link #restoreWhole} did: the seams put back, or the failure that left the class as it was.
	 *
	 * @param restored as {@link #restore} returns them; empty on a failure
	 * @param failure  what {@link #restore} threw, or null
	 */
	public record Restoring(List<String> restored, Throwable failure) {
	}

	/**
	 * {@link #restore}, all or nothing, as a class loads: on a copy of the class, whose methods then replace the class's.
	 * Where it throws, the class stays exactly as it was, and the failure is returned for the caller to log, never thrown:
	 * the class loads as the platform has it, its Fabric mods' hooks of the seams finding nothing, as before SEAM-1.
	 */
	public static Restoring restoreWhole(ClassNode node) {
		try {
			ClassNode copy = new ClassNode();
			node.accept(copy);
			List<String> restored = restore(copy);
			if (!restored.isEmpty()) {
				node.methods.clear();
				node.methods.addAll(copy.methods);
			}
			return new Restoring(restored, null);
		} catch (RuntimeException | LinkageError | StackOverflowError e) {
			return new Restoring(List.of(), e);
		}
	}

	/** The game's classes with their seams put back ({@link #restore}): pre-flight's view, as they will load. */
	public static ClassBytesSource over(ClassBytesSource game) {
		return internalName -> {
			byte[] data = game.read(internalName);
			if (data == null || !names(internalName)) return data;
			ClassNode node = new ClassNode();
			new ClassReader(data).accept(node, 0);
			if (restore(node).isEmpty()) return data;
			ClassWriter writer = new ClassWriter(0);
			node.accept(writer);
			return writer.toByteArray();
		};
	}

	/**
	 * The seams a class as read has had put back: name, descriptor and the platform's answer they give
	 * ({@code isSticky(L…;)Z} to {@code a.isStickyBlock()}), as {@link #restore} marked them.
	 */
	public static List<Restored> restoredIn(ClassNode node) {
		List<Restored> found = new ArrayList<>();
		for (MethodNode method : node.methods) {
			if (method.invisibleAnnotations == null || method.invisibleAnnotations.stream().noneMatch(a -> MARKER.equals(a.desc))) continue;
			for (Seam seam : SEAMS) {
				if (seam.owner().equals(node.name) && seam.desc().equals(method.desc) && (seam.name().equals(method.name) || seam.srgName().equals(method.name))) {
					found.add(new Restored(method.name, method.desc, seam.answer()));
				}
			}
		}
		return found;
	}

	/** A seam a class has had put back: its name and descriptor as the game names it, and the platform's answer it gives. */
	public record Restored(String name, String desc, String answer) {
	}

	/** Points the method's places with one of the seam's forms at the seam; how many. */
	private static int pointAt(MethodNode method, Seam seam, String name) {
		if (method.instructions == null || method.instructions.size() == 0) return 0;
		Set<LabelNode> targets = jumpTargets(method);
		int pointed = 0;
		for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (!(insn instanceof MethodInsnNode call)) continue;
			for (List<Call> form : seam.forms()) {
				if (!matches(call, form.get(0))) continue;
				List<AbstractInsnNode> rest = form.size() == 1 ? List.of() : rest(call, form, targets);
				if (rest == null) continue;
				for (AbstractInsnNode gone : rest) method.instructions.remove(gone);
				MethodInsnNode seamCall = new MethodInsnNode(Opcodes.INVOKESTATIC, seam.owner(), name, seam.desc(), false);
				method.instructions.set(call, seamCall);
				insn = seamCall;
				pointed++;
				break;
			}
		}
		return pointed;
	}

	private static boolean matches(MethodInsnNode insn, Call call) {
		return (insn.getOpcode() == Opcodes.INVOKEVIRTUAL || insn.getOpcode() == Opcodes.INVOKEINTERFACE) && insn.owner.equals(call.owner())
				&& insn.name.equals(call.name()) && insn.desc.equals(call.desc());
	}

	/**
	 * The instructions after {@code first} that make the form's other calls, ending with the jump after its last, where the
	 * code is the form: {@code first} takes the seam's parameters loaded from locals, in order, and each call is followed by
	 * a jump if false to one place, its operands the locals the first call's parameters came from; no instruction between
	 * the first jump and the last is a jump's target. Null where it isn't.
	 */
	private static List<AbstractInsnNode> rest(MethodInsnNode first, List<Call> form, Set<LabelNode> targets) {
		int[] firstOperands = form.get(0).operands();
		int[] locals = new int[firstOperands.length];
		AbstractInsnNode at = first;
		for (int i = firstOperands.length - 1; i >= 0; i--) {
			at = previousReal(at);
			if (firstOperands[i] != i || !(at instanceof VarInsnNode load) || load.getOpcode() != Opcodes.ALOAD) return null;
			locals[i] = load.var;
		}
		AbstractInsnNode jump = nextReal(first);
		if (!(jump instanceof JumpInsnNode ifFalse) || ifFalse.getOpcode() != Opcodes.IFEQ) return null;
		List<AbstractInsnNode> rest = new ArrayList<>();
		at = jump;
		for (Call call : form.subList(1, form.size())) {
			for (int operand : call.operands()) {
				at = nextReal(at);
				if (!(at instanceof VarInsnNode load) || load.getOpcode() != Opcodes.ALOAD || load.var != locals[operand]) return null;
				rest.add(at);
			}
			at = nextReal(at);
			if (!(at instanceof MethodInsnNode next) || !matches(next, call)) return null;
			rest.add(at);
			at = nextReal(at);
			if (!(at instanceof JumpInsnNode alsoFalse) || alsoFalse.getOpcode() != Opcodes.IFEQ || alsoFalse.label != ifFalse.label) return null;
			rest.add(at);
		}
		for (AbstractInsnNode insn = jump.getNext(); insn != at; insn = insn.getNext()) {
			if (insn instanceof LabelNode label && targets.contains(label)) return null;
		}
		return rest;
	}

	private static AbstractInsnNode previousReal(AbstractInsnNode insn) {
		do insn = insn.getPrevious(); while (insn != null && insn.getOpcode() < 0);
		return insn;
	}

	private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
		do insn = insn.getNext(); while (insn != null && insn.getOpcode() < 0);
		return insn;
	}

	/** The labels a jump, a switch or an exception handler goes to. */
	private static Set<LabelNode> jumpTargets(MethodNode method) {
		Set<LabelNode> targets = new HashSet<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof JumpInsnNode jump) targets.add(jump.label);
			if (insn instanceof TableSwitchInsnNode table) {
				targets.add(table.dflt);
				targets.addAll(table.labels);
			}
			if (insn instanceof LookupSwitchInsnNode lookup) {
				targets.add(lookup.dflt);
				targets.addAll(lookup.labels);
			}
		}
		for (TryCatchBlockNode block : method.tryCatchBlocks) targets.add(block.handler);
		return targets;
	}

	/**
	 * {@code private static boolean name(params) { return call && call...; }}: the seam's first form, with its maxs and the
	 * one frame its jumps need (the method's start, so {@code F_SAME}).
	 */
	private static MethodNode body(Seam seam, String name) {
		MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, name, seam.desc(), null, null);
		method.invisibleAnnotations = new ArrayList<>(List.of(new AnnotationNode(MARKER)));
		List<Call> form = seam.forms().get(0);
		Label no = new Label();
		int stack = 0;
		for (int i = 0; i < form.size(); i++) {
			Call call = form.get(i);
			for (int operand : call.operands()) method.visitVarInsn(Opcodes.ALOAD, operand);
			stack = Math.max(stack, call.operands().length);
			method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, call.owner(), call.name(), call.desc(), false);
			if (form.size() > 1) method.visitJumpInsn(Opcodes.IFEQ, no);
		}
		if (form.size() > 1) {
			method.visitInsn(Opcodes.ICONST_1);
			method.visitInsn(Opcodes.IRETURN);
			method.visitLabel(no);
			method.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			method.visitInsn(Opcodes.ICONST_0);
		}
		method.visitInsn(Opcodes.IRETURN);
		method.visitMaxs(stack, Type.getArgumentsAndReturnSizes(seam.desc()) >> 2);
		return method;
	}
}
