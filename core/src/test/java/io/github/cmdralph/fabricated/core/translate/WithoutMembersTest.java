package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;

class WithoutMembersTest {
	private static final String MIXIN = "com/example/mixin/TagFileMixin";

	@Test
	void aLeftOutFieldTakesItsStoresAndInitialiserWithIt() throws Exception {
		// As fabric-tag-api-v1's TagFileMixin: a field the platform's target has too, initialised in the constructor and
		// set by a method that stays. Left in, Mixin would point the stores at the target's field.
		Set<String> missing = new TreeSet<>(Set.of("remove", "count", "gone"));
		byte[] stripped = ModTranslator.withoutMembers(mixinWithField(), Set.of("remove", "count", "gone"), missing);

		ClassNode node = new ClassNode();
		new ClassReader(stripped).accept(node, 0);
		assertTrue(node.fields.isEmpty(), "the fields go");
		assertEquals(List.of("<init>", "setRemove", "setCount", "kept"), node.methods.stream().map(m -> m.name).toList(), "the accessor of the same name goes");
		for (MethodNode method : node.methods) {
			for (AbstractInsnNode insn : method.instructions) {
				if (insn instanceof FieldInsnNode f && f.owner.equals(MIXIN)) {
					assertTrue(f.getOpcode() == Opcodes.GETSTATIC || f.getOpcode() == Opcodes.GETFIELD, method.name + " still stores to " + f.name);
				}
			}
		}
		assertEquals(Set.of("gone"), missing);

		// The JVM verifies what's left: the constructor and the setters still run.
		Class<?> type = new ClassLoader(WithoutMembersTest.class.getClassLoader()) {
			Class<?> define() {
				return defineClass(MIXIN.replace('/', '.'), stripped, 0, stripped.length);
			}
		}.define();
		Object instance = type.getDeclaredConstructor().newInstance();
		type.getMethod("setRemove", List.class).invoke(instance, new ArrayList<>());
		type.getMethod("setCount", long.class).invoke(null, 7L);
		assertEquals(3, type.getMethod("kept").invoke(instance));
	}

	@Test
	void storesToFieldsKeptStay() {
		byte[] stripped = ModTranslator.withoutMembers(mixinWithField(), Set.of("kept"), new TreeSet<>());
		ClassNode node = new ClassNode();
		new ClassReader(stripped).accept(node, 0);
		long stores = node.methods.stream().flatMap(m -> java.util.stream.StreamSupport.stream(m.instructions.spliterator(), false))
				.filter(i -> i.getOpcode() == Opcodes.PUTFIELD || i.getOpcode() == Opcodes.PUTSTATIC).count();
		assertEquals(3, stores);
	}

	/**
	 * A class with a {@code List remove} field the constructor initialises, its accessor {@code remove()}, a static
	 * {@code long count}, setters for both, and a method that touches neither.
	 */
	private static byte[] mixinWithField() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, MIXIN, null, "java/lang/Object", null);
		cw.visitField(Opcodes.ACC_PRIVATE, "remove", "Ljava/util/List;", null, null).visitEnd();
		cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "count", "J", null, null).visitEnd();

		MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		init.visitCode();
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESTATIC, Type.COLLECTIONS, "emptyList", "()Ljava/util/List;", false);
		init.visitFieldInsn(Opcodes.PUTFIELD, MIXIN, "remove", "Ljava/util/List;");
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(0, 0);
		init.visitEnd();

		MethodVisitor remove = cw.visitMethod(Opcodes.ACC_PUBLIC, "remove", "()Ljava/util/List;", null, null);
		remove.visitCode();
		remove.visitVarInsn(Opcodes.ALOAD, 0);
		remove.visitFieldInsn(Opcodes.GETFIELD, MIXIN, "remove", "Ljava/util/List;");
		remove.visitInsn(Opcodes.ARETURN);
		remove.visitMaxs(0, 0);
		remove.visitEnd();

		MethodVisitor setRemove = cw.visitMethod(Opcodes.ACC_PUBLIC, "setRemove", "(Ljava/util/List;)V", null, null);
		setRemove.visitCode();
		setRemove.visitVarInsn(Opcodes.ALOAD, 0);
		setRemove.visitVarInsn(Opcodes.ALOAD, 1);
		setRemove.visitFieldInsn(Opcodes.PUTFIELD, MIXIN, "remove", "Ljava/util/List;");
		setRemove.visitInsn(Opcodes.RETURN);
		setRemove.visitMaxs(0, 0);
		setRemove.visitEnd();

		MethodVisitor setCount = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "setCount", "(J)V", null, null);
		setCount.visitCode();
		setCount.visitVarInsn(Opcodes.LLOAD, 0);
		setCount.visitFieldInsn(Opcodes.PUTSTATIC, MIXIN, "count", "J");
		setCount.visitInsn(Opcodes.RETURN);
		setCount.visitMaxs(0, 0);
		setCount.visitEnd();

		MethodVisitor kept = cw.visitMethod(Opcodes.ACC_PUBLIC, "kept", "()I", null, null);
		kept.visitCode();
		kept.visitInsn(Opcodes.ICONST_3);
		kept.visitInsn(Opcodes.IRETURN);
		kept.visitMaxs(0, 0);
		kept.visitEnd();

		cw.visitEnd();
		return cw.toByteArray();
	}

	private static final class Type {
		static final String COLLECTIONS = Collections.class.getName().replace('.', '/');
	}
}
