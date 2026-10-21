package io.github.cmdralph.fabricated.core.translate;

import java.nio.charset.StandardCharsets;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Points a Fabric mod's {@code Class.getResource} and {@code Class.getResourceAsStream} calls at Fabricated's
 * {@code ClassResources}, which also asks the class loader when the class's own module has no such file, as Fabric's
 * unnamed modules do (RES-1). Only the call changes: the stack takes the class and the name either way.
 */
final class ResourceLookups {
	/** Fabricated's (in its library jar, which Fabric mods read). */
	static final String CLASS_RESOURCES = "io/github/cmdralph/fabricated/loader/ClassResources";
	private static final String STREAM = "(Ljava/lang/String;)Ljava/io/InputStream;";
	private static final String URL = "(Ljava/lang/String;)Ljava/net/URL;";

	private ResourceLookups() {
	}

	/** {@code classFile} with those calls pointed at {@code ClassResources}, or null when it makes none. */
	static byte[] apply(byte[] classFile) {
		String text = new String(classFile, StandardCharsets.ISO_8859_1);
		if (!text.contains("getResource") || !text.contains("java/lang/Class")) return null;
		boolean[] changed = {false};
		ClassReader reader = new ClassReader(classFile);
		ClassWriter writer = new ClassWriter(reader, 0);
		reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
					@Override
					public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean itf) {
						if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/lang/Class")
								&& (method.equals("getResourceAsStream") && desc.equals(STREAM) || method.equals("getResource") && desc.equals(URL))) {
							super.visitMethodInsn(Opcodes.INVOKESTATIC, CLASS_RESOURCES, method, "(Ljava/lang/Class;" + desc.substring(1), false);
							changed[0] = true;
							return;
						}
						super.visitMethodInsn(opcode, owner, method, desc, itf);
					}
				};
			}
		}, 0);
		return changed[0] ? writer.toByteArray() : null;
	}
}
