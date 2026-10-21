package io.github.cmdralph.fabricated.core.remap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class OfficialClassesTest {
	private static final String MAPPINGS = """
			# a comment
			net.minecraft.client.Camera -> a:
			    float xRot -> b
			    1:2:void setRotation(float,float) -> a
			    3:4:void alignWithEntity() -> b
			net.minecraft.client.SubCamera -> c:
			    5:6:void tick() -> a
			""";

	/** The obfuscated jar: {@code a} (Camera) and {@code c extends a} (SubCamera), whose {@code a()V} uses what it inherits. */
	private static final Map<String, byte[]> OFFICIAL = Map.of("a", camera(), "c", subCamera());

	private static byte[] camera() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "a", null, "java/lang/Object", null);
		cw.visitField(Opcodes.ACC_PRIVATE, "b", "F", null, null).visitEnd();
		method(cw, "a", "(FF)V", mv -> {
		});
		method(cw, "b", "()V", mv -> {
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitInsn(Opcodes.FCONST_0);
			mv.visitInsn(Opcodes.FCONST_0);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "a", "a", "(FF)V", false);
		});
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static byte[] subCamera() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "c", null, "a", null);
		method(cw, "a", "()V", mv -> {
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitFieldInsn(Opcodes.GETFIELD, "c", "b", "F");
			mv.visitInsn(Opcodes.FCONST_0);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "c", "a", "(FF)V", false);
		});
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static void method(ClassWriter cw, String name, String desc, Consumer<MethodVisitor> body) {
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, name, desc, null, null);
		mv.visitCode();
		body.accept(mv);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	private static ClassNode node(byte[] data) {
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, 0);
		return node;
	}

	private static List<String> references(MethodNode method) {
		List<String> found = new ArrayList<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof MethodInsnNode call) found.add(call.owner + "." + call.name + call.desc);
			if (insn instanceof FieldInsnNode field) found.add(field.owner + "." + field.name + ":" + field.desc);
		}
		return found;
	}

	@Test
	void classesAreReadInMojangsNamesWithInheritedMembersNamedWhereDeclared() throws IOException {
		OfficialClasses vanilla = new OfficialClasses(OFFICIAL::get, () -> new StringReader(MAPPINGS));

		ClassNode camera = node(vanilla.read("net/minecraft/client/Camera"));
		assertEquals("net/minecraft/client/Camera", camera.name);
		assertEquals(List.of("setRotation", "alignWithEntity"),
				camera.methods.stream().map(m -> m.name).toList(), "methods in Mojang's names");
		assertEquals("xRot", camera.fields.getFirst().name);

		ClassNode sub = node(vanilla.read("net/minecraft/client/SubCamera"));
		assertEquals("net/minecraft/client/Camera", sub.superName);
		MethodNode tick = sub.methods.getFirst();
		assertEquals("tick", tick.name);
		assertEquals(List.of("net/minecraft/client/SubCamera.xRot:F", "net/minecraft/client/SubCamera.setRotation(FF)V"), references(tick),
				"what it inherits, named as the class declaring it maps it");
		assertNull(vanilla.read("net/minecraft/client/Missing"));
	}

	@Test
	void withoutReadableMappingsNoClassIsRead() throws IOException {
		OfficialClasses vanilla = new OfficialClasses(OFFICIAL::get, () -> {
			throw new IOException("no mappings");
		});
		assertNull(vanilla.read("net/minecraft/client/Camera"));
		assertNull(vanilla.read("a"), "nor in obfuscated names");
	}
}
