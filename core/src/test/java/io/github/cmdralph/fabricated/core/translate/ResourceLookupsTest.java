package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class ResourceLookupsTest {
	/** A mod class reading {@code /version.json} through itself (as Lattice does), and a file through its class loader. */
	private static byte[] reader() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/Reader", null, "java/lang/Object", null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "read", "()V", null, null);
		mv.visitCode();
		mv.visitLdcInsn(Type.getObjectType("com/example/Reader"));
		mv.visitLdcInsn("/version.json");
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;", false);
		mv.visitInsn(Opcodes.POP);
		mv.visitLdcInsn(Type.getObjectType("com/example/Reader"));
		mv.visitLdcInsn("icon.png");
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getResource", "(Ljava/lang/String;)Ljava/net/URL;", false);
		mv.visitInsn(Opcodes.POP);
		mv.visitLdcInsn(Type.getObjectType("com/example/Reader"));
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;", false);
		mv.visitLdcInsn("data.json");
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/ClassLoader", "getResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;", false);
		mv.visitInsn(Opcodes.POP);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	@Test
	void aClassesResourceReadsGoThroughClassResources() throws Exception {
		byte[] adapted = ResourceLookups.apply(reader());
		ClassNode node = new ClassNode();
		new ClassReader(adapted).accept(node, 0);
		List<String> calls = new ArrayList<>();
		for (AbstractInsnNode insn : node.methods.getFirst().instructions) {
			if (insn instanceof MethodInsnNode call) calls.add(call.getOpcode() + " " + call.owner + "." + call.name + call.desc);
		}
		assertEquals(List.of(
				Opcodes.INVOKESTATIC + " " + ResourceLookups.CLASS_RESOURCES + ".getResourceAsStream(Ljava/lang/Class;Ljava/lang/String;)Ljava/io/InputStream;",
				Opcodes.INVOKESTATIC + " " + ResourceLookups.CLASS_RESOURCES + ".getResource(Ljava/lang/Class;Ljava/lang/String;)Ljava/net/URL;",
				Opcodes.INVOKEVIRTUAL + " java/lang/Class.getClassLoader()Ljava/lang/ClassLoader;",
				Opcodes.INVOKEVIRTUAL + " java/lang/ClassLoader.getResourceAsStream(Ljava/lang/String;)Ljava/io/InputStream;"), calls,
				"a class loader's lookup already sees every jar");
		// The stack is the same either way: the class passes the JVM's verifier.
		ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				if (!name.equals("com.example.Reader")) throw new ClassNotFoundException(name);
				return defineClass(name, adapted, 0, adapted.length);
			}
		};
		Class.forName("com.example.Reader", true, loader);
	}

	/** A mod class loading its translations with a control (as LuckPerms does), once with its own class loader. */
	private static byte[] bundleReader() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/Translations", null, "java/lang/Object", null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "load", "()V", null, null);
		mv.visitCode();
		mv.visitLdcInsn("messages");
		mv.visitFieldInsn(Opcodes.GETSTATIC, "java/util/Locale", "ROOT", "Ljava/util/Locale;");
		mv.visitInsn(Opcodes.ACONST_NULL);
		mv.visitTypeInsn(Opcodes.CHECKCAST, "java/util/ResourceBundle$Control");
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/ResourceBundle", "getBundle",
				"(Ljava/lang/String;Ljava/util/Locale;Ljava/util/ResourceBundle$Control;)Ljava/util/ResourceBundle;", false);
		mv.visitInsn(Opcodes.POP);
		mv.visitLdcInsn("messages");
		mv.visitFieldInsn(Opcodes.GETSTATIC, "java/util/Locale", "ROOT", "Ljava/util/Locale;");
		mv.visitInsn(Opcodes.ACONST_NULL);
		mv.visitInsn(Opcodes.ACONST_NULL);
		mv.visitTypeInsn(Opcodes.CHECKCAST, "java/util/ResourceBundle$Control");
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/ResourceBundle", "getBundle",
				"(Ljava/lang/String;Ljava/util/Locale;Ljava/lang/ClassLoader;Ljava/util/ResourceBundle$Control;)Ljava/util/ResourceBundle;", false);
		mv.visitInsn(Opcodes.POP);
		mv.visitLdcInsn("messages");
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/ResourceBundle", "getBundle", "(Ljava/lang/String;)Ljava/util/ResourceBundle;", false);
		mv.visitInsn(Opcodes.POP);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	@Test
	void resourceBundlesWithAControlGoThroughResourceBundles() throws Exception {
		byte[] adapted = ResourceLookups.apply(bundleReader());
		ClassNode node = new ClassNode();
		new ClassReader(adapted).accept(node, 0);
		List<String> calls = new ArrayList<>();
		for (AbstractInsnNode insn : node.methods.getFirst().instructions) {
			if (insn instanceof MethodInsnNode call) calls.add(call.getOpcode() + " " + call.owner + "." + call.name + call.desc);
		}
		assertEquals(List.of(
				Opcodes.INVOKESTATIC + " " + ResourceLookups.RESOURCE_BUNDLES
						+ ".getBundle(Ljava/lang/String;Ljava/util/Locale;Ljava/util/ResourceBundle$Control;Ljava/lang/Class;)Ljava/util/ResourceBundle;",
				Opcodes.INVOKESTATIC + " " + ResourceLookups.RESOURCE_BUNDLES
						+ ".getBundle(Ljava/lang/String;Ljava/util/Locale;Ljava/lang/ClassLoader;Ljava/util/ResourceBundle$Control;)Ljava/util/ResourceBundle;",
				Opcodes.INVOKESTATIC + " java/util/ResourceBundle.getBundle(Ljava/lang/String;)Ljava/util/ResourceBundle;"), calls,
				"a lookup without a control works from a named module");
		// The calling class is pushed for the first call, one more stack slot: the class passes the JVM's verifier.
		ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				if (!name.equals("com.example.Translations")) throw new ClassNotFoundException(name);
				return defineClass(name, adapted, 0, adapted.length);
			}
		};
		Class.forName("com.example.Translations", true, loader);
	}

	@Test
	void leavesClassesWithoutSuchReadsAlone() {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/Plain", null, "java/lang/Object", null);
		cw.visitEnd();
		assertNull(ResourceLookups.apply(cw.toByteArray()));
	}
}
