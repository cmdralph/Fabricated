package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import io.github.cmdralph.fabricated.core.testutil.TestSources;

/** Public: the stand-in for the loader's class, in another package, calls it. */
public class ObservedPluginsTest {
	/** What the stand-in for the loader's {@code PluginAnswers} was handed, in order. */
	private static final List<String> ANSWERS = Collections.synchronizedList(new ArrayList<>());

	/** The stand-in's answer: the plugin's, but false for a mixin among those left out, as the loader's. */
	public static boolean answered(boolean apply, String mixin, String leftOut) {
		ANSWERS.add(apply + " " + mixin);
		return apply && !List.of(leftOut.split("\n")).contains(mixin);
	}

	@Test
	void aPluginTellsWhatItAnswersAndTurnsDownWhatPreflightLeftOutWhole(@TempDir Path dir) throws Exception {
		// LEAVE-4: Pehkui's plugin turns down the compatNNN mixins of other versions, which pre-flight leaves out too.
		Map<String, byte[]> classes = TestSources.compile(dir, Map.of(
				"com/example/mixin/Plugin.java", """
						package com.example.mixin;
						public class Plugin {
							public boolean shouldApplyMixin(String target, String mixin) {
								return !mixin.endsWith("Rejected");
							}
						}""",
				"com/example/mixin/Inheriting.java", "package com.example.mixin; public class Inheriting extends Plugin { }",
				"com/example/mixin/Abstract.java", "package com.example.mixin; public abstract class Abstract { public boolean shouldApplyMixin(String t, String m) { return true; } }"));
		byte[] plugin = classes.get("com/example/mixin/Plugin");
		assertTrue(ObservedPlugins.observable(plugin));
		assertFalse(ObservedPlugins.observable(classes.get("com/example/mixin/Inheriting")), "a plugin inheriting the method isn't observed");
		assertFalse(ObservedPlugins.observable(classes.get("com/example/mixin/Abstract")), "Mixin makes no abstract plugin");
		byte[] observed = ObservedPlugins.observe(plugin, List.of("com.example.mixin.LeftOut", "com.example.mixin.Gone"));
		assertFalse(ObservedPlugins.observable(observed), "observed once");

		ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] bytes = name.equals("com.example.mixin.Plugin") ? observed : name.equals(ObservedPlugins.ANSWERS.replace('/', '.')) ? answers() : null;
				if (bytes == null) throw new ClassNotFoundException(name);
				return defineClass(name, bytes, 0, bytes.length);
			}
		};
		Object instance = loader.loadClass("com.example.mixin.Plugin").getDeclaredConstructor().newInstance();
		Method should = instance.getClass().getMethod("shouldApplyMixin", String.class, String.class);
		ANSWERS.clear();
		assertEquals(true, should.invoke(instance, "net.minecraft.world.Thing", "com.example.mixin.Kept"));
		assertEquals(false, should.invoke(instance, "net.minecraft.world.Thing", "com.example.mixin.Rejected"));
		assertEquals(false, should.invoke(instance, "net.minecraft.world.Thing", "com.example.mixin.LeftOut"), "pre-flight left it out whole");
		assertEquals(List.of("true com.example.mixin.Kept", "false com.example.mixin.Rejected", "true com.example.mixin.LeftOut"), ANSWERS);

		assertFalse(ObservedPlugins.canName(Collections.nCopies(2000, "com.example.mixin.a.very.long.package.name.LeftOutMixin")));
		assertThrows(IllegalArgumentException.class, () -> ObservedPlugins.observe(classes.get("com/example/mixin/Inheriting"), List.of()));
	}

	/** The stand-in for the loader's {@code PluginAnswers}: its {@code answered} calls this test's. */
	private static byte[] answers() {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, ObservedPlugins.ANSWERS, null, "java/lang/Object", null);
		String desc = "(ZLjava/lang/String;Ljava/lang/String;)Z";
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "answered", desc, null, null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ILOAD, 0);
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitVarInsn(Opcodes.ALOAD, 2);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, ObservedPluginsTest.class.getName().replace('.', '/'), "answered", desc, false);
		mv.visitInsn(Opcodes.IRETURN);
		mv.visitMaxs(3, 3);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}
}
