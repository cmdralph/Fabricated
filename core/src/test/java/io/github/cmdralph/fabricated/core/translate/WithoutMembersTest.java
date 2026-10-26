package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;

class WithoutMembersTest {
	private static final String MIXIN = "com/example/mixin/TagFileMixin";
	private static final String COLLECTIONS = Collections.class.getName().replace('.', '/');
	private static final String ARRAY_LIST = ArrayList.class.getName().replace('.', '/');

	@Test
	void aLeftOutFieldTakesItsStoresAndInitialiserWithIt() throws Exception {
		// As fabric-tag-api-v1's TagFileMixin: a field the platform's target has too, initialised in the constructor and
		// set by a method that stays. Left in, Mixin would point the stores at the target's field.
		Set<String> missing = new TreeSet<>(Set.of("remove", "count", "names", "gone"));
		byte[] stripped = ModTranslator.withoutMembers(mixinWithFields(), Set.of("remove", "count", "names", "gone"), missing);

		ClassNode node = new ClassNode();
		new ClassReader(stripped).accept(node, 0);
		assertEquals(List.of("size"), node.fields.stream().map(f -> f.name).toList(), "the fields left out go");
		assertEquals(List.of("<init>", "<init>", "setRemove", "setCount", "kept"), node.methods.stream().map(m -> m.name).toList(),
				"the accessor of the same name goes");
		for (MethodNode method : node.methods) {
			for (AbstractInsnNode insn : method.instructions) {
				if (insn instanceof FieldInsnNode f && f.owner.equals(MIXIN) && !f.name.equals("size")) {
					assertTrue(f.getOpcode() == Opcodes.GETSTATIC || f.getOpcode() == Opcodes.GETFIELD, method.name + " still stores to " + f.name);
				}
			}
		}
		assertEquals(Set.of("gone"), missing);

		// Mixin reads the initialisers as statements that each end in their field's store: the ones of the fields left
		// out go whole, the one of the field kept stays as it was.
		assertEquals(List.of(Opcodes.ALOAD, Opcodes.INVOKESPECIAL, Opcodes.ALOAD, Opcodes.ICONST_5, Opcodes.PUTFIELD, Opcodes.RETURN),
				opcodes(method(node, "<init>", "()V")));
		// A store of a parameter, in a constructor's body, can't be an initialiser: it becomes pops.
		assertEquals(List.of(Opcodes.ALOAD, Opcodes.INVOKESPECIAL, Opcodes.ALOAD, Opcodes.ALOAD, Opcodes.POP, Opcodes.POP, Opcodes.RETURN),
				opcodes(method(node, "<init>", "(Ljava/util/List;)V")));

		// The JVM verifies what's left: the constructors and the setters still run.
		Class<?> type = new ClassLoader(WithoutMembersTest.class.getClassLoader()) {
			Class<?> define() {
				return defineClass(MIXIN.replace('/', '.'), stripped, 0, stripped.length);
			}
		}.define();
		Object instance = type.getDeclaredConstructor().newInstance();
		assertEquals(5, type.getDeclaredField("size").getInt(instance));
		type.getDeclaredConstructor(List.class).newInstance(new ArrayList<>());
		type.getMethod("setRemove", List.class).invoke(instance, new ArrayList<>());
		type.getMethod("setCount", long.class).invoke(null, 7L);
		assertEquals(3, type.getMethod("kept").invoke(instance));
	}

	@Test
	void storesToFieldsKeptStay() {
		byte[] stripped = ModTranslator.withoutMembers(mixinWithFields(), Set.of("kept"), new TreeSet<>());
		ClassNode node = new ClassNode();
		new ClassReader(stripped).accept(node, 0);
		long stores = node.methods.stream().flatMap(m -> StreamSupport.stream(m.instructions.spliterator(), false))
				.filter(i -> i.getOpcode() == Opcodes.PUTFIELD || i.getOpcode() == Opcodes.PUTSTATIC).count();
		assertEquals(6, stores);
	}

	private static MethodNode method(ClassNode node, String name, String descriptor) {
		return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).findFirst().orElseThrow();
	}

	private static List<Integer> opcodes(MethodNode method) {
		List<Integer> opcodes = new ArrayList<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (insn.getOpcode() >= 0) opcodes.add(insn.getOpcode());
		}
		return opcodes;
	}

	/**
	 * A class as javac writes it, with line numbers: a {@code List remove} the constructor initialises and its accessor
	 * {@code remove()}, an {@code int size = 5} and a {@code List names = new ArrayList<>()}, a constructor that sets
	 * {@code remove} from its parameter, a static {@code long count}, setters for {@code remove} and {@code count}, and a
	 * method that touches none of them.
	 */
	private static byte[] mixinWithFields() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, MIXIN, null, "java/lang/Object", null);
		cw.visitField(Opcodes.ACC_PRIVATE, "remove", "Ljava/util/List;", null, null).visitEnd();
		cw.visitField(Opcodes.ACC_PUBLIC, "size", "I", null, null).visitEnd();
		cw.visitField(Opcodes.ACC_PRIVATE, "names", "Ljava/util/List;", null, null).visitEnd();
		cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "count", "J", null, null).visitEnd();

		MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		init.visitCode();
		line(init, 10);
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		line(init, 12);
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESTATIC, COLLECTIONS, "emptyList", "()Ljava/util/List;", false);
		init.visitFieldInsn(Opcodes.PUTFIELD, MIXIN, "remove", "Ljava/util/List;");
		line(init, 13);
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitInsn(Opcodes.ICONST_5);
		init.visitFieldInsn(Opcodes.PUTFIELD, MIXIN, "size", "I");
		line(init, 14);
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitTypeInsn(Opcodes.NEW, ARRAY_LIST);
		init.visitInsn(Opcodes.DUP);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, ARRAY_LIST, "<init>", "()V", false);
		init.visitFieldInsn(Opcodes.PUTFIELD, MIXIN, "names", "Ljava/util/List;");
		line(init, 10);
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(0, 0);
		init.visitEnd();

		MethodVisitor initList = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/util/List;)V", null, null);
		initList.visitCode();
		line(initList, 20);
		initList.visitVarInsn(Opcodes.ALOAD, 0);
		initList.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		line(initList, 21);
		initList.visitVarInsn(Opcodes.ALOAD, 0);
		initList.visitVarInsn(Opcodes.ALOAD, 1);
		initList.visitFieldInsn(Opcodes.PUTFIELD, MIXIN, "remove", "Ljava/util/List;");
		line(initList, 22);
		initList.visitInsn(Opcodes.RETURN);
		initList.visitMaxs(0, 0);
		initList.visitEnd();

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

	private static void line(MethodVisitor mv, int line) {
		Label label = new Label();
		mv.visitLabel(label);
		mv.visitLineNumber(line, label);
	}
}
