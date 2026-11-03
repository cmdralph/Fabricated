package io.github.cmdralph.fabricated.core.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.core.bytecode.InstalledClasses;
import io.github.cmdralph.fabricated.core.testutil.TestSources;

/**
 * CORPUS-87: ImmediatelyFast's Forge side hooks AppleSkin's HUD with a static {@code @Inject} handler, written against
 * AppleSkin's Forge build; AppleSkin's Fabric build declares the method an instance one, and Forge 47's Mixin refuses the
 * handler there, failing the class.
 */
class NativeModMixinsTest {
	private static final String CALLBACK_INFO = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
	private static final Map<String, String> MIXIN = Map.of(
			"org/spongepowered/asm/mixin/Mixin.java",
			"package org.spongepowered.asm.mixin; public @interface Mixin { Class<?>[] value() default {}; String[] targets() default {}; boolean remap() default true; }",
			"org/spongepowered/asm/mixin/Pseudo.java", "package org.spongepowered.asm.mixin; public @interface Pseudo { }",
			"org/spongepowered/asm/mixin/injection/At.java", "package org.spongepowered.asm.mixin.injection; public @interface At { String value(); }",
			"org/spongepowered/asm/mixin/injection/Inject.java",
			"package org.spongepowered.asm.mixin.injection; public @interface Inject { String[] method() default {}; At[] at(); boolean remap() default true; }",
			"org/spongepowered/asm/mixin/injection/callback/CallbackInfo.java", "package org.spongepowered.asm.mixin.injection.callback; public class CallbackInfo { }");
	private static final Map<String, String> APPLESKIN = Map.of("squeek/appleskin/client/HUDOverlayHandler.java", """
			package squeek.appleskin.client;
			public class HUDOverlayHandler {
				public void drawExhaustionOverlay(Object context, float exhaustion) { }
				public void drawExhaustionOverlay(Object context) { }
				public static void drawSaturationOverlay(Object context) { }
			}
			""");
	private static final String HANDLER = "forceDrawBatch(L" + CALLBACK_INFO + ";)V";

	@TempDir
	Path dir;

	@Test
	void aStaticHandlerForAFabricModsInstanceMethodsIsAppliedAsAnInstanceOne() throws Exception {
		List<String> notes = new ArrayList<>();
		Map<String, Set<String>> handlers = NativeModMixins.instanceHandlers(installed(Map.of("if_/MixinAppleSkin.java", """
				package if_;
				import org.spongepowered.asm.mixin.*;
				import org.spongepowered.asm.mixin.injection.*;
				import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
				@Pseudo
				@Mixin(targets = "squeek.appleskin.client.HUDOverlayHandler", remap = false)
				abstract class MixinAppleSkin {
					@Inject(method = "drawExhaustionOverlay", at = @At("RETURN"))
					private static void forceDrawBatch(CallbackInfo ci) { }
					@Inject(method = "drawSaturationOverlay", at = @At("RETURN"))
					private static void forStatic(CallbackInfo ci) { }
					@Inject(method = "drawExhaustionOverlay", at = @At("HEAD"))
					private void forInstance(CallbackInfo ci) { }
				}
				"""), "if_/MixinAppleSkin"), notes::add);

		assertEquals(Map.of("if_/MixinAppleSkin", Set.of(HANDLER)), handlers, "only the static handler of the instance methods");
		assertEquals(1, notes.size(), notes::toString);
		assertTrue(notes.get(0).contains("forceDrawBatch of if_.MixinAppleSkin (ImmediatelyFast) hooks squeek.appleskin.client.HUDOverlayHandler::drawExhaustionOverlay"), notes::toString);
		assertTrue(notes.get(0).contains("AppleSkin's Fabric build declares an instance method; Fabricated applies it as an instance handler"), notes::toString);
	}

	@Test
	void aHandlerMixinMayResolveOtherwiseOrThatTheMixinCallsIsLeftAsItIs() throws Exception {
		List<String> notes = new ArrayList<>();
		Map<String, Set<String>> handlers = NativeModMixins.instanceHandlers(installed(Map.of(
				"if_/Remapped.java", """
						package if_;
						import org.spongepowered.asm.mixin.*;
						import org.spongepowered.asm.mixin.injection.*;
						import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
						@Mixin(targets = "squeek.appleskin.client.HUDOverlayHandler")
						abstract class Remapped {
							@Inject(method = "drawExhaustionOverlay", at = @At("RETURN"))
							private static void forceDrawBatch(CallbackInfo ci) { }
						}
						""",
				"if_/CallsItself.java", """
						package if_;
						import org.spongepowered.asm.mixin.*;
						import org.spongepowered.asm.mixin.injection.*;
						import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
						@Mixin(targets = "squeek.appleskin.client.HUDOverlayHandler", remap = false)
						abstract class CallsItself {
							@Inject(method = "drawExhaustionOverlay", at = @At("RETURN"))
							private static void forceDrawBatch(CallbackInfo ci) { }
							@Inject(method = "drawExhaustionOverlay", at = @At("HEAD"))
							private static void again(CallbackInfo ci) { forceDrawBatch(ci); }
						}
						""",
				"if_/Pattern.java", """
						package if_;
						import org.spongepowered.asm.mixin.*;
						import org.spongepowered.asm.mixin.injection.*;
						import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
						@Mixin(targets = "squeek.appleskin.client.HUDOverlayHandler", remap = false)
						abstract class Pattern {
							@Inject(method = "drawExhaustion*", at = @At("RETURN"))
							private static void forceDrawBatch(CallbackInfo ci) { }
						}
						""",
				"if_/OwnClass.java", """
						package if_;
						import org.spongepowered.asm.mixin.*;
						import org.spongepowered.asm.mixin.injection.*;
						import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
						@Mixin(targets = "if_.Own", remap = false)
						abstract class OwnClass {
							@Inject(method = "run", at = @At("RETURN"))
							private static void forceDrawBatch(CallbackInfo ci) { }
						}
						""",
				"if_/Own.java", "package if_; public class Own { public void run() { } }"),
				"if_/Remapped", "if_/CallsItself", "if_/Pattern", "if_/OwnClass"), notes::add);

		// The handler calling the other one is no handler the mixin calls: it is applied as an instance handler.
		assertEquals(Map.of("if_/CallsItself", Set.of("again(L" + CALLBACK_INFO + ";)V")), handlers);
		assertEquals(3, notes.size(), "a pattern and a target of the mod's own are left to Mixin, without a note: " + notes);
		assertTrue(notes.get(0).contains("if_.Remapped") && notes.get(0).contains("left as it is, as Mixin finds its target through the mod's refmap"), notes::toString);
		assertTrue(notes.get(1).contains("forceDrawBatch of if_.CallsItself") && notes.get(1).contains("left as it is, as the mixin calls it itself"), notes::toString);
		assertTrue(notes.get(2).contains("again of if_.CallsItself") && notes.get(2).contains("Fabricated applies it as an instance handler"), notes::toString);
	}

	/**
	 * The handler made an instance method does what it did as a static one: its locals one slot further, its frames with
	 * {@code this} (the JVM verifies the class as it defines it), a lambda capturing one of them.
	 */
	@Test
	void theInstanceHandlerDoesWhatTheStaticOneDid() throws Exception {
		Map<String, byte[]> classes = TestSources.compile(dir, Map.of("h/Handlers.java", """
				package h;
				import java.util.function.IntSupplier;
				public class Handlers {
					public static String handle(String name, long count, double scale, int[] seen) {
						StringBuilder out = new StringBuilder(name);
						long total = 0;
						for (int i = 0; i < count; i++) {
							if (i % 2 == 0) total += i;
							else seen[0]++;
						}
						int captured = (int) (total * scale);
						IntSupplier supplier = () -> captured + seen[0];
						try {
							if (name.isEmpty()) throw new IllegalStateException("empty");
							out.append(':').append(supplier.getAsInt());
						} catch (IllegalStateException e) {
							out.append('!');
						}
						return out.toString();
					}
				}
				"""));
		// As the class file has its frames, and as ModLauncher reads it for its transformers (expanded).
		for (int flags : new int[] {0, ClassReader.EXPAND_FRAMES}) {
			ClassNode node = new ClassNode();
			new ClassReader(classes.get("h/Handlers")).accept(node, flags);
			String key = "handle(Ljava/lang/String;JD[I)Ljava/lang/String;";
			assertTrue(NativeModMixins.applyInstanceHandlers(node, Set.of(key)));
			assertFalse(NativeModMixins.applyInstanceHandlers(node, Set.of(key)), "an instance method is left as it is");
			MethodNode handle = node.methods.stream().filter(m -> (m.name + m.desc).equals(key)).findFirst().orElseThrow();
			assertEquals(0, handle.access & Opcodes.ACC_STATIC);
			ClassWriter writer = new ClassWriter(0);
			node.accept(writer);

			Class<?> type = new ClassLoader(getClass().getClassLoader()) {
				Class<?> define(byte[] data) {
					return defineClass("h.Handlers", data, 0, data.length);
				}
			}.define(writer.toByteArray());
			Method method = type.getMethod("handle", String.class, long.class, double.class, int[].class);
			Object instance = type.getConstructor().newInstance();
			int[] seen = {0};
			assertEquals("abc:" + ((0 + 2 + 4) * 3 + 3), method.invoke(instance, "abc", 6L, 3.0, seen));
			assertEquals(3, seen[0]);
			assertEquals("!", method.invoke(instance, "", 0L, 1.0, new int[1]));
		}
	}

	@Test
	void theConfigsOfAJarsManifestListTheirMixinsForTheSide() throws Exception {
		byte[] manifest = "Manifest-Version: 1.0\r\nMixinConfigs: a.mixins.json, b.mixins.json,missing.mixins.json\r\n\r\n".getBytes(StandardCharsets.UTF_8);
		List<String> configs = NativeModMixins.manifestConfigs(manifest);
		assertEquals(List.of("a.mixins.json", "b.mixins.json", "missing.mixins.json"), configs);
		assertEquals(List.of(), NativeModMixins.manifestConfigs("Manifest-Version: 1.0\r\n\r\n".getBytes(StandardCharsets.UTF_8)));
		Map<String, String> files = Map.of(
				"a.mixins.json", "{\"package\": \"a.mixin\", \"mixins\": [\"Common\"], \"client\": [\"hud.Client\"], \"server\": [\"Server\"]}",
				"b.mixins.json", "{\"package\": \"b\", \"mixins\": [\"Common\"");
		NativeModMixins.Files read = path -> files.containsKey(path) ? files.get(path).getBytes(StandardCharsets.UTF_8) : null;
		assertEquals(List.of("a/mixin/Common", "a/mixin/hud/Client"), NativeModMixins.listed(read, configs, true), "a config that doesn't parse lists none");
		assertEquals(List.of("a/mixin/Common", "a/mixin/Server"), NativeModMixins.listed(read, configs, false));
	}

	/** AppleSkin's Fabric build, and a jar of the other mod's classes, listing {@code mixins}. */
	private InstalledClasses installed(Map<String, String> otherMod, String... mixins) throws Exception {
		Map<String, byte[]> classes = TestSources.compile(dir, MIXIN, APPLESKIN, otherMod);
		return new InstalledClasses(List.of(
				new InstalledClasses.Jar("ImmediatelyFast", name -> name.startsWith("if_/") && classes.containsKey(name), classes::get, () -> List.of(mixins)),
				new InstalledClasses.Jar("AppleSkin", name -> name.startsWith("squeek/") && classes.containsKey(name), classes::get, List::of, true)));
	}
}
