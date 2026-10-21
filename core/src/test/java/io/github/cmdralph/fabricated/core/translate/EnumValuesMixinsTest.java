package io.github.cmdralph.fabricated.core.translate;

import static io.github.cmdralph.fabricated.core.testutil.TestEnumBodies.BODIES;
import static io.github.cmdralph.fabricated.core.testutil.TestEnumBodies.BODY;
import static io.github.cmdralph.fabricated.core.testutil.TestEnumBodies.COLOR;
import static io.github.cmdralph.fabricated.core.testutil.TestEnumBodies.DELEGATED;
import static io.github.cmdralph.fabricated.core.testutil.TestEnumBodies.HOLDER;
import static io.github.cmdralph.fabricated.core.testutil.TestEnumBodies.MIXIN;
import static io.github.cmdralph.fabricated.core.testutil.TestEnumBodies.NAMED;
import static io.github.cmdralph.fabricated.core.testutil.TestEnumBodies.TARGET;
import static io.github.cmdralph.fabricated.core.testutil.TestEnumBodies.body;
import static io.github.cmdralph.fabricated.core.testutil.TestEnumBodies.color;
import static io.github.cmdralph.fabricated.core.testutil.TestEnumBodies.holder;
import static io.github.cmdralph.fabricated.core.testutil.TestEnumBodies.mixin;
import static io.github.cmdralph.fabricated.core.testutil.TestEnumBodies.target;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;

class EnumValuesMixinsTest {
	/**
	 * ENUM-5: Wilder Wild's grass colour, shaped as NeoForge's {@code GrassColorModifier}: the enum forwards
	 * {@code modify} to a {@code Color} delegate its second constructor takes, and the mixin's constant overrides it with a
	 * body of its own, then hands itself to another class from its class initializer.
	 */
	@Test
	void aConstantWithABodyBecomesAConstantWithADelegateAddedByAMixin() throws Exception {
		EnumValuesMixins.Result result = EnumValuesMixins.of(node(mixin()), node(target()), Map.of(BODY, node(body(false)))::get, BODIES);
		assertNotNull(result);
		assertEquals(List.of("TUNDRA"), result.constants());

		// The delegate runs the body.
		byte[] delegate = result.classes().get(BODIES + "$0");
		assertNotNull(delegate, result.classes().keySet().toString());
		ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] bytes = name.equals(COLOR.replace('/', '.')) ? color() : name.equals((BODIES + "$0").replace('/', '.')) ? delegate : null;
				if (bytes == null) throw new ClassNotFoundException(name);
				return defineClass(name, bytes, 0, bytes.length);
			}
		};
		Class<?> delegateClass = Class.forName((BODIES + "$0").replace('/', '.'), true, loader);
		Object instance = delegateClass.getConstructor().newInstance();
		assertEquals(42, delegateClass.getMethod("apply", int.class).invoke(instance, 21));

		// The mixin: the enum's values shadowed, a constructor invoker taking the delegate, and the injector after the
		// values are set, which makes the constant, appends it and runs the rest of the class initializer.
		ClassNode mixin = node(result.mixin());
		assertEquals(MIXIN, mixin.name);
		assertTrue((mixin.access & Opcodes.ACC_ENUM) == 0, "an ordinary mixin, which FML doesn't refuse");
		assertNotNull(Annotations.find(mixin.invisibleAnnotations, mixin.visibleAnnotations, Annotations.MIXIN));
		assertEquals("$VALUES", mixin.fields.getFirst().name);
		// Mixin reads its member annotations (kept at runtime) from the visible ones.
		assertEquals(List.of("Lorg/spongepowered/asm/mixin/Shadow;", "Lorg/spongepowered/asm/mixin/Final;", "Lorg/spongepowered/asm/mixin/Mutable;"),
				mixin.fields.getFirst().visibleAnnotations.stream().map(a -> a.desc).toList());
		MethodNode invoker = method(mixin, "fabricated$new0");
		assertEquals(DELEGATED.replace(")V", ")L" + TARGET + ";"), invoker.desc);
		assertEquals("<init>", Annotations.value(annotation(invoker, "Lorg/spongepowered/asm/mixin/gen/Invoker;"), "value"));
		MethodNode adds = method(mixin, "fabricated$addConstants");
		AnnotationNode inject = annotation(adds, "Lorg/spongepowered/asm/mixin/injection/Inject;");
		assertEquals(List.of("<clinit>"), Annotations.value(inject, "method"));
		AnnotationNode at = (AnnotationNode) ((List<?>) Annotations.value(inject, "at")).getFirst();
		assertEquals("L" + TARGET + ";$VALUES:[L" + TARGET + ";", Annotations.value(at, "target"));
		assertEquals(Opcodes.PUTSTATIC, Annotations.value(at, "opcode"));
		boolean made = false;
		boolean handed = false;
		for (AbstractInsnNode insn : adds.instructions) {
			if (insn instanceof MethodInsnNode call && call.name.equals("fabricated$new0")) made = true;
			if (insn instanceof FieldInsnNode field && field.owner.equals(HOLDER)) handed = true;
			assertTrue(!(insn instanceof MethodInsnNode call) || !call.owner.equals(BODY), "the body's class isn't used");
		}
		assertTrue(made && handed, "makes the constant and hands it on");
	}

	/**
	 * The generated injector runs as Mixin merges it: in the enum's class initializer after its values are set, its own
	 * references and invoker pointing at the enum. The new constant follows the enum's own, at the next ordinal, with the
	 * body behind its delegate, and the class initializer's rest has run.
	 */
	@Test
	void theInjectorAddsTheConstantWhenMergedIntoTheEnum() throws Exception {
		EnumValuesMixins.Result result = EnumValuesMixins.of(node(mixin()), node(target()), Map.of(BODY, node(body(false)))::get, BODIES);
		assertNotNull(result);
		ClassNode handler = new ClassNode();
		new ClassReader(result.mixin()).accept(new org.objectweb.asm.commons.ClassRemapper(handler,
				new org.objectweb.asm.commons.SimpleRemapper(MIXIN, TARGET)), 0);
		Map<String, byte[]> classes = new java.util.HashMap<>(Map.of(TARGET, merged(method(handler, "fabricated$addConstants")), COLOR, color(),
				HOLDER, holder()));
		result.classes().forEach(classes::put);
		ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] bytes = classes.get(name.replace('.', '/'));
				if (bytes == null) throw new ClassNotFoundException(name);
				return defineClass(name, bytes, 0, bytes.length);
			}
		};
		Class<?> modifier = Class.forName(TARGET.replace('/', '.'), true, loader);
		Object[] values = (Object[]) modifier.getMethod("values").invoke(null);
		assertEquals(2, values.length);
		Enum<?> tundra = (Enum<?>) values[1];
		assertEquals("TUNDRA", tundra.name());
		assertEquals(1, tundra.ordinal());
		assertEquals(42, modifier.getMethod("modify", int.class).invoke(tundra, 21));
		assertTrue(Class.forName(HOLDER.replace('/', '.'), true, loader).getField("TUNDRA").get(null) == tundra);
	}

	/** A runnable {@link #target()} with NONE, and the handler and an invoker merged in as Mixin would. */
	private static byte[] merged(MethodNode handler) {
		String self = "L" + TARGET + ";";
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER | Opcodes.ACC_ENUM, TARGET, null, "java/lang/Enum", null);
		cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM, "NONE", self, null, null).visitEnd();
		// Not final: Mixin removes final from a field its mixin shadows as @Mutable.
		cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, "$VALUES", "[" + self, null, null).visitEnd();
		cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "delegate", "L" + COLOR + ";", null, null).visitEnd();
		for (String constructor : List.of(NAMED, DELEGATED)) {
			MethodVisitor init = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", constructor, null, null);
			init.visitCode();
			init.visitVarInsn(Opcodes.ALOAD, 0);
			init.visitVarInsn(Opcodes.ALOAD, 1);
			init.visitVarInsn(Opcodes.ILOAD, 2);
			init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Enum", "<init>", "(Ljava/lang/String;I)V", false);
			init.visitVarInsn(Opcodes.ALOAD, 0);
			if (constructor.equals(DELEGATED)) init.visitVarInsn(Opcodes.ALOAD, 4);
			else init.visitInsn(Opcodes.ACONST_NULL);
			init.visitFieldInsn(Opcodes.PUTFIELD, TARGET, "delegate", "L" + COLOR + ";");
			init.visitInsn(Opcodes.RETURN);
			init.visitMaxs(0, 0);
			init.visitEnd();
		}
		MethodVisitor modify = cw.visitMethod(Opcodes.ACC_PUBLIC, "modify", "(I)I", null, null);
		modify.visitCode();
		modify.visitVarInsn(Opcodes.ALOAD, 0);
		modify.visitFieldInsn(Opcodes.GETFIELD, TARGET, "delegate", "L" + COLOR + ";");
		modify.visitVarInsn(Opcodes.ILOAD, 1);
		modify.visitMethodInsn(Opcodes.INVOKEINTERFACE, COLOR, "apply", "(I)I", true);
		modify.visitInsn(Opcodes.IRETURN);
		modify.visitMaxs(0, 0);
		modify.visitEnd();
		MethodVisitor values = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "values", "()[" + self, null, null);
		values.visitCode();
		values.visitFieldInsn(Opcodes.GETSTATIC, TARGET, "$VALUES", "[" + self);
		values.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "[" + self, "clone", "()Ljava/lang/Object;", false);
		values.visitTypeInsn(Opcodes.CHECKCAST, "[" + self);
		values.visitInsn(Opcodes.ARETURN);
		values.visitMaxs(0, 0);
		values.visitEnd();
		MethodVisitor invoker = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "fabricated$new0", DELEGATED.replace(")V", ")" + self), null, null);
		invoker.visitCode();
		invoker.visitTypeInsn(Opcodes.NEW, TARGET);
		invoker.visitInsn(Opcodes.DUP);
		invoker.visitVarInsn(Opcodes.ALOAD, 0);
		invoker.visitVarInsn(Opcodes.ILOAD, 1);
		invoker.visitVarInsn(Opcodes.ALOAD, 2);
		invoker.visitVarInsn(Opcodes.ALOAD, 3);
		invoker.visitMethodInsn(Opcodes.INVOKESPECIAL, TARGET, "<init>", DELEGATED, false);
		invoker.visitInsn(Opcodes.ARETURN);
		invoker.visitMaxs(0, 0);
		invoker.visitEnd();
		handler.invisibleAnnotations = null;
		handler.accept(cw);
		MethodVisitor clinit = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
		clinit.visitCode();
		clinit.visitTypeInsn(Opcodes.NEW, TARGET);
		clinit.visitInsn(Opcodes.DUP);
		clinit.visitLdcInsn("NONE");
		clinit.visitInsn(Opcodes.ICONST_0);
		clinit.visitLdcInsn("none");
		clinit.visitMethodInsn(Opcodes.INVOKESPECIAL, TARGET, "<init>", NAMED, false);
		clinit.visitFieldInsn(Opcodes.PUTSTATIC, TARGET, "NONE", self);
		clinit.visitInsn(Opcodes.ICONST_1);
		clinit.visitTypeInsn(Opcodes.ANEWARRAY, TARGET);
		clinit.visitInsn(Opcodes.DUP);
		clinit.visitInsn(Opcodes.ICONST_0);
		clinit.visitFieldInsn(Opcodes.GETSTATIC, TARGET, "NONE", self);
		clinit.visitInsn(Opcodes.AASTORE);
		clinit.visitFieldInsn(Opcodes.PUTSTATIC, TARGET, "$VALUES", "[" + self);
		clinit.visitInsn(Opcodes.ACONST_NULL);
		clinit.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET, handler.name, handler.desc, false);
		clinit.visitInsn(Opcodes.RETURN);
		clinit.visitMaxs(0, 0);
		clinit.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	@Test
	void aBodyUsingItsInstanceOrAModReadingTheConstantAsAFieldStaysAnEnumMixin() throws Exception {
		assertNull(EnumValuesMixins.of(node(mixin()), node(target()), Map.of(BODY, node(body(true)))::get, BODIES));
		ClassWriter reader = new ClassWriter(0);
		reader.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/Reader", null, "java/lang/Object", null);
		MethodVisitor mv = reader.visitMethod(Opcodes.ACC_STATIC, "read", "()Ljava/lang/Object;", null, null);
		mv.visitCode();
		mv.visitFieldInsn(Opcodes.GETSTATIC, TARGET, "TUNDRA", "L" + TARGET + ";");
		mv.visitInsn(Opcodes.ARETURN);
		mv.visitMaxs(1, 0);
		mv.visitEnd();
		reader.visitEnd();
		assertTrue(EnumValuesMixins.readAsFields(List.of(node(reader.toByteArray())), TARGET, List.of("TUNDRA")));
	}

	private static ClassNode node(byte[] bytes) {
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, 0);
		return node;
	}

	private static MethodNode method(ClassNode node, String name) {
		return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow(() -> new AssertionError("no method " + name));
	}

	private static AnnotationNode annotation(MethodNode method, String desc) {
		AnnotationNode ann = Annotations.find(null, method.visibleAnnotations, desc);
		assertNotNull(ann, desc + " on " + method.name);
		return ann;
	}
}
