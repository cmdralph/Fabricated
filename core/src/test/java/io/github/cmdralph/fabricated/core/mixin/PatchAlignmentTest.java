package io.github.cmdralph.fabricated.core.mixin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

class PatchAlignmentTest {
	/** {@code this.helper(i)} for each {@code i}, a {@code -1} standing for {@code this.helper(0, null)}, an overload. */
	private static MethodNode calls(int... args) {
		MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
		for (int arg : args) {
			method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
			method.instructions.add(new InsnNode(Opcodes.ICONST_0 + Math.max(arg, 0)));
			if (arg < 0) method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
			method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "game/Thing", "helper", arg < 0 ? "(ILjava/lang/Object;)V" : "(I)V"));
		}
		method.instructions.add(new InsnNode(Opcodes.RETURN));
		return method;
	}

	@Test
	void instructionsThePatchKeptHaveTheirPlaceAndAReplacedOneWhatThePatchPutThere() {
		// Vanilla: helper(1), helper(2), helper(3); the patch made the second helper(0, null).
		PatchAlignment alignment = PatchAlignment.of(calls(1, 2, 3), calls(1, -1, 3));

		assertEquals(2, alignment.placeOf(2), "the first call stays");
		assertEquals(9, alignment.placeOf(8), "so does the last, after the longer replacement");
		assertEquals(-1, alignment.placeOf(5), "the second was replaced");
		// Between the second call's receiver and the third's: its argument, the null and the overload's call.
		assertArrayEquals(new int[] {4, 7}, alignment.replacementOf(5));
		assertNull(alignment.replacementOf(2), "a kept instruction has no replacement");
	}

	@Test
	void whichCopyOfARepeatedSequenceThePatchRemovedIsUncertain() {
		MethodNode vanilla = calls(1, 1);
		MethodNode patched = calls(1);
		PatchAlignment alignment = PatchAlignment.of(vanilla, patched);

		assertEquals(PatchAlignment.UNCERTAIN, alignment.placeOf(2));
		assertEquals(PatchAlignment.UNCERTAIN, alignment.placeOf(5));
		assertNull(alignment.replacementOf(5));
	}

	@Test
	void aReplacementBesideAnInstructionThePatchRepeatsIsWhatBothAlignmentsPlaceThere() {
		// As NeoForge's Camera.alignWithEntity: vanilla's setRotation(-xRot) became setRotation(-xRot, -roll), so vanilla's
		// FNEG pairs with the patch's first from the front and with its second from the back.
		MethodNode vanilla = method(new InsnNode(Opcodes.ICONST_1), new VarInsnNode(Opcodes.ALOAD, 0), xRot(), new InsnNode(Opcodes.FNEG),
				setRotation("(F)V"), new InsnNode(Opcodes.ICONST_2), new InsnNode(Opcodes.RETURN));
		MethodNode patched = method(new InsnNode(Opcodes.ICONST_3), new VarInsnNode(Opcodes.ALOAD, 0), xRot(), new InsnNode(Opcodes.FNEG),
				new VarInsnNode(Opcodes.ALOAD, 0), new FieldInsnNode(Opcodes.GETFIELD, "game/Camera", "roll", "F"), new InsnNode(Opcodes.FNEG),
				setRotation("(FF)V"), new InsnNode(Opcodes.ICONST_4), new InsnNode(Opcodes.RETURN));
		PatchAlignment alignment = PatchAlignment.of(vanilla, patched);

		assertEquals(PatchAlignment.UNCERTAIN, alignment.placeOf(3), "which FNEG is vanilla's");
		assertArrayEquals(new int[] {7, 9}, alignment.replacementOf(4), "the overload's call, and what follows it until the return");
	}

	private static MethodNode method(AbstractInsnNode... insns) {
		MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "alignWithEntity", "()V", null, null);
		for (AbstractInsnNode insn : insns) method.instructions.add(insn);
		return method;
	}

	private static FieldInsnNode xRot() {
		return new FieldInsnNode(Opcodes.GETFIELD, "game/Camera", "xRot", "F");
	}

	private static MethodInsnNode setRotation(String desc) {
		return new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "game/Camera", "setRotation", desc);
	}
}
