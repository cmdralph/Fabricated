package io.github.cmdralph.fabricated.core.bytecode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import net.fabricmc.api.EnvType;

class EnvironmentStripperTest {
	private static final String ENV = "Lnet/fabricmc/api/EnvType;";

	/** A class implementing a client-only interface, with a client-only method and field and a common method. */
	private static byte[] sidedClass() {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "mod/Thing", null, "java/lang/Object", new String[] {"mod/ClientOnly", "mod/Common"});
		AnnotationVisitor itf = cw.visitAnnotation("Lnet/fabricmc/api/EnvironmentInterface;", false);
		itf.visitEnum("value", ENV, "CLIENT");
		itf.visit("itf", Type.getObjectType("mod/ClientOnly"));
		itf.visitEnd();
		var client = cw.visitMethod(Opcodes.ACC_PUBLIC, "render", "()V", null, null);
		client.visitAnnotation("Lnet/fabricmc/api/Environment;", false).visitEnum("value", ENV, "CLIENT");
		client.visitEnd();
		cw.visitMethod(Opcodes.ACC_PUBLIC, "tick", "()V", null, null).visitEnd();
		var field = cw.visitField(Opcodes.ACC_PRIVATE, "model", "Ljava/lang/Object;", null, null);
		field.visitAnnotation("Lnet/fabricmc/api/Environment;", false).visitEnum("value", ENV, "CLIENT");
		field.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static ClassNode node(byte[] bytes) {
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, 0);
		return node;
	}

	@Test
	void stripsTheOtherSidesMembersAndInterfaces() {
		byte[] bytes = sidedClass();
		assertTrue(EnvironmentStripper.mayNeedStripping(bytes));

		ClassNode server = node(bytes);
		assertTrue(EnvironmentStripper.strip(server, EnvType.SERVER));
		assertEquals(List.of("mod/Common"), server.interfaces);
		assertEquals(List.of("tick"), server.methods.stream().map(m -> m.name).toList());
		assertTrue(server.fields.isEmpty());

		ClassNode client = node(bytes);
		assertFalse(EnvironmentStripper.strip(client, EnvType.CLIENT), "nothing to strip on its own side");
		assertEquals(2, client.interfaces.size());
	}

	@Test
	void plainClassesAreNotLookedAt() {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "mod/Plain", null, "java/lang/Object", null);
		cw.visitEnd();
		assertFalse(EnvironmentStripper.mayNeedStripping(cw.toByteArray()));
	}
}
