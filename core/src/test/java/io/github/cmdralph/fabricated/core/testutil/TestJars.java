package io.github.cmdralph.fabricated.core.testutil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Builds jars and class files for tests. No third-party binaries are committed to the repository. */
public final class TestJars {
	private final Map<String, byte[]> entries = new LinkedHashMap<>();

	public static TestJars create() {
		return new TestJars();
	}

	public TestJars text(String name, String content) {
		entries.put(name, content.getBytes(StandardCharsets.UTF_8));
		return this;
	}

	public TestJars bytes(String name, byte[] content) {
		entries.put(name, content);
		return this;
	}

	public TestJars fabricModJson(String json) {
		return text("fabric.mod.json", json);
	}

	public byte[] toBytes() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(out)) {
			for (Map.Entry<String, byte[]> e : entries.entrySet()) {
				zip.putNextEntry(new ZipEntry(e.getKey()));
				zip.write(e.getValue());
				zip.closeEntry();
			}
		}
		return out.toByteArray();
	}

	public Path write(Path file) throws IOException {
		Files.createDirectories(file.toAbsolutePath().getParent());
		Files.write(file, toBytes());
		return file;
	}

	/** Minimal {@code fabric.mod.json}. Extra JSON members (without braces) can be appended. */
	public static String modJson(String id, String version, String extraMembers) {
		return "{\"schemaVersion\":1,\"id\":\"" + id + "\",\"version\":\"" + version + "\""
				+ (extraMembers == null || extraMembers.isEmpty() ? "" : "," + extraMembers) + "}";
	}

	/** An empty public class with a no-arg constructor. */
	public static byte[] emptyClass(String internalName, String superName, String... interfaces) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internalName, null, superName, interfaces.length == 0 ? null : interfaces);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}
}
