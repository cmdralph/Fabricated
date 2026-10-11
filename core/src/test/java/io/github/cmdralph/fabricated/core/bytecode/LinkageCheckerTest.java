package io.github.cmdralph.fabricated.core.bytecode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import io.github.cmdralph.fabricated.core.testutil.TestJars;
import io.github.cmdralph.fabricated.core.translate.LinkageAnalysis;

class LinkageCheckerTest {
	private static byte[] caller(String name, String owner, String method, String desc, boolean clientOnly) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
		if (clientOnly) {
			var a = cw.visitAnnotation("Lnet/fabricmc/api/Environment;", true);
			a.visitEnum("value", "Lnet/fabricmc/api/EnvType;", "CLIENT");
			a.visitEnd();
		}
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "()V", null, null);
		mv.visitCode();
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, owner, method, desc, false);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static byte[] game() {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "net/minecraft/Util", null, "java/lang/Object", null);
		cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "present", "()V", null, null).visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static List<LinkageChecker.Issue> check(byte[] cls, boolean client) {
		ClassIndex index = new ClassIndex(Map.of("net/minecraft/Util", game(),
				"net/minecraft/Child", TestJars.emptyClass("net/minecraft/Child", "net/minecraft/Util"))::get);
		List<LinkageChecker.Issue> issues = new ArrayList<>();
		new LinkageChecker(index, LinkageAnalysis.CHECKED, client).check(cls, issues);
		return issues;
	}

	@Test
	void acceptsExistingAndInheritedMembers() {
		assertTrue(check(caller("m/A", "net/minecraft/Util", "present", "()V", false), false).isEmpty());
		assertTrue(check(caller("m/A", "net/minecraft/Child", "present", "()V", false), false).isEmpty());
		assertTrue(check(caller("m/A", "com/other/Lib", "whatever", "()V", false), false).isEmpty(), "unchecked namespaces are ignored");
	}

	@Test
	void reportsMissingMethodsAndClasses() {
		List<LinkageChecker.Issue> issues = check(caller("m/A", "net/minecraft/Util", "removed", "()V", false), false);
		assertEquals(1, issues.size());
		assertEquals("method", issues.getFirst().kind());
		List<LinkageChecker.Issue> classIssues = check(caller("m/A", "net/fabricmc/fabric/impl/Gone", "x", "()V", false), false);
		assertEquals("class", classIssues.getFirst().kind());
	}

	@Test
	void skipsClientOnlyCodeOnServer() {
		assertTrue(check(caller("m/A", "net/minecraft/Util", "removed", "()V", true), false).isEmpty());
		assertTrue(check(caller("m/A", "net/minecraft/client/Minecraft", "getInstance", "()V", false), false).isEmpty());
		assertEquals(1, check(caller("m/A", "net/minecraft/Util", "removed", "()V", true), true).size());
	}
}
