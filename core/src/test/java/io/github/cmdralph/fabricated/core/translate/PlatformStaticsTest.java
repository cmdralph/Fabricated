package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.core.testutil.BytesClassLoader;

/**
 * PLUGIN-1: MixinConstraints' Forge abstraction, built for Forge 47, calls {@code LoadingModList.get().getModFileById(id)},
 * which Forge 64 made an interface's static method, its accessor private. The call is made static, and runs; a call
 * on a receiver kept in a local is left as it is.
 */
class PlatformStaticsTest {
	private static final String LIST = "net/minecraftforge/fml/loading/LoadingModList";
	private static final String BY_ID = "(Ljava/lang/String;)Ljava/lang/String;";
	private static final String USER = "com/example/ForgeAbstraction";

	/** Forge 64's shape: {@code interface LoadingModList { static String getModFileById(String id); private static LoadingModList get(); }}. */
	private static byte[] interfaceList() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, LIST, null, "java/lang/Object", null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "getModFileById", BY_ID, null, null);
		mv.visitCode();
		mv.visitLdcInsn("file of ");
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false);
		mv.visitInsn(Opcodes.ARETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "get", "()L" + LIST + ";", null, null);
		mv.visitCode();
		mv.visitInsn(Opcodes.ACONST_NULL);
		mv.visitInsn(Opcodes.ARETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** {@code version(id)} as built for Forge 47's class: {@code LoadingModList.get().getModFileById(id)}; {@code kept(id)} through a local. */
	private static byte[] user() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, USER, null, "java/lang/Object", null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "version", BY_ID, null, null);
		mv.visitCode();
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, LIST, "get", "()L" + LIST + ";", false);
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, LIST, "getModFileById", BY_ID, false);
		mv.visitInsn(Opcodes.ARETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "kept", BY_ID, null, null);
		mv.visitCode();
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, LIST, "get", "()L" + LIST + ";", false);
		mv.visitVarInsn(Opcodes.ASTORE, 1);
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, LIST, "getModFileById", BY_ID, false);
		mv.visitInsn(Opcodes.ARETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static Object call(byte[] user, String method) throws Throwable {
		Class<?> type = new BytesClassLoader(Map.of(LIST, interfaceList(), USER, user)).loadClass(USER.replace('/', '.'));
		Method m = type.getMethod(method, String.class);
		try {
			return m.invoke(null, "flashback");
		} catch (InvocationTargetException e) {
			throw e.getCause();
		}
	}

	@Test
	void aCallThroughTheAccessorOfAMethodThePlatformMadeStaticIsMadeStatic() throws Throwable {
		assertThrows(IncompatibleClassChangeError.class, () -> call(user(), "version"), "as built, it can't link");
		PlatformStatics statics = new PlatformStatics(Map.of(LIST, interfaceList())::get);
		byte[] adapted = statics.apply(user());
		assertNotNull(adapted);
		assertEquals("file of flashback", call(adapted, "version"));

		ClassNode node = new ClassNode();
		new ClassReader(adapted).accept(node, 0);
		MethodNode kept = node.methods.stream().filter(m -> m.name.equals("kept")).findFirst().orElseThrow();
		boolean accessor = false;
		for (AbstractInsnNode insn : kept.instructions) accessor |= insn instanceof MethodInsnNode call && call.name.equals("get") && !call.itf;
		assertTrue(accessor, "a receiver kept in a local is left as it is");
	}

	@Test
	void classesOfAPlatformWithTheOldShapeOrNotCallingTheLoaderAreLeftAlone() {
		// Forge 47's shape: a class with a public static get() and an instance getModFileById, where the call links.
		ClassWriter old = new ClassWriter(0);
		old.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, LIST, null, "java/lang/Object", null);
		old.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE, "get", "()L" + LIST + ";", null, null).visitEnd();
		old.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_NATIVE, "getModFileById", BY_ID, null, null).visitEnd();
		old.visitEnd();
		assertNull(new PlatformStatics(Map.of(LIST, old.toByteArray())::get).apply(user()));
		assertNull(new PlatformStatics(name -> null).apply(user()), "nor where the platform has no such class");
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Other", null, "java/lang/Object", null);
		cw.visitEnd();
		assertNull(new PlatformStatics(Map.of(LIST, interfaceList())::get).apply(cw.toByteArray()));
	}
}
