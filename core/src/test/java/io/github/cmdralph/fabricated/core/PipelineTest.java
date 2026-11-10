package io.github.cmdralph.fabricated.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.api.FabricApiModule;
import io.github.cmdralph.fabricated.api.FabricMod;
import io.github.cmdralph.fabricated.core.accesswidener.AccessWidenerFile.InjectedInterface;
import io.github.cmdralph.fabricated.core.api.FabricatedApiImpl;
import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.archive.Hashing;
import io.github.cmdralph.fabricated.core.archive.SafeArchive;
import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.InstalledClasses;
import io.github.cmdralph.fabricated.core.cache.TranslationCache;
import io.github.cmdralph.fabricated.core.config.FabricatedConfig;
import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.metadata.FabricModJsonParser;
import io.github.cmdralph.fabricated.core.mixin.MixinFailures;
import io.github.cmdralph.fabricated.core.remap.MappingComposer;
import io.github.cmdralph.fabricated.core.remap.MappingTable;
import io.github.cmdralph.fabricated.core.report.Report;
import io.github.cmdralph.fabricated.core.resolve.Candidate;
import io.github.cmdralph.fabricated.core.resolve.DownloadedMods;
import io.github.cmdralph.fabricated.core.resolve.FabricApiPlan;
import io.github.cmdralph.fabricated.core.runtime.ModContainerImpl;
import io.github.cmdralph.fabricated.core.testutil.TestEarlyRisers;
import io.github.cmdralph.fabricated.core.testutil.TestEnumBodies;
import io.github.cmdralph.fabricated.core.testutil.TestEnums;
import io.github.cmdralph.fabricated.core.testutil.TestJars;
import io.github.cmdralph.fabricated.core.testutil.TestSources;
import io.github.cmdralph.fabricated.core.translate.DefaultBridge;
import io.github.cmdralph.fabricated.core.translate.ModsTomlWriter;
import io.github.cmdralph.fabricated.core.translate.PackageGuard;
import io.github.cmdralph.fabricated.core.translate.PlatformDescriptor;
import io.github.cmdralph.fabricated.core.translate.TranslatedMod;
import io.github.cmdralph.fabricated.core.translate.TranslationEnvironment.Namespace;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;

class PipelineTest {
	private static final String TARGET = "net/minecraft/world/Thing";

	private static final String PLAN = """
			{"fabricApiVersion": "0.161.0+26.2", "modules": {
			  "fabric-api-base": {"plan": "native", "version": "1.0.0"},
			  "fabric-renderer-indigo": {"plan": "unsupported", "reason": "replaces NeoForge's model pipeline"},
			  "fabric-item-api-v1": {"plan": "passthrough", "skipMixins": ["item.mixins.json:Conflicting"], "reason": "test"},
			  "fabric-renderer-api-v1": {"plan": "unsupported", "reason": "replaces NeoForge's model pipeline"},
			  "fabric-model-loading-api-v1": {"plan": "passthrough", "dropDependencies": ["fabric-renderer-api-v1"], "reason": "only its renderer hooks need it"},
			  "fabric-block-view-api-v2": {"plan": "passthrough"}
			}}""";

	private static byte[] targetClass() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		cw.visitField(Opcodes.ACC_PRIVATE, "count", "I", null, null).visitEnd();
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
		mv.visitCode();
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 1);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static byte[] mixinClass(String name, String targetMethod) {
		return mixinClass(name, TARGET, targetMethod);
	}

	/** A mixin as {@link #mixinClass(String, String)} with a method that refers to {@code used}. */
	private static byte[] mixinUsing(String name, String used) {
		ClassWriter cw = new ClassWriter(0);
		new ClassReader(mixinClass(name, "tick")).accept(new org.objectweb.asm.ClassVisitor(Opcodes.ASM9, cw) {
			@Override
			public void visitEnd() {
				MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "uses", "()Ljava/lang/Object;", null, null);
				mv.visitCode();
				mv.visitLdcInsn(Type.getObjectType(used));
				mv.visitInsn(Opcodes.ARETURN);
				mv.visitMaxs(1, 0);
				mv.visitEnd();
				super.visitEnd();
			}
		}, 0);
		return cw.toByteArray();
	}

	/** A mixin as {@link #mixinClass(String, String)} that merges a method of its own, {@code added}, into its target. */
	private static byte[] mixinAdding(String name, String added) {
		ClassWriter cw = new ClassWriter(0);
		new ClassReader(mixinClass(name, "tick")).accept(new org.objectweb.asm.ClassVisitor(Opcodes.ASM9, cw) {
			@Override
			public void visitEnd() {
				MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, added, "()V", null, null);
				mv.visitCode();
				mv.visitInsn(Opcodes.RETURN);
				mv.visitMaxs(0, 1);
				mv.visitEnd();
				super.visitEnd();
			}
		}, 0);
		return cw.toByteArray();
	}

	/**
	 * A mixin as {@link #mixinAdding(String, String)} adding {@code prefix + added}, a soft implementation of an interface's
	 * method ({@code @Implements(@Interface(iface = Runnable.class, prefix = ...))}), which Mixin merges as {@code added}.
	 */
	private static byte[] mixinSoftImplementing(String name, String prefix, String added) {
		ClassWriter cw = new ClassWriter(0);
		new ClassReader(mixinAdding(name, prefix + added)).accept(new org.objectweb.asm.ClassVisitor(Opcodes.ASM9, cw) {
			@Override
			public void visit(int version, int access, String className, String signature, String superName, String[] interfaces) {
				super.visit(version, access, className, signature, superName, interfaces);
				AnnotationVisitor implementsAnn = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Implements;", false);
				AnnotationVisitor value = implementsAnn.visitArray("value");
				AnnotationVisitor iface = value.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/Interface;");
				iface.visit("iface", Type.getObjectType("java/lang/Runnable"));
				iface.visit("prefix", prefix);
				iface.visitEnd();
				value.visitEnd();
				implementsAnn.visitEnd();
			}
		}, 0);
		return cw.toByteArray();
	}

	/** A mixin as {@link #mixinClass(String, String)} with a method that calls a static method of {@code owner}. */
	private static byte[] mixinCalling(String name, String owner) {
		ClassWriter cw = new ClassWriter(0);
		new ClassReader(mixinClass(name, "tick")).accept(new org.objectweb.asm.ClassVisitor(Opcodes.ASM9, cw) {
			@Override
			public void visitEnd() {
				MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "calls", "()V", null, null);
				mv.visitCode();
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "call", "()V", false);
				mv.visitInsn(Opcodes.RETURN);
				mv.visitMaxs(0, 0);
				mv.visitEnd();
				super.visitEnd();
			}
		}, 0);
		return cw.toByteArray();
	}

	/** A mixin as {@link #mixinClass(String, String)} that adds {@code iface} to its target. */
	private static byte[] mixinImplementing(String name, String iface) {
		ClassWriter cw = new ClassWriter(0);
		new ClassReader(mixinClass(name, "tick")).accept(new org.objectweb.asm.ClassVisitor(Opcodes.ASM9, cw) {
			@Override
			public void visit(int version, int access, String className, String signature, String superName, String[] interfaces) {
				super.visit(version, access, className, signature, superName, new String[] {iface});
			}
		}, 0);
		return cw.toByteArray();
	}

	/**
	 * A mixin as {@link #mixinClass(String, String, String)} that also adds an interface to its target, which the mod's
	 * code may cast to: a failure in it can't be left out (LEAVE-1), so it disables the mod.
	 */
	private static byte[] duckMixinClass(String name, String target, String targetMethod) {
		ClassWriter cw = new ClassWriter(0);
		new ClassReader(mixinClass(name, target, targetMethod)).accept(new org.objectweb.asm.ClassVisitor(Opcodes.ASM9, cw) {
			@Override
			public void visit(int version, int access, String className, String signature, String superName, String[] interfaces) {
				super.visit(version, access, className, signature, superName, new String[] {className + "$Duck"});
			}
		}, 0);
		return cw.toByteArray();
	}

	private static byte[] duckMixinClass(String name, String targetMethod) {
		return duckMixinClass(name, TARGET, targetMethod);
	}

	/** A mixin into {@code target} whose required {@code @Inject} goes at the head of {@code targetMethod}. */
	private static byte[] mixinClass(String name, String target, String targetMethod) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, name, null, "java/lang/Object", null);
		AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor arr = m.visitArray("value");
		arr.visit(null, Type.getObjectType(target));
		arr.visitEnd();
		m.visitEnd();
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "handler", "()V", null, null);
		AnnotationVisitor inject = mv.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", false);
		AnnotationVisitor methods = inject.visitArray("method");
		methods.visit(null, targetMethod);
		methods.visitEnd();
		AnnotationVisitor ats = inject.visitArray("at");
		AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
		at.visit("value", "HEAD");
		at.visitEnd();
		ats.visitEnd();
		inject.visit("require", 1);
		inject.visitEnd();
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static void writeMods(Path mods) throws Exception {
		TestJars.create()
				.fabricModJson(TestJars.modJson("good-mod", "1.0.0", """
						"name":"Good Mod","entrypoints":{"main":["com.good.Main"]},"mixins":["good.mixins.json"],
						"accessWidener":"good.accesswidener","depends":{"fabricloader":">=0.20.1","minecraft":"~26.2","fabric-api-base":"*","cloth-config":"*"}"""))
				.text("good.mixins.json", "{\"required\":true,\"package\":\"com.good.mixin\",\"mixins\":[\"ThingMixin\"]}")
				.bytes("com/good/mixin/ThingMixin.class", mixinClass("com/good/mixin/ThingMixin", "tick"))
				.bytes("com/good/Main.class", TestJars.emptyClass("com/good/Main", "java/lang/Object"))
				.text("good.accesswidener", "accessWidener v2 official\naccessible field net/minecraft/world/Thing count I\n")
				.bytes("com/google/gson/Gson.class", TestJars.emptyClass("com/google/gson/Gson", "java/lang/Object"))
				.write(mods.resolve("good.jar"));

		TestJars.create().fabricModJson(TestJars.modJson("needs-missing", "1.0", "\"depends\":{\"absent-lib\":\"*\"}"))
				.write(mods.resolve("needs-missing.jar"));

		TestJars.create().fabricModJson(TestJars.modJson("bad-mixin", "2.0", "\"mixins\":[\"bad.mixins.json\"]"))
				.text("bad.mixins.json", "{\"required\":true,\"package\":\"com.bad.mixin\",\"mixins\":[\"BadMixin\"]}")
				.bytes("com/bad/mixin/BadMixin.class", duckMixinClass("com/bad/mixin/BadMixin", "removedByNeoForge"))
				.write(mods.resolve("bad.jar"));

		byte[] inner = TestJars.create()
				.fabricModJson(TestJars.modJson("inner-lib", "3.1", "\"custom\":{\"fabric-loom:generated\":true}"))
				.bytes("com/inner/Lib.class", TestJars.emptyClass("com/inner/Lib", "java/lang/Object"))
				.toBytes();
		TestJars.create().fabricModJson(TestJars.modJson("holder", "1.0", "\"jars\":[{\"file\":\"META-INF/jars/inner.jar\"}],\"depends\":{\"inner-lib\":\">=3\"}"))
				.bytes("META-INF/jars/inner.jar", inner)
				.write(mods.resolve("holder.jar"));

		TestJars.create().fabricModJson(TestJars.modJson("dup-a", "1.0", ""))
				.bytes("com/shared/Same.class", TestJars.emptyClass("com/shared/Same", "java/lang/Object"))
				.bytes("com/conflict/Thing.class", TestJars.emptyClass("com/conflict/Thing", "java/lang/Object"))
				.write(mods.resolve("dup-a.jar"));
		TestJars.create().fabricModJson(TestJars.modJson("dup-b", "1.0", ""))
				.bytes("com/shared/Same.class", TestJars.emptyClass("com/shared/Same", "java/lang/Object"))
				.write(mods.resolve("dup-b.jar"));
		TestJars.create().fabricModJson(TestJars.modJson("res-a", "1.0", "")).text("licenses/LICENSE", "MIT").write(mods.resolve("res-a.jar"));
		TestJars.create().fabricModJson(TestJars.modJson("res-b", "1.0", "")).text("licenses/LICENSE", "Apache").write(mods.resolve("res-b.jar"));
		// Another version of dup-a's class (as two versions of a bundled library): dup-a's copy is used, as on a class path.
		TestJars.create().fabricModJson(TestJars.modJson("dup-c", "1.0", ""))
				.bytes("com/conflict/Thing.class", TestJars.emptyClass("com/conflict/Thing", "java/lang/Number"))
				.write(mods.resolve("dup-c.jar"));
		// The rest of a package dup-a has (split between jars, as Kotlin libraries ship theirs): dup-a's module serves it.
		TestJars.create().fabricModJson(TestJars.modJson("dup-d", "1.0", ""))
				.bytes("com/conflict/Other.class", TestJars.emptyClass("com/conflict/Other", "java/lang/Object"))
				.write(mods.resolve("dup-d.jar"));
		// The same, with a service provider there: the module that has the provider declares the service.
		TestJars.create().fabricModJson(TestJars.modJson("dup-e", "1.0", ""))
				.bytes("com/conflict/Provider.class", TestJars.emptyClass("com/conflict/Provider", "java/lang/Object"))
				.text("META-INF/services/java.lang.Runnable", "# a comment\ncom.conflict.Provider\n")
				.write(mods.resolve("dup-e.jar"));
		// The same service with another provider, which that module would also have to declare: refused.
		TestJars.create().fabricModJson(TestJars.modJson("dup-f", "1.0", ""))
				.bytes("com/conflict/OtherProvider.class", TestJars.emptyClass("com/conflict/OtherProvider", "java/lang/Object"))
				.text("META-INF/services/java.lang.Runnable", "com.conflict.OtherProvider\n")
				.write(mods.resolve("dup-f.jar"));
		// A copy of dup-e's provider and service (as a library's Fabric jar repeats its common jar's): hidden.
		TestJars.create().fabricModJson(TestJars.modJson("dup-g", "1.0", ""))
				.bytes("com/conflict/Provider.class", TestJars.emptyClass("com/conflict/Provider", "java/lang/Object"))
				.text("META-INF/services/java.lang.Runnable", "com.conflict.Provider\n")
				.write(mods.resolve("dup-g.jar"));

		// A service file naming a class the jar doesn't have (a shaded library minimized), next to one it has: the module
		// would be refused, and the game with it, so the file is left out.
		TestJars.create().fabricModJson(TestJars.modJson("svc-missing", "1.0", ""))
				.bytes("com/svc/Present.class", TestJars.emptyClass("com/svc/Present", "java/lang/Object"))
				.text("META-INF/services/java.lang.Runnable", "com.svc.Present\ncom.svc.Dropped\n")
				.text("META-INF/services/java.lang.AutoCloseable", "com.svc.Present\n")
				.write(mods.resolve("svc-missing.jar"));

		TestJars.create().fabricModJson(TestJars.modJson("fabric-api-base", "0.4.0", "")).write(mods.resolve("fapi-base.jar"));
		TestJars.create().fabricModJson(TestJars.modJson("client-only", "1.0", "\"environment\":\"client\"")).write(mods.resolve("client-only.jar"));
		Files.writeString(mods.resolve("broken.jar"), "not a zip");
	}

	private static Pipeline.Input input(Path game, List<Path> jars, Namespace ns, Map<String, byte[]> gameClasses, Pipeline.MappingSupplier mappings) throws Exception {
		return input(game, jars, ns, gameClasses, mappings, List.of(new Pipeline.NeoForgeMod("cloth_config", "15.0.0", "Cloth Config", Map.of(), List.of())));
	}

	private static Pipeline.Input input(Path game, List<Path> jars, Namespace ns, Map<String, byte[]> gameClasses, Pipeline.MappingSupplier mappings,
			List<Pipeline.NeoForgeMod> neoForgeMods) throws Exception {
		return input(game, jars, ns, gameClasses, mappings, neoForgeMods, InstalledClasses.NONE);
	}

	private static Pipeline.Input input(Path game, List<Path> jars, Namespace ns, Map<String, byte[]> gameClasses, Pipeline.MappingSupplier mappings,
			List<Pipeline.NeoForgeMod> neoForgeMods, InstalledClasses installed) throws Exception {
		return input(game, jars, ns, gameClasses, mappings, neoForgeMods, installed, ClassBytesSource.EMPTY);
	}

	/** @param libraries the classes NeoForge loads outside its transforming class loader (the JDK's and libraries') */
	private static Pipeline.Input input(Path game, List<Path> jars, Namespace ns, Map<String, byte[]> gameClasses, Pipeline.MappingSupplier mappings,
			List<Pipeline.NeoForgeMod> neoForgeMods, InstalledClasses installed, ClassBytesSource libraries) throws Exception {
		return input(game, jars, ns, gameClasses, mappings, neoForgeMods, installed, libraries, "0.17.4+mixin.0.8.7", "0.17.1");
	}

	/** @param mixin the Mixin NeoForge runs, and {@code lowestBehavior} the lowest behaviour version its FML accepts */
	private static Pipeline.Input input(Path game, List<Path> jars, Namespace ns, Map<String, byte[]> gameClasses, Pipeline.MappingSupplier mappings,
			List<Pipeline.NeoForgeMod> neoForgeMods, InstalledClasses installed, ClassBytesSource libraries, String mixin, String lowestBehavior)
			throws Exception {
		FabricatedConfig config = FabricatedConfig.load(game.resolve("config"));
		TranslationCache cache = new TranslationCache(game, config.cacheEnabled(), config.verifyIntegrity());
		return new Pipeline.Input(game, game.resolve("config"), config, cache, jars, ns == Namespace.OFFICIAL ? "26.2" : "1.21.11",
				"26.2.0.90", false, ns, mixin, "0.5.3", lowestBehavior, "0.1.0-test", "test-build", false, gameClasses::get, List.of(game.resolve("minecraft.jar")),
				Pipeline.VanillaGame.NONE, List.of(game.resolve("fabricated.jar")), libraries, Set.of("net/neoforged/neoforge"),
				FabricApiPlan.parse(PLAN), name -> null, Map::of, neoForgeMods, installed, mappings, new String[0], PlatformDescriptor.NEOFORGE);
	}

	@Test
	void anotherPlatformsDescriptorDecidesHowItsLoaderLearnsOfTheMod(@TempDir Path game) throws Exception {
		// docs/PORTING.md §6 item 5: Forge reads META-INF/mods.toml, takes mixin configs from the manifest's MixinConfigs,
		// and has no enum extension files; a branch hands the pipeline its descriptor.
		Path mods = Files.createDirectories(game.resolve("mods"));
		writeMods(mods);
		List<Path> jars;
		try (var s = Files.list(mods)) {
			jars = s.sorted().toList();
		}
		Pipeline.Result result = Pipeline.run(withDescriptor(input(game, jars, Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null), PlatformDescriptor.FORGE));
		Pipeline.LoadedMod good = result.mods().stream().filter(m -> m.translated().candidate().id().equals("good-mod")).findFirst().orElseThrow();
		try (SafeArchive overlay = SafeArchive.open(good.translated().overlay(), ArchiveLimits.DEFAULTS)) {
			String toml = overlay.readText(ModsTomlWriter.FORGE_PATH);
			assertTrue(toml != null && toml.contains("modId=\"good_mod\"") && toml.contains("mandatory=false") && !toml.contains("[[mixins]]"), toml);
			assertNull(overlay.readText(ModsTomlWriter.PATH), "no NeoForge descriptor");
			String manifest = overlay.readText("META-INF/MANIFEST.MF");
			assertTrue(manifest.contains("MixinConfigs: good.mixins.json"), manifest);
			assertTrue(manifest.contains("Fabricated-Translated-Mod: good_mod"), manifest);
			Map<?, ?> pack = (Map<?, ?>) io.github.cmdralph.fabricated.core.json.Json.parseObject(overlay.readText("pack.mcmeta")).get("pack");
			assertEquals("Good Mod", pack.get("description"), "Forge reads no mod pack without metadata");
			assertEquals(82, ((Number) pack.get("min_format")).intValue());
			assertEquals(2048, ((Number) pack.get("max_format")).intValue());
		}
		// The report names the platform's loader.
		List<String> environment = result.report().render().lines().map(String::strip).toList();
		assertTrue(environment.stream().anyMatch(l -> l.startsWith("Forge:")) && environment.stream().noneMatch(l -> l.startsWith("NeoForge:")),
				result.report().render());
	}

	/**
	 * Forge Config API Port is replaced only where the platform's in-game mod provides its API (NeoForge's); on Forge its
	 * jar loads as other mods do (REPL-1). Mod Menu's stays out on both.
	 */
	@Test
	void modsTheInGameModProvidesAreReplacedOnlyOnItsPlatform(@TempDir Path game) throws Exception {
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path port = TestJars.create().fabricModJson(TestJars.modJson("forgeconfigapiport", "26.1.5", "\"name\":\"Forge Config API Port\""))
				.write(mods.resolve("port.jar"));
		Path menu = TestJars.create().fabricModJson(TestJars.modJson("modmenu", "20.0.3", "\"name\":\"Mod Menu\"")).write(mods.resolve("menu.jar"));

		Pipeline.Result neoForge = Pipeline.run(input(game, List.of(port, menu), Namespace.OFFICIAL, Map.of(), null, List.of()));
		for (String id : List.of("forgeconfigapiport", "modmenu")) {
			assertTrue(neoForge.report().mods().stream().anyMatch(r -> r.id().equals(id) && r.status() == Report.Status.SKIPPED), id);
			assertTrue(neoForge.mods().stream().noneMatch(m -> m.translated().candidate().id().equals(id)), id);
		}
		// On Forge the port loads; Mod Menu stays out, Forge listing mods itself.
		Pipeline.Result forge = Pipeline.run(withDescriptor(input(game, List.of(port, menu), Namespace.OFFICIAL, Map.of(), null, List.of()),
				PlatformDescriptor.FORGE));
		assertTrue(forge.mods().stream().anyMatch(m -> m.translated().candidate().id().equals("forgeconfigapiport")), forge.report().render());
		assertTrue(forge.report().mods().stream().anyMatch(r -> r.id().equals("modmenu") && r.status() == Report.Status.SKIPPED), forge.report().render());
		assertTrue(forge.mods().stream().noneMatch(m -> m.translated().candidate().id().equals("modmenu")), forge.report().render());
	}

	/**
	 * PLAT-5: NeoForge's namespace is NeoForge's, whose copies a Fabric mod brings are left out for its classes; Forge has
	 * none of it, so there Forge Config API Port's copies of NeoForge's config classes stay the mod's.
	 */
	@Test
	void neoForgesPackagesAreThePlatformsOnlyOnNeoForge(@TempDir Path game) throws Exception {
		Path mods = Files.createDirectories(game.resolve("mods"));
		String copy = "net/neoforged/neoforge/common/ModConfigSpec";
		Path port = TestJars.create().fabricModJson(TestJars.modJson("config-port", "1.0", ""))
				.bytes(copy + ".class", TestJars.emptyClass(copy, "java/lang/Object"))
				.write(mods.resolve("port.jar"));
		String note = "bundles package net.neoforged.neoforge.common, which the platform already provides";

		Pipeline.Result neoForge = Pipeline.run(input(game, List.of(port), Namespace.OFFICIAL, Map.of(), null, List.of()));
		assertTrue(neoForge.report().render().contains(note), neoForge.report().render());
		Pipeline.Result forge = Pipeline.run(withDescriptor(input(game, List.of(port), Namespace.OFFICIAL, Map.of(), null, List.of()),
				PlatformDescriptor.FORGE));
		assertTrue(forge.mods().stream().anyMatch(m -> m.translated().candidate().id().equals("config-port")), forge.report().render());
		assertFalse(forge.report().render().contains(note), forge.report().render());
	}

	/**
	 * A mod nesting a module of LWJGL the game doesn't ship (NanoVG) keeps it, while its copy of the game's LWJGL core is
	 * left out: the game's LWJGL is on no module layer on FML 10 and later, so the platform's loaders decide (PLAT-2).
	 */
	@Test
	void aLibrarysModuleThePlatformLacksStaysInTheMod(@TempDir Path game) throws Exception {
		Path mods = Files.createDirectories(game.resolve("mods"));
		String core = "org/lwjgl/system/MemoryUtil";
		String nanovg = "org/lwjgl/nanovg/NanoVG";
		Path ui = TestJars.create().fabricModJson(TestJars.modJson("vector-ui", "1.0", ""))
				.bytes(core + ".class", TestJars.emptyClass(core, "java/lang/Object"))
				.bytes(nanovg + ".class", TestJars.emptyClass(nanovg, "java/lang/Object"))
				.write(mods.resolve("vector-ui.jar"));
		byte[] platformCore = TestJars.emptyClass(core, "java/lang/Object");
		ClassBytesSource libraries = name -> name.equals(core) ? platformCore : null;

		Pipeline.Result result = Pipeline.run(input(game, List.of(ui), Namespace.OFFICIAL, Map.of(), null, List.of(), InstalledClasses.NONE, libraries));
		Pipeline.LoadedMod mod = result.mods().stream().filter(m -> m.translated().candidate().id().equals("vector-ui")).findFirst()
				.orElseThrow(() -> new AssertionError(result.report().render()));
		assertEquals(Set.of("org/lwjgl/system"), mod.packages().excludedPackages(), result.report().render());
		assertFalse(mod.packages().hides(nanovg + ".class"));
		assertTrue(mod.packages().hides(core + ".class"));
	}

	/** On another platform, the report's reasons and suggestions name its loader where they named NeoForge (PLAT-3). */
	@Test
	void reasonsAndSuggestionsNameThePlatformsLoader(@TempDir Path game) throws Exception {
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path wthit = TestJars.create().fabricModJson(TestJars.modJson("wthit", "12.0.0", "\"name\":\"WTHIT\"")).write(mods.resolve("wthit.jar"));
		Path needy = TestJars.create().fabricModJson(TestJars.modJson("needy", "1.0.0", "\"depends\":{\"absent-lib\":\"*\"}"))
				.write(mods.resolve("needy.jar"));

		Pipeline.Result result = Pipeline.run(withDescriptor(input(game, List.of(wthit, needy), Namespace.OFFICIAL, Map.of(), null, List.of()),
				PlatformDescriptor.FORGE));

		Report.ModRow row = result.report().mods().stream().filter(r -> r.id().equals("wthit")).findFirst().orElseThrow();
		assertTrue(row.detail().startsWith("its Fabric build isn't supported on Forge: "), row.detail());
		assertEquals("Install WTHIT for Forge instead (from the same download page).", row.suggestion());
		Report.ModRow missing = result.report().mods().stream().filter(r -> r.id().equals("needy")).findFirst().orElseThrow();
		assertEquals("Install absent-lib (a Fabric or Forge build) or remove needy.", missing.suggestion());
		// The compatibility plan's own reasons are NeoForge's data; nothing else names NeoForge.
		assertFalse(result.report().render().replace(row.detail(), "").contains("NeoForge"), result.report().render());
	}

	/** {@code in} with another platform's descriptor. */
	private static Pipeline.Input withDescriptor(Pipeline.Input in, PlatformDescriptor descriptor) throws Exception {
		java.lang.reflect.RecordComponent[] parts = Pipeline.Input.class.getRecordComponents();
		Class<?>[] types = new Class<?>[parts.length];
		Object[] values = new Object[parts.length];
		for (int i = 0; i < parts.length; i++) {
			types[i] = parts[i].getType();
			values[i] = types[i] == PlatformDescriptor.class ? descriptor : parts[i].getAccessor().invoke(in);
		}
		return Pipeline.Input.class.getDeclaredConstructor(types).newInstance(values);
	}

	@Test
	void runsTheWholePipelineAndIsolatesBrokenMods(@TempDir Path game) throws Exception {
		Path mods = Files.createDirectories(game.resolve("mods"));
		writeMods(mods);
		List<Path> jars;
		try (var s = Files.list(mods)) {
			jars = s.sorted().toList();
		}
		Map<String, byte[]> gameClasses = Map.of(TARGET, targetClass());

		Pipeline.Input first = input(game, jars, Namespace.OFFICIAL, gameClasses, null);
		Pipeline.Result result = Pipeline.run(first);
		Map<String, Report.Status> status = new HashMap<>();
		for (Report.ModRow row : result.report().mods()) status.putIfAbsent(row.id(), row.status());

		Set<String> loaded = result.mods().stream().map(m -> m.translated().candidate().id()).collect(Collectors.toSet());
		assertEquals(Set.of("good-mod", "holder", "inner-lib", "dup-a", "dup-b", "dup-c", "dup-d", "dup-e", "dup-g", "res-a", "res-b", "svc-missing"), loaded,
				result.report().render());
		Pipeline.LoadedMod svc = result.mods().stream().filter(m -> m.translated().candidate().id().equals("svc-missing")).findFirst().orElseThrow();
		assertTrue(svc.packages().hides("META-INF/services/java.lang.Runnable"), "a provider the module lacks: the service file is left out");
		assertFalse(svc.packages().hides("META-INF/services/java.lang.AutoCloseable"), "one whose provider it has stays");
		assertTrue(result.report().render().contains("with com.svc.Dropped, which its module doesn't have"), result.report().render());
		Pipeline.LoadedMod resB = result.mods().stream().filter(m -> m.translated().candidate().id().equals("res-b")).findFirst().orElseThrow();
		assertEquals(Set.of("licenses"), resB.packages().excludedPackages(), "clashing resource-only directory is hidden, not fatal");
		assertEquals(Report.Status.DISABLED, status.get("needs-missing"));
		assertTrue(result.dependencyFailures().contains("needs-missing"), result.dependencyFailures().toString());
		assertEquals(Report.Status.DISABLED, status.get("bad-mixin"));
		assertTrue(result.report().render().contains("dup-c WARNING: has a different com.conflict.Thing than dup_a; dup_a's copies are used"),
				result.report().render());
		assertEquals(Report.Status.REJECTED, status.get("broken.jar"));
		assertEquals(Report.Status.SKIPPED, status.get("client-only"));
		assertTrue(result.report().mods().stream().anyMatch(r -> r.id().equals("fabric-api-base") && r.status() == Report.Status.PROVIDED_NATIVELY));
		assertTrue(result.report().mods().stream().anyMatch(r -> r.id().equals("fabric-api-base") && r.status() == Report.Status.SKIPPED),
				"the bundled upstream module jar is skipped in favour of the native one");

		Pipeline.LoadedMod good = result.mods().stream().filter(m -> m.translated().candidate().id().equals("good-mod")).findFirst().orElseThrow();
		assertEquals(Set.of("com/google/gson"), good.packages().excludedPackages(), "bundled platform library is filtered");
		Pipeline.LoadedMod dupB = result.mods().stream().filter(m -> m.translated().candidate().id().equals("dup-b")).findFirst().orElseThrow();
		assertEquals(Set.of("com/shared"), dupB.packages().excludedPackages(), "identical shared package uses the first copy");
		Pipeline.LoadedMod dupA = result.mods().stream().filter(m -> m.translated().candidate().id().equals("dup-a")).findFirst().orElseThrow();
		Pipeline.LoadedMod dupD = result.mods().stream().filter(m -> m.translated().candidate().id().equals("dup-d")).findFirst().orElseThrow();
		assertEquals(Set.of("com/conflict"), dupD.packages().excludedPackages());
		Pipeline.LoadedMod dupE = result.mods().stream().filter(m -> m.translated().candidate().id().equals("dup-e")).findFirst().orElseThrow();
		Pipeline.LoadedMod dupG = result.mods().stream().filter(m -> m.translated().candidate().id().equals("dup-g")).findFirst().orElseThrow();
		String service = "META-INF/services/java.lang.Runnable";
		assertEquals(Map.of(dupD.translated().jar(), Set.of("com/conflict/Other.class"), dupE.translated().jar(), Set.of("com/conflict/Provider.class", service)),
				dupA.packages().borrowed(), "a split package is served by one module, with the service its provider is for");
		assertTrue(dupE.packages().hides(service) && dupG.packages().hides(service), "the service is the providing module's");
		assertEquals(Report.Status.DISABLED, status.get("dup-f"));
		assertTrue(result.report().mods().stream().anyMatch(r -> r.id().equals("dup-f") && r.detail().contains("with other providers than dup_a")),
				result.report().render());

		try (SafeArchive overlay = SafeArchive.open(good.translated().overlay(), ArchiveLimits.DEFAULTS)) {
			String toml = overlay.readText("META-INF/neoforge.mods.toml");
			assertTrue(toml.contains("modId=\"good_mod\""), toml);
			assertTrue(toml.contains("config=\"good.mixins.json\""));
			assertTrue(toml.contains("behaviorVersion=\"0.17.4\""), "Fabric Loader 0.20's 0.17.5, lowered to the running Mixin: " + toml);
			assertTrue(toml.contains("modId=\"cloth_config\""), "loads after the NeoForge mod it depends on");
			String at = overlay.readText("META-INF/accesstransformer.cfg");
			assertTrue(at.contains("public net.minecraft.world.Thing count"), at);
		}
		assertEquals(good.translated().candidate().path(), good.translated().jar(), "26.x mods are not copied");

		assertTrue(result.runtime().isModLoaded("good-mod"));
		assertEquals("good-mod", result.runtime().getModContainer("good_mod").orElseThrow().getMetadata().getId(),
				"a translated mod answers to its platform id too, which data packs' mod_loaded conditions name (CORPUS-109)");
		assertTrue(result.runtime().isModLoaded("cloth_config"));
		assertTrue(result.runtime().isModLoaded("fabric-api-base"));
		assertFalse(result.runtime().isModLoaded("bad-mixin"));
		assertEquals(1, result.runtime().declared("main").size());
		assertTrue(result.runtime().getModContainer("inner-lib").orElseThrow().getContainingMod().isPresent());

		String report = result.report().render();
		assertTrue(report.contains("removedByNeoForge"), report);
		assertTrue(report.contains("absent-lib"));
		result.report().write(game.resolve("logs"));
		assertTrue(Files.exists(game.resolve("logs").resolve(Report.FILE_NAME)));
		first.cache().finish(5);

		// Second launch: everything comes from the cache and outputs are byte-identical.
		byte[] firstOverlay = Files.readAllBytes(good.translated().overlay());
		Pipeline.Result second = Pipeline.run(input(game, jars, Namespace.OFFICIAL, gameClasses, null));
		assertTrue(second.mods().stream().allMatch(m -> m.translated().cacheHit()));
		Pipeline.LoadedMod goodAgain = second.mods().stream().filter(m -> m.translated().candidate().id().equals("good-mod")).findFirst().orElseThrow();
		assertArrayEquals(firstOverlay, Files.readAllBytes(goodAgain.translated().overlay()));
	}

	/** A class of an installed NeoForge mod with one method, {@code method()V}. */
	private static byte[] installedClass(String name, String method) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, method, "()V", null, null);
		mv.visitCode();
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 1);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * A Fabric addon's mixin into a class of the NeoForge build of the mod it extends is checked against the installed
	 * build; its cached translation holds while that class is the same, and is checked again when the mod changes.
	 */
	@Test
	void mixinsIntoAnInstalledModAreCheckedAgainWhenItChanges(@TempDir Path game) throws Exception {
		String renderer = "net/example/sodium/Renderer";
		Path addon = TestJars.create().fabricModJson(TestJars.modJson("addon", "1.0", "\"mixins\":[\"addon.mixins.json\"]"))
				.text("addon.mixins.json", "{\"required\":true,\"package\":\"com.addon.mixin\",\"mixins\":[\"RendererMixin\"]}")
				.bytes("com/addon/mixin/RendererMixin.class", duckMixinClass("com/addon/mixin/RendererMixin", renderer, "drawSections"))
				.write(Files.createDirectories(game.resolve("mods")).resolve("addon.jar"));
		Map<String, byte[]> sodium = new HashMap<>(Map.of(renderer, installedClass(renderer, "drawSections")));
		Supplier<InstalledClasses> installed = () -> new InstalledClasses(List.of(new InstalledClasses.Jar("Sodium", sodium::containsKey, sodium::get)));

		Pipeline.Input firstLaunch = input(game, List.of(addon), Namespace.OFFICIAL, Map.of(), null, List.of(), installed.get());
		Pipeline.Result first = Pipeline.run(firstLaunch);
		assertEquals(List.of("addon"), first.mods().stream().map(m -> m.translated().candidate().id()).toList(), first.report().render());
		assertFalse(first.mods().getFirst().translated().cacheHit());
		firstLaunch.cache().finish(5);
		Pipeline.Input sameLaunch = input(game, List.of(addon), Namespace.OFFICIAL, Map.of(), null, List.of(), installed.get());
		assertTrue(Pipeline.run(sameLaunch).mods().getFirst().translated().cacheHit());
		sameLaunch.cache().finish(5);

		sodium.put(renderer, installedClass(renderer, "renderSections"));
		Pipeline.Result changed = Pipeline.run(input(game, List.of(addon), Namespace.OFFICIAL, Map.of(), null, List.of(), installed.get()));
		assertTrue(changed.mods().isEmpty(), changed.report().render());
		Report.ModRow row = changed.report().mods().stream().filter(r -> r.id().equals("addon")).findFirst().orElseThrow();
		assertEquals(Report.Status.DISABLED, row.status());
		assertTrue(row.detail().contains("drawSections, which does not exist in net.example.sodium.Renderer"), row.detail());
	}

	/**
	 * Code calling a class only an installed NeoForge mod has (Forgified Fabric API's modules, a multi-loader library's
	 * NeoForge build) links: the linkage check counts the installed mods' classes (PROVIDER-1).
	 */
	@Test
	void codeUsingAnInstalledModsClassLinks(@TempDir Path game) throws Exception {
		String helper = "net/fabricmc/fabric/api/client/keybinding/v1/KeyBindingHelper";
		String draw = "register(Ljava/lang/Object;)V";
		Path user = TestJars.create().fabricModJson(TestJars.modJson("user", "1.0", ""))
				.bytes("com/user/Keys.class", caller("com/user/Keys", helper, draw))
				.write(Files.createDirectories(game.resolve("mods")).resolve("user.jar"));
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, helper, null, "java/lang/Object", null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "register", "(Ljava/lang/Object;)V", null, null);
		mv.visitCode();
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 2);
		mv.visitEnd();
		cw.visitEnd();
		Map<String, byte[]> provider = Map.of(helper, cw.toByteArray());
		InstalledClasses installed = new InstalledClasses(List.of(new InstalledClasses.Jar("Forgified Fabric Key Binding API", provider::containsKey, provider::get)));

		Pipeline.Result result = Pipeline.run(input(game, List.of(user), Namespace.OFFICIAL, Map.of(), null, List.of(), installed));
		assertEquals(List.of("user"), result.mods().stream().map(m -> m.translated().candidate().id()).toList(), result.report().render());
		assertFalse(result.report().render().contains("missing class"), result.report().render());

		Path without = Files.createDirectories(game.resolve("other"));
		Pipeline.Result missing = Pipeline.run(input(without, List.of(Files.copy(user, Files.createDirectories(without.resolve("mods")).resolve("user.jar"))),
				Namespace.OFFICIAL, Map.of(), null));
		assertTrue(missing.report().render().contains("com.user.Keys -> missing class net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper"),
				missing.report().render());
	}

	/** A mixin into a mod that isn't installed isn't verified, and is checked once that mod is installed. */
	@Test
	void mixinsIntoAModInstalledLaterAreCheckedThen(@TempDir Path game) throws Exception {
		String renderer = "net/example/sodium/Renderer";
		Path addon = TestJars.create().fabricModJson(TestJars.modJson("addon", "1.0", "\"mixins\":[\"addon.mixins.json\"]"))
				.text("addon.mixins.json", "{\"required\":true,\"package\":\"com.addon.mixin\",\"mixins\":[\"RendererMixin\"]}")
				.bytes("com/addon/mixin/RendererMixin.class", duckMixinClass("com/addon/mixin/RendererMixin", renderer, "drawSections"))
				.write(Files.createDirectories(game.resolve("mods")).resolve("addon.jar"));
		Map<String, byte[]> sodium = new HashMap<>();
		Supplier<InstalledClasses> installed = () -> new InstalledClasses(List.of(new InstalledClasses.Jar("Sodium", sodium::containsKey, sodium::get)));

		Pipeline.Input without = input(game, List.of(addon), Namespace.OFFICIAL, Map.of(), null, List.of(), installed.get());
		Pipeline.Result first = Pipeline.run(without);
		assertEquals(List.of("addon"), first.mods().stream().map(m -> m.translated().candidate().id()).toList(), first.report().render());
		without.cache().finish(5);

		sodium.put(renderer, installedClass(renderer, "renderSections"));
		Pipeline.Result installedNow = Pipeline.run(input(game, List.of(addon), Namespace.OFFICIAL, Map.of(), null, List.of(), installed.get()));
		assertTrue(installedNow.mods().isEmpty(), installedNow.report().render());
		Report.ModRow row = installedNow.report().mods().stream().filter(r -> r.id().equals("addon")).findFirst().orElseThrow();
		assertTrue(row.detail().contains("drawSections, which does not exist in net.example.sodium.Renderer"), row.detail());
	}

	/**
	 * A mixin may inject into a method another Fabric mod's mixin adds to the target, as owo's creative screen mixin
	 * does into the one Fabric API's adds: Mixin merges every mixin's methods before it injects. One into a method no
	 * mixin adds still disables its mod.
	 */
	@Test
	void anInjectorIntoAMethodAnotherModsMixinAddsIsNotAFailure(@TempDir Path game) throws Exception {
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path api = TestJars.create().fabricModJson(TestJars.modJson("api", "1.0", "\"name\":\"Api\",\"mixins\":[\"api.mixins.json\"]"))
				.text("api.mixins.json", "{\"required\":true,\"package\":\"com.api.mixin\",\"mixins\":[\"ThingMixin\"]}")
				.bytes("com/api/mixin/ThingMixin.class", mixinAdding("com/api/mixin/ThingMixin", "updateSelection"))
				.write(mods.resolve("api.jar"));
		Path user = TestJars.create().fabricModJson(TestJars.modJson("user", "1.0", "\"mixins\":[\"user.mixins.json\"],\"depends\":{\"api\":\"*\"}"))
				.text("user.mixins.json", "{\"required\":true,\"package\":\"com.user.mixin\",\"mixins\":[\"ThingMixin\"]}")
				.bytes("com/user/mixin/ThingMixin.class", duckMixinClass("com/user/mixin/ThingMixin", "updateSelection"))
				.write(mods.resolve("user.jar"));
		Path stale = TestJars.create().fabricModJson(TestJars.modJson("stale", "1.0", "\"mixins\":[\"stale.mixins.json\"],\"depends\":{\"api\":\"*\"}"))
				.text("stale.mixins.json", "{\"required\":true,\"package\":\"com.stale.mixin\",\"mixins\":[\"ThingMixin\"]}")
				.bytes("com/stale/mixin/ThingMixin.class", duckMixinClass("com/stale/mixin/ThingMixin", "updateSelections"))
				.write(mods.resolve("stale.jar"));

		Pipeline.Result result = Pipeline.run(input(game, List.of(api, user, stale), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));

		Set<String> loaded = result.mods().stream().map(m -> m.translated().candidate().id()).collect(Collectors.toSet());
		assertEquals(Set.of("api", "user"), loaded, result.report().render());
		assertTrue(result.report().render().contains("updateSelection, which is not in net.minecraft.world.Thing; Api's mixin com.api.mixin.ThingMixin adds it"),
				result.report().render());
		Report.ModRow row = result.report().mods().stream().filter(r -> r.id().equals("stale")).findFirst().orElseThrow();
		assertTrue(row.detail().contains("updateSelections, which does not exist in net.minecraft.world.Thing"), row.detail());
	}

	/**
	 * A mixin may inject into a method its mod's other mixin adds as a soft implementation, which Mixin merges without its
	 * prefix (ShulkerBoxTooltip's ender chest mixins on 26.x).
	 */
	@Test
	void anInjectorIntoASoftImplementationAnotherMixinAddsIsNotAFailure(@TempDir Path game) throws Exception {
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path softie = TestJars.create().fabricModJson(TestJars.modJson("softie", "1.0", "\"name\":\"Softie\",\"mixins\":[\"softie.mixins.json\"]"))
				.text("softie.mixins.json", "{\"required\":true,\"package\":\"com.softie.mixin\",\"mixins\":[\"ThingMixin\",\"ThingHookMixin\"]}")
				.bytes("com/softie/mixin/ThingMixin.class", mixinSoftImplementing("com/softie/mixin/ThingMixin", "container$", "setChanged"))
				.bytes("com/softie/mixin/ThingHookMixin.class", mixinClass("com/softie/mixin/ThingHookMixin", TARGET, "setChanged"))
				.write(mods.resolve("softie.jar"));

		Pipeline.Result result = Pipeline.run(input(game, List.of(softie), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));

		Set<String> loaded = result.mods().stream().map(m -> m.translated().candidate().id()).collect(Collectors.toSet());
		assertEquals(Set.of("softie"), loaded, result.report().render());
		assertTrue(result.report().render().contains("setChanged, which is not in net.minecraft.world.Thing; Softie's mixin com.softie.mixin.ThingMixin adds it"),
				result.report().render());
	}

	/** On 26.x an addon's mixins into another Fabric mod's classes are checked against that mod's jar. */
	@Test
	void mixinsIntoAnotherFabricModAreChecked(@TempDir Path game) throws Exception {
		Path mods = Files.createDirectories(game.resolve("mods"));
		String renderer = "com/base/Renderer";
		Path base = TestJars.create().fabricModJson(TestJars.modJson("base", "2.0", "\"name\":\"Base\""))
				.bytes(renderer + ".class", installedClass(renderer, "renderSections"))
				.write(mods.resolve("base.jar"));
		Path good = TestJars.create().fabricModJson(TestJars.modJson("good-addon", "1.0", "\"mixins\":[\"good.mixins.json\"],\"depends\":{\"base\":\"*\"}"))
				.text("good.mixins.json", "{\"required\":true,\"package\":\"com.good.mixin\",\"mixins\":[\"RendererMixin\"]}")
				.bytes("com/good/mixin/RendererMixin.class", mixinClass("com/good/mixin/RendererMixin", renderer, "renderSections"))
				.write(mods.resolve("good-addon.jar"));
		Path stale = TestJars.create().fabricModJson(TestJars.modJson("stale-addon", "1.0", "\"mixins\":[\"stale.mixins.json\"],\"depends\":{\"base\":\"*\"}"))
				.text("stale.mixins.json", "{\"required\":true,\"package\":\"com.stale.mixin\",\"mixins\":[\"RendererMixin\"]}")
				.bytes("com/stale/mixin/RendererMixin.class", duckMixinClass("com/stale/mixin/RendererMixin", renderer, "drawSections"))
				.write(mods.resolve("stale-addon.jar"));

		Pipeline.Result result = Pipeline.run(input(game, List.of(base, good, stale), Namespace.OFFICIAL, Map.of(), null, List.of()));

		Set<String> loaded = result.mods().stream().map(m -> m.translated().candidate().id()).collect(Collectors.toSet());
		assertEquals(Set.of("base", "good-addon"), loaded, result.report().render());
		Report.ModRow row = result.report().mods().stream().filter(r -> r.id().equals("stale-addon")).findFirst().orElseThrow();
		assertTrue(row.detail().contains("drawSections, which does not exist in com.base.Renderer"), row.detail());
	}

	/**
	 * On 1.21.11 too, against the other mod's classes remapped as they will load: the good addon's selector names
	 * Minecraft's class by its intermediary name, which only matches the remapped target.
	 */
	@Test
	void mixinsIntoAnotherFabricModAreCheckedOnObfuscatedVersions(@TempDir Path game) throws Exception {
		String tiny = "v1\tofficial\tintermediary\nCLASS\ta\tnet/minecraft/class_1\n";
		String proguard = "net.minecraft.world.Thing -> a:\n";
		MappingTable table = MappingComposer.compose(new StringReader(tiny), new StringReader(proguard));
		Path mods = Files.createDirectories(game.resolve("mods"));
		String renderer = "com/base/Renderer";
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, renderer, null, "java/lang/Object", null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "render", "(Lnet/minecraft/class_1;)V", null, null);
		mv.visitCode();
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 2);
		mv.visitEnd();
		cw.visitEnd();
		Path base = TestJars.create().fabricModJson(TestJars.modJson("base", "2.0", "\"name\":\"Base\""))
				.bytes(renderer + ".class", cw.toByteArray())
				.write(mods.resolve("base.jar"));
		Path good = TestJars.create().fabricModJson(TestJars.modJson("good-addon", "1.0", "\"mixins\":[\"good.mixins.json\"],\"depends\":{\"base\":\"*\"}"))
				.text("good.mixins.json", "{\"required\":true,\"package\":\"com.good.mixin\",\"refmap\":\"good-refmap.json\",\"mixins\":[\"RendererMixin\"]}")
				.text("good-refmap.json", "{\"mappings\":{\"com/good/mixin/RendererMixin\":{\"render\":\"Lcom/base/Renderer;render(Lnet/minecraft/class_1;)V\"}}}")
				.bytes("com/good/mixin/RendererMixin.class", mixinClass("com/good/mixin/RendererMixin", renderer, "render"))
				.write(mods.resolve("good-addon.jar"));
		Path stale = TestJars.create().fabricModJson(TestJars.modJson("stale-addon", "1.0", "\"mixins\":[\"stale.mixins.json\"],\"depends\":{\"base\":\"*\"}"))
				.text("stale.mixins.json", "{\"required\":true,\"package\":\"com.stale.mixin\",\"mixins\":[\"RendererMixin\"]}")
				.bytes("com/stale/mixin/RendererMixin.class", duckMixinClass("com/stale/mixin/RendererMixin", renderer, "drawSections"))
				.write(mods.resolve("stale-addon.jar"));

		Pipeline.Result result = Pipeline.run(input(game, List.of(base, good, stale), Namespace.INTERMEDIARY, Map.of(TARGET, targetClass()),
				(cache, path) -> new Pipeline.Mappings(() -> table, "test-hash"), List.of()));

		Set<String> loaded = result.mods().stream().map(m -> m.translated().candidate().id()).collect(Collectors.toSet());
		assertEquals(Set.of("base", "good-addon"), loaded, result.report().render());
		Report.ModRow row = result.report().mods().stream().filter(r -> r.id().equals("stale-addon")).findFirst().orElseThrow();
		assertTrue(row.detail().contains("drawSections, which does not exist in com.base.Renderer"), row.detail());
	}

	/** REMAP-2: a mixin config the mod adds to Mixin itself, unlisted in its metadata (ReplayMod's), has its refmap remapped. */
	@Test
	void refmapsOfUnlistedMixinConfigsAreRemapped(@TempDir Path game) throws Exception {
		String tiny = "v1\tofficial\tintermediary\nCLASS\ta\tnet/minecraft/class_1\n";
		String proguard = "net.minecraft.world.Thing -> a:\n";
		MappingTable table = MappingComposer.compose(new StringReader(tiny), new StringReader(proguard));
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path replay = TestJars.create().fabricModJson(TestJars.modJson("replay", "1.0", ""))
				.text("mixins.replay.json", "{\"package\":\"com.replay.mixin\",\"refmap\":\"mixins.replay.refmap.json\",\"mixins\":[]}")
				.text("mixins.replay.refmap.json", "{\"mappings\":{\"com/replay/mixin/ThingMixin\":{\"tick\":\"Lnet/minecraft/class_1;tick()V\"}}}")
				.write(mods.resolve("replay.jar"));

		Pipeline.Result result = Pipeline.run(input(game, List.of(replay), Namespace.INTERMEDIARY, Map.of(TARGET, targetClass()),
				(cache, path) -> new Pipeline.Mappings(() -> table, "test-hash"), List.of()));

		try (SafeArchive jar = SafeArchive.open(result.mods().getFirst().translated().jar(), ArchiveLimits.DEFAULTS)) {
			String refmap = jar.readText("mixins.replay.refmap.json");
			assertTrue(refmap.contains("Lnet/minecraft/world/Thing;tick()V"), refmap);
		}
	}

	/**
	 * SEC-6: a class whose attribute says it is 2 GiB long makes ASM 9.8 and older throw an {@link OutOfMemoryError} as
	 * it reads it, which went past every catch around a mod and stopped the game. As one jar's mixin (which another
	 * mod's pre-flight reads too) and as another's plain class (remapped on 1.21.x, read by the linkage check on 26.x),
	 * it disables its own mod, if any, and no other.
	 */
	@Test
	void aClassAskingForMoreMemoryThanAnArrayHoldsStopsNoOtherMod(@TempDir Path game) throws Exception {
		String tiny = "v1\tofficial\tintermediary\nCLASS\ta\tnet/minecraft/class_1\n";
		MappingTable table = MappingComposer.compose(new StringReader(tiny), new StringReader("net.minecraft.world.Thing -> a:\n"));
		for (Namespace ns : Namespace.values()) {
			Path dir = Files.createDirectories(game.resolve(ns.name()));
			Path mods = Files.createDirectories(dir.resolve("mods"));
			Path good = TestJars.create().fabricModJson(TestJars.modJson("good-mod", "1.0", "\"mixins\":[\"good.mixins.json\"]"))
					.text("good.mixins.json", "{\"required\":true,\"package\":\"com.good.mixin\",\"mixins\":[\"ThingMixin\"]}")
					.bytes("com/good/mixin/ThingMixin.class", mixinClass("com/good/mixin/ThingMixin", "tick"))
					.write(mods.resolve("good.jar"));
			Path mixin = TestJars.create().fabricModJson(TestJars.modJson("hostile-mixin", "1.0", "\"mixins\":[\"hostile.mixins.json\"]"))
					.text("hostile.mixins.json", "{\"required\":true,\"package\":\"com.hostile.mixin\",\"mixins\":[\"ThingMixin\"]}")
					.bytes("com/hostile/mixin/ThingMixin.class", TestJars.classWithAnOversizedAttribute("com/hostile/mixin/ThingMixin"))
					.write(mods.resolve("hostile-mixin.jar"));
			Path plain = TestJars.create().fabricModJson(TestJars.modJson("hostile-class", "1.0", ""))
					.bytes("com/hostile/Plain.class", TestJars.classWithAnOversizedAttribute("com/hostile/Plain"))
					.write(mods.resolve("hostile-class.jar"));

			Pipeline.Result result = Pipeline.run(input(dir, List.of(good, mixin, plain), ns, Map.of(TARGET, targetClass()),
					(cache, path) -> new Pipeline.Mappings(() -> table, "test-hash"), List.of()));

			String report = ns + ": " + result.report().render();
			Set<String> loaded = result.mods().stream().map(m -> m.translated().candidate().id()).collect(Collectors.toSet());
			// Remapped on 1.21.x, the plain class disables its mod too; on 26.x the linkage check reports it unreadable, as
			// any corrupt class, which the JVM and the platform's ASM refuse to load.
			assertEquals(ns == Namespace.INTERMEDIARY ? Set.of("good-mod") : Set.of("good-mod", "hostile-class"), loaded, report);
			Report.ModRow row = result.report().mods().stream().filter(r -> r.id().equals("hostile-mixin")).findFirst().orElseThrow();
			assertTrue(row.status() == Report.Status.DISABLED && row.detail().contains("more memory than"), report);
			assertTrue(ns == Namespace.INTERMEDIARY || report.contains("hostile-class: ? -> missing class unreadable class file"), report);
		}
	}

	/**
	 * SEC-10: Fabric's class path ignores a jar's module descriptor, where NeoForge's loader makes the jar the module it
	 * describes, whose layer then fails to resolve if it requires a module nobody has (FML 12, read in its classes).
	 * The descriptors, at the root and a multi-release jar's, are left out, as the jars without one load.
	 */
	@Test
	void aJarsModuleDescriptorIsLeftOutAsFabricsClassPathIgnoresIt(@TempDir Path game) throws Exception {
		ClassWriter descriptor = new ClassWriter(0);
		descriptor.visit(Opcodes.V17, Opcodes.ACC_MODULE, "module-info", null, null, null);
		org.objectweb.asm.ModuleVisitor module = descriptor.visitModule("hostile.mod", 0, null);
		module.visitRequire("java.base", Opcodes.ACC_MANDATED, null);
		module.visitRequire("nobody.has.this", 0, null);
		module.visitEnd();
		descriptor.visitEnd();
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path described = TestJars.create().fabricModJson(TestJars.modJson("described", "1.0", ""))
				.bytes("module-info.class", descriptor.toByteArray())
				.bytes("META-INF/versions/17/module-info.class", descriptor.toByteArray())
				.bytes("com/described/A.class", TestJars.emptyClass("com/described/A", "java/lang/Object"))
				.write(mods.resolve("described.jar"));
		Path plain = TestJars.create().fabricModJson(TestJars.modJson("plain", "1.0", ""))
				.bytes("com/plain/A.class", TestJars.emptyClass("com/plain/A", "java/lang/Object"))
				.write(mods.resolve("plain.jar"));

		Pipeline.Result result = Pipeline.run(input(game, List.of(described, plain), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null, List.of()));

		Map<String, PackageGuard.Decision> packages = result.mods().stream().collect(Collectors.toMap(m -> m.translated().candidate().id(), Pipeline.LoadedMod::packages));
		assertTrue(packages.get("described").hides("module-info.class") && packages.get("described").hides("META-INF/versions/17/module-info.class"),
				result.report().render());
		assertFalse(packages.get("described").hides("com/described/A.class"));
		assertEquals(Set.of(), packages.get("plain").hiddenFiles(), "a jar without one is left as it is");
		assertTrue(result.report().render().contains("has a module descriptor"), result.report().render());
	}

	/**
	 * A launch whose translations all come from the cache doesn't read the mapping table; a mod asking to map a name
	 * then has it read (PERF-2).
	 */
	@Test
	void aCachedLaunchReadsTheMappingTableOnlyWhenAModMapsAName(@TempDir Path game) throws Exception {
		String tiny = "v1\tofficial\tintermediary\nCLASS\ta\tnet/minecraft/class_1\n";
		String proguard = "net.minecraft.world.Thing -> a:\n";
		MappingTable table = MappingComposer.compose(new StringReader(tiny), new StringReader(proguard));
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path mod = TestJars.create().fabricModJson(TestJars.modJson("plain", "1.0", "")).write(mods.resolve("plain.jar"));
		AtomicInteger reads = new AtomicInteger();
		Pipeline.MappingSupplier mappings = (cache, path) -> new Pipeline.Mappings(() -> {
			reads.incrementAndGet();
			return table;
		}, "test-hash");

		Pipeline.Result cold = Pipeline.run(input(game, List.of(mod), Namespace.INTERMEDIARY, Map.of(TARGET, targetClass()), mappings, List.of()));
		assertEquals(1, reads.get(), cold.report().render());
		assertTrue(cold.report().render().matches("(?s).*Mappings: +1 classes.*"), cold.report().render());
		Pipeline.Result warm = Pipeline.run(input(game, List.of(mod), Namespace.INTERMEDIARY, Map.of(TARGET, targetClass()), mappings, List.of()));
		assertEquals(1, warm.mods().size(), warm.report().render());
		assertEquals(1, reads.get(), "the cached launch read the table");
		assertTrue(warm.report().render().matches("(?s).*Mappings: +not read.*"), warm.report().render());
		assertEquals("net.minecraft.world.Thing", warm.runtime().getMappingResolver().mapClassName("intermediary", "net.minecraft.class_1"));
		assertEquals(2, reads.get());
	}

	/**
	 * A NeoForge mod's mod properties are its custom values to Fabric code, and the Fabric mods it says it provides are
	 * present: Sodium for NeoForge makes Fabric API's default renderer stand down and provides Indium, whose Fabric jar
	 * isn't loaded. Of two copies of a NeoForge mod (one nested in another mod file), the newest counts.
	 */
	@Test
	void neoForgeModsSpeakToFabricModsThroughTheirProperties(@TempDir Path game) throws Exception {
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path indium = TestJars.create().fabricModJson(TestJars.modJson("indium", "1.0.0", "")).write(mods.resolve("indium.jar"));
		Path addon = TestJars.create().fabricModJson(TestJars.modJson("sodium-addon", "1.0.0", "\"depends\":{\"indium\":\"*\",\"sodium\":\">=0.9\"}"))
				.write(mods.resolve("addon.jar"));
		Map<String, Object> sodiumProperties = Map.of("fabric-renderer-api-v1:contains_renderer", true, "fabric:provides", List.of("indium"));
		// A multi-loader mod's NeoForge build finds its own files through Fabric's API when it is present.
		Path sodiumJar = TestJars.create().text("sodium.toml", "defaults").write(game.resolve("sodium-neoforge.jar"));
		List<Pipeline.NeoForgeMod> neoForgeMods = List.of(
				new Pipeline.NeoForgeMod("sodium", "0.8.0", "Sodium", Map.of(), List.of()),
				new Pipeline.NeoForgeMod("sodium", "0.9.2+mc26.3", "Sodium", sodiumProperties, List.of(sodiumJar)),
				new Pipeline.NeoForgeMod("sodium", "0.9.1", "Sodium", Map.of(), List.of()));

		Pipeline.Result result = Pipeline.run(input(game, List.of(indium, addon), Namespace.OFFICIAL, Map.of(), null, neoForgeMods));

		Set<String> loaded = result.mods().stream().map(m -> m.translated().candidate().id()).collect(Collectors.toSet());
		assertEquals(Set.of("sodium-addon"), loaded, result.report().render());
		assertTrue(result.report().mods().stream().anyMatch(r -> r.id().equals("indium") && r.status() == Report.Status.SKIPPED
				&& r.detail().contains("NeoForge mod Sodium 0.9.2+mc26.3 provides it")), result.report().render());
		ModMetadata sodium = result.runtime().getModContainer("sodium").orElseThrow().getMetadata();
		assertEquals("0.9.2+mc26.3", sodium.getVersion().getFriendlyString());
		assertTrue(sodium.getCustomValue("fabric-renderer-api-v1:contains_renderer").getAsBoolean());
		assertEquals("indium", sodium.getCustomValue("fabric:provides").getAsArray().get(0).getAsString());
		assertEquals("sodium", result.runtime().getModContainer("indium").orElseThrow().getMetadata().getId());
		assertEquals("defaults", Files.readString(result.runtime().getModContainer("sodium").orElseThrow().findPath("sodium.toml").orElseThrow()));
		try (SafeArchive overlay = SafeArchive.open(result.mods().getFirst().translated().overlay(), ArchiveLimits.DEFAULTS)) {
			String toml = overlay.readText("META-INF/neoforge.mods.toml");
			assertTrue(toml.contains("modId=\"sodium\""), "loads after the mod providing what it depends on: " + toml);
			assertFalse(toml.contains("modId=\"indium\""), toml);
		}
	}

	/**
	 * WTHIT's Fabric build, which refuses the loader itself, is refused with a mod needing it, and the player is told to
	 * install its build for the loader, unless the player asks to load it anyway.
	 */
	@Test
	void aRefusedFabricBuildLoadsOnRequest(@TempDir Path game) throws Exception {
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path wthit = TestJars.create().fabricModJson(TestJars.modJson("wthit", "12.0.0", "\"name\":\"WTHIT\"")).write(mods.resolve("wthit.jar"));
		Path addon = TestJars.create().fabricModJson(TestJars.modJson("wthit-addon", "1.0.0", "\"depends\":{\"wthit\":\"*\"}"))
				.write(mods.resolve("addon.jar"));

		Pipeline.Result result = Pipeline.run(withDescriptor(input(game, List.of(wthit, addon), Namespace.OFFICIAL, Map.of(), null, List.of()),
				PlatformDescriptor.FORGE));
		assertTrue(result.mods().isEmpty(), result.report().render());
		Report.ModRow row = result.report().mods().stream().filter(r -> r.id().equals("wthit")).findFirst().orElseThrow();
		assertEquals(Report.Status.DISABLED, row.status());
		assertTrue(row.detail().startsWith("its Fabric build isn't supported on Forge: "), row.detail());
		assertEquals("Install WTHIT for Forge instead (from the same download page).", row.suggestion());
		assertFalse(result.runtime().isModLoaded("wthit"));
		assertTrue(result.report().mods().stream().anyMatch(r -> r.id().equals("wthit-addon") && r.status() == Report.Status.DISABLED),
				result.report().render());

		// A player testing support for it can load it anyway.
		Files.createDirectories(game.resolve("config"));
		Files.writeString(game.resolve("config").resolve(FabricatedConfig.FILE_NAME), "{\"schema\": 1, \"mods\": {\"wthit\": {\"loadUnsupported\": true}}}");
		Pipeline.Result forced = Pipeline.run(withDescriptor(input(game, List.of(wthit, addon), Namespace.OFFICIAL, Map.of(), null, List.of()),
				PlatformDescriptor.FORGE));
		Set<String> loaded = forced.mods().stream().map(m -> m.translated().candidate().id()).collect(Collectors.toSet());
		assertEquals(Set.of("wthit", "wthit-addon"), loaded, forced.report().render());
		assertTrue(forced.report().render().contains("Unsupported mods loaded on request (loadUnsupported)"), forced.report().render());
	}

	/**
	 * Essential's loader downloads Essential as the game starts, when NeoForge takes no mod any more: the jar it left in
	 * an earlier launch (the newest of its numbered copies) loads with the mods folder's, next to its loader, and one past
	 * 30 days is reported, as its loader doesn't update it then (DOWNLOAD-1), on Forge too. Where the loader doesn't load
	 * (the player disabled it), the jar isn't read.
	 */
	@Test
	void aJarALoaderDownloadedInAnEarlierLaunchLoadsWithIt(@TempDir Path game) throws Exception {
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path loader = TestJars.create().fabricModJson(TestJars.modJson("essential-loader", "1.3.2", "")).write(mods.resolve("essential-loader.jar"));
		Path downloads = Files.createDirectories(game.resolve("essential"));
		TestJars.create().fabricModJson(TestJars.modJson("essential", "1.5.0", "")).write(downloads.resolve("Essential (fabric_26.2).jar"));
		Path newest = TestJars.create().fabricModJson(TestJars.modJson("essential", "1.5.1", "")).write(downloads.resolve("Essential (fabric_26.2).1.jar"));
		TestJars.create().fabricModJson(TestJars.modJson("essential-other", "1.0.0", "")).write(downloads.resolve("Essential (fabric_26.1.2).jar"));

		Path config = Files.createDirectories(game.resolve("config"));
		FabricatedConfig.setModDisabled(config, "essential-loader", true);
		Pipeline.Result disabled = Pipeline.run(input(game, List.of(loader), Namespace.OFFICIAL, Map.of(), null, List.of()));
		assertTrue(disabled.mods().isEmpty(), disabled.report().render());
		assertTrue(disabled.report().mods().stream().noneMatch(r -> r.id().startsWith("essential-other") || r.id().equals("essential")), disabled.report().render());
		assertTrue(disabled.report().lines(DownloadedMods.SECTION).isEmpty(), disabled.report().render());
		FabricatedConfig.setModDisabled(config, "essential-loader", false);

		for (PlatformDescriptor platform : List.of(PlatformDescriptor.NEOFORGE, PlatformDescriptor.FORGE)) {
			Pipeline.Result result = Pipeline.run(withDescriptor(input(game, List.of(loader), Namespace.OFFICIAL, Map.of(), null, List.of()), platform));
			Map<String, String> loaded = result.mods().stream().collect(Collectors.toMap(m -> m.translated().candidate().id(), m -> m.translated().candidate().version()));
			assertEquals(Map.of("essential-loader", "1.3.2", "essential", "1.5.1"), loaded, result.report().render());
			assertEquals(1, result.report().lines(DownloadedMods.SECTION).size(), result.report().render());
			assertTrue(result.report().lines(DownloadedMods.STALE_SECTION).isEmpty(), result.report().render());
		}

		Files.setLastModifiedTime(newest, FileTime.from(Instant.now().minus(Duration.ofDays(40))));
		Pipeline.Result old = Pipeline.run(input(game, List.of(loader), Namespace.OFFICIAL, Map.of(), null, List.of()));
		List<String> stale = old.report().lines(DownloadedMods.STALE_SECTION);
		assertEquals(1, stale.size(), old.report().render());
		assertTrue(stale.getFirst().startsWith("Essential's jar ") && stale.getFirst().contains(" is 40 days old"), stale.getFirst());
	}

	/**
	 * Iris's Fabric build hooks Sodium's Fabric build: next to Sodium for NeoForge it would load with its hooks missing,
	 * so it is refused, even when the player asked to load it. Alone, it loads.
	 */
	@Test
	void aFabricBuildIsRefusedNextToTheNeoForgeBuildOfItsPartner(@TempDir Path game) throws Exception {
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path iris = TestJars.create().fabricModJson(TestJars.modJson("iris", "1.11.4", "\"name\":\"Iris\"")).write(mods.resolve("iris.jar"));
		List<Pipeline.NeoForgeMod> sodium = List.of(new Pipeline.NeoForgeMod("sodium", "0.9.2+mc26.3", "Sodium", Map.of(), List.of()));
		Pipeline.Result plain = Pipeline.run(input(game, List.of(iris), Namespace.OFFICIAL, Map.of(), null, sodium));
		assertEquals("Use the NeoForge builds of both, or the Fabric builds of both.",
				plain.report().mods().stream().filter(r -> r.id().equals("iris")).findFirst().orElseThrow().suggestion());

		Files.createDirectories(game.resolve("config"));
		Files.writeString(game.resolve("config").resolve(FabricatedConfig.FILE_NAME), "{\"schema\": 1, \"mods\": {\"iris\": {\"loadUnsupported\": true}}}");
		Pipeline.Result result = Pipeline.run(input(game, List.of(iris), Namespace.OFFICIAL, Map.of(), null, sodium));

		assertTrue(result.mods().isEmpty(), result.report().render());
		Report.ModRow row = result.report().mods().stream().filter(r -> r.id().equals("iris")).findFirst().orElseThrow();
		assertEquals(Report.Status.DISABLED, row.status());
		assertTrue(row.detail().startsWith("its Fabric build can't run next to the NeoForge mod Sodium 0.9.2+mc26.3: "), row.detail());
		assertEquals("Use the NeoForge builds of both, or the Fabric builds of both.", row.suggestion());

		// Without Sodium for NeoForge, the request stands.
		Pipeline.Result alone = Pipeline.run(input(game, List.of(iris), Namespace.OFFICIAL, Map.of(), null, List.of()));
		assertEquals(List.of("iris"), alone.mods().stream().map(m -> m.translated().candidate().id()).toList(), alone.report().render());
	}

	/**
	 * A mod NeoForge refuses to load keeps no interface injections or environment classes: injecting its interface into a
	 * game class would make that class fail to load (the interface is in no module layer), crashing the game.
	 */
	@Test
	void resultWithoutRefusedModsInjectsNothingOfTheirs(@TempDir Path dir) throws Exception {
		Pipeline.LoadedMod a = loadedMod(dir, "mod-a", "com/a/Iface", "com/a/ClientThing");
		Pipeline.LoadedMod b = loadedMod(dir, "mod-b", "com/b/Iface", "com/b/ClientThing");
		Pipeline.Result result = new Pipeline.Result(null, List.of(a, b), null, Map.of(), null, Set.of(), Set.of(), Map.of());
		assertEquals(List.of("com/a/Iface", "com/b/Iface"), interfacesOf(result, "net/minecraft/world/Thing"));
		assertEquals(Set.of("com/a/ClientThing", "com/b/ClientThing"), result.environmentClasses());

		Pipeline.Result kept = result.without(Set.of("mod-b"));
		assertEquals(List.of(a), kept.mods());
		assertEquals(List.of("com/a/Iface"), interfacesOf(kept, "net/minecraft/world/Thing"));
		assertEquals(Set.of("com/a/ClientThing"), kept.environmentClasses());
		assertTrue(result.without(Set.of("mod-a", "mod-b")).injectedInterfaces().isEmpty());
		assertEquals(1, result.defaultBridges().get("net/minecraft/world/Thing").size(), "a bridge two mods need is declared once");
		assertTrue(result.without(Set.of("mod-a", "mod-b")).defaultBridges().isEmpty());
		// FINAL-2: the methods made overridable at class load, by class, each once.
		assertEquals(Map.of("net/minecraft/world/Thing", Set.of("check()Z")), result.overridableMethods());
		assertTrue(result.without(Set.of("mod-a", "mod-b")).overridableMethods().isEmpty());
		// Read from any class-loading thread: never changed after it is published.
		assertThrows(UnsupportedOperationException.class, () -> kept.injectedInterfaces().get("net/minecraft/world/Thing").clear());
		assertThrows(UnsupportedOperationException.class, () -> kept.injectedInterfaces().clear());
	}

	/** A translation that fails unexpectedly leaves its stack trace in the report: core has no log of its own. */
	@Test
	void reportsTheCauseOfAnUnexpectedTranslationFailure() {
		IllegalStateException root = new IllegalStateException("bad\nline");
		RuntimeException cause = new RuntimeException("wrapped", root);
		List<String> lines = Pipeline.causeLines("broken-mod", cause);
		assertEquals("broken-mod:", lines.getFirst());
		assertEquals("  java.lang.RuntimeException: wrapped", lines.get(1));
		assertTrue(lines.get(2).startsWith("    at io.github.cmdralph.fabricated.core.PipelineTest."), lines.get(2));
		assertTrue(lines.contains("  Caused by: java.lang.IllegalStateException: bad\nline"), lines.toString());
		assertTrue(lines.stream().filter(l -> l.startsWith("    at ")).count() <= 24, "12 frames per exception at most");

		Report report = new Report("test");
		lines.forEach(l -> report.line("Translation errors", l));
		assertFalse(report.render().contains("bad\nline"), "exception text can't start a line of its own in the report");
	}

	private static Pipeline.LoadedMod loadedMod(Path dir, String id, String iface, String environmentClass) throws Exception {
		String json = TestJars.modJson(id, "1.0", "");
		Path file = TestJars.create().fabricModJson(json).write(dir.resolve(id + ".jar"));
		Candidate c = new Candidate(file, id + "-hash", FabricModJsonParser.parse(json), id + ".jar", null, 0, List.of(), Set.of());
		InjectedInterface injection = new InjectedInterface("net/minecraft/world/Thing", iface, iface, 1);
		TranslatedMod mod = new TranslatedMod(c, id.replace('-', '_'), file, file, List.of(), 0, List.of(), List.of(injection),
				List.of(new DefaultBridge("net/minecraft/world/Thing", "net/minecraft/world/Extension", "size", "()I")), Set.of(environmentClass),
				Set.of("net/minecraft/world/Thing check()Z"), false, 0);
		return new Pipeline.LoadedMod(mod, PackageGuard.Decision.NONE, List.of());
	}

	private static List<String> interfacesOf(Pipeline.Result result, String target) {
		return result.injectedInterfaces().getOrDefault(target, List.of()).stream().map(InjectedInterface::interfaceName).toList();
	}

	@Test
	void remapsIntermediaryModsForObfuscatedVersions(@TempDir Path game) throws Exception {
		String tiny = "v1\tofficial\tintermediary\nCLASS\ta\tnet/minecraft/class_1\nMETHOD\ta\t()V\tb\tmethod_7\n";
		String proguard = "net.minecraft.world.Thing -> a:\n    void tick() -> b\n";
		MappingTable table = MappingComposer.compose(new StringReader(tiny), new StringReader(proguard));

		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/old/Caller", null, "net/minecraft/class_1", null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "method_7", "()V", null, null);
		mv.visitCode();
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 1);
		mv.visitEnd();
		cw.visitEnd();
		Path jar = TestJars.create().fabricModJson(TestJars.modJson("old-mod", "1.0", "\"depends\":{\"minecraft\":\"1.21.11\"}"))
				.bytes("com/old/Caller.class", cw.toByteArray())
				.write(game.resolve("mods/old.jar"));

		Pipeline.Result result = Pipeline.run(input(game, List.of(jar), Namespace.INTERMEDIARY, Map.of(TARGET, targetClass()),
				(cache, path) -> new Pipeline.Mappings(() -> table, "test-hash")));
		assertEquals(1, result.mods().size(), result.report().render());
		Path remapped = result.mods().getFirst().translated().jar();
		assertEquals("old_mod.jar", remapped.getFileName().toString(), "named after the mod, as the platform's logs name it");
		try (SafeArchive archive = SafeArchive.open(remapped, ArchiveLimits.DEFAULTS)) {
			ClassNode node = new ClassNode();
			new ClassReader(archive.read("com/old/Caller.class")).accept(node, 0);
			assertEquals(TARGET, node.superName);
			assertNotNull(node.methods.stream().filter(m -> m.name.equals("tick")).findFirst().orElse(null));
		}
		assertEquals("net.minecraft.world.Thing", result.runtime().getMappingResolver().mapClassName("intermediary", "net.minecraft.class_1"));
	}

	private static Candidate candidate(String id, String extraJson) throws Exception {
		return new Candidate(Path.of(id + ".jar"), "00", FabricModJsonParser.parse(TestJars.modJson(id, "1.0", extraJson)), id + ".jar", null, 0, List.of(), Set.of());
	}

	@Test
	void planSkipListsMergeWithUserOverrides() throws Exception {
		FabricApiPlan plan = FabricApiPlan.parse(PLAN);
		var config = io.github.cmdralph.fabricated.core.config.FabricatedConfig.parse(
				"{\"mods\":{\"fabric-item-api-v1\":{\"skipMixins\":[\"Other\"]}}}");
		assertEquals(Set.of("item.mixins.json:Conflicting", "Other"), Pipeline.effectiveOverride(config, plan, candidate("fabric-item-api-v1", ""), List.of()).skipMixins());
		assertEquals(Set.of(), Pipeline.effectiveOverride(config, plan, candidate("unrelated", ""), List.of()).skipMixins());
		// The mod's own hints add to them.
		Candidate hinted = candidate("unrelated", "\"custom\":{\"fabricated\":{\"skipMixins\":[\"a.mixins.json:Broken\"]}}");
		assertEquals(Set.of("a.mixins.json:Broken"), Pipeline.effectiveOverride(config, plan, hinted, List.of()).skipMixins());
		org.junit.jupiter.api.Assertions.assertThrows(io.github.cmdralph.fabricated.core.json.JsonException.class, () -> FabricApiPlan.parse(
				"{\"fabricApiVersion\":\"1\",\"modules\":{\"x\":{\"plan\":\"native\",\"version\":\"1\",\"skipMixins\":[\"A\"]}}}"));
	}

	@Test
	void aModuleLoadsWithoutTheDependenciesItsPlanDrops(@TempDir Path game) throws Exception {
		Path mods = Files.createDirectories(game.resolve("mods"));
		String needsRenderer = "\"depends\":{\"fabric-renderer-api-v1\":\"*\"}";
		Path loading = TestJars.create().fabricModJson(TestJars.modJson("fabric-model-loading-api-v1", "1.0", needsRenderer)).write(mods.resolve("loading.jar"));
		Path other = TestJars.create().fabricModJson(TestJars.modJson("fabric-block-view-api-v2", "1.0", needsRenderer)).write(mods.resolve("view.jar"));
		Pipeline.Result result = Pipeline.run(input(game, List.of(loading, other), Namespace.OFFICIAL, Map.of(), null));

		Set<String> loaded = result.mods().stream().map(m -> m.translated().candidate().id()).collect(Collectors.toSet());
		assertEquals(Set.of("fabric-model-loading-api-v1"), loaded, result.report().render());
		assertTrue(result.dependencyFailures().contains("fabric-block-view-api-v2"), "a module whose plan keeps the dependency still needs it");
		assertTrue(result.report().render().contains("fabric-model-loading-api-v1 without fabric-renderer-api-v1: only its renderer hooks need it"),
				result.report().render());
		org.junit.jupiter.api.Assertions.assertThrows(io.github.cmdralph.fabricated.core.json.JsonException.class, () -> FabricApiPlan.parse(
				"{\"fabricApiVersion\":\"1\",\"modules\":{\"x\":{\"plan\":\"unsupported\",\"dropDependencies\":[\"y\"]}}}"));
	}

	/**
	 * On 1.21.11 the model loading API's wrapper models call the renderer API's {@code emitQuads}, which its plan loads it
	 * without: that stays a warning. Another Fabric API module calling a missing method is disabled.
	 */
	@Test
	void aModuleIsNotDisabledForCallsIntoTheDependenciesItsPlanDrops(@TempDir Path game) throws Exception {
		Path mods = Files.createDirectories(game.resolve("mods"));
		String emitQuads = "emitQuads(Lnet/fabricmc/fabric/api/renderer/v1/mesh/QuadEmitter;)V";
		String wrapper = "net/fabricmc/fabric/impl/client/model/loading/Wrapper";
		Path loading = TestJars.create().fabricModJson(TestJars.modJson("fabric-model-loading-api-v1", "1.0", ""))
				.bytes(wrapper + ".class", caller(wrapper, TARGET, emitQuads))
				.write(mods.resolve("loading.jar"));
		String itemCaller = "net/fabricmc/fabric/impl/item/Caller";
		Path item = TestJars.create().fabricModJson(TestJars.modJson("fabric-item-api-v1", "1.0", ""))
				.bytes(itemCaller + ".class", caller(itemCaller, TARGET, emitQuads))
				.write(mods.resolve("item.jar"));
		Pipeline.Result result = Pipeline.run(input(game, List.of(loading, item), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));

		String report = result.report().render();
		Set<String> loaded = result.mods().stream().map(m -> m.translated().candidate().id()).collect(Collectors.toSet());
		assertEquals(Set.of("fabric-model-loading-api-v1"), loaded, report);
		assertTrue(report.contains("fabric-model-loading-api-v1: net.fabricmc.fabric.impl.client.model.loading.Wrapper -> missing method net.minecraft.world.Thing."
				+ emitQuads), report);
		assertTrue(report.contains("references code that does not exist at runtime: net.fabricmc.fabric.impl.item.Caller"), report);
	}

	/** A class whose static {@code run} calls {@code method} (name and descriptor, taking one object) of {@code owner}. */
	private static byte[] caller(String name, String owner, String method) {
		int paren = method.indexOf('(');
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "(L" + owner + ";)V", null, null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitInsn(Opcodes.ACONST_NULL);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, method.substring(0, paren), method.substring(paren), false);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** Continuity on 1.21.11: its mixins use the renderer API, which can't run there; Mixin would crash the game. */
	@Test
	void aMixinUsingAnUnsupportedModuleFailsItsMod(@TempDir Path game) throws Exception {
		Path mods = Files.createDirectories(game.resolve("mods"));
		String helper = "net/fabricmc/fabric/api/renderer/v1/render/RenderLayerHelper";
		Path renderer = TestJars.create().fabricModJson(TestJars.modJson("fabric-renderer-api-v1", "1.0", ""))
				.bytes(helper + ".class", TestJars.emptyClass(helper, "java/lang/Object"))
				.write(mods.resolve("renderer.jar"));
		Path needsIt = TestJars.create().fabricModJson(TestJars.modJson("ctm-mod", "1.0", "\"mixins\":[\"ctm.mixins.json\"]"))
				.text("ctm.mixins.json", "{\"required\":true,\"package\":\"com.ctm.mixin\",\"mixins\":[\"LayersMixin\",\"ThingMixin\"]}")
				.bytes("com/ctm/mixin/LayersMixin.class", mixinUsing("com/ctm/mixin/LayersMixin", helper))
				.bytes("com/ctm/mixin/ThingMixin.class", mixinClass("com/ctm/mixin/ThingMixin", "tick"))
				.write(mods.resolve("ctm.jar"));
		// The same with a plugin, which may skip the mixin at runtime: only the mixin goes.
		Path optional = TestJars.create().fabricModJson(TestJars.modJson("optional-ctm", "1.0", "\"mixins\":[\"opt.mixins.json\"]"))
				.text("opt.mixins.json", "{\"required\":true,\"package\":\"com.opt.mixin\",\"plugin\":\"com.opt.Plugin\",\"mixins\":[\"LayersMixin\"]}")
				.bytes("com/opt/mixin/LayersMixin.class", mixinUsing("com/opt/mixin/LayersMixin", helper))
				.bytes("com/opt/Plugin.class", TestJars.emptyClass("com/opt/Plugin", "java/lang/Object"))
				.write(mods.resolve("opt.jar"));
		Pipeline.Result result = Pipeline.run(input(game, List.of(renderer, needsIt, optional), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));

		Set<String> loaded = result.mods().stream().map(m -> m.translated().candidate().id()).collect(Collectors.toSet());
		assertEquals(Set.of("optional-ctm"), loaded, result.report().render());
		Report.ModRow ctm = result.report().mods().stream().filter(r -> r.id().equals("ctm-mod")).findFirst().orElseThrow();
		assertEquals(Report.Status.DISABLED, ctm.status());
		assertTrue(ctm.detail().contains("uses " + helper.replace('/', '.') + " from fabric-renderer-api-v1"), ctm.detail());
		assertTrue(result.report().render().contains("com.opt.mixin.LayersMixin"), result.report().render());
	}

	/**
	 * CORPUS-15: a mod's required mixin (its config required, without a plugin) calling into a Fabric mod that isn't
	 * loading would stop the game as Mixin applies it (Continuity's into the renderer API, run 362): the mod is disabled
	 * with the reason. With a plugin, which can skip it, the mod loads.
	 */
	@Test
	void aRequiredMixinUsingAModThatIsNotLoadingDisablesItsModAndAnotherIsLeftOut(@TempDir Path game) throws Exception {
		Path mods = Files.createDirectories(game.resolve("mods"));
		String api = "com/lib/Api";
		// The library fails pre-flight (its mixin injects into a method the game doesn't have), so it isn't loading.
		Path lib = TestJars.create().fabricModJson(TestJars.modJson("lib-mod", "1.0", "\"mixins\":[\"lib.mixins.json\"]"))
				.text("lib.mixins.json", "{\"required\":true,\"package\":\"com.lib.mixin\",\"mixins\":[\"BrokenMixin\"]}")
				.bytes("com/lib/mixin/BrokenMixin.class", duckMixinClass("com/lib/mixin/BrokenMixin", "methodTheGameDoesNotHave"))
				.bytes(api + ".class", TestJars.emptyClass(api, "java/lang/Object"))
				.write(mods.resolve("lib.jar"));
		Path user = TestJars.create().fabricModJson(TestJars.modJson("user-mod", "1.0", "\"mixins\":[\"user.mixins.json\"]"))
				.text("user.mixins.json", "{\"required\":true,\"package\":\"com.user.mixin\",\"mixins\":[\"UsesMixin\"]}")
				.bytes("com/user/mixin/UsesMixin.class", mixinCalling("com/user/mixin/UsesMixin", api))
				.write(mods.resolve("user.jar"));
		Path guarded = TestJars.create().fabricModJson(TestJars.modJson("guarded-mod", "1.0", "\"mixins\":[\"guarded.mixins.json\"]"))
				.text("guarded.mixins.json", "{\"required\":true,\"package\":\"com.guarded.mixin\",\"plugin\":\"com.guarded.Plugin\",\"mixins\":[\"UsesMixin\"]}")
				.bytes("com/guarded/mixin/UsesMixin.class", mixinCalling("com/guarded/mixin/UsesMixin", api))
				.bytes("com/guarded/Plugin.class", TestJars.emptyClass("com/guarded/Plugin", "java/lang/Object"))
				.write(mods.resolve("guarded.jar"));
		// CORPUS-47: OneConfig's chat mixin needs adventure-platform-fabric, which it nests and pre-flight disables.
		Path loose = TestJars.create().fabricModJson(TestJars.modJson("loose-mod", "1.0", "\"mixins\":[\"loose.mixins.json\"]"))
				.text("loose.mixins.json", "{\"package\":\"com.loose.mixin\",\"mixins\":[\"UsesMixin\",\"KeptMixin\"]}")
				.bytes("com/loose/mixin/UsesMixin.class", mixinCalling("com/loose/mixin/UsesMixin", api))
				.bytes("com/loose/mixin/KeptMixin.class", mixinClass("com/loose/mixin/KeptMixin", "tick"))
				.write(mods.resolve("loose.jar"));
		Pipeline.Result result = Pipeline.run(input(game, List.of(lib, user, guarded, loose), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));

		Set<String> loaded = result.mods().stream().map(m -> m.translated().candidate().id()).collect(Collectors.toSet());
		assertEquals(Set.of("guarded-mod", "loose-mod"), loaded, result.report().render());
		Report.ModRow row = result.report().mods().stream().filter(r -> r.id().equals("user-mod")).findFirst().orElseThrow();
		assertEquals(Report.Status.DISABLED, row.status());
		assertTrue(row.detail().contains("com.user.mixin.UsesMixin -> needs class com.lib.Api of lib-mod, which isn't loading"), row.detail());
		// Mixin stops the game applying such a mixin, required or not, unless a plugin skips it: it is left out.
		for (String id : List.of("guarded-mod", "loose-mod")) {
			Pipeline.LoadedMod mod = result.mods().stream().filter(m -> m.translated().candidate().id().equals(id)).findFirst().orElseThrow();
			String config = id.substring(0, id.indexOf('-')) + ".mixins.json";
			try (SafeArchive overlay = SafeArchive.open(mod.translated().overlay(), ArchiveLimits.DEFAULTS)) {
				String mixins = String.valueOf(Json.parseObject(overlay.readText(config)).get("mixins"));
				assertFalse(mixins.contains("UsesMixin"), mixins);
				if (id.equals("loose-mod")) assertTrue(mixins.contains("KeptMixin"), "only that mixin goes: " + mixins);
			}
		}
		String rendered = result.report().render();
		assertTrue(rendered.contains("loose-mod: com.loose.mixin.UsesMixin -> needs class com.lib.Api of lib-mod, which isn't loading; Mixin would stop"
				+ " the game applying it, so it is left out"), rendered);
		assertTrue(result.report().mods().stream().filter(r -> r.id().equals("loose-mod")).findFirst().orElseThrow().detail()
				.contains("1 mixin(s) left out that need a Fabric mod that isn't loading"), rendered);

		// Skipping that mixin changes the mod's translation, not its jar: the analysis cached for the jar is not reused.
		Files.writeString(game.resolve("config/fabricated.json"), "{\"schema\":1,\"mods\":{\"user-mod\":{\"skipMixins\":[\"user.mixins.json:UsesMixin\"]}}}");
		Pipeline.Result skipped = Pipeline.run(input(game, List.of(lib, user, guarded, loose), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));
		assertEquals(Set.of("guarded-mod", "user-mod", "loose-mod"), skipped.mods().stream().map(m -> m.translated().candidate().id()).collect(Collectors.toSet()),
				skipped.report().render());
	}

	/**
	 * LINK-2: a mixin adding an interface only a Fabric mod that isn't loading has (Porting Lib's base module's texture
	 * mixin on Forge 47 clients, whose interface is its extensions module's) leaves the game unable to define its target,
	 * required or not: the mod is disabled with the reason, unless its config's plugin may skip the mixin (LINK-3).
	 */
	@Test
	void aMixinAddingAnInterfaceOfAModThatIsNotLoadingDisablesItsMod(@TempDir Path game) throws Exception {
		Path mods = Files.createDirectories(game.resolve("mods"));
		String api = "com/lib/Extensions";
		Path lib = TestJars.create().fabricModJson(TestJars.modJson("lib-mod", "1.0", "\"mixins\":[\"lib.mixins.json\"]"))
				.text("lib.mixins.json", "{\"required\":true,\"package\":\"com.lib.mixin\",\"mixins\":[\"BrokenMixin\"]}")
				.bytes("com/lib/mixin/BrokenMixin.class", duckMixinClass("com/lib/mixin/BrokenMixin", "methodTheGameDoesNotHave"))
				.bytes(api + ".class", TestJars.emptyClass(api, "java/lang/Object"))
				.write(mods.resolve("lib.jar"));
		Path withPlugin = TestJars.create().fabricModJson(TestJars.modJson("base-mod", "1.0", "\"mixins\":[\"base.mixins.json\"]"))
				.text("base.mixins.json", "{\"required\":true,\"package\":\"com.base.mixin\",\"plugin\":\"com.base.Plugin\",\"mixins\":[\"TextureMixin\"]}")
				.bytes("com/base/mixin/TextureMixin.class", mixinImplementing("com/base/mixin/TextureMixin", api))
				.bytes("com/base/Plugin.class", TestJars.emptyClass("com/base/Plugin", "java/lang/Object"))
				.write(mods.resolve("base.jar"));
		Path optional = TestJars.create().fabricModJson(TestJars.modJson("optional-mod", "1.0", "\"mixins\":[\"optional.mixins.json\"]"))
				.text("optional.mixins.json", "{\"package\":\"com.optional.mixin\",\"mixins\":[\"TextureMixin\"]}")
				.bytes("com/optional/mixin/TextureMixin.class", mixinImplementing("com/optional/mixin/TextureMixin", api))
				.write(mods.resolve("optional.jar"));
		Path own = TestJars.create().fabricModJson(TestJars.modJson("own-mod", "1.0", "\"mixins\":[\"own.mixins.json\"]"))
				.text("own.mixins.json", "{\"package\":\"com.own.mixin\",\"plugin\":\"com.own.Plugin\",\"mixins\":[\"TextureMixin\"]}")
				.bytes("com/own/mixin/TextureMixin.class", mixinImplementing("com/own/mixin/TextureMixin", "com/own/Extensions"))
				.bytes("com/own/Extensions.class", TestJars.emptyClass("com/own/Extensions", "java/lang/Object"))
				.bytes("com/own/Plugin.class", TestJars.emptyClass("com/own/Plugin", "java/lang/Object"))
				.write(mods.resolve("own.jar"));
		Pipeline.Result result = Pipeline.run(input(game, List.of(lib, withPlugin, optional, own), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));

		Set<String> loaded = result.mods().stream().map(m -> m.translated().candidate().id()).collect(Collectors.toSet());
		assertEquals(Set.of("base-mod", "own-mod"), loaded, "an interface of the mod's own loads: " + result.report().render());
		Report.ModRow row = result.report().mods().stream().filter(r -> r.id().equals("optional-mod")).findFirst().orElseThrow();
		assertEquals(Report.Status.DISABLED, row.status());
		assertTrue(row.detail().contains("adds an interface to its targets -> needs class com.lib.Extensions of lib-mod, which isn't loading"), row.detail());
		// LINK-3: a config's plugin would skip such a mixin at runtime (a compatibility mixin for that mod): it is left out,
		// as the plugin would leave it, and its mod loads.
		Pipeline.LoadedMod base = result.mods().stream().filter(m -> m.translated().candidate().id().equals("base-mod")).findFirst().orElseThrow();
		try (SafeArchive overlay = SafeArchive.open(base.translated().overlay(), ArchiveLimits.DEFAULTS)) {
			String mixins = String.valueOf(Json.parseObject(overlay.readText("base.mixins.json")).get("mixins"));
			assertFalse(mixins.contains("TextureMixin"), mixins);
		}
		String rendered = result.report().render();
		assertTrue(rendered.contains("base-mod: com.base.mixin.TextureMixin adds an interface to its targets -> needs class com.lib.Extensions of lib-mod,"
				+ " which isn't loading; Mixin would stop the game applying it, so it is left out"), rendered);
	}

	/**
	 * CORPUS-89: a mod whose class extends one of a mod it nests that isn't loading (Modern Industrialization's items,
	 * Magna's tools, which pre-flight disabled on Forge 47) would throw as it first uses that class, where on Fabric the
	 * nested mod is always there: it is disabled with the reason. A mod nesting it without extending its classes (Porting
	 * Lib's base jar and its GUI utilities, CORPUS-93), and one extending them without nesting it (an optional
	 * integration, which guards such uses), load.
	 */
	@Test
	void aModExtendingAClassOfAModItNestsThatIsNotLoadingIsDisabledWithIt(@TempDir Path game) throws Exception {
		Path mods = Files.createDirectories(game.resolve("mods"));
		String tool = "com/magna/Tool";
		// The nested mod fails pre-flight (its mixin injects into a method the game doesn't have), so it isn't loading.
		byte[] magna = TestJars.create().fabricModJson(TestJars.modJson("magna", "1.0", "\"mixins\":[\"magna.mixins.json\"]"))
				.text("magna.mixins.json", "{\"required\":true,\"package\":\"com.magna.mixin\",\"mixins\":[\"BrokenMixin\"]}")
				.bytes("com/magna/mixin/BrokenMixin.class", duckMixinClass("com/magna/mixin/BrokenMixin", "methodTheGameDoesNotHave"))
				.bytes(tool + ".class", TestJars.emptyClass(tool, "java/lang/Object"))
				.toBytes();
		Path industrial = TestJars.create().fabricModJson(TestJars.modJson("industrial", "1.0", "\"jars\":[{\"file\":\"META-INF/jars/magna.jar\"}]"))
				.bytes("META-INF/jars/magna.jar", magna)
				.bytes("com/industrial/Drill.class", TestJars.emptyClass("com/industrial/Drill", tool))
				.write(mods.resolve("industrial.jar"));
		byte[] gui = TestJars.create().fabricModJson(TestJars.modJson("gui-utils", "1.0", "\"mixins\":[\"gui.mixins.json\"]"))
				.text("gui.mixins.json", "{\"required\":true,\"package\":\"com.gui.mixin\",\"mixins\":[\"BrokenMixin\"]}")
				.bytes("com/gui/mixin/BrokenMixin.class", duckMixinClass("com/gui/mixin/BrokenMixin", "methodTheGameDoesNotHave"))
				.bytes("com/gui/Tooltip.class", TestJars.emptyClass("com/gui/Tooltip", "java/lang/Object"))
				.toBytes();
		Path base = TestJars.create().fabricModJson(TestJars.modJson("base", "1.0", "\"jars\":[{\"file\":\"META-INF/jars/gui.jar\"}]"))
				.bytes("META-INF/jars/gui.jar", gui)
				.bytes("com/base/Api.class", TestJars.emptyClass("com/base/Api", "java/lang/Object"))
				.write(mods.resolve("base.jar"));
		Path compat = TestJars.create().fabricModJson(TestJars.modJson("compat", "1.0", ""))
				.bytes("com/compat/MagnaDrill.class", TestJars.emptyClass("com/compat/MagnaDrill", tool))
				.write(mods.resolve("compat.jar"));
		Pipeline.Result result = Pipeline.run(input(game, List.of(industrial, base, compat), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));

		Set<String> loaded = result.mods().stream().map(m -> m.translated().candidate().id()).collect(Collectors.toSet());
		assertEquals(Set.of("base", "compat"), loaded, result.report().render());
		Report.ModRow row = result.report().mods().stream().filter(r -> r.id().equals("industrial")).findFirst().orElseThrow();
		assertEquals(Report.Status.DISABLED, row.status());
		assertTrue(row.detail().contains("a class of it needs a mod it nests, which isn't loading: com.industrial.Drill -> needs class com.magna.Tool"
				+ " of magna (a supertype, from a mod it nests), which isn't loading"), row.detail());
	}

	@Test
	void aRequiredMixinUsingAModOnlyForTheOtherSideKeepsItsMod(@TempDir Path game) throws Exception {
		// CORPUS-15: on Fabric a client-only library isn't on a server either, and a mod's mixin using it is in a class
		// only the client loads (as GlitchCore's into its client render helper); it is no reason to disable the mod.
		Path mods = Files.createDirectories(game.resolve("mods"));
		String api = "com/lib/Api";
		Path lib = TestJars.create().fabricModJson(TestJars.modJson("client-lib", "1.0", "\"environment\":\"client\""))
				.bytes(api + ".class", TestJars.emptyClass(api, "java/lang/Object"))
				.write(mods.resolve("lib.jar"));
		Path user = TestJars.create().fabricModJson(TestJars.modJson("user-mod", "1.0", "\"mixins\":[\"user.mixins.json\"]"))
				.text("user.mixins.json", "{\"required\":true,\"package\":\"com.user.mixin\",\"mixins\":[\"UsesMixin\"]}")
				.bytes("com/user/mixin/UsesMixin.class", mixinCalling("com/user/mixin/UsesMixin", api))
				.write(mods.resolve("user.jar"));
		Pipeline.Result result = Pipeline.run(input(game, List.of(lib, user), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));

		Set<String> loaded = result.mods().stream().map(m -> m.translated().candidate().id()).collect(Collectors.toSet());
		assertEquals(Set.of("user-mod"), loaded, result.report().render());
	}

	@Test
	void anEnumMixinIntoAnExtensibleEnumBecomesAnEnumExtension(@TempDir Path game) throws Exception {
		// ENUM-1: NeoForge's loader adds the constants, from the mod's enum extension file; a constant built from a value
		// computed when the class loads, from a method generated next to the mixins (ENUM-3). An enum mixin it can't take
		// (a constant built from the mixin's own fields) still disables its mod (CORPUS-10).
		String kind = "net/minecraft/world/Kind";
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path cooking = TestJars.create().fabricModJson(TestJars.modJson("cooking", "1.0", "\"mixins\":[\"cooking.mixins.json\"]"))
				.text("cooking.mixins.json", "{\"required\":true,\"package\":\"com.cooking.mixin\",\"mixins\":[\"KindMixin\",\"ThingMixin\"]}")
				.bytes("com/cooking/mixin/KindMixin.class", TestEnums.mixin("com/cooking/mixin/KindMixin", kind, List.of(TestEnums.Constant.plain("COOKING_POT")), cw -> {
				}))
				.bytes("com/cooking/mixin/ThingMixin.class", mixinClass("com/cooking/mixin/ThingMixin", "tick"))
				.write(mods.resolve("cooking.jar"));
		Path stew = TestJars.create().fabricModJson(TestJars.modJson("stew", "1.0", "\"mixins\":[\"stew.mixins.json\"]"))
				.text("stew.mixins.json", "{\"required\":true,\"package\":\"com.stew.mixin\",\"mixins\":[\"KindMixin\"]}")
				.bytes("com/stew/mixin/KindMixin.class", TestEnums.mixin("com/stew/mixin/KindMixin", kind, List.of(new TestEnums.Constant("STEW_BOWL",
						"(Ljava/lang/String;)V", mv -> mv.visitFieldInsn(Opcodes.GETSTATIC, "com/stew/Names", "BOWL", "Ljava/lang/String;"))), cw -> {
				}))
				.write(mods.resolve("stew.jar"));
		Path soup = TestJars.create().fabricModJson(TestJars.modJson("soup", "1.0", "\"mixins\":[\"soup.mixins.json\"]"))
				.text("soup.mixins.json", "{\"required\":true,\"package\":\"com.soup.mixin\",\"mixins\":[\"KindMixin\"]}")
				.bytes("com/soup/mixin/KindMixin.class", TestEnums.mixin("com/soup/mixin/KindMixin", kind, List.of(new TestEnums.Constant("SOUP_BOWL",
						"(Ljava/lang/String;)V", mv -> mv.visitFieldInsn(Opcodes.GETSTATIC, "com/soup/mixin/KindMixin", "NAME", "Ljava/lang/String;"))), cw -> {
				}))
				.write(mods.resolve("soup.jar"));
		Map<String, byte[]> gameClasses = Map.of(TARGET, targetClass(), kind, TestEnums.target(kind, true, List.of("()V", "(Ljava/lang/String;)V"), List.of()));
		Pipeline.Result result = Pipeline.run(input(game, List.of(cooking, stew, soup), Namespace.OFFICIAL, gameClasses, null));

		Map<String, Pipeline.LoadedMod> loaded = result.mods().stream().collect(Collectors.toMap(m -> m.translated().candidate().id(), m -> m));
		assertEquals(Set.of("cooking", "stew"), loaded.keySet(), result.report().render());
		try (SafeArchive overlay = SafeArchive.open(loaded.get("stew").translated().overlay(), ArchiveLimits.DEFAULTS)) {
			Map<?, ?> entry = (Map<?, ?>) Json.asList(Json.parseObject(overlay.readText("META-INF/fabricated/enum_extensions.json")).get("entries")).getFirst();
			assertEquals(Map.of("class", "com/stew/FabricatedEnumArguments", "method", "arguments0"), entry.get("parameters"));
			assertTrue(overlay.read("com/stew/FabricatedEnumArguments.class") != null, "the class computing the arguments is in the overlay");
		}
		try (SafeArchive overlay = SafeArchive.open(loaded.get("cooking").translated().overlay(), ArchiveLimits.DEFAULTS)) {
			assertTrue(overlay.readText("META-INF/neoforge.mods.toml").contains("enumExtensions=\"META-INF/fabricated/enum_extensions.json\""));
			Map<?, ?> entry = (Map<?, ?>) Json.asList(Json.parseObject(overlay.readText("META-INF/fabricated/enum_extensions.json")).get("entries")).getFirst();
			assertEquals(Map.of("enum", "net/minecraft/world/Kind", "name", "COOKING_POT", "constructor", "()V", "parameters", List.of()), entry);
			String config = overlay.readText("cooking.mixins.json");
			assertTrue(!config.contains("KindMixin") && config.contains("ThingMixin"), "only the enum mixin is left out: " + config);
		}
		Report.ModRow row = result.report().mods().stream().filter(r -> r.id().equals("soup")).findFirst().orElseThrow();
		assertEquals(Report.Status.DISABLED, row.status());
		assertTrue(row.detail().contains("extensible enums"), row.detail());
	}

	@Test
	void anEnumMixinsInjectorStaysAMixinWithoutItsConstants(@TempDir Path game) throws Exception {
		// CORPUS-65: Farmer's Delight's enum mixin into RecipeBookCategories adds an injector besides its constants. The
		// constants go to the mod's enum extension file; the injector stays in the config, in a mixin that is no longer
		// an enum (which FML would refuse), and passes pre-flight.
		String kind = "net/minecraft/world/Kind";
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path cooking = TestJars.create().fabricModJson(TestJars.modJson("cooking", "1.0", "\"mixins\":[\"cooking.mixins.json\"]"))
				.text("cooking.mixins.json", "{\"required\":true,\"package\":\"com.cooking.mixin\",\"mixins\":[\"KindMixin\"]}")
				.bytes("com/cooking/mixin/KindMixin.class", TestEnums.mixin("com/cooking/mixin/KindMixin", kind, List.of(TestEnums.Constant.plain("COOKING_POT")), cw -> {
					MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "handler", "()V", null, null);
					AnnotationVisitor inject = mv.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", false);
					AnnotationVisitor methods = inject.visitArray("method");
					methods.visit(null, "tick");
					methods.visitEnd();
					AnnotationVisitor ats = inject.visitArray("at");
					AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
					at.visit("value", "HEAD");
					at.visitEnd();
					ats.visitEnd();
					inject.visit("require", 1);
					inject.visitEnd();
					mv.visitCode();
					mv.visitInsn(Opcodes.RETURN);
					mv.visitMaxs(0, 1);
					mv.visitEnd();
				}))
				.write(mods.resolve("cooking.jar"));
		Map<String, byte[]> gameClasses = Map.of(TARGET, targetClass(), kind, TestEnums.target(kind, true, List.of("()V"), List.of(), Map.of(), cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 1);
			mv.visitEnd();
		}));
		Pipeline.Result result = Pipeline.run(input(game, List.of(cooking), Namespace.OFFICIAL, gameClasses, null));

		Map<String, Pipeline.LoadedMod> loaded = result.mods().stream().collect(Collectors.toMap(m -> m.translated().candidate().id(), m -> m));
		assertEquals(Set.of("cooking"), loaded.keySet(), result.report().render());
		try (SafeArchive overlay = SafeArchive.open(loaded.get("cooking").translated().overlay(), ArchiveLimits.DEFAULTS)) {
			Map<?, ?> entry = (Map<?, ?>) Json.asList(Json.parseObject(overlay.readText("META-INF/fabricated/enum_extensions.json")).get("entries")).getFirst();
			assertEquals("COOKING_POT", entry.get("name"));
			String config = overlay.readText("cooking.mixins.json");
			assertTrue(config == null || config.contains("KindMixin"), "the mixin stays in its config: " + config);
			ClassNode kept = new ClassNode();
			new ClassReader(overlay.read("com/cooking/mixin/KindMixin.class")).accept(kept, ClassReader.SKIP_CODE);
			assertTrue((kept.access & Opcodes.ACC_ENUM) == 0 && kept.fields.isEmpty(), "no longer an enum, nor its constants");
			assertEquals(List.of("handler"), kept.methods.stream().map(m -> m.name).toList());
		}
	}

	@Test
	void aClassTweakersEnumConstantsNeoForgeAddsNeedNoNewerMixin(@TempDir Path game) throws Exception {
		// Farmer's Delight Refabricated on 1.21.11, whose NeoForge runs Mixin 0.16.5: its class tweaker declares the
		// constants its enum mixins add, which Mixin adds from 0.17.1 only. NeoForge's loader adds those of its extensible
		// enums instead (ENUM-1), and a client enum's constant isn't added on a server, so only a constant an enum mixin
		// on this side adds some other way still needs the newer Mixin.
		String kind = "net/minecraft/world/Kind";
		String tab = "net/minecraft/client/Tab";
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path cooking = TestJars.create().fabricModJson(TestJars.modJson("cooking", "1.0", "\"mixins\":[\"cooking.mixins.json\"],\"accessWidener\":\"cooking.classtweaker\""))
				.text("cooking.mixins.json", "{\"required\":true,\"package\":\"com.cooking.mixin\",\"mixins\":[\"KindMixin\"],\"client\":[\"TabMixin\"]}")
				.text("cooking.classtweaker", "classTweaker v2 official\nextend-enum " + kind + " COOKING_POT\nextend-enum " + tab + " COOKING_TAB\n")
				.bytes("com/cooking/mixin/KindMixin.class", TestEnums.mixin("com/cooking/mixin/KindMixin", kind, List.of(TestEnums.Constant.plain("COOKING_POT")), cw -> {
				}))
				.bytes("com/cooking/mixin/TabMixin.class", TestEnums.mixin("com/cooking/mixin/TabMixin", tab, List.of(TestEnums.Constant.plain("COOKING_TAB")), cw -> {
				}))
				.write(mods.resolve("cooking.jar"));
		Path baking = TestJars.create().fabricModJson(TestJars.modJson("baking", "1.0", "\"mixins\":[\"baking.mixins.json\"],\"accessWidener\":\"baking.classtweaker\""))
				.text("baking.mixins.json", "{\"required\":true,\"package\":\"com.baking.mixin\",\"mixins\":[\"KindMixin\"]}")
				.text("baking.classtweaker", "classTweaker v2 official\nextend-enum " + kind + " BAKING_OVEN\nextend-enum " + kind + " BAKING_TRAY\n")
				.bytes("com/baking/mixin/KindMixin.class", TestEnums.mixin("com/baking/mixin/KindMixin", kind, List.of(TestEnums.Constant.plain("BAKING_OVEN")), cw -> {
				}))
				.write(mods.resolve("baking.jar"));
		Map<String, byte[]> gameClasses = Map.of(TARGET, targetClass(), kind, TestEnums.target(kind, true, List.of("()V"), List.of()));
		Pipeline.Result result = Pipeline.run(input(game, List.of(cooking, baking), Namespace.OFFICIAL, gameClasses, null, List.of(), InstalledClasses.NONE,
				ClassBytesSource.EMPTY, "0.16.5+mixin.0.8.7", "0.16.5"));

		Set<String> loaded = result.mods().stream().map(m -> m.translated().candidate().id()).collect(Collectors.toSet());
		assertEquals(Set.of("cooking"), loaded, result.report().render());
		Report.ModRow row = result.report().mods().stream().filter(r -> r.id().equals("baking")).findFirst().orElseThrow();
		assertTrue(row.detail().contains("Mixin 0.17.1"), "the constant no enum mixin adds still needs it: " + row.detail());
	}

	@Test
	void anEnumMixinTheEnumExtensionFileCantCarryBecomesAMixinAddingTheConstants(@TempDir Path game) throws Exception {
		// ENUM-5: Wilder Wild's grass colour, a constant with a body of its own in one of NeoForge's extensible enums. FML
		// refuses the enum mixin; an ordinary mixin adding the constant, with a delegate class running the body, passes
		// pre-flight and loads the mod.
		// A second such mixin in the config gets delegates of its own, not the first one's names.
		Map<String, String> other = Map.of(TestEnumBodies.MIXIN, "com/example/mixin/OtherMixin", TestEnumBodies.BODY, "com/example/mixin/OtherMixin$1");
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path tundra = TestJars.create().fabricModJson(TestJars.modJson("example", "1.0", "\"mixins\":[\"example.mixins.json\"]"))
				.text("example.mixins.json", "{\"required\":true,\"package\":\"com.example.mixin\",\"mixins\":[\"ModifierMixin\",\"OtherMixin\"]}")
				.bytes(TestEnumBodies.MIXIN + ".class", TestEnumBodies.mixin())
				.bytes(TestEnumBodies.BODY + ".class", TestEnumBodies.body(false))
				.bytes("com/example/mixin/OtherMixin.class", renamed(TestEnumBodies.mixin(), other))
				.bytes("com/example/mixin/OtherMixin$1.class", renamed(TestEnumBodies.body(false), other))
				.bytes(TestEnumBodies.HOLDER + ".class", TestEnumBodies.holder())
				.write(mods.resolve("tundra.jar"));
		Map<String, byte[]> gameClasses = Map.of(TARGET, targetClass(), TestEnumBodies.TARGET, TestEnumBodies.target(), TestEnumBodies.COLOR,
				TestEnumBodies.color());
		Pipeline.Result result = Pipeline.run(input(game, List.of(tundra), Namespace.OFFICIAL, gameClasses, null));

		assertEquals(List.of("example"), result.mods().stream().map(m -> m.translated().candidate().id()).toList(), result.report().render());
		try (SafeArchive overlay = SafeArchive.open(result.mods().getFirst().translated().overlay(), ArchiveLimits.DEFAULTS)) {
			ClassNode mixin = new ClassNode();
			new ClassReader(overlay.read(TestEnumBodies.MIXIN + ".class")).accept(mixin, ClassReader.SKIP_CODE);
			assertEquals(0, mixin.access & Opcodes.ACC_ENUM, "the enum mixin is replaced by an ordinary one");
			assertNotNull(overlay.read(TestEnumBodies.BODIES + "$0.class"), "the body's delegate is in the overlay");
			List<String> delegates = new java.util.ArrayList<>();
			for (String name : List.of(TestEnumBodies.MIXIN, "com/example/mixin/OtherMixin")) {
				ClassNode replaced = new ClassNode();
				new ClassReader(overlay.read(name + ".class")).accept(replaced, 0);
				for (MethodNode m : replaced.methods) {
					for (AbstractInsnNode insn : m.instructions) {
						if (insn instanceof org.objectweb.asm.tree.TypeInsnNode t && t.getOpcode() == Opcodes.NEW && t.desc.startsWith(TestEnumBodies.BODIES)) {
							delegates.add(t.desc);
						}
					}
				}
			}
			assertEquals(List.of(TestEnumBodies.BODIES + "$0", TestEnumBodies.BODIES + "1$0"), delegates);
			assertNotNull(overlay.read(TestEnumBodies.BODIES + "1$0.class"), "the second mixin's delegate is in the overlay");
		}
	}

	/** The class with the classes {@code names} names renamed. */
	private static byte[] renamed(byte[] data, Map<String, String> names) {
		ClassWriter cw = new ClassWriter(0);
		new ClassReader(data).accept(new org.objectweb.asm.commons.ClassRemapper(cw, new org.objectweb.asm.commons.SimpleRemapper(names)), 0);
		return cw.toByteArray();
	}

	@Test
	void anEnumMixinBecomesAMixinAddingItsConstantsWhereMixinAppliesNoEnumMixins(@TempDir Path game) throws Exception {
		// ENUM-7: Friends and Foes' mob categories on Forge 61 to 66, whose Mixin 0.8.7 applies no enum mixins (the
		// constructor its enum mixin shadows stopped the corpus clients' bootstrap). An ordinary mixin adding the constants
		// takes its place; where none can, as the enum mixin has a method of its own, pre-flight disables the mod, which
		// Mixin would stop the game on.
		String category = "net/minecraft/world/Category";
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path friends = TestJars.create().fabricModJson(TestJars.modJson("friends", "1.0", "\"mixins\":[\"friends.mixins.json\"]"))
				.text("friends.mixins.json", "{\"required\":true,\"package\":\"com.friends.mixin\",\"mixins\":[\"CategoryMixin\"]}")
				.bytes("com/friends/mixin/CategoryMixin.class", TestEnums.mixin("com/friends/mixin/CategoryMixin", category,
						List.of(TestEnums.Constant.plain("FRIENDS_GLARES")), cw -> {
						}))
				.write(mods.resolve("friends.jar"));
		Path foes = TestJars.create().fabricModJson(TestJars.modJson("foes", "1.0", "\"mixins\":[\"foes.mixins.json\"]"))
				.text("foes.mixins.json", "{\"required\":false,\"package\":\"com.foes.mixin\",\"plugin\":\"com.foes.Plugin\",\"mixins\":[\"CategoryMixin\"]}")
				.bytes("com/foes/mixin/CategoryMixin.class", TestEnums.mixin("com/foes/mixin/CategoryMixin", category,
						List.of(TestEnums.Constant.plain("FOES_RASCALS")), cw -> {
							MethodVisitor rascal = cw.visitMethod(Opcodes.ACC_PUBLIC, "rascal", "()V", null, null);
							rascal.visitCode();
							rascal.visitInsn(Opcodes.RETURN);
							rascal.visitMaxs(0, 1);
							rascal.visitEnd();
						}))
				.write(mods.resolve("foes.jar"));
		Map<String, byte[]> gameClasses = Map.of(TARGET, targetClass(), category, TestEnums.target(category, false, List.of("()V"), List.of(),
				Map.of(), cw -> {
					cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, "$VALUES", "[L" + category + ";", null,
							null).visitEnd();
					MethodVisitor clinit = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
					clinit.visitCode();
					clinit.visitInsn(Opcodes.ICONST_0);
					clinit.visitTypeInsn(Opcodes.ANEWARRAY, category);
					clinit.visitFieldInsn(Opcodes.PUTSTATIC, category, "$VALUES", "[L" + category + ";");
					clinit.visitInsn(Opcodes.RETURN);
					clinit.visitMaxs(1, 0);
					clinit.visitEnd();
				}));
		Pipeline.Result forge = Pipeline.run(withDescriptor(input(game, List.of(friends, foes), Namespace.OFFICIAL, gameClasses, null, List.of(),
				InstalledClasses.NONE, ClassBytesSource.EMPTY, "0.8.7+Jenkins-b321.git-4", "0.8.7"), PlatformDescriptor.FORGE));

		assertEquals(List.of("friends"), forge.mods().stream().map(m -> m.translated().candidate().id()).toList(), forge.report().render());
		try (SafeArchive overlay = SafeArchive.open(forge.mods().getFirst().translated().overlay(), ArchiveLimits.DEFAULTS)) {
			ClassNode mixin = new ClassNode();
			new ClassReader(overlay.read("com/friends/mixin/CategoryMixin.class")).accept(mixin, ClassReader.SKIP_CODE);
			assertEquals(0, mixin.access & Opcodes.ACC_ENUM, "the enum mixin is replaced by an ordinary one");
		}
		assertTrue(forge.report().render().contains("can't apply an enum mixin"), forge.report().render());
		// Fatal though its config is optional and has a plugin: Mixin's error there stops the game whatever the config.
		Report.ModRow row = forge.report().mods().stream().filter(r -> r.id().equals("foes")).findFirst().orElseThrow();
		assertTrue(row.detail().contains("applies no enum mixins"), row.detail());

		// Where Mixin applies enum mixins (Fabric's from 0.17.1), the enum mixin stays one.
		Pipeline.Result neoForge = Pipeline.run(input(game, List.of(friends), Namespace.OFFICIAL, gameClasses, null, List.of()));
		assertEquals(List.of("friends"), neoForge.mods().stream().map(m -> m.translated().candidate().id()).toList(), neoForge.report().render());
		try (SafeArchive overlay = SafeArchive.open(neoForge.mods().getFirst().translated().overlay(), ArchiveLimits.DEFAULTS)) {
			assertNull(overlay.read("com/friends/mixin/CategoryMixin.class"), "the mod's own enum mixin");
		}
	}

	@Test
	void anEnumMixinsConstantsTheModReadsAsFieldsOfTheEnumComeFromAHolderWhereMixinAppliesNoEnumMixins(@TempDir Path game) throws Exception {
		// CORPUS-80: Farmer's Delight on Forge 61 to 66, whose Mixin 0.8.7 applies no enum mixins. Its recipe book type and
		// search category become ordinary mixins adding the constants (ENUM-7), but its own classes read them as fields of
		// the enums (FDRecipeBookTypes.COOKING = RecipeBookType.FARMERSDELIGHT_COOKING), which no mixin can add, so pre-flight
		// disabled it. The reads go to holder classes instead. Forge makes RecipeBookType extensible with an init that throws
		// there ("This is not implemented yet"), which its create factory runs on each constant: the mixin skips it for the
		// mod's. The search category's constant takes the mod's categories, from its suppliers, as Fabric's would.
		String bookType = "net/minecraft/world/inventory/BookType";
		String settings = "net/minecraft/stats/BookSettings";
		String search = "net/minecraft/client/gui/screens/recipebook/SearchCategory";
		String category = "net/minecraft/world/item/crafting/BookCategory";
		String supplier = "Ljava/util/function/Supplier;";
		ClassWriter types = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		types.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "com/cooking/Types", null, "java/lang/Object", null);
		types.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "COOKING", "L" + bookType + ";", null, null).visitEnd();
		MethodVisitor typesInit = types.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
		typesInit.visitCode();
		typesInit.visitFieldInsn(Opcodes.GETSTATIC, bookType, "COOKING", "L" + bookType + ";");
		typesInit.visitFieldInsn(Opcodes.PUTSTATIC, "com/cooking/Types", "COOKING", "L" + bookType + ";");
		typesInit.visitInsn(Opcodes.RETURN);
		typesInit.visitMaxs(0, 0);
		typesInit.visitEnd();
		types.visitEnd();
		ClassWriter screen = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		screen.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "com/cooking/client/Screen", null, "java/lang/Object", null);
		MethodVisitor searched = screen.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "category", "()Ljava/lang/Object;", null, null);
		searched.visitCode();
		searched.visitFieldInsn(Opcodes.GETSTATIC, search, "COOKING", "L" + search + ";");
		searched.visitInsn(Opcodes.ARETURN);
		searched.visitMaxs(0, 0);
		searched.visitEnd();
		screen.visitEnd();
		ClassWriter categories = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		categories.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "com/cooking/Categories", null, "java/lang/Object", null);
		categories.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "MEALS", supplier, null, null).visitEnd();
		categories.visitEnd();
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path cooking = TestJars.create().fabricModJson(TestJars.modJson("cooking", "1.0", "\"mixins\":[\"cooking.mixins.json\"]"))
				.text("cooking.mixins.json", "{\"required\":true,\"package\":\"com.cooking.mixin\",\"mixins\":[\"BookTypeMixin\"],"
						+ "\"client\":[\"SearchCategoryMixin\"],\"injectors\":{\"defaultRequire\":1}}")
				.bytes("com/cooking/mixin/BookTypeMixin.class", TestEnums.mixin("com/cooking/mixin/BookTypeMixin", bookType,
						List.of(TestEnums.Constant.plain("COOKING")), cw -> {
						}))
				.bytes("com/cooking/mixin/SearchCategoryMixin.class", TestEnums.mixin("com/cooking/mixin/SearchCategoryMixin", search,
						List.of(new TestEnums.Constant("COOKING", "([L" + category + ";)V", mv -> {
							mv.visitInsn(Opcodes.ICONST_1);
							mv.visitTypeInsn(Opcodes.ANEWARRAY, category);
							mv.visitInsn(Opcodes.DUP);
							mv.visitInsn(Opcodes.ICONST_0);
							mv.visitFieldInsn(Opcodes.GETSTATIC, "com/cooking/Categories", "MEALS", supplier);
							mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/function/Supplier", "get", "()Ljava/lang/Object;", true);
							mv.visitTypeInsn(Opcodes.CHECKCAST, category);
							mv.visitInsn(Opcodes.AASTORE);
						})), cw -> {
						}))
				.bytes("com/cooking/Types.class", types.toByteArray())
				.bytes("com/cooking/client/Screen.class", screen.toByteArray())
				.bytes("com/cooking/Categories.class", categories.toByteArray())
				.write(mods.resolve("cooking.jar"));

		ClassWriter book = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		book.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, settings, null, "java/lang/Object", null);
		MethodVisitor register = book.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "register", "(L" + bookType + ";)V", null, null);
		register.visitCode();
		register.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException");
		register.visitInsn(Opcodes.DUP);
		register.visitLdcInsn("This is not implemented yet, poke Forge if you actually use this");
		register.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V", false);
		register.visitInsn(Opcodes.ATHROW);
		register.visitMaxs(0, 0);
		register.visitEnd();
		book.visitEnd();
		ClassWriter bookCategory = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		bookCategory.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, category, null, "java/lang/Object", null);
		bookCategory.visitEnd();
		Map<String, byte[]> gameClasses = Map.of(TARGET, targetClass(), settings, book.toByteArray(), category, bookCategory.toByteArray(),
				bookType, withInterface(TestEnums.target(bookType, false, List.of("()V"), List.of(), Map.of(), cw -> {
					valuesArray(cw, bookType);
					MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "init", "()V", null, null);
					init.visitCode();
					init.visitVarInsn(Opcodes.ALOAD, 0);
					init.visitMethodInsn(Opcodes.INVOKESTATIC, settings, "register", "(L" + bookType + ";)V", false);
					init.visitInsn(Opcodes.RETURN);
					init.visitMaxs(0, 0);
					init.visitEnd();
					MethodVisitor create = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create", "(Ljava/lang/String;)L" + bookType + ";", null, null);
					create.visitCode();
					create.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException");
					create.visitInsn(Opcodes.DUP);
					create.visitLdcInsn("Enum not extended!");
					create.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V", false);
					create.visitInsn(Opcodes.ATHROW);
					create.visitMaxs(0, 0);
					create.visitEnd();
				}), "net/minecraftforge/common/IExtensibleEnum"),
				search, TestEnums.target(search, false, List.of("([L" + category + ";)V"), List.of(), Map.of(), cw -> valuesArray(cw, search)));
		Pipeline.Input in = withDescriptor(input(game, List.of(cooking), Namespace.OFFICIAL, gameClasses, null, List.of(), InstalledClasses.NONE,
				ClassBytesSource.EMPTY, "0.8.7+Jenkins-b321.git-4", "0.8.7"), PlatformDescriptor.FORGE);
		Pipeline.Result forge = Pipeline.run(withClientSide(in));

		assertEquals(List.of("cooking"), forge.mods().stream().map(m -> m.translated().candidate().id()).toList(), forge.report().render());
		String report = forge.report().render();
		assertTrue(report.contains("skipping Forge's init for them, which never returns here"), report);
		assertTrue(report.contains("reading them as fields of the enum, which a mixin can't add, read them from com.cooking.FabricatedEnumConstants"), report);
		try (SafeArchive overlay = SafeArchive.open(forge.mods().getFirst().translated().overlay(), ArchiveLimits.DEFAULTS)) {
			ClassNode mixin = new ClassNode();
			new ClassReader(overlay.read("com/cooking/mixin/BookTypeMixin.class")).accept(mixin, ClassReader.SKIP_CODE);
			assertEquals(0, mixin.access & Opcodes.ACC_ENUM, "the enum mixin is replaced by an ordinary one");
			assertTrue(mixin.methods.stream().anyMatch(m -> m.name.equals("fabricated$skipInit")), "Forge's init is skipped for the constant");
			ClassNode searchMixin = new ClassNode();
			new ClassReader(overlay.read("com/cooking/mixin/SearchCategoryMixin.class")).accept(searchMixin, ClassReader.SKIP_CODE);
			assertEquals(0, searchMixin.access & Opcodes.ACC_ENUM);
			for (String[] read : List.of(new String[] {"com/cooking/Types", bookType}, new String[] {"com/cooking/client/Screen", search})) {
				ClassNode reader = new ClassNode();
				new ClassReader(overlay.read(read[0] + ".class")).accept(reader, 0);
				String owner = null;
				for (MethodNode m : reader.methods) {
					for (AbstractInsnNode insn : m.instructions) {
						if (insn instanceof FieldInsnNode f && f.getOpcode() == Opcodes.GETSTATIC && f.name.equals("COOKING")) owner = f.owner;
					}
				}
				assertTrue(owner != null && owner.startsWith("com/cooking/FabricatedEnumConstants"), read[0] + " reads " + owner);
				ClassNode holder = new ClassNode();
				new ClassReader(overlay.read(owner + ".class")).accept(holder, ClassReader.SKIP_CODE);
				assertEquals(List.of("COOKING:L" + read[1] + ";"), holder.fields.stream().map(f -> f.name + ":" + f.desc).toList());
			}
		}
	}

	@Test
	void fabricAsmsEarlyRisersEnumConstantsComeFromEnumMixinsOfFabricatedsOwn(@TempDir Path game) throws Exception {
		// CORPUS-80 on Forge 47: Farmer's Delight adds its recipe book type and search category with Fabric ASM, whose
		// Mixin plugin runs the mod's early riser and serves the enum mixins it makes from Fabric's class loader. Forge's
		// takes no added URLs, so Mixin dropped the plugin's config, no constant was made, and the mod's entrypoint failed
		// on ClassTinkerers.getEnum. Fabricated reads the early riser instead and adds the constants with enum mixins of its
		// own, which Forge's Mixin 0.8.5 can't apply either: they become ordinary mixins calling the enums' create factories
		// (ENUM-7). The search category's argument comes from the mod's supplier, through a helper next to it.
		String bookType = "net/minecraft/world/inventory/RecipeBookType";
		String categories = "net/minecraft/client/RecipeBookCategories";
		String stack = "net/minecraft/world/item/ItemStack";
		String stacks = "com/cooking/Stacks";
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path cooking = TestJars.create().fabricModJson(TestJars.modJson("cooking", "1.0", "\"entrypoints\":{\"mm:early_risers\":[\"com.cooking.CookingASM\"]}"))
				.bytes("com/cooking/CookingASM.class", TestEarlyRisers.cooking("com/cooking/CookingASM", bookType.replace('/', '.'), categories.replace('/', '.'),
						stack.replace('/', '.'), stacks))
				.bytes(stacks + ".class", TestEarlyRisers.stacks(stacks, stack))
				.write(mods.resolve("cooking.jar"));
		Path fabricAsm = TestJars.create().fabricModJson(TestJars.modJson("mm", "2.3", "\"mixins\":[\"mm.mixins.json\"]"))
				.text("mm.mixins.json", "{\"required\":true,\"package\":\"com.chocohead.mm.mixin\",\"plugin\":\"com.chocohead.mm.Plugin\",\"mixins\":[]}")
				.write(mods.resolve("mm.jar"));
		Path broken = TestJars.create().fabricModJson(TestJars.modJson("broken", "1.0", "\"entrypoints\":{\"mm:early_risers\":[\"com.broken.BrokenASM\"]}"))
				.bytes("com/broken/BrokenASM.class", TestEarlyRisers.riser("com/broken/BrokenASM", run -> {
					run.visitLdcInsn("net.minecraft.world.Thing");
					run.visitInsn(Opcodes.ACONST_NULL);
					run.visitMethodInsn(Opcodes.INVOKESTATIC, "com/chocohead/mm/api/ClassTinkerers", "addTransformation",
							"(Ljava/lang/String;Ljava/util/function/Consumer;)V", false);
				}))
				.text("silky.at", "net/minecraft/class_1\n")
				.write(mods.resolve("broken.jar"));

		Map<String, byte[]> gameClasses = Map.of(TARGET, targetClass(), stack, TestJars.emptyClass(stack, "java/lang/Object"),
				bookType, withInterface(TestEnums.target(bookType, false, List.of("()V"), List.of(), Map.of(), cw -> {
					valuesArray(cw, bookType);
					unextended(cw, "(Ljava/lang/String;)L" + bookType + ";");
				}), "net/minecraftforge/common/IExtensibleEnum"),
				categories, withInterface(TestEnums.target(categories, false, List.of("([L" + stack + ";)V"), List.of(), Map.of(), cw -> {
					valuesArray(cw, categories);
					unextended(cw, "(Ljava/lang/String;[L" + stack + ";)L" + categories + ";");
				}), "net/minecraftforge/common/IExtensibleEnum"));
		Pipeline.Input in = withDescriptor(input(game, List.of(broken, cooking, fabricAsm), Namespace.OFFICIAL, gameClasses, null, List.of(),
				InstalledClasses.NONE, ClassBytesSource.EMPTY, "0.8.5+Jenkins-b310.git-155314e6e91465dad727e621a569906a410cd6f4", "0.8.5"),
				PlatformDescriptor.FORGE);
		Pipeline.Result forge = Pipeline.run(withClientSide(in));

		String report = forge.report().render();
		assertEquals(List.of("broken", "cooking", "mm"), forge.mods().stream().map(m -> m.translated().candidate().id()).sorted().toList(), report);
		assertTrue(report.contains("Fabric ASM's early riser(s) com.cooking.CookingASM add constants to net.minecraft.world.inventory.RecipeBookType"
				+ " (COOKING); net.minecraft.client.RecipeBookCategories (COOKING_SEARCH)"), report);
		assertTrue(report.contains("Its constants (COOKING_SEARCH) go to net.minecraft.client.RecipeBookCategories"), report);
		assertTrue(report.contains("a mixin into its class initializer adds them with the enum's create factories"), report);
		assertTrue(report.contains("Fabric ASM's Mixin plugin can't run on Forge"), report);
		assertTrue(report.contains("Fabric ASM's early riser can't be read: it uses Fabric ASM's ClassTinkerers.addTransformation, which Fabricated"
				+ " doesn't run"), report);
		assertTrue(report.contains("broken: WARN [silky.at] Fabric ASM's access transformer isn't applied"), report);
		Pipeline.LoadedMod mod = forge.mods().stream().filter(m -> m.translated().candidate().id().equals("cooking")).findFirst().orElseThrow();
		try (SafeArchive overlay = SafeArchive.open(mod.translated().overlay(), ArchiveLimits.DEFAULTS)) {
			String configName = "fabricated-cooking-early-risers.mixins.json";
			Map<String, Object> config = io.github.cmdralph.fabricated.core.json.Json.parseObject(overlay.readText(configName));
			assertEquals("com.cooking.fabricatedearlyrisers", config.get("package"));
			assertEquals(List.of("RecipeBookTypeAdditions", "RecipeBookCategoriesAdditions"), config.get("mixins"));
			assertTrue(overlay.readText("META-INF/MANIFEST.MF").contains(configName), overlay.readText("META-INF/MANIFEST.MF"));
			for (String mixin : List.of("RecipeBookTypeAdditions", "RecipeBookCategoriesAdditions")) {
				ClassNode node = new ClassNode();
				new ClassReader(overlay.read("com/cooking/fabricatedearlyrisers/" + mixin + ".class")).accept(node, ClassReader.SKIP_CODE);
				assertEquals(0, node.access & Opcodes.ACC_ENUM, mixin + " is an ordinary mixin now");
			}
			assertNotNull(overlay.read(stacks + "$FabricatedArguments.class"), "the supplied arguments' helper");
			ClassNode supplier = new ClassNode();
			new ClassReader(overlay.read(stacks + ".class")).accept(supplier, ClassReader.SKIP_CODE);
			assertTrue(supplier.methods.stream().filter(m -> m.name.equals("stacks")).allMatch(m -> (m.access & Opcodes.ACC_PRIVATE) == 0),
					"the helper may call the mod's supplier");
		}
		Pipeline.LoadedMod mm = forge.mods().stream().filter(m -> m.translated().candidate().id().equals("mm")).findFirst().orElseThrow();
		try (SafeArchive overlay = SafeArchive.open(mm.translated().overlay(), ArchiveLimits.DEFAULTS)) {
			String manifest = overlay.readText("META-INF/MANIFEST.MF");
			assertTrue(manifest == null || !manifest.contains("mm.mixins.json"), "Fabric ASM's own config is left out: " + manifest);
		}
	}

	/** Forge's {@code create} factory of an extensible enum, which its enum extender writes as the game loads. */
	private static void unextended(ClassWriter cw, String desc) {
		MethodVisitor create = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create", desc, null, null);
		create.visitCode();
		create.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException");
		create.visitInsn(Opcodes.DUP);
		create.visitLdcInsn("Enum not extended!");
		create.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V", false);
		create.visitInsn(Opcodes.ATHROW);
		create.visitMaxs(0, 0);
		create.visitEnd();
	}

	/** The class implementing {@code itf} too (Forge's marker of an extensible enum). */
	private static byte[] withInterface(byte[] data, String itf) {
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, 0);
		node.interfaces.add(itf);
		ClassWriter cw = new ClassWriter(0);
		node.accept(cw);
		return cw.toByteArray();
	}

	/** The enum's values array, set in its class initializer as javac does. */
	private static void valuesArray(ClassWriter cw, String name) {
		cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, "$VALUES", "[L" + name + ";", null, null).visitEnd();
		MethodVisitor clinit = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
		clinit.visitCode();
		clinit.visitInsn(Opcodes.ICONST_0);
		clinit.visitTypeInsn(Opcodes.ANEWARRAY, name);
		clinit.visitFieldInsn(Opcodes.PUTSTATIC, name, "$VALUES", "[L" + name + ";");
		clinit.visitInsn(Opcodes.RETURN);
		clinit.visitMaxs(1, 0);
		clinit.visitEnd();
	}

	/** {@code in} on a client. */
	private static Pipeline.Input withClientSide(Pipeline.Input in) throws Exception {
		java.lang.reflect.RecordComponent[] parts = Pipeline.Input.class.getRecordComponents();
		Class<?>[] types = new Class<?>[parts.length];
		Object[] values = new Object[parts.length];
		for (int i = 0; i < parts.length; i++) {
			types[i] = parts[i].getType();
			values[i] = parts[i].getName().equals("clientSide") ? Boolean.TRUE : parts[i].getAccessor().invoke(in);
		}
		return Pipeline.Input.class.getDeclaredConstructor(types).newInstance(values);
	}

	@Test
	void anInterfaceMixinLeftWithoutItsInjectorsNoLongerMakesItsTargetItsOwnSupertype(@TempDir Path game) throws Exception {
		// CORPUS-76: AppleSkin's TooltipComponentMixin extends its target ClientTooltipComponent for two static injectors,
		// which Forge 47's Mixin 0.8.5 applies none of in an interface mixin (INTERFACE-1). Where Fabricated doesn't apply
		// them either (here, injecting at a call) they are left out; without them Mixin took it for an accessor mixin and made
		// ClientTooltipComponent implement it, so extend itself, and the client stopped with a ClassCircularityError. Left
		// with nothing for its target it is left out whole; left with another interface it keeps that one alone.
		String tooltip = "net/minecraft/client/Tooltip";
		Map<String, String> injectors = Map.of(
				"org/spongepowered/asm/mixin/injection/Inject.java",
				"package org.spongepowered.asm.mixin.injection; public @interface Inject { String[] method(); At[] at(); }",
				"org/spongepowered/asm/mixin/injection/At.java", "package org.spongepowered.asm.mixin.injection; public @interface At { String value(); }",
				"org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable.java",
				"package org.spongepowered.asm.mixin.injection.callback; public class CallbackInfoReturnable<R> { }");
		String hook = """
				@Inject(method = "create", at = @At("INVOKE"))
				private static void onCreate(Object data, CallbackInfoReturnable<net.minecraft.client.Tooltip> info) { }""";
		Map<String, byte[]> classes = TestSources.compile(game.resolve("build"), TestSources.MIXIN_ANNOTATIONS, injectors, Map.of(
				"net/minecraft/client/Tooltip.java", "package net.minecraft.client; public interface Tooltip { static Tooltip create(Object data) { return null; } }",
				"com/apple/Bridge.java", "package com.apple; public interface Bridge<T> { }",
				"com/apple/mixin/TooltipMixin.java", """
						package com.apple.mixin;
						import org.spongepowered.asm.mixin.Mixin;
						import org.spongepowered.asm.mixin.injection.*;
						import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
						@Mixin(net.minecraft.client.Tooltip.class)
						public interface TooltipMixin extends net.minecraft.client.Tooltip {
						""" + hook + "\n}",
				"com/apple/mixin/TooltipBridge.java", """
						package com.apple.mixin;
						import org.spongepowered.asm.mixin.Mixin;
						import org.spongepowered.asm.mixin.injection.*;
						import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
						@Mixin(net.minecraft.client.Tooltip.class)
						public interface TooltipBridge extends net.minecraft.client.Tooltip, com.apple.Bridge<String> {
						""" + hook + "\n}"));
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path apple = TestJars.create().fabricModJson(TestJars.modJson("apple", "1.0", "\"mixins\":[\"apple.mixins.json\"]"))
				.text("apple.mixins.json", "{\"required\":true,\"package\":\"com.apple.mixin\",\"mixins\":[\"TooltipMixin\",\"TooltipBridge\"]}")
				.bytes("com/apple/Bridge.class", classes.get("com/apple/Bridge"))
				.bytes("com/apple/mixin/TooltipMixin.class", classes.get("com/apple/mixin/TooltipMixin"))
				.bytes("com/apple/mixin/TooltipBridge.class", classes.get("com/apple/mixin/TooltipBridge"))
				.write(mods.resolve("apple.jar"));
		Map<String, byte[]> gameClasses = Map.of(TARGET, targetClass(), tooltip, classes.get(tooltip));
		Pipeline.Result forge = Pipeline.run(withDescriptor(input(game, List.of(apple), Namespace.OFFICIAL, gameClasses, null, List.of(),
				InstalledClasses.NONE, ClassBytesSource.EMPTY, "0.8.5+Jenkins-b310.git-155314e6e91465dad727e621a569906a410cd6f4", "0.8.5"),
				PlatformDescriptor.FORGE));

		String report = forge.report().render();
		assertEquals(List.of("apple"), forge.mods().stream().map(m -> m.translated().candidate().id()).toList(), report);
		assertTrue(report.contains("Left out whole: with the methods above left out it adds nothing to net.minecraft.client.Tooltip"), report);
		try (SafeArchive overlay = SafeArchive.open(forge.mods().getFirst().translated().overlay(), ArchiveLimits.DEFAULTS)) {
			String config = new String(overlay.read("apple.mixins.json"), StandardCharsets.UTF_8);
			assertFalse(config.contains("\"TooltipMixin\""), config);
			assertTrue(config.contains("\"TooltipBridge\""), config);
			ClassNode bridge = new ClassNode();
			new ClassReader(overlay.read("com/apple/mixin/TooltipBridge.class")).accept(bridge, 0);
			assertEquals(List.of("com/apple/Bridge"), bridge.interfaces, "it no longer extends its target");
			assertEquals("Ljava/lang/Object;Lcom/apple/Bridge<Ljava/lang/String;>;", bridge.signature);
			assertTrue(bridge.methods.isEmpty(), "its injector is left out");
		}

		// Where Mixin applies injectors in interface mixins (from 0.8.6), both stay as the mod has them.
		Pipeline.Result neoForge = Pipeline.run(input(game, List.of(apple), Namespace.OFFICIAL, gameClasses, null, List.of()));
		try (SafeArchive overlay = SafeArchive.open(neoForge.mods().getFirst().translated().overlay(), ArchiveLimits.DEFAULTS)) {
			assertNull(overlay.read("com/apple/mixin/TooltipMixin.class"), neoForge.report().render());
			assertNull(overlay.read("com/apple/mixin/TooltipBridge.class"), neoForge.report().render());
		}
	}

	@Test
	void anInterfaceMixinsInjectAtHeadIsKeptForFabricatedToApplyWhereMixinDoesnt(@TempDir Path game) throws Exception {
		// INTERFACE-1, CORPUS-91: Forge 47's Mixin 0.8.5 applies no injector in an interface mixin and drops one with a
		// non-public method. An @Inject Fabricated applies itself (at HEAD, RETURN or TAIL) becomes a public synthetic method
		// Mixin merges into the target, named for its mixin and marked with where it injects; Fabricated's Mixin extension calls
		// it from there (InterfaceInjectsTest). CORPUS-96: Mixin takes an interface mixin whose methods are all synthetic for an
		// accessor mixin and makes its target implement it, so the mixin, AppleSkin's shape, no longer extends its target, which
		// would make that its own supertype (CORPUS-76's ClassCircularityError). A mixin with another injector Fabricated
		// doesn't apply, left out, keeps the one it does: it isn't left out whole as adding nothing.
		Map<String, String> injectors = Map.of(
				"org/spongepowered/asm/mixin/injection/Inject.java",
				"package org.spongepowered.asm.mixin.injection; public @interface Inject { String[] method(); At[] at(); boolean cancellable() default false; }",
				"org/spongepowered/asm/mixin/injection/At.java", "package org.spongepowered.asm.mixin.injection; public @interface At { String value(); }",
				"org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable.java",
				"package org.spongepowered.asm.mixin.injection.callback; public class CallbackInfoReturnable<R> { }");
		Map<String, byte[]> classes = TestSources.compile(game.resolve("build"), TestSources.MIXIN_ANNOTATIONS, injectors, Map.of(
				"net/minecraft/client/Tooltip.java", "package net.minecraft.client; public interface Tooltip { static Tooltip create(Object data) { return null; } }",
				"com/apple/mixin/TooltipMixin.java", """
						package com.apple.mixin;
						import org.spongepowered.asm.mixin.Mixin;
						import org.spongepowered.asm.mixin.injection.*;
						import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
						@Mixin(net.minecraft.client.Tooltip.class)
						public interface TooltipMixin extends net.minecraft.client.Tooltip {
							@Inject(method = "create", at = @At("HEAD"), cancellable = true)
							private static void onCreate(Object data, CallbackInfoReturnable<net.minecraft.client.Tooltip> info) { }
						}""",
				"com/apple/mixin/TooltipBoth.java", """
						package com.apple.mixin;
						import org.spongepowered.asm.mixin.Mixin;
						import org.spongepowered.asm.mixin.injection.*;
						import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
						@Mixin(net.minecraft.client.Tooltip.class)
						public interface TooltipBoth {
							@Inject(method = "create", at = @At("HEAD"))
							private static void atHead(Object data, CallbackInfoReturnable<net.minecraft.client.Tooltip> info) { }
							@Inject(method = "create", at = @At("INVOKE"))
							private static void atCall(Object data, CallbackInfoReturnable<net.minecraft.client.Tooltip> info) { }
						}"""));
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path apple = TestJars.create().fabricModJson(TestJars.modJson("apple", "1.0", "\"mixins\":[\"apple.mixins.json\"]"))
				.text("apple.mixins.json", "{\"required\":true,\"package\":\"com.apple.mixin\",\"mixins\":[\"TooltipMixin\",\"TooltipBoth\"]}")
				.bytes("com/apple/mixin/TooltipMixin.class", classes.get("com/apple/mixin/TooltipMixin"))
				.bytes("com/apple/mixin/TooltipBoth.class", classes.get("com/apple/mixin/TooltipBoth"))
				.write(mods.resolve("apple.jar"));
		Map<String, byte[]> gameClasses = Map.of(TARGET, targetClass(), "net/minecraft/client/Tooltip", classes.get("net/minecraft/client/Tooltip"));
		Pipeline.Result forge = Pipeline.run(withDescriptor(input(game, List.of(apple), Namespace.OFFICIAL, gameClasses, null, List.of(),
				InstalledClasses.NONE, ClassBytesSource.EMPTY, "0.8.5+Jenkins-b310.git-155314e6e91465dad727e621a569906a410cd6f4", "0.8.5"),
				PlatformDescriptor.FORGE));

		String report = forge.report().render();
		assertTrue(report.contains("@Inject handler onCreate(Ljava/lang/Object;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V is an"
				+ " injector in an interface mixin, which Forge's Mixin doesn't apply (Mixin does from 0.8.6); Fabricated applies it as the mixin applies,"
				+ " as Mixin would (INTERFACE-1)"), report);
		assertTrue(report.contains("With its injectors made handlers Fabricated applies (INTERFACE-1), Mixin takes it for an accessor mixin and makes"
				+ " net.minecraft.client.Tooltip implement it; it no longer extends net.minecraft.client.Tooltip, which would make that its own supertype"
				+ " (CORPUS-76, CORPUS-96)"), report);
		assertFalse(report.contains("Left out whole"), report);
		try (SafeArchive overlay = SafeArchive.open(forge.mods().getFirst().translated().overlay(), ArchiveLimits.DEFAULTS)) {
			byte[] config = overlay.read("apple.mixins.json");
			String listed = config == null ? "\"TooltipMixin\",\"TooltipBoth\"" : new String(config, StandardCharsets.UTF_8);
			assertTrue(listed.contains("\"TooltipMixin\"") && listed.contains("\"TooltipBoth\""), "the mixins stay listed: " + listed);
			ClassNode both = new ClassNode();
			new ClassReader(overlay.read("com/apple/mixin/TooltipBoth.class")).accept(both, 0);
			assertEquals(List.of("fabricated$TooltipBoth$atHead"), both.methods.stream().map(m -> m.name).toList(), "the injector at a call is left out");
			ClassNode mixin = new ClassNode();
			new ClassReader(overlay.read("com/apple/mixin/TooltipMixin.class")).accept(mixin, 0);
			assertEquals(List.of(), mixin.interfaces, "it no longer extends its target");
			assertNull(mixin.signature);
			MethodNode handler = mixin.methods.stream().filter(m -> m.name.equals("fabricated$TooltipMixin$onCreate")).findFirst().orElseThrow();
			assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, handler.access);
			assertNull(handler.visibleAnnotations == null ? null : Annotations.find(handler.visibleAnnotations, "Lorg/spongepowered/asm/mixin/injection/Inject;"));
			assertNull(handler.invisibleAnnotations == null ? null : Annotations.find(handler.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/injection/Inject;"));
			AnnotationNode marker = Annotations.find(handler.invisibleAnnotations, "Lio/github/cmdralph/fabricated/InterfaceInject;");
			assertEquals(List.of("net/minecraft/client/Tooltip create (Ljava/lang/Object;)Lnet/minecraft/client/Tooltip;"), Annotations.value(marker, "targets"));
			assertEquals(List.of("HEAD"), Annotations.value(marker, "points"));
			assertEquals(Boolean.TRUE, Annotations.value(marker, "cancellable"));
		}

		// Where Mixin applies injectors in interface mixins (from 0.8.6), they stay as the mod has them.
		Pipeline.Result neoForge = Pipeline.run(input(game, List.of(apple), Namespace.OFFICIAL, gameClasses, null, List.of()));
		try (SafeArchive overlay = SafeArchive.open(neoForge.mods().getFirst().translated().overlay(), ArchiveLimits.DEFAULTS)) {
			assertNull(overlay.read("com/apple/mixin/TooltipMixin.class"), neoForge.report().render());
			assertNull(overlay.read("com/apple/mixin/TooltipBoth.class"), neoForge.report().render());
		}
	}

	@Test
	void aFabricJarsDataLosesItsNeoForgeBuildsConditionsAndModifiers(@TempDir Path game) throws Exception {
		// DATA-1: Tectonic's Fabric jar carries its NeoForge build's conditions, whose type only that build registers.
		String modifier = "data/tectonic/lithostitched/worldgen_modifier/lanterns.json";
		String plain = "data/tectonic/worldgen/noise/plain.json";
		// PACK-4: and its built-in pack's pack.mcmeta has overlays gated by them.
		String builtinMetadata = "resourcepacks/tectonic/pack.mcmeta";
		// MODIFIER-1: YUNG's Extras' Fabric jar carries its NeoForge build's biome modifiers, adding what its Fabric code adds.
		String biomeModifier = "data/tectonic/neoforge/biome_modifier/desert_decorations.json";
		String structureModifier = "data/tectonic/neoforge/structure_modifier/swamp_structures.json";
		// MODIFIER-2: and its Forge build's, which Forge 47 to 66 apply from any jar, gated by Forge's conditions or in a
		// pack overlay's folder alike; MODIFIER-3: two of them, which must not turn into one object.
		String forgeBiomeModifier = "data/tectonic/forge/biome_modifier/desert_decorations.json";
		String otherForgeBiomeModifier = "increased_height/data/tectonic/forge/biome_modifier/vanilla_desert_well_removal.json";
		String forgeStructureModifier = "data/tectonic/forge/structure_modifier/swamp_structures.json";
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path tectonic = TestJars.create().fabricModJson(TestJars.modJson("tectonic", "1.0", ""))
				.text(biomeModifier, "{\"type\": \"neoforge:add_features\", \"biomes\": \"#c:is_desert\", \"features\": \"tectonic:bush\", \"step\": \"vegetal_decoration\"}")
				.text(structureModifier, "{\"type\": \"neoforge:add_spawns\"}")
				.text(forgeBiomeModifier, "{\"forge:conditions\": [{\"type\": \"forge:mod_loaded\", \"modid\": \"tectonic\"}], \"type\": \"forge:add_features\","
						+ " \"biomes\": \"#forge:is_desert\", \"features\": \"tectonic:bush\", \"step\": \"vegetal_decoration\"}")
				.text(otherForgeBiomeModifier, "{\"type\": \"forge:remove_features\", \"biomes\": \"#forge:is_desert\", \"features\": \"minecraft:desert_well\","
						+ " \"steps\": \"surface_structures\"}")
				.text(forgeStructureModifier, "{\"type\": \"forge:none\"}")
				.text(modifier, "{\"neoforge:conditions\": [{\"type\": \"tectonic:config\"}], \"type\": \"lithostitched:add_features\"}")
				.text(plain, "{\"amplitudes\": [1.0]}")
				.text(builtinMetadata, "{\"pack\": {\"pack_format\": 48}, \"neoforge:overlays\": {\"entries\": [{\"directory\": \"increased_height\","
						+ " \"neoforge:conditions\": [{\"type\": \"tectonic:config\"}]}]}}")
				.write(mods.resolve("tectonic.jar"));
		Pipeline.Input firstLaunch = input(game, List.of(tectonic), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null);
		Pipeline.Result result = Pipeline.run(firstLaunch);

		try (SafeArchive overlay = SafeArchive.open(result.mods().getFirst().translated().overlay(), ArchiveLimits.DEFAULTS)) {
			assertEquals(Map.of("type", "lithostitched:add_features"), Json.parseObject(overlay.readText(modifier)));
			assertNull(overlay.read(plain), "a file without them is the jar's own");
			assertEquals(Json.parseObject("{\"pack\": {\"pack_format\": 48}}"), Json.parseObject(overlay.readText(builtinMetadata)));
			// As modifiers every NeoForge version has, over nothing: 26.x has no neoforge:none biome modifier.
			assertEquals(Map.of("type", "neoforge:add_features", "biomes", List.of(), "features", List.of(), "step", "vegetal_decoration"),
					Json.parseObject(overlay.readText(biomeModifier)), "the NeoForge build's biome modifier is turned off");
			assertEquals(Map.of("type", "neoforge:clear_spawns", "structures", List.of()), Json.parseObject(overlay.readText(structureModifier)));
			// As Forge's modifier every Forge version has, over nothing, each file one: Forge's none is one instance, which a
			// registry takes once (Forge 47's Fabric API and 26.1.2's game refused the second); the mod's Fabric code alone
			// adds its features.
			Map<String, Object> off = Map.of("type", "forge:add_features", "biomes", List.of(), "features", List.of(), "step", "vegetal_decoration");
			assertEquals(off, Json.parseObject(overlay.readText(forgeBiomeModifier)), "the Forge build's biome modifier is turned off");
			assertEquals(off, Json.parseObject(overlay.readText(otherForgeBiomeModifier)), "and its second one, in an overlay's folder");
			assertNull(overlay.read(forgeStructureModifier), "Forge has no structure modifier but its one none: the jar's stays");
		}
		// PROVIDER-3: its container shows its files as NeoForge reads them, to mods reading them through it (Forgified
		// Fabric API's resource loader), from a copy of its jar: the jar itself is left as it is.
		assertContainerShowsTheRewrites(result, builtinMetadata, plain);
		try (SafeArchive original = SafeArchive.open(tectonic, ArchiveLimits.DEFAULTS)) {
			assertTrue(original.readText(builtinMetadata).contains("neoforge:overlays"), "the mod's jar is never changed");
		}
		firstLaunch.cache().finish(5);
		Pipeline.Result again = Pipeline.run(input(game, List.of(tectonic), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));
		assertTrue(again.mods().getFirst().translated().cacheHit());
		assertContainerShowsTheRewrites(again, builtinMetadata, plain);
	}

	private static void assertContainerShowsTheRewrites(Pipeline.Result result, String builtinMetadata, String plain) throws Exception {
		ModContainerImpl container = result.containers().get("tectonic");
		assertEquals(Json.parseObject("{\"pack\": {\"pack_format\": 48}}"),
				Json.parseObject(Files.readString(container.findPath(builtinMetadata).orElseThrow())));
		assertEquals(Json.parseObject("{\"amplitudes\": [1.0]}"), Json.parseObject(Files.readString(container.findPath(plain).orElseThrow())));
		assertTrue(container.findPath("META-INF/neoforge.mods.toml").isEmpty() && container.findPath("META-INF/mods.toml").isEmpty(),
				"not the files translation generated for the platform");
	}

	@Test
	void aFabricJarsNeoForgeBuildsLootModifiersAreTurnedOffUnlessItUsesPortingLib(@TempDir Path game) throws Exception {
		// CORPUS-23: Artifacts' Fabric jar carries its NeoForge build's global loot modifiers, whose types only that build
		// registers; NeoForge reads them from any jar, Fabric reads none.
		String modifier = "data/artifacts/loot_modifiers/inject/archaeology/desert_pyramid.json";
		String list = "data/neoforge/loot_modifiers/global_loot_modifiers.json";
		String table = "data/artifacts/loot_table/chests/cellar.json";
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path artifacts = TestJars.create().fabricModJson(TestJars.modJson("artifacts", "1.0", ""))
				.text(modifier, "{\"type\": \"artifacts:replace_with_lootable\", \"conditions\": []}")
				.text(list, "{\"replace\": false, \"entries\": [\"artifacts:inject/archaeology/desert_pyramid\"]}")
				.text(table, "{\"pools\": []}")
				.write(mods.resolve("artifacts.jar"));
		// Porting Lib reads them on Fabric, from the same folder: a mod using it keeps its own.
		String kept = "data/create/loot_modifiers/crushing_wheel.json";
		Path create = TestJars.create().fabricModJson(TestJars.modJson("create", "1.0", "\"depends\":{\"porting_lib_loot\":\"*\"}"))
				.text(kept, "{\"type\": \"create:crushing\"}")
				.write(mods.resolve("create.jar"));
		Path portingLib = TestJars.create().fabricModJson(TestJars.modJson("porting_lib_loot", "1.0", "")).write(mods.resolve("porting_lib_loot.jar"));
		Pipeline.Result result = Pipeline.run(input(game, List.of(artifacts, create, portingLib), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));

		Pipeline.LoadedMod translated = result.mods().stream().filter(m -> m.translated().candidate().id().equals("artifacts")).findFirst().orElseThrow();
		try (SafeArchive overlay = SafeArchive.open(translated.translated().overlay(), ArchiveLimits.DEFAULTS)) {
			Object never = List.of(Map.of("type", "neoforge:never"));
			assertEquals(Map.of("neoforge:conditions", never), Json.parseObject(overlay.readText(modifier)), "skipped on every NeoForge version");
			// 1.21.11 reads the list itself and names no modifier of the mod's; 26.x skips it as a modifier of its own.
			assertEquals(Map.of("neoforge:conditions", never, "replace", false, "entries", List.of()), Json.parseObject(overlay.readText(list)));
			assertNull(overlay.read(table), "a loot table is the jar's own");
		}
		assertTrue(translated.translated().notes().stream().anyMatch(n -> n.startsWith("2 NeoForge global loot modifier file(s)")),
				translated.translated().notes().toString());
		Pipeline.LoadedMod withPortingLib = result.mods().stream().filter(m -> m.translated().candidate().id().equals("create")).findFirst().orElseThrow();
		try (SafeArchive overlay = SafeArchive.open(withPortingLib.translated().overlay(), ArchiveLimits.DEFAULTS)) {
			assertNull(overlay.read(kept), "Porting Lib's modifier is the jar's own");
		}
	}

	/**
	 * CORPUS-98: Forge reads only the lists in {@code forge}'s namespace, decodes only the modifiers they name, and reads no
	 * {@code neoforge:conditions}; Porting Lib reads those lists on Fabric for 1.20.1 mods, which may not name it as a
	 * dependency (Farmer's Delight's Fabric jar lists its modifiers there). On Forge a Fabric jar's lists and modifiers are
	 * its own, where the stubs left Forge 47's manager and Porting Lib's a list without {@code replace}.
	 */
	@Test
	void onForgeAFabricJarsLootModifiersAreItsOwn(@TempDir Path game) throws Exception {
		String modifier = "data/farmersdelight/loot_modifiers/a_modifier.json";
		String forgeList = "data/forge/loot_modifiers/global_loot_modifiers.json";
		String neoForgeList = "data/neoforge/loot_modifiers/global_loot_modifiers.json";
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path farmersDelight = TestJars.create().fabricModJson(TestJars.modJson("farmersdelight", "1.0", ""))
				.text(modifier, "{\"type\": \"farmersdelight:a_type\", \"conditions\": []}")
				.text(forgeList, "{\"replace\": false, \"entries\": [\"farmersdelight:a_modifier\"]}")
				.text(neoForgeList, "{\"replace\": false, \"entries\": [\"farmersdelight:a_modifier\"]}")
				.write(mods.resolve("farmersdelight.jar"));

		Pipeline.Result result = Pipeline.run(withDescriptor(input(game, List.of(farmersDelight), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null),
				PlatformDescriptor.FORGE));

		Pipeline.LoadedMod translated = result.mods().stream().filter(m -> m.translated().candidate().id().equals("farmersdelight")).findFirst().orElseThrow();
		try (SafeArchive overlay = SafeArchive.open(translated.translated().overlay(), ArchiveLimits.DEFAULTS)) {
			for (String name : List.of(modifier, forgeList, neoForgeList)) assertNull(overlay.read(name), name + " is the jar's own");
		}
		assertTrue(translated.translated().notes().stream().noneMatch(n -> n.contains("global loot modifier")), translated.translated().notes().toString());
	}

	@Test
	void aModNeedingANewerMixinExtrasIsDisabledUnlessTheReleasesSinceOnlyFixedBugs(@TempDir Path game) throws Exception {
		// MIXEXT-1: Freecam's 1.21.11 build asks for MixinExtras 0.5.5, NeoForge 21.11 ships 0.5.3 (the test input's), and
		// MixinExtras stopped the game reading the config. A config may take the setting from its parent. MIXEXT-2: 0.5.4 and
		// 0.5.5 only fixed bugs, so such a config asks for 0.5.3 instead, unless a @Redirect of its package has several
		// injection points, which only 0.5.5 reads. A release after those is not known, and still disables the mod.
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path fixesOnly = TestJars.create().fabricModJson(TestJars.modJson("freecam", "1.0", "\"mixins\":[\"freecam.mixins.json\"]"))
				.text("freecam.mixins.json", "{\"parent\":\"freecam-common.mixins.json\",\"package\":\"com.freecam.mixin\",\"mixins\":[]}")
				.text("freecam-common.mixins.json", "{\"package\":\"com.freecam.mixin\",\"mixins\":[],\"mixinextras\":{\"minVersion\":\"0.5.5\"}}")
				.write(mods.resolve("freecam.jar"));
		Path unknown = TestJars.create().fabricModJson(TestJars.modJson("newer", "1.0", "\"mixins\":[\"newer.mixins.json\"]"))
				.text("newer.mixins.json", "{\"package\":\"com.newer.mixin\",\"mixins\":[],\"mixinextras\":{\"minVersion\":\"0.6.0\"}}")
				.write(mods.resolve("newer.jar"));
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/points/mixin/Redirecting", null, "java/lang/Object", null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "redirect", "(L" + TARGET + ";)Z", null, null);
		AnnotationVisitor redirect = mv.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Redirect;", true);
		AnnotationVisitor ats = redirect.visitArray("at");
		for (String called : List.of("isDebug", "isDemo")) {
			AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
			at.visit("value", "INVOKE");
			at.visit("target", "L" + TARGET + ";" + called + "()Z");
			at.visitEnd();
		}
		ats.visitEnd();
		redirect.visitEnd();
		mv.visitEnd();
		cw.visitEnd();
		Path severalPoints = TestJars.create().fabricModJson(TestJars.modJson("points", "1.0", "\"mixins\":[\"points.mixins.json\"]"))
				.text("points.mixins.json", "{\"package\":\"com.points.mixin\",\"mixins\":[],\"mixinextras\":{\"minVersion\":\"0.5.5\"}}")
				.bytes("com/points/mixin/Redirecting.class", cw.toByteArray())
				.write(mods.resolve("points.jar"));
		Path met = TestJars.create().fabricModJson(TestJars.modJson("older", "1.0", "\"mixins\":[\"older.mixins.json\"]"))
				.text("older.mixins.json", "{\"package\":\"com.older.mixin\",\"mixins\":[],\"mixinextras\":{\"minVersion\":\"0.5.0\"}}")
				.write(mods.resolve("older.jar"));
		Pipeline.Result result = Pipeline.run(input(game, List.of(fixesOnly, unknown, severalPoints, met), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));

		assertEquals(List.of("freecam", "older"), result.mods().stream().map(m -> m.translated().candidate().id()).sorted().toList(), result.report().render());
		try (SafeArchive overlay = SafeArchive.open(result.mods().stream().filter(m -> m.translated().candidate().id().equals("freecam")).findFirst()
				.orElseThrow().translated().overlay(), ArchiveLimits.DEFAULTS)) {
			assertEquals(Map.of("minVersion", "0.5.3"), Json.parseObject(overlay.readText("freecam.mixins.json")).get("mixinextras"),
					"the config asks for NeoForge's MixinExtras, over its parent's setting");
		}
		assertTrue(result.report().render().contains("freecam: Its mixin config freecam.mixins.json asks for MixinExtras 0.5.5; it runs with NeoForge's 0.5.3"),
				result.report().render());
		Report.ModRow newer = result.report().mods().stream().filter(r -> r.id().equals("newer")).findFirst().orElseThrow();
		assertEquals(Report.Status.DISABLED, newer.status());
		assertTrue(newer.detail().contains("requires MixinExtras 0.6.0 or later; this NeoForge ships MixinExtras 0.5.3"), newer.detail());
		Report.ModRow points = result.report().mods().stream().filter(r -> r.id().equals("points")).findFirst().orElseThrow();
		assertEquals(Report.Status.DISABLED, points.status());
		assertTrue(points.detail().contains("com.points.mixin.Redirecting has a @Redirect at several injection points, which MixinExtras reads from 0.5.5"),
				points.detail());
	}

	@Test
	void aMixinConfigDeclaresTheLastCompatibilityLevelTheRunningMixinKnows(@TempDir Path game) throws Exception {
		// LEVEL-1: Fabric API for 26.x declares JAVA_25; Forge 64 to 66 run Mixin 0.8.7, whose levels end at JAVA_21, and
		// Mixin stopped the game reading the config. A level that Mixin knows stays, and Fabric's Mixin isn't limited.
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path rules = TestJars.create().fabricModJson(TestJars.modJson("rules", "1.0", "\"mixins\":[\"rules.mixins.json\",\"older.mixins.json\"]"))
				.text("rules.mixins.json", "{\"package\":\"com.rules.mixin\",\"compatibilityLevel\":\"JAVA_25\",\"mixins\":[]}")
				.text("older.mixins.json", "{\"package\":\"com.rules.older\",\"compatibilityLevel\":\"JAVA_17\",\"mixins\":[]}")
				.write(mods.resolve("rules.jar"));
		Pipeline.Result forge = Pipeline.run(withDescriptor(input(game, List.of(rules), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null, List.of(),
				InstalledClasses.NONE, ClassBytesSource.EMPTY, "0.8.7+Jenkins-b123.git-0", "0.8.7"), PlatformDescriptor.FORGE));

		assertEquals(List.of("rules"), forge.mods().stream().map(m -> m.translated().candidate().id()).toList(), forge.report().render());
		try (SafeArchive overlay = SafeArchive.open(forge.mods().getFirst().translated().overlay(), ArchiveLimits.DEFAULTS)) {
			assertEquals("JAVA_21", Json.parseObject(overlay.readText("rules.mixins.json")).get("compatibilityLevel"));
			assertNull(overlay.read("older.mixins.json"), "a level that Mixin knows is the jar's own");
		}
		assertTrue(forge.report().render().contains("rules: Its mixin config rules.mixins.json declares compatibility level JAVA_25, which Forge's"
				+ " Mixin 0.8.7+Jenkins-b123.git-0 on Minecraft 26.2 can't set; it loads at JAVA_21"), forge.report().render());

		Path neoForgeGame = Files.createDirectories(game.resolve("neoforge"));
		Pipeline.Result neoForge = Pipeline.run(input(neoForgeGame, List.of(rules), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));
		try (SafeArchive overlay = SafeArchive.open(neoForge.mods().getFirst().translated().overlay(), ArchiveLimits.DEFAULTS)) {
			assertNull(overlay.read("rules.mixins.json"), "Fabric's Mixin reads the config as the jar has it");
		}
	}

	@Test
	void aModWhoseMixinOnlyGivesALibraryClassAnInterfaceLoadsWithABridge(@TempDir Path game) throws Exception {
		// LIB-1: Carpet's mixins give Brigadier's CommandNode an interface removing a child; NeoForge loads Brigadier without
		// mixin support, and pre-flight disabled Carpet. The mixin into a library class that does more is still judged.
		Map<String, byte[]> classes = TestSources.compile(game.resolve("build"), TestSources.MIXIN_ANNOTATIONS, Map.of(
				"lib/Node.java", "package lib; public class Node { private final java.util.Map<String, Node> children = new java.util.HashMap<>(); }",
				"mod/NodeAccess.java", "package mod; public interface NodeAccess { void removeChild(String name); }",
				"mod/mixin/NodeMixin.java", """
						package mod.mixin;
						import org.spongepowered.asm.mixin.*;
						@Mixin(value = lib.Node.class, remap = false)
						public class NodeMixin implements mod.NodeAccess {
							@Shadow @Final private java.util.Map<String, lib.Node> children;
							@Override public void removeChild(String name) { children.remove(name); }
						}""",
				"mod/Commands.java", "package mod; public class Commands { public static void drop(Object node) { ((NodeAccess) node).removeChild(\"tick\"); } }"));
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path carpet = TestJars.create().fabricModJson(TestJars.modJson("carpet", "1.0", "\"mixins\":[\"carpet.mixins.json\"]"))
				.text("carpet.mixins.json", "{\"required\":true,\"package\":\"mod.mixin\",\"mixins\":[\"NodeMixin\"]}")
				.bytes("mod/NodeAccess.class", classes.get("mod/NodeAccess"))
				.bytes("mod/mixin/NodeMixin.class", classes.get("mod/mixin/NodeMixin"))
				.bytes("mod/Commands.class", classes.get("mod/Commands"))
				.write(mods.resolve("carpet.jar"));
		ClassBytesSource libraries = name -> name.startsWith("lib/") ? classes.get(name) : null;
		Pipeline.Result result = Pipeline.run(input(game, List.of(carpet), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null, List.of(),
				InstalledClasses.NONE, libraries));

		assertEquals(List.of("carpet"), result.mods().stream().map(m -> m.translated().candidate().id()).toList(), result.report().render());
		assertTrue(result.report().render().contains("are served by a generated class"), result.report().render());
		try (SafeArchive overlay = SafeArchive.open(result.mods().getFirst().translated().overlay(), ArchiveLimits.DEFAULTS)) {
			assertFalse(new String(overlay.read("carpet.mixins.json"), StandardCharsets.UTF_8).contains("NodeMixin"), "Mixin doesn't apply it");
			assertNotNull(overlay.read("mod/NodeAccess$FabricatedBridge.class"));
			String commands = new String(overlay.read("mod/Commands.class"), StandardCharsets.ISO_8859_1);
			assertTrue(commands.contains("mod/NodeAccess$FabricatedBridge"), "the mod's call goes to the bridge");
		}
	}

	@Test
	void aModWithAnAccessorMixinIntoALibraryClassLoadsWithABridge(@TempDir Path game) throws Exception {
		// LIB-1b: owo-lib's accessor of JOML's Matrix3x2fStack; pre-flight disabled owo, as Mixin never applies it there.
		Map<String, byte[]> classes = TestSources.compile(game.resolve("build"), TestSources.MIXIN_ANNOTATIONS, Map.of(
				"lib/Stack.java", "package lib; public class Stack { private int curr; }",
				"mod/mixin/StackAccessor.java", """
						package mod.mixin;
						@org.spongepowered.asm.mixin.Mixin(lib.Stack.class)
						public interface StackAccessor { @org.spongepowered.asm.mixin.gen.Accessor("curr") int mod$getCurr(); }""",
				"mod/Graphics.java", "package mod; public class Graphics { public static int depth(lib.Stack s) { return ((mod.mixin.StackAccessor) s).mod$getCurr(); } }"));
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path owo = TestJars.create().fabricModJson(TestJars.modJson("owo", "1.0", "\"mixins\":[\"owo.mixins.json\"]"))
				.text("owo.mixins.json", "{\"required\":true,\"package\":\"mod.mixin\",\"mixins\":[\"StackAccessor\"]}")
				.bytes("mod/mixin/StackAccessor.class", classes.get("mod/mixin/StackAccessor"))
				.bytes("mod/Graphics.class", classes.get("mod/Graphics"))
				.write(mods.resolve("owo.jar"));
		ClassBytesSource libraries = name -> name.startsWith("lib/") ? classes.get(name) : null;
		Pipeline.Result result = Pipeline.run(input(game, List.of(owo), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null, List.of(),
				InstalledClasses.NONE, libraries));

		assertEquals(List.of("owo"), result.mods().stream().map(m -> m.translated().candidate().id()).toList(), result.report().render());
		assertTrue(result.report().render().contains("are served by a generated class"), result.report().render());
		try (SafeArchive overlay = SafeArchive.open(result.mods().getFirst().translated().overlay(), ArchiveLimits.DEFAULTS)) {
			byte[] config = overlay.read("owo.mixins.json");
			assertTrue(config == null || new String(config, StandardCharsets.UTF_8).contains("StackAccessor"), "Mixin loads the accessor as the mod's interface");
			assertNotNull(overlay.read("mod/StackAccessor$FabricatedBridge.class"), "outside the mixin package");
			String graphics = new String(overlay.read("mod/Graphics.class"), StandardCharsets.ISO_8859_1);
			assertTrue(graphics.contains("mod/StackAccessor$FabricatedBridge"), "the mod's call goes to the bridge");
		}
	}

	@Test
	void aModsClassesLoseTheOtherSidesMembersAsTheyAreTranslatedWithTheirFrames(@TempDir Path game) throws Exception {
		// ENV-1 (CORPUS-59): stripped at class load, Cobblemon's cooking pot screen had its frames computed anew by ModLauncher,
		// which loads the classes they name: JEI's runtime, not installed, and the client's initializer failed. The pipeline
		// translates for the dedicated server here.
		Map<String, byte[]> classes = TestSources.compile(game.resolve("build"), Map.of(
				"net/fabricmc/api/EnvType.java", "package net.fabricmc.api; public enum EnvType { CLIENT, SERVER }",
				"net/fabricmc/api/Environment.java", "package net.fabricmc.api; public @interface Environment { EnvType value(); }",
				"optional/Runtime.java", "package optional; public interface Runtime { static Runtime get() { return null; } }",
				"mod/Screen.java", """
						package mod;
						public class Screen {
							@net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT) public static void render() { }
							public static Object shown(boolean jei) { Object shown = jei ? optional.Runtime.get() : null; return shown == null ? "none" : shown; }
						}""",
				"mod/Plain.java", "package mod; public class Plain { public static String name() { return \"plain\"; } }"));
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path jar = TestJars.create().fabricModJson(TestJars.modJson("pots", "1.0", ""))
				.bytes("mod/Screen.class", classes.get("mod/Screen"))
				.bytes("mod/Plain.class", classes.get("mod/Plain"))
				.write(mods.resolve("pots.jar"));
		Pipeline.Result result = Pipeline.run(input(game, List.of(jar), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));

		assertEquals(List.of("pots"), result.mods().stream().map(m -> m.translated().candidate().id()).toList(), result.report().render());
		assertTrue(result.mods().getFirst().translated().environmentClasses().isEmpty(), "nothing is left to strip at class load");
		assertTrue(result.environmentClasses().isEmpty());
		try (SafeArchive overlay = SafeArchive.open(result.mods().getFirst().translated().overlay(), ArchiveLimits.DEFAULTS)) {
			byte[] screen = overlay.read("mod/Screen.class");
			assertNotNull(screen, "the stripped class shadows the jar's");
			ClassNode stripped = new ClassNode();
			new ClassReader(screen).accept(stripped, 0);
			assertEquals(List.of("<init>", "shown"), stripped.methods.stream().map(m -> m.name).toList(), "the client's method is gone");
			assertEquals(frames(classes.get("mod/Screen"), "shown"), frames(screen, "shown"), "the compiler's frames, none computed anew");
			assertNull(overlay.read("mod/Plain.class"), "a class without the annotations is the jar's");
		}
	}

	/** The frames of a class's method, as text: what a frame computation would replace. */
	private static List<String> frames(byte[] data, String method) {
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, 0);
		List<String> frames = new ArrayList<>();
		for (var insn : node.methods.stream().filter(m -> m.name.equals(method)).findFirst().orElseThrow().instructions) {
			if (insn instanceof org.objectweb.asm.tree.FrameNode frame) frames.add(frame.type + " " + frame.local + " " + frame.stack);
		}
		assertFalse(frames.isEmpty(), "the method has frames");
		return frames;
	}

	@Test
	void aModWhoseMixinGivesALibraryClassAnotherLibrarysInterfaceLoadsWithAnAdapter(@TempDir Path game) throws Exception {
		// LIB-2: adventure-platform-fabric makes authlib's GameProfile an Adventure Identity, an interface of another jar
		// with default methods, and casts a player's profile to it; pre-flight disabled it, and with it OneConfig's chat event.
		Map<String, byte[]> classes = TestSources.compile(game.resolve("build"), TestSources.MIXIN_ANNOTATIONS, Map.of(
				"lib/Profile.java", "package lib; public class Profile { private final java.util.UUID id = java.util.UUID.randomUUID(); public java.util.UUID getId() { return id; } }",
				"api/Identity.java", "package api; public interface Identity { java.util.UUID uuid(); default Identity identity() { return this; } }",
				"mod/mixin/ProfileMixin.java", """
						package mod.mixin;
						import org.spongepowered.asm.mixin.*;
						@Mixin(value = lib.Profile.class, remap = false)
						public abstract class ProfileMixin implements api.Identity {
							@Shadow public abstract java.util.UUID getId();
							@Override public java.util.UUID uuid() { return getId(); }
						}""",
				"mod/Player.java", "package mod; public class Player { public static api.Identity of(lib.Profile p) { return (api.Identity) (Object) p; } }"));
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path api = TestJars.create().fabricModJson(TestJars.modJson("adventure-api", "1.0", ""))
				.bytes("api/Identity.class", classes.get("api/Identity"))
				.write(mods.resolve("adventure-api.jar"));
		Path platform = TestJars.create().fabricModJson(TestJars.modJson("adventure-platform", "1.0", "\"mixins\":[\"adventure.mixins.json\"]"))
				.text("adventure.mixins.json", "{\"required\":true,\"package\":\"mod.mixin\",\"mixins\":[\"ProfileMixin\"]}")
				.bytes("mod/mixin/ProfileMixin.class", classes.get("mod/mixin/ProfileMixin"))
				.bytes("mod/Player.class", classes.get("mod/Player"))
				.write(mods.resolve("adventure-platform.jar"));
		// The libraries NeoForge loads without mixin support, and the JDK, as its platform class loader serves them.
		ClassBytesSource libraries = name -> {
			if (name.startsWith("lib/")) return classes.get(name);
			if (!name.startsWith("java/")) return null;
			try (var in = ClassLoader.getSystemResourceAsStream(name + ".class")) {
				return in == null ? null : in.readAllBytes();
			}
		};
		Pipeline.Result result = Pipeline.run(input(game, List.of(api, platform), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null, List.of(),
				InstalledClasses.NONE, libraries));

		assertEquals(Set.of("adventure-api", "adventure-platform"), result.mods().stream().map(m -> m.translated().candidate().id()).collect(Collectors.toSet()),
				result.report().render());
		assertTrue(result.report().render().contains("are served by a generated adapter"), result.report().render());
		Pipeline.LoadedMod loaded = result.mods().stream().filter(m -> m.translated().candidate().id().equals("adventure-platform")).findFirst().orElseThrow();
		try (SafeArchive overlay = SafeArchive.open(loaded.translated().overlay(), ArchiveLimits.DEFAULTS)) {
			assertFalse(new String(overlay.read("adventure.mixins.json"), StandardCharsets.UTF_8).contains("ProfileMixin"), "Mixin doesn't apply it");
			assertNotNull(overlay.read("mod/ProfileMixin$FabricatedAdapter.class"), "outside the mixin package");
			String player = new String(overlay.read("mod/Player.class"), StandardCharsets.ISO_8859_1);
			assertTrue(player.contains("mod/ProfileMixin$FabricatedAdapter"), "the mod's cast wraps the profile");
		}
	}

	@Test
	void aBuildForIntermediaryNamesIsDisabledOnAnUnobfuscatedVersion(@TempDir Path game) throws Exception {
		// OBF-1: Modrinth lists Do a Barrel Roll's 1.21.11 build for 26.2; its mixins name intermediary classes.
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path old = TestJars.create().fabricModJson(TestJars.modJson("barrel", "1.0", "\"mixins\":[\"barrel.mixins.json\"]"))
				.text("barrel.mixins.json", "{\"required\":true,\"package\":\"com.barrel.mixin\",\"client\":[],\"mixins\":[\"CameraMixin\"]}")
				.bytes("com/barrel/mixin/CameraMixin.class", mixinClass("com/barrel/mixin/CameraMixin", "net/minecraft/class_4184", "method_19325"))
				.write(mods.resolve("barrel.jar"));
		Pipeline.Result result = Pipeline.run(input(game, List.of(old), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));

		assertTrue(result.mods().isEmpty(), result.report().render());
		Report.ModRow row = result.report().mods().stream().filter(r -> r.id().equals("barrel")).findFirst().orElseThrow();
		assertEquals(Report.Status.DISABLED, row.status());
		assertTrue(row.detail().contains("obfuscated names") && row.detail().contains("net.minecraft.class_4184"), row.detail());
	}

	@Test
	void aModWhoseMixinsAreOfANewerJavaThanTheGamesIsDisabled(@TempDir Path game) throws Exception {
		// JAVA-1: Ledger 1.3.18's classes are Java 21's and its depends names no Java; Mixin made LivingEntity a Java 21
		// class, which Forge 47's Java 17 couldn't load. A mixin of the running Java loads as before.
		int running = Runtime.version().feature();
		byte[] newer = mixinClass("com/ledger/mixin/LivingEntityMixin", TARGET, "tick");
		newer[6] = (byte) ((running + 45) >> 8);
		newer[7] = (byte) (running + 45);
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path ledger = TestJars.create().fabricModJson(TestJars.modJson("ledger", "1.0", "\"mixins\":[\"ledger.mixins.json\"]"))
				.text("ledger.mixins.json", "{\"required\":true,\"package\":\"com.ledger.mixin\",\"mixins\":[\"LivingEntityMixin\"]}")
				.bytes("com/ledger/mixin/LivingEntityMixin.class", newer)
				.write(mods.resolve("ledger.jar"));
		Path current = TestJars.create().fabricModJson(TestJars.modJson("current", "1.0", "\"mixins\":[\"current.mixins.json\"]"))
				.text("current.mixins.json", "{\"required\":true,\"package\":\"com.current.mixin\",\"mixins\":[\"ThingMixin\"]}")
				.bytes("com/current/mixin/ThingMixin.class", mixinClass("com/current/mixin/ThingMixin", TARGET, "tick"))
				.write(mods.resolve("current.jar"));
		Pipeline.Result result = Pipeline.run(input(game, List.of(ledger, current), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));

		assertEquals(List.of("current"), result.mods().stream().map(m -> m.translated().candidate().id()).toList(), result.report().render());
		Report.ModRow row = result.report().mods().stream().filter(r -> r.id().equals("ledger")).findFirst().orElseThrow();
		assertEquals(Report.Status.DISABLED, row.status());
		assertTrue(row.detail().contains("com.ledger.mixin.LivingEntityMixin is a class of Java " + (running + 1))
				&& row.detail().contains("Java " + running + " can't load"), row.detail());
	}

	@Test
	void aFieldFabricatedsOwnMixinRestoresIsTheGamesForPreflight(@TempDir Path game) throws Exception {
		// PIP-1: Fabricated's mixin gives GuiRenderer back vanilla's map of picture-in-picture renderers, which NeoForge
		// replaced; malilib shadows it. Fabricated's unique and shadowed fields aren't the game's.
		String own = "io/github/cmdralph/fabricated/neoforge/mixin/RestoringMixin";
		ClassWriter restoring = new ClassWriter(0);
		restoring.visit(Opcodes.V21, Opcodes.ACC_ABSTRACT, own, null, "java/lang/Object", null);
		AnnotationVisitor target = restoring.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false).visitArray("value");
		target.visit(null, Type.getObjectType(TARGET));
		target.visitEnd();
		restoring.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "renderers", "Ljava/util/Map;", null, null).visitEnd();
		restoring.visitField(Opcodes.ACC_PRIVATE, "fabricated$own", "I", null, null).visitAnnotation("Lorg/spongepowered/asm/mixin/Unique;", false).visitEnd();
		restoring.visitEnd();
		TestJars.create().text("fabricated.mixins.json", "{\"package\":\"io.github.cmdralph.fabricated.neoforge.mixin\",\"client\":[\"RestoringMixin\"]}")
				.bytes(own + ".class", restoring.toByteArray()).write(game.resolve("fabricated.jar"));

		Path mods = Files.createDirectories(game.resolve("mods"));
		Path user = TestJars.create().fabricModJson(TestJars.modJson("pip-user", "1.0", "\"mixins\":[\"pip.mixins.json\"]"))
				.text("pip.mixins.json", "{\"required\":true,\"package\":\"com.pip.mixin\",\"mixins\":[\"Uses\",\"UsesUnique\"]}")
				.bytes("com/pip/mixin/Uses.class", shadowing("com/pip/mixin/Uses", "renderers", "Ljava/util/Map;"))
				.bytes("com/pip/mixin/UsesUnique.class", shadowing("com/pip/mixin/UsesUnique", "fabricated$own", "I"))
				.write(mods.resolve("user.jar"));
		Pipeline.Result result = Pipeline.run(input(game, List.of(user), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));

		String findings = result.mods().stream().filter(m -> m.translated().candidate().id().equals("pip-user")).findFirst().orElseThrow()
				.translated().findings().toString();
		assertTrue(findings.contains("fabricated$own") && !findings.contains("renderers"), findings);
	}

	@Test
	void aFieldThePlatformRetypedIsReachedThroughFabricatedsBridge(@TempDir Path game, @TempDir Path without) throws Exception {
		// CORPUS-37: where Fabricated's in-game mod adds a bridge giving a retyped field's value as vanilla's type, a Fabric
		// mod's accessor of the field invokes it; without the bridge, pre-flight refuses the accessor as before.
		String own = "io/github/cmdralph/fabricated/forge/mixin/BridgingMixin";
		ClassWriter bridging = new ClassWriter(0);
		bridging.visit(Opcodes.V21, Opcodes.ACC_ABSTRACT, own, null, "java/lang/Object", null);
		AnnotationVisitor target = bridging.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false).visitArray("value");
		target.visit(null, Type.getObjectType(TARGET));
		target.visitEnd();
		bridging.visitField(Opcodes.ACC_PRIVATE, "count", "I", null, null).visitAnnotation("Lorg/spongepowered/asm/mixin/Shadow;", false).visitEnd();
		MethodVisitor bridge = bridging.visitMethod(Opcodes.ACC_PUBLIC, "fabricated$get$count", "()J", null, null);
		bridge.visitCode();
		bridge.visitVarInsn(Opcodes.ALOAD, 0);
		bridge.visitFieldInsn(Opcodes.GETFIELD, own, "count", "I");
		bridge.visitInsn(Opcodes.I2L);
		bridge.visitInsn(Opcodes.LRETURN);
		bridge.visitMaxs(2, 1);
		bridge.visitEnd();
		bridging.visitEnd();
		TestJars.create().text("fabricated.mixins.json", "{\"package\":\"io.github.cmdralph.fabricated.forge.mixin\",\"mixins\":[\"BridgingMixin\"]}")
				.bytes(own + ".class", bridging.toByteArray()).write(game.resolve("fabricated.jar"));

		ClassWriter accessor = new ClassWriter(0);
		accessor.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE, "com/count/mixin/CountAccessor", null,
				"java/lang/Object", null);
		AnnotationVisitor targets = accessor.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false).visitArray("value");
		targets.visit(null, Type.getObjectType(TARGET));
		targets.visitEnd();
		MethodVisitor getter = accessor.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "count$get", "()J", null, null);
		AnnotationVisitor field = getter.visitAnnotation("Lorg/spongepowered/asm/mixin/gen/Accessor;", true);
		field.visit("value", "count");
		field.visitEnd();
		getter.visitEnd();
		accessor.visitEnd();
		byte[] counter = TestJars.create().fabricModJson(TestJars.modJson("counter", "1.0", "\"mixins\":[\"counter.mixins.json\"]"))
				.text("counter.mixins.json", "{\"required\":true,\"package\":\"com.count.mixin\",\"mixins\":[\"CountAccessor\"]}")
				.bytes("com/count/mixin/CountAccessor.class", accessor.toByteArray())
				.toBytes();

		Path mod = Files.write(Files.createDirectories(game.resolve("mods")).resolve("counter.jar"), counter);
		Pipeline.Result result = Pipeline.run(input(game, List.of(mod), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));
		Pipeline.LoadedMod loaded = result.mods().stream().filter(m -> m.translated().candidate().id().equals("counter")).findFirst()
				.orElseThrow(() -> new AssertionError(result.report().render()));
		String findings = loaded.translated().findings().toString();
		assertTrue(findings.contains("fabricated$get$count") && findings.contains("CORPUS-37"), findings);

		TestJars.create().text("in-game.txt", "").write(without.resolve("fabricated.jar"));
		Path plain = Files.write(Files.createDirectories(without.resolve("mods")).resolve("counter.jar"), counter);
		Pipeline.Result refused = Pipeline.run(input(without, List.of(plain), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));
		assertTrue(refused.mods().stream().noneMatch(m -> m.translated().candidate().id().equals("counter")), refused.report().render());
	}

	/** A mixin into {@link #TARGET} shadowing one field. */
	private static byte[] shadowing(String name, String field, String desc) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, name, null, "java/lang/Object", null);
		AnnotationVisitor target = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false).visitArray("value");
		target.visit(null, Type.getObjectType(TARGET));
		target.visitEnd();
		cw.visitField(Opcodes.ACC_PRIVATE, field, desc, null, null).visitAnnotation("Lorg/spongepowered/asm/mixin/Shadow;", false).visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	@Test
	void anInterfaceAMixinAddsGetsItsSecondDefaultBridged(@TempDir Path game) throws Exception {
		// CORPUS-11: Entity Culling's interface mixin gives BlockEntityRenderer a second default of NeoForge's
		// getRenderBoundingBox. The bridge is kept in the cache.
		String box = "(Ljava/lang/Object;)Ljava/lang/String;";
		String renderer = "net/minecraft/client/Renderer";
		String extension = "net/neoforged/neoforge/client/Extension";
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path cull = TestJars.create().fabricModJson(TestJars.modJson("cull", "1.0", "\"mixins\":[\"cull.mixins.json\"]"))
				.text("cull.mixins.json", "{\"required\":true,\"package\":\"com.cull.mixin\",\"mixins\":[\"RendererMixin\"]}")
				.bytes("com/cull/FabricExtension.class", itf("com/cull/FabricExtension", List.of(), "box" + box, null))
				.bytes("com/cull/mixin/RendererMixin.class", itf("com/cull/mixin/RendererMixin", List.of("com/cull/FabricExtension"), null, renderer))
				.write(mods.resolve("cull.jar"));
		Map<String, byte[]> gameClasses = Map.of(TARGET, targetClass(), extension, itf(extension, List.of(), "box" + box, null),
				renderer, itf(renderer, List.of(extension), null, null));
		DefaultBridge expected = new DefaultBridge(renderer, extension, "box", box);
		for (boolean cached : new boolean[] {false, true}) {
			Pipeline.Result result = Pipeline.run(input(game, List.of(cull), Namespace.OFFICIAL, gameClasses, null));
			TranslatedMod translated = result.mods().getFirst().translated();
			assertEquals(cached, translated.cacheHit());
			assertEquals(List.of(expected), translated.defaultBridges(), result.report().render());
			assertEquals(List.of(expected), result.defaultBridges().get(renderer));
		}
	}

	@Test
	void aMixinsPrivateMethodHidingAnInheritedDefaultIsRenamed(@TempDir Path game) throws Exception {
		// HIDE-1: fabric-entity-events-v1 gives LivingEntity a private self(), which hid Forge's default self() from mobs.
		String living = "net/minecraft/world/Living";
		String extension = "net/neoforged/neoforge/common/LivingExtension";
		String self = "()L" + living + ";";
		String mixin = "com/hide/mixin/LivingMixin";
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path jar = TestJars.create().fabricModJson(TestJars.modJson("hide", "1.0", "\"mixins\":[\"hide.mixins.json\"]"))
				.text("hide.mixins.json", "{\"required\":true,\"package\":\"com.hide.mixin\",\"mixins\":[\"LivingMixin\"]}")
				.bytes(mixin + ".class", selfMixin(mixin, living, self))
				.write(mods.resolve("hide.jar"));
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, living, null, "java/lang/Object", new String[] {extension});
		cw.visitEnd();
		Map<String, byte[]> gameClasses = Map.of(TARGET, targetClass(), extension, itf(extension, List.of(), "self" + self, null), living, cw.toByteArray());

		Pipeline.Result result = Pipeline.run(input(game, List.of(jar), Namespace.OFFICIAL, gameClasses, null));
		TranslatedMod translated = result.mods().getFirst().translated();
		String renamed = "self$fabricated$" + Integer.toHexString(mixin.hashCode());
		assertTrue(translated.notes().stream().anyMatch(n -> n.contains(renamed) && n.contains("HIDE-1")), translated.notes().toString());
		try (SafeArchive overlay = SafeArchive.open(translated.overlay(), ArchiveLimits.DEFAULTS)) {
			ClassNode node = new ClassNode();
			new ClassReader(overlay.read(mixin + ".class")).accept(node, 0);
			assertEquals(List.of("<init>", renamed, "use"), node.methods.stream().map(m -> m.name).toList());
			MethodInsnNode call = (MethodInsnNode) Arrays.stream(node.methods.get(2).instructions.toArray())
					.filter(MethodInsnNode.class::isInstance).findFirst().orElseThrow();
			assertEquals(renamed, call.name, "the mixin's call of it follows");
		}
	}

	/** A mixin into {@code target} with a private {@code self()} ({@code self} its descriptor) and a method calling it. */
	private static byte[] selfMixin(String name, String target, String self) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, name, null, "java/lang/Object", null);
		AnnotationVisitor mixin = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor values = mixin.visitArray("value");
		values.visit(null, Type.getObjectType(target));
		values.visitEnd();
		mixin.visitEnd();
		MethodVisitor init = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "()V", null, null);
		init.visitCode();
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(1, 1);
		init.visitEnd();
		MethodVisitor helper = cw.visitMethod(Opcodes.ACC_PRIVATE, "self", self, null, null);
		helper.visitAnnotation("Lorg/spongepowered/asm/mixin/Unique;", false).visitEnd();
		helper.visitCode();
		helper.visitInsn(Opcodes.ACONST_NULL);
		helper.visitInsn(Opcodes.ARETURN);
		helper.visitMaxs(1, 1);
		helper.visitEnd();
		MethodVisitor use = cw.visitMethod(Opcodes.ACC_PRIVATE, "use", "()V", null, null);
		use.visitCode();
		use.visitVarInsn(Opcodes.ALOAD, 0);
		use.visitMethodInsn(Opcodes.INVOKESPECIAL, name, "self", self, false);
		use.visitInsn(Opcodes.POP);
		use.visitInsn(Opcodes.RETURN);
		use.visitMaxs(1, 1);
		use.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** An interface with these superinterfaces, a default method ({@code name + descriptor}) if any, and an {@code @Mixin} target if any. */
	private static byte[] itf(String name, List<String> interfaces, String defaultMethod, String mixinTarget) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, name, null, "java/lang/Object",
				interfaces.toArray(String[]::new));
		if (mixinTarget != null) {
			AnnotationVisitor mixin = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
			AnnotationVisitor values = mixin.visitArray("value");
			values.visit(null, Type.getObjectType(mixinTarget));
			values.visitEnd();
			mixin.visitEnd();
		}
		if (defaultMethod != null) {
			int paren = defaultMethod.indexOf('(');
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, defaultMethod.substring(0, paren), defaultMethod.substring(paren), null, null);
			mv.visitCode();
			mv.visitInsn(Opcodes.ACONST_NULL);
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitMaxs(1, 2);
			mv.visitEnd();
		}
		cw.visitEnd();
		return cw.toByteArray();
	}

	@Test
	void missingMappingsDisableModsWithAClearMessage(@TempDir Path game) throws Exception {
		Path jar = TestJars.create().fabricModJson(TestJars.modJson("old-mod", "1.0", "")).write(game.resolve("mods/old.jar"));
		Pipeline.Result result = Pipeline.run(input(game, List.of(jar), Namespace.INTERMEDIARY, Map.of(),
				(cache, path) -> {
					throw new io.github.cmdralph.fabricated.core.remap.MappingException("client-1.21.11-mappings.txt not found");
				}));
		assertTrue(result.mods().isEmpty());
		assertTrue(result.report().render().contains("mapping file is unavailable"));
	}

	@Test
	void obfuscatedVersionWithoutFabricModsStartsCleanly(@TempDir Path game) throws Exception {
		Pipeline.Result result = Pipeline.run(input(game, List.of(), Namespace.INTERMEDIARY, Map.of(),
				(cache, path) -> {
					throw new AssertionError("mappings are not needed without Fabric mods");
				}));
		assertTrue(result.mods().isEmpty());
	}

	@Test
	void fabricIdsSharingANeoForgeIdLoadOnce(@TempDir Path game) throws Exception {
		Path a = TestJars.create().fabricModJson(TestJars.modJson("same-id", "1.0", "")).write(game.resolve("mods/a.jar"));
		Path b = TestJars.create().fabricModJson(TestJars.modJson("same_id", "1.0", "")).write(game.resolve("mods/b.jar"));
		Path c = TestJars.create().fabricModJson(TestJars.modJson("cloth-config", "15.0.0", "")).write(game.resolve("mods/c.jar"));
		Pipeline.Result result = Pipeline.run(input(game, List.of(a, b, c), Namespace.OFFICIAL, Map.of(), null));
		assertEquals(1, result.mods().size());
		assertEquals("same_id", result.mods().get(0).translated().neoForgeId());
		String report = result.report().render();
		assertTrue(report.contains("NeoForge cannot load both"), report);
		assertTrue(report.contains("a NeoForge build of this mod is installed"), report);
	}

	/**
	 * Forgified Fabric API's version carries build metadata with a '+' of its own, which Fabric's SemVer refuses; it
	 * still satisfies a version range, and the ids its mods.toml provides ({@code fabric}) satisfy dependencies too.
	 */
	@Test
	void aNeoForgeBuildOfFabricApiSatisfiesRangesAndItsProvidedIds(@TempDir Path game) throws Exception {
		Path ranged = TestJars.create().fabricModJson(TestJars.modJson("ranged", "1.0", "\"depends\":{\"fabric-api\":\">=0.100.0\"}"))
				.write(game.resolve("mods/ranged.jar"));
		Path legacy = TestJars.create().fabricModJson(TestJars.modJson("legacy", "1.0", "\"depends\":{\"fabric\":\"*\"}"))
				.write(game.resolve("mods/legacy.jar"));
		List<Pipeline.NeoForgeMod> neoForge = List.of(new Pipeline.NeoForgeMod("fabric_api", "0.116.15+2.3.6+1.21.1", "Forgified Fabric API",
				Map.of(Pipeline.NeoForgeMod.PROVIDES, List.of("fabric", "fabric-api")), List.of()));
		Pipeline.Result result = Pipeline.run(input(game, List.of(ranged, legacy), Namespace.OFFICIAL, Map.of(), null, neoForge));
		assertEquals(Set.of("ranged", "legacy"), result.mods().stream().map(m -> m.translated().candidate().id()).collect(Collectors.toSet()),
				result.report().render());
		assertEquals("0.116.15+2.3.6+1.21.1", result.runtime().getModContainer("fabric-api").orElseThrow().getMetadata().getVersion().getFriendlyString());
	}

	/**
	 * A native module another mod provides (Forgified Fabric API's, Sodium's bundled fabric-api-base) is that mod's:
	 * Fabricated's copy stands down, the provider satisfies dependencies on it, and the official jar's copy is skipped.
	 */
	@Test
	void aNativeModuleAnotherModProvidesIsThatModsOnly(@TempDir Path game) throws Exception {
		Path user = TestJars.create().fabricModJson(TestJars.modJson("user", "1.0", "\"depends\":{\"fabric-api-base\":\">=0.4\"}"))
				.write(game.resolve("mods/user.jar"));
		Path official = TestJars.create().fabricModJson(TestJars.modJson("fabric-api-base", "0.4.42", "")).write(game.resolve("mods/base.jar"));
		List<Pipeline.NeoForgeMod> neoForge = List.of(new Pipeline.NeoForgeMod("fabric_api_base", "0.4.42+d1308ded19",
				"Forgified Fabric API Base", Map.of(), List.of()));
		Pipeline.Result result = Pipeline.run(input(game, List.of(user, official), Namespace.OFFICIAL, Map.of(), null, neoForge));
		assertEquals(List.of("user"), result.mods().stream().map(m -> m.translated().candidate().id()).toList(), result.report().render());
		List<Report.ModRow> rows = result.report().mods().stream().filter(r -> r.id().equals("fabric-api-base")).toList();
		assertEquals(List.of(Report.Status.SKIPPED, Report.Status.PROVIDED_BY_MOD), rows.stream().map(Report.ModRow::status).toList());
		assertTrue(rows.get(0).detail().contains("a NeoForge build of this mod is installed"), rows.get(0).detail());
		assertEquals("Forgified Fabric API Base", rows.get(1).source());
		assertTrue(result.report().mods().stream().noneMatch(r -> r.status() == Report.Status.PROVIDED_NATIVELY), result.report().render());
		assertEquals("Forgified Fabric API Base", result.runtime().getModContainer("fabric-api-base").orElseThrow().getMetadata().getName());
		FabricatedApiImpl api = new FabricatedApiImpl("0.5.0", FabricApiPlan.parse(PLAN), result.report(), result.runtime(), true);
		assertEquals(FabricApiModule.Status.PROVIDED_BY_MOD, api.fabricApiModule("fabric-api-base").orElseThrow().status(),
				"the provider's row wins over the skipped copy's");
		assertEquals(Map.of("fabric-api-base", "fabric_api_base"), Pipeline.providedElsewhere(FabricApiPlan.parse(PLAN), neoForge).entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().id())));
		// Of two copies of the provider (one nested in another mod's jar), the newest, which NeoForge loads.
		List<Pipeline.NeoForgeMod> twice = List.of(new Pipeline.NeoForgeMod("fabric_api_base", "0.4.30", "Older Base", Map.of(), List.of()),
				neoForge.getFirst());
		assertEquals("Forgified Fabric API Base", Pipeline.providedElsewhere(FabricApiPlan.parse(PLAN), twice).get("fabric-api-base").displayName());
		// A module beyond the plan that parts of the in-game mod serve (one Fabricated passes through) counts when named.
		List<Pipeline.NeoForgeMod> content = List.of(neoForge.getFirst(), new Pipeline.NeoForgeMod("fabric_content_registries_v0", "4.0.11",
				"Forgified Fabric Content Registries (v0)", Map.of(), List.of()));
		assertEquals(List.of("fabric-api-base"), List.copyOf(Pipeline.providedElsewhere(FabricApiPlan.parse(PLAN), content).keySet()));
		Map<String, Pipeline.NeoForgeMod> more = Pipeline.providedElsewhere(FabricApiPlan.parse(PLAN),
				List.of("fabric-content-registries-v0", "fabric-renderer-api-v1"), content);
		assertEquals(List.of("fabric-api-base", "fabric-content-registries-v0"), List.copyOf(more.keySet()));
		// The in-game mod's bridges ask the result, which then knows it too.
		Pipeline.Result also = result.alsoProvidedElsewhere(more.keySet());
		assertEquals(Set.of("fabric-api-base", "fabric-content-registries-v0"), also.providedElsewhere());
		assertSame(result, result.alsoProvidedElsewhere(Set.of("fabric-api-base")));
		assertEquals(result.mods(), also.mods());
	}

	/**
	 * Fabric Loader gives every mod a root, and mods take the first without checking (OneConfig's list of mods, POLY-1;
	 * owo's data loader, CORPUS-77). Without NeoForge, MixinExtras is the in-game mod's, which nests it on Forge.
	 */
	@Test
	@SuppressWarnings("deprecation") // owo calls getRootPath()
	void builtInModsHaveTheRootOfWhatProvidesThem(@TempDir Path game) throws Exception {
		TestJars.create().text("game.txt", "").write(game.resolve("minecraft.jar"));
		TestJars.create().text("in-game.txt", "").write(game.resolve("fabricated.jar"));
		Pipeline.Result result = Pipeline.run(input(game, List.of(), Namespace.OFFICIAL, Map.of(), null));
		Map<String, String> files = Map.of("minecraft", "game.txt", "fabricloader", "in-game.txt", "fabricated", "in-game.txt",
				"fabric-api-base", "in-game.txt", "mixinextras", "in-game.txt");
		files.forEach((id, file) -> {
			ModContainer mod = result.runtime().getModContainer(id).orElseThrow();
			assertEquals(ModOrigin.Kind.PATH, mod.getOrigin().getKind(), id);
			assertTrue(Files.exists(mod.getRootPath().resolve(file)), id);
		});
		assertEquals(Path.of(System.getProperty("java.home")), result.runtime().getModContainer("java").orElseThrow().getRootPaths().getFirst());

		// With NeoForge, which ships MixinExtras, it is NeoForge's.
		Path neoForgeJar = TestJars.create().text("neoforge.txt", "").write(game.resolve("neoforge.jar"));
		Pipeline.Result neo = Pipeline.run(input(game, List.of(), Namespace.OFFICIAL, Map.of(), null,
				List.of(new Pipeline.NeoForgeMod("neoforge", "21.1.1", "NeoForge", Map.of(), List.of(neoForgeJar)))));
		assertTrue(Files.exists(neo.runtime().getModContainer("mixinextras").orElseThrow().getRootPath().resolve("neoforge.txt")));
	}

	/** A mixin config a mod declares in its nested jar is that jar's, which NeoForge's mod file then declares (ADV-1). */
	@Test
	void aMixinConfigInANestedJarLoadsWithThatJar(@TempDir Path game) throws Exception {
		byte[] shared = TestJars.create().fabricModJson(TestJars.modJson("shared", "1.0", ""))
				.text("shared.mixins.json", "{\"required\":true,\"package\":\"com.shared.mixin\",\"mixins\":[\"ThingMixin\"]}")
				.bytes("com/shared/mixin/ThingMixin.class", mixinClass("com/shared/mixin/ThingMixin", "tick"))
				.toBytes();
		Path holder = TestJars.create()
				.fabricModJson(TestJars.modJson("holder", "1.0", "\"jars\":[{\"file\":\"META-INF/jars/shared.jar\"}],\"mixins\":[\"shared.mixins.json\"]"))
				.bytes("META-INF/jars/shared.jar", shared)
				.write(game.resolve("mods/holder.jar"));
		Pipeline.Result result = Pipeline.run(input(game, List.of(holder), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));
		Map<String, Pipeline.LoadedMod> byId = result.mods().stream().collect(Collectors.toMap(m -> m.translated().candidate().id(), m -> m));
		assertEquals(Set.of("holder", "shared"), byId.keySet(), result.report().render());
		try (SafeArchive holderOverlay = SafeArchive.open(byId.get("holder").translated().overlay(), ArchiveLimits.DEFAULTS);
				SafeArchive sharedOverlay = SafeArchive.open(byId.get("shared").translated().overlay(), ArchiveLimits.DEFAULTS)) {
			assertFalse(holderOverlay.readText("META-INF/neoforge.mods.toml").contains("shared.mixins.json"));
			assertTrue(sharedOverlay.readText("META-INF/neoforge.mods.toml").contains("config=\"shared.mixins.json\""));
		}
	}

	/**
	 * Fabric lets mods depend on each other, in pairs or rings, while NeoForge's and Forge's sorters stop the game at a
	 * cycle of load orderings ("Mod Sorting failed", "Detected Cycles"): a translated mod is ordered only after the mods
	 * before it in the resolved order, so a cycle loses one of its orderings and nothing else does (ORDER-1).
	 */
	@Test
	void dependenciesOnEachOtherLeaveNoCycleOfLoadOrderings(@TempDir Path game) throws Exception {
		Map<String, String> depends = Map.of("pair_a", "pair_b", "pair_b", "pair_a", "ring_c", "ring_d", "ring_d", "ring_e", "ring_e", "ring_c", "user_f", "pair_a");
		for (PlatformDescriptor descriptor : List.of(PlatformDescriptor.NEOFORGE, PlatformDescriptor.FORGE)) {
			Path dir = game.resolve(descriptor.loaderName());
			List<Path> jars = new ArrayList<>();
			for (Map.Entry<String, String> mod : depends.entrySet()) {
				jars.add(TestJars.create()
						.fabricModJson(TestJars.modJson(mod.getKey(), "1.0", "\"depends\":{\"" + mod.getValue() + "\":\"*\"}"))
						.write(dir.resolve("mods/" + mod.getKey() + ".jar")));
			}
			Pipeline.Result result = Pipeline.run(withDescriptor(input(dir, jars, Namespace.OFFICIAL, Map.of(), null), descriptor));
			Map<String, Set<String>> after = new HashMap<>();
			for (Pipeline.LoadedMod mod : result.mods()) {
				try (SafeArchive overlay = SafeArchive.open(mod.translated().overlay(), ArchiveLimits.DEFAULTS)) {
					java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[\\[dependencies\\.(\\w+)]]\nmodId=\"(\\w+)\"")
							.matcher(overlay.readText(descriptor.path()));
					while (m.find()) {
						if (depends.containsKey(m.group(2))) after.computeIfAbsent(m.group(1), k -> new java.util.TreeSet<>()).add(m.group(2));
					}
				}
			}
			assertEquals(depends.keySet(), result.mods().stream().map(m -> m.translated().candidate().id()).collect(Collectors.toSet()),
					result.report().render());
			assertFalse(hasCycle(after), descriptor.loaderName() + ": " + after);
			assertEquals(Set.of("pair_a"), after.get("user_f"), "an ordering outside a cycle stays");
			// Each cycle keeps all its orderings but one: a pair one, the ring two.
			assertEquals(1, after.getOrDefault("pair_a", Set.of()).size() + after.getOrDefault("pair_b", Set.of()).size(), after.toString());
			assertEquals(2, after.getOrDefault("ring_c", Set.of()).size() + after.getOrDefault("ring_d", Set.of()).size()
					+ after.getOrDefault("ring_e", Set.of()).size(), after.toString());
			// The report says which orderings went, and why.
			List<String> loadOrder = result.report().render().lines().filter(l -> l.contains("doesn't load after")).toList();
			assertEquals(2, loadOrder.size(), result.report().render());
			assertTrue(loadOrder.stream().allMatch(l -> l.contains(descriptor.loaderName() + " refuses")), loadOrder.toString());
		}
	}

	private static boolean hasCycle(Map<String, Set<String>> after) {
		Map<String, Integer> state = new HashMap<>();
		for (String id : after.keySet()) {
			if (visits(id, after, state)) return true;
		}
		return false;
	}

	/** Depth first: 1 while on the path, 2 once done. */
	private static boolean visits(String id, Map<String, Set<String>> after, Map<String, Integer> state) {
		Integer s = state.get(id);
		if (s != null) return s == 1;
		state.put(id, 1);
		for (String next : after.getOrDefault(id, Set.of())) {
			if (visits(next, after, state)) return true;
		}
		state.put(id, 2);
		return false;
	}

	/** Classes find the native libraries of a jar without classes nested beside them, as on Fabric's class path (Skiko's). */
	@Test
	void rootFilesOfAJarWithoutClassesAreServedByTheModulesBesideIt(@TempDir Path game) throws Exception {
		String generated = "\"custom\":{\"fabric-loom:generated\":true}";
		byte[] lib = TestJars.create().fabricModJson(TestJars.modJson("native-user", "1.0", generated))
				.bytes("com/lib/Loader.class", TestJars.emptyClass("com/lib/Loader", "java/lang/Object"))
				.text("LICENSE", "lib's")
				.toBytes();
		byte[] natives = TestJars.create().fabricModJson(TestJars.modJson("natives", "1.0", generated))
				.text("libfoo.so", "ELF").text("libfoo.so.sha256", "0123").text("LICENSE", "natives'")
				.toBytes();
		Path bundle = TestJars.create()
				.fabricModJson(TestJars.modJson("bundle", "1.0", "\"jars\":[{\"file\":\"META-INF/jars/lib.jar\"},{\"file\":\"META-INF/jars/natives.jar\"}]"))
				.bytes("com/bundle/Main.class", TestJars.emptyClass("com/bundle/Main", "java/lang/Object"))
				.bytes("META-INF/jars/lib.jar", lib)
				.bytes("META-INF/jars/natives.jar", natives)
				.write(game.resolve("mods/bundle.jar"));
		Path other = TestJars.create().fabricModJson(TestJars.modJson("other", "1.0", ""))
				.bytes("com/other/Main.class", TestJars.emptyClass("com/other/Main", "java/lang/Object"))
				.write(game.resolve("mods/other.jar"));
		Pipeline.Result result = Pipeline.run(input(game, List.of(bundle, other), Namespace.OFFICIAL, Map.of(), null));
		Map<String, Pipeline.LoadedMod> byId = result.mods().stream().collect(Collectors.toMap(m -> m.translated().candidate().id(), m -> m));
		assertEquals(Set.of("bundle", "native-user", "natives", "other"), byId.keySet(), result.report().render());
		Path nativesJar = byId.get("natives").translated().jar();
		assertEquals(Map.of(nativesJar, Set.of("libfoo.so", "libfoo.so.sha256")), byId.get("native-user").packages().borrowed(),
				"a sibling's module serves them, but its own LICENSE stays its own");
		assertEquals(Map.of(nativesJar, Set.of("LICENSE", "libfoo.so", "libfoo.so.sha256")), byId.get("bundle").packages().borrowed(), "so does the parent's");
		assertEquals(Map.of(), byId.get("other").packages().borrowed(), "an unrelated mod's module doesn't");
		assertTrue(result.report().render().contains("its 3 root file(s) are also served by the modules of bundle, native_user"), result.report().render());
	}

	@Test
	void apiListsTheInstalledFabricApiModulesThePlanLeavesOut(@TempDir Path game) throws Exception {
		// Forge's plans list only the modules they make native or change: the others in the installed release pass through.
		byte[] networking = TestJars.create().fabricModJson(TestJars.modJson("fabric-networking-api-v1", "4.0.0", ""))
				.bytes("net/fabricmc/fabric/impl/networking/Impl.class", TestJars.emptyClass("net/fabricmc/fabric/impl/networking/Impl", "java/lang/Object"))
				.toBytes();
		Path fabricApi = TestJars.create()
				.fabricModJson(TestJars.modJson("fabric-api", "0.161.0+26.2", "\"jars\":[{\"file\":\"META-INF/jars/networking.jar\"}]"))
				.bytes("META-INF/jars/networking.jar", networking)
				.write(game.resolve("mods/fabric-api.jar"));
		Pipeline.Input in = input(game, List.of(fabricApi), Namespace.OFFICIAL, Map.of(), null);
		Pipeline.Result result = Pipeline.run(in);
		FabricatedApiImpl api = new FabricatedApiImpl(in.fabricatedVersion(), in.fabricApiPlan(), result.report(), result.runtime(), true);

		FabricApiModule module = api.fabricApiModule("fabric-networking-api-v1").orElseThrow(() -> new AssertionError(result.report().render()));
		assertEquals(FabricApiModule.Status.LOADED, module.status());
		assertEquals("4.0.0", module.version());
		assertEquals(FabricApiModule.Status.NATIVE, api.fabricApiModule("fabric-api-base").orElseThrow().status(), "the plan's modules still come first");
		assertTrue(api.fabricApiModule("fabric-api").isEmpty(), "the release's own jar isn't a module");

		// Another mod (Forgified Fabric API's module, here) provides it: the module says so, as a module the plan lists would.
		Pipeline.Input provided = input(game, List.of(fabricApi), Namespace.OFFICIAL, Map.of(), null,
				List.of(new Pipeline.NeoForgeMod("fabric_networking_api_v1", "4.1.0", "Forgified Networking", Map.of(), List.of())));
		Pipeline.Result withProvider = Pipeline.run(provided);
		FabricApiModule elsewhere = new FabricatedApiImpl(provided.fabricatedVersion(), provided.fabricApiPlan(), withProvider.report(), withProvider.runtime(),
				true).fabricApiModule("fabric-networking-api-v1").orElseThrow(() -> new AssertionError(withProvider.report().render()));
		assertEquals(FabricApiModule.Status.PROVIDED_BY_MOD, elsewhere.status(), withProvider.report().render());
		assertEquals("4.1.0", elsewhere.version());
	}

	@Test
	void apiReflectsTheLaunch(@TempDir Path game) throws Exception {
		Path mods = Files.createDirectories(game.resolve("mods"));
		writeMods(mods);
		List<Path> jars;
		try (var s = Files.list(mods)) {
			jars = s.sorted().toList();
		}
		Pipeline.Input in = input(game, jars, Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null);
		Pipeline.Result result = Pipeline.run(in);
		FabricatedApiImpl api = new FabricatedApiImpl(in.fabricatedVersion(), in.fabricApiPlan(), result.report(), result.runtime(), true);

		assertTrue(result.runtime().isModLoaded("fabricated"), "mods can detect Fabricated");
		assertEquals(FabricMod.Status.LOADED, api.fabricMod("good_mod").orElseThrow().status(), "found by NeoForge id");
		assertEquals("Good Mod", api.fabricMod("good-mod").orElseThrow().name());
		assertEquals(FabricMod.Status.DISABLED, api.fabricMod("bad-mixin").orElseThrow().status());
		assertEquals(FabricMod.Status.SKIPPED, api.fabricMod("client-only").orElseThrow().status());
		assertTrue(api.fabricMod("broken.jar").isEmpty(), "rejected jars are not mods");
		assertEquals(FabricApiModule.Status.NATIVE, api.fabricApiModule("fabric-api-base").orElseThrow().status());
		assertEquals(FabricApiModule.Status.UNSUPPORTED, api.fabricApiModule("fabric-renderer-indigo").orElseThrow().status());
		assertEquals(FabricApiModule.Status.NOT_INSTALLED, api.fabricApiModule("fabric-item-api-v1").orElseThrow().status());

		List<String> ran = new ArrayList<>();
		api.afterEntrypoints(() -> ran.add("first"));
		api.afterEntrypoints(() -> {
			throw new IllegalStateException("broken action");
		});
		// What else an action can throw (SEC-7): a checked exception Java doesn't see (Kotlin's), an assertion.
		api.afterEntrypoints(() -> PipelineTest.<RuntimeException>sneakyThrow(new java.io.IOException("undeclared")));
		api.afterEntrypoints(() -> {
			throw new AssertionError("asserted");
		});
		api.afterEntrypoints(() -> ran.add("third"));
		assertTrue(ran.isEmpty(), "waits for the entrypoints");
		List<Throwable> errors = new ArrayList<>();
		api.markEntrypointsComplete(Set.of("good-mod"), (action, error) -> errors.add(error));
		assertEquals(List.of("first", "third"), ran, "a throwing action does not stop the others");
		assertEquals(3, errors.size());
		// The JVM's own failure is no action's, as for entrypoints (EntrypointRunner.fatalCause).
		FabricatedApiImpl waiting = new FabricatedApiImpl(in.fabricatedVersion(), in.fabricApiPlan(), result.report(), result.runtime(), true);
		waiting.afterEntrypoints(() -> {
			throw new OutOfMemoryError("heap");
		});
		assertThrows(OutOfMemoryError.class, () -> waiting.markEntrypointsComplete(Set.of(), (action, error) -> errors.add(error)));
		assertEquals(FabricMod.Status.FAILED, api.fabricMod("good-mod").orElseThrow().status());
		api.afterEntrypoints(() -> ran.add("late"));
		assertEquals("late", ran.getLast(), "runs right away once the entrypoints ran");
	}

	/** Throws {@code t} where the compiler doesn't see it, as Kotlin code throws a checked exception. */
	@SuppressWarnings("unchecked")
	private static <T extends Throwable> void sneakyThrow(Throwable t) throws T {
		throw (T) t;
	}

	@Test
	void modsCanSkipTheirOwnMixinsAndEntrypoints(@TempDir Path game) throws Exception {
		Path jar = TestJars.create().fabricModJson(TestJars.modJson("hinted", "1.0", """
						"mixins":["hinted.mixins.json"],"entrypoints":{"server":["com.hinted.Server"]},
						"custom":{"fabricated":{"skipMixins":["hinted.mixins.json:BadMixin"],"entrypoints":{"main":["com.hinted.NeoForge"]},
						"skipEntrypoints":["server"]}}"""))
				.text("hinted.mixins.json", "{\"required\":true,\"package\":\"com.hinted.mixin\",\"mixins\":[\"BadMixin\"]}")
				.bytes("com/hinted/mixin/BadMixin.class", mixinClass("com/hinted/mixin/BadMixin", "removedByNeoForge"))
				.write(game.resolve("mods/hinted.jar"));
		Pipeline.Result result = Pipeline.run(input(game, List.of(jar), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));
		assertTrue(result.runtime().isModLoaded("hinted"), result.report().render());
		assertEquals(List.of("com.hinted.NeoForge"), result.runtime().declared("main").stream().map(d -> d.spec().value()).toList());
		assertTrue(result.runtime().declared("server").isEmpty());
		assertTrue(result.report().render().contains("Compatibility hints"));
	}

	/**
	 * A required mixin NeoForge's code can't take, which nothing outside it calls into, is left out of its config (or only
	 * its failing injector, when it adds no fields) and the mod loads without it (LEAVE-1); the report says so, in the
	 * mod's row too, which {@code /fabricated why} shows.
	 */
	@Test
	void aSelfContainedMixinNeoForgeCanNotTakeIsLeftOutAndTheModLoads(@TempDir Path game) throws Exception {
		// Broken keeps state in a field of its own, which its injectors may share: it goes whole. Stateless only loses its
		// failing injector.
		ClassNode broken = new ClassNode();
		new ClassReader(mixinClass("com/partial/mixin/Broken", "removedByNeoForge")).accept(broken, 0);
		broken.fields.add(new org.objectweb.asm.tree.FieldNode(Opcodes.ACC_PRIVATE, "calls", "I", null, null));
		Path jar = TestJars.create().fabricModJson(TestJars.modJson("partial-mod", "1.0", "\"mixins\":[\"partial.mixins.json\"]"))
				.text("partial.mixins.json", "{\"required\":true,\"package\":\"com.partial.mixin\",\"mixins\":[\"Works\",\"Broken\",\"Stateless\"]}")
				.bytes("com/partial/mixin/Works.class", mixinClass("com/partial/mixin/Works", "tick"))
				.bytes("com/partial/mixin/Broken.class", write(broken))
				.bytes("com/partial/mixin/Stateless.class", mixinClass("com/partial/mixin/Stateless", "removedByNeoForge"))
				.write(game.resolve("mods/partial.jar"));
		Pipeline.Result result = Pipeline.run(input(game, List.of(jar), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));
		String report = result.report().render();
		assertTrue(result.runtime().isModLoaded("partial-mod"), report);
		assertTrue(report.contains(Pipeline.leftOutSection("NeoForge")), report);
		assertTrue(report.contains("partial-mod: LEFT_OUT [partial.mixins.json com.partial.mixin.Broken]"), report);
		assertTrue(report.contains("partial-mod: LEFT_OUT [partial.mixins.json com.partial.mixin.Stateless]"), report);
		Report.ModRow row = result.report().mods().stream().filter(r -> r.id().equals("partial-mod")).findFirst().orElseThrow();
		assertTrue(row.detail().contains("2 mixin(s) left out in whole or in part"), row.detail());
		try (SafeArchive overlay = SafeArchive.open(result.mods().getFirst().translated().overlay(), ArchiveLimits.DEFAULTS)) {
			String config = new String(overlay.read("partial.mixins.json"), StandardCharsets.UTF_8);
			assertTrue(config.contains("Works") && config.contains("Stateless") && !config.contains("Broken"), config);
			ClassNode stateless = new ClassNode();
			new ClassReader(overlay.read("com/partial/mixin/Stateless.class")).accept(stateless, 0);
			assertTrue(stateless.methods.stream().noneMatch(m -> m.name.equals("handler")), "its failing injector is left out");
		}
	}

	/**
	 * A mixin that failed as Mixin applied it in the last launch, which pre-flight passed, is left out of its config
	 * the next (APPLY-1); the report says so, in the mod's row too. Once the mod's jar changes it is tried again.
	 */
	@Test
	void aMixinThatFailedAsMixinAppliedItIsLeftOutTheNextLaunch(@TempDir Path game) throws Exception {
		Path jar = TestJars.create().fabricModJson(TestJars.modJson("failing-mod", "1.0", "\"mixins\":[\"failing.mixins.json\"]"))
				.text("failing.mixins.json", "{\"required\":true,\"package\":\"com.failing.mixin\",\"mixins\":[\"Works\",\"Failed\"]}")
				.bytes("com/failing/mixin/Works.class", mixinClass("com/failing/mixin/Works", "tick"))
				.bytes("com/failing/mixin/Failed.class", mixinClass("com/failing/mixin/Failed", "tick"))
				.write(game.resolve("mods/failing.jar"));
		Pipeline.Input in = input(game, List.of(jar), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null);
		List<String> notes = new ArrayList<>();
		assertTrue(Pipeline.mixinFailures(in).record(new MixinFailures.Failure("failing-mod", Hashing.sha256(jar), "failing.mixins.json:Failed",
				"InvalidInjectionException: it failed"), notes), notes.toString());

		Pipeline.Result result = Pipeline.run(in);
		String report = result.report().render();
		assertTrue(result.runtime().isModLoaded("failing-mod"), report);
		assertTrue(report.contains(Pipeline.FAILED_LAST_LAUNCH), report);
		assertTrue(report.contains("failing-mod: failing.mixins.json:Failed failed as Mixin applied it in the last launch (InvalidInjectionException: it failed)"),
				report);
		Report.ModRow row = result.report().mods().stream().filter(r -> r.id().equals("failing-mod")).findFirst().orElseThrow();
		assertTrue(row.detail().contains("1 mixin(s) left out that failed as Mixin applied them in the last launch"), row.detail());
		assertFalse(report.contains("names no mixin of this mod"), report);
		try (SafeArchive overlay = SafeArchive.open(result.mods().getFirst().translated().overlay(), ArchiveLimits.DEFAULTS)) {
			String config = new String(overlay.read("failing.mixins.json"), StandardCharsets.UTF_8);
			assertTrue(config.contains("Works") && !config.contains("Failed"), config);
		}

		// Another jar of the mod: the mixin is tried again, and the record goes.
		TestJars.create().fabricModJson(TestJars.modJson("failing-mod", "1.1", "\"mixins\":[\"failing.mixins.json\"]"))
				.text("failing.mixins.json", "{\"required\":true,\"package\":\"com.failing.mixin\",\"mixins\":[\"Works\",\"Failed\"]}")
				.bytes("com/failing/mixin/Works.class", mixinClass("com/failing/mixin/Works", "tick"))
				.bytes("com/failing/mixin/Failed.class", mixinClass("com/failing/mixin/Failed", "tick"))
				.write(jar);
		result = Pipeline.run(input(game, List.of(jar), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));
		assertFalse(result.report().render().contains(Pipeline.FAILED_LAST_LAUNCH), result.report().render());
		assertFalse(Files.exists(MixinFailures.file(game)));
		Report.ModRow again = result.report().mods().stream().filter(r -> r.id().equals("failing-mod")).findFirst().orElseThrow();
		assertFalse(again.detail().contains("failed as Mixin applied them"), again.detail());
	}

	@Test
	void aSingleHandlerMethodCanBeLeftOut(@TempDir Path game) throws Exception {
		ClassNode mixin = new ClassNode();
		new ClassReader(mixinClass("com/part/mixin/PartlyBad", "removedByNeoForge")).accept(mixin, 0);
		mixin.methods.add(new org.objectweb.asm.tree.MethodNode(Opcodes.ACC_PRIVATE, "helper", "()V", null, null));
		Path jar = TestJars.create().fabricModJson(TestJars.modJson("part-mod", "1.0", """
						"mixins":["part.mixins.json"],"custom":{"fabricated":{"skipMixins":["part.mixins.json:PartlyBad#handler","part.mixins.json:PartlyBad#gone"]}}"""))
				.text("part.mixins.json", "{\"required\":true,\"package\":\"com.part.mixin\",\"mixins\":[\"PartlyBad\"]}")
				.bytes("com/part/mixin/PartlyBad.class", write(mixin))
				.write(game.resolve("mods/part.jar"));
		Pipeline.Result result = Pipeline.run(input(game, List.of(jar), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));
		String report = result.report().render();
		assertTrue(result.runtime().isModLoaded("part-mod"), "pre-flight checks the mixin without the handler: " + report);
		assertTrue(report.contains("has no method or field gone to leave out"), report);
		try (SafeArchive overlay = SafeArchive.open(result.mods().getFirst().translated().overlay(), ArchiveLimits.DEFAULTS)) {
			assertNull(overlay.read("part.mixins.json"), "the mixin itself stays in its config");
			ClassNode stripped = new ClassNode();
			new ClassReader(overlay.read("com/part/mixin/PartlyBad.class")).accept(stripped, 0);
			assertEquals(List.of("helper"), stripped.methods.stream().map(m -> m.name).toList());
		}
	}

	@Test
	void aPluginConfigLosesOnlyItsFailingInjectorsIncludingThoseOfMixinsItsPluginAdds(@TempDir Path game) throws Exception {
		ClassNode listed = new ClassNode();
		new ClassReader(mixinClass("com/plug/mixin/Listed", "removedByNeoForge")).accept(listed, 0);
		// A helper of the same name as the failing handler, which other code of the mixin calls: it stays.
		listed.methods.add(new org.objectweb.asm.tree.MethodNode(Opcodes.ACC_PRIVATE, "handler", "(I)V", null, null));
		Path jar = TestJars.create().fabricModJson(TestJars.modJson("plug-mod", "1.0", "\"mixins\":[\"plug.mixins.json\"]"))
				.text("plug.mixins.json", "{\"required\":true,\"package\":\"com.plug.mixin\",\"plugin\":\"com.plug.Plugin\",\"mixins\":[\"Listed\"]}")
				.bytes("com/plug/mixin/Listed.class", write(listed))
				// Not listed: the plugin's getMixins() adds it at runtime.
				.bytes("com/plug/mixin/sub/Added.class", mixinClass("com/plug/mixin/sub/Added", "removedByNeoForge"))
				.write(game.resolve("mods/plug.jar"));
		Pipeline.Result result = Pipeline.run(input(game, List.of(jar), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));
		String report = result.report().render();
		assertTrue(result.runtime().isModLoaded("plug-mod"), report);
		assertTrue(report.contains("LEFT_OUT [plug.mixins.json com.plug.mixin.sub.Added]"), report);
		try (SafeArchive overlay = SafeArchive.open(result.mods().getFirst().translated().overlay(), ArchiveLimits.DEFAULTS)) {
			assertNull(overlay.read("plug.mixins.json"), "the mixins stay in their config");
			for (String mixin : List.of("com/plug/mixin/Listed.class", "com/plug/mixin/sub/Added.class")) {
				ClassNode stripped = new ClassNode();
				new ClassReader(overlay.read(mixin)).accept(stripped, 0);
				assertTrue(stripped.methods.stream().noneMatch(m -> m.name.equals("handler") && m.desc.equals("()V")), mixin + " loses its failing handler");
			}
			ClassNode kept = new ClassNode();
			new ClassReader(overlay.read("com/plug/mixin/Listed.class")).accept(kept, 0);
			assertTrue(kept.methods.stream().anyMatch(m -> m.name.equals("handler") && m.desc.equals("(I)V")), "its namesake helper stays");
		}
	}

	@Test
	void mixinsAPluginAddsGetTheRepairsOfListedOnes(@TempDir Path game) throws Exception {
		// As Friends and Foes' plugin adds its CommandsMixin: a @Redirect whose injection point array of one NeoForge
		// 1.21.11's MixinExtras reads as one annotation (MIX-1).
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/plug/mixin/Redirecting", null, "java/lang/Object", null);
		AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor targets = m.visitArray("value");
		targets.visit(null, Type.getObjectType(TARGET));
		targets.visitEnd();
		m.visitEnd();
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "redirect", "(L" + TARGET + ";)Z", null, null);
		AnnotationVisitor redirect = mv.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Redirect;", true);
		AnnotationVisitor methods = redirect.visitArray("method");
		methods.visit(null, "tick");
		methods.visitEnd();
		AnnotationVisitor ats = redirect.visitArray("at");
		AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
		at.visit("value", "INVOKE");
		at.visit("target", "L" + TARGET + ";isDebug()Z");
		at.visitEnd();
		ats.visitEnd();
		redirect.visit("require", 0);
		redirect.visitEnd();
		mv.visitCode();
		mv.visitInsn(Opcodes.ICONST_1);
		mv.visitInsn(Opcodes.IRETURN);
		mv.visitMaxs(1, 2);
		mv.visitEnd();
		cw.visitEnd();
		Path jar = TestJars.create().fabricModJson(TestJars.modJson("plug-mod", "1.0", "\"mixins\":[\"plug.mixins.json\"]"))
				.text("plug.mixins.json", "{\"required\":true,\"package\":\"com.plug.mixin\",\"plugin\":\"com.plug.Plugin\",\"mixins\":[]}")
				.bytes("com/plug/mixin/Redirecting.class", cw.toByteArray())
				.write(game.resolve("mods/plug.jar"));
		Pipeline.Result result = Pipeline.run(input(game, List.of(jar), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));
		assertTrue(result.runtime().isModLoaded("plug-mod"), result.report().render());
		try (SafeArchive overlay = SafeArchive.open(result.mods().getFirst().translated().overlay(), ArchiveLimits.DEFAULTS)) {
			byte[] repaired = overlay.read("com/plug/mixin/Redirecting.class");
			assertNotNull(repaired, "the plugin's mixin is repaired");
			ClassNode node = new ClassNode();
			new ClassReader(repaired).accept(node, 0);
			org.objectweb.asm.tree.AnnotationNode ann = node.methods.getFirst().visibleAnnotations.getFirst();
			assertTrue(ann.values.get(ann.values.indexOf("at") + 1) instanceof org.objectweb.asm.tree.AnnotationNode, ann.values.toString());
		}
	}

	/** A class whose {@code run} adds {@code config} to Mixin by name, as ReplayMod's launcher does, or a name it is given when null. */
	private static byte[] configAdder(String name, String config) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "(Ljava/lang/String;)V", null, null);
		mv.visitCode();
		if (config != null) mv.visitLdcInsn(config);
		else mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/spongepowered/asm/mixin/Mixins", "addConfiguration", "(Ljava/lang/String;)V", false);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * MIXCFG-1: a mixin config the mod's code adds to Mixin itself by a constant name, unlisted in its metadata (ReplayMod's
	 * plugin adds eight so), is checked as a listed one: a required mixin the platform's code can't take is left out of it
	 * where the mod can do without, as rewritten in the overlay, which the mod's call reads by name, or else the mod is
	 * disabled. The descriptor still doesn't list it: the mod's code adds it, when it does.
	 */
	@Test
	void aMixinConfigTheModsCodeAddsByNameIsCheckedAsAListedOne(@TempDir Path game) throws Exception {
		Path mods = Files.createDirectories(game.resolve("mods"));
		ClassNode broken = new ClassNode();
		new ClassReader(mixinClass("com/adds/mixin/Broken", "removedByNeoForge")).accept(broken, 0);
		broken.fields.add(new org.objectweb.asm.tree.FieldNode(Opcodes.ACC_PRIVATE, "calls", "I", null, null));
		Path adds = TestJars.create().fabricModJson(TestJars.modJson("adds-mod", "1.0", "\"mixins\":[\"adds.mixins.json\"]"))
				.text("adds.mixins.json", "{\"required\":true,\"package\":\"com.adds.listed\",\"mixins\":[\"Listed\"]}")
				.bytes("com/adds/listed/Listed.class", mixinClass("com/adds/listed/Listed", "tick"))
				.bytes("com/adds/Launcher.class", configAdder("com/adds/Launcher", "adds.extra.mixins.json"))
				// Comments, as ReplayMod's configs have, which Mixin's reader takes.
				.text("adds.extra.mixins.json", "{\"required\":true,\"package\":\"com.adds.mixin\",\"mixins\":[\n// one\n\"Works\",\"Broken\"]}")
				.bytes("com/adds/mixin/Works.class", mixinClass("com/adds/mixin/Works", "tick"))
				.bytes("com/adds/mixin/Broken.class", write(broken))
				.write(mods.resolve("adds.jar"));
		Path ducks = TestJars.create().fabricModJson(TestJars.modJson("adds-duck", "1.0", ""))
				.bytes("com/duck/Launcher.class", configAdder("com/duck/Launcher", "duck.mixins.json"))
				.text("duck.mixins.json", "{\"required\":true,\"package\":\"com.duck.mixin\",\"mixins\":[\"Duck\"]}")
				.bytes("com/duck/mixin/Duck.class", duckMixinClass("com/duck/mixin/Duck", "removedByNeoForge"))
				.write(mods.resolve("duck.jar"));
		Pipeline.Result result = Pipeline.run(input(game, List.of(adds, ducks), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));

		String report = result.report().render();
		assertTrue(result.runtime().isModLoaded("adds-mod"), report);
		assertTrue(report.contains("adds-mod: LEFT_OUT [adds.extra.mixins.json com.adds.mixin.Broken]"), report);
		assertTrue(report.contains("checked as listed ones: adds.extra.mixins.json (MIXCFG-1)"), report);
		try (SafeArchive overlay = SafeArchive.open(result.mods().getFirst().translated().overlay(), ArchiveLimits.DEFAULTS)) {
			String config = overlay.readText("adds.extra.mixins.json");
			assertTrue(config.contains("Works") && !config.contains("Broken"), config);
			String toml = overlay.readText("META-INF/neoforge.mods.toml");
			assertTrue(toml.contains("adds.mixins.json") && !toml.contains("adds.extra.mixins.json"), toml);
		}
		Report.ModRow duck = result.report().mods().stream().filter(r -> r.id().equals("adds-duck")).findFirst().orElseThrow();
		assertEquals(Report.Status.DISABLED, duck.status());
		assertTrue(duck.detail().contains("removedByNeoForge"), duck.detail());
	}

	/** MIXCFG-1: a config added by a name the mod's code computes can't be checked; the report says so, and the mod loads. */
	@Test
	void aMixinConfigAddedByANameTheModsCodeComputesIsReportedUnchecked(@TempDir Path game) throws Exception {
		Path jar = TestJars.create().fabricModJson(TestJars.modJson("computes-mod", "1.0", ""))
				.bytes("com/computes/Launcher.class", configAdder("com/computes/Launcher", null))
				.bytes("com/computes/Missing.class", configAdder("com/computes/Missing", "missing.mixins.json"))
				.write(game.resolve("mods/computes.jar"));
		Pipeline.Result result = Pipeline.run(input(game, List.of(jar), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));
		String report = result.report().render();
		assertTrue(result.runtime().isModLoaded("computes-mod"), report);
		assertTrue(report.contains("computes-mod: WARN [Mixins.addConfiguration com.computes.Launcher] Adds mixin configs to Mixin by names its code"
				+ " computes, which pre-flight can't read: their mixins aren't checked"), report);
		assertTrue(report.contains("computes-mod: WARN [missing.mixins.json] Added to Mixin by com.computes.Missing, but not in the mod's jar"), report);
	}

	private static Path patchTarget(Path game) throws Exception {
		return TestJars.create().fabricModJson(TestJars.modJson("target-mod", "2.0", "\"mixins\":[\"target.mixins.json\"]"))
				.text("target.mixins.json", "{\"required\":true,\"package\":\"com.target.mixin\",\"mixins\":[\"BadMixin\"]}")
				.bytes("com/target/mixin/BadMixin.class", duckMixinClass("com/target/mixin/BadMixin", "removedByNeoForge"))
				.write(game.resolve("mods/target.jar"));
	}

	private static Path patch(Path game, String depends) throws Exception {
		return TestJars.create().fabricModJson(TestJars.modJson("target-fix", "1.0", depends
						+ "\"custom\":{\"fabricated\":{\"patches\":[{\"target\":\"target-mod\",\"skipMixins\":[\"target.mixins.json:BadMixin\"]}]}}"))
				.write(game.resolve("mods/fix.jar"));
	}

	@Test
	void compatPatchesFixOtherModsTheyDependOn(@TempDir Path game) throws Exception {
		Pipeline.Result result = Pipeline.run(input(game, List.of(patchTarget(game), patch(game, "\"depends\":{\"target-mod\":\">=2\"},")),
				Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));
		assertTrue(result.runtime().isModLoaded("target-mod"), result.report().render());
		assertTrue(result.report().render().contains("target-mod <- target-fix"));
	}

	@Test
	void compatPatchesNeedADependencyOnTheirTarget(@TempDir Path game) throws Exception {
		Pipeline.Result result = Pipeline.run(input(game, List.of(patchTarget(game), patch(game, "")), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));
		assertFalse(result.runtime().isModLoaded("target-mod"), "the patch does not apply");
		assertTrue(result.report().render().contains("does not depend on it"));
	}

	@Test
	void mixinsUsingAnInterfaceOfASkippedMixinAreSkippedToo(@TempDir Path game) throws Exception {
		ClassNode adder = new ClassNode();
		new ClassReader(mixinClass("com/dep/mixin/AddsExt", "tick")).accept(adder, 0);
		adder.interfaces.add("com/dep/Ext");
		ClassNode user = new ClassNode();
		new ClassReader(mixinClass("com/dep/mixin/UsesExt", "tick")).accept(user, 0);
		user.fields.add(new org.objectweb.asm.tree.FieldNode(Opcodes.ACC_PRIVATE, "ext", "Lcom/dep/Ext;", null, null));
		// Uses the interface only in a handler that is left out, so it stays.
		ClassNode handlerUser = new ClassNode();
		new ClassReader(mixinClass("com/dep/mixin/UsesExtInHandler", "tick")).accept(handlerUser, 0);
		org.objectweb.asm.tree.MethodNode cast = new org.objectweb.asm.tree.MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "useExt", "(Ljava/lang/Object;)V", null, null);
		cast.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
		cast.instructions.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.CHECKCAST, "com/dep/Ext"));
		cast.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.POP));
		cast.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));
		cast.maxStack = 1;
		cast.maxLocals = 1;
		handlerUser.methods.add(cast);
		Path jar = TestJars.create().fabricModJson(TestJars.modJson("dep-mod", "1.0", """
						"mixins":["dep.mixins.json"],"custom":{"fabricated":{"skipMixins":["dep.mixins.json:AddsExt","dep.mixins.json:UsesExtInHandler#useExt"]}}"""))
				.text("dep.mixins.json", "{\"required\":true,\"package\":\"com.dep.mixin\",\"mixins\":[\"AddsExt\",\"UsesExt\",\"UsesExtInHandler\"]}")
				.bytes("com/dep/mixin/AddsExt.class", write(adder))
				.bytes("com/dep/mixin/UsesExt.class", write(user))
				.bytes("com/dep/mixin/UsesExtInHandler.class", write(handlerUser))
				.bytes("com/dep/Ext.class", TestJars.emptyClass("com/dep/Ext", "java/lang/Object"))
				.write(game.resolve("mods/dep.jar"));
		Pipeline.Result result = Pipeline.run(input(game, List.of(jar), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));
		String report = result.report().render();
		assertTrue(result.runtime().isModLoaded("dep-mod"), report);
		assertTrue(report.contains("also left out"), report);
		try (SafeArchive overlay = SafeArchive.open(result.mods().getFirst().translated().overlay(), ArchiveLimits.DEFAULTS)) {
			String config = new String(overlay.read("dep.mixins.json"), java.nio.charset.StandardCharsets.UTF_8);
			assertFalse(config.contains("\"UsesExt\""), config);
			assertTrue(config.contains("UsesExtInHandler"), config);
		}
	}

	private static byte[] write(ClassNode node) {
		ClassWriter writer = new ClassWriter(0);
		node.accept(writer);
		return writer.toByteArray();
	}
}
