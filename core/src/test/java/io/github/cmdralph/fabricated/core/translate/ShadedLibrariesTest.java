package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import io.github.cmdralph.fabricated.core.testutil.BytesClassLoader;

/**
 * LIB-3: Wover's item API calls the Guava Fabric's Mixin carries relocated, {@code org.spongepowered.include.com.google},
 * which SpongePowered's Mixin, Forge's, doesn't carry. Where the platform lacks the relocated class and has Guava's own,
 * the mod's code names Guava's own, and runs; where the platform has the relocated class (Fabric's Mixin, NeoForge's), or
 * neither, the class is left as it is.
 */
class ShadedLibrariesTest {
	private static final String OWN = "com/google/common/collect/ImmutableList";
	private static final String RELOCATED = ShadedLibraries.SHADED + OWN;
	private static final String USER = "com/example/SmithingTemplates";

	/** The library's class: {@code static ImmutableList of()}, under {@code name}. */
	private static byte[] library(String name) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "()V", null, null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "of", "()L" + name + ";", null, null);
		mv.visitCode();
		mv.visitTypeInsn(Opcodes.NEW, name);
		mv.visitInsn(Opcodes.DUP);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, name, "<init>", "()V", false);
		mv.visitInsn(Opcodes.ARETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** The mod's {@code static Object combine()}, returning the relocated {@code ImmutableList.of()}. */
	private static byte[] user() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, USER, null, "java/lang/Object", null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "combine", "()Ljava/lang/Object;", null, null);
		mv.visitCode();
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, RELOCATED, "of", "()L" + RELOCATED + ";", false);
		mv.visitInsn(Opcodes.ARETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static Object combine(byte[] user) throws Throwable {
		Class<?> type = new BytesClassLoader(Map.of(OWN, library(OWN), USER, user)).loadClass(USER.replace('/', '.'));
		try {
			return type.getMethod("combine").invoke(null);
		} catch (InvocationTargetException e) {
			throw e.getCause();
		}
	}

	@Test
	void namesTheLibrarysOwnClassWhereThePlatformLacksTheRelocatedOne() throws Throwable {
		assertThrows(NoClassDefFoundError.class, () -> combine(user()), "as built, the relocated class isn't there");
		byte[] adapted = new ShadedLibraries(Map.of(OWN, library(OWN))::get).apply(user());
		assertNotNull(adapted);
		assertEquals(OWN.replace('/', '.'), combine(adapted).getClass().getName());
	}

	@Test
	void leavesTheClassWhereThePlatformHasTheRelocatedOneOrNeither() {
		assertNull(new ShadedLibraries(Map.of(OWN, library(OWN), RELOCATED, library(RELOCATED))::get).apply(user()), "Fabric's Mixin carries it");
		assertNull(new ShadedLibraries(name -> null).apply(user()), "nor where the library's own class isn't there either");
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Other", null, "java/lang/Object", null);
		cw.visitEnd();
		assertNull(new ShadedLibraries(Map.of(OWN, library(OWN))::get).apply(cw.toByteArray()));
	}
}
