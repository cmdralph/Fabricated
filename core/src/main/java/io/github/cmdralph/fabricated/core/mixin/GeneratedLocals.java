package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;

/**
 * Whether the table Mixin generates from a method's code types a slot as a given reference type at an instruction
 * ({@code Locals.generateLocalVariableTable}, the same in SpongePowered's 0.8.5 and 0.8.7 and Fabric's 0.16 and 0.17):
 * Mixin looks a slot up there where the method's own local variable table doesn't cover it (PRE-36). Mixin runs its
 * verifier over the method and gives the slot an entry for each run of instructions, in their order, over which the
 * verifier types it alike: starting at the first of them where that is a label, after it otherwise, typed as the
 * verifier types the slot, or, where it holds nothing there, as the entry before was.
 * <p>
 * The verifier's type is followed here as far as the question needs: the values stored are typed as it reads them
 * ({@link MixinPreflight#flowType}), a slot holds nothing where a path reaches the instruction without a store to it
 * (the verifier's merge of a value with none is none), and where values of unlike types meet the slot may or may not be
 * of the type (the verifiers merge them into one of them or a superclass, SpongePowered's keeping an interface's type).
 * Nothing is told where the verifier may stop before the method's end ({@link MixinPreflight#verifierTypes}), or of a
 * method with subroutines.
 */
final class GeneratedLocals {
	/** The entry covering an instruction is not of the type, is of it, or may be. */
	static final byte NOT = 0, IS = 1, MAYBE = 2;

	// What the verifier holds in the slot: nothing, a value of exactly the type, one that may be of it, one of another
	// reference type, null, a primitive, or one of the last two or nothing.
	private static final int UNREACHED = 0, UNSET = 1, EXACT = 2, UNSURE = 3, OTHER = 4, NULL = 5, INT = 6, FLOAT = 7, LONG = 8, DOUBLE = 9, VAGUE = 10;

	final MethodNode method;
	final String owner;
	final Function<String, ClassIndex.ClassInfo> hierarchy;
	/** Whether Mixin's verifier types the whole method ({@link MixinPreflight#verifierTypes}). */
	final boolean typed;
	private final Map<String, byte[]> entries = new HashMap<>();
	private List<List<Integer>> successors;
	private List<List<Integer>> handlers;
	private boolean followed = true;

	GeneratedLocals(ClassNode owner, MethodNode method, Function<String, ClassIndex.ClassInfo> hierarchy) {
		this.method = method;
		this.owner = owner.name;
		this.hierarchy = hierarchy;
		this.typed = MixinPreflight.verifierTypes(owner, method, hierarchy);
	}

	/** Whether the generated entry covering the instruction at {@code index} for {@code slot} is of {@code type}; {@link #MAYBE} where that isn't told. */
	byte typeAt(Type type, int slot, int index) {
		if (!typed) return MAYBE;
		byte[] types = entries.computeIfAbsent(slot + type.getDescriptor(), key -> entries(type, slot));
		return types == null ? MAYBE : types[index];
	}

	private byte[] entries(Type type, int slot) {
		int[] held = held(type, slot);
		if (held == null) return null;
		InsnList insns = method.instructions;
		byte[] types = new byte[held.length];
		// Before the first entry Mixin falls back on any entry of the method's table for the slot.
		byte entry = MAYBE;
		// The type of the last entry of a value, which one of none takes: none yet.
		byte lastKnown = NOT;
		int before = -1;
		for (int i = 0; i < held.length; i++) {
			int now = held[i];
			if (now == UNREACHED) {
				types[i] = entry;
				continue;
			}
			byte of = switch (now) {
				case UNSET -> lastKnown;
				case EXACT -> IS;
				case UNSURE -> MAYBE;
				case VAGUE -> lastKnown == NOT ? NOT : MAYBE;
				default -> NOT;
			};
			if (now != UNSET) lastKnown = of;
			boolean same = before == now && now != UNSURE && now != OTHER && now != VAGUE;
			if (same) {
				types[i] = entry;
			} else if (before != now) {
				// A new entry: from this instruction where it is a label, from the next otherwise.
				types[i] = insns.get(i) instanceof LabelNode ? of : entry;
				entry = of;
			} else {
				// Alike as far as told: the entry goes on, or a new one starts.
				entry = entry == of ? entry : MAYBE;
				types[i] = entry;
			}
			before = now;
		}
		return types;
	}

	/** What the verifier holds in {@code slot} before each instruction, as far as {@code type} is concerned; null where the code isn't followed. */
	private int[] held(Type type, int slot) {
		InsnList insns = method.instructions;
		int size = insns.size();
		if (successors == null && followed) flow();
		if (!followed || size == 0) return null;
		boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
		int argSlots = isStatic ? 0 : 1;
		int initial = !isStatic && slot == 0 ? of(Type.getObjectType(owner), type) : UNSET;
		for (Type arg : Type.getArgumentTypes(method.desc)) {
			if (argSlots == slot) initial = of(arg, type);
			argSlots += arg.getSize();
		}
		Map<AbstractInsnNode, String> caught = MixinPreflight.catchTypes(method);
		int[] held = new int[size];
		held[0] = initial;
		ArrayDeque<Integer> queue = new ArrayDeque<>(List.of(0));
		while (!queue.isEmpty()) {
			int i = queue.poll();
			int in = held[i];
			for (int handler : handlers.get(i)) merge(held, handler, in, queue);
			int out = in;
			if (insns.get(i) instanceof VarInsnNode var && var.getOpcode() >= Opcodes.ISTORE && var.getOpcode() <= Opcodes.ASTORE) {
				boolean wide = var.getOpcode() == Opcodes.LSTORE || var.getOpcode() == Opcodes.DSTORE;
				if (var.var == slot) {
					out = switch (var.getOpcode()) {
						case Opcodes.ISTORE -> INT;
						case Opcodes.FSTORE -> FLOAT;
						case Opcodes.LSTORE -> LONG;
						case Opcodes.DSTORE -> DOUBLE;
						default -> {
							Type stored = MixinPreflight.flowType(method, i, owner, isStatic, argSlots, caught);
							yield stored == null ? UNSURE : stored.getSort() == Type.VOID ? NULL : of(stored, type);
						}
					};
				} else if (var.var == slot - 1 && wide || var.var == slot + 1 && (in == LONG || in == DOUBLE)) {
					out = UNSET;
				}
			}
			for (int next : successors.get(i)) merge(held, next, out, queue);
		}
		return held;
	}

	private int of(Type value, Type type) {
		return switch (value.getSort()) {
			case Type.OBJECT, Type.ARRAY -> value.equals(type) ? EXACT : MixinPreflight.mayBeTyped(value, type, hierarchy) ? UNSURE : OTHER;
			case Type.FLOAT -> FLOAT;
			case Type.LONG -> LONG;
			case Type.DOUBLE -> DOUBLE;
			default -> INT;
		};
	}

	private static void merge(int[] held, int at, int value, ArrayDeque<Integer> queue) {
		int before = held[at];
		int merged;
		if (before == UNREACHED || before == value) merged = value;
		else if (before == UNSET || value == UNSET) merged = UNSET;
		else if (before == VAGUE || value == VAGUE) merged = VAGUE;
		else if (before >= INT || value >= INT) merged = before == NULL || value == NULL ? VAGUE : UNSET;
		else if (before == NULL) merged = value;
		else if (value == NULL) merged = before;
		else merged = UNSURE;
		if (merged != before) {
			held[at] = merged;
			queue.add(at);
		}
	}

	/** Where each instruction leads, and the handlers of the try blocks it is in. */
	private void flow() {
		InsnList insns = method.instructions;
		int size = insns.size();
		List<List<Integer>> next = new ArrayList<>(size);
		List<List<Integer>> caught = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			AbstractInsnNode insn = insns.get(i);
			List<Integer> to = new ArrayList<>(2);
			int op = insn.getOpcode();
			if (op == Opcodes.JSR || op == Opcodes.RET) {
				followed = false;
				return;
			}
			if (insn instanceof JumpInsnNode jump) {
				to.add(insns.indexOf(jump.label));
				if (op != Opcodes.GOTO && i + 1 < size) to.add(i + 1);
			} else if (insn instanceof TableSwitchInsnNode table) {
				to.add(insns.indexOf(table.dflt));
				for (LabelNode label : table.labels) to.add(insns.indexOf(label));
			} else if (insn instanceof LookupSwitchInsnNode lookup) {
				to.add(insns.indexOf(lookup.dflt));
				for (LabelNode label : lookup.labels) to.add(insns.indexOf(label));
			} else if (!(op >= Opcodes.IRETURN && op <= Opcodes.RETURN || op == Opcodes.ATHROW) && i + 1 < size) {
				to.add(i + 1);
			}
			next.add(to);
			caught.add(new ArrayList<>(0));
		}
		for (TryCatchBlockNode block : method.tryCatchBlocks) {
			int handler = insns.indexOf(block.handler);
			for (int i = insns.indexOf(block.start); i < insns.indexOf(block.end); i++) caught.get(i).add(handler);
		}
		successors = next;
		handlers = caught;
	}
}
