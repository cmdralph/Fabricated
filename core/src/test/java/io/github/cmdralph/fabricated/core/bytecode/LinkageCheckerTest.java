package io.github.cmdralph.fabricated.core.bytecode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.cache.TranslationCache;
import io.github.cmdralph.fabricated.core.metadata.FabricModJsonParser;
import io.github.cmdralph.fabricated.core.resolve.Candidate;
import io.github.cmdralph.fabricated.core.testutil.TestJars;
import io.github.cmdralph.fabricated.core.translate.TranslatedMod;
import io.github.cmdralph.fabricated.core.translate.LinkageAnalysis;

class LinkageCheckerTest {
	private static byte[] caller(String name, String owner, String method, String desc, boolean clientOnly) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
		if (clientOnly) {
			var a = cw.visitAnnotation("Lnet/fabricmc/api/Environment;", true);
			a.visitEnum("value", "Lnet/fabricmc/api/EnvType;", "CLIENT");
			a.visitEnd();
		}
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "()V", null, null);
		mv.visitCode();
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, owner, method, desc, false);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static byte[] game() {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "net/minecraft/Util", null, "java/lang/Object", null);
		cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "present", "()V", null, null).visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static List<LinkageChecker.Issue> check(byte[] cls, boolean client) {
		ClassIndex index = new ClassIndex(Map.of("net/minecraft/Util", game(),
				"net/minecraft/Child", TestJars.emptyClass("net/minecraft/Child", "net/minecraft/Util"))::get);
		List<LinkageChecker.Issue> issues = new ArrayList<>();
		new LinkageChecker(index, LinkageAnalysis.CHECKED, client).check(cls, issues);
		return issues;
	}

	@Test
	void acceptsExistingAndInheritedMembers() {
		assertTrue(check(caller("m/A", "net/minecraft/Util", "present", "()V", false), false).isEmpty());
		assertTrue(check(caller("m/A", "net/minecraft/Child", "present", "()V", false), false).isEmpty());
		assertTrue(check(caller("m/A", "com/other/Lib", "whatever", "()V", false), false).isEmpty(), "unchecked namespaces are ignored");
	}

	@Test
	void reportsMissingMethodsAndClasses() {
		List<LinkageChecker.Issue> issues = check(caller("m/A", "net/minecraft/Util", "removed", "()V", false), false);
		assertEquals(1, issues.size());
		assertEquals("method", issues.getFirst().kind());
		List<LinkageChecker.Issue> classIssues = check(caller("m/A", "net/fabricmc/fabric/impl/Gone", "x", "()V", false), false);
		assertEquals("class", classIssues.getFirst().kind());
	}

	@Test
	void skipsClientOnlyCodeOnServer() {
		assertTrue(check(caller("m/A", "net/minecraft/Util", "removed", "()V", true), false).isEmpty());
		assertTrue(check(caller("m/A", "net/minecraft/client/Minecraft", "getInstance", "()V", false), false).isEmpty());
		assertEquals(1, check(caller("m/A", "net/minecraft/Util", "removed", "()V", true), true).size());
	}

	@Test
	void skipsClassesLeftInIntermediaryNamesOnServer() {
		// A 1.21.11 server's mappings name only the classes the server has: a client class (here Minecraft's client
		// class) keeps its Intermediary name, so code using it is client code.
		assertTrue(check(caller("m/A", "net/minecraft/class_310", "method_1551", "()V", false), false).isEmpty());
		assertTrue(check(caller("m/A", "net/minecraft/class_310$class_311", "x", "()V", false), false).isEmpty());
		assertEquals(1, check(caller("m/A", "net/minecraft/class_310", "method_1551", "()V", false), true).size(),
				"on a client every class is named, so an unnamed one is missing");
	}

	@Test
	void resolvesMethodsOfInjectedInterfaces() {
		// A mod injects m/Ext (with default method ext()V) into net/minecraft/Util and calls it on Util.
		ClassWriter iface = new ClassWriter(0);
		iface.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, "m/Ext", null, "java/lang/Object", null);
		iface.visitMethod(Opcodes.ACC_PUBLIC, "ext", "()V", null, null).visitEnd();
		iface.visitEnd();
		Map<String, byte[]> classes = Map.of("net/minecraft/Util", game(), "m/Ext", iface.toByteArray());
		byte[] call = caller("m/A", "net/minecraft/Util", "ext", "()V", false);

		List<LinkageChecker.Issue> without = new ArrayList<>();
		new LinkageChecker(new ClassIndex(classes::get), LinkageAnalysis.CHECKED, false).check(call, without);
		assertEquals(1, without.size(), "without the injection the method is missing");

		List<LinkageChecker.Issue> with = new ArrayList<>();
		new LinkageChecker(new ClassIndex(classes::get, Map.of("net/minecraft/Util", List.of("m/Ext"))), LinkageAnalysis.CHECKED, false)
				.check(call, with);
		assertTrue(with.isEmpty(), with.toString());
	}

	@Test
	void membersMixinsAddCountAsPresent() {
		// A mixin into net/minecraft/Util adds field "added" and method extra()V, and shadows "count".
		ClassWriter mixin = new ClassWriter(0);
		mixin.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "m/UtilMixin", null, "java/lang/Object", null);
		AnnotationVisitor ann = mixin.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor values = ann.visitArray("value");
		values.visit(null, Type.getObjectType("net/minecraft/Util"));
		values.visitEnd();
		ann.visitEnd();
		mixin.visitField(Opcodes.ACC_PUBLIC, "added", "Ljava/util/Map;", null, null).visitEnd();
		var shadow = mixin.visitField(Opcodes.ACC_PRIVATE, "count", "I", null, null);
		shadow.visitAnnotation("Lorg/spongepowered/asm/mixin/Shadow;", true).visitEnd();
		shadow.visitEnd();
		mixin.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "extra", "()V", null, null).visitEnd();
		mixin.visitEnd();

		Map<String, List<String>> added = new java.util.HashMap<>();
		LinkageAnalysis.mixinAdditions(mixin.toByteArray(), added);
		assertEquals(List.of("added:Ljava/util/Map;", "extra()V"), added.get("net/minecraft/Util"));

		ClassIndex index = new ClassIndex(Map.of("net/minecraft/Util", game())::get, added);
		assertEquals(ClassIndex.Resolution.FOUND, index.resolveField("net/minecraft/Util", "added:Ljava/util/Map;"));
		assertEquals(ClassIndex.Resolution.FOUND, index.resolveMethod("net/minecraft/Util", "extra()V"));
		assertEquals(ClassIndex.Resolution.MISSING, index.resolveField("net/minecraft/Util", "added:I"), "fields resolve by name and type");
	}

	private static TranslatedMod mod(Path dir, String id, TestJars jar) throws Exception {
		Path file = jar.fabricModJson(TestJars.modJson(id, "1.0", "")).write(dir.resolve(id + ".jar"));
		Candidate c = new Candidate(file, id + "-hash", FabricModJsonParser.parse(TestJars.modJson(id, "1.0", "")), id + ".jar", null, 0,
				List.of(), Set.of());
		return new TranslatedMod(c, id.replace('-', '_'), file, file, List.of(), 0, List.of(), List.of(), List.of(), Set.of(), Set.of(), false, 0);
	}

	@Test
	void interfacesImplementedByMixinsCountAsPresent(@TempDir Path dir) throws Exception {
		// Mod "ext" adds m/Ext (default method ext()V) to net/minecraft/Util through a mixin that implements it,
		// as Fabric API modules did before classtweaker interface injection; mod "user" calls Util.ext().
		ClassWriter iface = new ClassWriter(0);
		iface.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, "m/Ext", null, "java/lang/Object", null);
		iface.visitMethod(Opcodes.ACC_PUBLIC, "ext", "()V", null, null).visitEnd();
		iface.visitEnd();
		ClassWriter mixin = new ClassWriter(0);
		mixin.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "m/UtilMixin", null, "java/lang/Object", new String[] {"m/Ext"});
		AnnotationVisitor ann = mixin.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor values = ann.visitArray("value");
		values.visit(null, Type.getObjectType("net/minecraft/Util"));
		values.visitEnd();
		ann.visitEnd();
		mixin.visitEnd();

		TranslatedMod ext = mod(dir, "ext", TestJars.create().bytes("m/Ext.class", iface.toByteArray()).bytes("m/UtilMixin.class", mixin.toByteArray()));
		TranslatedMod user = mod(dir, "user", TestJars.create().bytes("u/A.class", caller("u/A", "net/minecraft/Util", "ext", "()V", false)));
		Map<String, byte[]> gameClasses = Map.of("net/minecraft/Util", game());
		Map<String, byte[]> modClasses = Map.of("m/Ext", iface.toByteArray());
		ClassBytesSource runtime = name -> gameClasses.containsKey(name) ? gameClasses.get(name) : modClasses.get(name);

		LinkageAnalysis analysis = new LinkageAnalysis(new TranslationCache(dir.resolve("game"), true, true), ArchiveLimits.DEFAULTS, false);
		assertTrue(analysis.analyse(List.of(ext, user), runtime, Map::of, "with-mixin").isEmpty());
		assertEquals(1, analysis.analyse(List.of(user), runtime, Map::of, "without-mixin").size(), "without the mixin the call is unresolved");
	}

	/**
	 * Fabric Loader's classes are Fabricated's own: its public API and the internals mods commonly use resolve, and a
	 * mod reaching for any other internal (Essential Loader's way of adding mods) is reported.
	 */
	@Test
	void fabricLoaderReferencesAreCheckedAgainstFabricatedsOwn(@TempDir Path dir) throws Exception {
		TranslatedMod api = mod(dir, "api-user", TestJars.create().bytes("u/A.class",
				caller("u/A", "net/fabricmc/loader/api/FabricLoader", "getInstance", "()Lnet/fabricmc/loader/api/FabricLoader;", false)));
		TranslatedMod internals = mod(dir, "internals-user", TestJars.create().bytes("u/B.class",
				caller("u/B", "net/fabricmc/loader/impl/ModContainerImpl", "create", "()V", false)));
		// FrozenLib's entrypoint injector names these internals, from a mixin plugin: they must link (AT-1's pass).
		ClassWriter metadata = new ClassWriter(0);
		metadata.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "u/Metadata", null, "java/lang/Object",
				new String[] {"net/fabricmc/loader/impl/metadata/EntrypointMetadata"});
		metadata.visitEnd();
		TranslatedMod injector = mod(dir, "injector", TestJars.create().bytes("u/Metadata.class", metadata.toByteArray()));
		LinkageAnalysis analysis = new LinkageAnalysis(new TranslationCache(dir.resolve("game"), true, true), ArchiveLimits.DEFAULTS, false);

		Map<String, List<String>> issues = analysis.analyse(List.of(api, internals, injector), ClassBytesSource.EMPTY, Map::of, "loader");

		assertEquals(Set.of("internals_user"), issues.keySet(), issues.toString());
		assertTrue(issues.get("internals_user").getFirst().contains("net.fabricmc.loader.impl.ModContainerImpl"), issues.toString());
		assertTrue(LinkageAnalysis.isLoaderIssue(issues.get("internals_user").getFirst()));
		assertTrue(!LinkageAnalysis.isLoaderIssue("net.fabricmc.fabric.impl.X -> missing method net.minecraft.Util.removed()V"),
				"a Fabric API module's class names Fabric API, not the loader");
	}
}
