package io.github.cmdralph.fabricated.core.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.MethodNode;

class RecordedFramesTest {
	private static final String DESC = "(ZFLjava/lang/Object;)V";

	@Test
	void aChopAfterAnAppendOfFewerLocalsThanTheArgumentsDropsLiveOnes() {
		// CORPUS-79: Forge 61's ScreenEffectRenderer.renderScreenEffect has four argument slots, appends three locals
		// (its PoseStack among them), then chops one. Mixin records each as four, so after the chop it counts four locals
		// where six are live, and Resourceful Lib's @Local PoseStack found none.
		byte[] forge = method(mv -> {
			Label appended = new Label();
			Label chopped = new Label();
			storeLocals(mv, 3);
			mv.visitVarInsn(Opcodes.ILOAD, 1);
			mv.visitJumpInsn(Opcodes.IFEQ, appended);
			mv.visitVarInsn(Opcodes.ALOAD, 6);
			mv.visitInsn(Opcodes.POP);
			mv.visitLabel(appended);
			mv.visitFrame(Opcodes.F_APPEND, 3, new Object[] {"java/lang/StringBuilder", "java/lang/Object", "java/lang/Object"}, 0, null);
			mv.visitVarInsn(Opcodes.ILOAD, 1);
			mv.visitJumpInsn(Opcodes.IFEQ, chopped);
			mv.visitVarInsn(Opcodes.ALOAD, 5);
			mv.visitInsn(Opcodes.POP);
			mv.visitLabel(chopped);
			mv.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
			mv.visitVarInsn(Opcodes.ALOAD, 4);
			mv.visitInsn(Opcodes.POP);
		});
		MethodNode walked = expanded(forge);
		List<RecordedFrames.Frame> recorded = recordedByMixin(forge);
		assertEquals(List.of(1), RecordedFrames.droppingChops(walked, recorded));

		// The repair records the chop as Mixin records a full frame of the locals it states: six, and then none drops.
		List<RecordedFrames.Frame> full = recorded(RecordedFrames.asFullFrames(walked));
		assertEquals(new RecordedFrames.Frame(recorded.get(1).index(), Opcodes.F_FULL, 6), full.get(1));
		List<RecordedFrames.Frame> repaired = new ArrayList<>(recorded);
		repaired.set(1, full.get(1));
		assertEquals(List.of(), RecordedFrames.droppingChops(walked, repaired));
	}

	@Test
	void aChopAfterARepairedOneIsJudgedFromTheLocalsTheRepairedOneStates() {
		// Two chops of one local each after the append of three: Mixin, given the first as six locals, still takes four
		// for the second, leaving four of the five live.
		byte[] forge = method(mv -> {
			Label appended = new Label();
			Label chopped = new Label();
			Label again = new Label();
			storeLocals(mv, 3);
			mv.visitVarInsn(Opcodes.ILOAD, 1);
			mv.visitJumpInsn(Opcodes.IFEQ, appended);
			mv.visitInsn(Opcodes.NOP);
			mv.visitLabel(appended);
			mv.visitFrame(Opcodes.F_APPEND, 3, new Object[] {"java/lang/StringBuilder", "java/lang/Object", "java/lang/Object"}, 0, null);
			mv.visitVarInsn(Opcodes.ILOAD, 1);
			mv.visitJumpInsn(Opcodes.IFEQ, chopped);
			mv.visitInsn(Opcodes.NOP);
			mv.visitLabel(chopped);
			mv.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
			mv.visitVarInsn(Opcodes.ILOAD, 1);
			mv.visitJumpInsn(Opcodes.IFEQ, again);
			mv.visitInsn(Opcodes.NOP);
			mv.visitLabel(again);
			mv.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
			mv.visitVarInsn(Opcodes.ALOAD, 4);
			mv.visitInsn(Opcodes.POP);
		});
		assertEquals(List.of(1, 2), RecordedFrames.droppingChops(expanded(forge), recordedByMixin(forge)));
	}

	@Test
	void appendsOfOneLocalEachAreOverCountedAndDropNone() {
		// As vanilla's method is shaped: Mixin counts more locals than are live, and drops none of them.
		byte[] vanilla = method(mv -> {
			Label first = new Label();
			Label second = new Label();
			Label chopped = new Label();
			storeLocals(mv, 1);
			mv.visitVarInsn(Opcodes.ILOAD, 1);
			mv.visitJumpInsn(Opcodes.IFEQ, first);
			mv.visitInsn(Opcodes.NOP);
			mv.visitLabel(first);
			mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {"java/lang/StringBuilder"}, 0, null);
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitVarInsn(Opcodes.ASTORE, 5);
			mv.visitVarInsn(Opcodes.ILOAD, 1);
			mv.visitJumpInsn(Opcodes.IFEQ, second);
			mv.visitInsn(Opcodes.NOP);
			mv.visitLabel(second);
			mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {"java/lang/Object"}, 0, null);
			mv.visitVarInsn(Opcodes.ILOAD, 1);
			mv.visitJumpInsn(Opcodes.IFEQ, chopped);
			mv.visitVarInsn(Opcodes.ALOAD, 5);
			mv.visitInsn(Opcodes.POP);
			mv.visitLabel(chopped);
			mv.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
			mv.visitVarInsn(Opcodes.ALOAD, 4);
			mv.visitInsn(Opcodes.POP);
		});
		assertEquals(List.of(), RecordedFrames.droppingChops(expanded(vanilla), recordedByMixin(vanilla)));
	}

	@Test
	void framesThatDontLineUpWithTheRecordedOnesAreLeftAlone() {
		byte[] forge = method(mv -> {
			Label appended = new Label();
			Label chopped = new Label();
			storeLocals(mv, 3);
			mv.visitVarInsn(Opcodes.ILOAD, 1);
			mv.visitJumpInsn(Opcodes.IFEQ, appended);
			mv.visitInsn(Opcodes.NOP);
			mv.visitLabel(appended);
			mv.visitFrame(Opcodes.F_APPEND, 3, new Object[] {"java/lang/StringBuilder", "java/lang/Object", "java/lang/Object"}, 0, null);
			mv.visitVarInsn(Opcodes.ILOAD, 1);
			mv.visitJumpInsn(Opcodes.IFEQ, chopped);
			mv.visitInsn(Opcodes.NOP);
			mv.visitLabel(chopped);
			mv.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
		});
		List<RecordedFrames.Frame> recorded = recordedByMixin(forge);
		assertEquals(List.of(), RecordedFrames.droppingChops(expanded(forge), recorded.subList(0, 1)), "one frame fewer than the method has");
		List<RecordedFrames.Frame> shifted = new ArrayList<>();
		for (RecordedFrames.Frame frame : recorded) shifted.add(new RecordedFrames.Frame(frame.index() + 1, frame.type(), frame.size()));
		assertEquals(List.of(), RecordedFrames.droppingChops(expanded(forge), shifted), "frames at other instructions");
		assertEquals(List.of(), RecordedFrames.droppingChops(read(forge, 0), recorded), "a method read without its frames expanded");
	}

	/** Stores a {@code StringBuilder} in slot 4, then {@code this} in the next {@code more - 1} slots. */
	private static void storeLocals(MethodVisitor mv, int more) {
		mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
		mv.visitInsn(Opcodes.DUP);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
		mv.visitVarInsn(Opcodes.ASTORE, 4);
		for (int slot = 5; slot < 4 + more; slot++) {
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitVarInsn(Opcodes.ASTORE, slot);
		}
	}

	/** A class with an instance method {@code render(ZFLjava/lang/Object;)V} of this code, its frames as given. */
	private static byte[] method(Consumer<MethodVisitor> code) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "test/Renderer", null, "java/lang/Object", null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "render", DESC, null, null);
		mv.visitCode();
		code.accept(mv);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(2, 8);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** The method as ModLauncher hands it to Mixin: its frames expanded. */
	private static MethodNode expanded(byte[] data) {
		return read(data, ClassReader.EXPAND_FRAMES);
	}

	private static MethodNode read(byte[] data, int flags) {
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, flags);
		return node.methods.stream().filter(m -> m.name.equals("render")).findFirst().orElseThrow();
	}

	/** The frames as Mixin 0.8's class metadata records them: from the class read without expanding its frames. */
	private static List<RecordedFrames.Frame> recordedByMixin(byte[] data) {
		return recorded(read(data, 0));
	}

	/**
	 * The frames as Mixin 0.8 records a method's ({@code ClassInfo.FrameData}): each one's size
	 * {@code Locals.computeFrameSize(frame, initialFrameSize)}, at least the method's argument slots.
	 */
	private static List<RecordedFrames.Frame> recorded(MethodNode method) {
		int initial = 1;
		for (Type argument : Type.getArgumentTypes(method.desc)) initial += argument.getSize();
		List<RecordedFrames.Frame> frames = new ArrayList<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (!(insn instanceof FrameNode frame)) continue;
			int size = 0;
			if (frame.local == null) {
				size = initial;
			} else {
				for (Object local : frame.local) size += local == Opcodes.LONG || local == Opcodes.DOUBLE ? 2 : 1;
			}
			frames.add(new RecordedFrames.Frame(method.instructions.indexOf(insn), frame.type, Math.max(initial, size)));
		}
		return frames;
	}
}
