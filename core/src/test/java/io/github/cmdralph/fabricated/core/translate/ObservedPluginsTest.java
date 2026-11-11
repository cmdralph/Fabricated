package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;
import io.github.cmdralph.fabricated.core.bytecode.InstalledClasses;
import io.github.cmdralph.fabricated.core.mixin.MixinConfigFile;
import io.github.cmdralph.fabricated.core.mixin.MixinPreflight;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding.Severity;
import io.github.cmdralph.fabricated.core.mixin.Refmap;
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

	@Test
	void aMixinLeftOutWholeIsMadeInertWhateverThePluginAnswers(@TempDir Path dir) throws Exception {
		// LEAVE-4: Mixin applies every mixin of a config whose plugin it couldn't load, the ones pre-flight left out whole too.
		Map<String, byte[]> classes = TestSources.compile(dir, Map.of(
				"net/minecraft/world/Thing.java", "package net.minecraft.world; public class Thing { public void tick() { } }",
				"net/minecraft/world/Shape.java", "package net.minecraft.world; public interface Shape { }",
				"com/example/mixin/Broken.java", """
						package com.example.mixin;
						@org.spongepowered.asm.mixin.Mixin(value = net.minecraft.world.Thing.class, priority = 900)
						public abstract class Broken extends Object implements Runnable {
							@org.spongepowered.asm.mixin.Shadow private int missing;
							private int count = 1;
							public void run() { missing++; }
							private static class Inner { }
						}""",
				"com/example/mixin/Gone.java", """
						package com.example.mixin;
						@org.spongepowered.asm.mixin.Mixin(targets = {"net.minecraft.world.Gone", "net.minecraft.world.Shape"})
						public interface Gone { @org.spongepowered.asm.mixin.gen.Accessor("value") int value(); }"""),
				mixinStubs());
		ClassIndex game = new ClassIndex(Map.of("net/minecraft/world/Thing", classes.get("net/minecraft/world/Thing"),
				"net/minecraft/world/Shape", classes.get("net/minecraft/world/Shape"))::get);
		MixinConfigFile config = MixinConfigFile.parse("{\"required\":true,\"package\":\"com.example.mixin\",\"plugin\":\"com.example.mixin.Plugin\","
				+ "\"mixins\":[\"Broken\",\"Gone\"]}");
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Broken", classes.get("com/example/mixin/Broken"), "com/example/mixin/Gone",
				classes.get("com/example/mixin/Gone")));
		ClassBytesSource view = mod::get;
		List<PreflightFinding> before = new MixinPreflight(game, InstalledClasses.NONE, view, ClassBytesSource.EMPTY, false, true, ClassBytesSource.EMPTY)
				.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertEquals(2, before.stream().filter(f -> f.severity() == Severity.LEFT_OUT).map(PreflightFinding::mixin).distinct().count(), before.toString());

		mod.replaceAll((name, bytes) -> ObservedPlugins.inert(bytes));
		for (String name : List.of("com/example/mixin/Broken", "com/example/mixin/Gone")) {
			ClassNode node = new ClassNode();
			new ClassReader(mod.get(name)).accept(node, 0);
			assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, node.access, name);
			assertEquals("java/lang/Object", node.superName, name);
			assertTrue(node.interfaces.isEmpty() && node.fields.isEmpty() && node.methods.isEmpty() && node.innerClasses.isEmpty(), name);
			assertEquals(List.of("Lorg/spongepowered/asm/mixin/Mixin;", "Lorg/spongepowered/asm/mixin/Pseudo;"),
					node.invisibleAnnotations.stream().map(a -> a.desc).toList(), name);
			assertNull(node.visibleAnnotations, name);
		}
		ClassNode broken = new ClassNode();
		new ClassReader(mod.get("com/example/mixin/Broken")).accept(broken, 0);
		assertEquals(List.of("value", List.of(Type.getObjectType("net/minecraft/world/Thing")), "priority", 900), broken.invisibleAnnotations.get(0).values,
				"the same @Mixin: Mixin reads its targets' names and asks the plugin as it would the mixin's");
		// Applied all the same, it has nothing that could fail: a target that isn't there is skipped, an interface is one an
		// accessor mixin, which an empty interface is to Mixin, may target.
		List<PreflightFinding> after = new MixinPreflight(game, InstalledClasses.NONE, view, ClassBytesSource.EMPTY, false, true, ClassBytesSource.EMPTY)
				.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.stream().noneMatch(f -> f.severity() == Severity.LEFT_OUT || f.severity() == Severity.FAIL), after.toString());
	}

	/** Mixin's annotations, {@code @Mixin} with its targets by name and its priority. */
	private static Map<String, String> mixinStubs() {
		Map<String, String> stubs = new HashMap<>(TestSources.MIXIN_ANNOTATIONS);
		stubs.put("org/spongepowered/asm/mixin/Mixin.java", "package org.spongepowered.asm.mixin; public @interface Mixin {"
				+ " Class<?>[] value() default {}; String[] targets() default {}; int priority() default 1000; }");
		return stubs;
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
