package io.github.cmdralph.fabricated.core.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.github.cmdralph.fabricated.core.archive.Hashing;
import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;
import io.github.cmdralph.fabricated.core.bytecode.InstalledClasses;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding.Severity;

class MixinPreflightTest {
	private static final String TARGET = "net/minecraft/world/Thing";
	private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";

	/** Game class: tick() calls helper(I)V once and reads field "count". */
	private static byte[] targetClass() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		cw.visitField(Opcodes.ACC_PRIVATE, "count", "I", null, null).visitEnd();
		MethodVisitor tick = cw.visitMethod(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
		tick.visitCode();
		tick.visitVarInsn(Opcodes.ALOAD, 0);
		tick.visitVarInsn(Opcodes.ALOAD, 0);
		tick.visitFieldInsn(Opcodes.GETFIELD, TARGET, "count", "I");
		tick.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "helper", "(I)V", false);
		tick.visitInsn(Opcodes.RETURN);
		tick.visitMaxs(0, 0);
		tick.visitEnd();
		MethodVisitor helper = cw.visitMethod(Opcodes.ACC_PRIVATE, "helper", "(I)V", null, null);
		helper.visitCode();
		Label start = new Label();
		Label end = new Label();
		helper.visitLabel(start);
		helper.visitInsn(Opcodes.RETURN);
		helper.visitLabel(end);
		helper.visitLocalVariable("this", "L" + TARGET + ";", null, start, end, 0);
		helper.visitLocalVariable("amount", "I", null, start, end, 1);
		helper.visitMaxs(0, 2);
		helper.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** {@link #TARGET} with tick() delegating to tick(I), which alone calls helper(I)V: NeoForge keeps old signatures so. */
	private static byte[] overloadedTarget() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor old = cw.visitMethod(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
		old.visitCode();
		old.visitVarInsn(Opcodes.ALOAD, 0);
		old.visitInsn(Opcodes.ICONST_0);
		old.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "tick", "(I)V", false);
		old.visitInsn(Opcodes.RETURN);
		old.visitMaxs(0, 0);
		old.visitEnd();
		MethodVisitor current = cw.visitMethod(Opcodes.ACC_PUBLIC, "tick", "(I)V", null, null);
		current.visitCode();
		current.visitVarInsn(Opcodes.ALOAD, 0);
		current.visitVarInsn(Opcodes.ILOAD, 1);
		current.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "helper", "(I)V", false);
		current.visitInsn(Opcodes.RETURN);
		current.visitMaxs(0, 0);
		current.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** A mixin into {@code target} shadowing its int field {@code field}. */
	private static byte[] shadowMixin(String name, String target, String field) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, name, null, "java/lang/Object", null);
		AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor values = m.visitArray("value");
		values.visit(null, Type.getObjectType(target));
		values.visitEnd();
		m.visitEnd();
		FieldVisitor shadow = cw.visitField(Opcodes.ACC_PRIVATE, field, "I", null, null);
		shadow.visitAnnotation("Lorg/spongepowered/asm/mixin/Shadow;", false).visitEnd();
		shadow.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * A Fabric addon's mixins into the NeoForge build of the mod it extends are checked against that build (and the
	 * classes read are recorded for the cache); a target no installed jar has is not judged.
	 */
	@Test
	void checksMixinsIntoTheClassesOfInstalledMods() throws Exception {
		String renderer = "net/example/sodium/Renderer";
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, renderer, null, "java/lang/Object", null);
		cw.visitField(Opcodes.ACC_PRIVATE, "sections", "I", null, null).visitEnd();
		cw.visitEnd();
		byte[] rendererClass = cw.toByteArray();
		InstalledClasses installed = new InstalledClasses(List.of(new InstalledClasses.Jar("Sodium", renderer::equals,
				name -> name.equals(renderer) ? rendererClass : null)));
		Map<String, byte[]> mod = Map.of(
				"com/example/mixin/Present", shadowMixin("com/example/mixin/Present", renderer, "sections"),
				"com/example/mixin/Gone", shadowMixin("com/example/mixin/Gone", renderer, "chunks"),
				"com/example/mixin/Elsewhere", shadowMixin("com/example/mixin/Elsewhere", "net/example/other/Thing", "x"));
		MixinPreflight preflight = new MixinPreflight(new ClassIndex(installed), installed, mod::get, ClassBytesSource.EMPTY, false, true);

		List<PreflightFinding> findings = preflight.check("test.mixins.json", MixinConfigFile.parse(config(true, "Present", "Gone", "Elsewhere")),
				Refmap.EMPTY, Set.of());

		assertEquals(2, findings.size(), findings.toString());
		PreflightFinding gone = findings.stream().filter(f -> f.mixin().endsWith("Gone")).findFirst().orElseThrow();
		assertEquals(Severity.FAIL, gone.severity());
		assertTrue(gone.suggestion().startsWith("This version of Sodium changed net.example.sodium.Renderer"), gone.suggestion());
		assertEquals(Severity.INFO, findings.stream().filter(f -> f.mixin().endsWith("Elsewhere")).findFirst().orElseThrow().severity());
		// What the result depends on: the installed class it read, and the target no installed jar has (yet).
		assertEquals(Map.of(renderer, Hashing.sha256(rendererClass), "net/example/other/Thing", ""), preflight.installedClasses());
	}

	@Test
	void aSelectorWithoutDescriptorTargetsTheFirstOverloadOnly() throws Exception {
		ClassIndex game = new ClassIndex(Map.of(TARGET, overloadedTarget())::get);
		Map<String, byte[]> mod = Map.of(
				"com/example/mixin/ByName", mixin("com/example/mixin/ByName", false, cw -> inject(cw, "h", "tick", "INVOKE", "Lnet/minecraft/world/Thing;helper(I)V", 1, null)),
				"com/example/mixin/ByDesc", mixin("com/example/mixin/ByDesc", false, cw -> inject(cw, "h", "tick(I)V", "INVOKE", "Lnet/minecraft/world/Thing;helper(I)V", 1, null)));
		MixinPreflight preflight = preflight(game, mod::get, ClassBytesSource.EMPTY, false);
		List<PreflightFinding> byName = preflight.check("test.mixins.json", MixinConfigFile.parse(config(true, "ByName")), Refmap.EMPTY, Set.of());
		assertEquals(1, byName.size(), "Mixin picks tick()V, which has no such call: " + byName);
		assertTrue(preflight.check("test.mixins.json", MixinConfigFile.parse(config(true, "ByDesc")), Refmap.EMPTY, Set.of()).isEmpty());
	}

	@Test
	void anInjectorIsPointedAtTheOverloadNeoForgeMovedTheCodeInto() throws Exception {
		ClassIndex game = new ClassIndex(Map.of(TARGET, overloadedTarget())::get);
		String ci = "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
		Map<String, byte[]> mod = new HashMap<>(Map.of(
				"com/example/mixin/Movable", mixin("com/example/mixin/Movable", false, cw -> injectWith(cw, ci)),
				"com/example/mixin/TakesArgs", mixin("com/example/mixin/TakesArgs", false, cw -> injectWith(cw, "()V"))));
		MixinPreflight preflight = preflight(game, name -> mod.get(name), ClassBytesSource.EMPTY, false);
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Movable", "TakesArgs"));
		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		assertEquals(Set.of("com/example/mixin/Movable.class"), repaired.keySet(), "only a handler taking just the callback info moves");
		assertTrue(notes.getFirst().message().contains("moved into tick(I)V"), notes.toString());
		mod.put("com/example/mixin/Movable", repaired.get("com/example/mixin/Movable.class"));
		List<PreflightFinding> after = preflight(game, name -> mod.get(name), ClassBytesSource.EMPTY, false)
				.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertEquals(1, after.size(), "the moved injector applies; the other still fails: " + after);
		assertTrue(after.getFirst().mixin().endsWith("TakesArgs"), after.toString());
	}

	/**
	 * {@link #TARGET} whose first constructor, {@code <init>()V}, only calls {@code this(0)}: as NeoForge keeps vanilla's
	 * signature when it adds a parameter ({@code deprecated}), or as vanilla itself delegates (not deprecated).
	 */
	private static byte[] delegatingConstructors(boolean deprecated) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor kept = cw.visitMethod(Opcodes.ACC_PUBLIC | (deprecated ? Opcodes.ACC_DEPRECATED : 0), "<init>", "()V", null, null);
		if (deprecated) kept.visitAnnotation("Ljava/lang/Deprecated;", true).visitEnd();
		kept.visitCode();
		kept.visitVarInsn(Opcodes.ALOAD, 0);
		kept.visitInsn(Opcodes.ICONST_0);
		kept.visitMethodInsn(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(I)V", false);
		kept.visitInsn(Opcodes.RETURN);
		kept.visitMaxs(0, 0);
		kept.visitEnd();
		MethodVisitor current = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(I)V", null, null);
		current.visitCode();
		current.visitVarInsn(Opcodes.ALOAD, 0);
		current.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		current.visitInsn(Opcodes.RETURN);
		current.visitMaxs(0, 0);
		current.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** An {@code @Inject} into {@code <init>} (no descriptor, so the first constructor) at {@code point}. */
	private static void injectIntoConstructor(ClassWriter cw, String point) {
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "h", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null);
		AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
		AnnotationVisitor methods = ann.visitArray("method");
		methods.visit(null, "<init>");
		methods.visitEnd();
		AnnotationVisitor ats = ann.visitArray("at");
		AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
		at.visit("value", point);
		at.visitEnd();
		ats.visitEnd();
		ann.visitEnd();
		mv.visitEnd();
	}

	@Test
	void anInjectionAtTheEndOfAConstructorNeoForgeKeptMovesToTheOneItCalls() throws Exception {
		Map<String, byte[]> mod = Map.of(
				"com/example/mixin/AtReturn", mixin("com/example/mixin/AtReturn", false, cw -> injectIntoConstructor(cw, "RETURN")),
				"com/example/mixin/AtHead", mixin("com/example/mixin/AtHead", false, cw -> injectIntoConstructor(cw, "HEAD")));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "AtReturn", "AtHead"));

		ClassIndex neoForge = new ClassIndex(Map.of(TARGET, delegatingConstructors(true))::get);
		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight(neoForge, mod::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		assertEquals(Set.of("com/example/mixin/AtReturn.class"), repaired.keySet(), "only the end of the constructor is the same moment: " + notes);
		assertTrue(notes.getFirst().message().contains("which NeoForge keeps only to call <init>(I)V"), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get("com/example/mixin/AtReturn.class")).accept(node, 0);
		AnnotationNode inject = node.methods.stream().filter(m -> m.name.equals("h")).findFirst().orElseThrow().invisibleAnnotations.getFirst();
		assertEquals(List.of("<init>(I)V"), Annotations.value(inject, "method"));

		ClassIndex vanilla = new ClassIndex(Map.of(TARGET, delegatingConstructors(false))::get);
		assertTrue(preflight(vanilla, mod::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty(),
				"a constructor vanilla delegates itself is the one the mixin means");
	}

	/**
	 * {@link #TARGET} as NeoForge makes {@code BakedQuad}: a record whose vanilla constructor {@code <init>(IJ)V} is kept,
	 * not deprecated, as {@code this(i, j, Extra.DEFAULT)} with a component of a NeoForge type appended ({@code neoForge}),
	 * or of a vanilla type (as vanilla's own delegating constructors pass).
	 */
	private static byte[] appendedComponent(boolean neoForge) {
		String extra = neoForge ? "Lnet/neoforged/neoforge/client/model/quad/BakedColors;" : "Lnet/minecraft/world/Other;";
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, TARGET, null, "java/lang/Record", null);
		MethodVisitor kept = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(IJ)V", null, null);
		kept.visitCode();
		kept.visitVarInsn(Opcodes.ALOAD, 0);
		kept.visitVarInsn(Opcodes.ILOAD, 1);
		kept.visitVarInsn(Opcodes.LLOAD, 2);
		kept.visitFieldInsn(Opcodes.GETSTATIC, Type.getType(extra).getInternalName(), "DEFAULT", extra);
		kept.visitMethodInsn(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(IJ" + extra + ")V", false);
		kept.visitInsn(Opcodes.RETURN);
		kept.visitMaxs(0, 0);
		kept.visitEnd();
		MethodVisitor canonical = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(IJ" + extra + ")V", null, null);
		canonical.visitCode();
		canonical.visitVarInsn(Opcodes.ALOAD, 0);
		canonical.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Record", "<init>", "()V", false);
		canonical.visitInsn(Opcodes.RETURN);
		canonical.visitMaxs(0, 0);
		canonical.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** Sodium's {@code BakedQuadMixin}: an {@code @Inject} at the end of {@code <init>(IJ)V} whose handler takes its parameters. */
	private static void injectTakingConstructorParameters(ClassWriter cw) {
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "init", "(IJLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null);
		AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
		AnnotationVisitor methods = ann.visitArray("method");
		methods.visit(null, "<init>(IJ)V");
		methods.visitEnd();
		AnnotationVisitor ats = ann.visitArray("at");
		AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
		at.visit("value", "RETURN");
		at.visitEnd();
		ats.visitEnd();
		ann.visitEnd();
		mv.visitCode();
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 5);
		mv.visitEnd();
	}

	@Test
	void anInjectionAtTheEndOfAConstructorNeoForgeAppendedComponentsToMovesThroughABridge() throws Exception {
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Quad", mixin("com/example/mixin/Quad", false, MixinPreflightTest::injectTakingConstructorParameters)));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Quad"));
		ClassIndex neoForge = new ClassIndex(Map.of(TARGET, appendedComponent(true))::get);
		List<PreflightFinding> notes = new ArrayList<>();

		Map<String, byte[]> repaired = preflight(neoForge, mod::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);

		assertEquals(Set.of("com/example/mixin/Quad.class"), repaired.keySet(), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get("com/example/mixin/Quad.class")).accept(node, 0);
		MethodNode handler = node.methods.stream().filter(m -> m.name.equals("init")).findFirst().orElseThrow();
		MethodNode bridge = node.methods.stream().filter(m -> m.name.equals("init$fabricated")).findFirst().orElseThrow();
		assertTrue(handler.invisibleAnnotations == null || handler.invisibleAnnotations.isEmpty(), "the injector moved to the bridge");
		assertEquals("(IJLnet/neoforged/neoforge/client/model/quad/BakedColors;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", bridge.desc);
		assertEquals(List.of("<init>(IJLnet/neoforged/neoforge/client/model/quad/BakedColors;)V"),
				Annotations.value(bridge.invisibleAnnotations.getFirst(), "method"));
		// The bridge passes its own first parameters and the callback info on, skipping the appended one.
		List<Integer> loads = new ArrayList<>();
		for (AbstractInsnNode insn : bridge.instructions) {
			if (insn instanceof VarInsnNode load) loads.add(load.var);
		}
		assertEquals(List.of(0, 1, 2, 5), loads);
		mod.put("com/example/mixin/Quad", repaired.get("com/example/mixin/Quad.class"));
		assertTrue(preflight(neoForge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());

		ClassIndex vanillaTypes = new ClassIndex(Map.of(TARGET, appendedComponent(false))::get);
		assertTrue(preflight(vanillaTypes, Map.of("com/example/mixin/Quad", mixin("com/example/mixin/Quad", false,
						MixinPreflightTest::injectTakingConstructorParameters))::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty(),
				"a delegate appending vanilla types may be vanilla's own");
	}

	/** A member named as mixins name what they add ({@code modid$name}) may come from another mod's mixin: not judged. */
	@Test
	void membersAnotherModsMixinMayAddAreNotJudged() throws Exception {
		ClassIndex game = new ClassIndex(Map.of(TARGET, targetClass())::get);
		Map<String, byte[]> mod = Map.of(
				"com/example/mixin/Added", mixin("com/example/mixin/Added", false, cw -> inject(cw, "h", "sodium$setActive", "HEAD", null, 1, null)),
				"com/example/mixin/Gone", mixin("com/example/mixin/Gone", false, cw -> inject(cw, "h", "setActive", "HEAD", null, 1, null)));
		List<PreflightFinding> findings = preflight(game, mod::get, ClassBytesSource.EMPTY, false)
				.check("test.mixins.json", MixinConfigFile.parse(config(true, "Added", "Gone")), Refmap.EMPTY, Set.of());

		assertEquals(Severity.INFO, findings.stream().filter(f -> f.mixin().endsWith("Added")).findFirst().orElseThrow().severity(), findings.toString());
		assertEquals(Severity.FAIL, findings.stream().filter(f -> f.mixin().endsWith("Gone")).findFirst().orElseThrow().severity(), findings.toString());
		assertTrue(MixinPreflight.addedByAMixin("iris$flag"));
		for (String javac : List.of("lambda$tick$0", "access$000", "this$0", "val$player", "tick")) {
			assertFalse(MixinPreflight.addedByAMixin(javac), javac);
		}
	}

	/** A library interface named {@code name}, extending {@code parent} (or none), declaring {@code parse(Object)}. */
	private static byte[] libraryInterface(String name, String parent) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, name, null, "java/lang/Object",
				parent == null ? null : new String[] {parent});
		cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "parse", "(Ljava/lang/Object;)Ljava/lang/Object;", null, null).visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** {@link #TARGET} whose {@code load} calls {@code parse} through {@code owner}. */
	private static byte[] callingThrough(String owner) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor load = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "load", "(L" + owner + ";)V", null, null);
		load.visitCode();
		load.visitVarInsn(Opcodes.ALOAD, 0);
		load.visitInsn(Opcodes.ACONST_NULL);
		load.visitMethodInsn(Opcodes.INVOKEINTERFACE, owner, "parse", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
		load.visitInsn(Opcodes.POP);
		load.visitInsn(Opcodes.RETURN);
		load.visitMaxs(0, 0);
		load.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	@Test
	void anInjectionAtACallMadeThroughASubtypeIsPointedThere() throws Exception {
		String decoder = "com/example/lib/Decoder";
		Map<String, byte[]> libraries = Map.of(decoder, libraryInterface(decoder, null),
				"com/example/lib/Codec", libraryInterface("com/example/lib/Codec", decoder),
				"com/example/lib/Unrelated", libraryInterface("com/example/lib/Unrelated", null));
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/AtParse", mixin("com/example/mixin/AtParse", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "h", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null);
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "load");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
			at.visit("value", "INVOKE");
			at.visit("target", "L" + decoder + ";parse(Ljava/lang/Object;)Ljava/lang/Object;");
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnd();
			mv.visitEnd();
		})));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "AtParse"));

		ClassIndex neoForge = new ClassIndex(Map.of(TARGET, callingThrough("com/example/lib/Codec"))::get);
		assertEquals(1, preflight(neoForge, mod::get, libraries::get, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).size(),
				"Mixin matches the call's owner exactly");
		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight(neoForge, mod::get, libraries::get, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		assertTrue(notes.getFirst().message().contains("calls through com.example.lib.Codec"), notes.toString());
		mod.put("com/example/mixin/AtParse", repaired.get("com/example/mixin/AtParse.class"));
		assertTrue(preflight(neoForge, mod::get, libraries::get, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());

		ClassIndex unrelated = new ClassIndex(Map.of(TARGET, callingThrough("com/example/lib/Unrelated"))::get);
		assertTrue(preflight(unrelated, mod::get, libraries::get, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty(), "a call on another type is another method");
	}

	@Test
	void aHardLocalCaptureSkipsItselfInsteadOfCrashing() throws Exception {
		ClassIndex game = new ClassIndex(Map.of(TARGET, targetClass())::get);
		byte[] capturing = mixin("com/example/mixin/Capturing", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "h", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;I)V", null, null);
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "tick");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;").visit("value", "HEAD");
			ats.visitEnd();
			ann.visitEnum("locals", "Lorg/spongepowered/asm/mixin/injection/callback/LocalCapture;", "CAPTURE_FAILHARD");
			ann.visit("require", 1);
			ann.visitEnd();
			mv.visitEnd();
		});
		MixinPreflight preflight = preflight(game, Map.of("com/example/mixin/Capturing", capturing)::get, ClassBytesSource.EMPTY, false);
		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = preflight.repairs("test.mixins.json", MixinConfigFile.parse(config(true, "Capturing")), Refmap.EMPTY, Set.of(), notes)
				.get("com/example/mixin/Capturing.class");
		ClassNode node = new ClassNode();
		new ClassReader(repaired).accept(node, 0);
		AnnotationNode inject = node.methods.stream().filter(m -> m.name.equals("h")).findFirst().orElseThrow().invisibleAnnotations.getFirst();
		assertEquals("CAPTURE_FAILSOFT", ((String[]) Annotations.value(inject, "locals"))[1]);
		assertEquals(0, Annotations.value(inject, "require"));
		assertEquals(Severity.INFO, notes.getFirst().severity(), notes.toString());
	}

	@Test
	void aModifiedConstantMustOccurInTheTarget() throws Exception {
		ClassIndex game = new ClassIndex(Map.of(TARGET, overloadedTarget())::get);
		java.util.function.IntFunction<byte[]> modifying = value -> mixin("com/example/mixin/Constants", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "h", "(I)I", null, null);
			AnnotationVisitor ann = mv.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/ModifyConstant;", false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "tick()V");
			methods.visitEnd();
			AnnotationVisitor constants = ann.visitArray("constant");
			AnnotationVisitor constant = constants.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/Constant;");
			constant.visit("intValue", value);
			constant.visitEnd();
			constants.visitEnd();
			ann.visit("require", 1);
			ann.visitEnd();
			mv.visitEnd();
		});
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Constants"));
		assertTrue(preflight(game, Map.of("com/example/mixin/Constants", modifying.apply(0))::get, ClassBytesSource.EMPTY, false)
				.check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty(), "tick()V pushes 0");
		List<PreflightFinding> missing = preflight(game, Map.of("com/example/mixin/Constants", modifying.apply(7))::get, ClassBytesSource.EMPTY, false)
				.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertEquals(1, missing.size(), missing.toString());
		assertEquals(Severity.FAIL, missing.getFirst().severity());
		assertTrue(missing.getFirst().message().contains("expects the constant 7"), missing.getFirst().message());
	}

	@Test
	void anInjectIsPinnedToTheOverloadItsHandlerWasWrittenFor() throws Exception {
		ClassIndex game = new ClassIndex(Map.of(TARGET, overloadedTarget())::get);
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Pinned", mixin("com/example/mixin/Pinned", false,
				cw -> injectWith(cw, "(ILorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V"))));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Pinned"));
		assertEquals(1, preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).size(),
				"Mixin picks tick()V, whose parameters the handler doesn't take");
		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight(game, mod::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		assertTrue(notes.getFirst().message().contains("pinned"), notes.toString());
		mod.put("com/example/mixin/Pinned", repaired.get("com/example/mixin/Pinned.class"));
		List<PreflightFinding> after = preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());
	}

	@Test
	void aWrapMethodIsPinnedOffAnAbstractOverload() throws Exception {
		ClassWriter target = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		target.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, TARGET, null, "java/lang/Object", null);
		target.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "create", "()Ljava/lang/Object;", null, null).visitEnd();
		MethodVisitor concrete = target.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "create", "(I)Ljava/lang/Object;", null, null);
		concrete.visitCode();
		concrete.visitInsn(Opcodes.ACONST_NULL);
		concrete.visitInsn(Opcodes.ARETURN);
		concrete.visitMaxs(0, 0);
		concrete.visitEnd();
		target.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(TARGET, target.toByteArray())::get);
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Wrap", mixin("com/example/mixin/Wrap", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "h", "(ILcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)Ljava/lang/Object;", null, null);
			AnnotationVisitor ann = mv.visitAnnotation("Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;", false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "create");
			methods.visitEnd();
			ann.visitEnd();
			mv.visitEnd();
		})));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Wrap"));
		List<PreflightFinding> before = preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(before.size() == 1 && before.getFirst().severity() == Severity.FAIL && before.getFirst().message().contains("abstract")
				&& before.getFirst().handler().equals("h(ILcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)Ljava/lang/Object;"), before.toString());
		List<PreflightFinding> notes = new ArrayList<>();
		mod.put("com/example/mixin/Wrap", preflight(game, mod::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes).get("com/example/mixin/Wrap.class"));
		assertTrue(notes.getFirst().message().contains("@WrapMethod h targets create(I)Ljava/lang/Object;"), notes.toString());
		List<PreflightFinding> after = preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());
	}

	/** An {@code @Inject} into tick (no descriptor) at its call to helper(I)V, whose handler has {@code desc}. */
	private static void injectWith(ClassWriter cw, String desc) {
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "h", desc, null, null);
		AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
		AnnotationVisitor methods = ann.visitArray("method");
		methods.visit(null, "tick");
		methods.visitEnd();
		AnnotationVisitor ats = ann.visitArray("at");
		AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
		at.visit("value", "INVOKE");
		at.visit("target", "Lnet/minecraft/world/Thing;helper(I)V");
		at.visitEnd();
		ats.visitEnd();
		ann.visit("require", 1);
		ann.visitEnd();
		mv.visitEnd();
	}

	/** Builds a mixin class targeting {@link #TARGET}; {@code body} adds members. */
	private static byte[] mixin(String name, boolean pseudo, Consumer<ClassWriter> body) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, name, null, "java/lang/Object", null);
		AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor values = m.visitArray("value");
		values.visit(null, Type.getObjectType(name.contains("Missing") ? "net/minecraft/world/Gone" : TARGET));
		values.visitEnd();
		m.visitEnd();
		if (pseudo) cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Pseudo;", false).visitEnd();
		body.accept(cw);
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static void inject(ClassWriter cw, String handler, String method, String atValue, String atTarget, Integer require, Integer ordinal) {
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, handler, "()V", null, null);
		AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
		AnnotationVisitor methods = ann.visitArray("method");
		methods.visit(null, method);
		methods.visitEnd();
		AnnotationVisitor ats = ann.visitArray("at");
		AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
		at.visit("value", atValue);
		if (atTarget != null) at.visit("target", atTarget);
		if (ordinal != null) at.visit("ordinal", ordinal);
		at.visitEnd();
		ats.visitEnd();
		if (require != null) ann.visit("require", require);
		ann.visitEnd();
		mv.visitEnd();
	}

	private static List<PreflightFinding> run(String configJson, String refmapJson, Map<String, byte[]> mixins, boolean client) throws Exception {
		ClassIndex game = new ClassIndex(Map.of(TARGET, targetClass())::get);
		Map<String, byte[]> mod = new HashMap<>(mixins);
		MixinPreflight preflight = preflight(game, mod::get, ClassBytesSource.EMPTY, client);
		Refmap refmap = refmapJson == null ? Refmap.EMPTY : Refmap.parse(refmapJson);
		return preflight.check("test.mixins.json", MixinConfigFile.parse(configJson), refmap, Set.of());
	}

	private static String config(boolean required, String... mixins) {
		return "{\"required\":" + required + ",\"package\":\"com.example.mixin\",\"mixins\":[\"" + String.join("\",\"", mixins) + "\"]}";
	}

	@Test
	void acceptsValidInjections() throws Exception {
		byte[] ok = mixin("com/example/mixin/Ok", false, cw -> {
			inject(cw, "head", "tick", "HEAD", null, 1, null);
			inject(cw, "invoke", "tick()V", "INVOKE", "Lnet/minecraft/world/Thing;helper(I)V", 1, 0);
			inject(cw, "field", "tick", "FIELD", "Lnet/minecraft/world/Thing;count:I", 1, null);
			cw.visitField(Opcodes.ACC_PRIVATE, "count", "I", null, null)
					.visitAnnotation("Lorg/spongepowered/asm/mixin/Shadow;", false).visitEnd();
		});
		List<PreflightFinding> findings = run(config(true, "Ok"), null, Map.of("com/example/mixin/Ok", ok), false);
		assertTrue(findings.isEmpty(), findings.toString());
	}

	/** A MixinExtras expression handler on tick() whose @Definition names {@code type} or {@code method}. */
	private static void expression(ClassWriter cw, String handler, Type type, String method) {
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, handler, "(Z)Z", null, null);
		AnnotationVisitor def = mv.visitAnnotation("Lcom/llamalad7/mixinextras/expression/Definition;", false);
		def.visit("id", "x");
		AnnotationVisitor values = def.visitArray(type != null ? "type" : "method");
		values.visit(null, type != null ? type : method);
		values.visitEnd();
		def.visitEnd();
		AnnotationVisitor ann = mv.visitAnnotation("Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;", false);
		AnnotationVisitor methods = ann.visitArray("method");
		methods.visit(null, "tick");
		methods.visitEnd();
		AnnotationVisitor ats = ann.visitArray("at");
		AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
		at.visit("value", "MIXINEXTRAS:EXPRESSION");
		at.visitEnd();
		ats.visitEnd();
		ann.visit("require", 1);
		ann.visitEnd();
		mv.visitEnd();
	}

	@Test
	void expressionsNeedWhatTheirDefinitionsName() throws Exception {
		byte[] ok = mixin("com/example/mixin/Expr", false, cw -> {
			expression(cw, "owner", Type.getObjectType(TARGET), null);
			expression(cw, "call", null, "Lnet/minecraft/world/Thing;helper(I)V");
		});
		assertTrue(run(config(true, "Expr"), null, Map.of("com/example/mixin/Expr", ok), false).isEmpty());

		byte[] gone = mixin("com/example/mixin/Gone", false, cw -> expression(cw, "h", Type.getObjectType("net/minecraft/world/HalfTransparent"), null));
		List<PreflightFinding> findings = run(config(true, "Gone"), null, Map.of("com/example/mixin/Gone", gone), false);
		assertEquals(1, findings.size(), findings.toString());
		assertEquals(Severity.FAIL, findings.getFirst().severity());
		assertTrue(findings.getFirst().message().contains("MIXINEXTRAS:EXPRESSION"), findings.toString());
	}

	@Test
	void aLocalCapturedByTypeMustBeTheOnlyOneOfThatType() throws Exception {
		java.util.function.IntFunction<byte[]> target = locals -> {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
			MethodVisitor tick = cw.visitMethod(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
			tick.visitCode();
			org.objectweb.asm.Label start = new org.objectweb.asm.Label();
			org.objectweb.asm.Label end = new org.objectweb.asm.Label();
			for (int i = 1; i <= locals; i++) {
				tick.visitInsn(Opcodes.ACONST_NULL);
				tick.visitVarInsn(Opcodes.ASTORE, i);
			}
			tick.visitLabel(start);
			tick.visitVarInsn(Opcodes.ALOAD, 0);
			tick.visitInsn(Opcodes.ICONST_0);
			tick.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "helper", "(I)V", false);
			tick.visitLabel(end);
			tick.visitInsn(Opcodes.RETURN);
			for (int i = 1; i <= locals; i++) tick.visitLocalVariable("s" + i, "Ljava/lang/String;", null, start, end, i);
			tick.visitMaxs(0, 0);
			tick.visitEnd();
			cw.visitEnd();
			return cw.toByteArray();
		};
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Typed"));
		java.util.function.IntFunction<List<PreflightFinding>> run = locals -> {
			ClassIndex game = new ClassIndex(Map.of(TARGET, target.apply(locals))::get);
			Map<String, byte[]> mod = Map.of("com/example/mixin/Typed", mixin("com/example/mixin/Typed", false, cw -> {
				MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "h", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;Ljava/lang/String;)V", null, null);
				mv.visitParameterAnnotation(1, "Lcom/llamalad7/mixinextras/sugar/Local;", false).visitEnd();
				AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
				AnnotationVisitor methods = ann.visitArray("method");
				methods.visit(null, "tick");
				methods.visitEnd();
				AnnotationVisitor ats = ann.visitArray("at");
				AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
				at.visit("value", "INVOKE");
				at.visit("target", "Lnet/minecraft/world/Thing;helper(I)V");
				at.visitEnd();
				ats.visitEnd();
				ann.visit("require", 1);
				ann.visitEnd();
				mv.visitEnd();
			}));
			return preflight(game, mod::get, ClassBytesSource.EMPTY, false)
					.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		};
		assertTrue(run.apply(1).isEmpty(), run.apply(1).toString());
		List<PreflightFinding> two = run.apply(2);
		assertEquals(1, two.size(), two.toString());
		assertTrue(two.getFirst().message().contains("more than one"), two.getFirst().message());
	}

	@Test
	void anInjectorMayTargetAMethodItsMixinAdds() throws Exception {
		byte[] own = mixin("com/example/mixin/Own", false, cw -> {
			cw.visitMethod(Opcodes.ACC_PUBLIC, "added", "()V", null, null).visitEnd();
			inject(cw, "h", "added", "HEAD", null, 1, null);
		});
		List<PreflightFinding> findings = run(config(true, "Own"), null, Map.of("com/example/mixin/Own", own), false);
		assertTrue(findings.isEmpty(), findings.toString());
	}

	@Test
	void anExpressionDefinitionMatchesAMethodReference() throws Exception {
		ClassWriter target = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		target.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor tick = target.visitMethod(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
		tick.visitCode();
		tick.visitVarInsn(Opcodes.ALOAD, 0);
		Handle metafactory = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
				"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
		// this::helper, as an IntConsumer
		tick.visitInvokeDynamicInsn("accept", "(L" + TARGET + ";)Ljava/util/function/IntConsumer;", metafactory, Type.getType("(I)V"),
				new Handle(Opcodes.H_INVOKEVIRTUAL, TARGET, "helper", "(I)V", false), Type.getType("(I)V"));
		tick.visitInsn(Opcodes.POP);
		tick.visitInsn(Opcodes.RETURN);
		tick.visitMaxs(0, 0);
		tick.visitEnd();
		target.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(TARGET, target.toByteArray())::get);
		Map<String, byte[]> mod = Map.of("com/example/mixin/Ref", mixin("com/example/mixin/Ref", false,
				cw -> expression(cw, "ref", null, "Lnet/minecraft/world/Thing;helper(I)V")));
		List<PreflightFinding> findings = preflight(game, mod::get, ClassBytesSource.EMPTY, false)
				.check("test.mixins.json", MixinConfigFile.parse(config(true, "Ref")), Refmap.EMPTY, Set.of());
		assertTrue(findings.isEmpty(), findings.toString());
	}

	private static void wrapHelper(ClassWriter cw, String handler, String desc) {
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, handler, desc, null, null);
		AnnotationVisitor ann = mv.visitAnnotation("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;", false);
		AnnotationVisitor methods = ann.visitArray("method");
		methods.visit(null, "tick");
		methods.visitEnd();
		AnnotationVisitor ats = ann.visitArray("at");
		AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
		at.visit("value", "INVOKE");
		at.visit("target", "Lnet/minecraft/world/Thing;helper(I)V");
		at.visitEnd();
		ats.visitEnd();
		ann.visitEnd();
		mv.visitEnd();
	}

	@Test
	void wrapOperationHandlersMustTakeWhatTheCallPasses() throws Exception {
		String operation = "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;";
		byte[] ok = mixin("com/example/mixin/Wrap", false, cw -> wrapHelper(cw, "wrap", "(Lnet/minecraft/world/Thing;I" + operation + ")V"));
		assertTrue(run(config(true, "Wrap"), null, Map.of("com/example/mixin/Wrap", ok), false).isEmpty());

		byte[] bad = mixin("com/example/mixin/WrapBad", false, cw -> wrapHelper(cw, "wrap", "(I" + operation + ")V"));
		List<PreflightFinding> findings = run(config(true, "WrapBad"), null, Map.of("com/example/mixin/WrapBad", bad), false);
		assertEquals(1, findings.size(), findings.toString());
		assertEquals(Severity.FAIL, findings.getFirst().severity());
		assertTrue(findings.getFirst().message().contains("passes [2]"), findings.toString());
	}

	/** An anonymous game class holding captured fields of type Map, named as given. */
	private static byte[] capturingClass(String name, String... capturedFields) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, name, null, "java/lang/Object", null);
		for (String field : capturedFields) {
			cw.visitField(Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, field, "Ljava/util/Map;", null, null).visitEnd();
		}
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** An accessor interface on {@code target} with a getter and a setter for field {@code field} of type Map. */
	private static byte[] capturedFieldAccessor(String name, String target, String field) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE, name, null, "java/lang/Object", null);
		AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor targets = m.visitArray("targets");
		targets.visit(null, target.replace('/', '.'));
		targets.visitEnd();
		m.visitEnd();
		for (String desc : List.of("()Ljava/util/Map;", "(Ljava/util/Map;)V")) {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, desc.startsWith("()") ? "getTags" : "setTags", desc, null, null);
			AnnotationVisitor accessor = mv.visitAnnotation("Lorg/spongepowered/asm/mixin/gen/Accessor;", true);
			accessor.visit("value", field);
			accessor.visitEnd();
			mv.visitEnd();
		}
		cw.visitEnd();
		return cw.toByteArray();
	}

	@Test
	void accessorsOfACapturedVariableFollowTheGamesFieldName() throws Exception {
		String anonymous = "net/minecraft/core/Registry$Tags$2";
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Captured", capturedFieldAccessor("com/example/mixin/Captured", anonymous, "val$tags")));
		ClassIndex game = new ClassIndex(Map.of(anonymous, capturingClass(anonymous, "val$p_363771_"))::get);
		MixinPreflight preflight = preflight(game, mod::get, ClassBytesSource.EMPTY, false);
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Captured"));
		assertEquals(Severity.FAIL, preflight.check("test.mixins.json", config, Refmap.EMPTY, Set.of()).getFirst().severity(),
				"without the repair, Mixin would not find the field");

		List<PreflightFinding> repairs = new java.util.ArrayList<>();
		Map<String, byte[]> repaired = preflight.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), repairs);
		assertEquals(Set.of("com/example/mixin/Captured.class"), repaired.keySet());
		assertEquals(2, repairs.size(), repairs.toString());
		mod.put("com/example/mixin/Captured", repaired.get("com/example/mixin/Captured.class"));
		assertTrue(preflight.check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty(), "both accessors now find val$p_363771_");

		// Two captured fields of the type: which one was meant can't be known, so nothing is repaired.
		ClassIndex ambiguous = new ClassIndex(Map.of(anonymous, capturingClass(anonymous, "val$p_1_", "val$p_2_"))::get);
		Map<String, byte[]> original = Map.of("com/example/mixin/Captured", capturedFieldAccessor("com/example/mixin/Captured", anonymous, "val$tags"));
		assertTrue(preflight(ambiguous, original::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new java.util.ArrayList<>()).isEmpty());
	}

	/** {@link #TARGET} with synthetic lambda bodies of the given names, all {@code (I)V}. */
	private static byte[] lambdaTarget(String... lambdas) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		for (String lambda : lambdas) {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, lambda, "(I)V", null, null);
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 1);
			mv.visitEnd();
		}
		cw.visitEnd();
		return cw.toByteArray();
	}

	@Test
	void injectorsIntoALambdaFollowTheGamesNumbering() throws Exception {
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Lambda",
				mixin("com/example/mixin/Lambda", false, cw -> inject(cw, "h", "lambda$tick$41(I)V", "HEAD", null, 1, null))));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Lambda"));
		ClassIndex game = new ClassIndex(Map.of(TARGET, lambdaTarget("lambda$tick$3", "lambda$other$4"))::get);
		MixinPreflight preflight = preflight(game, mod::get, ClassBytesSource.EMPTY, false);
		assertTrue(preflight.check("test.mixins.json", config, Refmap.EMPTY, Set.of()).stream().anyMatch(f -> f.message().contains("does not exist")));

		List<PreflightFinding> repairs = new java.util.ArrayList<>();
		Map<String, byte[]> repaired = preflight.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), repairs);
		assertEquals(1, repairs.size(), repairs.toString());
		org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
		new org.objectweb.asm.ClassReader(repaired.get("com/example/mixin/Lambda.class")).accept(node, 0);
		assertEquals(List.of("lambda$tick$3(I)V"), io.github.cmdralph.fabricated.core.bytecode.Annotations.value(
				io.github.cmdralph.fabricated.core.bytecode.Annotations.find(node.methods.getFirst(), INJECT), "method"));
		mod.put("com/example/mixin/Lambda", repaired.get("com/example/mixin/Lambda.class"));
		assertTrue(preflight.check("test.mixins.json", config, Refmap.EMPTY, Set.of()).stream().noneMatch(f -> f.message().contains("does not exist")));

		// Two lambdas of tick with that descriptor: which one was meant can't be known.
		ClassIndex ambiguous = new ClassIndex(Map.of(TARGET, lambdaTarget("lambda$tick$3", "lambda$tick$5"))::get);
		Map<String, byte[]> original = Map.of("com/example/mixin/Lambda",
				mixin("com/example/mixin/Lambda", false, cw -> inject(cw, "h", "lambda$tick$41(I)V", "HEAD", null, 1, null)));
		assertTrue(preflight(ambiguous, original::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new java.util.ArrayList<>()).isEmpty());
	}

	/** An accessor interface on {@link #TARGET} with one non-static {@code @Invoker} method. */
	private static byte[] invokerInterface(String name, String method, String desc) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE, name, null, "java/lang/Object", null);
		AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor values = m.visitArray("value");
		values.visit(null, Type.getObjectType(TARGET));
		values.visitEnd();
		m.visitEnd();
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, method, desc, null, null);
		mv.visitAnnotation("Lorg/spongepowered/asm/mixin/gen/Invoker;", false).visitEnd();
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	@Test
	void accessorsDuplicatingAPublicMethodUseItInstead() throws Exception {
		Map<String, byte[]> mod = new HashMap<>(Map.of(
				"com/example/mixin/Public", invokerInterface("com/example/mixin/Public", "tick", "()V"),
				"com/example/mixin/Private", invokerInterface("com/example/mixin/Private", "helper", "(I)V")));
		ClassIndex game = new ClassIndex(Map.of(TARGET, targetClass())::get);
		MixinPreflight preflight = preflight(game, mod::get, ClassBytesSource.EMPTY, false);
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Public", "Private"));
		List<PreflightFinding> repairs = new java.util.ArrayList<>();
		Map<String, byte[]> repaired = preflight.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), repairs);
		assertEquals(Set.of("com/example/mixin/Public.class"), repaired.keySet(), "only a public method of the target is used instead");
		assertEquals(1, repairs.size(), repairs.toString());
		assertEquals(Severity.WARN, repairs.getFirst().severity());

		org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
		new org.objectweb.asm.ClassReader(repaired.get("com/example/mixin/Public.class")).accept(node, 0);
		org.objectweb.asm.tree.MethodNode tick = node.methods.getFirst();
		assertTrue((tick.access & Opcodes.ACC_SYNTHETIC) != 0, "synthetic, so Mixin still treats the interface as an accessor mixin");
		assertEquals(List.of("Lorg/spongepowered/asm/mixin/Shadow;"), tick.visibleAnnotations.stream().map(a -> a.desc).toList());
		mod.put("com/example/mixin/Public", repaired.get("com/example/mixin/Public.class"));
		List<PreflightFinding> findings = preflight.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertEquals(1, findings.size(), findings.toString());
		assertTrue(findings.getFirst().mixin().endsWith("Private"), "the private duplicate still fails: " + findings);
	}

	@Test
	void failsWhenRequiredInjectionTargetIsMissing() throws Exception {
		byte[] bad = mixin("com/example/mixin/Bad", false, cw -> inject(cw, "h", "removedMethod", "HEAD", null, 1, null));
		List<PreflightFinding> findings = run(config(true, "Bad"), null, Map.of("com/example/mixin/Bad", bad), false);
		assertEquals(1, findings.size());
		assertEquals(Severity.FAIL, findings.getFirst().severity());
		assertTrue(findings.getFirst().message().contains("removedMethod"));
	}

	@Test
	void warnsWhenInjectorIsOptional() throws Exception {
		byte[] soft = mixin("com/example/mixin/Soft", false, cw -> inject(cw, "h", "tick", "INVOKE", "Lnet/minecraft/world/Thing;gone()V", null, null));
		List<PreflightFinding> findings = run(config(true, "Soft"), null, Map.of("com/example/mixin/Soft", soft), false);
		assertEquals(Severity.WARN, findings.getFirst().severity(), "require defaults to 0");
	}

	@Test
	void respectsDefaultRequireAndOrdinal() throws Exception {
		byte[] ord = mixin("com/example/mixin/Ord", false, cw -> inject(cw, "h", "tick", "INVOKE", "Lnet/minecraft/world/Thing;helper(I)V", null, 3));
		String cfg = "{\"required\":true,\"package\":\"com.example.mixin\",\"mixins\":[\"Ord\"],\"injectors\":{\"defaultRequire\":1}}";
		List<PreflightFinding> findings = run(cfg, null, Map.of("com/example/mixin/Ord", ord), false);
		assertEquals(Severity.FAIL, findings.getFirst().severity(), "only one call exists, ordinal 3 cannot match");
	}

	@Test
	void resolvesSelectorsThroughRefmap() throws Exception {
		byte[] mapped = mixin("com/example/mixin/Mapped", false, cw -> inject(cw, "h", "devName", "INVOKE", "devTarget", 1, null));
		String refmap = "{\"mappings\":{\"com/example/mixin/Mapped\":{\"devName\":\"Lnet/minecraft/world/Thing;tick()V\",\"devTarget\":\"Lnet/minecraft/world/Thing;helper(I)V\"}}}";
		assertTrue(run(config(true, "Mapped"), refmap, Map.of("com/example/mixin/Mapped", mapped), false).isEmpty());
	}

	@Test
	void missingTargetClassDependsOnRequiredAndPseudo() throws Exception {
		byte[] missing = mixin("com/example/mixin/Missing", false, cw -> { });
		assertEquals(Severity.FAIL, run(config(true, "Missing"), null, Map.of("com/example/mixin/Missing", missing), false).getFirst().severity());
		assertEquals(Severity.WARN, run(config(false, "Missing"), null, Map.of("com/example/mixin/Missing", missing), false).getFirst().severity());
		// Before compatibility level 0.17.4, Mixin skips a missing target instead of rejecting the mixin.
		MixinPreflight lenient = preflight(new ClassIndex(Map.of(TARGET, targetClass())::get), Map.of("com/example/mixin/Missing", missing)::get,
				ClassBytesSource.EMPTY, false, false);
		assertEquals(Severity.WARN, lenient.check("test.mixins.json", MixinConfigFile.parse(config(true, "Missing")), Refmap.EMPTY, Set.of()).getFirst().severity());
		byte[] pseudo = mixin("com/example/mixin/PseudoMissing", true, cw -> { });
		assertTrue(run(config(true, "PseudoMissing"), null, Map.of("com/example/mixin/PseudoMissing", pseudo), false).isEmpty());
	}

	@Test
	void missingShadowFails() throws Exception {
		byte[] shadow = mixin("com/example/mixin/Shadowy", false, cw -> cw.visitField(Opcodes.ACC_PRIVATE, "gone", "I", null, null)
				.visitAnnotation("Lorg/spongepowered/asm/mixin/Shadow;", false).visitEnd());
		List<PreflightFinding> findings = run(config(true, "Shadowy"), null, Map.of("com/example/mixin/Shadowy", shadow), false);
		assertEquals(Severity.FAIL, findings.getFirst().severity());
		assertTrue(findings.getFirst().message().contains("@Shadow field gone"));
	}

	@Test
	void aConfigInheritsRequireFromItsParent() throws Exception {
		MixinConfigFile parent = MixinConfigFile.parse("{\"required\":true,\"injectors\":{\"defaultRequire\":1}}");
		MixinConfigFile child = MixinConfigFile.parse("{\"parent\":\"p.json\",\"package\":\"a\",\"mixins\":[]}").inheriting(parent);
		assertTrue(child.required());
		assertEquals(1, child.defaultRequire());
		MixinConfigFile own = MixinConfigFile.parse("{\"parent\":\"p.json\",\"required\":false,\"injectors\":{\"defaultRequire\":2}}").inheriting(parent);
		assertTrue(!own.required() && own.defaultRequire() == 2, "a config's own settings win");
	}

	@Test
	void aPluginConfigsCrashingMixinIsLeftOut() throws Exception {
		byte[] bad = mixin("com/example/mixin/Bad", false, cw -> inject(cw, "h", "removedMethod", "HEAD", null, 1, null));
		String cfg = "{\"required\":true,\"package\":\"com.example.mixin\",\"plugin\":\"com.example.Plugin\",\"mixins\":[\"Bad\"]}";
		List<PreflightFinding> findings = run(cfg, null, Map.of("com/example/mixin/Bad", bad), false);
		assertTrue(findings.stream().noneMatch(f -> f.severity() == Severity.FAIL), findings.toString());
		assertTrue(findings.stream().anyMatch(f -> f.severity() == Severity.LEFT_OUT && "com.example.mixin.Bad".equals(f.mixin())
				&& f.handler().startsWith("h(")), "only the failing handler is left out: " + findings);
	}

	@Test
	void clientMixinsAreSkippedOnServer() throws Exception {
		byte[] bad = mixin("com/example/mixin/ClientBad", false, cw -> inject(cw, "h", "removedMethod", "HEAD", null, 1, null));
		String cfg = "{\"required\":true,\"package\":\"com.example.mixin\",\"client\":[\"ClientBad\"]}";
		assertTrue(run(cfg, null, Map.of("com/example/mixin/ClientBad", bad), false).isEmpty());
		assertEquals(Severity.FAIL, run(cfg, null, Map.of("com/example/mixin/ClientBad", bad), true).getFirst().severity());
	}

	@Test
	void reportsMixinClassMissingFromJar() throws Exception {
		List<PreflightFinding> findings = run(config(true, "Absent"), null, Map.of(), false);
		assertEquals(Severity.FAIL, findings.getFirst().severity());
	}

	@Test
	void parsesMemberSelectors() {
		assertEquals(new MemberRef("a/B", "foo", "(I)V", false), MemberRef.parse("La/B;foo(I)V"));
		assertEquals(new MemberRef(null, "foo", null, false), MemberRef.parse("foo"));
		assertEquals(new MemberRef("a/B", "bar", "I", false), MemberRef.parse("La/B;bar:I"));
		assertEquals(new MemberRef("net/minecraft/world/level/Level", "getBlockState", "(Lnet/minecraft/core/BlockPos;)V", false),
				MemberRef.parse("net/minecraft/world/level/Level.getBlockState (Lnet/minecraft/core/BlockPos;)V"), "whitespace is ignored, as Mixin does");
		assertEquals(new MemberRef(null, "LOGGER", "Lorg/slf4j/Logger;", false), MemberRef.parse("LOGGER:Lorg/slf4j/Logger;"));
		assertEquals(new MemberRef("a/b/C", "baz", "()V", false), MemberRef.parse("a.b.C.baz()V"));
		assertTrue(MemberRef.parse("foo*").fuzzy());
		assertTrue(MemberRef.parse("/regex/").fuzzy());
		assertTrue(MemberRef.parse("foo(I)").matchesMethod("foo", "(I)Z"), "descriptor without return type");
		assertEquals("foo", MixinPreflight.inferAccessorName("getFoo"));
		assertEquals("URL", MixinPreflight.inferAccessorName("getURL"));
		assertEquals("doThing", MixinPreflight.inferInvokerName("callDoThing"));
		assertEquals("<init>", MixinPreflight.inferInvokerName("newThing"));
	}

	@Test
	void failsWhenTargetIsAnUntransformableLibraryClass() throws Exception {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "com/example/mixin/LibMixin", null, "java/lang/Object", null);
		AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor values = m.visitArray("value");
		values.visit(null, Type.getObjectType("com/mojang/datafixers/Lib"));
		values.visitEnd();
		m.visitEnd();
		cw.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(TARGET, targetClass())::get);
		Map<String, byte[]> mod = Map.of("com/example/mixin/LibMixin", cw.toByteArray());
		Map<String, byte[]> libraries = Map.of("com/mojang/datafixers/Lib", new byte[] {1});

		List<PreflightFinding> withLibraries = preflight(game, mod::get, libraries::get, false)
				.check("test.mixins.json", MixinConfigFile.parse(config(true, "LibMixin")), Refmap.EMPTY, Set.of());
		assertEquals(1, withLibraries.size(), withLibraries.toString());
		assertEquals(PreflightFinding.Severity.FAIL, withLibraries.getFirst().severity());
		assertTrue(withLibraries.getFirst().message().contains("without mixin support"));

		// Unknown non-Minecraft targets (another mod's classes) are still only noted.
		List<PreflightFinding> unknown = preflight(game, mod::get, ClassBytesSource.EMPTY, false)
				.check("test.mixins.json", MixinConfigFile.parse(config(true, "LibMixin")), Refmap.EMPTY, Set.of());
		assertEquals(PreflightFinding.Severity.INFO, unknown.getFirst().severity());
	}

	private static void injectSliced(ClassWriter cw, String handler, String atValue, String atTarget, String fromTarget) {
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, handler, "()V", null, null);
		AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
		AnnotationVisitor methods = ann.visitArray("method");
		methods.visit(null, "tick");
		methods.visitEnd();
		AnnotationVisitor ats = ann.visitArray("at");
		AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
		at.visit("value", atValue);
		at.visit("target", atTarget);
		at.visitEnd();
		ats.visitEnd();
		AnnotationVisitor slices = ann.visitArray("slice");
		AnnotationVisitor slice = slices.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/Slice;");
		AnnotationVisitor from = slice.visitAnnotation("from", "Lorg/spongepowered/asm/mixin/injection/At;");
		from.visit("value", "INVOKE");
		from.visit("target", fromTarget);
		from.visitEnd();
		slice.visitEnd();
		slices.visitEnd();
		ann.visit("require", 1);
		ann.visitEnd();
		mv.visitEnd();
	}

	@Test
	void evaluatesSlices() throws Exception {
		// tick() reads count, then calls helper(I)V. A slice starting at helper excludes the field read.
		byte[] outside = mixin("com/example/mixin/Outside", false, cw -> injectSliced(cw, "h", "FIELD",
				"Lnet/minecraft/world/Thing;count:I", "Lnet/minecraft/world/Thing;helper(I)V"));
		List<PreflightFinding> fail = run(config(true, "Outside"), null, Map.of("com/example/mixin/Outside", outside), false);
		assertEquals(1, fail.size(), fail.toString());
		assertEquals(PreflightFinding.Severity.FAIL, fail.getFirst().severity());

		byte[] inside = mixin("com/example/mixin/Inside", false, cw -> injectSliced(cw, "h", "INVOKE",
				"Lnet/minecraft/world/Thing;helper(I)V", "Lnet/minecraft/world/Thing;helper(I)V"));
		assertTrue(run(config(true, "Inside"), null, Map.of("com/example/mixin/Inside", inside), false).isEmpty());

		// A slice start that is not found falls back to the method start, as in Mixin.
		byte[] fallback = mixin("com/example/mixin/Fallback", false, cw -> injectSliced(cw, "h", "FIELD",
				"Lnet/minecraft/world/Thing;count:I", "Lnet/minecraft/world/Thing;absent()V"));
		assertTrue(run(config(true, "Fallback"), null, Map.of("com/example/mixin/Fallback", fallback), false).isEmpty());

		// tick() loads its locals before calling helper: a LOAD sliced from the call can't match, an unsliced one may.
		byte[] noLoad = mixin("com/example/mixin/NoLoad", false, cw -> injectSliced(cw, "h", "LOAD",
				"Lnet/minecraft/world/Thing;", "Lnet/minecraft/world/Thing;helper(I)V"));
		List<PreflightFinding> missingLoad = run(config(true, "NoLoad"), null, Map.of("com/example/mixin/NoLoad", noLoad), false);
		assertEquals(1, missingLoad.size(), missingLoad.toString());
		assertEquals(PreflightFinding.Severity.FAIL, missingLoad.getFirst().severity());
		byte[] load = mixin("com/example/mixin/Load", false, cw -> inject(cw, "h", "tick", "LOAD", null, 1, null));
		assertTrue(run(config(true, "Load"), null, Map.of("com/example/mixin/Load", load), false).isEmpty());
	}

	@Test
	void newPointWithConstructorDescriptorMatchesThatConstructorOnly() throws Exception {
		// Target method make() does: new net/minecraft/world/Thing(I) — a one-argument constructor.
		ClassWriter target = new ClassWriter(0);
		target.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "net/minecraft/world/Maker", null, "java/lang/Object", null);
		MethodVisitor make = target.visitMethod(Opcodes.ACC_PUBLIC, "make", "()Ljava/lang/Object;", null, null);
		make.visitCode();
		make.visitTypeInsn(Opcodes.NEW, "net/minecraft/world/Thing");
		make.visitInsn(Opcodes.DUP);
		make.visitInsn(Opcodes.ICONST_1);
		make.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/world/Thing", "<init>", "(I)V", false);
		make.visitInsn(Opcodes.ARETURN);
		make.visitMaxs(3, 1);
		make.visitEnd();
		target.visitEnd();
		ClassIndex game = new ClassIndex(Map.of("net/minecraft/world/Maker", target.toByteArray())::get);

		java.util.function.Function<String, byte[]> mixinFor = desc -> {
			ClassWriter cw = new ClassWriter(0);
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "com/example/mixin/M", null, "java/lang/Object", null);
			AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
			AnnotationVisitor values = m.visitArray("value");
			values.visit(null, Type.getObjectType("net/minecraft/world/Maker"));
			values.visitEnd();
			m.visitEnd();
			inject(cw, "h", "make", "NEW", desc, 1, null);
			cw.visitEnd();
			return cw.toByteArray();
		};
		java.util.function.Function<String, List<PreflightFinding>> run = desc -> {
			Map<String, byte[]> mod = Map.of("com/example/mixin/M", mixinFor.apply(desc));
			try {
				return preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", MixinConfigFile.parse(config(true, "M")), Refmap.EMPTY, Set.of());
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		};
		assertTrue(run.apply("(I)Lnet/minecraft/world/Thing;").isEmpty(), "matching constructor");
		assertEquals(1, run.apply("(II)Lnet/minecraft/world/Thing;").size(), "two-argument constructor is never called");
		assertTrue(run.apply("Lnet/minecraft/world/Thing;").isEmpty(), "class-only NEW still matches any constructor");
	}

	@Test
	void methodsThatAlreadyExistInTheTargetAreReported() throws Exception {
		// TARGET declares tick()V and helper(I)V. A plain mixin method tick()V collides with it, and so does an
		// @Invoker named helper(I)V, which Mixin would have to generate.
		byte[] merged = mixin("com/example/mixin/Merged", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 1);
			mv.visitEnd();
		});
		String strict = "{\"required\":true,\"package\":\"com.example.mixin\",\"mixins\":[\"Merged\"],\"overwrites\":{\"requireAnnotations\":true}}";
		List<PreflightFinding> fail = run(strict, null, Map.of("com/example/mixin/Merged", merged), false);
		assertEquals(1, fail.size(), fail.toString());
		assertEquals(PreflightFinding.Severity.FAIL, fail.getFirst().severity());

		List<PreflightFinding> warn = run(config(true, "Merged"), null, Map.of("com/example/mixin/Merged", merged), false);
		assertEquals(1, warn.size(), warn.toString());
		assertEquals(PreflightFinding.Severity.WARN, warn.getFirst().severity());

		byte[] invoker = mixin("com/example/mixin/Inv", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "helper", "(I)V", null, null);
			mv.visitAnnotation("Lorg/spongepowered/asm/mixin/gen/Invoker;", false).visitEnd();
			mv.visitEnd();
		});
		List<PreflightFinding> clash = run(config(true, "Inv"), null, Map.of("com/example/mixin/Inv", invoker), false);
		assertEquals(1, clash.size(), clash.toString());
		assertTrue(clash.getFirst().message().contains("already exists"), clash.getFirst().message());

		// A static invoker is rewritten inside the accessor interface; nothing is generated in the target.
		byte[] staticInvoker = mixin("com/example/mixin/StaticInv", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "helper", "(I)V", null, null);
			mv.visitAnnotation("Lorg/spongepowered/asm/mixin/gen/Invoker;", false).visitEnd();
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 1);
			mv.visitEnd();
		});
		List<PreflightFinding> none = run(config(true, "StaticInv"), null, Map.of("com/example/mixin/StaticInv", staticInvoker), false);
		assertTrue(none.stream().noneMatch(f -> f.message().contains("already exists")), none.toString());
	}

	/** An @Inject at HEAD of helper(I)V whose handler has the given descriptor; {@code sugarIndex} marks a @Local parameter. */
	private static byte[] headInjector(String name, String handlerDesc, int sugarIndex) {
		return headInjector(name, handlerDesc, sugarIndex, null);
	}

	/** As above, the @Local parameter capturing the local named {@code localName} when not null. */
	private static byte[] headInjector(String name, String handlerDesc, int sugarIndex, String localName) {
		return mixin(name, false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "h", handlerDesc, null, null);
			if (sugarIndex >= 0) {
				AnnotationVisitor local = mv.visitParameterAnnotation(sugarIndex, "Lcom/llamalad7/mixinextras/sugar/Local;", false);
				if (localName != null) {
					AnnotationVisitor names = local.visitArray("name");
					names.visit(null, localName);
					names.visitEnd();
				}
				local.visitEnd();
			}
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "helper");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;").visit("value", "HEAD");
			ats.visitEnd();
			ann.visitEnd();
			mv.visitEnd();
		});
	}

	@Test
	void injectHandlerParametersMustMatchTheTarget() throws Exception {
		String ci = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
		java.util.function.BiFunction<String, Integer, List<PreflightFinding>> run = (desc, sugar) -> {
			try {
				return MixinPreflightTest.run(config(true, "Sig"), null, Map.of("com/example/mixin/Sig", headInjector("com/example/mixin/Sig", desc, sugar)), false);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		};
		assertTrue(run.apply("(I" + ci + ")V", -1).isEmpty(), "target parameters then callback info");
		assertTrue(run.apply("(" + ci + ")V", -1).isEmpty(), "callback info only");
		assertTrue(run.apply("(I" + ci + "I)V", 2).isEmpty(), "MixinExtras @Local parameters are not counted");
		List<PreflightFinding> wrong = run.apply("(J" + ci + ")V", -1);
		assertEquals(1, wrong.size(), wrong.toString());
		assertEquals(Severity.FAIL, wrong.getFirst().severity());
		assertTrue(wrong.getFirst().message().contains("does not match the parameters"), wrong.getFirst().message());
	}

	@Test
	void aCapturedLocalMustExistInTheTarget() throws Exception {
		String handler = "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;I)V";
		List<PreflightFinding> present = run(config(true, "Present"), null,
				Map.of("com/example/mixin/Present", headInjector("com/example/mixin/Present", handler, 1, "amount")), false);
		assertTrue(present.isEmpty(), present.toString());
		List<PreflightFinding> gone = run(config(true, "Gone"), null,
				Map.of("com/example/mixin/Gone", headInjector("com/example/mixin/Gone", handler, 1, "testPos")), false);
		// Reported as the injector's require says (none in this config: a warning).
		assertEquals(1, gone.size(), gone.toString());
		assertTrue(gone.getFirst().message().contains("local variable testPos"), gone.getFirst().message());
		// By type alone: helper has an int local (amount) but no String one.
		assertTrue(run(config(true, "ByType"), null, Map.of("com/example/mixin/ByType", headInjector("com/example/mixin/ByType", handler, 1)), false).isEmpty());
		List<PreflightFinding> noString = run(config(true, "NoString"), null, Map.of("com/example/mixin/NoString",
				headInjector("com/example/mixin/NoString", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;Ljava/lang/String;)V", 1)), false);
		assertEquals(1, noString.size(), noString.toString());
		assertTrue(noString.getFirst().message().contains("local variable of type java.lang.String"), noString.getFirst().message());
	}

	private static byte[] accessorMixin(String name, String method, String desc) {
		return mixin(name, false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, method, desc, null, null);
			mv.visitAnnotation("Lorg/spongepowered/asm/mixin/gen/Accessor;", false).visitEnd();
			mv.visitEnd();
		});
	}

	@Test
	void accessorFieldTypeMustMatch() throws Exception {
		// TARGET has a private int field "count".
		assertTrue(run(config(true, "Get"), null, Map.of("com/example/mixin/Get", accessorMixin("com/example/mixin/Get", "getCount", "()I")), false).isEmpty());
		assertTrue(run(config(true, "Set"), null, Map.of("com/example/mixin/Set", accessorMixin("com/example/mixin/Set", "setCount", "(I)V")), false).isEmpty());
		List<PreflightFinding> wrongType = run(config(true, "Long"), null, Map.of("com/example/mixin/Long", accessorMixin("com/example/mixin/Long", "getCount", "()J")), false);
		assertEquals(1, wrongType.size(), wrongType.toString());
		assertTrue(wrongType.getFirst().message().contains("different type"), wrongType.getFirst().message());
		List<PreflightFinding> missing = run(config(true, "Gone"), null, Map.of("com/example/mixin/Gone", accessorMixin("com/example/mixin/Gone", "getTotal", "()I")), false);
		assertTrue(missing.getFirst().message().contains("does not exist"), missing.getFirst().message());
	}

	/** Pre-flight with no installed jars, and missing targets fatal unless said otherwise. */
	private static MixinPreflight preflight(ClassIndex game, ClassBytesSource mod, ClassBytesSource untransformable, boolean clientSide) {
		return preflight(game, mod, untransformable, clientSide, true);
	}

	private static MixinPreflight preflight(ClassIndex game, ClassBytesSource mod, ClassBytesSource untransformable, boolean clientSide,
			boolean missingTargetsFatal) {
		return new MixinPreflight(game, InstalledClasses.NONE, mod, untransformable, clientSide, missingTargetsFatal);
	}
}
