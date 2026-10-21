package io.github.cmdralph.fabricated.core.testutil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.tools.ToolProvider;

/** Compiles Java sources for tests, for class files as javac writes them (frames, bridges, lambdas). */
public final class TestSources {
	/** Mixin's annotations, as mods compile against them (only their names and elements matter to translation). */
	public static final Map<String, String> MIXIN_ANNOTATIONS = Map.of(
			"org/spongepowered/asm/mixin/Mixin.java", "package org.spongepowered.asm.mixin; public @interface Mixin { Class<?>[] value(); boolean remap() default true; }",
			"org/spongepowered/asm/mixin/Shadow.java", "package org.spongepowered.asm.mixin; public @interface Shadow { boolean remap() default true; }",
			"org/spongepowered/asm/mixin/Final.java", "package org.spongepowered.asm.mixin; public @interface Final { }",
			"org/spongepowered/asm/mixin/Unique.java", "package org.spongepowered.asm.mixin; public @interface Unique { }");

	private TestSources() {
	}

	/**
	 * Compiles the sources (file path → content) in {@code dir}.
	 *
	 * @return the class files, by internal name
	 */
	@SafeVarargs
	public static Map<String, byte[]> compile(Path dir, Map<String, String>... sources) throws IOException {
		Path src = Files.createDirectories(dir.resolve("src"));
		Path out = Files.createDirectories(dir.resolve("out"));
		List<String> args = new ArrayList<>(List.of("-d", out.toString(), "--release", "21", "-nowarn"));
		for (Map<String, String> set : sources) {
			for (Map.Entry<String, String> e : set.entrySet()) {
				Path file = src.resolve(e.getKey());
				Files.createDirectories(file.getParent());
				Files.writeString(file, e.getValue());
				args.add(file.toString());
			}
		}
		if (ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)) != 0) {
			throw new IllegalStateException("The test sources don't compile");
		}
		Map<String, byte[]> classes = new HashMap<>();
		try (Stream<Path> files = Files.walk(out)) {
			files.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
				String name = out.relativize(p).toString().replace('\\', '/');
				try {
					classes.put(name.substring(0, name.length() - ".class".length()), Files.readAllBytes(p));
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		}
		return classes;
	}
}
