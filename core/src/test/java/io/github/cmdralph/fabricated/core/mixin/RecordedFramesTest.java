package io.github.cmdralph.fabricated.core.mixin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

class RecordedFramesTest {
	private static final String OWNER = "test/Renderer";
	private static final String RENDER = "(ZFLjava/lang/Object;)V";

	@Test
	void mixinCountsTooFewLocalsAfterAChopFromTheFramesItRecorded() {
		// CORPUS-79: Forge 61's ScreenEffectRenderer.renderScreenEffect has four argument slots, appends three locals
		// (its PoseStack among them), then chops one. Mixin records each as four, so after the chop it counts four locals
		// where six are live, and Resourceful Lib's @Local PoseStack found none.
		byte[] forge = method(Opcodes.ACC_PUBLIC, "render", RENDER, mv -> {
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
		// As Forge's ModLauncher hands it to Mixin, and as Mixin 0.8.7 records it: both compressed.
		MethodNode walked = read(forge, 0);
		assertArrayEquals(new int[] {8, 4}, RecordedFrames.counted(walked, recorded(walked)));
		assertArrayEquals(new int[] {8, 4}, RecordedFrames.counted(walked, recorded(walked).subList(0, 1)), "the chop counted from itself");
		// FRAME-1: as NeoForge's FML (and Fabric Loader) hand it to Mixin, expanded, and as Fabric's Mixin records it from the
		// class read compressed: the same count.
		MethodNode walkedExpanded = read(forge, ClassReader.EXPAND_FRAMES);
		assertArrayEquals(new int[] {8, 4}, RecordedFrames.counted(walkedExpanded, recorded(walked)));

		// Recorded from its expanded frames, as the repair records them, Mixin counts the locals there are.
		assertArrayEquals(new int[] {7, 6}, RecordedFrames.counted(walked, recorded(RecordedFrames.withExpandedFrames(OWNER, walked))));
		assertArrayEquals(new int[] {7, 6}, RecordedFrames.counted(walkedExpanded, recorded(RecordedFrames.withExpandedFrames(OWNER, walkedExpanded))));
	}

	@Test
	void mixinCountsTooManyLocalsAfterAppendsOfFewerThanTheArguments() {
		// As vanilla's method is shaped: two appends of one local each, then a chop of one.
		byte[] vanilla = method(Opcodes.ACC_PUBLIC, "render", RENDER, mv -> {
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
		MethodNode walked = read(vanilla, 0);
		assertArrayEquals(new int[] {8, 12, 8}, RecordedFrames.counted(walked, recorded(walked)));
		assertArrayEquals(new int[] {5, 6, 5}, RecordedFrames.counted(walked, recorded(RecordedFrames.withExpandedFrames(OWNER, walked))));
	}

	@Test
	void mixinCountsAChopOfALongAsOneSlot() {
		// Where the arguments take one slot, appends and chops are recorded as they are, but a chop of a long takes two.
		byte[] longs = method(Opcodes.ACC_PUBLIC, "tick", "()V", mv -> {
			Label appended = new Label();
			Label chopped = new Label();
			mv.visitInsn(Opcodes.LCONST_0);
			mv.visitVarInsn(Opcodes.LSTORE, 1);
			mv.visitInsn(Opcodes.ICONST_0);
			mv.visitJumpInsn(Opcodes.IFEQ, appended);
			mv.visitInsn(Opcodes.NOP);
			mv.visitLabel(appended);
			mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {Opcodes.LONG}, 0, null);
			mv.visitInsn(Opcodes.ICONST_0);
			mv.visitJumpInsn(Opcodes.IFEQ, chopped);
			mv.visitInsn(Opcodes.NOP);
			mv.visitLabel(chopped);
			mv.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
		});
		MethodNode walked = read(longs, 0);
		assertArrayEquals(new int[] {3, 2}, RecordedFrames.counted(walked, recorded(walked)));
		assertArrayEquals(new int[] {3, 1}, RecordedFrames.counted(walked, recorded(RecordedFrames.withExpandedFrames(OWNER, walked))));
	}

	@Test
	void aMethodMixinCountsRightCountsTheSameFromItsExpandedFrames() {
		byte[] counted = method(Opcodes.ACC_PUBLIC, "tick", "()V", mv -> {
			Label[] at = {new Label(), new Label(), new Label(), new Label(), new Label()};
			mv.visitInsn(Opcodes.ICONST_0);
			mv.visitJumpInsn(Opcodes.IFEQ, at[4]);
			mv.visitInsn(Opcodes.NOP);
			mv.visitLabel(at[0]);
			mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {Opcodes.INTEGER}, 0, null);
			mv.visitInsn(Opcodes.NOP);
			mv.visitLabel(at[1]);
			mv.visitFrame(Opcodes.F_APPEND, 2, new Object[] {"java/lang/Object", Opcodes.FLOAT}, 0, null);
			mv.visitInsn(Opcodes.NOP);
			mv.visitLabel(at[2]);
			mv.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
			mv.visitInsn(Opcodes.NOP);
			mv.visitLabel(at[3]);
			mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			mv.visitInsn(Opcodes.NOP);
			mv.visitLabel(at[4]);
			mv.visitFrame(Opcodes.F_FULL, 2, new Object[] {OWNER, Opcodes.INTEGER}, 0, new Object[0]);
		});
		MethodNode walked = read(counted, 0);
		int[] expected = {2, 4, 3, 3, 2};
		assertArrayEquals(expected, RecordedFrames.counted(walked, recorded(walked)));
		assertArrayEquals(expected, RecordedFrames.counted(walked, recorded(RecordedFrames.withExpandedFrames(OWNER, walked))));
	}

	@Test
	void framesAreExpandedAsAsmExpandsThem() {
		assertExpandedAsAsm(method(Opcodes.ACC_PUBLIC, "render", RENDER, mv -> {
			storeLocals(mv, 3);
			frame(mv, Opcodes.F_APPEND, new Object[] {"java/lang/StringBuilder", "java/lang/Object", "java/lang/Object"}, null);
			frame(mv, Opcodes.F_SAME1, null, new Object[] {Opcodes.INTEGER});
			frame(mv, Opcodes.F_CHOP, new Object[2], null);
			frame(mv, Opcodes.F_SAME, null, null);
		}));
		// A constructor: `this` uninitialized until its super call, and a new object uninitialized on the stack.
		assertExpandedAsAsm(method(Opcodes.ACC_PUBLIC, "<init>", "(J[ILjava/lang/String;)V", mv -> {
			Label created = new Label();
			frame(mv, Opcodes.F_SAME1, null, new Object[] {Opcodes.INTEGER});
			mv.visitLabel(created);
			mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
			mv.visitInsn(Opcodes.DUP);
			frame(mv, Opcodes.F_FULL, new Object[] {Opcodes.UNINITIALIZED_THIS, Opcodes.LONG, "[I", "java/lang/String"}, new Object[] {created, created});
			frame(mv, Opcodes.F_FULL, new Object[] {OWNER, Opcodes.LONG, "[I", "java/lang/String"}, null);
			frame(mv, Opcodes.F_APPEND, new Object[] {Opcodes.DOUBLE, Opcodes.TOP, "java/lang/Object"}, null);
			frame(mv, Opcodes.F_SAME, null, null);
			frame(mv, Opcodes.F_CHOP, new Object[2], null);
			frame(mv, Opcodes.F_SAME1, null, new Object[] {Opcodes.NULL});
		}));
		// A static method: no `this`, and arguments of every kind of type.
		assertExpandedAsAsm(method(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "mix", "(D[[Ljava/lang/Object;JBCSZF)V", mv -> {
			frame(mv, Opcodes.F_APPEND, new Object[] {Opcodes.INTEGER}, null);
			frame(mv, Opcodes.F_APPEND, new Object[] {Opcodes.LONG, "java/lang/String"}, null);
			frame(mv, Opcodes.F_CHOP, new Object[3], null);
			frame(mv, Opcodes.F_FULL, new Object[0], new Object[] {Opcodes.FLOAT});
			frame(mv, Opcodes.F_APPEND, new Object[] {Opcodes.DOUBLE}, null);
		}));
	}

	/** The method's frames expanded from its compressed ones as ASM expands them, at the same instructions. */
	private static void assertExpandedAsAsm(byte[] data) {
		MethodNode compressed = read(data, 0);
		MethodNode asm = read(data, ClassReader.EXPAND_FRAMES);
		MethodNode expanded = RecordedFrames.withExpandedFrames(OWNER, compressed);
		assertEquals(compressed.instructions.size(), expanded.instructions.size(), "instructions");
		List<String> expected = new ArrayList<>();
		for (AbstractInsnNode insn : asm.instructions) {
			if (insn instanceof FrameNode frame) expected.add(describe(asm.instructions.indexOf(frame), frame, asm));
		}
		List<String> actual = new ArrayList<>();
		for (AbstractInsnNode insn : expanded.instructions) {
			// A label in the expanded frames is the compressed method's own, so it is found there.
			if (insn instanceof FrameNode frame) actual.add(describe(expanded.instructions.indexOf(frame), frame, compressed));
		}
		assertEquals(expected, actual, compressed.name + compressed.desc);
	}

	/** A frame's instruction index, type, locals and stack, a label as its instruction index in the method it is in. */
	private static String describe(int index, FrameNode frame, MethodNode labelsIn) {
		return index + " " + frame.type + " " + entries(labelsIn, frame.local) + " " + entries(labelsIn, frame.stack);
	}

	private static List<String> entries(MethodNode method, List<Object> entries) {
		List<String> described = new ArrayList<>();
		if (entries == null) return described;
		for (Object entry : entries) described.add(entry instanceof LabelNode label ? "label " + method.instructions.indexOf(label) : String.valueOf(entry));
		return described;
	}

	/** A compressed frame, after a {@code NOP} so that no two frames share an instruction. */
	private static void frame(MethodVisitor mv, int type, Object[] local, Object[] stack) {
		mv.visitInsn(Opcodes.NOP);
		mv.visitFrame(type, local == null ? 0 : local.length, local, stack == null ? 0 : stack.length, stack);
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

	/** A class {@code test/Renderer} with one method of this code, its frames written as given. */
	private static byte[] method(int access, String name, String desc, Consumer<MethodVisitor> code) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, OWNER, null, "java/lang/Object", null);
		MethodVisitor mv = cw.visitMethod(access, name, desc, null, null);
		mv.visitCode();
		code.accept(mv);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(4, 16);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static MethodNode read(byte[] data, int flags) {
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, flags);
		return node.methods.get(0);
	}

	/**
	 * The frames as Mixin 0.8.7 and Fabric's Mixin record a method's ({@code ClassInfo.FrameData}): each one's type, and its
	 * size {@code Locals.computeFrameSize(frame, initialFrameSize)}, at least the method's argument slots (Fabric's
	 * {@code FrameData.size}; its {@code rawSize}, without that floor, is read only at behaviour 0.9.2).
	 */
	private static List<RecordedFrames.Frame> recorded(MethodNode method) {
		int initial = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
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
			frames.add(new RecordedFrames.Frame(frame.type, Math.max(initial, size)));
		}
		return frames;
	}
}
