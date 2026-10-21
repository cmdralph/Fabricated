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
import java.util.function.Function;

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
	/** A class the target constructs (a record of the game, say). */
	private static final String THING = "net/minecraft/world/Outline";
	private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
	private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
	private static final String WRAP_OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
	private static final String OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;";
	private static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
	private static final String LOCAL = "Lcom/llamalad7/mixinextras/sugar/Local;";

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
		assertEquals(Severity.LEFT_OUT, gone.severity());
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
		assertEquals(Severity.LEFT_OUT, findings.stream().filter(f -> f.mixin().endsWith("Gone")).findFirst().orElseThrow().severity(), findings.toString());
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

	/** {@link #TARGET} whose {@code tick} calls {@code helper} through each of {@code descs} on itself, passing zeros and nulls. */
	private static byte[] callingHelper(String... descs) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor tick = cw.visitMethod(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
		tick.visitCode();
		for (String desc : descs) {
			tick.visitVarInsn(Opcodes.ALOAD, 0);
			for (Type arg : Type.getArgumentTypes(desc)) {
				switch (arg.getSort()) {
					case Type.INT -> tick.visitInsn(Opcodes.ICONST_0);
					case Type.LONG -> tick.visitInsn(Opcodes.LCONST_0);
					default -> tick.visitInsn(Opcodes.ACONST_NULL);
				}
			}
			tick.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "helper", desc, false);
		}
		tick.visitInsn(Opcodes.RETURN);
		tick.visitMaxs(0, 0);
		tick.visitEnd();
		for (String desc : descs) {
			MethodVisitor helper = cw.visitMethod(Opcodes.ACC_PUBLIC, "helper", desc, null, null);
			helper.visitCode();
			helper.visitInsn(Opcodes.RETURN);
			helper.visitMaxs(0, 0);
			helper.visitEnd();
		}
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** A handler {@code name} of {@code injector} on {@code tick} at its call to {@code helper(I)V}, with the code {@code body} writes. */
	private static void atHelperCall(ClassWriter cw, String injector, String name, String desc, Consumer<MethodVisitor> body) {
		atHelperCall(cw, injector, name, desc, "(I)V", body);
	}

	/** A handler {@code name} of {@code injector} on {@code tick} at its call to {@code helper} with {@code helperDesc}. */
	private static void atHelperCall(ClassWriter cw, String injector, String name, String desc, String helperDesc, Consumer<MethodVisitor> body) {
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, name, desc, null, null);
		AnnotationVisitor ann = mv.visitAnnotation(injector, false);
		AnnotationVisitor methods = ann.visitArray("method");
		methods.visit(null, "tick");
		methods.visitEnd();
		boolean single = injector.equals(REDIRECT);
		AnnotationVisitor ats = single ? null : ann.visitArray("at");
		AnnotationVisitor at = single ? ann.visitAnnotation("at", AT) : ats.visitAnnotation(null, AT);
		at.visit("value", "INVOKE");
		at.visit("target", "L" + TARGET + ";helper" + helperDesc);
		at.visitEnd();
		if (ats != null) ats.visitEnd();
		ann.visitEnd();
		if (body != null) {
			mv.visitCode();
			body.accept(mv);
		}
		mv.visitEnd();
	}

	@Test
	void anInjectionAtACallNeoForgeAddedAnArgumentToIsPointedThere() throws Exception {
		String thing = "L" + TARGET + ";";
		byte[] original = mixin("com/example/mixin/AtHelper", false, cw -> {
			atHelperCall(cw, INJECT, "inject", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null);
			atHelperCall(cw, REDIRECT, "redirect", "(" + thing + "I)V", null);
			atHelperCall(cw, WRAP_OPERATION, "wrap", "(" + thing + "I" + OPERATION + ")V", null);
			atHelperCall(cw, "Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;", "condition", "(" + thing + "I)Z", null);
		});
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/AtHelper", original));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "AtHelper"));
		ClassIndex neoForge = new ClassIndex(Map.of(TARGET, callingHelper("(ILjava/lang/String;)V"))::get);
		assertEquals(4, preflight(neoForge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).size());

		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight(neoForge, mod::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);

		assertEquals(4, notes.size(), notes.toString());
		assertTrue(notes.stream().allMatch(n -> n.message().contains("an overload with one more argument")), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get("com/example/mixin/AtHelper.class")).accept(node, 0);
		Map<String, MethodNode> methods = new HashMap<>();
		node.methods.forEach(m -> methods.put(m.name, m));
		AnnotationNode inject = methods.get("inject").invisibleAnnotations.getFirst();
		assertEquals("L" + TARGET + ";helper(ILjava/lang/String;)V",
				Annotations.value((AnnotationNode) ((List<?>) Annotations.value(inject, "at")).getFirst(), "target"));
		for (String handler : List.of("redirect", "wrap", "condition")) {
			assertTrue(methods.get(handler).invisibleAnnotations == null || methods.get(handler).invisibleAnnotations.isEmpty(), handler);
		}
		assertEquals("(" + thing + "ILjava/lang/String;)V", methods.get("redirect$fabricated").desc);
		List<Integer> loads = new ArrayList<>();
		for (AbstractInsnNode insn : methods.get("redirect$fabricated").instructions) {
			if (insn instanceof VarInsnNode load) loads.add(load.var);
		}
		assertEquals(List.of(0, 1, 2), loads, "the redirect's bridge leaves the added argument out");
		assertEquals("(" + thing + "ILjava/lang/String;)Z", methods.get("condition$fabricated").desc);
		assertEquals("(" + thing + "ILjava/lang/String;" + OPERATION + ")V", methods.get("wrap$fabricated").desc);
		assertEquals("(" + OPERATION + "Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;", methods.get("lambda$wrap$fabricated").desc);
		mod.put("com/example/mixin/AtHelper", repaired.get("com/example/mixin/AtHelper.class"));
		List<PreflightFinding> after = preflight(neoForge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());

		for (byte[] game : List.of(callingHelper("(I)V", "(ILjava/lang/String;)V"), callingHelper("(II)V"), callingHelper("(ILjava/lang/String;Z)V"))) {
			assertTrue(preflight(new ClassIndex(Map.of(TARGET, game)::get), Map.of("com/example/mixin/AtHelper", original)::get, ClassBytesSource.EMPTY, false)
					.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty(),
					"the named call still made, an added argument that can't be told apart, or two added");
		}
	}

	/** A game class whose {@code tick} constructs {@code THING} once with each of {@code initDescs}. */
	private static byte[] constructingHelper(String... initDescs) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor tick = cw.visitMethod(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
		tick.visitCode();
		for (String desc : initDescs) {
			tick.visitTypeInsn(Opcodes.NEW, THING);
			tick.visitInsn(Opcodes.DUP);
			for (Type arg : Type.getArgumentTypes(desc)) tick.visitInsn(arg.getSort() == Type.INT ? Opcodes.ICONST_0 : Opcodes.ACONST_NULL);
			tick.visitMethodInsn(Opcodes.INVOKESPECIAL, THING, "<init>", desc, false);
			tick.visitInsn(Opcodes.POP);
		}
		tick.visitInsn(Opcodes.RETURN);
		tick.visitMaxs(0, 0);
		tick.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** A handler {@code name} of {@code injector} on {@code tick} at its construction of {@code THING} with {@code (I)}. */
	private static void atNewThing(ClassWriter cw, String injector, String name, String desc, Consumer<MethodVisitor> body) {
		atNewThing(cw, injector, name, desc, -1, body);
	}

	/** {@link #atNewThing}, with the parameter at {@code local} (unless -1) a MixinExtras {@code @Local(ordinal = 0)}. */
	private static void atNewThing(ClassWriter cw, String injector, String name, String desc, int local, Consumer<MethodVisitor> body) {
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, name, desc, null, null);
		if (local >= 0) {
			AnnotationVisitor sugar = mv.visitParameterAnnotation(local, LOCAL, false);
			sugar.visit("ordinal", 0);
			sugar.visitEnd();
		}
		AnnotationVisitor ann = mv.visitAnnotation(injector, false);
		AnnotationVisitor methods = ann.visitArray("method");
		methods.visit(null, "tick");
		methods.visitEnd();
		boolean single = injector.equals(REDIRECT);
		AnnotationVisitor ats = single ? null : ann.visitArray("at");
		AnnotationVisitor at = single ? ann.visitAnnotation("at", AT) : ats.visitAnnotation(null, AT);
		at.visit("value", "NEW");
		at.visit("target", "(I)L" + THING + ";");
		at.visitEnd();
		if (ats != null) ats.visitEnd();
		ann.visitEnd();
		if (body != null) {
			mv.visitCode();
			body.accept(mv);
		}
		mv.visitEnd();
	}

	/**
	 * A constructor NeoForge's build calls with one more argument (a record NeoForge gave a component, as
	 * {@code BlockOutlineRenderState}): injectors at it are pointed at that constructor, with bridges as for a static call,
	 * and a {@code @WrapOperation}'s puts the argument back for the original construction.
	 */
	@Test
	void anInjectionAtAConstructorNeoForgeAddedAnArgumentToIsPointedThere() throws Exception {
		String thing = "L" + THING + ";";
		String name = "com/example/mixin/AtNew";
		// wrap(amount, original) { return (Thing) original.call(amount); }
		Consumer<ClassWriter> wrap = cw -> atNewThing(cw, WRAP_OPERATION, "wrap", "(I" + OPERATION + ")" + thing, mv -> {
			mv.visitVarInsn(Opcodes.ALOAD, 2);
			mv.visitInsn(Opcodes.ICONST_1);
			mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
			mv.visitInsn(Opcodes.DUP);
			mv.visitInsn(Opcodes.ICONST_0);
			mv.visitVarInsn(Opcodes.ILOAD, 1);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
			mv.visitInsn(Opcodes.AASTORE);
			mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, OPERATION.substring(1, OPERATION.length() - 1), "call", "([Ljava/lang/Object;)Ljava/lang/Object;", true);
			mv.visitTypeInsn(Opcodes.CHECKCAST, THING);
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitMaxs(5, 3);
		});
		byte[] original = mixin(name, false, cw -> {
			atNewThing(cw, INJECT, "inject", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null);
			atNewThing(cw, REDIRECT, "redirect", "(I)" + thing, null);
			wrap.accept(cw);
		});
		Map<String, byte[]> mod = new HashMap<>(Map.of(name, original));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "AtNew"));
		byte[] game = constructingHelper("(ILjava/lang/String;)V");
		ClassIndex neoForge = new ClassIndex(Map.of(TARGET, game)::get);
		assertEquals(3, preflight(neoForge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).size());

		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight(neoForge, mod::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);

		assertEquals(3, notes.size(), notes.toString());
		assertTrue(notes.stream().allMatch(n -> n.message().contains("a constructor taking one more argument")), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get(name + ".class")).accept(node, 0);
		Map<String, MethodNode> methods = new HashMap<>();
		node.methods.forEach(m -> methods.put(m.name, m));
		AnnotationNode inject = methods.get("inject").invisibleAnnotations.getFirst();
		assertEquals("(ILjava/lang/String;)" + thing, Annotations.value((AnnotationNode) ((List<?>) Annotations.value(inject, "at")).getFirst(), "target"));
		assertEquals("(ILjava/lang/String;)" + thing, methods.get("redirect$fabricated").desc);
		assertEquals("(ILjava/lang/String;" + OPERATION + ")" + thing, methods.get("wrap$fabricated").desc);
		mod.put(name, repaired.get(name + ".class"));
		List<PreflightFinding> after = preflight(neoForge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());

		// The bridge's Operation constructs with the added argument put back (a mixin with only that handler, to load it).
		String wrapName = "com/example/mixin/AtNewWrap";
		ClassWriter wrapWriter = new ClassWriter(0);
		wrapWriter.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, wrapName, null, "java/lang/Object", null);
		AnnotationVisitor mixinAnnotation = wrapWriter.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor mixinTargets = mixinAnnotation.visitArray("value");
		mixinTargets.visit(null, Type.getObjectType(TARGET));
		mixinTargets.visitEnd();
		mixinAnnotation.visitEnd();
		MethodVisitor init = wrapWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		init.visitCode();
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(1, 1);
		init.visitEnd();
		wrap.accept(wrapWriter);
		wrapWriter.visitEnd();
		byte[] wrapOnly = wrapWriter.toByteArray();
		Map<String, byte[]> wrapRepaired = preflight(neoForge, Map.of(wrapName, wrapOnly)::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", MixinConfigFile.parse(config(true, "AtNewWrap")), Refmap.EMPTY, Set.of(), new ArrayList<>());
		ClassWriter operation = new ClassWriter(0);
		String operationName = OPERATION.substring(1, OPERATION.length() - 1);
		operation.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, operationName, null, "java/lang/Object", null);
		operation.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_VARARGS, "call", "([Ljava/lang/Object;)Ljava/lang/Object;", null, null).visitEnd();
		operation.visitEnd();
		ClassWriter thingClass = new ClassWriter(0);
		thingClass.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, THING, null, "java/lang/Object", null);
		thingClass.visitEnd();
		ClassLoader loader = new BytesClassLoader(Map.of(wrapName, wrapRepaired.get(wrapName + ".class"), TARGET, game, operationName, operation.toByteArray(),
				THING, thingClass.toByteArray()));
		Class<?> mixin = loader.loadClass(wrapName.replace('/', '.'));
		Class<?> operationType = loader.loadClass(operationName.replace('/', '.'));
		List<Object> called = new ArrayList<>();
		Object constructor = java.lang.reflect.Proxy.newProxyInstance(loader, new Class<?>[] {operationType}, (proxy, method, args) -> {
			called.addAll(java.util.Arrays.asList((Object[]) args[0]));
			return null;
		});
		java.lang.reflect.Method bridge = mixin.getDeclaredMethod("wrap$fabricated", int.class, String.class, operationType);
		bridge.setAccessible(true);
		bridge.invoke(mixin.getConstructor().newInstance(), 5, "renderers", constructor);
		assertEquals(List.of(5, "renderers"), called);

		for (byte[] other : List.of(constructingHelper("(I)V", "(ILjava/lang/String;)V"), constructingHelper("(II)V"))) {
			assertTrue(preflight(new ClassIndex(Map.of(TARGET, other)::get), Map.of(name, original)::get, ClassBytesSource.EMPTY, false)
					.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty(),
					"the named constructor still called, or an added argument that can't be told apart");
		}
	}

	/**
	 * A handler that also takes MixinExtras sugar (resourcefullib's {@code @WrapOperation} at {@code BlockOutlineRenderState}'s
	 * construction takes a {@code @Local BlockState}) gets a bridge that takes the sugar too, with its annotation, and passes
	 * it on.
	 */
	@Test
	void aBridgeTakesTheHandlersSugarAndPassesItOn() throws Exception {
		String thing = "L" + THING + ";";
		String name = "com/example/mixin/AtNewSugar";
		Consumer<MethodVisitor> returnNull = mv -> {
			mv.visitInsn(Opcodes.ACONST_NULL);
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitMaxs(1, 4);
		};
		byte[] original = mixin(name, false, cw -> {
			atNewThing(cw, WRAP_OPERATION, "wrap", "(I" + OPERATION + "Ljava/lang/String;)" + thing, 2, returnNull);
			atNewThing(cw, REDIRECT, "redirect", "(ILjava/lang/String;)" + thing, 1, returnNull);
		});
		MixinConfigFile config = MixinConfigFile.parse(config(true, "AtNewSugar"));
		byte[] game = constructingHelper("(ILjava/lang/String;)V");
		ClassIndex neoForge = new ClassIndex(Map.of(TARGET, game)::get);

		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight(neoForge, Map.of(name, original)::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);

		assertEquals(2, notes.size(), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get(name + ".class")).accept(node, 0);
		Map<String, MethodNode> methods = new HashMap<>();
		node.methods.forEach(m -> methods.put(m.name, m));
		MethodNode wrap = methods.get("wrap$fabricated");
		assertEquals("(ILjava/lang/String;" + OPERATION + "Ljava/lang/String;)" + thing, wrap.desc);
		assertEquals(LOCAL, wrap.invisibleParameterAnnotations[3].getFirst().desc);
		assertTrue(Annotations.ofParameter(methods.get("wrap"), 2).isEmpty(), "the sugar moved off the handler");
		MethodNode redirect = methods.get("redirect$fabricated");
		assertEquals("(ILjava/lang/String;Ljava/lang/String;)" + thing, redirect.desc);
		assertEquals(LOCAL, redirect.invisibleParameterAnnotations[2].getFirst().desc);
		List<Integer> loads = new ArrayList<>();
		for (AbstractInsnNode insn : redirect.instructions) {
			if (insn instanceof VarInsnNode load) loads.add(load.var);
		}
		assertEquals(List.of(0, 1, 3), loads, "the redirect's bridge passes the amount and the local, not the added argument");
		List<PreflightFinding> after = preflight(neoForge, Map.of(name, repaired.get(name + ".class"))::get, ClassBytesSource.EMPTY, false)
				.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());

		// The bridges verify (their stack and locals sizes are right).
		ClassWriter operation = new ClassWriter(0);
		String operationName = OPERATION.substring(1, OPERATION.length() - 1);
		operation.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, operationName, null, "java/lang/Object", null);
		operation.visitEnd();
		ClassLoader loader = new BytesClassLoader(Map.of(name, repaired.get(name + ".class"), operationName, operation.toByteArray()));
		assertEquals(name.replace('/', '.'), Class.forName(name.replace('/', '.'), true, loader).getName());

		byte[] notSugar = mixin(name, false, cw -> atNewThing(cw, REDIRECT, "redirect", "(ILjava/lang/String;)" + thing, -1, returnNull));
		assertTrue(preflight(neoForge, Map.of(name, notSugar)::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty(), "a parameter after the call's that isn't sugar");
	}

	/**
	 * An injector in a named group is one of the mod's alternatives (Iris's {@code @Group(min = 1, max = 1)} pairs):
	 * pre-flight neither points it elsewhere, which could make two of a group apply, nor requires it, as Mixin doesn't.
	 */
	@Test
	void anInjectorInAGroupIsLeftToItsGroup() throws Exception {
		Map<String, byte[]> mod = Map.of("com/example/mixin/Grouped", mixin("com/example/mixin/Grouped", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "inject", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null);
			AnnotationVisitor group = mv.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Group;", false);
			group.visit("name", "alternatives");
			group.visit("max", 1);
			group.visitEnd();
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "tick");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", "INVOKE");
			at.visit("target", "L" + TARGET + ";helper(I)V");
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnd();
			mv.visitEnd();
		}));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Grouped"));
		ClassIndex neoForge = new ClassIndex(Map.of(TARGET, callingHelper("(ILjava/lang/String;)V"))::get);

		assertTrue(preflight(neoForge, mod::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
		List<PreflightFinding> findings = preflight(neoForge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(findings.stream().noneMatch(f -> f.severity() == Severity.FAIL), findings.toString());
	}

	/**
	 * A bridge takes the call's exact types, so a handler taking a supertype of the receiver (with {@code @Coerce}) isn't
	 * bridged: the bridge wouldn't carry the annotation, and Mixin would refuse it at apply time, after pre-flight.
	 */
	@Test
	void aHandlerCoercingTheReceiverIsNotBridged() throws Exception {
		Map<String, byte[]> mod = Map.of("com/example/mixin/Coerced", mixin("com/example/mixin/Coerced", false,
				cw -> atHelperCall(cw, REDIRECT, "coerced", "(Ljava/lang/Object;I)V", null)));
		ClassIndex neoForge = new ClassIndex(Map.of(TARGET, callingHelper("(ILjava/lang/String;)V"))::get);

		assertTrue(preflight(neoForge, mod::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", MixinConfigFile.parse(config(true, "Coerced")), Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
	}

	/** Two handlers sharing a name get bridges and lambdas of distinct names; the former handlers become {@code @Unique}. */
	@Test
	void handlersSharingANameGetDistinctBridges() throws Exception {
		String thing = "L" + TARGET + ";";
		Map<String, byte[]> mod = Map.of("com/example/mixin/Shared", mixin("com/example/mixin/Shared", false, cw -> {
			atHelperCall(cw, WRAP_OPERATION, "wrap", "(" + thing + "I" + OPERATION + ")V", "(I)V", null);
			atHelperCall(cw, WRAP_OPERATION, "wrap", "(" + thing + "J" + OPERATION + ")V", "(J)V", null);
		}));
		ClassIndex neoForge = new ClassIndex(Map.of(TARGET, callingHelper("(ILjava/lang/String;)V", "(JLjava/lang/String;)V"))::get);
		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight(neoForge, mod::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", MixinConfigFile.parse(config(true, "Shared")), Refmap.EMPTY, Set.of(), notes);

		assertEquals(2, notes.size(), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get("com/example/mixin/Shared.class")).accept(node, 0);
		Set<String> names = new java.util.HashSet<>();
		for (MethodNode m : node.methods) {
			if (m.name.contains("$fabricated")) assertTrue(names.add(m.name), "two generated methods named " + m.name);
			if (m.name.equals("wrap")) assertTrue(Annotations.find(m, "Lorg/spongepowered/asm/mixin/Unique;") != null, m.desc);
		}
		assertEquals(4, names.size(), names.toString());
	}

	@Test
	void theAddedArgumentOfACallIsToldApartByType() {
		assertEquals(1, MixinPreflight.insertedArgument("(I)V", "(ILjava/lang/String;)V"));
		assertEquals(0, MixinPreflight.insertedArgument("(I)V", "(Ljava/lang/String;I)V"));
		assertEquals(1, MixinPreflight.insertedArgument("(ILjava/lang/String;)Z", "(IJLjava/lang/String;)Z"));
		assertEquals(-1, MixinPreflight.insertedArgument("(I)V", "(II)V"), "either int may be the added one");
		assertEquals(-1, MixinPreflight.insertedArgument("(I)V", "(IJ)Z"), "another result");
		assertEquals(-1, MixinPreflight.insertedArgument("(I)V", "(IJJ)V"));
		assertEquals(-1, MixinPreflight.insertedArgument("(I)V", "(J)V"));
	}

	/** Defines the given classes (internal names) itself, before asking the test's class loader. */
	private static final class BytesClassLoader extends ClassLoader {
		private final Map<String, byte[]> classes;

		BytesClassLoader(Map<String, byte[]> classes) {
			super(MixinPreflightTest.class.getClassLoader());
			this.classes = classes;
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			synchronized (getClassLoadingLock(name)) {
				byte[] bytes = classes.get(name.replace('.', '/'));
				if (bytes == null) return super.loadClass(name, resolve);
				Class<?> loaded = findLoadedClass(name);
				return loaded != null ? loaded : defineClass(name, bytes, 0, bytes.length);
			}
		}
	}

	@Test
	void aWrapOperationBridgePutsTheAddedArgumentBackForTheOriginalCall() throws Exception {
		String thing = "L" + TARGET + ";";
		String name = "com/example/mixin/Run";
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
		AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor values = m.visitArray("value");
		values.visit(null, Type.getObjectType(TARGET));
		values.visitEnd();
		m.visitEnd();
		MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		init.visitCode();
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(0, 0);
		init.visitEnd();
		// wrap(thing, amount, original) { original.call(thing, amount); }
		atHelperCall(cw, WRAP_OPERATION, "wrap", "(" + thing + "I" + OPERATION + ")V", mv -> {
			mv.visitVarInsn(Opcodes.ALOAD, 3);
			mv.visitInsn(Opcodes.ICONST_2);
			mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
			mv.visitInsn(Opcodes.DUP);
			mv.visitInsn(Opcodes.ICONST_0);
			mv.visitVarInsn(Opcodes.ALOAD, 1);
			mv.visitInsn(Opcodes.AASTORE);
			mv.visitInsn(Opcodes.DUP);
			mv.visitInsn(Opcodes.ICONST_1);
			mv.visitVarInsn(Opcodes.ILOAD, 2);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
			mv.visitInsn(Opcodes.AASTORE);
			mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, OPERATION.substring(1, OPERATION.length() - 1), "call", "([Ljava/lang/Object;)Ljava/lang/Object;", true);
			mv.visitInsn(Opcodes.POP);
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 0);
		});
		cw.visitEnd();
		// A long before the int: the added argument goes first, and takes two local variable slots.
		byte[] game = callingHelper("(JI)V");
		Map<String, byte[]> repaired = preflight(new ClassIndex(Map.of(TARGET, game)::get), Map.of(name, cw.toByteArray())::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", MixinConfigFile.parse(config(true, "Run")), Refmap.EMPTY, Set.of(), new ArrayList<>());

		ClassWriter operation = new ClassWriter(0);
		String operationName = OPERATION.substring(1, OPERATION.length() - 1);
		operation.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, operationName, null, "java/lang/Object", null);
		operation.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_VARARGS, "call", "([Ljava/lang/Object;)Ljava/lang/Object;", null, null).visitEnd();
		operation.visitEnd();
		ClassLoader loader = new BytesClassLoader(Map.of(name, repaired.get(name + ".class"), TARGET, game, operationName, operation.toByteArray()));
		Class<?> mixin = loader.loadClass(name.replace('/', '.'));
		Class<?> operationType = loader.loadClass(operationName.replace('/', '.'));
		List<Object> called = new ArrayList<>();
		Object original = java.lang.reflect.Proxy.newProxyInstance(loader, new Class<?>[] {operationType}, (proxy, method, args) -> {
			called.addAll(java.util.Arrays.asList((Object[]) args[0]));
			return null;
		});
		java.lang.reflect.Method bridge = mixin.getDeclaredMethod("wrap$fabricated", loader.loadClass(TARGET.replace('/', '.')), long.class, int.class, operationType);
		bridge.setAccessible(true);
		bridge.invoke(mixin.getConstructor().newInstance(), null, 7L, 5, original);

		assertEquals(java.util.Arrays.asList(null, 7L, 5), called);
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
		assertEquals(Severity.LEFT_OUT, missing.getFirst().severity());
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
		assertTrue(before.size() == 1 && before.getFirst().severity() == Severity.LEFT_OUT && before.getFirst().message().contains("abstract")
				&& "h(ILcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)Ljava/lang/Object;".equals(before.getFirst().handler()), before.toString());
		List<PreflightFinding> notes = new ArrayList<>();
		mod.put("com/example/mixin/Wrap", preflight(game, mod::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes).get("com/example/mixin/Wrap.class"));
		assertTrue(notes.getFirst().message().contains("@WrapMethod h targets create(I)Ljava/lang/Object;"), notes.toString());
		List<PreflightFinding> after = preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());
	}

	@Test
	void aWrapMethodWhoseHandlerTakesOtherParametersFails() throws Exception {
		// NeoForge's build declares the method with other parameters than the vanilla one the handler was written for.
		ClassWriter target = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		target.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor render = target.visitMethod(Opcodes.ACC_PUBLIC, "render", "(Ljava/lang/String;)V", null, null);
		render.visitCode();
		render.visitInsn(Opcodes.RETURN);
		render.visitMaxs(0, 0);
		render.visitEnd();
		target.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(TARGET, target.toByteArray())::get);
		java.util.function.Function<String, byte[]> wrapping = desc -> mixin("com/example/mixin/Wrap", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "h", desc, null, null);
			AnnotationVisitor ann = mv.visitAnnotation("Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;", false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "render");
			methods.visitEnd();
			ann.visitEnd();
			mv.visitEnd();
		});
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Wrap"));
		String operation = "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;";
		List<PreflightFinding> wrong = preflight(game, Map.of("com/example/mixin/Wrap", wrapping.apply("(FL" + operation.substring(1) + ")V"))::get,
				ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertEquals(1, wrong.size(), wrong.toString());
		assertEquals(Severity.LEFT_OUT, wrong.getFirst().severity());
		assertTrue(wrong.getFirst().message().contains("does not match the parameters of render(Ljava/lang/String;)V"), wrong.getFirst().message());
		List<PreflightFinding> right = preflight(game, Map.of("com/example/mixin/Wrap", wrapping.apply("(Ljava/lang/String;" + operation + ")V"))::get,
				ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(right.isEmpty(), right.toString());
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
		return mixin(name, pseudo, List.of(), body);
	}

	/** A mixin that also adds {@code interfaces} to its target. */
	private static byte[] mixin(String name, boolean pseudo, List<String> interfaces, Consumer<ClassWriter> body) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, name, null, "java/lang/Object", interfaces.toArray(String[]::new));
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
		assertEquals(Severity.LEFT_OUT, findings.getFirst().severity());
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
		assertEquals(Severity.LEFT_OUT, findings.getFirst().severity());
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

	/**
	 * A {@code @Redirect} built against Fabric's Mixin, whose {@code at} is an array, gets the single injection point
	 * NeoForge 1.21.11's MixinExtras reads (MIX-1); the mixin is rewritten even when its target is no game class.
	 */
	@Test
	void aRedirectsInjectionPointArrayOfOneBecomesSingle() throws Exception {
		Function<String, byte[]> redirect = target -> {
			ClassWriter cw = new ClassWriter(0);
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/mixin/Redir", null, "java/lang/Object", null);
			AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
			AnnotationVisitor targets = m.visitArray("value");
			targets.visit(null, Type.getObjectType(target));
			targets.visitEnd();
			m.visitEnd();
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "h", "(L" + TARGET + ";I)V", null, null);
			AnnotationVisitor ann = mv.visitAnnotation(REDIRECT, true);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "tick");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", "INVOKE");
			at.visit("target", "L" + TARGET + ";helper(I)V");
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 3);
			mv.visitEnd();
			cw.visitEnd();
			return cw.toByteArray();
		};
		ClassIndex game = new ClassIndex(Map.of(TARGET, targetClass())::get);
		for (String target : List.of(TARGET, "com/othermod/Machine")) {
			Map<String, byte[]> mod = Map.of("com/example/mixin/Redir", redirect.apply(target));
			Map<String, byte[]> repaired = preflight(game, mod::get, ClassBytesSource.EMPTY, false)
					.repairs("test.mixins.json", MixinConfigFile.parse(config(true, "Redir")), Refmap.EMPTY, Set.of(), new ArrayList<>());
			ClassNode node = new ClassNode();
			new ClassReader(repaired.get("com/example/mixin/Redir.class")).accept(node, 0);
			AnnotationNode ann = node.methods.getFirst().visibleAnnotations.getFirst();
			assertTrue(ann.values.get(ann.values.indexOf("at") + 1) instanceof AnnotationNode, target + ": " + ann.values);
		}
	}

	@Test
	void failsWhenRequiredInjectionTargetIsMissing() throws Exception {
		byte[] bad = mixin("com/example/mixin/Bad", false, cw -> inject(cw, "h", "removedMethod", "HEAD", null, 1, null));
		List<PreflightFinding> findings = run(config(true, "Bad"), null, Map.of("com/example/mixin/Bad", bad), false);
		assertEquals(1, findings.size());
		assertEquals(Severity.LEFT_OUT, findings.getFirst().severity());
		assertTrue(findings.getFirst().message().contains("removedMethod"));
	}

	/**
	 * A failure in a self-contained mixin leaves the failing injector out, and the mod loads (LEAVE-1); with a field of
	 * its own, which its injectors may share, the whole mixin goes, its working injector too. The same mixin adding an
	 * interface to its target still fails the mod: the mod's own code casts the target to that interface.
	 */
	@Test
	void onlyASelfContainedMixinIsLeftOut() throws Exception {
		Consumer<ClassWriter> body = cw -> {
			inject(cw, "works", "tick", "HEAD", null, 1, null);
			inject(cw, "broken", "removedMethod", "HEAD", null, 1, null);
			cw.visitField(Opcodes.ACC_PRIVATE, "count", "I", null, null).visitAnnotation("Lorg/spongepowered/asm/mixin/Shadow;", false).visitEnd();
		};
		List<PreflightFinding> alone = run(config(true, "Alone"), null, Map.of("com/example/mixin/Alone", mixin("com/example/mixin/Alone", false, body)), false);
		assertEquals(1, alone.size(), alone.toString());
		assertEquals(Severity.LEFT_OUT, alone.getFirst().severity());
		assertEquals("broken()V", alone.getFirst().handler(), "only the injector: the mixin adds no fields");

		// With a field of its own, which its injectors may share, the whole mixin goes.
		byte[] stateful = mixin("com/example/mixin/Stateful", false, body.andThen(cw -> cw.visitField(Opcodes.ACC_PRIVATE, "count", "I", null, null).visitEnd()));
		List<PreflightFinding> withField = run(config(true, "Stateful"), null, Map.of("com/example/mixin/Stateful", stateful), false);
		assertEquals(1, withField.size(), withField.toString());
		assertEquals(Severity.LEFT_OUT, withField.getFirst().severity());
		assertEquals(null, withField.getFirst().handler(), "the whole mixin");

		byte[] duck = mixin("com/example/mixin/Duck", false, List.of("com/example/api/Duck"), body);
		List<PreflightFinding> withInterface = run(config(true, "Duck"), null, Map.of("com/example/mixin/Duck", duck), false);
		assertEquals(1, withInterface.size(), withInterface.toString());
		assertEquals(Severity.FAIL, withInterface.getFirst().severity());
		assertEquals("broken()V", withInterface.getFirst().handler());
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
		assertEquals(Severity.LEFT_OUT, findings.getFirst().severity(), "only one call exists, ordinal 3 cannot match");
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
		assertEquals(Severity.LEFT_OUT, run(config(true, "Missing"), null, Map.of("com/example/mixin/Missing", missing), false).getFirst().severity());
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
		assertEquals(Severity.LEFT_OUT, findings.getFirst().severity());
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

	/**
	 * A broken accessor in a plugin's or an optional config is left out alone: the interface still applies, so the mod's
	 * casts to it and its other accessors work (OneConfig's {@code KeyMappingRegistryAccessor}, whose {@code MAP} field
	 * NeoForge replaced). In a required config without a plugin it fails the mod, as Mixin would stop the game.
	 */
	@Test
	void aBrokenAccessorIsLeftOutAloneUnlessTheConfigRequiresIt() throws Exception {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE, "com/example/mixin/Access", null, "java/lang/Object", null);
		AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor values = m.visitArray("value");
		values.visit(null, Type.getObjectType(TARGET));
		values.visitEnd();
		m.visitEnd();
		for (String field : List.of("gone", "count")) {
			MethodVisitor getter = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "get" + field, "()I", null, null);
			AnnotationVisitor accessor = getter.visitAnnotation("Lorg/spongepowered/asm/mixin/gen/Accessor;", false);
			accessor.visit("value", field);
			accessor.visitEnd();
			getter.visitEnd();
		}
		cw.visitEnd();
		Map<String, byte[]> mod = Map.of("com/example/mixin/Access", cw.toByteArray());
		for (String cfg : List.of("{\"required\":true,\"package\":\"com.example.mixin\",\"plugin\":\"com.example.Plugin\",\"mixins\":[\"Access\"]}",
				"{\"required\":false,\"package\":\"com.example.mixin\",\"mixins\":[\"Access\"]}")) {
			List<PreflightFinding> findings = run(cfg, null, mod, false);
			assertEquals(List.of("getgone()I"), findings.stream().filter(f -> f.severity() == Severity.LEFT_OUT).map(PreflightFinding::handler).toList(),
					cfg + ": " + findings);
			assertTrue(findings.stream().noneMatch(f -> f.severity() == Severity.FAIL), cfg + ": " + findings);
		}
		List<PreflightFinding> required = run(config(true, "Access"), null, mod, false);
		assertTrue(required.stream().anyMatch(f -> f.severity() == Severity.FAIL && "getgone()I".equals(f.handler())), required.toString());
	}

	@Test
	void anEnumMixinIntoAnExtensibleEnumFailsItsModEvenWithAPlugin() throws Exception {
		// The mod's own classes read the constants such a mixin adds: leaving it out, as a plugin config's crashing
		// injectors are, would only move the crash into the mod (Farmer's Delight, CORPUS-10).
		ClassWriter target = new ClassWriter(0);
		target.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM, TARGET, null, "java/lang/Enum",
				new String[] {"net/neoforged/fml/common/asm/enumextension/IExtensibleEnum"});
		target.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(TARGET, target.toByteArray())::get);
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM, "com/example/mixin/Constants", null, "java/lang/Enum", null);
		AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor values = m.visitArray("value");
		values.visit(null, Type.getObjectType(TARGET));
		values.visitEnd();
		m.visitEnd();
		cw.visitEnd();
		Map<String, byte[]> mod = Map.of("com/example/mixin/Constants", cw.toByteArray());
		for (String plugin : List.of("", ",\"plugin\":\"com.example.Plugin\"")) {
			String cfg = "{\"required\":true,\"package\":\"com.example.mixin\"" + plugin + ",\"mixins\":[\"Constants\"]}";
			List<PreflightFinding> findings = preflight(game, mod::get, ClassBytesSource.EMPTY, false)
					.check("test.mixins.json", MixinConfigFile.parse(cfg), Refmap.EMPTY, Set.of());
			assertEquals(Severity.FAIL, findings.getFirst().severity(), findings.toString());
			assertTrue(findings.getFirst().message().contains("extensible enums"), findings.toString());
		}
	}

	@Test
	void clientMixinsAreSkippedOnServer() throws Exception {
		byte[] bad = mixin("com/example/mixin/ClientBad", false, cw -> inject(cw, "h", "removedMethod", "HEAD", null, 1, null));
		String cfg = "{\"required\":true,\"package\":\"com.example.mixin\",\"client\":[\"ClientBad\"]}";
		assertTrue(run(cfg, null, Map.of("com/example/mixin/ClientBad", bad), false).isEmpty());
		assertEquals(Severity.LEFT_OUT, run(cfg, null, Map.of("com/example/mixin/ClientBad", bad), true).getFirst().severity());
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
		assertEquals(PreflightFinding.Severity.LEFT_OUT, withLibraries.getFirst().severity());
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
		assertEquals(PreflightFinding.Severity.LEFT_OUT, fail.getFirst().severity());

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
		assertEquals(PreflightFinding.Severity.LEFT_OUT, missingLoad.getFirst().severity());
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
		assertEquals(PreflightFinding.Severity.LEFT_OUT, fail.getFirst().severity());

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
		assertEquals(Severity.LEFT_OUT, wrong.getFirst().severity());
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
