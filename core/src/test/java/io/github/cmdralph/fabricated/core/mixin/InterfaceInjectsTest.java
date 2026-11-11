package io.github.cmdralph.fabricated.core.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.testutil.BytesClassLoader;
import io.github.cmdralph.fabricated.core.testutil.TestSources;

/**
 * Fabricated's injector handlers of interface mixins on Forge 47's Mixin (INTERFACE-1, CORPUS-91): marked in
 * translation, merged as Mixin merges a method (here, added to the target as they are), then called from the target as
 * Mixin's injectors (and MixinExtras') call a handler. The callback info classes and MixinExtras' {@code Operation} are
 * stand-ins, as far as the calls use them; the locals Mixin works out are the local variable table's here.
 */
class InterfaceInjectsTest {
	private static final Map<String, String> MIXIN = Map.ofEntries(
			Map.entry("org/spongepowered/asm/mixin/injection/At.java", """
					package org.spongepowered.asm.mixin.injection; import java.lang.annotation.*;
					@Retention(RetentionPolicy.RUNTIME) public @interface At { String value(); int ordinal() default -1; String target() default "";
						int opcode() default -1; Shift shift() default Shift.NONE; int by() default 0; enum Shift { NONE, BEFORE, AFTER, BY } }"""),
			Map.entry("org/spongepowered/asm/mixin/injection/Inject.java", "package org.spongepowered.asm.mixin.injection; import java.lang.annotation.*;"
					+ " import org.spongepowered.asm.mixin.injection.callback.LocalCapture;"
					+ " @Retention(RetentionPolicy.RUNTIME) public @interface Inject { String[] method() default {}; At[] at(); boolean cancellable() default false;"
					+ " String id() default \"\"; int require() default -1; int allow() default -1; LocalCapture locals() default LocalCapture.NO_CAPTURE;"
					+ " Slice[] slice() default {}; }"),
			Map.entry("org/spongepowered/asm/mixin/injection/Slice.java", "package org.spongepowered.asm.mixin.injection; public @interface Slice { At from(); }"),
			Map.entry("org/spongepowered/asm/mixin/injection/Redirect.java", "package org.spongepowered.asm.mixin.injection; import java.lang.annotation.*;"
					+ " @Retention(RetentionPolicy.RUNTIME) public @interface Redirect { String[] method(); At at(); Slice slice() default @Slice(from = @At(\"HEAD\")); }"),
			Map.entry("org/spongepowered/asm/mixin/injection/ModifyConstant.java", "package org.spongepowered.asm.mixin.injection; import java.lang.annotation.*;"
					+ " @Retention(RetentionPolicy.RUNTIME) public @interface ModifyConstant { String[] method(); Constant[] constant(); }"),
			Map.entry("org/spongepowered/asm/mixin/injection/ModifyVariable.java", "package org.spongepowered.asm.mixin.injection; import java.lang.annotation.*;"
					+ " @Retention(RetentionPolicy.RUNTIME) public @interface ModifyVariable { String[] method(); At at(); }"),
			Map.entry("org/spongepowered/asm/mixin/injection/Constant.java", "package org.spongepowered.asm.mixin.injection; public @interface Constant {"
					+ " int intValue() default 0; double doubleValue() default 0; Class<?> classValue() default Object.class; int ordinal() default -1;"
					+ " boolean expandZeroConditions() default false; }"),
			Map.entry("org/spongepowered/asm/mixin/injection/Coerce.java", "package org.spongepowered.asm.mixin.injection; public @interface Coerce { }"),
			Map.entry("org/spongepowered/asm/mixin/injection/callback/LocalCapture.java", "package org.spongepowered.asm.mixin.injection.callback;"
					+ " public enum LocalCapture { NO_CAPTURE, PRINT, CAPTURE_FAILSOFT, CAPTURE_FAILHARD, CAPTURE_FAILEXCEPTION }"),
			Map.entry("org/spongepowered/asm/mixin/injection/callback/CallbackInfo.java", """
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
					"""),
			Map.entry("org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable.java", """
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
					"""),
			Map.entry("com/llamalad7/mixinextras/injector/wrapoperation/WrapOperation.java", "package com.llamalad7.mixinextras.injector.wrapoperation;"
					+ " import java.lang.annotation.*; import org.spongepowered.asm.mixin.injection.At;"
					+ " @Retention(RetentionPolicy.RUNTIME) public @interface WrapOperation { String[] method(); At[] at(); }"),
			Map.entry("com/llamalad7/mixinextras/injector/wrapoperation/Operation.java", "package com.llamalad7.mixinextras.injector.wrapoperation;"
					+ " public interface Operation<R> { R call(Object... args); }"),
			Map.entry("com/llamalad7/mixinextras/injector/wrapoperation/WrapOperationRuntime.java", "package com.llamalad7.mixinextras.injector.wrapoperation;"
					+ " public class WrapOperationRuntime { public static void checkArgumentCount(Object[] args, int expected, String types) {"
					+ " if (args.length != expected) throw new IllegalArgumentException(args.length + \" where \" + types); } }"),
			Map.entry("com/llamalad7/mixinextras/sugar/Local.java", "package com.llamalad7.mixinextras.sugar;"
					+ " public @interface Local { int ordinal() default -1; int index() default -1; boolean argsOnly() default false; String[] name() default {}; }"));
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
			"test/Box.java", """
					package test;
					import java.util.List;
					import java.util.function.Predicate;
					public interface Box {
						List<String> items();
						default int count(String item) {
							int n = 0;
							for (int i = 0; i < items().size(); i++) {
								String s = items().get(i);
								if (s.equals(item)) n += s.length();
							}
							return n;
						}
						default boolean any(Predicate<String> test) {
							for (int i = 0; i < items().size(); i++) {
								String s = items().get(i);
								if (test.test(s)) return true;
							}
							return false;
						}
						default int max() { return 64; }
						static double scale(double x) { return x * 8.0; }
						static String label(String s) { return s.trim() + Seen.SUFFIX; }
						static long wide(long a, int b) { return Math.addExact(a, b); }
						static boolean isBasket(Object o) { return o instanceof Basket; }
						static Basket asBasket(Object o) { return (Basket) o; }
						static Class<?> kind() { return Basket.class; }
					}
					""",
			"test/Basket.java", "package test; public record Basket(java.util.List<String> items) implements Box { }",
			"test/Seen.java", "package test; public final class Seen { public static final java.util.List<Object> LOG = new java.util.ArrayList<>();"
					+ " public static String SUFFIX = \"?\"; }");
	private static final Map<String, String> MOD = Map.of(
			"test/mixin/ChainMixin.java", """
					package test.mixin;
					import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
					import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
					import org.spongepowered.asm.mixin.Mixin;
					import org.spongepowered.asm.mixin.injection.*;
					import test.Box;
					@Mixin(Box.class)
					public interface ChainMixin {
						@WrapOperation(method = "label", at = @At(value = "INVOKE", target = "Ljava/lang/String;trim()Ljava/lang/String;"))
						private static String bracket(String s, Operation<String> trim) { return "[" + trim.call(s) + "]"; }
						@Redirect(method = "label", at = @At(value = "INVOKE", target = "Ljava/lang/String;trim()Ljava/lang/String;"))
						private static String upper(String s) { return s.trim().toUpperCase(); }
						@WrapOperation(method = "label", at = @At(value = "INVOKE", target = "Ljava/lang/String;trim()Ljava/lang/String;"))
						private static String star(String s, Operation<String> trim) { return "*" + trim.call(s) + "*"; }
						@Redirect(method = "label", at = @At(value = "INVOKE", target = "Ljava/lang/String;trim()Ljava/lang/String;"))
						private static String lost(String s) { return "lost"; }
						@WrapOperation(method = "count", at = @At(value = "INVOKE", target = "Ljava/lang/String;length()I"))
						private int tenfold(String s, Operation<Integer> length, String item) { return length.call(s) * 10; }
						@WrapOperation(method = "count", at = @At(value = "INVOKE", target = "Ljava/lang/String;length()I"))
						private int plusOne(String s, Operation<Integer> length) { return length.call(s) + 1; }
						@ModifyConstant(method = "max", constant = @Constant(intValue = 64))
						private int seven(int max) { return max / 8 - 1; }
						@ModifyConstant(method = "max", constant = @Constant(intValue = 64))
						private int halve(int max) { return max / 2; }
					}
					""",
			"test/mixin/LocalMixin.java", """
					package test.mixin;
					import java.util.function.Predicate;
					import com.llamalad7.mixinextras.sugar.Local;
					import org.spongepowered.asm.mixin.Mixin;
					import org.spongepowered.asm.mixin.injection.*;
					import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
					import test.Box;
					import test.Seen;
					@Mixin(Box.class)
					public interface LocalMixin {
						@Redirect(method = "label", at = @At(value = "INVOKE", target = "Ljava/lang/String;trim()Ljava/lang/String;"))
						private static String upper(String s) { return s.trim().toUpperCase(); }
						@Inject(method = "label", at = @At(value = "FIELD", target = "Ltest/Seen;SUFFIX:Ljava/lang/String;", opcode = 178))
						private static void seen(String s, CallbackInfoReturnable<String> cir, @Local String local) { Seen.LOG.add("local " + local); }
						@Redirect(method = "any", at = @At(value = "INVOKE", target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z"))
						private boolean noLong(Predicate<String> test, Object s, @Local long l) { return true; }
					}
					""",
			"test/mixin/ClassConstantMixin.java", """
					package test.mixin;
					import org.spongepowered.asm.mixin.Mixin;
					import org.spongepowered.asm.mixin.injection.*;
					import test.Basket;
					import test.Box;
					@Mixin(Box.class)
					public interface ClassConstantMixin {
						@ModifyConstant(method = "isBasket", constant = @Constant(classValue = Basket.class))
						private static Class<?> checked(Class<?> type) { return type; }
						@ModifyConstant(method = "asBasket", constant = @Constant(classValue = Basket.class))
						private static Class<?> cast(Class<?> type) { return type; }
						@ModifyConstant(method = "kind", constant = @Constant(classValue = Basket.class))
						private static Class<?> widened(Class<?> type) { return Object.class; }
					}
					""",
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
					""",
			"test/mixin/BoxMixin.java", """
					package test.mixin;
					import java.util.function.Predicate;
					import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
					import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
					import com.llamalad7.mixinextras.sugar.Local;
					import org.spongepowered.asm.mixin.Mixin;
					import org.spongepowered.asm.mixin.injection.*;
					import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
					import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
					import test.Box;
					import test.Seen;
					@Mixin(Box.class)
					public interface BoxMixin {
						@Inject(method = "count", at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;"), locals = LocalCapture.CAPTURE_FAILSOFT)
						private void slot(String item, CallbackInfoReturnable<Integer> cir, int n, int i) { Seen.LOG.add("slot " + i + " after " + n); }
						@Inject(method = "count", at = @At("RETURN"), locals = LocalCapture.CAPTURE_FAILSOFT)
						private void counted(String item, CallbackInfoReturnable<Integer> cir, int n) { Seen.LOG.add("counted " + n + " " + cir.getReturnValueI()); }
						@Inject(method = "count", at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;"), locals = LocalCapture.CAPTURE_FAILSOFT,
								require = 0)
						private void misread(String item, CallbackInfoReturnable<Integer> cir, String s) { Seen.LOG.add("misread"); }
						@Inject(method = "label", at = @At(value = "INVOKE", target = "Ljava/lang/String;trim()Ljava/lang/String;", shift = At.Shift.AFTER))
						private static void trimmed(String s, CallbackInfoReturnable<String> cir) { Seen.LOG.add("trimmed " + s); }
						@Inject(method = "label", at = @At(value = "FIELD", target = "Ltest/Seen;SUFFIX:Ljava/lang/String;", opcode = 178), cancellable = true)
						private static void suffix(String s, CallbackInfoReturnable<String> cir) { if (s.isBlank()) cir.setReturnValue("empty"); }
						@Redirect(method = "any", at = @At(value = "INVOKE", target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z"))
						private boolean test(Predicate<String> test, Object s, Predicate<String> same, @Local int i) {
							Seen.LOG.add("test " + i + " " + (test == same));
							return i == 1 || test.test((String) s);
						}
						@WrapOperation(method = "count", at = @At(value = "INVOKE", target = "Ljava/lang/String;length()I"))
						private int tenfold(String s, Operation<Integer> length, String item) { return length.call(s) * 10 + (s.equals(item) ? 0 : 1000); }
						@WrapOperation(method = "wide", at = @At(value = "INVOKE", target = "Ljava/lang/Math;addExact(JJ)J"))
						private static long plusOne(long a, long b, Operation<Long> add) { return add.call(a, b + 1); }
						@ModifyConstant(method = "max", constant = @Constant(intValue = 64))
						private int seven(int max) { return max / 8 - 1; }
						@ModifyConstant(method = "scale", constant = @Constant(doubleValue = 8.0))
						private static double half(double eight, double x) { return x < 0 ? 0 : eight / 2; }
					}
					""",
			"test/mixin/RefusedMixin.java", """
					package test.mixin;
					import java.util.function.Predicate;
					import com.llamalad7.mixinextras.sugar.Local;
					import org.spongepowered.asm.mixin.Mixin;
					import org.spongepowered.asm.mixin.injection.*;
					import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
					import test.Box;
					@Mixin(Box.class)
					public interface RefusedMixin {
						@Redirect(method = "any", at = @At(value = "INVOKE", target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z"), slice = @Slice(from = @At("HEAD")))
						private boolean sliced(Predicate<String> test, Object s) { return true; }
						@ModifyVariable(method = "count", at = @At("HEAD"))
						private String variable(String item) { return item; }
						@Redirect(method = "any", at = @At(value = "INVOKE", target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z"))
						private boolean coerced(@Coerce Object test, Object s) { return true; }
						@Redirect(method = "any", at = @At(value = "INVOKE", target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z"))
						private int wrongReturn(Predicate<String> test, Object s) { return 0; }
						@Redirect(method = "any", at = @At(value = "INVOKE", target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z"))
						private boolean named(Predicate<String> test, Object s, @Local(name = "i") int i) { return true; }
						@ModifyConstant(method = "max", constant = @Constant(intValue = 64, expandZeroConditions = true))
						private int expanded(int max) { return max; }
						@ModifyConstant(method = "scale", constant = @Constant(doubleValue = 8.0))
						private double notStatic(double eight) { return eight; }
						@Inject(method = "count", at = @At(value = "INVOKE", target = "Ljava/lang/String;length()I", shift = At.Shift.BY, by = 2))
						private void shifted(String item, CallbackInfoReturnable<Integer> cir) { }
					}
					""");

	@Test
	void anInterfaceMixinsInjectsAtHeadReturnAndTailAreCalledAsMixinWouldCallThem(@TempDir Path dir) throws Exception {
		Map<String, byte[]> classes = TestSources.compile(dir, TestSources.MIXIN_ANNOTATIONS, MIXIN, GAME, MOD);
		ClassNode mixin = node(classes.get("test/mixin/CopperMixin"));
		ClassNode target = node(classes.get("test/Copper"));

		Map<String, String> refused = mark(mixin, target);
		assertEquals(Map.of("never", "it finds no RETURN in none()[I in test.Copper",
				"invoked", "it finds no INVOKE Ljava/lang/String;length()I in name(Ljava/lang/String;)Ljava/lang/String; in test.Copper",
				"wrongInfo", "its handler doesn't take the callback info twice(I)I in test.Copper gives"), refused);
		MethodNode quiet = method(mixin, "fabricated$CopperMixin$quiet");
		assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, quiet.access, "what Mixin 0.8.5 merges from an interface mixin");
		assertNull(Annotations.find(quiet.visibleAnnotations, MixinNames.INJECT), "no injector Mixin 0.8.5 reads, which it refuses in an interface mixin");
		assertNotNull(Annotations.find(quiet.invisibleAnnotations, MixinNames.INJECT), "the injector, which pre-flight checks and Fabricated applies");
		assertTrue(Annotations.find(method(mixin, "never"), MixinNames.INJECT) != null, "a refused handler is left as it was");

		List<String> warnings = new ArrayList<>();
		assertEquals(5, InterfaceInjects.apply(merged(mixin, target), InterfaceInjectsTest::localsAt, warnings::add));
		assertEquals(List.of(), warnings);
		assertEquals(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, method(target, "fabricated$CopperMixin$quiet").access);
		assertNull(Annotations.find(method(target, "fabricated$CopperMixin$quiet"), InterfaceInjects.MARKER));
		assertNull(Annotations.find(method(target, "fabricated$CopperMixin$quiet"), MixinNames.INJECT));

		Map<String, byte[]> loaded = new HashMap<>(classes);
		loaded.put("test/Copper", write(target));
		ClassLoader loader = new BytesClassLoader(loaded);
		Class<?> copper = loader.loadClass("test.Copper");
		List<Object> seen = seen(loader);

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
	void anInterfaceMixinsInjectsAtCallsAndFieldsCaptureTheLocalsMixinFinds(@TempDir Path dir) throws Exception {
		Map<String, byte[]> classes = TestSources.compileWithLocals(dir, TestSources.MIXIN_ANNOTATIONS, MIXIN, GAME, MOD);
		ClassNode mixin = node(classes.get("test/mixin/BoxMixin"));
		ClassNode target = node(classes.get("test/Box"));
		assertEquals(Map.of(), mark(mixin, target));
		mixin.methods.removeIf(m -> !m.name.equals("fabricated$BoxMixin$slot") && !m.name.equals("fabricated$BoxMixin$counted")
				&& !m.name.equals("fabricated$BoxMixin$misread") && !m.name.equals("fabricated$BoxMixin$trimmed") && !m.name.equals("fabricated$BoxMixin$suffix"));

		List<String> warnings = new ArrayList<>();
		assertEquals(5, InterfaceInjects.apply(merged(mixin, target), InterfaceInjectsTest::localsAt, warnings::add));
		assertEquals(List.of("@Inject handler fabricated$BoxMixin$misread of an interface mixin captures the locals [java.lang.String] in"
				+ " count(Ljava/lang/String;)I in test.Box, where Mixin finds [int]; it is skipped there, as Mixin skips it (CAPTURE_FAILSOFT, INTERFACE-1)"),
				warnings, "captured otherwise than Mixin finds them, failing soft");

		ClassLoader loader = load(classes, target);
		List<Object> seen = seen(loader);
		Object basket = loader.loadClass("test.Basket").getConstructor(List.class).newInstance(List.of("ab", "c", "ab"));
		assertEquals(4, basket.getClass().getMethod("count", String.class).invoke(basket, "ab"));
		assertEquals(List.of("slot 0 after 0", "slot 1 after 2", "slot 2 after 2", "counted 4 4"), seen,
				"the locals in scope before each call, and at the return");
		seen.clear();
		Class<?> box = loader.loadClass("test.Box");
		assertEquals("a?", call(box, "label", String.class, " a "));
		assertEquals("empty", call(box, "label", String.class, " "), "cancelled where the field is read");
		assertEquals(List.of("trimmed  a ", "trimmed  "), seen, "after the call its @At shifts after");
	}

	@Test
	void anInterfaceMixinsRedirectWrapOperationAndModifyConstantApplyAsMixinWould(@TempDir Path dir) throws Exception {
		Map<String, byte[]> classes = TestSources.compileWithLocals(dir, TestSources.MIXIN_ANNOTATIONS, MIXIN, GAME, MOD);
		ClassNode mixin = node(classes.get("test/mixin/BoxMixin"));
		ClassNode target = node(classes.get("test/Box"));
		assertEquals(Map.of(), mark(mixin, target));
		mixin.methods.removeIf(m -> m.name.equals("fabricated$BoxMixin$slot") || m.name.equals("fabricated$BoxMixin$counted")
				|| m.name.equals("fabricated$BoxMixin$misread") || m.name.equals("fabricated$BoxMixin$trimmed") || m.name.equals("fabricated$BoxMixin$suffix"));

		List<String> warnings = new ArrayList<>();
		assertEquals(5, InterfaceInjects.apply(merged(mixin, target), InterfaceInjectsTest::localsAt, warnings::add));
		assertEquals(List.of(), warnings);
		assertTrue(target.methods.stream().anyMatch(m -> m.name.equals("fabricated$operation$fabricated$BoxMixin$tenfold")
				&& m.access == (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC)), "the operation's bridge");

		ClassLoader loader = load(classes, target);
		List<Object> seen = seen(loader);
		Class<?> box = loader.loadClass("test.Box");
		Object basket = loader.loadClass("test.Basket").getConstructor(List.class).newInstance(List.of("ab", "c", "ab"));
		assertEquals(40, box.getMethod("count", String.class).invoke(basket, "ab"), "each length wrapped, the wrapper given the target's parameter");
		Predicate<String> never = s -> false;
		assertEquals(true, box.getMethod("any", Predicate.class).invoke(basket, never), "the redirected call's result");
		assertEquals(List.of("test 0 true", "test 1 true"), seen, "the redirector given the call's receiver, the target's parameter and the local");
		assertEquals(7, box.getMethod("max").invoke(basket), "the constant modified in a default method");
		assertEquals(12.0, call(box, "scale", double.class, 3.0), "and in a static one, with the target's parameter");
		assertEquals(-0.0, call(box, "scale", double.class, -3.0));
		assertEquals(8L, box.getMethod("wide", long.class, int.class).invoke(null, 4L, 3), "a static call wrapped, its operation given other arguments");
	}

	@Test
	void whatFabricatedDoesntApplyAsMixinWouldIsLeftOutWithWhy(@TempDir Path dir) throws Exception {
		Map<String, byte[]> classes = TestSources.compileWithLocals(dir, TestSources.MIXIN_ANNOTATIONS, MIXIN, GAME, MOD);
		ClassNode mixin = node(classes.get("test/mixin/RefusedMixin"));
		ClassNode target = node(classes.get("test/Box"));
		Map<String, String> refused = mark(mixin, target);
		assertEquals(Map.of("sliced", "its @Redirect sets slice",
				"variable", "it is a @ModifyVariable",
				"coerced", "its handler's parameter 1 is annotated @Coerce",
				"wrongReturn", "its handler doesn't take and return what its @Redirect gives it in any(Ljava/util/function/Predicate;)Z in test.Box:"
						+ " (Ljava/util/function/Predicate;Ljava/lang/Object;)Z",
				"named", "its @Local sets name",
				"expanded", "its @Constant sets expandZeroConditions",
				"notStatic", "its handler is not static where scale(D)D in test.Box is",
				"shifted", "its @At sets by"), refused);
		assertTrue(mixin.methods.stream().noneMatch(InterfaceInjects::marked));

		// Captured otherwise than Mixin finds them, failing hard: Mixin fails the class.
		ClassNode box = node(classes.get("test/mixin/BoxMixin"));
		mark(box, target);
		MethodNode misread = method(box, "fabricated$BoxMixin$misread");
		AnnotationNode inject = Annotations.find(misread.invisibleAnnotations, MixinNames.INJECT);
		Annotations.put(inject, "locals", new String[] {"Lorg/spongepowered/asm/mixin/injection/callback/LocalCapture;", "CAPTURE_FAILHARD"});
		box.methods.removeIf(m -> m != misread);
		IllegalStateException failed = assertThrows(IllegalStateException.class, () -> InterfaceInjects.apply(merged(box, target), InterfaceInjectsTest::localsAt, w -> { }));
		assertEquals("The handler fabricated$BoxMixin$misread(Ljava/lang/String;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;"
				+ "Ljava/lang/String;)V of an interface mixin can't apply in test.Box: @Inject handler fabricated$BoxMixin$misread of an interface mixin"
				+ " captures the locals [java.lang.String] in count(Ljava/lang/String;)I in test.Box, where Mixin finds [int] (INTERFACE-1)", failed.getMessage());
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
		IllegalStateException failed = assertThrows(IllegalStateException.class, () -> InterfaceInjects.apply(target, InterfaceInjectsTest::localsAt, w -> { }));
		assertEquals("The handler fabricated$CopperMixin$floor(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V of an interface"
				+ " mixin can't apply in test.Copper: it injects 0 time(s), where its @Inject requires 1 (INTERFACE-1)", failed.getMessage());
	}

	/**
	 * Over another handler's call replacing a call, a {@code @WrapOperation} wraps that call, as MixinExtras chains them
	 * (its {@code Operation} passing on what that handler takes after the call's receiver and arguments, static or not),
	 * once the redirects have applied, whichever the mixin declares first; a second {@code @Redirect} is left out there
	 * with a warning, not failing the class; a second {@code @ModifyConstant} of a constant modifies it before the first,
	 * as Mixin wraps the constant again.
	 */
	@Test
	void handlersOverAnotherHandlersCallChainAsMixinExtrasChainsThem(@TempDir Path dir) throws Exception {
		Map<String, byte[]> classes = TestSources.compileWithLocals(dir, TestSources.MIXIN_ANNOTATIONS, MIXIN, GAME, MOD);
		ClassNode mixin = node(classes.get("test/mixin/ChainMixin"));
		ClassNode target = node(classes.get("test/Box"));
		assertEquals(Map.of(), mark(mixin, target));

		List<String> warnings = new ArrayList<>();
		assertEquals(8, InterfaceInjects.apply(merged(mixin, target), InterfaceInjectsTest::localsAt, warnings::add));
		assertEquals(List.of("@Redirect handler fabricated$ChainMixin$lost of an interface mixin is left out in label(Ljava/lang/String;)Ljava/lang/String;"
				+ " in test.Box: another handler already replaces the call there (INTERFACE-2)"), warnings);

		ClassLoader loader = load(classes, target);
		Class<?> box = loader.loadClass("test.Box");
		assertEquals("*[A]*?", call(box, "label", String.class, " a "), "the wrapper of the wrapper of the redirect");
		Object basket = loader.loadClass("test.Basket").getConstructor(List.class).newInstance(List.of("ab", "c", "ab"));
		assertEquals(42, box.getMethod("count", String.class).invoke(basket, "ab"), "the outer wrapper's operation given the inner's and its parameter");
		assertEquals(3, box.getMethod("max").invoke(basket), "the second modifier first");
	}

	/**
	 * A {@code @Local} the locals at a point don't tell, or a point Mixin can't work out the locals at, leaves the handler
	 * out there with a warning, not failing the class; and the locals a handler is given are those Mixin works out
	 * without the ones Fabricated's calls of other handlers store, so an implicit {@code @Local} doesn't find a redirected
	 * call's receiver too.
	 */
	@Test
	void aLocalTheLocalsDontTellLeavesItsHandlerOutThereAndFabricatedsOwnLocalsArentAmongThem(@TempDir Path dir) throws Exception {
		Map<String, byte[]> classes = TestSources.compileWithLocals(dir, TestSources.MIXIN_ANNOTATIONS, MIXIN, GAME, MOD);
		ClassNode mixin = node(classes.get("test/mixin/LocalMixin"));
		ClassNode target = node(classes.get("test/Box"));
		assertEquals(Map.of(), mark(mixin, target));
		mixin.methods.removeIf(m -> m.name.equals("fabricated$LocalMixin$noLong"));

		// A frame analysis sees the receiver the redirect stores, as Mixin's does where it generates a local variable table.
		List<String> warnings = new ArrayList<>();
		assertEquals(2, InterfaceInjects.apply(merged(mixin, target), InterfaceInjectsTest::analysedAt, warnings::add));
		assertEquals(List.of(), warnings);
		ClassLoader loader = load(classes, target);
		assertEquals("A?", call(loader.loadClass("test.Box"), "label", String.class, " a "));
		assertEquals(List.of("local  a "), seen(loader), "the parameter, the one String local Mixin finds");

		ClassNode other = node(classes.get("test/mixin/LocalMixin"));
		ClassNode box = node(classes.get("test/Box"));
		mark(other, box);
		other.methods.removeIf(m -> m.name.equals("fabricated$LocalMixin$upper"));
		warnings.clear();
		assertEquals(2, InterfaceInjects.apply(merged(other, box), (m, insn) -> m.name.equals("label") ? null : localsAt(m, insn), warnings::add));
		assertEquals(List.of("@Inject handler fabricated$LocalMixin$seen of an interface mixin is left out in label(Ljava/lang/String;)Ljava/lang/String;"
				+ " in test.Box: its @Local java.lang.String finds no locals Mixin can tell there (INTERFACE-2)",
				"@Redirect handler fabricated$LocalMixin$noLong of an interface mixin is left out in any(Ljava/util/function/Predicate;)Z in test.Box:"
						+ " its @Local long finds no local of that type there (INTERFACE-2)"), warnings);
		loader = load(classes, box);
		assertEquals("a?", call(loader.loadClass("test.Box"), "label", String.class, " a "));
		Object basket = loader.loadClass("test.Basket").getConstructor(List.class).newInstance(List.of("ab"));
		Predicate<String> never = s -> false;
		assertEquals(false, loader.loadClass("test.Box").getMethod("any", Predicate.class).invoke(basket, never), "the call as it was");
		assertEquals(List.of(), seen(loader));
	}

	/**
	 * A {@code @ModifyConstant} of a class modifies the class a constant loads; one finding the class a cast or an
	 * instanceof check names, which Mixin refuses or gives the object checked too, is left out with why.
	 */
	@Test
	void anInterfaceMixinsClassConstantIsModifiedWhereLoadedOnly(@TempDir Path dir) throws Exception {
		Map<String, byte[]> classes = TestSources.compileWithLocals(dir, TestSources.MIXIN_ANNOTATIONS, MIXIN, GAME, MOD);
		ClassNode mixin = node(classes.get("test/mixin/ClassConstantMixin"));
		ClassNode target = node(classes.get("test/Box"));
		assertEquals(Map.of("checked", "its @Constant test.Basket is the class an instanceof check names in isBasket(Ljava/lang/Object;)Z, which Fabricated"
						+ " doesn't modify",
				"cast", "its @Constant test.Basket is the class a cast names in asBasket(Ljava/lang/Object;)Ltest/Basket;, which Fabricated doesn't modify"),
				mark(mixin, target));
		assertEquals(1, InterfaceInjects.apply(merged(mixin, target), InterfaceInjectsTest::localsAt, w -> { }));
		ClassLoader loader = load(classes, target);
		Class<?> box = loader.loadClass("test.Box");
		assertEquals(Object.class, box.getMethod("kind").invoke(null));
		assertEquals(true, call(box, "isBasket", Object.class, loader.loadClass("test.Basket").getConstructor(List.class).newInstance(List.of())));
	}

	/** Marks each of the mixin's handlers, returning those refused, by their name, with why. */
	private static Map<String, String> mark(ClassNode mixin, ClassNode target) {
		Map<String, String> refused = new TreeMap<>();
		for (MethodNode m : List.copyOf(mixin.methods)) {
			AnnotationNode injector = Annotations.all(m).stream().filter(a -> PreflightAsm.isInjector(a.desc)).findFirst().orElse(null);
			if (injector == null) continue;
			String name = m.name;
			String why = InterfaceInjects.mark(mixin, m, injector, List.of(target), s -> s, 1);
			if (why != null) refused.put(name, why);
		}
		return refused;
	}

	/** The target as Mixin leaves it: the marked handlers merged into it as methods of its own. */
	private static ClassNode merged(ClassNode mixin, ClassNode target) {
		for (MethodNode m : mixin.methods) {
			if (InterfaceInjects.marked(m)) target.methods.add(m);
		}
		return target;
	}

	/** The locals Mixin finds at an instruction of a method with a local variable table: those it has in scope there. */
	private static String[] localsAt(MethodNode method, AbstractInsnNode insn) {
		int at = method.instructions.indexOf(insn);
		String[] slots = new String[method.maxLocals];
		for (LocalVariableNode local : method.localVariables) {
			if (method.instructions.indexOf(local.start) <= at && at < method.instructions.indexOf(local.end)) slots[local.index] = local.desc;
		}
		return slots;
	}

	/**
	 * The locals a frame analysis finds at an instruction of straight-line code, as Mixin's does where it generates a
	 * method's local variable table: every slot stored before it, temporaries too.
	 */
	private static String[] analysedAt(MethodNode method, AbstractInsnNode insn) {
		AnalyzerAdapter frame = new AnalyzerAdapter("test/Box", method.access, method.name, method.desc, null);
		for (AbstractInsnNode i = method.instructions.getFirst(); i != insn; i = i.getNext()) {
			if (!(i instanceof FrameNode)) i.accept(frame);
		}
		String[] slots = new String[method.maxLocals];
		for (int slot = 0; slot < frame.locals.size() && slot < slots.length; slot++) {
			if (frame.locals.get(slot) instanceof String type) slots[slot] = Type.getObjectType(type).getDescriptor();
		}
		return slots;
	}

	private static ClassLoader load(Map<String, byte[]> classes, ClassNode target) {
		Map<String, byte[]> loaded = new HashMap<>(classes);
		loaded.put(target.name, write(target));
		return new BytesClassLoader(loaded);
	}

	@SuppressWarnings("unchecked")
	private static List<Object> seen(ClassLoader loader) throws Exception {
		return (List<Object>) loader.loadClass("test.Seen").getField("LOG").get(null);
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
