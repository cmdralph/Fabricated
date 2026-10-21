package io.github.cmdralph.fabricated.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Enforces the "no network access" promise: Fabricated's compiled classes must not reference any
 * networking API. Scans the bytecode of every class in the core module and the Fabric Loader API layer.
 */
class NoNetworkTest {
	private static final List<String> FORBIDDEN = List.of(
			"java/net/Socket", "java/net/ServerSocket", "java/net/URL", "java/net/URLConnection", "java/net/HttpURLConnection",
			"java/net/DatagramSocket", "java/net/http/", "java/nio/channels/SocketChannel", "javax/net/");
	/**
	 * Fabric Loader's legacy launcher API takes the jar a mod adds to the class path as a (file) URL, which its stand-in
	 * only turns into a path ({@code URL.toURI}); it opens nothing (LOADER-2).
	 */
	private static final String LEGACY_LAUNCHER = "net/fabricmc/loader/launch/common/FabricLauncherBase";

	@Test
	void noNetworkingApisAreReferenced() throws Exception {
		List<String> violations = new ArrayList<>();
		int scanned = 0;
		for (Class<?> anchor : List.of(Pipeline.class, io.github.cmdralph.fabricated.fabricloader.FabricLoaderImpl.class)) {
			Path root = classesRoot(anchor);
			if (Files.isRegularFile(root)) {
				// Classes packaged as a jar: walk it through a zip file system.
				try (java.nio.file.FileSystem fs = java.nio.file.FileSystems.newFileSystem(root, (ClassLoader) null);
						Stream<Path> files = Files.walk(fs.getRootDirectories().iterator().next())) {
					for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
						scanned++;
						scan(file, violations);
					}
				}
				continue;
			}
			try (Stream<Path> files = Files.walk(root)) {
				for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
					scanned++;
					scan(file, violations);
				}
			}
		}
		assertTrue(scanned > 50, "expected to scan the compiled classes, scanned " + scanned);
		assertEquals(List.of(), violations);
	}

	private static Path classesRoot(Class<?> anchor) throws URISyntaxException {
		return Path.of(anchor.getProtectionDomain().getCodeSource().getLocation().toURI());
	}

	private static void scan(Path file, List<String> violations) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
				String name;

				@Override
				public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
					this.name = name;
				}

				@Override
				public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature, String[] exceptions) {
					return new MethodVisitor(Opcodes.ASM9) {
						@Override
						public void visitMethodInsn(int opcode, String owner, String n, String d, boolean itf) {
							if (name.equals(LEGACY_LAUNCHER) && owner.equals("java/net/URL") && n.equals("toURI")) return;
							check(owner);
						}

						@Override
						public void visitTypeInsn(int opcode, String type) {
							check(type);
						}

						@Override
						public void visitFieldInsn(int opcode, String owner, String n, String d) {
							check(owner);
						}

						private void check(String owner) {
							for (String f : FORBIDDEN) {
								if (owner.startsWith(f)) violations.add(name + " -> " + owner);
							}
						}
					};
				}
			}, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		}
	}
}
