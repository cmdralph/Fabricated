package io.github.cmdralph.fabricated.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import io.github.cmdralph.fabricated.core.api.FabricatedApiImpl;
import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.archive.SafeArchive;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.cache.TranslationCache;
import io.github.cmdralph.fabricated.core.config.FabricatedConfig;
import io.github.cmdralph.fabricated.core.metadata.FabricModJsonParser;
import io.github.cmdralph.fabricated.core.remap.MappingComposer;
import io.github.cmdralph.fabricated.core.remap.MappingTable;
import io.github.cmdralph.fabricated.core.report.Report;
import io.github.cmdralph.fabricated.core.resolve.Candidate;
import io.github.cmdralph.fabricated.core.resolve.FabricApiPlan;
import io.github.cmdralph.fabricated.core.testutil.TestJars;
import io.github.cmdralph.fabricated.core.translate.TranslationEnvironment.Namespace;

class PipelineTest {
	private static final String TARGET = "net/minecraft/world/Thing";

	private static final String PLAN = """
			{"fabricApiVersion": "0.161.0+26.2", "modules": {
			  "fabric-api-base": {"plan": "native", "version": "1.0.0"},
			  "fabric-renderer-indigo": {"plan": "unsupported", "reason": "replaces NeoForge's model pipeline"},
			  "fabric-item-api-v1": {"plan": "passthrough", "skipMixins": ["item.mixins.json:Conflicting"], "reason": "test"}
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
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, name, null, "java/lang/Object", null);
		AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor arr = m.visitArray("value");
		arr.visit(null, Type.getObjectType(TARGET));
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
				.bytes("com/bad/mixin/BadMixin.class", mixinClass("com/bad/mixin/BadMixin", "removedByNeoForge"))
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
		TestJars.create().fabricModJson(TestJars.modJson("dup-c", "1.0", ""))
				.bytes("com/conflict/Thing.class", TestJars.emptyClass("com/conflict/Thing", "java/lang/Number"))
				.write(mods.resolve("dup-c.jar"));

		TestJars.create().fabricModJson(TestJars.modJson("fabric-api-base", "0.4.0", "")).write(mods.resolve("fapi-base.jar"));
		TestJars.create().fabricModJson(TestJars.modJson("client-only", "1.0", "\"environment\":\"client\"")).write(mods.resolve("client-only.jar"));
		Files.writeString(mods.resolve("broken.jar"), "not a zip");
	}

	private static Pipeline.Input input(Path game, List<Path> jars, Namespace ns, Map<String, byte[]> gameClasses, Pipeline.MappingSupplier mappings) throws Exception {
		FabricatedConfig config = FabricatedConfig.load(game.resolve("config"));
		TranslationCache cache = new TranslationCache(game, config.cacheEnabled(), config.verifyIntegrity());
		return new Pipeline.Input(game, game.resolve("config"), config, cache, jars, ns == Namespace.OFFICIAL ? "26.2" : "1.21.11",
				"26.2.0.90", false, ns, "0.17.4+mixin.0.8.7", "0.17.1", "0.1.0-test", false, gameClasses::get, ClassBytesSource.EMPTY, Set.of("net/neoforged/neoforge"),
				FabricApiPlan.parse(PLAN), name -> null, Map::of, List.of(new Pipeline.NeoForgeMod("cloth_config", "15.0.0", "Cloth Config")),
				mappings, new String[0]);
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
		assertEquals(Set.of("good-mod", "holder", "inner-lib", "dup-a", "dup-b", "res-a", "res-b"), loaded, result.report().render());
		Pipeline.LoadedMod resB = result.mods().stream().filter(m -> m.translated().candidate().id().equals("res-b")).findFirst().orElseThrow();
		assertEquals(Set.of("licenses"), resB.excludedPackages(), "clashing resource-only directory is hidden, not fatal");
		assertEquals(Report.Status.DISABLED, status.get("needs-missing"));
		assertTrue(result.dependencyFailures().contains("needs-missing"), result.dependencyFailures().toString());
		assertEquals(Report.Status.DISABLED, status.get("bad-mixin"));
		assertEquals(Report.Status.DISABLED, status.get("dup-c"));
		assertEquals(Report.Status.REJECTED, status.get("broken.jar"));
		assertEquals(Report.Status.SKIPPED, status.get("client-only"));
		assertTrue(result.report().mods().stream().anyMatch(r -> r.id().equals("fabric-api-base") && r.status() == Report.Status.PROVIDED_NATIVELY));
		assertTrue(result.report().mods().stream().anyMatch(r -> r.id().equals("fabric-api-base") && r.status() == Report.Status.SKIPPED),
				"the bundled upstream module jar is skipped in favour of the native one");

		Pipeline.LoadedMod good = result.mods().stream().filter(m -> m.translated().candidate().id().equals("good-mod")).findFirst().orElseThrow();
		assertEquals(Set.of("com/google/gson"), good.excludedPackages(), "bundled platform library is filtered");
		Pipeline.LoadedMod dupB = result.mods().stream().filter(m -> m.translated().candidate().id().equals("dup-b")).findFirst().orElseThrow();
		assertEquals(Set.of("com/shared"), dupB.excludedPackages(), "identical shared package uses the first copy");

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
		return new Candidate(Path.of(id + ".jar"), "00", FabricModJsonParser.parse(TestJars.modJson(id, "1.0", extraJson)), id + ".jar", null, 0, List.of());
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

	private static Path patchTarget(Path game) throws Exception {
		return TestJars.create().fabricModJson(TestJars.modJson("target-mod", "2.0", "\"mixins\":[\"target.mixins.json\"]"))
				.text("target.mixins.json", "{\"required\":true,\"package\":\"com.target.mixin\",\"mixins\":[\"BadMixin\"]}")
				.bytes("com/target/mixin/BadMixin.class", mixinClass("com/target/mixin/BadMixin", "removedByNeoForge"))
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
}
