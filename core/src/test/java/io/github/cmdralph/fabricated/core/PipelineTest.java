package io.github.cmdralph.fabricated.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.objectweb.asm.tree.ClassNode;

import io.github.cmdralph.fabricated.api.FabricApiModule;
import io.github.cmdralph.fabricated.api.FabricMod;
import io.github.cmdralph.fabricated.core.accesswidener.AccessWidenerFile.InjectedInterface;
import io.github.cmdralph.fabricated.core.api.FabricatedApiImpl;
import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.archive.SafeArchive;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.InstalledClasses;
import io.github.cmdralph.fabricated.core.cache.TranslationCache;
import io.github.cmdralph.fabricated.core.config.FabricatedConfig;
import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.metadata.FabricModJsonParser;
import io.github.cmdralph.fabricated.core.remap.MappingComposer;
import io.github.cmdralph.fabricated.core.remap.MappingTable;
import io.github.cmdralph.fabricated.core.report.Report;
import io.github.cmdralph.fabricated.core.resolve.Candidate;
import io.github.cmdralph.fabricated.core.resolve.FabricApiPlan;
import io.github.cmdralph.fabricated.core.testutil.TestEnums;
import io.github.cmdralph.fabricated.core.testutil.TestJars;
import io.github.cmdralph.fabricated.core.translate.DefaultBridge;
import io.github.cmdralph.fabricated.core.translate.PackageGuard;
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
						"accessWidener":"good.accesswidener","depends":{"fabricloader":">=0.19.0","minecraft":"~26.2","fabric-api-base":"*","cloth-config":"*"}"""))
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
		FabricatedConfig config = FabricatedConfig.load(game.resolve("config"));
		TranslationCache cache = new TranslationCache(game, config.cacheEnabled(), config.verifyIntegrity());
		return new Pipeline.Input(game, game.resolve("config"), config, cache, jars, ns == Namespace.OFFICIAL ? "26.2" : "1.21.11",
				"26.2.0.90", false, ns, "0.17.4+mixin.0.8.7", "0.5.3", "0.17.1", "0.1.0-test", false, gameClasses::get, List.of(game.resolve("minecraft.jar")),
				List.of(game.resolve("fabricated.jar")), ClassBytesSource.EMPTY, Set.of("net/neoforged/neoforge"),
				FabricApiPlan.parse(PLAN), name -> null, Map::of, neoForgeMods, installed, mappings, new String[0]);
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
			assertTrue(toml.contains("behaviorVersion=\"0.17.1\""), toml);
			assertTrue(toml.contains("modId=\"cloth_config\""), "loads after the NeoForge mod it depends on");
			String at = overlay.readText("META-INF/accesstransformer.cfg");
			assertTrue(at.contains("public net.minecraft.world.Thing count"), at);
		}
		assertEquals(good.translated().candidate().path(), good.translated().jar(), "26.x mods are not copied");

		assertTrue(result.runtime().isModLoaded("good-mod"));
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
				(cache, path) -> new Pipeline.Mappings(table, "test-hash"), List.of()));

		Set<String> loaded = result.mods().stream().map(m -> m.translated().candidate().id()).collect(Collectors.toSet());
		assertEquals(Set.of("base", "good-addon"), loaded, result.report().render());
		Report.ModRow row = result.report().mods().stream().filter(r -> r.id().equals("stale-addon")).findFirst().orElseThrow();
		assertTrue(row.detail().contains("drawSections, which does not exist in com.base.Renderer"), row.detail());
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

	/** The Fabric builds of Sodium and Iris can't run on NeoForge: the player is told to install their NeoForge builds. */
	@Test
	void fabricBuildsOfRendererReplacementsAskForTheirNeoForgeBuild(@TempDir Path game) throws Exception {
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path sodium = TestJars.create().fabricModJson(TestJars.modJson("sodium", "0.9.2", "\"name\":\"Sodium\"")).write(mods.resolve("sodium.jar"));
		Path addon = TestJars.create().fabricModJson(TestJars.modJson("sodium-addon", "1.0.0", "\"depends\":{\"sodium\":\"*\"}"))
				.write(mods.resolve("addon.jar"));

		Pipeline.Result result = Pipeline.run(input(game, List.of(sodium, addon), Namespace.OFFICIAL, Map.of(), null, List.of()));

		assertTrue(result.mods().isEmpty(), result.report().render());
		Report.ModRow row = result.report().mods().stream().filter(r -> r.id().equals("sodium")).findFirst().orElseThrow();
		assertEquals(Report.Status.DISABLED, row.status());
		assertTrue(row.detail().startsWith("its Fabric build can't run on NeoForge: "), row.detail());
		assertEquals("Install Sodium for NeoForge instead (from the same download page).", row.suggestion());
		assertFalse(result.runtime().isModLoaded("sodium"));
		assertTrue(result.report().mods().stream().anyMatch(r -> r.id().equals("sodium-addon") && r.status() == Report.Status.DISABLED),
				result.report().render());

		// A player testing support for it can load it anyway.
		Files.createDirectories(game.resolve("config"));
		Files.writeString(game.resolve("config").resolve(FabricatedConfig.FILE_NAME), "{\"schema\": 1, \"mods\": {\"sodium\": {\"loadUnsupported\": true}}}");
		Pipeline.Result forced = Pipeline.run(input(game, List.of(sodium, addon), Namespace.OFFICIAL, Map.of(), null, List.of()));
		Set<String> loaded = forced.mods().stream().map(m -> m.translated().candidate().id()).collect(Collectors.toSet());
		assertEquals(Set.of("sodium", "sodium-addon"), loaded, forced.report().render());
		assertTrue(forced.report().render().contains("Unsupported mods loaded on request (loadUnsupported)"), forced.report().render());
	}

	/**
	 * Iris's Fabric build hooks Sodium's Fabric build: next to Sodium for NeoForge it would load with its hooks missing,
	 * so it is refused, even when the player asked to load it. Without that request, the player is told to install
	 * Iris for NeoForge, as anywhere else.
	 */
	@Test
	void aFabricBuildIsRefusedNextToTheNeoForgeBuildOfItsPartner(@TempDir Path game) throws Exception {
		Path mods = Files.createDirectories(game.resolve("mods"));
		Path iris = TestJars.create().fabricModJson(TestJars.modJson("iris", "1.11.4", "\"name\":\"Iris\"")).write(mods.resolve("iris.jar"));
		List<Pipeline.NeoForgeMod> sodium = List.of(new Pipeline.NeoForgeMod("sodium", "0.9.2+mc26.3", "Sodium", Map.of(), List.of()));
		Pipeline.Result plain = Pipeline.run(input(game, List.of(iris), Namespace.OFFICIAL, Map.of(), null, sodium));
		assertEquals("Install Iris for NeoForge instead (from the same download page).",
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
		Pipeline.Result result = new Pipeline.Result(null, List.of(a, b), null, Map.of(), null, Set.of());
		assertEquals(List.of("com/a/Iface", "com/b/Iface"), interfacesOf(result, "net/minecraft/world/Thing"));
		assertEquals(Set.of("com/a/ClientThing", "com/b/ClientThing"), result.environmentClasses());

		Pipeline.Result kept = result.without(Set.of("mod-b"));
		assertEquals(List.of(a), kept.mods());
		assertEquals(List.of("com/a/Iface"), interfacesOf(kept, "net/minecraft/world/Thing"));
		assertEquals(Set.of("com/a/ClientThing"), kept.environmentClasses());
		assertTrue(result.without(Set.of("mod-a", "mod-b")).injectedInterfaces().isEmpty());
		assertEquals(1, result.defaultBridges().get("net/minecraft/world/Thing").size(), "a bridge two mods need is declared once");
		assertTrue(result.without(Set.of("mod-a", "mod-b")).defaultBridges().isEmpty());
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
		InjectedInterface injection = new InjectedInterface("net/minecraft/world/Thing", iface, iface, false, 1);
		TranslatedMod mod = new TranslatedMod(c, id.replace('-', '_'), file, file, List.of(), 0, List.of(), List.of(injection),
				List.of(new DefaultBridge("net/minecraft/world/Thing", "net/minecraft/world/Extension", "size", "()I")), Set.of(environmentClass), false, 0);
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
				(cache, path) -> new Pipeline.Mappings(table, "test-hash")));
		assertEquals(1, result.mods().size(), result.report().render());
		Path remapped = result.mods().getFirst().translated().jar();
		assertTrue(remapped.toString().endsWith("remapped.jar"));
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
	void aRequiredMixinUsingAModThatIsNotLoadingDisablesItsMod(@TempDir Path game) throws Exception {
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
		Pipeline.Result result = Pipeline.run(input(game, List.of(lib, user, guarded), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));

		Set<String> loaded = result.mods().stream().map(m -> m.translated().candidate().id()).collect(Collectors.toSet());
		assertEquals(Set.of("guarded-mod"), loaded, result.report().render());
		Report.ModRow row = result.report().mods().stream().filter(r -> r.id().equals("user-mod")).findFirst().orElseThrow();
		assertEquals(Report.Status.DISABLED, row.status());
		assertTrue(row.detail().contains("com.user.mixin.UsesMixin -> needs class com.lib.Api of lib-mod, which isn't loading"), row.detail());
	}

	@Test
	void anEnumMixinIntoAnExtensibleEnumBecomesAnEnumExtension(@TempDir Path game) throws Exception {
		// ENUM-1: NeoForge's loader adds the constants, from the mod's enum extension file; an enum mixin it can't take
		// (a constant built from a value computed when the class loads) still disables its mod (CORPUS-10).
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
		Map<String, byte[]> gameClasses = Map.of(TARGET, targetClass(), kind, TestEnums.target(kind, true, List.of("()V", "(Ljava/lang/String;)V"), List.of()));
		Pipeline.Result result = Pipeline.run(input(game, List.of(cooking, stew), Namespace.OFFICIAL, gameClasses, null));

		Map<String, Pipeline.LoadedMod> loaded = result.mods().stream().collect(Collectors.toMap(m -> m.translated().candidate().id(), m -> m));
		assertEquals(Set.of("cooking"), loaded.keySet(), result.report().render());
		try (SafeArchive overlay = SafeArchive.open(loaded.get("cooking").translated().overlay(), ArchiveLimits.DEFAULTS)) {
			assertTrue(overlay.readText("META-INF/neoforge.mods.toml").contains("enumExtensions=\"META-INF/fabricated/enum_extensions.json\""));
			Map<?, ?> entry = (Map<?, ?>) Json.asList(Json.parseObject(overlay.readText("META-INF/fabricated/enum_extensions.json")).get("entries")).getFirst();
			assertEquals(Map.of("enum", "net/minecraft/world/Kind", "name", "COOKING_POT", "constructor", "()V", "parameters", List.of()), entry);
			String config = overlay.readText("cooking.mixins.json");
			assertTrue(!config.contains("KindMixin") && config.contains("ThingMixin"), "only the enum mixin is left out: " + config);
		}
		Report.ModRow row = result.report().mods().stream().filter(r -> r.id().equals("stew")).findFirst().orElseThrow();
		assertEquals(Report.Status.DISABLED, row.status());
		assertTrue(row.detail().contains("extensible enums"), row.detail());
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

	/** Fabric Loader gives every mod a root, and mods take the first without checking (OneConfig's list of mods, POLY-1). */
	@Test
	void builtInModsHaveTheRootOfWhatProvidesThem(@TempDir Path game) throws Exception {
		TestJars.create().text("game.txt", "").write(game.resolve("minecraft.jar"));
		TestJars.create().text("in-game.txt", "").write(game.resolve("fabricated.jar"));
		Pipeline.Result result = Pipeline.run(input(game, List.of(), Namespace.OFFICIAL, Map.of(), null));
		Map<String, String> files = Map.of("minecraft", "game.txt", "fabricloader", "in-game.txt", "fabricated", "in-game.txt",
				"fabric-api-base", "in-game.txt");
		files.forEach((id, file) -> {
			ModContainer mod = result.runtime().getModContainer(id).orElseThrow();
			assertEquals(ModOrigin.Kind.PATH, mod.getOrigin().getKind(), id);
			assertTrue(Files.exists(mod.getRootPaths().getFirst().resolve(file)), id);
		});
		assertEquals(Path.of(System.getProperty("java.home")), result.runtime().getModContainer("java").orElseThrow().getRootPaths().getFirst());
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
		api.afterEntrypoints(() -> ran.add("third"));
		assertTrue(ran.isEmpty(), "waits for the entrypoints");
		List<Throwable> errors = new ArrayList<>();
		api.markEntrypointsComplete(Set.of("good-mod"), (action, error) -> errors.add(error));
		assertEquals(List.of("first", "third"), ran, "a throwing action does not stop the others");
		assertEquals(1, errors.size());
		assertEquals(FabricMod.Status.FAILED, api.fabricMod("good-mod").orElseThrow().status());
		api.afterEntrypoints(() -> ran.add("late"));
		assertEquals("late", ran.getLast(), "runs right away once the entrypoints ran");
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
	 * A required mixin NeoForge's code can't take, which nothing outside it calls into, is left out of its config and the
	 * mod loads without it (LEAVE-1); the report says so, in the mod's row too, which {@code /fabricated why} shows.
	 */
	@Test
	void aSelfContainedMixinNeoForgeCanNotTakeIsLeftOutAndTheModLoads(@TempDir Path game) throws Exception {
		Path jar = TestJars.create().fabricModJson(TestJars.modJson("partial-mod", "1.0", "\"mixins\":[\"partial.mixins.json\"]"))
				.text("partial.mixins.json", "{\"required\":true,\"package\":\"com.partial.mixin\",\"mixins\":[\"Works\",\"Broken\"]}")
				.bytes("com/partial/mixin/Works.class", mixinClass("com/partial/mixin/Works", "tick"))
				.bytes("com/partial/mixin/Broken.class", mixinClass("com/partial/mixin/Broken", "removedByNeoForge"))
				.write(game.resolve("mods/partial.jar"));
		Pipeline.Result result = Pipeline.run(input(game, List.of(jar), Namespace.OFFICIAL, Map.of(TARGET, targetClass()), null));
		String report = result.report().render();
		assertTrue(result.runtime().isModLoaded("partial-mod"), report);
		assertTrue(report.contains(Pipeline.LEFT_OUT_SECTION), report);
		assertTrue(report.contains("partial-mod: LEFT_OUT [partial.mixins.json com.partial.mixin.Broken]"), report);
		Report.ModRow row = result.report().mods().stream().filter(r -> r.id().equals("partial-mod")).findFirst().orElseThrow();
		assertTrue(row.detail().contains("1 mixin(s) left out"), row.detail());
		try (SafeArchive overlay = SafeArchive.open(result.mods().getFirst().translated().overlay(), ArchiveLimits.DEFAULTS)) {
			String config = new String(overlay.read("partial.mixins.json"), StandardCharsets.UTF_8);
			assertTrue(config.contains("Works") && !config.contains("Broken"), config);
		}
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
