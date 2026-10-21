package io.github.cmdralph.fabricated.core.mixin;

import java.util.Arrays;

import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * Where each instruction of vanilla's version of a method is in NeoForge's patched one (VANILLA-1): the two instruction
 * sequences aligned on a longest common subsequence, an instruction compared by its opcode and what it names or pushes
 * (not by local variable slots, which a patch adding a local shifts). An instruction NeoForge's patch changed or
 * removed has no place; the patched instructions between the places of its aligned neighbours are what the patch put
 * there instead. Where a method repeats the same instructions, which copy a patch changed can't be told: the alignment
 * is made matching from the front and from the back, and only what both agree on is answered. Labels, line numbers and
 * frames take no part.
 */
final class PatchAlignment {
	/** An instruction whose place the two alignments disagree on. */
	static final int UNCERTAIN = -2;
	/** Methods whose differing middle part would need more cells than this are not aligned. */
	private static final long MAX_CELLS = 4_000_000;

	private final int[] fromFront;
	private final int[] fromBack;
	private final int patchedSize;

	private PatchAlignment(int[] fromFront, int[] fromBack, int patchedSize) {
		this.fromFront = fromFront;
		this.fromBack = fromBack;
		this.patchedSize = patchedSize;
	}

	/** The alignment of the two methods' instructions, or null when they are too large to align. */
	static PatchAlignment of(MethodNode vanilla, MethodNode patched) {
		int[] vanillaReal = realInstructions(vanilla);
		int[] patchedReal = realInstructions(patched);
		String[] a = tokens(vanilla, vanillaReal);
		String[] b = tokens(patched, patchedReal);
		int[] front = match(a, b);
		int[] back = match(reversed(a), reversed(b));
		if (front == null || back == null) return null;
		int[] fromFront = new int[vanilla.instructions.size()];
		int[] fromBack = new int[vanilla.instructions.size()];
		Arrays.fill(fromFront, -1);
		Arrays.fill(fromBack, -1);
		for (int k = 0; k < a.length; k++) {
			if (front[k] >= 0) fromFront[vanillaReal[k]] = patchedReal[front[k]];
			int r = back[a.length - 1 - k];
			if (r >= 0) fromBack[vanillaReal[k]] = patchedReal[b.length - 1 - r];
		}
		return new PatchAlignment(fromFront, fromBack, patched.instructions.size());
	}

	/**
	 * The index in the patched method of vanilla's instruction at {@code vanillaIndex}; -1 when the patch changed it,
	 * {@link #UNCERTAIN} when that can't be told.
	 */
	int placeOf(int vanillaIndex) {
		return fromFront[vanillaIndex] == fromBack[vanillaIndex] ? fromFront[vanillaIndex] : UNCERTAIN;
	}

	/**
	 * The patched instructions, as {@code [from, to)} indices, between the places of the aligned instructions around
	 * vanilla's at {@code vanillaIndex}: what NeoForge's patch put where it changed that instruction. Null when it has
	 * a place, or when that can't be told.
	 */
	int[] replacementOf(int vanillaIndex) {
		if (placeOf(vanillaIndex) != -1) return null;
		int[] front = between(fromFront, vanillaIndex);
		int[] back = between(fromBack, vanillaIndex);
		return front != null && Arrays.equals(front, back) ? front : null;
	}

	private int[] between(int[] places, int vanillaIndex) {
		int from = 0;
		for (int i = vanillaIndex - 1; i >= 0; i--) {
			if (places[i] >= 0) {
				from = places[i] + 1;
				break;
			}
		}
		int to = patchedSize;
		for (int i = vanillaIndex + 1; i < places.length; i++) {
			if (places[i] >= 0) {
				to = places[i];
				break;
			}
		}
		return from <= to ? new int[] {from, to} : null;
	}

	/**
	 * For each token of {@code a}, the index of the token of {@code b} it is matched with on a longest common
	 * subsequence taken from the front (-1 for none); null when the differing middle part is too large.
	 */
	private static int[] match(String[] a, String[] b) {
		int[] match = new int[a.length];
		Arrays.fill(match, -1);
		int start = 0;
		while (start < a.length && start < b.length && a[start].equals(b[start])) {
			match[start] = start;
			start++;
		}
		int endA = a.length;
		int endB = b.length;
		while (endA > start && endB > start && a[endA - 1].equals(b[endB - 1])) {
			match[--endA] = --endB;
		}
		int n = endA - start;
		int m = endB - start;
		if ((long) n * m > MAX_CELLS) return null;
		if (n == 0 || m == 0) return match;
		// Lengths of the longest common subsequences of the suffixes, then walked from the front.
		int[][] lcs = new int[n + 1][m + 1];
		for (int i = n - 1; i >= 0; i--) {
			for (int j = m - 1; j >= 0; j--) {
				lcs[i][j] = a[start + i].equals(b[start + j]) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
			}
		}
		int i = 0;
		int j = 0;
		while (i < n && j < m) {
			if (a[start + i].equals(b[start + j]) && lcs[i][j] == lcs[i + 1][j + 1] + 1) {
				match[start + i] = start + j;
				i++;
				j++;
			} else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
				i++;
			} else {
				j++;
			}
		}
		return match;
	}

	private static String[] reversed(String[] tokens) {
		String[] reversed = new String[tokens.length];
		for (int k = 0; k < tokens.length; k++) reversed[k] = tokens[tokens.length - 1 - k];
		return reversed;
	}

	private static int[] realInstructions(MethodNode method) {
		int[] real = new int[method.instructions.size()];
		int count = 0;
		for (int i = 0; i < method.instructions.size(); i++) {
			if (method.instructions.get(i).getOpcode() >= 0) real[count++] = i;
		}
		return Arrays.copyOf(real, count);
	}

	private static String[] tokens(MethodNode method, int[] real) {
		String[] tokens = new String[real.length];
		for (int k = 0; k < real.length; k++) tokens[k] = token(method.instructions.get(real[k]));
		return tokens;
	}

	/** An instruction as it is compared: its opcode and what it names or pushes, without local variable slots or jump targets. */
	private static String token(AbstractInsnNode insn) {
		String op = Integer.toString(insn.getOpcode());
		return switch (insn) {
			case MethodInsnNode m -> op + " " + m.owner + "." + m.name + m.desc;
			case FieldInsnNode f -> op + " " + f.owner + "." + f.name + ":" + f.desc;
			case TypeInsnNode t -> op + " " + t.desc;
			case LdcInsnNode l -> op + " " + l.cst.getClass().getSimpleName() + ":" + l.cst;
			case IntInsnNode i -> op + " " + i.operand;
			case InvokeDynamicInsnNode d -> op + " " + d.name + d.desc + " " + handle(d.bsm);
			case MultiANewArrayInsnNode a -> op + " " + a.desc + " " + a.dims;
			case TableSwitchInsnNode s -> op + " " + s.min + ".." + s.max;
			case LookupSwitchInsnNode s -> op + " " + s.keys;
			default -> op;
		};
	}

	private static String handle(Handle h) {
		return h.getOwner() + "." + h.getName() + h.getDesc();
	}
}
