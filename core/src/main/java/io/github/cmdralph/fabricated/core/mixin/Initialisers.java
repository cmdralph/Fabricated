package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * A mixin's initialiser: the instructions of its constructor that Mixin merges into its target's constructors, its
 * field initialisers. Mixin takes the first constructor with line numbers and, after its call to the delegate
 * constructor, the instructions on lines outside the constructor's own ({@code Constructor.getRange} and
 * {@code InsnRange.apply}, read in SpongePowered's 0.8.7 and Fabric's 0.17.4; 0.8.5's {@code MixinApplicatorStandard} has
 * the same).
 * <p>
 * SpongePowered's releases, which Forge runs (0.8.5 to 0.8.7), refuse a jump there as they merge it ("Unsupported JUMP
 * opcode in initialiser"), copy none of its labels, so a switch there would name none, and refuse an array element's
 * load or store ("Cannot handle IASTORE opcode (0x4F) in class initialiser"). Fabric's Mixin copies the labels and takes
 * all three. Cobblemon's {@code client.ItemRendererMixin} sets a field to {@code a != null && b == c ? x : y}, and the
 * Forge 52 client stopped as Mixin applied it (PRE-28); its {@code client.ModelLoaderMixin} sets one to
 * {@code Arrays.asList("egg", "tm")}, an array's stores.
 * <p>
 * Where SpongePowered's Mixin runs, the value of such a field initialiser ({@code this.f = value}) is computed by a method
 * the mixin adds ({@link #outline}), and the initialiser calls it: {@code this.f = this.fabricated$init$f()}. Mixin merges
 * the method into the target as any of the mixin's, and the initialiser no longer branches.
 */
final class Initialisers {
	private Initialisers() {
	}

	/** The mixin's initialiser, as Mixin finds it; empty where it finds none. */
	static List<AbstractInsnNode> of(ClassNode mixin) {
		MethodNode ctor = constructorOf(mixin, null);
		if (ctor == null) return List.of();
		InsnList insns = ctor.instructions;
		// Constructor.getRange: the lines from the delegate call's to the return's, or to the delegate call's where the
		// return follows a field store on its line; Mixin then puts the delegate call's line before that return.
		boolean lineValid = false;
		int line = 0, start = 0, end = 0, delegate = -1;
		AbstractInsnNode endReturn = null;
		for (int i = 0; i < insns.size(); i++) {
			AbstractInsnNode insn = insns.get(i);
			if (insn instanceof LineNumberNode number) {
				line = number.line;
				lineValid = true;
			} else if (insn instanceof MethodInsnNode call) {
				if (call.getOpcode() == Opcodes.INVOKESPECIAL && call.name.equals("<init>") && delegate == -1) {
					delegate = i;
					start = line;
				}
			} else if (insn.getOpcode() == Opcodes.PUTFIELD) {
				lineValid = false;
			} else if (insn.getOpcode() == Opcodes.RETURN) {
				if (lineValid) {
					end = line;
				} else {
					end = start;
					endReturn = insn;
				}
			}
		}
		if (start == 0 || end == 0 || end < start) return List.of();
		// InsnRange.apply(insns, false), from the delegate call: what lies on lines outside the range, and on its last line
		// up to a return.
		List<AbstractInsnNode> initialiser = new ArrayList<>();
		boolean gather = false;
		int trim = -1;
		for (int i = delegate; i < insns.size(); i++) {
			AbstractInsnNode insn = insns.get(i);
			if (insn == endReturn) {
				gather = false; // the line Mixin put before it
				trim = -1;
			}
			if (insn instanceof LineNumberNode number) {
				line = number.line;
				AbstractInsnNode next = i + 1 < insns.size() ? insns.get(i + 1) : null;
				if (line == end && (next == null || next == endReturn || next.getOpcode() != Opcodes.RETURN)) {
					gather = true;
					trim = Opcodes.RETURN;
				} else {
					gather = line < start || line > end;
					trim = -1;
				}
			} else if (gather && !(insn instanceof LabelNode)) {
				if (insn.getOpcode() == trim) trim = -1;
				else initialiser.add(insn);
			}
		}
		return initialiser;
	}

	/** The first instruction of the mixin's initialiser that SpongePowered's Mixin refuses there and Fabric's takes, or null. */
	static AbstractInsnNode refused(ClassNode mixin) {
		for (AbstractInsnNode insn : of(mixin)) {
			if (branches(insn) || refusedOpcode(insn.getOpcode())) return insn;
		}
		return null;
	}

	/**
	 * Outlines the values of the mixin's field initialisers that SpongePowered's Mixin refuses into methods the mixin
	 * adds, private and {@code @Unique} ({@code fabricated$init$<field>}), where each is an expression on the field's
	 * receiver, {@code this}, as javac writes a field's initialiser: it neither reaches below the receiver on the stack nor
	 * stores a local or a field, and its jumps stay within it. Every refused value or none: a mixin whose initialiser is
	 * refused anyway is left as it is. The constructor and the methods have their frames expanded.
	 *
	 * @param expanded the mixin parsed again with its frames expanded, where there is anything to outline
	 * @return the fields whose values were outlined
	 */
	static List<String> outline(ClassNode mixin, Supplier<ClassNode> expanded) {
		if (refused(mixin) == null) return List.of();
		ClassNode copy = expanded.get();
		List<AbstractInsnNode> initialiser = copy == null ? List.of() : of(copy);
		if (initialiser.isEmpty()) return List.of();
		MethodNode ctor = constructorOf(copy, initialiser.get(0));
		// The stack's size before and after each instruction, -1 where it is unreachable.
		Map<AbstractInsnNode, Integer> before = new IdentityHashMap<>();
		Map<AbstractInsnNode, Integer> after = new IdentityHashMap<>();
		AnalyzerAdapter stack = new AnalyzerAdapter(copy.name, ctor.access, ctor.name, ctor.desc, null);
		try {
			for (AbstractInsnNode insn : ctor.instructions) {
				before.put(insn, stack.stack == null ? -1 : stack.stack.size());
				insn.accept(stack);
				after.put(insn, stack.stack == null ? -1 : stack.stack.size());
			}
		} catch (RuntimeException e) {
			return List.of();
		}
		Set<AbstractInsnNode> merged = Collections.newSetFromMap(new IdentityHashMap<>());
		merged.addAll(initialiser);
		List<MethodNode> methods = new ArrayList<>();
		List<String> fields = new ArrayList<>();
		for (AbstractInsnNode receiver : initialiser) {
			if (!(receiver instanceof VarInsnNode load) || load.getOpcode() != Opcodes.ALOAD || load.var != 0 || before.get(receiver) != 0) continue;
			// The statement ends where the stack is empty again: at the store of the field.
			AbstractInsnNode store = receiver.getNext();
			while (store != null && (store.getOpcode() < 0 || after.get(store) != 0)) store = store.getNext();
			if (!(store instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.PUTFIELD || !field.owner.equals(copy.name) || !merged.contains(store)) continue;
			List<AbstractInsnNode> value = new ArrayList<>();
			for (AbstractInsnNode insn = receiver.getNext(); insn != store; insn = insn.getNext()) value.add(insn);
			if (value.stream().noneMatch(insn -> branches(insn) || refusedOpcode(insn.getOpcode()))) continue;
			MethodNode method = valueMethod(copy, ctor, field, value, merged);
			if (method == null) return List.of();
			ctor.instructions.insert(receiver, new MethodInsnNode(Opcodes.INVOKESPECIAL, copy.name, method.name, method.desc, false));
			ctor.instructions.insert(receiver, new VarInsnNode(Opcodes.ALOAD, 0));
			methods.add(method);
			fields.add(field.name);
		}
		if (methods.isEmpty() || refused(copy) != null) return List.of();
		mixin.methods.set(mixin.methods.indexOf(constructorOf(mixin, null)), ctor);
		mixin.methods.addAll(methods);
		return fields;
	}

	/**
	 * The method computing a field initialiser's {@code value}, moved out of the constructor, or null where it is no
	 * expression on the receiver alone: the method then underflows its stack, or ends with more than the value.
	 */
	private static MethodNode valueMethod(ClassNode mixin, MethodNode ctor, FieldInsnNode field, List<AbstractInsnNode> value, Set<AbstractInsnNode> merged) {
		Set<LabelNode> own = Collections.newSetFromMap(new IdentityHashMap<>());
		for (AbstractInsnNode insn : value) {
			if (insn instanceof LabelNode label) own.add(label);
			else if (insn.getOpcode() >= 0 && !merged.contains(insn)) return null;
		}
		for (AbstractInsnNode insn : value) {
			int opcode = insn.getOpcode();
			if (insn instanceof VarInsnNode load && (opcode != Opcodes.ALOAD || load.var != 0) || opcode == Opcodes.IINC || opcode == Opcodes.PUTFIELD
					|| opcode == Opcodes.PUTSTATIC || opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN
					|| insn instanceof LineNumberNode line && !own.contains(line.start)) return null;
		}
		// Its jumps stay within it, and nothing else names its labels.
		Set<AbstractInsnNode> within = Collections.newSetFromMap(new IdentityHashMap<>());
		within.addAll(value);
		for (AbstractInsnNode insn : ctor.instructions) {
			boolean inside = within.contains(insn);
			for (LabelNode label : targets(insn)) {
				if (inside != own.contains(label)) return null;
			}
		}
		for (TryCatchBlockNode block : ctor.tryCatchBlocks) {
			if (own.contains(block.start) || own.contains(block.end) || own.contains(block.handler)) return null;
		}
		if (ctor.localVariables != null) {
			for (LocalVariableNode local : ctor.localVariables) {
				if (own.contains(local.start) || own.contains(local.end)) return null;
			}
		}
		MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE, "fabricated$init$" + field.name, "()" + field.desc, null, null);
		method.visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode(MixinNames.UNIQUE)));
		for (AbstractInsnNode insn : value) {
			ctor.instructions.remove(insn);
			if (insn instanceof FrameNode frame) {
				// The receiver isn't on the method's stack.
				if (frame.stack == null || frame.stack.isEmpty() || !mixin.name.equals(frame.stack.get(0))) return null;
				frame.stack = new ArrayList<>(frame.stack.subList(1, frame.stack.size()));
			}
			method.instructions.add(insn);
		}
		AnalyzerAdapter stack = new AnalyzerAdapter(mixin.name, method.access, method.name, method.desc, null);
		try {
			method.instructions.accept(stack);
		} catch (RuntimeException e) {
			return null; // it reaches below its start: the receiver
		}
		if (stack.stack == null || stack.stack.size() != Type.getType(field.desc).getSize()) return null;
		method.instructions.add(new InsnNode(Type.getType(field.desc).getOpcode(Opcodes.IRETURN)));
		method.maxStack = ctor.maxStack;
		method.maxLocals = 1;
		return method;
	}

	/** The labels an instruction jumps to. */
	private static List<LabelNode> targets(AbstractInsnNode insn) {
		if (insn instanceof JumpInsnNode jump) return List.of(jump.label);
		List<LabelNode> labels = new ArrayList<>();
		if (insn instanceof TableSwitchInsnNode table) {
			labels.add(table.dflt);
			labels.addAll(table.labels);
		} else if (insn instanceof LookupSwitchInsnNode lookup) {
			labels.add(lookup.dflt);
			labels.addAll(lookup.labels);
		}
		return labels;
	}

	/** The constructor Mixin takes the initialiser from: the one holding {@code insn}, or the first with line numbers. */
	private static MethodNode constructorOf(ClassNode mixin, AbstractInsnNode insn) {
		for (MethodNode method : mixin.methods) {
			if (method.name.equals("<init>") && method.instructions != null && (insn == null ? hasLineNumbers(method) : method.instructions.contains(insn))) return method;
		}
		return null;
	}

	private static boolean refusedOpcode(int opcode) {
		return opcode >= Opcodes.IALOAD && opcode <= Opcodes.SALOAD || opcode >= Opcodes.IASTORE && opcode <= Opcodes.SASTORE;
	}

	static boolean branches(AbstractInsnNode insn) {
		return insn instanceof JumpInsnNode || insn instanceof TableSwitchInsnNode || insn instanceof LookupSwitchInsnNode;
	}

	private static boolean hasLineNumbers(MethodNode method) {
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof LineNumberNode) return true;
		}
		return false;
	}
}
