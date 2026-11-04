package io.github.cmdralph.fabricated.core.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.testutil.BytesClassLoader;

/**
 * A constructor's arguments handed to the constructor it calls, for an injection into the first that the platform moved
 * into the other (IRIS-1): the field and bridge pre-flight adds, as Mixin merges them into the target (here, built into
 * it), the injection at the moved point (here, a call to the bridge), and Fabricated's Mixin extension's change to the
 * first constructor ({@link ConstructorHandoffs#apply}), run on the JVM, which verifies the class.
 */
class ConstructorHandoffsTest {
	private static final String OWNER = "test/Shader";
	private static final String NAMED = "(Ljava/lang/String;J)V";
	private static final String LOCATED = "(Ljava/lang/StringBuilder;J)V";

	@Test
	void theFirstConstructorsArgumentsReachTheHandlerOnlyThroughIt() throws Exception {
		ClassNode target = shader();
		assertEquals(1, ConstructorHandoffs.apply(target), "the named constructor hands its arguments on");
		FieldNode field = target.fields.stream().filter(f -> f.name.equals("arguments")).findFirst().orElseThrow();
		assertTrue(field.invisibleAnnotations.isEmpty(), "the marker goes");
		assertEquals(0, ConstructorHandoffs.apply(target), "once");

		ClassWriter writer = new ClassWriter(0);
		target.accept(writer);
		Class<?> shader = new BytesClassLoader(Map.of(OWNER, writer.toByteArray())).loadClass("test.Shader");
		Field seen = shader.getField("seen");
		Field seenCount = shader.getField("seenCount");
		Field arguments = shader.getDeclaredField("arguments");
		arguments.setAccessible(true);

		Object named = shader.getConstructor(String.class, long.class).newInstance("rendertype_solid", 7L);
		assertEquals("rendertype_solid", seen.get(null), "the handler gets what the named constructor was called with");
		assertEquals(7L, seenCount.get(null));
		assertNull(arguments.get(named), "nothing outlives the construction");

		seen.set(null, null);
		Object located = shader.getConstructor(StringBuilder.class, long.class).newInstance(new StringBuilder("minecraft:solid"), 1L);
		assertNull(seen.get(null), "a construction through the located constructor alone doesn't call it");
		assertNull(arguments.get(located));
	}

	/**
	 * {@code Shader(String name, long n) { this(new StringBuilder(name), n); }} and {@code Shader(StringBuilder location,
	 * long n) { super(); bridge(location, n, null); }}, with the handler (writing {@code seen} and {@code seenCount}), its
	 * bridge and the arguments' field as pre-flight makes them, merged.
	 */
	private static ClassNode shader() {
		ClassNode node = new ClassNode();
		node.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, OWNER, null, "java/lang/Object", null);
		node.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "seen", "Ljava/lang/String;", null, null).visitEnd();
		node.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "seenCount", "J", null, null).visitEnd();
		ClassNode mixin = new ClassNode();
		mixin.name = OWNER;
		FieldNode field = ConstructorHandoffs.field(mixin, NAMED);
		field.name = "arguments";
		node.fields.add(field);

		MethodNode named = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", NAMED, null, null);
		named.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		named.instructions.add(new TypeInsnNode(Opcodes.NEW, "java/lang/StringBuilder"));
		named.instructions.add(new InsnNode(Opcodes.DUP));
		named.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
		named.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false));
		named.instructions.add(new VarInsnNode(Opcodes.LLOAD, 2));
		named.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, OWNER, "<init>", LOCATED, false));
		named.instructions.add(new InsnNode(Opcodes.RETURN));
		named.maxStack = 5;
		named.maxLocals = 4;
		node.methods.add(named);

		// The handler, written for the named constructor: seen = name; seenCount = n.
		MethodNode handler = new MethodNode(Opcodes.ACC_PRIVATE, "setup", "(Ljava/lang/String;JLjava/lang/Object;)V", null, null);
		handler.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
		handler.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, OWNER, "seen", "Ljava/lang/String;"));
		handler.instructions.add(new VarInsnNode(Opcodes.LLOAD, 2));
		handler.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, OWNER, "seenCount", "J"));
		handler.instructions.add(new InsnNode(Opcodes.RETURN));
		handler.maxStack = 2;
		handler.maxLocals = 5;
		AnnotationNode inject = new AnnotationNode(MixinNames.INJECT);
		handler.visibleAnnotations = new ArrayList<>(List.of(inject));
		node.methods.add(handler);

		MethodNode located = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", LOCATED, null, null);
		located.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		located.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
		located.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		located.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
		located.instructions.add(new VarInsnNode(Opcodes.LLOAD, 2));
		located.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
		located.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, OWNER, "bridge", "(Ljava/lang/StringBuilder;JLjava/lang/Object;)V", false));
		located.instructions.add(new InsnNode(Opcodes.RETURN));
		located.maxStack = 5;
		located.maxLocals = 4;
		node.methods.add(located);

		MethodNode bridge = HandlerBridges.handedOffBridge(OWNER, handler, inject, named, located, field, "bridge");
		assertTrue(Annotations.all(bridge).contains(inject), "the injector moves to the bridge");
		node.methods.add(bridge);
		// As Mixin merges it: the class read back, as the class loader reads it.
		ClassWriter writer = new ClassWriter(0);
		node.accept(writer);
		ClassNode read = new ClassNode();
		new ClassReader(writer.toByteArray()).accept(read, 0);
		return read;
	}
}
