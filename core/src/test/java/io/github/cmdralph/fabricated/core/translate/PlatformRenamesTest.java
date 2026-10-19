package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;

class PlatformRenamesTest {
	private static final String TYPE = "net/neoforged/fml/config/ModConfig$Type";

	private static byte[] enumWith(String... constants) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ENUM, TYPE, null, "java/lang/Enum", null);
		for (String c : constants) cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_ENUM, c, "L" + TYPE + ";", null, null).visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** A mod class reading {@code ModConfig.Type.SERVER}. */
	private static byte[] user() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/Config", null, "java/lang/Object", null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "type", "()Ljava/lang/Object;", null, null);
		mv.visitCode();
		mv.visitFieldInsn(Opcodes.GETSTATIC, TYPE, "SERVER", "L" + TYPE + ";");
		mv.visitInsn(Opcodes.ARETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	@Test
	void renamesTheConfigTypesFml12Renamed() {
		PlatformRenames renames = PlatformRenames.of(Map.of(TYPE, enumWith("LOCAL", "CLIENT", "SYNCED", "STARTUP"))::get);
		ClassNode node = new ClassNode();
		new ClassReader(renames.apply(user())).accept(node, 0);
		FieldInsnNode read = (FieldInsnNode) node.methods.getFirst().instructions.getFirst();
		assertEquals("SYNCED", read.name);
	}

	@Test
	void leavesModsAloneWhereTheOldNamesExist() {
		PlatformRenames renames = PlatformRenames.of(Map.of(TYPE, enumWith("COMMON", "CLIENT", "SERVER", "STARTUP"))::get);
		assertNull(renames.apply(user()));
	}
}
