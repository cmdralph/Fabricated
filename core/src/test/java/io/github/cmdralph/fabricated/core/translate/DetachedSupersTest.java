package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

class DetachedSupersTest {
	private static final String PART = "game/Part";
	private static final String RECORD = "java/lang/Record";
	private static final String OBJECT = "java/lang/Object";

	private final Map<String, ClassNode> classes = new HashMap<>();

	private void target(String name, String superName) {
		ClassNode node = new ClassNode();
		node.name = name;
		node.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL;
		node.superName = superName;
		node.interfaces.add(PART);
		classes.put(name, node);
	}

	/**
	 * As Fabric API's renderer's SimpleModelWrapperMixin: extends Object, implements the part interface, its constructor
	 * initialises a field with a new Object, and emitQuads calls the interface's default as its super (and, with
	 * {@code ownSuperCall}, Object's toString as its super too).
	 */
	private static byte[] mixin(String signature, boolean interfaceSuperCall, boolean ownSuperCall) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_ABSTRACT, "mod/WrapperMixin", signature, OBJECT, new String[] {PART});
		MethodVisitor init = cw.visitMethod(0, "<init>", "()V", null, null);
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, OBJECT, "<init>", "()V", false);
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitTypeInsn(Opcodes.NEW, OBJECT);
		init.visitInsn(Opcodes.DUP);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, OBJECT, "<init>", "()V", false);
		init.visitFieldInsn(Opcodes.PUTFIELD, "mod/WrapperMixin", "lock", "Ljava/lang/Object;");
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(3, 1);
		init.visitEnd();
		MethodVisitor emit = cw.visitMethod(Opcodes.ACC_PUBLIC, "emitQuads", "()V", null, null);
		if (interfaceSuperCall) {
			emit.visitVarInsn(Opcodes.ALOAD, 0);
			emit.visitMethodInsn(Opcodes.INVOKESPECIAL, PART, "emitQuads", "()V", true);
		}
		if (ownSuperCall) {
			emit.visitVarInsn(Opcodes.ALOAD, 0);
			emit.visitMethodInsn(Opcodes.INVOKESPECIAL, OBJECT, "toString", "()Ljava/lang/String;", false);
			emit.visitInsn(Opcodes.POP);
		}
		emit.visitInsn(Opcodes.RETURN);
		emit.visitMaxs(1, 1);
		emit.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static ClassNode node(byte[] data) {
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, 0);
		return node;
	}

	private static List<String> calls(ClassNode node, String method) {
		List<String> calls = new ArrayList<>();
		for (MethodNode m : node.methods) {
			if (!m.name.equals(method)) continue;
			for (AbstractInsnNode insn : m.instructions) {
				if (insn instanceof MethodInsnNode call) calls.add(call.owner + "." + call.name + (call.itf ? " (interface)" : ""));
			}
		}
		return calls;
	}

	@Test
	void aMixinCallingAnInterfaceDefaultAsItsSuperGetsItsTargetsSuperclass() {
		target("game/Wrapper", RECORD);
		byte[] data = mixin(null, true, false);
		assertEquals(RECORD, DetachedSupers.alignedSuperclass(node(data), List.of("game/Wrapper"), classes::get));

		ClassNode rewritten = node(DetachedSupers.rewrite(data, RECORD));
		assertEquals(RECORD, rewritten.superName);
		// Only the super call: the field's new Object keeps Object's constructor.
		assertEquals(List.of(RECORD + ".<init>", OBJECT + ".<init>"), calls(rewritten, "<init>"));
		assertEquals(List.of(PART + ".emitQuads (interface)"), calls(rewritten, "emitQuads"), "the call itself stays");
	}

	@Test
	void theGenericSignatureNamesTheNewSuperclass() {
		assertEquals("<T:Ljava/lang/Object;>Ljava/lang/Record;Lgame/Part;",
				DetachedSupers.withSuperclass("<T:Ljava/lang/Object;>Ljava/lang/Object;Lgame/Part;", OBJECT, RECORD), "the bound stays Object");
		assertNull(DetachedSupers.withSuperclass("Lmod/Base<Ljava/lang/String;>;Lgame/Part;", "mod/Base", RECORD),
				"a generic superclass's arguments don't fit the new one: Mixin reads the raw types");
		assertNull(DetachedSupers.withSuperclass(null, OBJECT, RECORD));

		target("game/Wrapper", RECORD);
		byte[] data = mixin("<T:Ljava/lang/Object;>Ljava/lang/Object;Lgame/Part;", true, false);
		assertEquals("<T:Ljava/lang/Object;>Ljava/lang/Record;Lgame/Part;", node(DetachedSupers.rewrite(data, RECORD)).signature);
	}

	@Test
	void otherMixinsKeepTheirSuperclass() {
		target("game/Wrapper", RECORD);
		target("game/Plain", OBJECT);
		target("game/Other", "game/Base");
		assertNull(DetachedSupers.alignedSuperclass(node(mixin(null, false, false)), List.of("game/Wrapper"), classes::get),
				"no call to an interface as its super");
		assertNull(DetachedSupers.alignedSuperclass(node(mixin(null, true, false)), List.of("game/Plain"), classes::get),
				"its superclass is its target's already");
		assertNull(DetachedSupers.alignedSuperclass(node(mixin(null, true, true)), List.of("game/Wrapper"), classes::get),
				"a call of its own superclass's method needs Mixin's rebinding");
		assertNull(DetachedSupers.alignedSuperclass(node(mixin(null, true, false)), List.of("game/Wrapper", "game/Other"), classes::get),
				"targets with different superclasses");
		assertNull(DetachedSupers.alignedSuperclass(node(mixin(null, true, false)), List.of("game/Missing"), classes::get), "an unknown target");

		ClassNode itf = node(mixin(null, true, false));
		itf.access |= Opcodes.ACC_INTERFACE;
		assertNull(DetachedSupers.alignedSuperclass(itf, List.of("game/Wrapper"), classes::get), "an interface mixin");
	}
}
