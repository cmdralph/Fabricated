package io.github.cmdralph.fabricated.core.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.testutil.BytesClassLoader;
import io.github.cmdralph.fabricated.core.testutil.TestSources;

/**
 * Fabricated's {@code @Inject} handlers of interface mixins on Forge 47's Mixin (INTERFACE-1, CORPUS-91): marked in
 * translation, merged as Mixin merges a method (here, added to the target as they are), then called from the target as
 * Mixin's {@code CallbackInjector} calls a handler. The callback info classes are Mixin's, as far as the calls use them.
 */
class InterfaceInjectsTest {
	private static final Map<String, String> MIXIN = Map.of(
			"org/spongepowered/asm/mixin/injection/At.java", "package org.spongepowered.asm.mixin.injection; import java.lang.annotation.*;"
					+ " @Retention(RetentionPolicy.RUNTIME) public @interface At { String value(); int ordinal() default -1; String target() default \"\"; }",
			"org/spongepowered/asm/mixin/injection/Inject.java", "package org.spongepowered.asm.mixin.injection; import java.lang.annotation.*;"
					+ " @Retention(RetentionPolicy.RUNTIME) public @interface Inject { String[] method() default {}; At[] at(); boolean cancellable() default false;"
					+ " String id() default \"\"; int require() default -1; int allow() default -1; }",
			"org/spongepowered/asm/mixin/injection/callback/CallbackInfo.java", """
					package org.spongepowered.asm.mixin.injection.callback;
					public class CallbackInfo {
						private final String name;
						private final boolean cancellable;
						private boolean cancelled;
						public CallbackInfo(String name, boolean cancellable) { this.name = name; this.cancellable = cancellable; }
						public String getId() { return name; }
						public boolean isCancellable() { return cancellable; }
						public boolean isCancelled() { return cancelled; }
						public void cancel() { if (!cancellable) throw new IllegalStateException("The call " + name + " is not cancellable."); cancelled = true; }
					}
					""",
			"org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable.java", """
					package org.spongepowered.asm.mixin.injection.callback;
					public class CallbackInfoReturnable<R> extends CallbackInfo {
						private R returnValue;
						public CallbackInfoReturnable(String name, boolean cancellable) { super(name, cancellable); }
						public CallbackInfoReturnable(String name, boolean cancellable, R returnValue) { super(name, cancellable); this.returnValue = returnValue; }
						@SuppressWarnings("unchecked") public CallbackInfoReturnable(String name, boolean cancellable, int returnValue) { this(name, cancellable, (R) Integer.valueOf(returnValue)); }
						@SuppressWarnings("unchecked") public CallbackInfoReturnable(String name, boolean cancellable, long returnValue) { this(name, cancellable, (R) Long.valueOf(returnValue)); }
						public void setReturnValue(R returnValue) { cancel(); this.returnValue = returnValue; }
						public R getReturnValue() { return returnValue; }
						public int getReturnValueI() { return returnValue == null ? 0 : (Integer) returnValue; }
						public long getReturnValueJ() { return returnValue == null ? 0 : (Long) returnValue; }
					}
					""");
	private static final Map<String, String> GAME = Map.of(
			"test/Copper.java", """
					package test;
					import java.util.List;
					public interface Copper {
						static String name(String s) { return s + "!"; }
						static int twice(int x) { if (x < 0) return -1; return x * 2; }
						static int sign(int x) { if (x < 0) return -1; return 1; }
						static long next(long x) { return x + 1; }
						default void touch(List<String> log) { log.add("body"); }
						static int[] none() { throw new UnsupportedOperationException(); }
					}
					""",
			"test/Plain.java", "package test; public class Plain implements Copper { }",
			"test/Seen.java", "package test; public final class Seen { public static final java.util.List<Object> LOG = new java.util.ArrayList<>(); }");
	private static final Map<String, String> MOD = Map.of(
			"test/mixin/CopperMixin.java", """
					package test.mixin;
					import java.util.List;
					import org.spongepowered.asm.mixin.Mixin;
					import org.spongepowered.asm.mixin.injection.At;
					import org.spongepowered.asm.mixin.injection.Inject;
					import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
					import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
					import test.Copper;
					import test.Seen;
					@Mixin(Copper.class)
					public interface CopperMixin {
						@Inject(method = "name", at = @At("HEAD"), cancellable = true)
						private static void quiet(String s, CallbackInfoReturnable<String> cir) { if (s.isEmpty()) cir.setReturnValue("quiet"); }
						@Inject(method = "twice", at = @At("RETURN"), cancellable = true)
						private static void floor(CallbackInfoReturnable<Integer> cir) { Seen.LOG.add(cir.getId()); if (cir.getReturnValueI() < 0) cir.setReturnValue(0); }
						@Inject(method = "sign", at = @At(value = "RETURN", ordinal = 1), id = "positive")
						private static void second(int x, CallbackInfoReturnable<Integer> cir) { Seen.LOG.add(cir.getId() + " " + x + " " + cir.getReturnValueI()); }
						@Inject(method = "next(J)J", at = @At("TAIL"))
						private static void seeNext(long x, CallbackInfoReturnable<Long> cir) { Seen.LOG.add(x + "->" + cir.getReturnValueJ()); }
						@Inject(method = "touch", at = @At("TAIL"))
						private void touched(List<String> log, CallbackInfo ci) { log.add("tail " + ci.isCancellable()); }
						@Inject(method = "none", at = @At("RETURN"))
						private static void never(CallbackInfoReturnable<int[]> cir) { }
						@Inject(method = "name", at = @At(value = "INVOKE", target = "Ljava/lang/String;length()I"))
						private static void invoked(String s, CallbackInfoReturnable<String> cir) { }
						@Inject(method = "twice", at = @At("HEAD"))
						private static void wrongInfo(int x, CallbackInfo ci) { }
					}
					""");

	@Test
	void anInterfaceMixinsInjectsAtHeadReturnAndTailAreCalledAsMixinWouldCallThem(@TempDir Path dir) throws Exception {
		Map<String, byte[]> classes = TestSources.compile(dir, TestSources.MIXIN_ANNOTATIONS, MIXIN, GAME, MOD);
		ClassNode mixin = node(classes.get("test/mixin/CopperMixin"));
		ClassNode target = node(classes.get("test/Copper"));

		Map<String, String> refused = mark(mixin, target);
		assertEquals(Map.of("never", "it finds no RETURN in none()[I in test.Copper", "invoked", "its @At sets target",
				"wrongInfo", "its handler doesn't take the callback info twice(I)I in test.Copper gives"), refused);
		MethodNode quiet = method(mixin, "fabricated$CopperMixin$quiet");
		assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, quiet.access, "what Mixin 0.8.5 merges from an interface mixin");
		assertNull(Annotations.find(quiet, MixinNames.INJECT), "no injector, which Mixin 0.8.5 refuses in an interface mixin");
		assertTrue(Annotations.find(method(mixin, "never"), MixinNames.INJECT) != null, "a refused handler is left as it was");

		// Mixin merges the marked handlers into the target as methods of its own.
		for (MethodNode m : mixin.methods) {
			if (m.invisibleAnnotations != null && Annotations.find(m.invisibleAnnotations, InterfaceInjects.MARKER) != null) target.methods.add(m);
		}
		assertEquals(5, InterfaceInjects.apply(target));
		assertEquals(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, method(target, "fabricated$CopperMixin$quiet").access);
		assertNull(Annotations.find(method(target, "fabricated$CopperMixin$quiet"), InterfaceInjects.MARKER));

		Map<String, byte[]> loaded = new HashMap<>(classes);
		loaded.put("test/Copper", write(target));
		ClassLoader loader = new BytesClassLoader(loaded);
		Class<?> copper = loader.loadClass("test.Copper");
		@SuppressWarnings("unchecked")
		List<Object> seen = (List<Object>) loader.loadClass("test.Seen").getField("LOG").get(null);

		assertEquals("quiet", call(copper, "name", String.class, ""), "cancelled at HEAD with a value");
		assertEquals("a!", call(copper, "name", String.class, "a"));
		assertEquals(0, call(copper, "twice", int.class, -5), "cancelled at a RETURN, with another value");
		assertEquals(6, call(copper, "twice", int.class, 3), "not cancelled at the other RETURN");
		assertEquals(-1, call(copper, "sign", int.class, -2));
		assertEquals(1, call(copper, "sign", int.class, 2));
		assertEquals(5L, call(copper, "next", long.class, 4L));
		assertEquals(List.of("twice", "twice", "positive 2 1", "4->5"), seen, "the callback info's id, at the RETURN its ordinal counts, and a long");
		List<String> log = new ArrayList<>();
		Object plain = loader.loadClass("test.Plain").getConstructor().newInstance();
		copper.getMethod("touch", List.class).invoke(plain, log);
		assertEquals(List.of("body", "tail false"), log, "a default method's TAIL, not cancellable");
	}

	@Test
	void aHandlerFindingFewerPointsThanItsInjectorRequiresFailsTheClassAsMixinWould(@TempDir Path dir) throws Exception {
		Map<String, byte[]> classes = TestSources.compile(dir, TestSources.MIXIN_ANNOTATIONS, MIXIN, GAME, MOD);
		ClassNode mixin = node(classes.get("test/mixin/CopperMixin"));
		ClassNode target = node(classes.get("test/Copper"));
		mark(mixin, target);
		target.methods.add(method(mixin, "fabricated$CopperMixin$floor"));
		// What another transformer may have done to the method after translation checked it.
		target.methods.removeIf(m -> m.name.equals("twice"));
		IllegalStateException failed = assertThrows(IllegalStateException.class, () -> InterfaceInjects.apply(target));
		assertEquals("The handler fabricated$CopperMixin$floor(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V of an interface"
				+ " mixin injects 0 time(s) in test.Copper, where its @Inject requires 1 (INTERFACE-1)", failed.getMessage());
	}

	/** Marks each of the mixin's handlers, returning those refused, by their name, with why. */
	private static Map<String, String> mark(ClassNode mixin, ClassNode target) {
		Map<String, String> refused = new TreeMap<>();
		for (MethodNode m : List.copyOf(mixin.methods)) {
			AnnotationNode inject = Annotations.find(m, MixinNames.INJECT);
			if (inject == null) continue;
			String name = m.name;
			String why = InterfaceInjects.mark(mixin, m, inject, List.of(target), s -> s, 1);
			if (why != null) refused.put(name, why);
		}
		return refused;
	}

	private static Object call(Class<?> owner, String name, Class<?> type, Object arg) throws Exception {
		Method method = owner.getMethod(name, type);
		return method.invoke(null, arg);
	}

	private static MethodNode method(ClassNode node, String name) {
		return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
	}

	private static ClassNode node(byte[] data) {
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, 0);
		return node;
	}

	/** As ModLauncher writes a class Mixin changed: its frames computed. */
	private static byte[] write(ClassNode node) {
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
			@Override
			protected String getCommonSuperClass(String first, String second) {
				return "java/lang/Object";
			}
		};
		node.accept(writer);
		return writer.toByteArray();
	}
}
