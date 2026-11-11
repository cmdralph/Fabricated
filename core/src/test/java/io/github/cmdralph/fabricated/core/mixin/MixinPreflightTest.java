package io.github.cmdralph.fabricated.core.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.github.cmdralph.fabricated.core.archive.Hashing;
import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;
import io.github.cmdralph.fabricated.core.bytecode.InstalledClasses;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding.Severity;
import io.github.cmdralph.fabricated.core.testutil.BytesClassLoader;
import io.github.cmdralph.fabricated.core.testutil.TestSources;

class MixinPreflightTest {
	private static final String TARGET = "net/minecraft/world/Thing";
	/** A class the target constructs (a record of the game, say). */
	private static final String THING = "net/minecraft/world/Outline";
	private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
	private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
	private static final String WRAP_OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
	private static final String OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;";
	private static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
	private static final String MODIFY_VARIABLE = "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;";
	private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
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
		MixinPreflight preflight = new MixinPreflight(new ClassIndex(installed), installed, mod::get, ClassBytesSource.EMPTY, false, true, ClassBytesSource.EMPTY);

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

	/**
	 * CORPUS-74: MoreCulling's mixin into {@code BlockModelRenderState} shadows {@code mesh}, the field Fabric API's
	 * renderer mixin adds there. Fabric's Mixin finds the shadow among the fields of the mixins it prepared before; Forge's
	 * (SpongePowered's) looks in the target's own only and stopped the client. Where the running Mixin doesn't find them,
	 * the shadow is declared as the field the other mixin adds (its access and signature, without Mixin's annotations),
	 * which Mixin merges with it into one field, and the mixin's code keeps using it; a shadow that can't be declared so
	 * (here: a static one of an instance field) fails as Mixin would make it. Where the running Mixin finds them
	 * (NeoForge's), nothing changes and the shadow isn't judged.
	 */
	@Test
	void aShadowOfAFieldAnotherModsMixinAddsIsThatFieldWhereMixinFindsOnlyTheTargetsOwn() throws Exception {
		String target = "net/minecraft/client/renderer/block/BlockModelRenderState";
		String mesh = "Lnet/fabricmc/fabric/api/client/renderer/v1/mesh/MutableMesh;";
		ClassWriter tw = new ClassWriter(0);
		tw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, target, null, "java/lang/Object", null);
		tw.visitField(Opcodes.ACC_PRIVATE, "modelParts", "Ljava/util/List;", null, null).visitEnd();
		tw.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(target, tw.toByteArray())::get);
		// Fabric API's mixin: @Unique @Nullable private MutableMesh mesh, and a static one for the refused case.
		String adder = "net/fabricmc/fabric/mixin/renderer/client/BlockModelRenderStateMixin";
		ClassWriter aw = new ClassWriter(0);
		aw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, adder, null, "java/lang/Object", null);
		AnnotationVisitor am = aw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor av = am.visitArray("value");
		av.visit(null, Type.getObjectType(target));
		av.visitEnd();
		am.visitEnd();
		FieldVisitor added = aw.visitField(Opcodes.ACC_PRIVATE, "mesh", mesh, null, null);
		added.visitAnnotation("Lorg/spongepowered/asm/mixin/Unique;", false).visitEnd();
		added.visitEnd();
		FieldVisitor shared = aw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "fabric$shared", "I", null, null);
		shared.visitAnnotation("Lorg/spongepowered/asm/mixin/Unique;", false).visitEnd();
		shared.visitEnd();
		aw.visitEnd();
		byte[] adderClass = aw.toByteArray();
		InstalledClasses installed = new InstalledClasses(List.of(new InstalledClasses.Jar("Fabric API", adder::equals,
				name -> name.equals(adder) ? adderClass : null, () -> List.of(adder), true)));
		// MoreCulling's mixin: @Shadow private MutableMesh mesh, read by its method; and a shadow of the static field.
		Function<String, byte[]> culling = field -> {
			ClassWriter cw = new ClassWriter(0);
			String name = "com/example/mixin/Culling";
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, name, null, "java/lang/Object", new String[] {"com/example/Extended"});
			AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
			AnnotationVisitor values = m.visitArray("value");
			values.visit(null, Type.getObjectType(target));
			values.visitEnd();
			m.visit("priority", 1200);
			m.visitEnd();
			boolean isMesh = field.equals("mesh");
			FieldVisitor shadow = cw.visitField(Opcodes.ACC_PRIVATE, field, isMesh ? mesh : "I", null, null);
			shadow.visitAnnotation("Lorg/spongepowered/asm/mixin/Shadow;", true).visitEnd();
			shadow.visitAnnotation("Lorg/jspecify/annotations/Nullable;", true).visitEnd();
			shadow.visitEnd();
			MethodVisitor read = cw.visitMethod(Opcodes.ACC_PUBLIC, "moreculling$hasMesh", "()Z", null, null);
			read.visitCode();
			read.visitVarInsn(Opcodes.ALOAD, 0);
			read.visitFieldInsn(Opcodes.GETFIELD, name, field, isMesh ? mesh : "I");
			if (!isMesh) read.visitInsn(Opcodes.POP);
			read.visitInsn(Opcodes.ICONST_1);
			read.visitInsn(Opcodes.IRETURN);
			read.visitMaxs(1, 1);
			read.visitEnd();
			cw.visitEnd();
			return cw.toByteArray();
		};
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Culling"));
		BiFunction<Map<String, byte[]>, Boolean, MixinPreflight> preflight = (mod, seesMixinFields) -> new MixinPreflight(game, installed,
				mod::get, ClassBytesSource.EMPTY, true, true, ClassBytesSource.EMPTY, "Forge", true, MixinPreflight.ConstructorInjects.UNSAFE_POINTS,
				false, false, false, true, seesMixinFields);

		// Forge: declared as Fabric API's field, its use left as it was; nothing left to judge.
		Map<String, byte[]> forge = new HashMap<>(Map.of("com/example/mixin/Culling", culling.apply("mesh")));
		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight.apply(forge, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		assertEquals(Set.of("com/example/mixin/Culling.class"), repaired.keySet(), notes.toString());
		assertEquals(1, notes.size(), notes.toString());
		assertEquals(Severity.INFO, notes.getFirst().severity());
		assertTrue(notes.getFirst().message().contains("Fabric API's mixin " + adder.replace('/', '.') + " adds to " + target.replace('/', '.')),
				notes.getFirst().message());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get("com/example/mixin/Culling.class")).accept(node, 0);
		org.objectweb.asm.tree.FieldNode field = node.fields.getFirst();
		assertEquals("mesh", field.name);
		assertEquals(mesh, field.desc);
		assertEquals(Opcodes.ACC_PRIVATE, field.access);
		assertNull(Annotations.find(field, "Lorg/spongepowered/asm/mixin/Shadow;"));
		assertNotNull(Annotations.find(field, "Lorg/jspecify/annotations/Nullable;"), "the mod's other annotations stay");
		FieldInsnNode use = (FieldInsnNode) java.util.Arrays.stream(node.methods.getFirst().instructions.toArray())
				.filter(i -> i instanceof FieldInsnNode).findFirst().orElseThrow();
		assertEquals("com/example/mixin/Culling", use.owner);
		assertEquals("mesh", use.name);
		forge.put("com/example/mixin/Culling", repaired.get("com/example/mixin/Culling.class"));
		assertTrue(preflight.apply(forge, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());

		// Forge, a shadow that can't be declared as the other mixin's field (an instance shadow of a static field): it fails.
		Map<String, byte[]> refused = new HashMap<>(Map.of("com/example/mixin/Culling", culling.apply("fabric$shared")));
		assertTrue(preflight.apply(refused, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
		List<PreflightFinding> failed = preflight.apply(refused, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertEquals(1, failed.size(), failed.toString());
		assertNotEquals(Severity.INFO, failed.getFirst().severity());
		assertTrue(failed.getFirst().message().contains("Forge's Mixin finds a @Shadow field only among the target's own"), failed.getFirst().message());

		// NeoForge (Fabric's Mixin): left as it is, and not judged.
		Map<String, byte[]> neoForge = new HashMap<>(Map.of("com/example/mixin/Culling", culling.apply("mesh")));
		assertTrue(preflight.apply(neoForge, true).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
		List<PreflightFinding> fabric = preflight.apply(neoForge, true).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertEquals(1, fabric.size(), fabric.toString());
		assertEquals(Severity.INFO, fabric.getFirst().severity());
		assertTrue(fabric.getFirst().message().contains("adds it, so it was not verified"), fabric.getFirst().message());
	}

	/**
	 * CORPUS-84: MoreCulling's mixin into {@code MultiPartBakedModel} shadows {@code getQuads(BlockState, Direction,
	 * RandomSource)}, which Forge 47 moves into the default method of {@code IDynamicBakedModel}, the interface it makes the
	 * model implement. Mixin finds a shadowed method only among the target's own, and stopped the corpus client; pre-flight
	 * looked it up through the supertypes and let it by. The shadow is dropped (a prefixed one's calls take the method's
	 * name), and the mixin's calls of it, which Mixin points at the target, reach the default method. Where a use isn't
	 * virtual (an {@code invokespecial}) nothing changes, and pre-flight fails the mixin as Mixin would. A shadow of a
	 * method the target declares (vanilla's model, as on Fabric) is left as it is.
	 */
	@Test
	void aShadowOfAMethodTheTargetOnlyInheritsIsDroppedForTheInheritedOne() throws Exception {
		String target = "net/minecraft/client/resources/model/MultiPartBakedModel";
		String dynamic = "net/minecraftforge/client/model/IDynamicBakedModel";
		String quads = "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/util/RandomSource;)Ljava/util/List;";
		ClassWriter iw = new ClassWriter(0);
		iw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, dynamic, null, "java/lang/Object", null);
		MethodVisitor fallback = iw.visitMethod(Opcodes.ACC_PUBLIC, "m_213637_", quads, null, null);
		fallback.visitCode();
		fallback.visitInsn(Opcodes.ACONST_NULL);
		fallback.visitInsn(Opcodes.ARETURN);
		fallback.visitMaxs(1, 4);
		fallback.visitEnd();
		iw.visitEnd();
		Function<Boolean, byte[]> model = declares -> {
			ClassWriter tw = new ClassWriter(0);
			tw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, target, null, "java/lang/Object", declares ? null : new String[] {dynamic});
			if (declares) tw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "m_213637_", quads, null, null).visitEnd();
			tw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "getSelectors", "()Ljava/util/BitSet;", null, null).visitEnd();
			tw.visitEnd();
			return tw.toByteArray();
		};
		String culling = "com/example/mixin/Culling";
		// @Shadow public abstract List getQuads(...) (prefixed or not), and a method calling it with the given opcode.
		BiFunction<String, Integer, byte[]> mixin = (shadowName, call) -> {
			ClassWriter cw = new ClassWriter(0);
			cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, culling, null, "java/lang/Object", null);
			AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
			AnnotationVisitor values = m.visitArray("value");
			values.visit(null, Type.getObjectType(target));
			values.visitEnd();
			m.visitEnd();
			MethodVisitor shadow = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, shadowName, quads, null, null);
			shadow.visitAnnotation(SHADOW, true).visitEnd();
			shadow.visitEnd();
			MethodVisitor reset = cw.visitMethod(Opcodes.ACC_PUBLIC, "resetTranslucencyCache", "()Z", null, null);
			reset.visitCode();
			reset.visitVarInsn(Opcodes.ALOAD, 0);
			reset.visitInsn(Opcodes.ACONST_NULL);
			reset.visitInsn(Opcodes.ACONST_NULL);
			reset.visitInsn(Opcodes.ACONST_NULL);
			reset.visitMethodInsn(call, culling, shadowName, quads, false);
			reset.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "isEmpty", "()Z", true);
			reset.visitInsn(Opcodes.IRETURN);
			reset.visitMaxs(4, 1);
			reset.visitEnd();
			cw.visitEnd();
			return cw.toByteArray();
		};
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Culling"));
		BiFunction<Boolean, byte[], MixinPreflight> preflight = (declares, culled) -> new MixinPreflight(
				new ClassIndex(Map.of(target, model.apply(declares), dynamic, iw.toByteArray())::get), InstalledClasses.NONE,
				Map.of(culling, culled)::get, ClassBytesSource.EMPTY, true, true, ClassBytesSource.EMPTY, "Forge");

		// Forge 47: the shadow is dropped, its call left as it was (Mixin points it at the target); nothing left to judge.
		for (String shadowName : List.of("m_213637_", "shadow$m_213637_")) {
			byte[] culled = mixin.apply(shadowName, Opcodes.INVOKEVIRTUAL);
			List<PreflightFinding> notes = new ArrayList<>();
			Map<String, byte[]> repaired = preflight.apply(false, culled).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
			assertEquals(Set.of(culling + ".class"), repaired.keySet(), notes.toString());
			assertEquals(1, notes.size(), notes.toString());
			assertEquals(Severity.INFO, notes.getFirst().severity());
			assertTrue(notes.getFirst().message().contains("@Shadow method m_213637_" + quads + " is one " + target.replace('/', '.')
					+ " inherits from " + dynamic.replace('/', '.')), notes.getFirst().message());
			ClassNode node = new ClassNode();
			new ClassReader(repaired.get(culling + ".class")).accept(node, 0);
			assertEquals(List.of("resetTranslucencyCache"), node.methods.stream().map(method -> method.name).toList());
			MethodInsnNode use = (MethodInsnNode) java.util.Arrays.stream(node.methods.getFirst().instructions.toArray())
					.filter(i -> i.getOpcode() == Opcodes.INVOKEVIRTUAL).findFirst().orElseThrow();
			assertEquals(culling + ".m_213637_" + quads, use.owner + "." + use.name + use.desc, shadowName);
			assertTrue(preflight.apply(false, repaired.get(culling + ".class")).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());
		}

		// Forge 47, a use that isn't virtual: nothing changes, and the mixin fails as Mixin would make it.
		byte[] special = mixin.apply("m_213637_", Opcodes.INVOKESPECIAL);
		assertTrue(preflight.apply(false, special).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
		List<PreflightFinding> failed = preflight.apply(false, special).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertEquals(1, failed.size(), failed.toString());
		assertEquals(Severity.LEFT_OUT, failed.getFirst().severity(), "a mixin nothing else uses is left out");
		assertTrue(failed.getFirst().message().contains("is not declared in " + target.replace('/', '.') + ", which inherits it, and Mixin "
				+ "finds a @Shadow method only among the target's own methods"), failed.getFirst().message());

		// The model declaring the method (vanilla's, as on Fabric): left as it is, and nothing to judge.
		byte[] vanilla = mixin.apply("m_213637_", Opcodes.INVOKEVIRTUAL);
		assertTrue(preflight.apply(true, vanilla).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
		assertTrue(preflight.apply(true, vanilla).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());
	}

	/**
	 * CORPUS-101: Porting Lib's {@code ItemTransformMixin} adds {@code public Vector3f rightRotation}, the field it ports
	 * from Forge, to {@code ItemTransform}, and writes it in {@code setRightRotation}. Forge 47 declares that field final;
	 * Mixin keeps the target's field for the mixin's, and the JVM refused the setter's write (an {@code IllegalAccessError}
	 * as the Forge 47 corpus client loaded its models). The mixin's field is declared {@code @Shadow @Final @Mutable}, so
	 * Mixin makes the target's writable. Nothing changes where the target's field isn't final, where the mixin writes it
	 * only in its constructor (allowed), or where the target has no such field (vanilla's, as on Fabric).
	 */
	@Test
	void aMixinsOwnFieldTheTargetDeclaresFinalIsMadeAMutableShadowWhereTheMixinWritesIt() throws Exception {
		String target = "net/minecraft/client/renderer/block/model/ItemTransform";
		String vector = "Lorg/joml/Vector3f;";
		Function<Integer, byte[]> transform = access -> {
			ClassWriter tw = new ClassWriter(0);
			tw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, target, null, "java/lang/Object", null);
			tw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "f_111755_", vector, null, null).visitEnd();
			if (access != null) tw.visitField(access, "rightRotation", vector, null, null).visitEnd();
			tw.visitEnd();
			return tw.toByteArray();
		};
		String ported = "com/example/mixin/ItemTransformMixin";
		// public Vector3f rightRotation, with getRightRotation(), and setRightRotation(Vector3f) or a constructor writing it.
		Function<String, byte[]> mixin = writer -> {
			ClassWriter cw = new ClassWriter(0);
			cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, ported, null, "java/lang/Object", null);
			AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
			AnnotationVisitor values = m.visitArray("value");
			values.visit(null, Type.getObjectType(target));
			values.visitEnd();
			m.visitEnd();
			cw.visitField(Opcodes.ACC_PUBLIC, "rightRotation", vector, null, null).visitEnd();
			MethodVisitor get = cw.visitMethod(Opcodes.ACC_PUBLIC, "getRightRotation", "()" + vector, null, null);
			get.visitCode();
			get.visitVarInsn(Opcodes.ALOAD, 0);
			get.visitFieldInsn(Opcodes.GETFIELD, ported, "rightRotation", vector);
			get.visitInsn(Opcodes.ARETURN);
			get.visitMaxs(1, 1);
			get.visitEnd();
			boolean constructor = writer.equals("<init>");
			MethodVisitor set = cw.visitMethod(Opcodes.ACC_PUBLIC, writer, "(" + vector + ")V", null, null);
			set.visitCode();
			if (constructor) {
				set.visitVarInsn(Opcodes.ALOAD, 0);
				set.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
			}
			set.visitVarInsn(Opcodes.ALOAD, 0);
			set.visitVarInsn(Opcodes.ALOAD, 1);
			set.visitFieldInsn(Opcodes.PUTFIELD, ported, "rightRotation", vector);
			set.visitInsn(Opcodes.RETURN);
			set.visitMaxs(2, 2);
			set.visitEnd();
			cw.visitEnd();
			return cw.toByteArray();
		};
		MixinConfigFile config = MixinConfigFile.parse(config(true, "ItemTransformMixin"));
		BiFunction<Integer, byte[], MixinPreflight> preflight = (access, mod) -> new MixinPreflight(
				new ClassIndex(Map.of(target, transform.apply(access))::get), InstalledClasses.NONE,
				Map.of(ported, mod)::get, ClassBytesSource.EMPTY, true, true, ClassBytesSource.EMPTY, "Forge");

		// Forge 47: the target's field is final, the setter writes it; a mutable shadow, and nothing left to judge.
		byte[] setter = mixin.apply("setRightRotation");
		int forge = Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL;
		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight.apply(forge, setter).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		assertEquals(Set.of(ported + ".class"), repaired.keySet(), notes.toString());
		assertEquals(1, notes.size(), notes.toString());
		assertEquals(Severity.INFO, notes.getFirst().severity());
		assertTrue(notes.getFirst().message().contains("Field rightRotation is the mixin's own, and " + target.replace('/', '.')
				+ " already declares it final"), notes.getFirst().message());
		assertTrue(notes.getFirst().message().contains("(CORPUS-101)"), notes.getFirst().message());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get(ported + ".class")).accept(node, 0);
		org.objectweb.asm.tree.FieldNode field = node.fields.getFirst();
		assertEquals("rightRotation", field.name);
		assertEquals(Opcodes.ACC_PUBLIC, field.access, "the mixin's own access, which Mixin checks against nothing on a shadow");
		for (String annotation : List.of(SHADOW, "Lorg/spongepowered/asm/mixin/Final;", "Lorg/spongepowered/asm/mixin/Mutable;")) {
			assertNotNull(Annotations.find(field, annotation), annotation);
		}
		assertTrue(preflight.apply(forge, repaired.get(ported + ".class")).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());

		// The target's field not final, the mixin writing it only in its constructor, or no such field (vanilla, as on
		// Fabric): left as it is, and nothing to judge.
		for (Map.Entry<Integer, byte[]> unchanged : List.of(Map.entry(Opcodes.ACC_PUBLIC, setter), Map.entry(forge, mixin.apply("<init>")))) {
			MixinPreflight check = preflight.apply(unchanged.getKey(), unchanged.getValue());
			assertTrue(check.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
			assertTrue(check.check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());
		}
		MixinPreflight vanilla = preflight.apply(null, setter);
		assertTrue(vanilla.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
		assertTrue(vanilla.check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());
	}

	/**
	 * ENUM-8: FrozenLib's {@code MobCategoryMixin} adds the mob categories its entrypoints declare as on Fabric: a class
	 * mixin into {@code MobCategory} with a constructor invoker, an {@code @Inject} into the class initializer after it sets
	 * the values array, and a {@code @Shadow @Final @Mutable $VALUES} it appends the new constants to. Forge makes
	 * {@code MobCategory} extensible; its enum extender, which runs after Mixin, finds the values array as the enum's first
	 * private static final synthetic field of its array type, and Mixin had made it non-final for the {@code @Mutable}
	 * shadow: "Enum has create method but we could not find $VALUES" stopped every Forge 61 advanced corpus server (extended
	 * run 42682670119). The shadow loses {@code @Mutable} and {@code @Final}, so Mixin leaves the field final for the
	 * extender, which makes it writable itself; an accessor setter of the array loses {@code @Mutable} too. A mixin into an
	 * enum Forge doesn't make extensible (NeoForge's), or a mutable shadow of another field, is left as it is.
	 */
	@Test
	void aMutableShadowOfTheValuesArrayOfAnEnumForgeMakesExtensibleIsNoLongerMutable() throws Exception {
		String category = "net/minecraft/world/entity/MobCategory";
		String self = "L" + category + ";";
		String array = "[" + self;
		String constructor = "(Ljava/lang/String;ILjava/lang/String;)V";
		Function<Boolean, byte[]> target = forge -> {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_ENUM, category,
					"Ljava/lang/Enum<" + self + ">;", "java/lang/Enum", forge ? new String[] {"net/minecraftforge/common/IExtensibleEnum"} : null);
			cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM, "MONSTER", self, null, null).visitEnd();
			cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "CACHE", array, null, null).visitEnd();
			cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, "$VALUES", array, null, null).visitEnd();
			MethodVisitor init = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", constructor, null, null);
			init.visitCode();
			init.visitVarInsn(Opcodes.ALOAD, 0);
			init.visitVarInsn(Opcodes.ALOAD, 1);
			init.visitVarInsn(Opcodes.ILOAD, 2);
			init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Enum", "<init>", "(Ljava/lang/String;I)V", false);
			init.visitInsn(Opcodes.RETURN);
			init.visitMaxs(0, 0);
			init.visitEnd();
			if (forge) {
				// The factory Forge's enum extender writes.
				MethodVisitor create = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create", "(Ljava/lang/String;Ljava/lang/String;)" + self, null, null);
				create.visitCode();
				create.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException");
				create.visitInsn(Opcodes.DUP);
				create.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "()V", false);
				create.visitInsn(Opcodes.ATHROW);
				create.visitMaxs(0, 0);
				create.visitEnd();
			}
			MethodVisitor clinit = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
			clinit.visitCode();
			clinit.visitTypeInsn(Opcodes.NEW, category);
			clinit.visitInsn(Opcodes.DUP);
			clinit.visitLdcInsn("MONSTER");
			clinit.visitInsn(Opcodes.ICONST_0);
			clinit.visitLdcInsn("monster");
			clinit.visitMethodInsn(Opcodes.INVOKESPECIAL, category, "<init>", constructor, false);
			clinit.visitFieldInsn(Opcodes.PUTSTATIC, category, "MONSTER", self);
			clinit.visitInsn(Opcodes.ICONST_1);
			clinit.visitTypeInsn(Opcodes.ANEWARRAY, category);
			clinit.visitInsn(Opcodes.DUP);
			clinit.visitInsn(Opcodes.ICONST_0);
			clinit.visitFieldInsn(Opcodes.GETSTATIC, category, "MONSTER", self);
			clinit.visitInsn(Opcodes.AASTORE);
			clinit.visitFieldInsn(Opcodes.PUTSTATIC, category, "$VALUES", array);
			clinit.visitInsn(Opcodes.ICONST_0);
			clinit.visitTypeInsn(Opcodes.ANEWARRAY, category);
			clinit.visitFieldInsn(Opcodes.PUTSTATIC, category, "CACHE", array);
			clinit.visitInsn(Opcodes.RETURN);
			clinit.visitMaxs(0, 0);
			clinit.visitEnd();
			cw.visitEnd();
			return cw.toByteArray();
		};
		String frozen = "com/example/mixin/MobCategoryMixin";
		// FrozenLib's shape, with a @Mutable shadow of `field`: newType(...) invokes the constructor, and
		// addCategories(CallbackInfo), injected after the class initializer sets $VALUES, appends to it.
		Function<String, byte[]> mixin = field -> {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, frozen, null, "java/lang/Object", null);
			AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
			AnnotationVisitor values = m.visitArray("value");
			values.visit(null, Type.getObjectType(category));
			values.visitEnd();
			m.visitEnd();
			FieldVisitor shadow = cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, field, array, null, null);
			shadow.visitAnnotation(SHADOW, true).visitEnd();
			shadow.visitAnnotation("Lorg/spongepowered/asm/mixin/Final;", true).visitEnd();
			shadow.visitAnnotation("Lorg/spongepowered/asm/mixin/Mutable;", true).visitEnd();
			shadow.visitEnd();
			MethodVisitor invoker = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "newType", constructor.replace(")V", ")" + self), null, null);
			invoker.visitAnnotation("Lorg/spongepowered/asm/mixin/gen/Invoker;", true).visit("value", "<init>");
			invoker.visitCode();
			invoker.visitTypeInsn(Opcodes.NEW, "java/lang/AssertionError");
			invoker.visitInsn(Opcodes.DUP);
			invoker.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/AssertionError", "<init>", "()V", false);
			invoker.visitInsn(Opcodes.ATHROW);
			invoker.visitMaxs(0, 0);
			invoker.visitEnd();
			MethodVisitor adds = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "addCategories",
					"(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null);
			AnnotationVisitor inject = adds.visitAnnotation(INJECT, true);
			AnnotationVisitor methods = inject.visitArray("method");
			methods.visit(null, "<clinit>");
			methods.visitEnd();
			AnnotationVisitor ats = inject.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", "FIELD");
			at.visit("opcode", Opcodes.PUTSTATIC);
			at.visit("target", self + "$VALUES:" + array);
			at.visitEnum("shift", "Lorg/spongepowered/asm/mixin/injection/At$Shift;", "AFTER");
			at.visitEnd();
			ats.visitEnd();
			inject.visitEnd();
			adds.visitCode();
			adds.visitFieldInsn(Opcodes.GETSTATIC, frozen, "$VALUES", array);
			adds.visitInsn(Opcodes.ICONST_2);
			adds.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Arrays", "copyOf", "([Ljava/lang/Object;I)[Ljava/lang/Object;", false);
			adds.visitTypeInsn(Opcodes.CHECKCAST, array);
			adds.visitInsn(Opcodes.DUP);
			adds.visitInsn(Opcodes.ICONST_1);
			adds.visitLdcInsn("FROZENLIB_GLARES");
			adds.visitInsn(Opcodes.ICONST_1);
			adds.visitLdcInsn("frozenlib:glares");
			adds.visitMethodInsn(Opcodes.INVOKESTATIC, frozen, "newType", constructor.replace(")V", ")" + self), false);
			adds.visitInsn(Opcodes.AASTORE);
			adds.visitFieldInsn(Opcodes.PUTSTATIC, frozen, "$VALUES", array);
			adds.visitInsn(Opcodes.RETURN);
			adds.visitMaxs(0, 0);
			adds.visitEnd();
			cw.visitEnd();
			return cw.toByteArray();
		};
		String accessor = "com/example/mixin/MobCategoryAccessor";
		ClassWriter aw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		aw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, accessor, null, "java/lang/Object", null);
		AnnotationVisitor am = aw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor av = am.visitArray("value");
		av.visit(null, Type.getObjectType(category));
		av.visitEnd();
		am.visitEnd();
		MethodVisitor setter = aw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "setValues", "(" + array + ")V", null, null);
		setter.visitAnnotation("Lorg/spongepowered/asm/mixin/gen/Accessor;", true).visit("value", "$VALUES");
		setter.visitAnnotation("Lorg/spongepowered/asm/mixin/Mutable;", true).visitEnd();
		setter.visitCode();
		setter.visitTypeInsn(Opcodes.NEW, "java/lang/UnsupportedOperationException");
		setter.visitInsn(Opcodes.DUP);
		setter.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/UnsupportedOperationException", "<init>", "()V", false);
		setter.visitInsn(Opcodes.ATHROW);
		setter.visitMaxs(0, 0);
		setter.visitEnd();
		aw.visitEnd();
		byte[] setterMixin = aw.toByteArray();

		MixinConfigFile config = MixinConfigFile.parse(config(true, "MobCategoryMixin", "MobCategoryAccessor"));
		BiFunction<Boolean, byte[], MixinPreflight> preflight = (forge, mod) -> new MixinPreflight(
				new ClassIndex(Map.of(category, target.apply(forge))::get), InstalledClasses.NONE,
				Map.of(frozen, mod, accessor, setterMixin)::get, ClassBytesSource.EMPTY, false, true, ClassBytesSource.EMPTY, "Forge");

		byte[] frozenLib = mixin.apply("$VALUES");
		ClassNode forgeTarget = new ClassNode();
		new ClassReader(target.apply(true)).accept(forgeTarget, 0);
		assertNull(valuesAsTheExtenderFindsThem(forgeTarget, node(frozenLib)), "the failure: Mixin made the array non-final");

		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight.apply(true, frozenLib).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		assertEquals(Set.of(frozen + ".class", accessor + ".class"), repaired.keySet(), notes.toString());
		assertEquals(2, notes.size(), notes.toString());
		for (PreflightFinding note : notes) {
			assertEquals(Severity.INFO, note.severity());
			assertTrue(note.message().contains("would make the values array of net.minecraft.world.entity.MobCategory writable")
					&& note.message().contains("(ENUM-8)"), note.message());
		}
		FieldNode shadow = node(repaired.get(frozen + ".class")).fields.getFirst();
		assertNotNull(Annotations.find(shadow, SHADOW), "still the enum's own array");
		assertNull(Annotations.find(shadow, "Lorg/spongepowered/asm/mixin/Mutable;"));
		assertNull(Annotations.find(shadow, "Lorg/spongepowered/asm/mixin/Final;"), "Mixin logs no write to a final field");
		MethodNode set = node(repaired.get(accessor + ".class")).methods.getFirst();
		assertNotNull(Annotations.find(set, "Lorg/spongepowered/asm/mixin/gen/Accessor;"));
		assertNull(Annotations.find(set, "Lorg/spongepowered/asm/mixin/Mutable;"));
		ClassNode extended = new ClassNode();
		new ClassReader(target.apply(true)).accept(extended, 0);
		FieldNode found = valuesAsTheExtenderFindsThem(extended, node(repaired.get(frozen + ".class")));
		assertNotNull(found, "Mixin leaves the array final, for the extender");
		assertEquals("$VALUES", found.name);
		assertTrue(preflight.apply(true, repaired.get(frozen + ".class")).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());

		// NeoForge's enum, or a mutable shadow of another array: left as it is.
		List<PreflightFinding> none = new ArrayList<>();
		assertTrue(preflight.apply(false, frozenLib).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), none).isEmpty(), none.toString());
		Map<String, byte[]> cache = preflight.apply(true, mixin.apply("CACHE")).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), none);
		assertEquals(Set.of(accessor + ".class"), cache.keySet(), none.toString());
	}

	private static ClassNode node(byte[] bytes) {
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, 0);
		return node;
	}

	/**
	 * The field of {@code target} Forge's enum extender takes for its values array once Mixin has applied {@code mixin}'s
	 * shadows (made non-final where {@code @Mutable}, {@code MixinApplicatorStandard}): the first of the enum's array type
	 * that is private, static, final and synthetic ({@code RuntimeEnumExtender.processClassWithFlags}); null where the
	 * extender stops the class loading.
	 */
	private static FieldNode valuesAsTheExtenderFindsThem(ClassNode target, ClassNode mixin) {
		for (FieldNode shadow : mixin.fields) {
			if (Annotations.find(shadow, "Lorg/spongepowered/asm/mixin/Mutable;") == null) continue;
			target.fields.stream().filter(f -> f.name.equals(shadow.name) && f.desc.equals(shadow.desc)).forEach(f -> f.access &= ~Opcodes.ACC_FINAL);
		}
		int flags = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC;
		return target.fields.stream().filter(f -> f.desc.equals("[L" + target.name + ";") && (f.access & flags) == flags).findFirst().orElse(null);
	}

	/**
	 * CORPUS-95: Fabric API's screen mixin and SpruceUI's both add {@code @Unique private Screen tickingScreen} to
	 * {@code Minecraft}. Fabric's Mixin renames the later one, as an earlier mixin adds the name; Forge's (SpongePowered's)
	 * prepares both before it adds either field, renames neither, and the two mixins shared one field: SpruceUI's hook
	 * cleared it before Fabric API's read it, and the Forge 26.1.2 corpus client stopped. Where the running Mixin doesn't
	 * see other mixins' fields, the mixin's field is renamed, with its code's uses and those of the nested class Mixin
	 * copies with it; a field no other mixin adds is left as it is. Nothing changes on Fabric's Mixin (NeoForge), where
	 * only the mod's own mixin adds the field, where the target declares it (Mixin renames it then), for a public unique
	 * field (Mixin discards it on either, as on Fabric), or for one whose {@code @Unique} Mixin doesn't read (not visible).
	 */
	@Test
	void aUniqueFieldAnotherModsMixinAddsTooIsRenamedWhereMixinSeesOnlyTheTargetsOwnFields() throws Exception {
		String target = "net/minecraft/client/Minecraft";
		String screen = "Lnet/minecraft/client/gui/screens/Screen;";
		Function<Boolean, byte[]> minecraft = declares -> {
			ClassWriter tw = new ClassWriter(0);
			tw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, target, null, "java/lang/Object", null);
			tw.visitField(Opcodes.ACC_PUBLIC, "screen", screen, null, null).visitEnd();
			if (declares) tw.visitField(Opcodes.ACC_PRIVATE, "tickingScreen", screen, null, null).visitEnd();
			tw.visitEnd();
			return tw.toByteArray();
		};
		// A mixin into Minecraft adding tickingScreen (with the access and @Unique given); SpruceUI's also adds a field of
		// its own, uses both, and has an anonymous class reading tickingScreen and a nested class that doesn't.
		String fabricApi = "net/fabricmc/fabric/mixin/screen/MinecraftMixin";
		String spruce = "dev/lambdaurora/spruceui/mixin/MinecraftClientMixin";
		String anonymous = spruce + "$1";
		String holder = spruce + "$Holder";
		BiFunction<String, Integer, byte[]> mixin = (name, unique) -> {
			ClassWriter cw = new ClassWriter(0);
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
			AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
			AnnotationVisitor values = m.visitArray("value");
			values.visit(null, Type.getObjectType(target));
			values.visitEnd();
			m.visitEnd();
			boolean visible = unique >= 0;
			FieldVisitor ticking = cw.visitField(visible ? unique : Opcodes.ACC_PRIVATE, "tickingScreen", screen, null, null);
			ticking.visitAnnotation("Lorg/spongepowered/asm/mixin/Unique;", visible).visitEnd();
			ticking.visitEnd();
			if (!name.equals(spruce)) {
				cw.visitEnd();
				return cw.toByteArray();
			}
			FieldVisitor own = cw.visitField(Opcodes.ACC_PRIVATE, "spruceui$frames", "I", null, null);
			own.visitAnnotation("Lorg/spongepowered/asm/mixin/Unique;", true).visitEnd();
			own.visitEnd();
			cw.visitInnerClass(anonymous, null, null, 0);
			cw.visitInnerClass(holder, spruce, "Holder", Opcodes.ACC_STATIC);
			MethodVisitor after = cw.visitMethod(Opcodes.ACC_PRIVATE, "spruceui$onAfterLoadingScreenTick", "()V", null, null);
			after.visitCode();
			after.visitVarInsn(Opcodes.ALOAD, 0);
			after.visitFieldInsn(Opcodes.GETFIELD, spruce, "tickingScreen", screen);
			after.visitInsn(Opcodes.POP);
			after.visitVarInsn(Opcodes.ALOAD, 0);
			after.visitInsn(Opcodes.ACONST_NULL);
			after.visitFieldInsn(Opcodes.PUTFIELD, spruce, "tickingScreen", screen);
			after.visitVarInsn(Opcodes.ALOAD, 0);
			after.visitInsn(Opcodes.ICONST_1);
			after.visitFieldInsn(Opcodes.PUTFIELD, spruce, "spruceui$frames", "I");
			after.visitInsn(Opcodes.RETURN);
			after.visitMaxs(2, 1);
			after.visitEnd();
			cw.visitEnd();
			return cw.toByteArray();
		};
		ClassWriter aw = new ClassWriter(0);
		aw.visit(Opcodes.V21, Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, anonymous, null, "java/lang/Object", null);
		aw.visitInnerClass(anonymous, null, null, 0);
		aw.visitField(Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, "this$0", "L" + spruce + ";", null, null).visitEnd();
		MethodVisitor read = aw.visitMethod(Opcodes.ACC_PUBLIC, "get", "()" + screen, null, null);
		read.visitCode();
		read.visitVarInsn(Opcodes.ALOAD, 0);
		read.visitFieldInsn(Opcodes.GETFIELD, anonymous, "this$0", "L" + spruce + ";");
		read.visitFieldInsn(Opcodes.GETFIELD, spruce, "tickingScreen", screen);
		read.visitInsn(Opcodes.ARETURN);
		read.visitMaxs(1, 1);
		read.visitEnd();
		aw.visitEnd();
		ClassWriter hw = new ClassWriter(0);
		hw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, holder, null, "java/lang/Object", null);
		hw.visitInnerClass(holder, spruce, "Holder", Opcodes.ACC_STATIC);
		hw.visitEnd();
		Map<String, byte[]> nested = Map.of(anonymous, aw.toByteArray(), holder, hw.toByteArray());
		MixinConfigFile config = MixinConfigFile.parse("{\"required\":true,\"package\":\"dev.lambdaurora.spruceui.mixin\",\"mixins\":[\"MinecraftClientMixin\"]}");
		// The installed jars: SpruceUI's own, and Fabric API's with its mixin of the given access.
		Function<Integer, InstalledClasses> installed = fabricAccess -> {
			List<InstalledClasses.Jar> jars = new ArrayList<>();
			byte[] own = mixin.apply(spruce, Opcodes.ACC_PRIVATE);
			jars.add(new InstalledClasses.Jar("SpruceUI", spruce::equals, name -> name.equals(spruce) ? own : null, () -> List.of(spruce), true));
			if (fabricAccess != null) {
				byte[] theirs = mixin.apply(fabricApi, fabricAccess);
				jars.add(new InstalledClasses.Jar("Fabric API", fabricApi::equals, name -> name.equals(fabricApi) ? theirs : null, () -> List.of(fabricApi)));
			}
			return new InstalledClasses(jars);
		};
		record Run(boolean declares, Integer fabricAccess, byte[] mod, boolean seesMixinFields) {
		}
		Function<Run, MixinPreflight> preflight = run -> {
			Map<String, byte[]> classes = new HashMap<>(nested);
			classes.put(spruce, run.mod());
			return new MixinPreflight(new ClassIndex(Map.of(target, minecraft.apply(run.declares()))::get), installed.apply(run.fabricAccess()),
					classes::get, ClassBytesSource.EMPTY, true, true, ClassBytesSource.EMPTY, "Forge", true,
					MixinPreflight.ConstructorInjects.UNSAFE_POINTS, false, false, false, true, run.seesMixinFields());
		};

		// Forge: renamed, in the mixin's code and its anonymous class's; its own field and the nested class left alone.
		byte[] spruceMixin = mixin.apply(spruce, Opcodes.ACC_PRIVATE);
		String renamed = "fabricated$dev_lambdaurora_spruceui_mixin_MinecraftClientMixin$tickingScreen";
		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight.apply(new Run(false, Opcodes.ACC_PRIVATE, spruceMixin, false))
				.repairs("spruceui.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		assertEquals(Set.of(spruce + ".class", anonymous + ".class"), repaired.keySet(), notes.toString());
		assertEquals(1, notes.size(), notes.toString());
		assertEquals(Severity.INFO, notes.getFirst().severity());
		assertTrue(notes.getFirst().message().contains("@Unique field tickingScreen is also one Fabric API's mixin " + fabricApi.replace('/', '.')
				+ " adds to " + target.replace('/', '.')), notes.getFirst().message());
		assertTrue(notes.getFirst().message().contains("renamed " + renamed), notes.getFirst().message());
		assertTrue(notes.getFirst().message().contains("(CORPUS-95)"), notes.getFirst().message());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get(spruce + ".class")).accept(node, 0);
		assertEquals(List.of(renamed, "spruceui$frames"), node.fields.stream().map(f -> f.name).toList());
		assertNotNull(Annotations.find(node.fields.getFirst(), "Lorg/spongepowered/asm/mixin/Unique;"), "still unique");
		assertEquals(List.of(renamed, renamed, "spruceui$frames"), java.util.Arrays.stream(node.methods.getFirst().instructions.toArray())
				.filter(i -> i instanceof FieldInsnNode).map(i -> ((FieldInsnNode) i).name).toList());
		ClassNode copied = new ClassNode();
		new ClassReader(repaired.get(anonymous + ".class")).accept(copied, 0);
		assertEquals(List.of("this$0", renamed), java.util.Arrays.stream(copied.methods.getFirst().instructions.toArray())
				.filter(i -> i instanceof FieldInsnNode).map(i -> ((FieldInsnNode) i).name).toList());
		assertTrue(preflight.apply(new Run(false, Opcodes.ACC_PRIVATE, repaired.get(spruce + ".class"), false))
				.check("spruceui.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());

		// Fabric's Mixin; only the mod's own mixin adding it; the target declaring it; a public unique field; a @Unique
		// Mixin doesn't read: left as it is, and nothing to judge.
		List<Run> unchanged = List.of(new Run(false, Opcodes.ACC_PRIVATE, spruceMixin, true), new Run(false, null, spruceMixin, false),
				new Run(true, Opcodes.ACC_PRIVATE, spruceMixin, false), new Run(false, Opcodes.ACC_PRIVATE, mixin.apply(spruce, Opcodes.ACC_PUBLIC), false),
				new Run(false, Opcodes.ACC_PRIVATE, mixin.apply(spruce, -1), false));
		for (Run run : unchanged) {
			List<PreflightFinding> none = new ArrayList<>();
			assertTrue(preflight.apply(run).repairs("spruceui.mixins.json", config, Refmap.EMPTY, Set.of(), none).isEmpty(), run + ": " + none);
			assertTrue(preflight.apply(run).check("spruceui.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty(), run.toString());
		}
	}

	/**
	 * An accessor named like a method its target already has replaces that method as Mixin merges it, as on Fabric
	 * Loader, where a Fabric mod's own class is the same (Iris's accessor of Sodium's {@code enumClass}, named like
	 * Sodium's {@code getEnumClass}). Not where the config requires {@code @Overwrite} on a method that replaces one
	 * (Mixin throws then, there too), nor in a class that isn't a Fabric mod's own (a NeoForge mod's).
	 */
	@Test
	void anAccessorNamedLikeAFabricModsOwnMethodReplacesItAsOnFabric() throws Exception {
		String builder = "net/example/sodium/EnumOptionBuilderImpl";
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, builder, null, "java/lang/Object", null);
		cw.visitField(Opcodes.ACC_PRIVATE, "enumClass", "Ljava/lang/Class;", null, null).visitEnd();
		MethodVisitor existing = cw.visitMethod(0, "getEnumClass", "()Ljava/lang/Class;", null, null);
		existing.visitCode();
		existing.visitInsn(Opcodes.ACONST_NULL);
		existing.visitInsn(Opcodes.ARETURN);
		existing.visitMaxs(1, 1);
		existing.visitEnd();
		cw.visitEnd();
		byte[] builderClass = cw.toByteArray();
		ClassWriter mw = new ClassWriter(0);
		mw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE, "com/example/mixin/BuilderAccessor", null,
				"java/lang/Object", null);
		AnnotationVisitor m = mw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor targets = m.visitArray("value");
		targets.visit(null, Type.getObjectType(builder));
		targets.visitEnd();
		m.visitEnd();
		MethodVisitor getter = mw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "getEnumClass", "()Ljava/lang/Class;", null, null);
		AnnotationVisitor accessor = getter.visitAnnotation("Lorg/spongepowered/asm/mixin/gen/Accessor;", true);
		accessor.visit("value", "enumClass");
		accessor.visitEnd();
		getter.visitEnd();
		mw.visitEnd();
		Map<String, byte[]> mod = Map.of("com/example/mixin/BuilderAccessor", mw.toByteArray());
		String lenient = config(true, "BuilderAccessor");
		String strict = "{\"required\":true,\"package\":\"com.example.mixin\",\"mixins\":[\"BuilderAccessor\"],\"overwrites\":{\"requireAnnotations\":true}}";
		java.util.function.BiFunction<Boolean, String, List<PreflightFinding>> run = (fabric, config) -> {
			InstalledClasses installed = new InstalledClasses(List.of(new InstalledClasses.Jar("Sodium", builder::equals,
					name -> name.equals(builder) ? builderClass : null, List::of, fabric)));
			try {
				return new MixinPreflight(new ClassIndex(installed), installed, mod::get, ClassBytesSource.EMPTY, true, true, ClassBytesSource.EMPTY)
						.check("test.mixins.json", MixinConfigFile.parse(config), Refmap.EMPTY, Set.of());
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		};

		List<PreflightFinding> replaced = run.apply(true, lenient);
		assertEquals(1, replaced.size(), replaced.toString());
		assertEquals(Severity.INFO, replaced.getFirst().severity());
		assertTrue(replaced.getFirst().message().contains("replaces Sodium's own method of that name"), replaced.getFirst().message());

		List<PreflightFinding> required = run.apply(true, strict);
		assertTrue(required.stream().anyMatch(f -> f.severity() != Severity.INFO && f.message().contains("already exists")), required.toString());

		List<PreflightFinding> neoForgeMods = run.apply(false, lenient);
		assertTrue(neoForgeMods.stream().anyMatch(f -> f.severity() != Severity.INFO && f.message().contains("already exists")), neoForgeMods.toString());
	}

	/**
	 * Polydium's font texture mixin keeps two nodes in {@code @Unique} fields typed with the mixin class, and its
	 * {@code @WrapOperation} handler takes two of them: the Mixin NeoForge runs leaves those descriptors as they are (the
	 * mixin has no inner classes, its target no mixin superclass), and the handler failed its check, stopping the game.
	 * They name the target, as Fabric's Mixin does since 0.17.1, with the code's references to them (SELF-1); an
	 * accessor interface, a mixin with several targets, and an enum mixin (whose constants Mixin's enum extensions know by
	 * the mixin's type, SELF-2) are left as they are.
	 */
	@Test
	void descriptorsNamingTheMixinClassNameItsTarget() throws Exception {
		String self = "com/example/mixin/Node";
		String selfDesc = "L" + self + ";";
		byte[] node = mixin(self, false, cw -> {
			FieldVisitor left = cw.visitField(Opcodes.ACC_PRIVATE, "left", selfDesc, null, null);
			left.visitAnnotation("Lorg/spongepowered/asm/mixin/Unique;", false).visitEnd();
			left.visitEnd();
			MethodVisitor track = cw.visitMethod(Opcodes.ACC_PRIVATE, "trackLeft", "(" + selfDesc + selfDesc + ")V", null, null);
			track.visitCode();
			track.visitVarInsn(Opcodes.ALOAD, 0);
			track.visitVarInsn(Opcodes.ALOAD, 1);
			track.visitFieldInsn(Opcodes.PUTFIELD, self, "left", selfDesc);
			track.visitVarInsn(Opcodes.ALOAD, 0);
			track.visitInvokeDynamicInsn("get", "(" + selfDesc + ")Ljava/util/function/Supplier;",
					new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
							"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;"
									+ "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false),
					Type.getType("()Ljava/lang/Object;"), new Handle(Opcodes.H_INVOKEVIRTUAL, self, "lambda$trackLeft$0", "()" + selfDesc, false),
					Type.getType("()" + selfDesc));
			track.visitInsn(Opcodes.POP);
			track.visitLdcInsn(Type.getType("[" + selfDesc));
			track.visitInsn(Opcodes.POP);
			track.visitInsn(Opcodes.RETURN);
			track.visitLocalVariable("node", selfDesc, null, new Label(), new Label(), 1);
			track.visitMaxs(2, 3);
			track.visitEnd();
			MethodVisitor lambda = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC, "lambda$trackLeft$0", "()" + selfDesc, null, null);
			lambda.visitCode();
			lambda.visitVarInsn(Opcodes.ALOAD, 0);
			lambda.visitFieldInsn(Opcodes.GETFIELD, self, "left", selfDesc);
			lambda.visitInsn(Opcodes.ARETURN);
			lambda.visitMaxs(1, 1);
			lambda.visitEnd();
		});
		// Several targets: Mixin names each where it merges the mixin into it.
		ClassWriter both = new ClassWriter(0);
		both.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "com/example/mixin/Both", null, "java/lang/Object", null);
		AnnotationVisitor m = both.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor values = m.visitArray("value");
		values.visit(null, Type.getObjectType(TARGET));
		values.visit(null, Type.getObjectType(THING));
		values.visitEnd();
		m.visitEnd();
		both.visitField(Opcodes.ACC_PRIVATE, "other", "Lcom/example/mixin/Both;", null, null).visitEnd();
		both.visitEnd();
		// An accessor interface: Mixin generates its methods in the target, or rewrites them in place.
		ClassWriter get = new ClassWriter(0);
		get.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE, "com/example/mixin/Get", null, "java/lang/Object", null);
		AnnotationVisitor getMixin = get.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor getTargets = getMixin.visitArray("value");
		getTargets.visit(null, Type.getObjectType(TARGET));
		getTargets.visitEnd();
		getMixin.visitEnd();
		MethodVisitor getter = get.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "next", "()Lcom/example/mixin/Get;", null, null);
		getter.visitAnnotation("Lorg/spongepowered/asm/mixin/gen/Accessor;", true).visitEnd();
		getter.visitEnd();
		get.visitEnd();
		// An enum mixin adding a constant (Flashback's texture format one): Mixin's enum extensions know the constant,
		// values() and valueOf by the mixin's own type.
		String kind = "com/example/mixin/Kind";
		ClassWriter enumMixin = new ClassWriter(0);
		enumMixin.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_ENUM, kind,
				"Ljava/lang/Enum<L" + kind + ";>;", "java/lang/Enum", null);
		AnnotationVisitor enumAnn = enumMixin.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor enumTargets = enumAnn.visitArray("value");
		enumTargets.visit(null, Type.getObjectType(TARGET));
		enumTargets.visitEnd();
		enumAnn.visitEnd();
		enumMixin.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM, "EXTRA", "L" + kind + ";", null, null).visitEnd();
		enumMixin.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, "$VALUES", "[L" + kind + ";", null, null).visitEnd();
		MethodVisitor enumValues = enumMixin.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "values", "()[L" + kind + ";", null, null);
		enumValues.visitCode();
		enumValues.visitFieldInsn(Opcodes.GETSTATIC, kind, "$VALUES", "[L" + kind + ";");
		enumValues.visitInsn(Opcodes.ARETURN);
		enumValues.visitMaxs(1, 0);
		enumValues.visitEnd();
		enumMixin.visitEnd();
		Map<String, byte[]> mod = Map.of(self, node, "com/example/mixin/Both", both.toByteArray(), "com/example/mixin/Get", get.toByteArray(),
				kind, enumMixin.toByteArray());
		ClassIndex game = new ClassIndex(Map.of(TARGET, targetClass())::get);
		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight(game, mod::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", MixinConfigFile.parse(config(true, "Node", "Both", "Get", "Kind")), Refmap.EMPTY, Set.of(), notes);

		assertEquals(Set.of(self + ".class"), repaired.keySet(), notes.toString());
		assertTrue(notes.stream().anyMatch(f -> f.severity() == Severity.INFO && f.message().startsWith("9 descriptor(s) of the mixin name its own class")
				&& f.message().contains("they name net.minecraft.world.Thing")), notes.toString());
		ClassNode out = new ClassNode();
		new ClassReader(repaired.get(self + ".class")).accept(out, 0);
		String real = "L" + TARGET + ";";
		assertEquals(self, out.name, "the mixin keeps its own name");
		assertEquals(real, out.fields.getFirst().desc);
		MethodNode track = out.methods.stream().filter(mn -> mn.name.equals("trackLeft")).findFirst().orElseThrow();
		assertEquals("(" + real + real + ")V", track.desc);
		assertEquals(real, track.localVariables.getFirst().desc);
		FieldInsnNode put = (FieldInsnNode) java.util.Arrays.stream(track.instructions.toArray()).filter(i -> i instanceof FieldInsnNode).findFirst().orElseThrow();
		assertEquals(self, put.owner, "owners are left to Mixin");
		assertEquals(real, put.desc);
		InvokeDynamicInsnNode indy = (InvokeDynamicInsnNode) java.util.Arrays.stream(track.instructions.toArray())
				.filter(i -> i instanceof InvokeDynamicInsnNode).findFirst().orElseThrow();
		assertEquals("(" + real + ")Ljava/util/function/Supplier;", indy.desc);
		assertEquals("()" + real, ((Handle) indy.bsmArgs[1]).getDesc());
		assertEquals(self, ((Handle) indy.bsmArgs[1]).getOwner());
		assertEquals("()" + real, ((Type) indy.bsmArgs[2]).getDescriptor());
		LdcInsnNode ldc = (LdcInsnNode) java.util.Arrays.stream(track.instructions.toArray()).filter(i -> i instanceof LdcInsnNode).findFirst().orElseThrow();
		assertEquals("[" + real, ((Type) ldc.cst).getDescriptor());
		assertEquals("()" + real, out.methods.stream().filter(mn -> mn.name.startsWith("lambda$")).findFirst().orElseThrow().desc);
	}

	/**
	 * Fabric API's moving block renderer mixin has an anonymous class calling a protected method of its outer class's
	 * superclass, through the static accessor javac adds to the mixin, whose descriptor names the mixin. Mixin copies the
	 * anonymous class into the target and renames the accessor as it merges it; the copy finds the new name by the
	 * descriptor it calls the accessor with, matched against the mixin's as Mixin read it. Named with the target first,
	 * the accessor kept its old name in the copy, which failed to link on Forge 66 (SELF-3). Such a mixin is left to Mixin,
	 * which names the target there itself; a mixin whose only inner class is a static synthetic one (javac's switch table,
	 * which Mixin loads as it is) is still renamed.
	 */
	@Test
	void aMixinWhoseInnerClassesMixinCopiesKeepsItsOwnType() throws Exception {
		String outer = "com/example/mixin/Moving";
		String anonymous = outer + "$1";
		byte[] moving = mixin(outer, false, cw -> {
			cw.visitInnerClass(anonymous, null, null, 0);
			MethodVisitor accessor = cw.visitMethod(Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, "access$100", "(L" + outer + ";I)V", null, null);
			accessor.visitCode();
			accessor.visitInsn(Opcodes.RETURN);
			accessor.visitMaxs(0, 2);
			accessor.visitEnd();
		});
		ClassWriter consumer = new ClassWriter(0);
		consumer.visit(Opcodes.V21, Opcodes.ACC_SUPER, anonymous, null, "java/lang/Object", null);
		consumer.visitOuterClass(outer, "build", "()V");
		consumer.visitInnerClass(anonymous, null, null, 0);
		consumer.visitField(Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, "this$0", "L" + outer + ";", null, null).visitEnd();
		consumer.visitEnd();
		String switching = "com/example/mixin/Switching";
		String table = switching + "$1";
		byte[] switchingMixin = mixin(switching, false, cw -> {
			cw.visitInnerClass(table, null, null, Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC);
			cw.visitField(Opcodes.ACC_PRIVATE, "next", "L" + switching + ";", null, null).visitEnd();
		});
		ClassWriter switchTable = new ClassWriter(0);
		switchTable.visit(Opcodes.V21, Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC, table, null, "java/lang/Object", null);
		switchTable.visitInnerClass(table, null, null, Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC);
		switchTable.visitField(Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, "$SwitchMap$x", "[I", null, null).visitEnd();
		switchTable.visitEnd();
		Map<String, byte[]> mod = Map.of(outer, moving, anonymous, consumer.toByteArray(), switching, switchingMixin, table, switchTable.toByteArray());
		ClassIndex game = new ClassIndex(Map.of(TARGET, targetClass())::get);
		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight(game, mod::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", MixinConfigFile.parse(config(true, "Moving", "Switching")), Refmap.EMPTY, Set.of(), notes);

		assertEquals(Set.of(switching + ".class"), repaired.keySet(), notes.toString());
		assertTrue(notes.stream().noneMatch(f -> f.mixin().endsWith("Moving") && f.message().contains("descriptor(s) of the mixin")), notes.toString());
		ClassNode out = new ClassNode();
		new ClassReader(repaired.get(switching + ".class")).accept(out, 0);
		assertEquals("L" + TARGET + ";", out.fields.getFirst().desc);
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
	 * Server Translations' mixin into vanilla's anonymous {@code EncoderCache$2} shadows its captured {@code val$codec},
	 * which NeoForge's 1.21.11 build names after the decompiled variable. The class implements a library interface pre-flight
	 * has no bytes of, so looking the field up through its supertypes couldn't tell it was missing, and the mixin stopped
	 * the server; Mixin only looks in the target class. The shadow is pointed at the one captured field of its type.
	 */
	@Test
	void aShadowOfACapturedVariableFieldFindsItUnderTheNameThisBuildGivesIt() throws Exception {
		String anonymous = "net/minecraft/util/EncoderCache$2";
		ClassWriter target = new ClassWriter(0);
		target.visit(Opcodes.V21, Opcodes.ACC_FINAL, anonymous, null, "java/lang/Object", new String[] {"com/mojang/serialization/Codec"});
		target.visitField(Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, "val$p_330_", "Lcom/mojang/serialization/Codec;", null, null).visitEnd();
		target.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(anonymous, target.toByteArray())::get);

		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/mixin/Cache", null, "java/lang/Object", null);
		AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor names = m.visitArray("targets");
		names.visit(null, anonymous);
		names.visitEnd();
		m.visitEnd();
		FieldVisitor shadow = cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "val$codec", "Lcom/mojang/serialization/Codec;", null, null);
		shadow.visitAnnotation("Lorg/spongepowered/asm/mixin/Shadow;", false).visitEnd();
		shadow.visitEnd();
		MethodVisitor uses = cw.visitMethod(Opcodes.ACC_PRIVATE, "codec", "()Ljava/lang/Object;", null, null);
		uses.visitCode();
		uses.visitVarInsn(Opcodes.ALOAD, 0);
		uses.visitFieldInsn(Opcodes.GETFIELD, "com/example/mixin/Cache", "val$codec", "Lcom/mojang/serialization/Codec;");
		uses.visitInsn(Opcodes.ARETURN);
		uses.visitMaxs(1, 1);
		uses.visitEnd();
		cw.visitEnd();
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Cache", cw.toByteArray()));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Cache"));

		List<PreflightFinding> before = preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertEquals(1, before.size(), before.toString());
		assertTrue(before.getFirst().message().contains("@Shadow field val$codec"), before.toString());

		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = preflight(game, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes)
				.get("com/example/mixin/Cache.class");
		assertNotNull(repaired, notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired).accept(node, 0);
		assertEquals("val$p_330_", node.fields.getFirst().name);
		org.objectweb.asm.tree.FieldInsnNode read = (org.objectweb.asm.tree.FieldInsnNode) node.methods.getFirst().instructions.get(1);
		assertEquals("val$p_330_", read.name, "the mixin's own uses follow");
		mod.put("com/example/mixin/Cache", repaired);
		assertTrue(preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());
	}

	@Test
	void aCallNeoForgeMovedIntoALambdaOfTheMethodIsFollowedThere() throws Exception {
		// LAMBDA-2: NeoForge's 1.21.11 build runs RegistryDataLoader.loadElementFromResource's register call from an
		// Optional callback; FrozenLib's static @WrapOperation of it gets the lambda added to its methods.
		ClassWriter target = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		target.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor load = target.visitMethod(Opcodes.ACC_PUBLIC, "load", "()V", null, null);
		load.visitCode();
		load.visitInvokeDynamicInsn("run", "()Ljava/lang/Runnable;",
				new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
						"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;"
								+ "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false),
				Type.getType("()V"), new Handle(Opcodes.H_INVOKESTATIC, TARGET, "lambda$load$0", "()V", false), Type.getType("()V"));
		load.visitInsn(Opcodes.POP);
		load.visitInsn(Opcodes.RETURN);
		load.visitMaxs(0, 0);
		load.visitEnd();
		MethodVisitor lambda = target.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, "lambda$load$0", "()V", null, null);
		lambda.visitCode();
		lambda.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET, "register", "()V", false);
		lambda.visitInsn(Opcodes.RETURN);
		lambda.visitMaxs(0, 0);
		lambda.visitEnd();
		target.visitMethod(Opcodes.ACC_STATIC, "register", "()V", null, null).visitEnd();
		target.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(TARGET, target.toByteArray())::get);
		Map<String, byte[]> mod = new HashMap<>();
		for (boolean isStatic : new boolean[] {true, false}) {
			mod.put("com/example/mixin/Wrap", mixin("com/example/mixin/Wrap", false, cw -> {
				MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | (isStatic ? Opcodes.ACC_STATIC : 0), "wrap",
						"(" + OPERATION + ")V", null, null);
				AnnotationVisitor ann = mv.visitAnnotation(WRAP_OPERATION, false);
				AnnotationVisitor methods = ann.visitArray("method");
				methods.visit(null, "load");
				methods.visitEnd();
				AnnotationVisitor ats = ann.visitArray("at");
				AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
				at.visit("value", "INVOKE");
				at.visit("target", "L" + TARGET + ";register()V");
				at.visitEnd();
				ats.visitEnd();
				ann.visitEnd();
				mv.visitEnd();
			}));
			MixinConfigFile config = MixinConfigFile.parse(config(true, "Wrap"));
			List<PreflightFinding> notes = new ArrayList<>();
			byte[] repaired = preflight(game, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes)
					.get("com/example/mixin/Wrap.class");
			if (!isStatic) {
				assertNull(repaired, "an instance handler can't go into a static lambda: " + notes);
				continue;
			}
			assertNotNull(repaired, notes.toString());
			assertTrue(notes.toString().contains("its lambda lambda$load$0"), notes.toString());
			mod.put("com/example/mixin/Wrap", repaired);
			List<PreflightFinding> after = preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
			assertTrue(after.isEmpty(), after.toString());
		}
	}

	private static final String REGISTER = "register(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;";
	private static final String REGISTER_WITH_ID = "(ILjava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;";

	/**
	 * {@link #TARGET} as NeoForge's {@code MappedRegistry}: {@code register(key, value)} only calls
	 * {@code register(this.size, key, value)}, which holds the code, with {@code int newId = id} and a call to
	 * {@code lookup(key)}.
	 */
	private static byte[] insertedIdTarget() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		cw.visitField(Opcodes.ACC_PRIVATE, "size", "I", null, null).visitEnd();
		MethodVisitor kept = cw.visitMethod(Opcodes.ACC_PUBLIC, "register", "(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;", null, null);
		org.objectweb.asm.Label keptStart = new org.objectweb.asm.Label();
		org.objectweb.asm.Label keptEnd = new org.objectweb.asm.Label();
		kept.visitCode();
		kept.visitLabel(keptStart);
		kept.visitVarInsn(Opcodes.ALOAD, 0);
		kept.visitVarInsn(Opcodes.ALOAD, 0);
		kept.visitFieldInsn(Opcodes.GETFIELD, TARGET, "size", "I");
		kept.visitVarInsn(Opcodes.ALOAD, 1);
		kept.visitVarInsn(Opcodes.ALOAD, 2);
		kept.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "register", REGISTER_WITH_ID, false);
		kept.visitInsn(Opcodes.ARETURN);
		kept.visitLabel(keptEnd);
		kept.visitLocalVariable("this", "L" + TARGET + ";", null, keptStart, keptEnd, 0);
		kept.visitLocalVariable("key", "Ljava/lang/String;", null, keptStart, keptEnd, 1);
		kept.visitLocalVariable("value", "Ljava/lang/Object;", null, keptStart, keptEnd, 2);
		kept.visitMaxs(0, 0);
		kept.visitEnd();
		MethodVisitor code = cw.visitMethod(Opcodes.ACC_PUBLIC, "register", REGISTER_WITH_ID, null, null);
		org.objectweb.asm.Label start = new org.objectweb.asm.Label();
		org.objectweb.asm.Label stored = new org.objectweb.asm.Label();
		org.objectweb.asm.Label end = new org.objectweb.asm.Label();
		code.visitCode();
		code.visitLabel(start);
		code.visitVarInsn(Opcodes.ILOAD, 1);
		code.visitVarInsn(Opcodes.ISTORE, 4);
		code.visitLabel(stored);
		code.visitVarInsn(Opcodes.ALOAD, 0);
		code.visitVarInsn(Opcodes.ALOAD, 2);
		code.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "lookup", "(Ljava/lang/String;)Ljava/lang/Object;", false);
		code.visitInsn(Opcodes.POP);
		code.visitVarInsn(Opcodes.ALOAD, 3);
		code.visitInsn(Opcodes.ARETURN);
		code.visitLabel(end);
		code.visitLocalVariable("this", "L" + TARGET + ";", null, start, end, 0);
		code.visitLocalVariable("id", "I", null, start, end, 1);
		code.visitLocalVariable("key", "Ljava/lang/String;", null, start, end, 2);
		code.visitLocalVariable("value", "Ljava/lang/Object;", null, start, end, 3);
		code.visitLocalVariable("newId", "I", null, stored, end, 4);
		code.visitMaxs(0, 0);
		code.visitEnd();
		MethodVisitor lookup = cw.visitMethod(Opcodes.ACC_PUBLIC, "lookup", "(Ljava/lang/String;)Ljava/lang/Object;", null, null);
		lookup.visitCode();
		lookup.visitInsn(Opcodes.ACONST_NULL);
		lookup.visitInsn(Opcodes.ARETURN);
		lookup.visitMaxs(0, 0);
		lookup.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * FrozenLib's registry mixin, against NeoForge's {@code MappedRegistry} whose {@code register} now only calls an
	 * overload with an id: an {@code @Inject} at its return taking its parameters and capturing the new entry's id
	 * ({@code @Local int}, by type or by name), and a {@code @ModifyExpressionValue} at a call the overload now makes,
	 * also taking the parameters. Both move to the overload through bridges taking its parameters; the id is the
	 * overload's local (by its ordinal, as the inserted parameter has the same type).
	 */
	@Test
	void injectorsOnAMethodNeoForgeReducedToACallWithAnInsertedParameterMoveThroughBridges() throws Exception {
		ClassIndex game = new ClassIndex(Map.of(TARGET, insertedIdTarget())::get);
		for (String byName : java.util.Arrays.asList(null, "newId")) {
			ClassWriter cw = new ClassWriter(0);
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "com/example/mixin/Reg", null, "java/lang/Object", null);
			AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
			AnnotationVisitor values = m.visitArray("value");
			values.visit(null, Type.getObjectType(TARGET));
			values.visitEnd();
			m.visitEnd();
			MethodVisitor added = cw.visitMethod(Opcodes.ACC_PRIVATE, "added",
					"(Ljava/lang/String;Ljava/lang/Object;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;I)V", null, null);
			AnnotationVisitor inject = added.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = inject.visitArray("method");
			methods.visit(null, REGISTER);
			methods.visitEnd();
			AnnotationVisitor ats = inject.visitArray("at");
			AnnotationVisitor atReturn = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
			atReturn.visit("value", "RETURN");
			atReturn.visitEnd();
			ats.visitEnd();
			inject.visit("require", 1);
			inject.visitEnd();
			AnnotationVisitor local = added.visitParameterAnnotation(3, "Lcom/llamalad7/mixinextras/sugar/Local;", false);
			if (byName != null) {
				AnnotationVisitor names = local.visitArray("name");
				names.visit(null, byName);
				names.visitEnd();
			}
			local.visitEnd();
			added.visitCode();
			added.visitInsn(Opcodes.RETURN);
			added.visitMaxs(0, 5);
			added.visitEnd();
			MethodVisitor looked = cw.visitMethod(Opcodes.ACC_PRIVATE, "looked", "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;", null, null);
			AnnotationVisitor mev = looked.visitAnnotation("Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;", true);
			AnnotationVisitor mevMethods = mev.visitArray("method");
			mevMethods.visit(null, REGISTER);
			mevMethods.visitEnd();
			AnnotationVisitor mevAts = mev.visitArray("at");
			AnnotationVisitor call = mevAts.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
			call.visit("value", "INVOKE");
			call.visit("target", "L" + TARGET + ";lookup(Ljava/lang/String;)Ljava/lang/Object;");
			call.visitEnd();
			mevAts.visitEnd();
			mev.visitEnd();
			looked.visitCode();
			looked.visitVarInsn(Opcodes.ALOAD, 1);
			looked.visitInsn(Opcodes.ARETURN);
			looked.visitMaxs(1, 4);
			looked.visitEnd();
			cw.visitEnd();
			Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Reg", cw.toByteArray()));
			MixinConfigFile config = MixinConfigFile.parse(config(true, "Reg"));
			assertFalse(preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty(),
					"the id is no local of the method Mixin picks");

			List<PreflightFinding> notes = new ArrayList<>();
			byte[] repaired = preflight(game, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes)
					.get("com/example/mixin/Reg.class");
			assertNotNull(repaired, notes.toString());
			assertEquals(2, notes.size(), notes.toString());
			assertTrue(notes.stream().anyMatch(n -> n.message().contains("reduced to a call to register" + REGISTER_WITH_ID)), notes.toString());
			assertTrue(notes.stream().anyMatch(n -> n.message().contains("moved into register" + REGISTER_WITH_ID)), notes.toString());
			ClassNode node = new ClassNode();
			new ClassReader(repaired).accept(node, 0);
			MethodNode injectBridge = node.methods.stream().filter(mn -> mn.name.equals("added$fabricated")).findFirst().orElseThrow();
			assertEquals("(ILjava/lang/String;Ljava/lang/Object;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;I)V", injectBridge.desc);
			AnnotationNode moved = Annotations.ofParameter(injectBridge, 4).getFirst();
			assertEquals(byName == null ? 1 : null, Annotations.value(moved, "ordinal"), "by type, the second int in scope: the id the overload stores");
			MethodNode mevBridge = node.methods.stream().filter(mn -> mn.name.equals("looked$fabricated")).findFirst().orElseThrow();
			assertEquals("(Ljava/lang/Object;ILjava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;", mevBridge.desc);
			assertTrue(node.methods.stream().filter(mn -> mn.name.equals("added") || mn.name.equals("looked"))
					.allMatch(mn -> Annotations.all(mn).stream().noneMatch(a -> a.desc.equals(INJECT) || a.desc.contains("ModifyExpressionValue"))));
			// The bridges pass the handlers everything but the id: the class passes the JVM's verifier (initializing links it).
			ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
				@Override
				protected Class<?> findClass(String name) throws ClassNotFoundException {
					if (!name.equals("com.example.mixin.Reg")) throw new ClassNotFoundException(name);
					return defineClass(name, repaired, 0, repaired.length);
				}
			};
			Class.forName("com.example.mixin.Reg", true, loader);

			mod.put("com/example/mixin/Reg", repaired);
			List<PreflightFinding> after = preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
			assertTrue(after.isEmpty(), after.toString());
		}
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

	/**
	 * {@link #TARGET} as NeoForge 21.1 makes {@code BakedQuad}: vanilla's constructor {@code <init>(I)V} kept, not
	 * deprecated, as {@code this(i, true)} for a primitive flag NeoForge added ({@code delegating}); or as vanilla has it,
	 * the one constructor ({@code delegating} false), with {@code <init>(IZ)V} too if {@code both}.
	 */
	private static byte[] keptWithFlag(boolean delegating, boolean both) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor kept = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(I)V", null, null);
		kept.visitCode();
		kept.visitVarInsn(Opcodes.ALOAD, 0);
		if (delegating) {
			kept.visitVarInsn(Opcodes.ILOAD, 1);
			kept.visitInsn(Opcodes.ICONST_1);
			kept.visitMethodInsn(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(IZ)V", false);
		} else {
			kept.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		}
		kept.visitInsn(Opcodes.RETURN);
		kept.visitMaxs(0, 0);
		kept.visitEnd();
		if (delegating || both) {
			MethodVisitor added = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(IZ)V", null, null);
			added.visitCode();
			added.visitVarInsn(Opcodes.ALOAD, 0);
			added.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
			added.visitInsn(Opcodes.RETURN);
			added.visitMaxs(0, 0);
			added.visitEnd();
		}
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** Sodium's {@code BakedQuadMixin} on 1.21.1: an {@code @Inject} at the end of {@code <init>(I)V} taking its parameter. */
	private static void injectTakingTheFlaglessParameters(ClassWriter cw) {
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "init", "(ILorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null);
		AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
		AnnotationVisitor methods = ann.visitArray("method");
		methods.visit(null, "<init>(I)V");
		methods.visitEnd();
		AnnotationVisitor ats = ann.visitArray("at");
		AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
		at.visit("value", "RETURN");
		at.visitEnd();
		ats.visitEnd();
		ann.visitEnd();
		mv.visitCode();
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 3);
		mv.visitEnd();
	}

	@Test
	void anInjectionAtTheEndOfAConstructorNeoForgeKeptForAFlagItAddedMovesWhereVanillaShowsIt() throws Exception {
		// CTOR-5: NeoForge 21.1 bakes quads with the constructor taking its ambient occlusion flag; vanilla's, kept as
		// this(..., true), never runs for them, so Sodium's quad properties were never computed (an NPE drawing items).
		java.util.function.Supplier<Map<String, byte[]>> mod = () -> new HashMap<>(Map.of("com/example/mixin/Quad",
				mixin("com/example/mixin/Quad", false, MixinPreflightTest::injectTakingTheFlaglessParameters)));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Quad"));
		ClassIndex neoForge = new ClassIndex(Map.of(TARGET, keptWithFlag(true, false))::get);
		Map<String, byte[]> vanilla = Map.of(TARGET, keptWithFlag(false, false));
		List<PreflightFinding> notes = new ArrayList<>();

		Map<String, byte[]> classes = mod.get();
		Map<String, byte[]> repaired = new MixinPreflight(neoForge, InstalledClasses.NONE, classes::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);

		assertEquals(Set.of("com/example/mixin/Quad.class"), repaired.keySet(), notes.toString());
		assertTrue(notes.getFirst().message().contains("which NeoForge keeps only to call <init>(IZ)V"), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get("com/example/mixin/Quad.class")).accept(node, 0);
		MethodNode bridge = node.methods.stream().filter(m -> m.name.equals("init$fabricated")).findFirst().orElseThrow();
		assertEquals("(IZLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", bridge.desc);
		assertEquals(List.of("<init>(IZ)V"), Annotations.value(bridge.invisibleAnnotations.getFirst(), "method"));
		classes.put("com/example/mixin/Quad", repaired.get("com/example/mixin/Quad.class"));
		assertTrue(new MixinPreflight(neoForge, InstalledClasses.NONE, classes::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());

		// Without vanilla's class, or where vanilla has both constructors (its own delegation), the injector stays.
		Map<String, byte[]> unknown = mod.get();
		assertTrue(preflight(neoForge, unknown::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty(), "a primitive flag alone may be vanilla's own");
		Map<String, byte[]> vanillaBoth = Map.of(TARGET, keptWithFlag(false, true));
		Map<String, byte[]> own = mod.get();
		assertTrue(new MixinPreflight(neoForge, InstalledClasses.NONE, own::get, ClassBytesSource.EMPTY, false, true, vanillaBoth::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty(), "vanilla has the constructor it calls");
	}

	/**
	 * {@link #TARGET} as NeoForge makes {@code ClientLanguage}: vanilla's constructor {@code <init>(Ljava/util/Map;Z)V},
	 * deprecated and declared first, as {@code this(storage, rtl, Map.of())}, or, with {@code fromParameter}, passing a
	 * value it works out from its storage ({@code Map.copyOf(storage)}); the other one takes NeoForge's component map.
	 */
	private static byte[] keptWithEmptyMap(boolean fromParameter) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor kept = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_DEPRECATED, "<init>", "(Ljava/util/Map;Z)V", null, null);
		kept.visitAnnotation("Ljava/lang/Deprecated;", true).visitEnd();
		kept.visitCode();
		kept.visitVarInsn(Opcodes.ALOAD, 0);
		kept.visitVarInsn(Opcodes.ALOAD, 1);
		kept.visitVarInsn(Opcodes.ILOAD, 2);
		if (fromParameter) {
			kept.visitVarInsn(Opcodes.ALOAD, 1);
			kept.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Map", "copyOf", "(Ljava/util/Map;)Ljava/util/Map;", true);
		} else {
			kept.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Map", "of", "()Ljava/util/Map;", true);
		}
		kept.visitMethodInsn(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(Ljava/util/Map;ZLjava/util/Map;)V", false);
		kept.visitInsn(Opcodes.RETURN);
		kept.visitMaxs(0, 0);
		kept.visitEnd();
		MethodVisitor full = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "(Ljava/util/Map;ZLjava/util/Map;)V", null, null);
		full.visitCode();
		full.visitVarInsn(Opcodes.ALOAD, 0);
		full.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		full.visitInsn(Opcodes.RETURN);
		full.visitMaxs(0, 0);
		full.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** owo-lib's {@code TranslationStorageMixin.onInit}: an {@code @Inject} at the end of {@code <init>}, taking vanilla's parameters. */
	private static void injectAtTheEndOfTheLanguagesConstructor(ClassWriter cw) {
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "onInit", "(Ljava/util/Map;ZLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null);
		AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
		AnnotationVisitor methods = ann.visitArray("method");
		methods.visit(null, "<init>");
		methods.visitEnd();
		AnnotationVisitor ats = ann.visitArray("at");
		AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
		at.visit("value", "TAIL");
		at.visitEnd();
		ats.visitEnd();
		ann.visitEnd();
		mv.visitCode();
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 4);
		mv.visitEnd();
	}

	@Test
	void anInjectionAtTheEndOfAConstructorNeoForgeKeptWithAnEmptyMapMovesThroughABridge() throws Exception {
		// CTOR-6: NeoForge builds ClientLanguage with the constructor taking its component map; vanilla's, kept as
		// this(storage, rtl, Map.of()), never runs, so owo-lib's text map stayed null and the 1.21.1 corpus client crashed.
		java.util.function.Supplier<Map<String, byte[]>> mod = () -> new HashMap<>(Map.of("com/example/mixin/Language",
				mixin("com/example/mixin/Language", false, MixinPreflightTest::injectAtTheEndOfTheLanguagesConstructor)));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Language"));
		ClassIndex neoForge = new ClassIndex(Map.of(TARGET, keptWithEmptyMap(false))::get);
		List<PreflightFinding> notes = new ArrayList<>();

		Map<String, byte[]> classes = mod.get();
		Map<String, byte[]> repaired = preflight(neoForge, classes::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);

		assertEquals(Set.of("com/example/mixin/Language.class"), repaired.keySet(), notes.toString());
		assertTrue(notes.stream().anyMatch(n -> n.message().contains("which NeoForge keeps only to call <init>(Ljava/util/Map;ZLjava/util/Map;)V")),
				notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get("com/example/mixin/Language.class")).accept(node, 0);
		MethodNode bridge = node.methods.stream().filter(m -> m.name.equals("onInit$fabricated")).findFirst().orElseThrow();
		assertEquals("(Ljava/util/Map;ZLjava/util/Map;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", bridge.desc);
		assertEquals(List.of("<init>(Ljava/util/Map;ZLjava/util/Map;)V"), Annotations.value(bridge.invisibleAnnotations.getFirst(), "method"));
		// The bridge passes the storage, the flag and the callback info on, skipping the component map.
		List<Integer> loads = new ArrayList<>();
		for (AbstractInsnNode insn : bridge.instructions) {
			if (insn instanceof VarInsnNode load) loads.add(load.var);
		}
		assertEquals(List.of(0, 1, 2, 4), loads);
		classes.put("com/example/mixin/Language", repaired.get("com/example/mixin/Language.class"));
		assertTrue(preflight(neoForge, classes::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of())
				.stream().noneMatch(f -> f.severity() != Severity.INFO), "the bridge fits the constructor it moved to");

		// A constructor passing a value it works out from its parameters doesn't hand the other one exactly what it got.
		Map<String, byte[]> worked = mod.get();
		ClassIndex copying = new ClassIndex(Map.of(TARGET, keptWithEmptyMap(true))::get);
		for (byte[] bytes : preflight(copying, worked::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).values()) {
			ClassNode unmoved = new ClassNode();
			new ClassReader(bytes).accept(unmoved, 0);
			assertTrue(unmoved.methods.stream().noneMatch(m -> m.name.equals("onInit$fabricated")), "no bridge for a worked-out value");
		}
	}

	private static final String PIECE = "piece(Ljava/lang/Object;I)V";
	private static final String PIECE_ANIMATED = "piece(Ljava/lang/Object;IFF)V";

	/**
	 * {@link #TARGET} as NeoForge 21.1 makes {@code HumanoidArmorLayer}: vanilla's {@code piece(Object, int)} (its
	 * {@code renderArmorPiece}), {@code deprecated} or not, only calls {@code piece(Object, int, float, float)} with zeros
	 * for the limb animation's values, which holds the code (a call to {@code inner(I)Z}); or, {@code vanilla}, as vanilla
	 * has it, without the overload.
	 */
	private static byte[] armorLayerLike(boolean vanilla, boolean deprecated) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor kept = cw.visitMethod(Opcodes.ACC_PRIVATE | (deprecated ? Opcodes.ACC_DEPRECATED : 0), "piece", "(Ljava/lang/Object;I)V", null, null);
		if (deprecated) kept.visitAnnotation("Ljava/lang/Deprecated;", true).visitEnd();
		kept.visitCode();
		kept.visitVarInsn(Opcodes.ALOAD, 0);
		if (vanilla) {
			kept.visitVarInsn(Opcodes.ILOAD, 2);
			kept.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "inner", "(I)Z", false);
			kept.visitInsn(Opcodes.POP);
		} else {
			kept.visitVarInsn(Opcodes.ALOAD, 1);
			kept.visitVarInsn(Opcodes.ILOAD, 2);
			kept.visitInsn(Opcodes.FCONST_0);
			kept.visitInsn(Opcodes.FCONST_0);
			kept.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "piece", "(Ljava/lang/Object;IFF)V", false);
		}
		kept.visitInsn(Opcodes.RETURN);
		kept.visitMaxs(0, 0);
		kept.visitEnd();
		if (!vanilla) {
			MethodVisitor animated = cw.visitMethod(Opcodes.ACC_PRIVATE, "piece", "(Ljava/lang/Object;IFF)V", null, null);
			animated.visitCode();
			animated.visitVarInsn(Opcodes.ALOAD, 0);
			animated.visitVarInsn(Opcodes.ILOAD, 2);
			animated.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "inner", "(I)Z", false);
			animated.visitInsn(Opcodes.POP);
			animated.visitInsn(Opcodes.RETURN);
			animated.visitMaxs(0, 0);
			animated.visitEnd();
		}
		MethodVisitor inner = cw.visitMethod(Opcodes.ACC_PRIVATE, "inner", "(I)Z", null, null);
		inner.visitCode();
		inner.visitInsn(Opcodes.ICONST_0);
		inner.visitInsn(Opcodes.IRETURN);
		inner.visitMaxs(0, 0);
		inner.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * {@code @Inject}s taking the method's parameters: Figura's armour hooks in {@code HumanoidArmorLayerMixinFabric}, one
	 * at the call to {@code inner(I)Z} (its {@code usesInnerModel}), one at the method's return, which undoes what the
	 * first one does; and Fabric API's armour renderers' at its start ({@code ArmorFeatureRendererMixin}, cancellable).
	 */
	private static void armorPieceHooks(ClassWriter cw) {
		for (String handler : List.of("onPiece", "postPiece", "renderPiece")) {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, handler, "(Ljava/lang/Object;ILorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",
					null, null);
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, PIECE);
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			if (handler.equals("onPiece")) {
				at.visit("value", "INVOKE");
				at.visit("target", "L" + TARGET + ";inner(I)Z");
			} else {
				at.visit("value", handler.equals("postPiece") ? "RETURN" : "HEAD");
			}
			at.visitEnd();
			ats.visitEnd();
			if (handler.equals("renderPiece")) ann.visit("cancellable", true);
			ann.visitEnd();
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 4);
			mv.visitEnd();
		}
	}

	@Test
	void injectorsTakingTheParametersOfAMethodThePlatformDeprecatedForAnOverloadMoveThereThroughBridges() throws Exception {
		// PRE-20: NeoForge 21.1 keeps HumanoidArmorLayer.renderArmorPiece only to call an overload that also takes the
		// limb animation's values, which render calls instead. Figura's hook at its usesInnerModel call was missing there,
		// and its hook at the return, which restores the model parts the first one moved, would never run. A hook at its
		// start stays, as one there may cancel the method to do its work another way, and vanilla's class doesn't call the
		// method here (ARMOR-1's test moves it where the class calls the overload in its place).
		java.util.function.Supplier<Map<String, byte[]>> mod = () -> new HashMap<>(Map.of("com/example/mixin/Armor",
				mixin("com/example/mixin/Armor", false, MixinPreflightTest::armorPieceHooks)));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Armor"));
		ClassIndex neoForge = new ClassIndex(Map.of(TARGET, armorLayerLike(false, true))::get);
		Map<String, byte[]> vanilla = Map.of(TARGET, armorLayerLike(true, false));
		List<PreflightFinding> notes = new ArrayList<>();

		Map<String, byte[]> classes = mod.get();
		Map<String, byte[]> repaired = new MixinPreflight(neoForge, InstalledClasses.NONE, classes::get, ClassBytesSource.EMPTY, true, true, vanilla::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);

		assertEquals(Set.of("com/example/mixin/Armor.class"), repaired.keySet(), notes.toString());
		assertTrue(notes.stream().anyMatch(n -> n.message().contains("@Inject onPiece targets " + PIECE + ", whose code NeoForge moved into " + PIECE_ANIMATED)),
				notes.toString());
		assertTrue(notes.stream().anyMatch(n -> n.message().contains("@Inject postPiece targets " + PIECE + ", which NeoForge deprecated for " + PIECE_ANIMATED)),
				notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get("com/example/mixin/Armor.class")).accept(node, 0);
		assertTrue(node.methods.stream().noneMatch(m -> m.name.equals("renderPiece$fabricated")), "the hook at the start stays");
		for (String handler : List.of("onPiece", "postPiece")) {
			MethodNode bridge = node.methods.stream().filter(m -> m.name.equals(handler + "$fabricated")).findFirst().orElseThrow();
			assertEquals("(Ljava/lang/Object;IFFLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", bridge.desc);
			assertEquals(List.of(PIECE_ANIMATED), Annotations.value(bridge.invisibleAnnotations.getFirst(), "method"));
			// The bridge passes the object, the int and the callback info on, skipping the animation's values.
			List<Integer> loads = new ArrayList<>();
			for (AbstractInsnNode insn : bridge.instructions) {
				if (insn instanceof VarInsnNode load) loads.add(load.var);
			}
			assertEquals(List.of(0, 1, 2, 5), loads);
		}
		classes.put("com/example/mixin/Armor", repaired.get("com/example/mixin/Armor.class"));
		assertTrue(new MixinPreflight(neoForge, InstalledClasses.NONE, classes::get, ClassBytesSource.EMPTY, true, true, vanilla::get)
				.check("test.mixins.json", config, Refmap.EMPTY, Set.of()).stream().noneMatch(f -> f.severity() != Severity.INFO),
				"the bridges fit the overload, and the hook at the start the method itself");

		// Not deprecated, or deprecated where vanilla's class has the overload too (vanilla's own), the hook at the
		// return stays; the one at the call moves all the same.
		Map<String, byte[]> notDeprecated = Map.of(TARGET, armorLayerLike(false, false));
		Map<String, byte[]> deprecated = Map.of(TARGET, armorLayerLike(false, true));
		for (List<Map<String, byte[]>> game : List.of(List.of(notDeprecated, vanilla), List.of(deprecated, deprecated))) {
			Map<String, byte[]> own = mod.get();
			List<PreflightFinding> stayed = new ArrayList<>();
			byte[] bytes = new MixinPreflight(new ClassIndex(game.get(0)::get), InstalledClasses.NONE, own::get, ClassBytesSource.EMPTY, true, true,
					game.get(1)::get).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), stayed).get("com/example/mixin/Armor.class");
			ClassNode unmoved = new ClassNode();
			new ClassReader(bytes).accept(unmoved, 0);
			assertTrue(unmoved.methods.stream().anyMatch(m -> m.name.equals("onPiece$fabricated")), stayed.toString());
			assertTrue(unmoved.methods.stream().noneMatch(m -> m.name.equals("postPiece$fabricated")), stayed.toString());
		}
	}

	/**
	 * {@code layer} ({@link #armorLayerLike}) with its pieces' methods of {@code access} and a method {@code caller()V}
	 * calling the piece {@code calls} times (the overload taking the animation's values, where the class has it), then
	 * jumping back to the first call where {@code looped}: as {@code HumanoidArmorLayer.render} calls
	 * {@code renderArmorPiece} for each armour slot.
	 */
	private static byte[] withPieceCaller(byte[] layer, int access, String caller, int calls, boolean looped) {
		ClassNode node = new ClassNode();
		new ClassReader(layer).accept(node, 0);
		boolean animated = node.methods.stream().anyMatch(m -> (m.name + m.desc).equals(PIECE_ANIMATED));
		for (MethodNode m : node.methods) {
			if (m.name.equals("piece")) m.access = m.access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE) | access;
		}
		MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, caller, "()V", null, null);
		LabelNode first = new LabelNode();
		method.instructions.add(first);
		for (int i = 0; i < calls; i++) {
			method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
			method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
			method.instructions.add(new InsnNode(Opcodes.ICONST_0));
			if (animated) {
				method.instructions.add(new InsnNode(Opcodes.FCONST_0));
				method.instructions.add(new InsnNode(Opcodes.FCONST_0));
			}
			String piece = animated ? PIECE_ANIMATED : PIECE;
			method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "piece", piece.substring("piece".length()), false));
		}
		if (looped) {
			method.instructions.add(new InsnNode(Opcodes.ICONST_0));
			method.instructions.add(new JumpInsnNode(Opcodes.IFNE, first));
		}
		method.instructions.add(new InsnNode(Opcodes.RETURN));
		node.methods.add(method);
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		node.accept(cw);
		return cw.toByteArray();
	}

	@Test
	void aHookAtTheStartOfAMethodThePlatformDeprecatedForAnOverloadMovesThereWhereItsClassCallsThatInItsPlace() throws Exception {
		// ARMOR-1: NeoForge 21.1's HumanoidArmorLayer.render calls renderArmorPiece's overload taking the limb animation's
		// values four times, where vanilla's calls renderArmorPiece four times, and both are private: the overload starts
		// where and as often as vanilla's method did, so Fabric API's cancellable armour renderer hook at its start moves
		// there. Cancelled, it skips the overload's body (vanilla's code there), as it skipped the method's on vanilla.
		java.util.function.Supplier<Map<String, byte[]>> mod = () -> new HashMap<>(Map.of("com/example/mixin/Armor",
				mixin("com/example/mixin/Armor", false, MixinPreflightTest::armorPieceHooks)));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Armor"));
		BiFunction<byte[], byte[], Map<String, byte[]>> repair = (platform, vanilla) -> new MixinPreflight(new ClassIndex(Map.of(TARGET, platform)::get),
				InstalledClasses.NONE, mod.get()::get, ClassBytesSource.EMPTY, true, true, Map.of(TARGET, vanilla)::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>());
		byte[] vanilla = withPieceCaller(armorLayerLike(true, false), Opcodes.ACC_PRIVATE, "render", 4, false);
		byte[] neoForge = withPieceCaller(armorLayerLike(false, true), Opcodes.ACC_PRIVATE, "render", 4, false);
		Map<String, byte[]> classes = mod.get();
		List<PreflightFinding> notes = new ArrayList<>();
		ClassIndex game = new ClassIndex(Map.of(TARGET, neoForge)::get);
		Map<String, byte[]> repaired = new MixinPreflight(game, InstalledClasses.NONE, classes::get, ClassBytesSource.EMPTY, true, true,
				Map.of(TARGET, vanilla)::get).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);

		assertTrue(notes.stream().anyMatch(n -> n.message().contains("@Inject renderPiece targets " + PIECE + ", which NeoForge deprecated for " + PIECE_ANIMATED
				+ "; the injector is pointed there, as its class calls it in place of each of vanilla's calls of the first")), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get("com/example/mixin/Armor.class")).accept(node, 0);
		MethodNode bridge = node.methods.stream().filter(m -> m.name.equals("renderPiece$fabricated")).findFirst().orElseThrow();
		// It takes the overload's parameters and the same (not returnable) callback info, and stays cancellable.
		assertEquals("(Ljava/lang/Object;IFFLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", bridge.desc);
		AnnotationNode moved = bridge.invisibleAnnotations.getFirst();
		assertEquals(List.of(PIECE_ANIMATED), Annotations.value(moved, "method"));
		assertEquals(true, Annotations.value(moved, "cancellable"));
		assertEquals("HEAD", Annotations.value(Annotations.list(Annotations.value(moved, "at")).getFirst(), "value"));
		// The handler gets the object, the int and that callback info, which the overload's start then tests.
		List<Integer> loads = new ArrayList<>();
		List<String> calls = new ArrayList<>();
		for (AbstractInsnNode insn : bridge.instructions) {
			if (insn instanceof VarInsnNode load) loads.add(load.var);
			if (insn instanceof MethodInsnNode call) calls.add(call.name + call.desc);
		}
		assertEquals(List.of(0, 1, 2, 5), loads);
		assertEquals(List.of("renderPiece(Ljava/lang/Object;ILorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V"), calls);
		classes.put("com/example/mixin/Armor", repaired.get("com/example/mixin/Armor.class"));
		assertTrue(new MixinPreflight(game, InstalledClasses.NONE, classes::get, ClassBytesSource.EMPTY, true, true, Map.of(TARGET, vanilla)::get)
				.check("test.mixins.json", config, Refmap.EMPTY, Set.of()).stream().noneMatch(f -> f.severity() != Severity.INFO),
				"the bridge fits the overload");

		// It stays where the platform's class calls the overload otherwise: more often (twice for each of vanilla's calls,
		// as NeoForge 21.1's section compiler calls tesselateBlock once per render type, FRAPI-5), from another method, or
		// inside a loop; and where the method isn't private in vanilla's class, which other classes may call (Indigo's
		// hook at the start of ModelBlockRenderer.tesselateBlock), or the overload isn't in the platform's.
		Map<String, List<byte[]>> stays = Map.of(
				"called more often", List.of(withPieceCaller(armorLayerLike(false, true), Opcodes.ACC_PRIVATE, "render", 8, false), vanilla),
				"called from another method", List.of(withPieceCaller(armorLayerLike(false, true), Opcodes.ACC_PRIVATE, "renderOther", 4, false), vanilla),
				"called in a loop", List.of(withPieceCaller(armorLayerLike(false, true), Opcodes.ACC_PRIVATE, "render", 4, true), vanilla),
				"public in vanilla's class", List.of(withPieceCaller(armorLayerLike(false, true), Opcodes.ACC_PUBLIC, "render", 4, false),
						withPieceCaller(armorLayerLike(true, false), Opcodes.ACC_PUBLIC, "render", 4, false)),
				"public in the platform's class", List.of(withPieceCaller(armorLayerLike(false, true), Opcodes.ACC_PUBLIC, "render", 4, false), vanilla),
				"not called by vanilla's class", List.of(withPieceCaller(armorLayerLike(false, true), Opcodes.ACC_PRIVATE, "render", 0, false),
						withPieceCaller(armorLayerLike(true, false), Opcodes.ACC_PRIVATE, "render", 0, false)));
		stays.forEach((why, platformAndVanilla) -> {
			ClassNode unmoved = new ClassNode();
			new ClassReader(repair.apply(platformAndVanilla.get(0), platformAndVanilla.get(1)).get("com/example/mixin/Armor.class")).accept(unmoved, 0);
			assertTrue(unmoved.methods.stream().noneMatch(m -> m.name.equals("renderPiece$fabricated")), why);
			assertTrue(unmoved.methods.stream().anyMatch(m -> m.name.equals("postPiece$fabricated")), why + ": the hook at the return moves all the same");
		});
	}

	private static final String CRAFTING_HELPER = "net/neoforged/neoforge/common/crafting/CraftingHelper";
	private static final String CODEC_DESC = "(Z)Ljava/util/List;";

	/**
	 * {@link #TARGET} as {@code Ingredient}: a private static {@code codec(boolean)} its static initializer calls twice, as
	 * vanilla has it; or, {@code neoForge}, deprecated, with the initializer calling {@code CraftingHelper}'s instead, or
	 * ({@code stillCalled}) calling both.
	 */
	private static byte[] ingredientLike(boolean neoForge, boolean stillCalled) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, TARGET, null, "java/lang/Object", null);
		cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "CODEC", "Ljava/util/List;", null, null).visitEnd();
		cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "CODEC_NONEMPTY", "Ljava/util/List;", null, null).visitEnd();
		MethodVisitor clinit = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
		clinit.visitCode();
		for (String[] field : new String[][] {{"CODEC", "1"}, {"CODEC_NONEMPTY", "0"}}) {
			clinit.visitInsn(field[1].equals("1") ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
			clinit.visitMethodInsn(Opcodes.INVOKESTATIC, neoForge ? CRAFTING_HELPER : TARGET, neoForge ? "makeIngredientCodec" : "codec", CODEC_DESC, false);
			clinit.visitFieldInsn(Opcodes.PUTSTATIC, TARGET, field[0], "Ljava/util/List;");
		}
		if (stillCalled) {
			clinit.visitInsn(Opcodes.ICONST_0);
			clinit.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET, "codec", CODEC_DESC, false);
			clinit.visitInsn(Opcodes.POP);
		}
		clinit.visitInsn(Opcodes.RETURN);
		clinit.visitMaxs(0, 0);
		clinit.visitEnd();
		MethodVisitor codec = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | (neoForge ? Opcodes.ACC_DEPRECATED : 0), "codec", CODEC_DESC, null, null);
		if (neoForge) codec.visitAnnotation("Ljava/lang/Deprecated;", true).visitEnd();
		codec.visitCode();
		codec.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/List", "of", "()Ljava/util/List;", true);
		codec.visitInsn(Opcodes.ARETURN);
		codec.visitMaxs(0, 0);
		codec.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * Fabric API 1.21.1's {@code IngredientMixin.injectCodec}: at the return of {@code codec(boolean)}, cancellable, it
	 * sets the return value to a list of the flag and what the method returned.
	 */
	private static void injectAtTheCodecsReturn(ClassWriter cw) {
		String cir = "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "injectCodec", "(ZL" + cir + ";)V", null, null);
		AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
		AnnotationVisitor methods = ann.visitArray("method");
		methods.visit(null, "codec");
		methods.visitEnd();
		AnnotationVisitor ats = ann.visitArray("at");
		AnnotationVisitor at = ats.visitAnnotation(null, AT);
		at.visit("value", "RETURN");
		at.visitEnd();
		ats.visitEnd();
		ann.visit("cancellable", true);
		ann.visitEnd();
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitVarInsn(Opcodes.ILOAD, 0);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, cir, "getReturnValue", "()Ljava/lang/Object;", false);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/List", "of", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;", true);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, cir, "setReturnValue", "(Ljava/lang/Object;)V", false);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(4, 2);
		mv.visitEnd();
	}

	/** Mixin's returnable callback info, as far as a handler and a bridge use it. */
	private static byte[] callbackInfoReturnableStub() {
		String cir = "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, cir, null, "java/lang/Object", null);
		cw.visitField(Opcodes.ACC_PRIVATE, "value", "Ljava/lang/Object;", null, null).visitEnd();
		MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/String;ZLjava/lang/Object;)V", null, null);
		init.visitCode();
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitVarInsn(Opcodes.ALOAD, 3);
		init.visitFieldInsn(Opcodes.PUTFIELD, cir, "value", "Ljava/lang/Object;");
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(0, 0);
		init.visitEnd();
		MethodVisitor get = cw.visitMethod(Opcodes.ACC_PUBLIC, "getReturnValue", "()Ljava/lang/Object;", null, null);
		get.visitCode();
		get.visitVarInsn(Opcodes.ALOAD, 0);
		get.visitFieldInsn(Opcodes.GETFIELD, cir, "value", "Ljava/lang/Object;");
		get.visitInsn(Opcodes.ARETURN);
		get.visitMaxs(0, 0);
		get.visitEnd();
		MethodVisitor set = cw.visitMethod(Opcodes.ACC_PUBLIC, "setReturnValue", "(Ljava/lang/Object;)V", null, null);
		set.visitCode();
		set.visitVarInsn(Opcodes.ALOAD, 0);
		set.visitVarInsn(Opcodes.ALOAD, 1);
		set.visitFieldInsn(Opcodes.PUTFIELD, cir, "value", "Ljava/lang/Object;");
		set.visitInsn(Opcodes.RETURN);
		set.visitMaxs(0, 0);
		set.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** The superclass of {@link #TARGET} in the inherited overload tests: Forge 47's {@code Entity}. */
	private static final String BASE = "net/minecraft/world/Base";
	private static final String MOVE = "move(Ljava/lang/Object;)Ljava/lang/Object;";
	private static final String MOVE_WITH = "travel(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;";
	private static final String CIR = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;";

	/**
	 * {@link #BASE} as Forge 47 makes {@code Entity}: {@code move(Object)} (its {@code changeDimension(ServerLevel)}, under
	 * its SRG name) only calls {@code travel(Object, String)} (Forge's {@code changeDimension(ServerLevel, ITeleporter)})
	 * with a value it works out from its parameter (the level's portal forcer); or, {@code vanilla}, with the code of its
	 * own, and the overload too where {@code vanillaOverload}.
	 */
	private static byte[] moverBase(boolean vanilla, boolean vanillaOverload) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, BASE, null, "java/lang/Object", null);
		MethodVisitor move = cw.visitMethod(Opcodes.ACC_PUBLIC, "move", MOVE.substring(4), null, null);
		move.visitCode();
		if (vanilla) {
			move.visitInsn(Opcodes.ACONST_NULL);
		} else {
			move.visitVarInsn(Opcodes.ALOAD, 0);
			move.visitVarInsn(Opcodes.ALOAD, 1);
			move.visitVarInsn(Opcodes.ALOAD, 1);
			move.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false);
			move.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BASE, "travel", MOVE_WITH.substring(6), false);
		}
		move.visitInsn(Opcodes.ARETURN);
		move.visitMaxs(0, 0);
		move.visitEnd();
		if (!vanilla || vanillaOverload) {
			MethodVisitor with = cw.visitMethod(Opcodes.ACC_PUBLIC, "travel", MOVE_WITH.substring(6), null, null);
			with.visitCode();
			with.visitInsn(Opcodes.ACONST_NULL);
			with.visitInsn(Opcodes.ARETURN);
			with.visitMaxs(0, 0);
			with.visitEnd();
		}
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * {@link #TARGET} extending {@link #BASE} as Forge 47 makes {@code ServerPlayer}: vanilla's override of
	 * {@code move(Object)}, which calls {@code inner()V}, is an override of {@code travel(Object, String)} (where
	 * {@code overrides}); or, {@code vanilla}, vanilla's override itself.
	 */
	private static byte[] moverTarget(boolean vanilla, boolean overrides) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, TARGET, null, BASE, null);
		if (vanilla || overrides) {
			MethodVisitor move = cw.visitMethod(Opcodes.ACC_PUBLIC, vanilla ? "move" : "travel", (vanilla ? MOVE.substring(4) : MOVE_WITH.substring(6)), null, null);
			move.visitCode();
			move.visitVarInsn(Opcodes.ALOAD, 0);
			move.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "inner", "()V", false);
			move.visitVarInsn(Opcodes.ALOAD, 0);
			move.visitInsn(Opcodes.ARETURN);
			move.visitMaxs(0, 0);
			move.visitEnd();
		}
		MethodVisitor inner = cw.visitMethod(Opcodes.ACC_PRIVATE, "inner", "()V", null, null);
		inner.visitCode();
		inner.visitInsn(Opcodes.RETURN);
		inner.visitMaxs(0, 0);
		inner.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * Carpet's and FrozenLib's dimension change hooks: at the method's start and returns taking its parameter, and one at
	 * its {@code inner()} call taking the callback info alone.
	 */
	private static void moveHooks(ClassWriter cw) {
		for (String handler : List.of("beforeMove", "afterMove", "atInner")) {
			boolean parameter = !handler.equals("atInner");
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, handler, "(" + (parameter ? "Ljava/lang/Object;" : "") + CIR + ")V", null, null);
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, MOVE);
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", switch (handler) {
				case "beforeMove" -> "HEAD";
				case "afterMove" -> "RETURN";
				default -> "INVOKE";
			});
			if (!parameter) at.visit("target", "L" + TARGET + ";inner()V");
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, parameter ? 3 : 2);
			mv.visitEnd();
		}
	}

	@Test
	void injectorsIntoAnOverrideThePlatformMovedToAnOverloadItsSuperclassCallsMoveThere() throws Exception {
		// PRE-24: Forge 47's ServerPlayer has no changeDimension(ServerLevel) of its own: Entity's passes the level and its
		// portal forcer to changeDimension(ServerLevel, ITeleporter), which ServerPlayer overrides with vanilla's code. Every
		// call of the method on a player runs that override once, so the hooks move there, bridged to take its parameters.
		java.util.function.Supplier<Map<String, byte[]>> mod = () -> new HashMap<>(Map.of("com/example/mixin/Moves",
				mixin("com/example/mixin/Moves", false, MixinPreflightTest::moveHooks)));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Moves"));
		Map<String, byte[]> platform = Map.of(BASE, moverBase(false, false), TARGET, moverTarget(false, true));
		Map<String, byte[]> vanilla = Map.of(BASE, moverBase(true, false), TARGET, moverTarget(true, false));
		ClassIndex forge = new ClassIndex(platform::get);
		Map<String, byte[]> classes = mod.get();
		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = new MixinPreflight(forge, InstalledClasses.NONE, classes::get, ClassBytesSource.EMPTY, false, true, vanilla::get,
				"Forge").repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);

		assertEquals(Set.of("com/example/mixin/Moves.class"), repaired.keySet(), notes.toString());
		for (String handler : List.of("beforeMove", "afterMove", "atInner")) {
			assertTrue(notes.stream().anyMatch(n -> n.message().contains("@Inject " + handler + " targets " + MOVE + ", which Forge's net.minecraft.world.Thing"
					+ " no longer overrides: the net.minecraft.world.Base method it inherits only passes its parameters on to " + MOVE_WITH)), notes.toString());
		}
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get("com/example/mixin/Moves.class")).accept(node, 0);
		for (String handler : List.of("beforeMove", "afterMove")) {
			MethodNode bridge = node.methods.stream().filter(m -> m.name.equals(handler + "$fabricated")).findFirst().orElseThrow();
			assertEquals("(Ljava/lang/Object;Ljava/lang/String;" + CIR + ")V", bridge.desc);
			assertEquals(List.of(MOVE_WITH), Annotations.value(bridge.invisibleAnnotations.getFirst(), "method"));
			// The bridge passes the level and the callback info on, not the value the inherited method worked out.
			List<Integer> loads = new ArrayList<>();
			for (AbstractInsnNode insn : bridge.instructions) {
				if (insn instanceof VarInsnNode load) loads.add(load.var);
			}
			assertEquals(List.of(0, 1, 3), loads);
		}
		MethodNode atInner = node.methods.stream().filter(m -> m.name.equals("atInner")).findFirst().orElseThrow();
		assertEquals(List.of(MOVE_WITH), Annotations.value(Annotations.find(atInner, INJECT), "method"));
		classes.put("com/example/mixin/Moves", repaired.get("com/example/mixin/Moves.class"));
		assertTrue(new MixinPreflight(forge, InstalledClasses.NONE, classes::get, ClassBytesSource.EMPTY, false, true, vanilla::get, "Forge")
				.check("test.mixins.json", config, Refmap.EMPTY, Set.of()).stream().noneMatch(f -> f.severity() != Severity.INFO),
				"the hooks fit the overload");

		// Where vanilla's superclass has the overload too (vanilla's own), or the class doesn't override it (its code is the
		// superclass's), or without vanilla's classes, the hooks stay and are reported missing.
		List<List<Map<String, byte[]>>> unmoved = List.of(
				List.of(platform, Map.of(BASE, moverBase(true, true), TARGET, moverTarget(true, false))),
				List.of(Map.of(BASE, moverBase(false, false), TARGET, moverTarget(false, false)), vanilla),
				List.of(platform, Map.of()));
		for (List<Map<String, byte[]>> game : unmoved) {
			Map<String, byte[]> own = mod.get();
			List<PreflightFinding> stayed = new ArrayList<>();
			Map<String, byte[]> none = new MixinPreflight(new ClassIndex(game.get(0)::get), InstalledClasses.NONE, own::get, ClassBytesSource.EMPTY, false, true,
					game.get(1)::get, "Forge").repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), stayed);
			assertTrue(none.isEmpty(), stayed.toString());
			assertTrue(new MixinPreflight(new ClassIndex(game.get(0)::get), InstalledClasses.NONE, own::get, ClassBytesSource.EMPTY, false, true,
					game.get(1)::get, "Forge").check("test.mixins.json", config, Refmap.EMPTY, Set.of()).stream()
					.anyMatch(f -> f.message().contains("targets method " + MOVE + ", which does not exist in net.minecraft.world.Thing")));
		}
	}

	/**
	 * {@link #TARGET} with {@code destroy()Z} as vanilla's {@code ServerPlayerGameMode.destroyBlock} has it, two booleans
	 * in scope at its {@code mine()} call ({@code ItemStack.mineBlock}): the block's removal, then the tool check; or,
	 * {@code patched}, as Forge 47's patch makes it, the removal declared after the call.
	 */
	private static byte[] destroyerLike(boolean patched) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "destroy", "()Z", null, null);
		mv.visitCode();
		Label start = new Label();
		Label removed = new Label();
		Label correct = new Label();
		Label end = new Label();
		mv.visitLabel(start);
		for (String step : patched ? List.of("tool", "mine", "remove") : List.of("remove", "tool", "mine")) {
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, step, step.equals("mine") ? "()V" : "()Z", false);
			if (step.equals("mine")) continue;
			boolean removal = step.equals("remove");
			mv.visitVarInsn(Opcodes.ISTORE, removal == patched ? 2 : 1);
			mv.visitLabel(removal ? removed : correct);
		}
		mv.visitVarInsn(Opcodes.ILOAD, 1);
		mv.visitVarInsn(Opcodes.ILOAD, 2);
		mv.visitInsn(Opcodes.IAND);
		mv.visitInsn(Opcodes.IRETURN);
		mv.visitLabel(end);
		mv.visitLocalVariable("this", "L" + TARGET + ";", null, start, end, 0);
		mv.visitLocalVariable("removed", "Z", null, removed, end, patched ? 2 : 1);
		mv.visitLocalVariable("correct", "Z", null, correct, end, patched ? 1 : 2);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		for (String step : List.of("remove", "tool", "mine")) {
			MethodVisitor m = cw.visitMethod(Opcodes.ACC_PRIVATE, step, step.equals("mine") ? "()V" : "()Z", null, null);
			m.visitCode();
			if (!step.equals("mine")) m.visitInsn(Opcodes.ICONST_1);
			m.visitInsn(step.equals("mine") ? Opcodes.RETURN : Opcodes.IRETURN);
			m.visitMaxs(0, 0);
			m.visitEnd();
		}
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** Apoli's {@code modifyEffectiveTool}: the second boolean in scope at the {@code mine()} call, required (its config's {@code defaultRequire}). */
	private static byte[] toolModifier() {
		return mixin("com/example/mixin/Tool", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "modifyEffectiveTool", "(Z)Z", null, null);
			AnnotationVisitor ann = mv.visitAnnotation(MODIFY_VARIABLE, true);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "destroy");
			methods.visitEnd();
			AnnotationVisitor at = ann.visitAnnotation("at", AT);
			at.visit("value", "INVOKE");
			at.visit("target", "L" + TARGET + ";mine()V");
			at.visitEnd();
			ann.visit("ordinal", 1);
			ann.visit("require", 1);
			ann.visitEnd();
			mv.visitCode();
			mv.visitVarInsn(Opcodes.ILOAD, 1);
			mv.visitInsn(Opcodes.IRETURN);
			mv.visitMaxs(1, 2);
			mv.visitEnd();
		});
	}

	@Test
	void aVariableModifierByOrdinalTakesVanillasLocalWhereThePatchDeclaresAnotherAfterThePoint() throws Exception {
		// PRE-25: Apoli's modifyEffectiveTool modifies the second boolean in scope at ItemStack.mineBlock in destroyBlock,
		// vanilla's tool check. Forge 47 declares the first, the block's removal, after the call: Mixin found no second one
		// there, injected nothing, and the Forge 47 server with Origins stopped ("(0/1) succeeded") where pre-flight passed it.
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Tool"));
		ClassIndex forge = new ClassIndex(Map.of(TARGET, destroyerLike(true))::get);
		Map<String, byte[]> vanilla = Map.of(TARGET, destroyerLike(false));
		BiFunction<Map<String, byte[]>, ClassBytesSource, MixinPreflight> preflight = (mod, original) -> new MixinPreflight(forge, InstalledClasses.NONE,
				mod::get, ClassBytesSource.EMPTY, false, true, original, "Forge");

		// Without vanilla's code to tell which local the ordinal means, pre-flight agrees with Mixin, which would find nothing
		// to modify: the injector is left out.
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Tool", toolModifier()));
		List<PreflightFinding> unrepaired = preflight.apply(mod, ClassBytesSource.EMPTY).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(unrepaired.stream().anyMatch(f -> f.severity() == Severity.LEFT_OUT && f.message().contains("@ModifyVariable modifyEffectiveTool modifies the local"
				+ " of type boolean with ordinal 1 in destroy()Z in net.minecraft.world.Thing, which has at most 1 of that type where its @At points are")),
				unrepaired.toString());
		// Where the method has two, as vanilla's does, it passes.
		assertTrue(new MixinPreflight(new ClassIndex(vanilla::get), InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, ClassBytesSource.EMPTY,
				"Forge").check("test.mixins.json", config, Refmap.EMPTY, Set.of()).stream().noneMatch(f -> f.severity() != Severity.INFO));

		// With vanilla's code, the ordinal follows vanilla's tool check, the first boolean there.
		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight.apply(mod, vanilla::get).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		assertTrue(notes.stream().anyMatch(n -> n.message().contains("@ModifyVariable modifyEffectiveTool modifies the local of type boolean with ordinal 1 in"
				+ " destroy()Z in net.minecraft.world.Thing, vanilla's there, which has ordinal 0 in this game build")), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get("com/example/mixin/Tool.class")).accept(node, 0);
		MethodNode handler = node.methods.stream().filter(m -> m.name.equals("modifyEffectiveTool")).findFirst().orElseThrow();
		assertEquals(0, Annotations.value(Annotations.find(handler, MODIFY_VARIABLE), "ordinal"));
		mod.put("com/example/mixin/Tool", repaired.get("com/example/mixin/Tool.class"));
		List<PreflightFinding> after = preflight.apply(mod, vanilla::get).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.stream().noneMatch(f -> f.severity() != Severity.INFO), after.toString());
	}

	/**
	 * {@link #TARGET} with {@code tick()V} as Forge 47's {@code AbstractMinecart.tick} has it: a client branch storing two
	 * doubles (slots 1 and 3), then the server's, which stores one (slot 5) and passes it to {@code push(DD)V}, the one
	 * double in scope there. The server's branch starts at a frame of {@code frameType}: a same frame, as javac writes it,
	 * or a full one.
	 */
	private static byte[] cartLike(int frameType) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
		mv.visitCode();
		Label start = new Label();
		Label x = new Label();
		Label y = new Label();
		Label server = new Label();
		Label gravity = new Label();
		Label end = new Label();
		mv.visitLabel(start);
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "client", "()Z", false);
		mv.visitJumpInsn(Opcodes.IFEQ, server);
		mv.visitInsn(Opcodes.DCONST_1);
		mv.visitVarInsn(Opcodes.DSTORE, 1);
		mv.visitLabel(x);
		mv.visitInsn(Opcodes.DCONST_1);
		mv.visitVarInsn(Opcodes.DSTORE, 3);
		mv.visitLabel(y);
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitVarInsn(Opcodes.DLOAD, 1);
		mv.visitVarInsn(Opcodes.DLOAD, 3);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "move", "(DD)V", false);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitLabel(server);
		mv.visitFrame(frameType, frameType == Opcodes.F_SAME ? 0 : 1, frameType == Opcodes.F_SAME ? null : new Object[] {TARGET}, 0, null);
		mv.visitInsn(Opcodes.DCONST_0);
		mv.visitVarInsn(Opcodes.DSTORE, 5);
		mv.visitLabel(gravity);
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitInsn(Opcodes.DCONST_0);
		mv.visitVarInsn(Opcodes.DLOAD, 5);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "push", "(DD)V", false);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitLabel(end);
		mv.visitLocalVariable("this", "L" + TARGET + ";", null, start, end, 0);
		mv.visitLocalVariable("x", "D", null, x, server, 1);
		mv.visitLocalVariable("y", "D", null, y, server, 3);
		mv.visitLocalVariable("gravity", "D", null, gravity, end, 5);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** FrozenLib's gravity hook: a {@code @ModifyArgs} at the {@code push(DD)V} call taking the double there by its type, required. */
	private static byte[] gravityArgs() {
		return mixin("com/example/mixin/Gravity", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "useGravity", "(" + ARGS + "D)V", null, null);
			AnnotationVisitor ann = mv.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/ModifyArgs;", true);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "tick");
			methods.visitEnd();
			AnnotationVisitor at = ann.visitAnnotation("at", AT);
			at.visit("value", "INVOKE");
			at.visit("target", "L" + TARGET + ";push(DD)V");
			at.visitEnd();
			ann.visit("require", 1);
			ann.visitEnd();
			mv.visitParameterAnnotation(1, LOCAL, false).visitEnd();
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 4);
			mv.visitEnd();
		});
	}

	@Test
	void aLocalTakenByTypeIsNamedByItsOrdinalAmongTheLocalsForgesMixinKeepsPastTheirScope() throws Exception {
		// PRE-26: FrozenLib's gravity hook takes @Local double at the first Vec3.add call in AbstractMinecart.tick, the one
		// double in scope there. Forge 47's Mixin walks the method's frames as their bytes have them and skips a same frame, so
		// it keeps the client branch's doubles too, and the Forge 47 server stopped ("Found 4 candidate variables").
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Gravity"));
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Gravity", gravityArgs()));
		BiFunction<byte[], Boolean, MixinPreflight> preflight = (target, keeps) -> new MixinPreflight(new ClassIndex(Map.of(TARGET, target)::get),
				InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, ClassBytesSource.EMPTY, "Forge", false,
				MixinPreflight.ConstructorInjects.RETURNS_ONLY, false, true, false, true, false, false, true, keeps);
		byte[] cart = cartLike(Opcodes.F_SAME);

		// Fabric's Mixin, as pre-flight judges it, finds the one double in scope.
		assertTrue(preflight.apply(cart, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).stream().noneMatch(f -> f.severity() != Severity.INFO));
		List<PreflightFinding> found = preflight.apply(cart, true).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(found.stream().anyMatch(f -> f.severity() == Severity.LEFT_OUT && f.message().contains("@ModifyArgs handler useGravity captures the local"
				+ " variable of type double by its type, of which Forge's Mixin finds more than one in tick in net.minecraft.world.Thing there, keeping those"
				+ " whose scope ended before")), found.toString());

		// The double in scope comes after the two kept: the @Local takes it by its ordinal among the three.
		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight.apply(cart, true).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		assertTrue(notes.stream().anyMatch(n -> n.message().contains("@ModifyArgs useGravity captures the local of type double by its type, of which Forge's"
				+ " Mixin finds more than one in tick in net.minecraft.world.Thing there, keeping those whose scope ended before; the one in scope there has"
				+ " ordinal 2 among them")), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get("com/example/mixin/Gravity.class")).accept(node, 0);
		MethodNode handler = node.methods.stream().filter(m -> m.name.equals("useGravity")).findFirst().orElseThrow();
		assertEquals(2, Annotations.value(Annotations.ofParameter(handler, 1).getFirst(), "ordinal"));
		mod.put("com/example/mixin/Gravity", repaired.get("com/example/mixin/Gravity.class"));
		List<PreflightFinding> after = preflight.apply(cart, true).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.stream().noneMatch(f -> f.severity() != Severity.INFO), after.toString());

		// Past a frame of another kind, which Mixin may look the slots up again at, what it keeps can't be told: nothing changes.
		mod.put("com/example/mixin/Gravity", gravityArgs());
		byte[] full = cartLike(Opcodes.F_FULL);
		assertTrue(preflight.apply(full, true).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).stream().noneMatch(f -> f.severity() != Severity.INFO));
		assertTrue(preflight.apply(full, true).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
	}

	@Test
	void aMixinWhoseFieldInitialisersBranchOrStoreArrayElementsHasTheirValuesComputedByMethodsWhereForgesMixinRefusesThem(@TempDir Path dir)
			throws Exception {
		// PRE-28: Cobblemon's ItemRendererMixin sets its model path field to one of two names, as javac writes it a jump in
		// the initialiser Mixin merges into the target's constructors, which SpongePowered's Mixin refuses ("Unsupported JUMP
		// opcode in initialiser"); the Forge 52 client stopped. An array element's store there it refuses too (Cobblemon's
		// ModelLoaderMixin). A branch in the constructor's own body, which Mixin doesn't merge, and Fabric's Mixin, which
		// takes both, pass.
		String mixin = "@org.spongepowered.asm.mixin.Mixin(net.minecraft.world.Thing.class) public class ";
		Map<String, byte[]> classes = new HashMap<>(TestSources.compile(dir, TestSources.MIXIN_ANNOTATIONS, Map.of(
				"net/minecraft/world/Thing.java", "package net.minecraft.world; public class Thing { public void tick() { } }",
				"com/example/mixin/Branching.java", "package com.example.mixin;\n" + mixin + "Branching {\n"
						+ "\tprivate final String path = System.getProperty(\"fabric\") != null ? \"fabric_resource\" : \"standalone\";\n"
						+ "\tprivate final int plain = 3;\n}\n",
				"com/example/mixin/Slots.java", "package com.example.mixin;\n" + mixin + "Slots {\n"
						+ "\tprivate final java.util.List<String> folders = java.util.Arrays.asList(\"egg\", \"tm\");\n}\n",
				"com/example/mixin/Body.java", "package com.example.mixin;\n" + mixin + "Body {\n\tprivate int count;\n\n"
						+ "\tBody() {\n\t\tif (System.getProperty(\"fabric\") != null) count = 1;\n\t}\n}\n",
				// A value storing a local (the pattern's): not outlined.
				"com/example/mixin/Pattern.java", "package com.example.mixin;\n" + mixin + "Pattern {\n"
						+ "\tprivate final boolean empty = System.getProperty(\"fabric\") instanceof String s && s.isEmpty();\n}\n")));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Branching", "Slots", "Body", "Pattern"));
		Function<Boolean, MixinPreflight> preflight = restricts -> new MixinPreflight(new ClassIndex(classes::get), InstalledClasses.NONE,
				classes::get, ClassBytesSource.EMPTY, false, true, ClassBytesSource.EMPTY, "Forge", true, MixinPreflight.ConstructorInjects.UNSAFE_POINTS,
				false, false, false, true, false, false, true, true, restricts);

		List<PreflightFinding> found = preflight.apply(true).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(found.stream().anyMatch(f -> f.severity() == Severity.LEFT_OUT && f.mixin().equals("com.example.mixin.Branching") && f.handler() == null
				&& f.message().equals("Its field initialisers, which Mixin merges into the constructors of its target, branch, which Forge's Mixin refuses"
						+ " there (Fabric's Mixin takes it)")), found.toString());
		assertTrue(found.stream().anyMatch(f -> f.severity() == Severity.LEFT_OUT && f.mixin().equals("com.example.mixin.Slots")
				&& f.message().contains("load or store an array element")), found.toString());
		assertTrue(found.stream().noneMatch(f -> "com.example.mixin.Body".equals(f.mixin()) && f.severity() != Severity.INFO), found.toString());
		assertTrue(preflight.apply(false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).stream()
				.noneMatch(f -> f.severity() != Severity.INFO), "Fabric's Mixin takes them");
		assertTrue(preflight.apply(false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());

		// Each value is computed by a method the initialiser calls, which Mixin merges as the mixin's others.
		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight.apply(true).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		assertEquals(Set.of("com/example/mixin/Branching.class", "com/example/mixin/Slots.class"), repaired.keySet(), notes.toString());
		assertTrue(notes.stream().anyMatch(n -> n.mixin().equals("com.example.mixin.Branching") && n.message().equals("The initialisers of its field(s)"
				+ " path branch or load or store an array element, which Forge's Mixin refuses as it merges them into the constructors of its target;"
				+ " each value is computed by a method the mixin adds (fabricated$init$<field>), as Fabric's Mixin merges them (PRE-28)")), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get("com/example/mixin/Branching.class")).accept(node, 0);
		MethodNode value = node.methods.stream().filter(m -> m.name.equals("fabricated$init$path")).findFirst().orElseThrow();
		assertEquals("()Ljava/lang/String;", value.desc);
		assertEquals(Opcodes.ACC_PRIVATE, value.access);
		assertNotNull(Annotations.find(value, "Lorg/spongepowered/asm/mixin/Unique;"));
		repaired.forEach((name, bytes) -> classes.put(name.substring(0, name.length() - ".class".length()), bytes));
		List<PreflightFinding> after = preflight.apply(true).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.stream().noneMatch(f -> f.severity() != Severity.INFO && !"com.example.mixin.Pattern".equals(f.mixin())), after.toString());
		assertTrue(after.stream().anyMatch(f -> f.severity() == Severity.LEFT_OUT && "com.example.mixin.Pattern".equals(f.mixin())), after.toString());

		// The classes verify, and their fields get the values they got.
		BytesClassLoader loader = new BytesClassLoader(classes);
		for (String[] field : new String[][] {{"Branching", "path", "standalone"}, {"Branching", "plain", "3"}, {"Slots", "folders", "[egg, tm]"}}) {
			Class<?> type = loader.loadClass("com.example.mixin." + field[0]);
			java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor();
			constructor.setAccessible(true);
			java.lang.reflect.Field read = type.getDeclaredField(field[1]);
			read.setAccessible(true);
			assertEquals(field[2], String.valueOf(read.get(constructor.newInstance())));
		}
	}

	@Test
	void anInjectionAtTheReturnOfAMethodNeoForgeNoLongerCallsWrapsTheCallItMakesInstead() throws Exception {
		// DEPRECATED-2: NeoForge 21.1 builds Ingredient's codecs with CraftingHelper.makeIngredientCodec, where vanilla's
		// static initializer calls Ingredient.codec(boolean), which it keeps, deprecated, and never calls: Fabric API's
		// hook at that method's return, which adds Fabric's custom ingredients to the codec, never ran.
		String name = "com/example/mixin/Ingredients";
		Map<String, byte[]> mod = new HashMap<>(Map.of(name, mixin(name, false, MixinPreflightTest::injectAtTheCodecsReturn)));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Ingredients"));
		ClassIndex neoForge = new ClassIndex(Map.of(TARGET, ingredientLike(true, false))::get);
		Map<String, byte[]> vanilla = Map.of(TARGET, ingredientLike(false, false));
		List<PreflightFinding> notes = new ArrayList<>();

		Map<String, byte[]> repaired = new MixinPreflight(neoForge, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);

		assertEquals(Set.of(name + ".class"), repaired.keySet(), notes.toString());
		assertTrue(notes.stream().anyMatch(n -> n.severity() == Severity.WARN && n.message().contains("codec" + CODEC_DESC
				+ ", which NeoForge deprecated and no longer calls: where vanilla's class calls it (<clinit>()V), NeoForge's calls "
				+ CRAFTING_HELPER.replace('/', '.') + ".makeIngredientCodec" + CODEC_DESC)), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get(name + ".class")).accept(node, 0);
		MethodNode handler = node.methods.stream().filter(m -> m.name.equals("injectCodec")).findFirst().orElseThrow();
		assertTrue(Annotations.all(handler).stream().noneMatch(a -> a.desc.equals(INJECT)), "the injector moved to the bridge");
		MethodNode bridge = node.methods.stream().filter(m -> m.name.equals("injectCodec$fabricated")).findFirst().orElseThrow();
		assertEquals("(Z" + OPERATION + ")Ljava/util/List;", bridge.desc);
		AnnotationNode wrapper = Annotations.all(bridge).stream().filter(a -> a.desc.equals(WRAP_OPERATION)).findFirst().orElseThrow();
		assertEquals(List.of("<clinit>()V"), Annotations.value(wrapper, "method"));
		assertEquals("L" + CRAFTING_HELPER + ";makeIngredientCodec" + CODEC_DESC,
				Annotations.value(Annotations.list(Annotations.value(wrapper, "at")).getFirst(), "target"));
		mod.put(name, repaired.get(name + ".class"));
		assertTrue(new MixinPreflight(neoForge, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.check("test.mixins.json", config, Refmap.EMPTY, Set.of()).stream().noneMatch(f -> f.severity() != Severity.INFO));

		// The bridge hands the handler the call's result as at the method's return, and returns what it set.
		ClassWriter operation = new ClassWriter(0);
		String operationName = OPERATION.substring(1, OPERATION.length() - 1);
		operation.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, operationName, null, "java/lang/Object", null);
		operation.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_VARARGS, "call", "([Ljava/lang/Object;)Ljava/lang/Object;", null, null).visitEnd();
		operation.visitEnd();
		ClassLoader loader = new BytesClassLoader(Map.of(name, repaired.get(name + ".class"), operationName, operation.toByteArray(),
				"org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable", callbackInfoReturnableStub()));
		Class<?> mixin = loader.loadClass(name.replace('/', '.'));
		Class<?> operationType = loader.loadClass(operationName.replace('/', '.'));
		List<Object> passed = new ArrayList<>();
		Object original = java.lang.reflect.Proxy.newProxyInstance(loader, new Class<?>[] {operationType}, (proxy, method, args) -> {
			passed.addAll(java.util.Arrays.asList((Object[]) args[0]));
			return List.of("neoforge");
		});
		java.lang.reflect.Method call = mixin.getDeclaredMethod("injectCodec$fabricated", boolean.class, operationType);
		call.setAccessible(true);
		assertEquals(List.of(false, List.of("neoforge")), call.invoke(null, false, original));
		assertEquals(List.of(false), passed, "the call gets its own arguments");

		// Where the class still calls the method, or vanilla's class isn't at hand, the injector stays.
		for (Map<String, byte[]> otherVanilla : List.of(vanilla, Map.<String, byte[]>of())) {
			ClassIndex game = new ClassIndex(Map.of(TARGET, ingredientLike(true, otherVanilla == vanilla))::get);
			Map<String, byte[]> fresh = Map.of(name, mixin(name, false, MixinPreflightTest::injectAtTheCodecsReturn));
			for (byte[] bytes : new MixinPreflight(game, InstalledClasses.NONE, fresh::get, ClassBytesSource.EMPTY, false, true, otherVanilla::get)
					.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).values()) {
				ClassNode kept = new ClassNode();
				new ClassReader(bytes).accept(kept, 0);
				assertTrue(kept.methods.stream().noneMatch(m -> m.name.equals("injectCodec$fabricated")), "no wrapper");
			}
		}
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

	private static final String CALLBACK_INFO = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";

	/** {@link #TARGET} with a static lambda body {@code lambda$load$1} of descriptor {@code desc}, as javac names one. */
	private static byte[] withLambda(String desc) {
		return withStaticMethod("lambda$load$1", desc);
	}

	/** {@link #TARGET} with an empty private static method {@code name} of descriptor {@code desc}. */
	private static byte[] withStaticMethod(String name, String desc) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor lambda = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, name, desc, null, null);
		lambda.visitCode();
		lambda.visitInsn(Opcodes.RETURN);
		lambda.visitMaxs(0, 0);
		lambda.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * A mixin {@code Captures} whose static {@code @Inject} handler {@code h} into {@code lambda$load$1} takes {@code params}
	 * and the callback info, and passes the ones at {@code used} to {@code String.valueOf}.
	 */
	private static byte[] capturingMixin(List<Type> params, int... used) {
		return capturingMixin("lambda$load$1", params, used);
	}

	/** {@link #capturingMixin(List, int...)} into {@code method}. */
	private static byte[] capturingMixin(String method, List<Type> params, int... used) {
		return mixin("com/example/mixin/Captures", false, cw -> {
			String desc = Type.getMethodDescriptor(Type.VOID_TYPE, java.util.stream.Stream.concat(params.stream(), java.util.stream.Stream.of(Type.getType(CALLBACK_INFO))).toArray(Type[]::new));
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "h", desc, null, null);
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, method);
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
			at.visit("value", "HEAD");
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			for (int slot : used) {
				mv.visitVarInsn(Opcodes.ALOAD, slot);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;", false);
				mv.visitInsn(Opcodes.POP);
			}
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(1, params.size() + 1);
			mv.visitEnd();
		});
	}

	/** {@link #TARGET} as NeoForge's {@code Camera}: {@code setRotation(FF)V} only calls {@code setRotation(FFF)V}, which calls {@code rotationYXZ}. */
	private static byte[] delegatingRotation() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "rotation", "Lorg/joml/Quaternionf;", null, null).visitEnd();
		MethodVisitor two = cw.visitMethod(Opcodes.ACC_PROTECTED, "setRotation", "(FF)V", null, null);
		two.visitCode();
		two.visitVarInsn(Opcodes.ALOAD, 0);
		two.visitVarInsn(Opcodes.FLOAD, 1);
		two.visitVarInsn(Opcodes.FLOAD, 2);
		two.visitInsn(Opcodes.FCONST_0);
		two.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "setRotation", "(FFF)V", false);
		two.visitInsn(Opcodes.RETURN);
		two.visitMaxs(0, 0);
		two.visitEnd();
		MethodVisitor three = cw.visitMethod(Opcodes.ACC_PROTECTED, "setRotation", "(FFF)V", null, null);
		three.visitCode();
		three.visitVarInsn(Opcodes.ALOAD, 0);
		three.visitFieldInsn(Opcodes.GETFIELD, TARGET, "rotation", "Lorg/joml/Quaternionf;");
		three.visitVarInsn(Opcodes.FLOAD, 1);
		three.visitVarInsn(Opcodes.FLOAD, 2);
		three.visitVarInsn(Opcodes.FLOAD, 3);
		three.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "org/joml/Quaternionf", "rotationYXZ", "(FFF)Lorg/joml/Quaternionf;", false);
		three.visitInsn(Opcodes.POP);
		three.visitInsn(Opcodes.RETURN);
		three.visitMaxs(0, 0);
		three.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	@Test
	void anArgumentModifierInAMethodThatOnlyCallsItsOverloadMovesThere() throws Exception {
		// CORPUS-18: Do a Barrel Roll's roll modifier in Camera.setRotation, whose code NeoForge moved into setRotation(FFF).
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Roll", mixin("com/example/mixin/Roll", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "setRoll", "(F)F", null, null);
			AnnotationVisitor ann = mv.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/ModifyArg;", true);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "setRotation");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", "INVOKE");
			at.visit("target", "Lorg/joml/Quaternionf;rotationYXZ(FFF)Lorg/joml/Quaternionf;");
			at.visit("remap", false);
			at.visitEnd();
			ats.visitEnd();
			ann.visit("index", 2);
			ann.visitEnd();
			mv.visitCode();
			mv.visitVarInsn(Opcodes.FLOAD, 1);
			mv.visitInsn(Opcodes.FRETURN);
			mv.visitMaxs(1, 2);
			mv.visitEnd();
		})));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Roll"));
		ClassIndex neoForge = new ClassIndex(Map.of(TARGET, delegatingRotation())::get);

		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight(neoForge, mod::get, ClassBytesSource.EMPTY, true).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		assertTrue(notes.getFirst().message().contains("setRoll targets setRotation(FF)V, whose code NeoForge moved into setRotation(FFF)V"), notes.toString());
		mod.put("com/example/mixin/Roll", repaired.get("com/example/mixin/Roll.class"));
		assertTrue(preflight(neoForge, mod::get, ClassBytesSource.EMPTY, true).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());
	}

	@Test
	void aVariableModifierByTypeMovesToTheOverloadTheMethodPassesItsParametersTo() throws Exception {
		// MODEL-3: Fabric API's before-bake model hook, a @ModifyVariable at the model ModelBakerImpl.bake gets, which Forge 47
		// moved into an overload that also takes the sprite getter. The handler takes bake's parameters after the model.
		String two = "(Ljava/lang/String;Ljava/lang/Integer;)Ljava/lang/Object;";
		String three = "(Ljava/lang/String;Ljava/lang/Integer;Ljava/util/function/Function;)Ljava/lang/Object;";
		ClassWriter target = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		target.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		target.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "sprites", "Ljava/util/function/Function;", null, null).visitEnd();
		MethodVisitor bake = target.visitMethod(Opcodes.ACC_PUBLIC, "bake", two, null, null);
		bake.visitCode();
		bake.visitVarInsn(Opcodes.ALOAD, 0);
		bake.visitVarInsn(Opcodes.ALOAD, 1);
		bake.visitVarInsn(Opcodes.ALOAD, 2);
		bake.visitVarInsn(Opcodes.ALOAD, 0);
		bake.visitFieldInsn(Opcodes.GETFIELD, TARGET, "sprites", "Ljava/util/function/Function;");
		bake.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "bake", three, false);
		bake.visitInsn(Opcodes.ARETURN);
		bake.visitMaxs(0, 0);
		bake.visitEnd();
		MethodVisitor withSprites = target.visitMethod(Opcodes.ACC_PUBLIC, "bake", three, null, null);
		withSprites.visitCode();
		withSprites.visitVarInsn(Opcodes.ALOAD, 0);
		withSprites.visitVarInsn(Opcodes.ALOAD, 1);
		withSprites.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "getModel", "(Ljava/lang/String;)Ljava/lang/Object;", false);
		withSprites.visitVarInsn(Opcodes.ASTORE, 4);
		withSprites.visitVarInsn(Opcodes.ALOAD, 4);
		withSprites.visitInsn(Opcodes.ARETURN);
		withSprites.visitMaxs(0, 0);
		withSprites.visitEnd();
		MethodVisitor getModel = target.visitMethod(Opcodes.ACC_PUBLIC, "getModel", "(Ljava/lang/String;)Ljava/lang/Object;", null, null);
		getModel.visitCode();
		getModel.visitInsn(Opcodes.ACONST_NULL);
		getModel.visitInsn(Opcodes.ARETURN);
		getModel.visitMaxs(0, 0);
		getModel.visitEnd();
		target.visitEnd();
		ClassIndex forge = new ClassIndex(Map.of(TARGET, target.toByteArray())::get);
		java.util.function.Function<Integer, byte[]> hook = ordinal -> mixin("com/example/mixin/Bake", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "before", "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Integer;)Ljava/lang/Object;", null, null);
			AnnotationVisitor ann = mv.visitAnnotation(MODIFY_VARIABLE, true);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "bake");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", "INVOKE_ASSIGN");
			at.visit("target", "L" + TARGET + ";getModel(Ljava/lang/String;)Ljava/lang/Object;");
			at.visitEnd();
			ats.visitEnd();
			if (ordinal != null) ann.visit("ordinal", ordinal);
			ann.visitEnd();
			mv.visitCode();
			mv.visitVarInsn(Opcodes.ALOAD, 1);
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitMaxs(1, 4);
			mv.visitEnd();
		});
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Bake"));
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Bake", hook.apply(null)));
		assertFalse(preflight(forge, mod::get, ClassBytesSource.EMPTY, true).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty(),
				"the model is assigned in the overload only");

		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = preflight(forge, mod::get, ClassBytesSource.EMPTY, true).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes)
				.get("com/example/mixin/Bake.class");
		assertNotNull(repaired, notes.toString());
		assertTrue(notes.getFirst().message().contains("before targets bake" + two), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired).accept(node, 0);
		MethodNode bridge = node.methods.stream().filter(m -> m.name.equals("before$fabricated")).findFirst().orElseThrow();
		assertEquals("(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Integer;Ljava/util/function/Function;)Ljava/lang/Object;", bridge.desc,
				"the bridge takes the overload's parameters, and passes the handler bake's");
		defineMixin("com.example.mixin.Bake", repaired);
		mod.put("com/example/mixin/Bake", repaired);
		List<PreflightFinding> after = preflight(forge, mod::get, ClassBytesSource.EMPTY, true).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());

		Map<String, byte[]> byOrdinal = Map.of("com/example/mixin/Bake", hook.apply(0));
		List<PreflightFinding> ordinalNotes = new ArrayList<>();
		preflight(forge, byOrdinal::get, ClassBytesSource.EMPTY, true).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), ordinalNotes);
		assertTrue(ordinalNotes.stream().noneMatch(n -> n.message().contains("targets bake")), "by its place among the locals, it stays: " + ordinalNotes);
	}

	@Test
	void aConstantThePlatformReplacedIsAMissingPoint() throws Exception {
		// CORPUS-46: Pehkui's reach hook modifies vanilla's 9.0 in Player.attack (doubleValue=9.0F, as its source writes it),
		// which NeoForge 21.1's patch replaced with a call to its interaction range: Mixin finds no target and stops the game.
		ClassIndex neoForge = new ClassIndex(Map.of(TARGET, constantTarget(0.5d))::get);
		ClassIndex vanilla = new ClassIndex(Map.of(TARGET, constantTarget(9.0d))::get);
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Reach", "Zeros", "Strings"));
		Map<String, byte[]> mod = Map.of(
				"com/example/mixin/Reach", constantMixin("com/example/mixin/Reach", "(D)D", "doubleValue=9.0F"),
				// Not judged: zero's conditional jumps, which Mixin also matches.
				"com/example/mixin/Zeros", constantMixin("com/example/mixin/Zeros", "(I)I", "intValue=0", "expandZeroConditions=LESS_THAN_ZERO"),
				"com/example/mixin/Strings", constantMixin("com/example/mixin/Strings", "(Ljava/lang/String;)Ljava/lang/String;", "stringValue=reach"));

		List<PreflightFinding> patched = preflight(neoForge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertEquals(1, patched.size(), patched.toString());
		assertTrue(patched.getFirst().mixin().endsWith("Reach") && patched.getFirst().message().contains("expects @At(\"CONSTANT\", args=[doubleValue=9.0F])"),
				patched.toString());
		List<PreflightFinding> unpatched = preflight(vanilla, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(unpatched.isEmpty(), unpatched.toString());
		assertEquals(InjectionPoints.Verdict.UNKNOWN, InjectionPoints.constantPointVerdict(List.of("doubleValue= 9.0"), -1, InjectionPoints.Range.ALL,
				methodOf(constantTarget(9.0d), "attack")), "Mixin doesn't parse a value with a space");
		assertEquals(InjectionPoints.Verdict.MISSING, InjectionPoints.constantPointVerdict(List.of("doubleValue=9.0"), 1, InjectionPoints.Range.ALL,
				methodOf(constantTarget(9.0d), "attack")), "the method has one 9.0");
	}

	/** {@link #TARGET} with {@code attack(Object)}, which loads {@code reach}, the string "reach" and 0. */
	private static byte[] constantTarget(double reach) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "attack", "(Ljava/lang/Object;)V", null, null);
		mv.visitCode();
		mv.visitLdcInsn(reach);
		mv.visitInsn(Opcodes.POP2);
		mv.visitLdcInsn("reach");
		mv.visitInsn(Opcodes.POP);
		mv.visitInsn(Opcodes.ICONST_0);
		mv.visitInsn(Opcodes.POP);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static MethodNode methodOf(byte[] bytes, String name) {
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, 0);
		return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
	}

	/** A mixin whose {@code @ModifyExpressionValue} takes the constant {@code args} name in {@code attack}. */
	private static byte[] constantMixin(String name, String handlerDesc, String... args) {
		return mixin(name, false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "modify", handlerDesc, null, null);
			AnnotationVisitor ann = mv.visitAnnotation("Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;", false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "attack(Ljava/lang/Object;)V");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", "CONSTANT");
			AnnotationVisitor values = at.visitArray("args");
			for (String arg : args) values.visit(null, arg);
			values.visitEnd();
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			Type value = Type.getArgumentTypes(handlerDesc)[0];
			mv.visitVarInsn(value.getOpcode(Opcodes.ILOAD), 1);
			mv.visitInsn(value.getOpcode(Opcodes.IRETURN));
			mv.visitMaxs(value.getSize() + 1, value.getSize() + 1);
			mv.visitEnd();
		});
	}

	@Test
	void anExpressionModifierMovesToAnOverloadTakingMoreArguments() throws Exception {
		// PRE-13: Polymer modifies the codecs vanilla's custom payload packets make with CustomPacketPayload.codec(fallback,
		// types); NeoForge's patches call codec(fallback, types, protocol, flow) in both places.
		String codecs = "com/example/lib/Codecs";
		String vanilla = "(Ljava/lang/Object;Ljava/util/List;)Ljava/lang/Object;";
		String neoForge = "(Ljava/lang/Object;Ljava/util/List;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;";
		ClassWriter target = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		target.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor init = target.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
		init.visitCode();
		for (int i = 0; i < 2; i++) {
			for (int arg = 0; arg < 4; arg++) init.visitInsn(Opcodes.ACONST_NULL);
			init.visitMethodInsn(Opcodes.INVOKESTATIC, codecs, "codec", neoForge, false);
			init.visitInsn(Opcodes.POP);
		}
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(0, 0);
		init.visitEnd();
		target.visitEnd();
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Payloads", mixin("com/example/mixin/Payloads", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "wrap", "(Ljava/lang/Object;)Ljava/lang/Object;", null, null);
			AnnotationVisitor ann = mv.visitAnnotation("Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;", false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "<clinit>");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", "INVOKE");
			at.visit("target", "L" + codecs + ";codec" + vanilla);
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitMaxs(1, 1);
			mv.visitEnd();
		})));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Payloads"));
		ClassIndex neoForgeGame = new ClassIndex(Map.of(TARGET, target.toByteArray())::get);

		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight(neoForgeGame, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		assertTrue(notes.getFirst().message().contains("through an overload with 2 more arguments, codec" + neoForge), notes.toString());
		mod.put("com/example/mixin/Payloads", repaired.get("com/example/mixin/Payloads.class"));
		assertTrue(preflight(neoForgeGame, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());
		assertFalse(CallRepairs.appendsArguments(vanilla, "(Ljava/lang/String;Ljava/util/List;Ljava/lang/String;)Ljava/lang/Object;"), "only after the named call's parameters");
	}

	@Test
	void anInjectionIntoALambdaWhoseCapturedValuesAreReorderedGetsABridge() throws Exception {
		// LAMBDA-3: Wover's handler takes WorldLoader's lambda's captured values in vanilla's order; NeoForge's build captures
		// a list earlier. Its handler reads neither list, only the values of a type the lambda has once.
		Type list = Type.getType(List.class);
		Type string = Type.getType(String.class);
		Type integer = Type.getType(Integer.class);
		String neoForgeOrder = Type.getMethodDescriptor(Type.VOID_TYPE, list, string, list, integer);
		ClassIndex game = new ClassIndex(Map.of(TARGET, withLambda(neoForgeOrder))::get);
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Captures"));
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Captures", capturingMixin(List.of(list, list, string, integer), 2, 3)));
		assertEquals(1, preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).size());

		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = preflight(game, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes)
				.get("com/example/mixin/Captures.class");
		assertNotNull(repaired, notes.toString());
		assertTrue(notes.getFirst().message().contains("h targets lambda$load$1" + neoForgeOrder + ", whose captured values"), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired).accept(node, 0);
		MethodNode bridge = node.methods.stream().filter(mn -> mn.name.equals("h$fabricated")).findFirst().orElseThrow();
		assertEquals(neoForgeOrder.replace(")V", CALLBACK_INFO + ")V"), bridge.desc);
		List<Integer> loads = new ArrayList<>();
		for (AbstractInsnNode insn : bridge.instructions) {
			if (insn instanceof VarInsnNode v) loads.add(v.var);
		}
		assertEquals(List.of(0, 2, 1, 3, 4), loads, "the string and the integer go where the handler takes them");
		ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				if (!name.equals("com.example.mixin.Captures")) throw new ClassNotFoundException(name);
				return defineClass(name, repaired, 0, repaired.length);
			}
		};
		Class.forName("com.example.mixin.Captures", true, loader);
		mod.put("com/example/mixin/Captures", repaired);
		List<PreflightFinding> after = preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());

		Map<String, byte[]> readsAList = Map.of("com/example/mixin/Captures", capturingMixin(List.of(list, list, string, integer), 1));
		assertTrue(preflight(game, readsAList::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty(), "which list is which is not known");

		// SIG-1: a method NeoForge gave one more parameter (Ledger's FireBlock.checkBurnOut, which takes a face too) is bridged
		// in order; its parameters are not matched by type, as only a lambda's captured values are reordered.
		ClassIndex withFace = new ClassIndex(Map.of(TARGET, withStaticMethod("checkBurnOut", Type.getMethodDescriptor(Type.VOID_TYPE, list, string, integer, Type.BOOLEAN_TYPE)))::get);
		List<PreflightFinding> burnNotes = new ArrayList<>();
		assertNotNull(preflight(withFace, Map.of("com/example/mixin/Captures", capturingMixin("checkBurnOut", List.of(list, string, integer), 1))::get,
				ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), burnNotes).get("com/example/mixin/Captures.class"));
		assertTrue(burnNotes.getFirst().message().contains("which this game build declares with more parameters"), burnNotes.toString());
		ClassIndex reordered = new ClassIndex(Map.of(TARGET, withStaticMethod("checkBurnOut", neoForgeOrder))::get);
		assertTrue(preflight(reordered, Map.of("com/example/mixin/Captures", capturingMixin("checkBurnOut", List.of(list, list, string, integer), 2, 3))::get,
				ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty(), "only a lambda's values are reordered");

		// FrozenLib's: NeoForge's build captures one more value, a boolean; the handler's occur in the lambda's in one way only.
		String withFlag = Type.getMethodDescriptor(Type.VOID_TYPE, list, string, Type.BOOLEAN_TYPE);
		ClassIndex flagged = new ClassIndex(Map.of(TARGET, withLambda(withFlag))::get);
		Map<String, byte[]> prefix = Map.of("com/example/mixin/Captures", capturingMixin(List.of(list, string), 0, 1));
		byte[] bridged = preflight(flagged, prefix::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).get("com/example/mixin/Captures.class");
		assertNotNull(bridged);
		Map<String, byte[]> prefixRepaired = Map.of("com/example/mixin/Captures", bridged);
		assertTrue(preflight(flagged, prefixRepaired::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());
	}

	@Test
	void aModifyArgsIntoALambdaThatCapturesMoreValuesGetsABridgeTakingTheArgsFirst() throws Exception {
		// CORPUS-58: Ledger's anvil hook takes AnvilMenu.onTake's lambda's values as vanilla's lambda captures them (the
		// player, the level, the position); NeoForge's captures the break chance too, and Mixin refused the handler as it
		// applied the mixin, which stopped the server.
		Type list = Type.getType(List.class);
		Type string = Type.getType(String.class);
		String neoForge = Type.getMethodDescriptor(Type.VOID_TYPE, list, Type.FLOAT_TYPE, string);
		ClassIndex game = new ClassIndex(Map.of(TARGET, lambdaCallingValueOf(neoForge))::get);
		MixinConfigFile config = MixinConfigFile.parse(config(true, "AnvilArgs"));
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/AnvilArgs", modifyArgsMixin(List.of(list, string))));
		List<PreflightFinding> before = preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertEquals(1, before.size(), before.toString());
		assertTrue(before.getFirst().message().contains("does not match the parameters of lambda$load$1" + neoForge), before.toString());

		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = preflight(game, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes)
				.get("com/example/mixin/AnvilArgs.class");
		assertNotNull(repaired, notes.toString());
		assertTrue(notes.getFirst().message().contains("h targets lambda$load$1" + neoForge + ", whose captured values"), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired).accept(node, 0);
		MethodNode bridge = node.methods.stream().filter(mn -> mn.name.equals("h$fabricated")).findFirst().orElseThrow();
		assertEquals(Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(ARGS), list, Type.FLOAT_TYPE, string), bridge.desc);
		assertNotNull(Annotations.find(bridge, "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;"), "the injector is on the bridge");
		List<Integer> loads = new ArrayList<>();
		for (AbstractInsnNode insn : bridge.instructions) {
			if (insn instanceof VarInsnNode v) loads.add(v.var);
		}
		assertEquals(List.of(0, 1, 3), loads, "the args, then the list and the string, without the break chance");
		ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				if (!name.equals("com.example.mixin.AnvilArgs")) throw new ClassNotFoundException(name);
				return defineClass(name, repaired, 0, repaired.length);
			}
		};
		Class.forName("com.example.mixin.AnvilArgs", true, loader);
		mod.put("com/example/mixin/AnvilArgs", repaired);
		List<PreflightFinding> after = preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());

		// A handler taking values the lambda doesn't have isn't bridged, and is left out before Mixin could stop the launch;
		// one taking the args alone fits any method.
		Map<String, byte[]> unrelated = Map.of("com/example/mixin/AnvilArgs", modifyArgsMixin(List.of(Type.getType(Integer.class))));
		assertTrue(preflight(game, unrelated::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
		List<PreflightFinding> refused = preflight(game, unrelated::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertEquals(1, refused.size(), refused.toString());
		assertTrue(refused.getFirst().message().contains("does not match the parameters"), refused.toString());
		Map<String, byte[]> argsOnly = Map.of("com/example/mixin/AnvilArgs", modifyArgsMixin(List.of()));
		List<PreflightFinding> fits = preflight(game, argsOnly::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(fits.isEmpty(), fits.toString());
	}

	private static final String ARGS = "Lorg/spongepowered/asm/mixin/injection/invoke/arg/Args;";

	/** {@link #TARGET} with a private static lambda {@code lambda$load$1} of descriptor {@code desc} passing its first value to {@code String.valueOf}. */
	private static byte[] lambdaCallingValueOf(String desc) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor lambda = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, "lambda$load$1", desc, null, null);
		lambda.visitCode();
		lambda.visitVarInsn(Opcodes.ALOAD, 0);
		lambda.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;", false);
		lambda.visitInsn(Opcodes.POP);
		lambda.visitInsn(Opcodes.RETURN);
		lambda.visitMaxs(0, 0);
		lambda.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * A mixin {@code AnvilArgs} whose static {@code @ModifyArgs} handler {@code h}, at {@code lambda$load$1}'s call of
	 * {@code String.valueOf}, takes Mixin's {@code Args} and then {@code params}, and reads each of them.
	 */
	private static byte[] modifyArgsMixin(List<Type> params) {
		return mixin("com/example/mixin/AnvilArgs", false, cw -> {
			Type[] args = java.util.stream.Stream.concat(java.util.stream.Stream.of(Type.getType(ARGS)), params.stream()).toArray(Type[]::new);
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "h", Type.getMethodDescriptor(Type.VOID_TYPE, args), null, null);
			AnnotationVisitor ann = mv.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/ModifyArgs;", false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "lambda$load$1");
			methods.visitEnd();
			AnnotationVisitor at = ann.visitAnnotation("at", AT);
			at.visit("value", "INVOKE");
			at.visit("target", "Ljava/lang/String;valueOf(Ljava/lang/Object;)Ljava/lang/String;");
			at.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			for (int slot = 1; slot < args.length; slot++) {
				mv.visitVarInsn(Opcodes.ALOAD, slot);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;", false);
				mv.visitInsn(Opcodes.POP);
			}
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(1, args.length);
			mv.visitEnd();
		});
	}

	@Test
	void aStaticHandlerOnALambdaThatTakesThisGetsABridgeTakingThisWhereMixinRefusesIt() throws Exception {
		// LAMBDA-7: Forge 47's MouseHandler reads the client in its screen lambdas, so they take this where vanilla's are
		// static; SpongePowered's 0.8.5 refuses fabric-screen-api-v1's static handlers there, 0.8.6 on take them.
		Type string = Type.getType(String.class);
		String desc = Type.getMethodDescriptor(Type.VOID_TYPE, string);
		ClassIndex game = new ClassIndex(Map.of(TARGET, lambdaClass("lambda$load$1", desc, false, mv -> mv.visitInsn(Opcodes.RETURN)))::get);
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Captures"));
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Captures", capturingMixin(List.of(string), 0)));
		java.util.function.Function<Boolean, MixinPreflight> forge = exact -> new MixinPreflight(game, InstalledClasses.NONE, mod::get,
				ClassBytesSource.EMPTY, false, true, ClassBytesSource.EMPTY, "Forge", !exact, exact ? MixinPreflight.ConstructorInjects.RETURNS_ONLY
						: MixinPreflight.ConstructorInjects.UNSAFE_POINTS, false, exact);
		assertTrue(forge.apply(false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty(), "0.8.7 takes it");
		assertTrue(forge.apply(false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());

		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = forge.apply(true).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes).get("com/example/mixin/Captures.class");
		assertNotNull(repaired, notes.toString());
		assertTrue(notes.getFirst().message().contains("h targets lambda$load$1" + desc + ", which this game build declares taking this"), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired).accept(node, 0);
		MethodNode bridge = node.methods.stream().filter(mn -> mn.name.equals("h$fabricated")).findFirst().orElseThrow();
		assertEquals(0, bridge.access & Opcodes.ACC_STATIC, "the bridge takes this");
		assertEquals(desc.replace(")V", CALLBACK_INFO + ")V"), bridge.desc);
		List<Integer> loads = new ArrayList<>();
		for (AbstractInsnNode insn : bridge.instructions) {
			if (insn instanceof VarInsnNode v) loads.add(v.var);
		}
		assertEquals(List.of(1, 2), loads, "the handler gets the bridge's parameters, not this");
		defineMixin(repaired);
		mod.put("com/example/mixin/Captures", repaired);
		List<PreflightFinding> after = forge.apply(true).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());
	}

	@Test
	void aStaticHandlerNamingMethodsThatTakeThisByTwoSelectorsGetsABridgeTakingThisWhereMixinRefusesIt() throws Exception {
		// LAMBDA-12: Architectury's screen click hooks name Forge 47's MouseHandler lambda by javac's name and by vanilla's;
		// the lambda takes this there, and SpongePowered's 0.8.5 refuses their static handlers.
		Type string = Type.getType(String.class);
		String desc = Type.getMethodDescriptor(Type.VOID_TYPE, string, Type.getType(CALLBACK_INFO));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Captures"));
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Captures", twoSelectorMixin(desc)));
		java.util.function.BiFunction<Boolean, Boolean, MixinPreflight> forge = (exact, lambdaStatic) -> new MixinPreflight(
				new ClassIndex(Map.of(TARGET, lambdaClass("m_1000_", "(Ljava/lang/String;)V", lambdaStatic, mv -> mv.visitInsn(Opcodes.RETURN)))::get),
				InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, ClassBytesSource.EMPTY, "Forge", !exact,
				exact ? MixinPreflight.ConstructorInjects.RETURNS_ONLY : MixinPreflight.ConstructorInjects.UNSAFE_POINTS, false, exact);
		List<PreflightFinding> refused = forge.apply(true, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(refused.stream().anyMatch(f -> f.message().contains("@Inject handler h is static where m_1000_")), refused.toString());
		assertTrue(forge.apply(false, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty(), "0.8.7 takes it");
		assertTrue(forge.apply(true, true).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty(), "a static method takes it");

		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = forge.apply(true, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes).get("com/example/mixin/Captures.class");
		assertNotNull(repaired, notes.toString());
		assertTrue(notes.getFirst().message().contains("@Inject h targets m_1000_(Ljava/lang/String;)V, which this game build declares taking this"), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired).accept(node, 0);
		MethodNode bridge = node.methods.stream().filter(mn -> mn.name.equals("h$fabricated")).findFirst().orElseThrow();
		assertEquals(0, bridge.access & Opcodes.ACC_STATIC, "the bridge takes this");
		assertEquals(desc, bridge.desc);
		assertEquals(List.of("lambda$onPress$0", "m_1000_"), Annotations.value(Annotations.find(bridge, INJECT), "method"), "both selectors stay");
		List<Integer> loads = new ArrayList<>();
		for (AbstractInsnNode insn : bridge.instructions) {
			if (insn instanceof VarInsnNode v) loads.add(v.var);
		}
		assertEquals(List.of(1, 2), loads, "the handler gets the bridge's parameters, not this");
		MethodNode handler = node.methods.stream().filter(mn -> mn.name.equals("h")).findFirst().orElseThrow();
		assertNull(Annotations.find(handler, INJECT));
		assertNotNull(Annotations.find(handler, MixinNames.UNIQUE));
		defineMixin(repaired);
		mod.put("com/example/mixin/Captures", repaired);
		List<PreflightFinding> after = forge.apply(true, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());
	}

	/** A mixin {@code Captures} whose static {@code @Inject} handler {@code h}, of {@code desc}, names {@code lambda$onPress$0} and {@code m_1000_}. */
	private static byte[] twoSelectorMixin(String desc) {
		return mixin("com/example/mixin/Captures", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "h", desc, null, null);
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "lambda$onPress$0");
			methods.visit(null, "m_1000_");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
			at.visit("value", "HEAD");
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, Type.getArgumentTypes(desc).length);
			mv.visitEnd();
		});
	}

	@Test
	void aHandlerTakingWhatVanillasLambdaTakesGetsABridgeTakingTheSubtypeThisBuildsTakes() throws Exception {
		// LAMBDA-11: Forge's and NeoForge 21.1's KeyboardHandler.charTyped lambdas take the screen where vanilla's take a
		// GuiEventListener, which JEI's and owo-lib's handlers take, naming vanilla's lambda with its descriptor (JEI's
		// selector, owo's refmap).
		String screen = "net/minecraft/world/Screen";
		String listener = "net/minecraft/world/Listener";
		String vanillaDesc = "(L" + listener + ";II)V";
		String forgeDesc = "(L" + screen + ";II)V";
		Map<String, byte[]> classes = Map.of(TARGET, charTypedTarget(screen), listener, plainClass(listener, Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT),
				screen, plainClass(screen, 0, listener));
		ClassIndex game = new ClassIndex(classes::get);
		Map<String, byte[]> vanilla = Map.of(TARGET, charTypedTarget(listener), listener, classes.get(listener));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Captures"));
		Refmap refmap = Refmap.parse("{\"mappings\":{\"com/example/mixin/Captures\":{\"lambda$charTyped$6\":\"L" + TARGET + ";lambda$charTyped$6" + vanillaDesc + "\"}}}");
		List<Type> listened = List.of(Type.getObjectType(listener), Type.INT_TYPE, Type.INT_TYPE);
		// By vanilla's descriptor, inline or the refmap's, or by name with vanilla's code at hand, which tells what it takes.
		for (Map.Entry<String, Refmap> named : List.of(Map.entry("lambda$charTyped$6" + vanillaDesc, Refmap.EMPTY), Map.entry("lambda$charTyped$6", refmap),
				Map.entry("lambda$charTyped$6", Refmap.EMPTY))) {
			Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Captures", capturingMixin(named.getKey(), listened, 0)));
			List<PreflightFinding> notes = new ArrayList<>();
			byte[] repaired = new MixinPreflight(game, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
					.repairs("test.mixins.json", config, named.getValue(), Set.of(), notes).get("com/example/mixin/Captures.class");
			assertNotNull(repaired, named + ": " + notes);
			assertTrue(notes.getLast().message().contains("h targets lambda$charTyped$6" + forgeDesc + ", which this game build declares taking a subtype of"
					+ " what the handler takes (net.minecraft.world.Screen for net.minecraft.world.Listener)"), named + ": " + notes);
			ClassNode node = new ClassNode();
			new ClassReader(repaired).accept(node, 0);
			MethodNode bridge = node.methods.stream().filter(mn -> mn.name.equals("h$fabricated")).findFirst().orElseThrow();
			assertEquals(forgeDesc.replace(")V", CALLBACK_INFO + ")V"), bridge.desc);
			List<Integer> loads = new ArrayList<>();
			for (AbstractInsnNode insn : bridge.instructions) {
				if (insn instanceof VarInsnNode v) loads.add(v.var);
			}
			assertEquals(List.of(0, 1, 2, 3), loads, "the screen goes where the handler takes the listener");
			defineMixin("com.example.mixin.Captures", repaired, classes);
			mod.put("com/example/mixin/Captures", repaired);
			List<PreflightFinding> after = new MixinPreflight(game, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
					.check("test.mixins.json", config, named.getValue(), Set.of());
			assertTrue(after.isEmpty(), named + ": " + after);
		}

		// Fabric's Mixin takes a handler with vanilla's exact types only: one taking a supertype of the listener isn't bridged,
		// nor one by name where nothing tells what vanilla's takes.
		Map<String, byte[]> wider = new HashMap<>(Map.of("com/example/mixin/Captures", capturingMixin("lambda$charTyped$6" + vanillaDesc,
				List.of(Type.getType(Object.class), Type.INT_TYPE, Type.INT_TYPE), 0)));
		List<PreflightFinding> pointed = new ArrayList<>();
		wider.put("com/example/mixin/Captures", new MixinPreflight(game, InstalledClasses.NONE, wider::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), pointed).get("com/example/mixin/Captures.class"));
		assertEquals(1, pointed.size(), "only pointed at this build's lambda: " + pointed);
		List<PreflightFinding> refused = new MixinPreflight(game, InstalledClasses.NONE, wider::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(refused.stream().anyMatch(f -> f.message().contains("does not match the parameters of lambda$charTyped$6" + forgeDesc)), refused.toString());
		Map<String, byte[]> byName = Map.of("com/example/mixin/Captures", capturingMixin("lambda$charTyped$6", listened, 0));
		List<PreflightFinding> unknown = preflight(game, byName::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(unknown.stream().anyMatch(f -> f.message().contains("does not match the parameters of lambda$charTyped$6" + forgeDesc)), unknown.toString());
	}

	/**
	 * {@link #TARGET} with a static {@code charTyped(II)V} that makes a {@code Runnable} of its lambda
	 * {@code lambda$charTyped$6}, capturing a {@code listener} (by internal name) and the two values.
	 */
	private static byte[] charTypedTarget(String listener) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		String desc = "(L" + listener + ";II)V";
		Handle metafactory = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
				"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;"
						+ "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
		MethodVisitor typed = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "charTyped", "(II)V", null, null);
		typed.visitCode();
		typed.visitInsn(Opcodes.ACONST_NULL);
		typed.visitTypeInsn(Opcodes.CHECKCAST, listener);
		typed.visitVarInsn(Opcodes.ILOAD, 0);
		typed.visitVarInsn(Opcodes.ILOAD, 1);
		typed.visitInvokeDynamicInsn("run", "(L" + listener + ";II)Ljava/lang/Runnable;", metafactory, Type.getType("()V"),
				new Handle(Opcodes.H_INVOKESTATIC, TARGET, "lambda$charTyped$6", desc, false), Type.getType("()V"));
		typed.visitInsn(Opcodes.POP);
		typed.visitInsn(Opcodes.RETURN);
		typed.visitMaxs(0, 0);
		typed.visitEnd();
		MethodVisitor lambda = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, "lambda$charTyped$6", desc, null, null);
		lambda.visitCode();
		lambda.visitInsn(Opcodes.RETURN);
		lambda.visitMaxs(0, 3);
		lambda.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** An empty class (or interface, by {@code access}) {@code name}, implementing {@code interfaces}. */
	private static byte[] plainClass(String name, int access, String... interfaces) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | access, name, null, "java/lang/Object", interfaces);
		cw.visitEnd();
		return cw.toByteArray();
	}

	@Test
	void aStaticHandlerOnAReorderedLambdaThatTakesThisGetsABridgeWhereMixinTakesStaticHandlers() throws Exception {
		// LAMBDA-8: Forge 66's SimpleJsonResourceReloadListener.prepare lambda reads the listener's codec, so it takes this,
		// and captures the id before the map, where vanilla's is static and takes the map first. Fabric API's resource
		// conditions hook it with a static handler, which Forge's Mixin 0.8.7 takes on a method that takes this; the values
		// still need placing.
		Type map = Type.getType(Map.class);
		Type string = Type.getType(String.class);
		Type object = Type.getType(Object.class);
		String forgeDesc = Type.getMethodDescriptor(Type.VOID_TYPE, string, map, object);
		ClassIndex game = new ClassIndex(Map.of(TARGET, lambdaClass("lambda$prepare$0", forgeDesc, false, mv -> mv.visitInsn(Opcodes.RETURN)))::get);
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Captures"));
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Captures", capturingMixin("lambda$prepare$0", List.of(map, string, object), 2)));
		java.util.function.Supplier<MixinPreflight> forge = () -> new MixinPreflight(game, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false,
				true, ClassBytesSource.EMPTY, "Forge", true, MixinPreflight.ConstructorInjects.UNSAFE_POINTS, false, false);
		List<PreflightFinding> before = forge.get().check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(before.stream().anyMatch(f -> f.message().contains("does not match the parameters of lambda$prepare$0")), before.toString());

		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = forge.get().repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes).get("com/example/mixin/Captures.class");
		assertNotNull(repaired, notes.toString());
		assertTrue(notes.getFirst().message().contains("h targets lambda$prepare$0" + forgeDesc + ", whose captured values this game build passes in another order or with more, taking this"),
				notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired).accept(node, 0);
		MethodNode bridge = node.methods.stream().filter(mn -> mn.name.equals("h$fabricated")).findFirst().orElseThrow();
		assertEquals(0, bridge.access & Opcodes.ACC_STATIC, "the bridge takes this, as the lambda does");
		assertEquals(forgeDesc.replace(")V", CALLBACK_INFO + ")V"), bridge.desc);
		List<Integer> loads = new ArrayList<>();
		for (AbstractInsnNode insn : bridge.instructions) {
			if (insn instanceof VarInsnNode v) loads.add(v.var);
		}
		assertEquals(List.of(2, 1, 3, 4), loads, "the static handler gets the map, the id and the value, not this");
		defineMixin(repaired);
		mod.put("com/example/mixin/Captures", repaired);
		List<PreflightFinding> after = forge.get().check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());
	}

	@Test
	void aHookNamingALambdaWithVanillasDescriptorFindsTheOneThePatchChangedInItsPlace() throws Exception {
		// LAMBDA-8: Forge 61's SimpleJsonResourceReloadListener.scanDirectory lambda also takes the codec, which it reads,
		// and captures the id before the map; Fabric API's resource conditions hook names vanilla's lambda with vanilla's
		// descriptor (its refmap's), which no method of this build has.
		Type map = Type.getType(Map.class);
		Type string = Type.getType(String.class);
		Type list = Type.getType(List.class);
		Type object = Type.getType(Object.class);
		String vanillaDesc = Type.getMethodDescriptor(Type.VOID_TYPE, map, string, object);
		String forgeDesc = Type.getMethodDescriptor(Type.VOID_TYPE, list, string, map, object);
		Map<String, byte[]> vanilla = Map.of(TARGET, scanningTarget("lambda$scan$0", vanillaDesc));
		ClassIndex forge = new ClassIndex(Map.of(TARGET, scanningTarget("lambda$scan$0", forgeDesc))::get);
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Captures"));
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Captures", capturingMixin("lambda$scan$0" + vanillaDesc, List.of(map, string, object), 2)));
		List<PreflightFinding> before = preflight(forge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(before.stream().anyMatch(f -> f.message().contains("which does not exist")), before.toString());
		assertTrue(preflight(forge, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty(),
				"without vanilla's code, nothing tells which lambda it was");

		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = new MixinPreflight(forge, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes).get("com/example/mixin/Captures.class");
		assertNotNull(repaired, notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired).accept(node, 0);
		MethodNode bridge = node.methods.stream().filter(mn -> mn.name.equals("h$fabricated")).findFirst().orElseThrow();
		assertEquals(List.of("lambda$scan$0" + forgeDesc), Annotations.value(Annotations.find(bridge, INJECT), "method"));
		List<Integer> loads = new ArrayList<>();
		for (AbstractInsnNode insn : bridge.instructions) {
			if (insn instanceof VarInsnNode v) loads.add(v.var);
		}
		assertEquals(List.of(2, 1, 3, 4), loads, "the map, the id and the value, without the codec");
		defineMixin(repaired);
		mod.put("com/example/mixin/Captures", repaired);
		List<PreflightFinding> after = new MixinPreflight(forge, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());

		// Where this build's lambda in that place has another name, it may be another lambda: left to the other checks.
		ClassIndex renumbered = new ClassIndex(Map.of(TARGET, scanningTarget("lambda$scan$2", forgeDesc))::get);
		Map<String, byte[]> original = Map.of("com/example/mixin/Captures", capturingMixin("lambda$scan$0" + vanillaDesc, List.of(map, string, object), 2));
		assertTrue(new MixinPreflight(renumbered, InstalledClasses.NONE, original::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
	}

	@Test
	void aHookNamingALambdaWhoseCreatingMethodThePatchGaveAParameterFindsItInItsPlace() throws Exception {
		// LAMBDA-9: NeoForge 26.2's LevelRenderer.addMainPass also takes the model view matrix, and its lambda captures it, so
		// Litematica's hooks into vanilla's lambda (named with vanilla's descriptor) found neither the lambda nor the method
		// creating it: here vanilla's scan takes no list, this build's takes one and gives it to its lambda.
		Type map = Type.getType(Map.class);
		Type string = Type.getType(String.class);
		Type list = Type.getType(List.class);
		Type object = Type.getType(Object.class);
		String vanillaDesc = Type.getMethodDescriptor(Type.VOID_TYPE, map, string, object);
		String patchedDesc = Type.getMethodDescriptor(Type.VOID_TYPE, list, string, map, object);
		Map<String, byte[]> vanilla = Map.of(TARGET, scanningTarget("lambda$scan$0", vanillaDesc, "(Ljava/util/Map;Ljava/lang/String;)V"));
		ClassIndex game = new ClassIndex(Map.of(TARGET, scanningTarget("lambda$scan$0", patchedDesc))::get);
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Captures"));
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Captures", capturingMixin("lambda$scan$0" + vanillaDesc, List.of(map, string, object), 2)));
		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = new MixinPreflight(game, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes).get("com/example/mixin/Captures.class");
		assertNotNull(repaired, notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired).accept(node, 0);
		MethodNode bridge = node.methods.stream().filter(mn -> mn.name.equals("h$fabricated")).findFirst().orElseThrow();
		assertEquals(List.of("lambda$scan$0" + patchedDesc), Annotations.value(Annotations.find(bridge, INJECT), "method"));
		defineMixin(repaired);
		mod.put("com/example/mixin/Captures", repaired);
		List<PreflightFinding> after = new MixinPreflight(game, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());
	}

	@Test
	void aHookNamingALambdaFindsItInTheMethodThePatchKeepsVanillasOneToCall() throws Exception {
		// LAMBDA-9 as NeoForge 26.2 has it: LevelRenderer keeps an addMainPass with vanilla's descriptor beside the one
		// taking the model view matrix (two of that name), and the lambda is in the second. Here the kept scan(Map, String)
		// only calls scan(Map, String, List) with no list.
		Type map = Type.getType(Map.class);
		Type string = Type.getType(String.class);
		Type list = Type.getType(List.class);
		Type object = Type.getType(Object.class);
		String vanillaScan = "(Ljava/util/Map;Ljava/lang/String;)V";
		String vanillaDesc = Type.getMethodDescriptor(Type.VOID_TYPE, map, string, object);
		String patchedDesc = Type.getMethodDescriptor(Type.VOID_TYPE, list, string, map, object);
		Map<String, byte[]> vanilla = Map.of(TARGET, scanningTarget("lambda$scan$0", vanillaDesc, vanillaScan));
		ClassIndex game = new ClassIndex(Map.of(TARGET, scanningTarget("lambda$scan$0", patchedDesc, "(Ljava/util/Map;Ljava/lang/String;Ljava/util/List;)V",
				vanillaScan))::get);
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Captures"));
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Captures", capturingMixin("lambda$scan$0" + vanillaDesc, List.of(map, string, object), 2)));
		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = new MixinPreflight(game, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes).get("com/example/mixin/Captures.class");
		assertNotNull(repaired, notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired).accept(node, 0);
		MethodNode bridge = node.methods.stream().filter(mn -> mn.name.equals("h$fabricated")).findFirst().orElseThrow();
		assertEquals(List.of("lambda$scan$0" + patchedDesc), Annotations.value(Annotations.find(bridge, INJECT), "method"));
		mod.put("com/example/mixin/Captures", repaired);
		List<PreflightFinding> after = new MixinPreflight(game, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());
	}

	/**
	 * {@link #TARGET} whose static {@code scan(Map, String, List)} parses a value and hands it to two lambdas, as
	 * {@code SimpleJsonResourceReloadListener.scanDirectory} does: a consumer {@code first} of descriptor {@code desc},
	 * whose captured values are the first parameters of {@code desc} (taken from the method's in their order of type: the
	 * map, the id, the list), which puts the value in the map, and an error logger {@code lambda$scan$1(String, Object)}.
	 */
	private static byte[] scanningTarget(String first, String desc) {
		return scanningTarget(first, desc, "(Ljava/util/Map;Ljava/lang/String;Ljava/util/List;)V");
	}

	/** {@link #scanningTarget(String, String)} with {@code scan} of descriptor {@code scanDesc}, its parameters still the map, the id, the list. */
	private static byte[] scanningTarget(String first, String desc, String scanDesc) {
		return scanningTarget(first, desc, scanDesc, null);
	}

	/**
	 * {@link #scanningTarget(String, String, String)} with another {@code scan} of descriptor {@code keptDesc} (the map
	 * and the id) that only calls the first with no list, as a patch keeps vanilla's method beside the one it gave a
	 * parameter; none with null.
	 */
	private static byte[] scanningTarget(String first, String desc, String scanDesc, String keptDesc) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		if (keptDesc != null) {
			MethodVisitor kept = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "scan", keptDesc, null, null);
			kept.visitCode();
			kept.visitVarInsn(Opcodes.ALOAD, 0);
			kept.visitVarInsn(Opcodes.ALOAD, 1);
			kept.visitInsn(Opcodes.ACONST_NULL);
			kept.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET, "scan", scanDesc, false);
			kept.visitInsn(Opcodes.RETURN);
			kept.visitMaxs(0, 0);
			kept.visitEnd();
		}
		Handle metafactory = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
				"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;"
						+ "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
		Type consumed = Type.getType("(Ljava/lang/Object;)V");
		Type[] captured = Type.getArgumentTypes(desc);
		MethodVisitor scan = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "scan", scanDesc, null, null);
		scan.visitCode();
		scan.visitInsn(Opcodes.ACONST_NULL);
		for (int n = 0; n < captured.length - 1; n++) {
			String type = captured[n].getDescriptor();
			scan.visitVarInsn(Opcodes.ALOAD, type.equals("Ljava/util/Map;") ? 0 : type.equals("Ljava/lang/String;") ? 1 : 2);
		}
		String capturing = Type.getMethodDescriptor(Type.getType(java.util.function.Consumer.class), java.util.Arrays.copyOf(captured, captured.length - 1));
		scan.visitInvokeDynamicInsn("accept", capturing, metafactory, consumed, new Handle(Opcodes.H_INVOKESTATIC, TARGET, first, desc, false), consumed);
		scan.visitInsn(Opcodes.POP);
		scan.visitVarInsn(Opcodes.ALOAD, 1);
		scan.visitInvokeDynamicInsn("accept", "(Ljava/lang/String;)Ljava/util/function/Consumer;", metafactory, consumed,
				new Handle(Opcodes.H_INVOKESTATIC, TARGET, "lambda$scan$1", "(Ljava/lang/String;Ljava/lang/Object;)V", false), consumed);
		scan.visitInsn(Opcodes.POP);
		scan.visitInsn(Opcodes.POP);
		scan.visitInsn(Opcodes.RETURN);
		scan.visitMaxs(0, 0);
		scan.visitEnd();
		MethodVisitor body = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, first, desc, null, null);
		body.visitCode();
		int slot = 0;
		int mapSlot = -1;
		int idSlot = -1;
		for (Type t : captured) {
			if (t.getDescriptor().equals("Ljava/util/Map;")) mapSlot = slot;
			if (t.getDescriptor().equals("Ljava/lang/String;")) idSlot = slot;
			if (t.getDescriptor().equals("Ljava/util/List;")) {
				body.visitVarInsn(Opcodes.ALOAD, slot);
				body.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "size", "()I", true);
				body.visitInsn(Opcodes.POP);
			}
			slot++;
		}
		body.visitVarInsn(Opcodes.ALOAD, mapSlot);
		body.visitVarInsn(Opcodes.ALOAD, idSlot);
		body.visitVarInsn(Opcodes.ALOAD, captured.length - 1);
		body.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
		body.visitInsn(Opcodes.POP);
		body.visitInsn(Opcodes.RETURN);
		body.visitMaxs(0, 0);
		body.visitEnd();
		MethodVisitor error = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, "lambda$scan$1", "(Ljava/lang/String;Ljava/lang/Object;)V", null, null);
		error.visitCode();
		error.visitInsn(Opcodes.RETURN);
		error.visitMaxs(0, 0);
		error.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	@Test
	void capturedValuesOfOneTypeAreToldApartByHowVanillasLambdaUsesThem() throws Exception {
		// LAMBDA-7: Forge 47's KeyboardHandler.keyPress lambda takes the result array before the screen, where vanilla's
		// takes the screen first; the handler uses all four ints, which no order or type tells apart. Here this build's
		// lambda also takes the key after the scancode: only what each is used for places them.
		Type screen = Type.getType(String.class);
		Type flags = Type.getType(boolean[].class);
		Type i = Type.INT_TYPE;
		String vanillaDesc = Type.getMethodDescriptor(Type.VOID_TYPE, i, screen, flags, i, i, i);
		String forgeDesc = Type.getMethodDescriptor(Type.VOID_TYPE, i, flags, screen, i, i, i);
		ClassIndex game = new ClassIndex(Map.of(TARGET, lambdaClass("lambda$keyPress$5", forgeDesc, true, mv -> keyPressBody(mv, 1, 2, 4, 3, 5)))::get);
		Map<String, byte[]> vanilla = Map.of(TARGET, lambdaClass("m_260734_", vanillaDesc, true, mv -> keyPressBody(mv, 2, 1, 3, 4, 5)));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Captures"));
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Captures", mixin("com/example/mixin/Captures", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "h", vanillaDesc.replace(")V", CALLBACK_INFO + ")V"), null, null);
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "lambda$keyPress$5");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
			at.visit("value", "HEAD");
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			for (int slot : new int[] {0, 3, 4, 5}) {
				mv.visitVarInsn(Opcodes.ILOAD, slot);
				mv.visitInsn(Opcodes.POP);
			}
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(1, 7);
			mv.visitEnd();
		})));
		assertTrue(new MixinPreflight(game, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, ClassBytesSource.EMPTY)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty(), "without vanilla's lambda, not known");

		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = new MixinPreflight(game, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes).get("com/example/mixin/Captures.class");
		assertNotNull(repaired, notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired).accept(node, 0);
		MethodNode bridge = node.methods.stream().filter(mn -> mn.name.equals("h$fabricated")).findFirst().orElseThrow();
		assertEquals(forgeDesc.replace(")V", CALLBACK_INFO + ")V"), bridge.desc);
		List<Integer> loads = new ArrayList<>();
		for (AbstractInsnNode insn : bridge.instructions) {
			if (insn instanceof VarInsnNode v) loads.add(v.var);
		}
		assertEquals(List.of(0, 2, 1, 4, 3, 5, 6), loads, "the screen, the array, the key and the scancode where the handler takes them");
		defineMixin(repaired);
		mod.put("com/example/mixin/Captures", repaired);
		List<PreflightFinding> after = new MixinPreflight(game, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());
	}

	/**
	 * A key lambda's body as vanilla's and Forge 47's are: unless the action (slot 0) is 1, it stores into the array at
	 * {@code array} what {@code Hooks.keyPressed} answers for the screen at {@code screen} and the key, scancode and
	 * modifiers at the slots given.
	 */
	private static void keyPressBody(MethodVisitor mv, int array, int screen, int key, int scancode, int modifiers) {
		Label skip = new Label();
		mv.visitVarInsn(Opcodes.ILOAD, 0);
		mv.visitInsn(Opcodes.ICONST_1);
		mv.visitJumpInsn(Opcodes.IF_ICMPEQ, skip);
		mv.visitVarInsn(Opcodes.ALOAD, array);
		mv.visitInsn(Opcodes.ICONST_0);
		mv.visitVarInsn(Opcodes.ALOAD, screen);
		mv.visitVarInsn(Opcodes.ILOAD, key);
		mv.visitVarInsn(Opcodes.ILOAD, scancode);
		mv.visitVarInsn(Opcodes.ILOAD, modifiers);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/example/Hooks", "keyPressed", "(Ljava/lang/String;III)Z", false);
		mv.visitInsn(Opcodes.BASTORE);
		mv.visitLabel(skip);
		mv.visitInsn(Opcodes.RETURN);
	}

	/** {@link #TARGET} with a synthetic private method {@code name} of descriptor {@code desc}, static or taking {@code this}, and {@code body}. */
	private static byte[] lambdaClass(String name, String desc, boolean isStatic, Consumer<MethodVisitor> body) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC | (isStatic ? Opcodes.ACC_STATIC : 0), name, desc, null, null);
		mv.visitCode();
		body.accept(mv);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** Defines the repaired {@code com.example.mixin.Captures} in a class loader of its own, which verifies it. */
	private void defineMixin(byte[] bytes) throws Exception {
		defineMixin("com.example.mixin.Captures", bytes);
	}

	/** Defines the repaired mixin {@code className} in a class loader of its own, which verifies it. */
	private void defineMixin(String className, byte[] bytes) throws Exception {
		defineMixin(className, bytes, Map.of());
	}

	/** {@link #defineMixin(String, byte[])}, with the classes {@code others} has (by internal name) for the verifier. */
	private void defineMixin(String className, byte[] bytes, Map<String, byte[]> others) throws Exception {
		ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] other = others.get(name.replace('.', '/'));
				if (other != null) return defineClass(name, other, 0, other.length);
				if (!name.equals(className)) throw new ClassNotFoundException(name);
				return defineClass(name, bytes, 0, bytes.length);
			}
		};
		Class.forName(className, true, loader);
	}

	/** {@link #TARGET} whose static {@code main} calls the static {@code owner.name(false)}, of descriptor {@code desc}. */
	private static byte[] mainCalling(String owner, String name, String desc) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor main = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
		main.visitCode();
		main.visitInsn(Opcodes.ICONST_0);
		main.visitMethodInsn(Opcodes.INVOKESTATIC, owner, name, desc, false);
		main.visitInsn(Opcodes.RETURN);
		main.visitMaxs(0, 0);
		main.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	@Test
	void anInjectionAfterFabricLoadersServerHookIsPointedWhereNeoForgeLoadsMods() throws Exception {
		// HOOK-1: owo-lib's MainMixin, one of a group for Fabric's and Quilt's hooks; neither is in NeoForge's Main.
		String fabricHook = "Lnet/fabricmc/loader/impl/game/minecraft/Hooks;startServer(Ljava/io/File;Ljava/lang/Object;)V";
		String quiltHook = "Lorg/quiltmc/loader/impl/game/minecraft/Hooks;startServer(Ljava/io/File;Ljava/lang/Object;)V";
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/AfterHooks", mixin("com/example/mixin/AfterHooks", false, cw -> {
			for (String hook : List.of(fabricHook, quiltHook)) {
				MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, hook.contains("quilt") ? "afterQuilt" : "afterFabric",
						"(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null);
				AnnotationVisitor group = mv.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Group;", false);
				group.visit("name", "serverFreezeHooks");
				group.visit("min", 1);
				group.visit("max", 1);
				group.visitEnd();
				AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
				AnnotationVisitor methods = ann.visitArray("method");
				methods.visit(null, "main");
				methods.visitEnd();
				AnnotationVisitor ats = ann.visitArray("at");
				AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
				at.visit("value", "INVOKE");
				at.visit("remap", false);
				at.visit("target", hook);
				at.visitEnum("shift", "Lorg/spongepowered/asm/mixin/injection/At$Shift;", "AFTER");
				at.visitEnd();
				ats.visitEnd();
				ann.visitEnd();
				mv.visitEnd();
			}
		})));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "AfterHooks"));
		ClassIndex neoForge = new ClassIndex(Map.of(TARGET, mainCalling("net/neoforged/neoforge/server/loading/ServerModLoader", "load", "(Z)V"))::get);

		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight(neoForge, mod::get, n -> null, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		assertEquals(1, notes.size(), notes.toString());
		assertTrue(notes.getFirst().message().contains("afterFabric expects a call to net.fabricmc.loader.impl.game.minecraft.Hooks.startServer")
				&& notes.getFirst().message().contains("ServerModLoader.load"), notes.toString());
		mod.put("com/example/mixin/AfterHooks", repaired.get("com/example/mixin/AfterHooks.class"));
		List<PreflightFinding> findings = preflight(neoForge, mod::get, n -> null, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(findings.stream().noneMatch(f -> f.message().contains("fabricmc")), findings.toString());
		assertTrue(findings.stream().allMatch(f -> f.message().contains("quiltmc") && f.severity() == Severity.WARN), "Quilt's stays unmatched, as on Fabric: " + findings);

		ClassIndex elsewhere = new ClassIndex(Map.of(TARGET, mainCalling("com/example/Other", "load", "(Z)V"))::get);
		assertTrue(preflight(elsewhere, mod::get, n -> null, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty(), "only where NeoForge loads mods");
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
		atHelperCall(cw, injector, name, desc, helperDesc, -1, body);
	}

	/** The same, at the {@code ordinal}-th such call ({@code -1}: each). */
	private static void atHelperCall(ClassWriter cw, String injector, String name, String desc, String helperDesc, int ordinal, Consumer<MethodVisitor> body) {
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, name, desc, null, null);
		AnnotationVisitor ann = mv.visitAnnotation(injector, false);
		AnnotationVisitor methods = ann.visitArray("method");
		methods.visit(null, "tick");
		methods.visitEnd();
		boolean single = injector.equals(REDIRECT) || injector.endsWith("/ModifyArg;");
		AnnotationVisitor ats = single ? null : ann.visitArray("at");
		AnnotationVisitor at = single ? ann.visitAnnotation("at", AT) : ats.visitAnnotation(null, AT);
		at.visit("value", "INVOKE");
		at.visit("target", "L" + TARGET + ";helper" + helperDesc);
		if (ordinal >= 0) at.visit("ordinal", ordinal);
		at.visitEnd();
		if (ats != null) ats.visitEnd();
		ann.visitEnd();
		if (body != null) {
			mv.visitCode();
			body.accept(mv);
		}
		mv.visitEnd();
	}

	/** Game class: tick() calls helper with each of {@code descs} in turn, the i-th passing i (and null for the rest). */
	private static byte[] numberedHelperCalls(String... descs) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor tick = cw.visitMethod(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
		tick.visitCode();
		for (int i = 0; i < descs.length; i++) {
			tick.visitVarInsn(Opcodes.ALOAD, 0);
			tick.visitInsn(Opcodes.ICONST_0 + i);
			for (int k = 1; k < Type.getArgumentTypes(descs[i]).length; k++) tick.visitInsn(Opcodes.ACONST_NULL);
			tick.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "helper", descs[i], false);
		}
		tick.visitInsn(Opcodes.RETURN);
		tick.visitMaxs(0, 0);
		tick.visitEnd();
		for (String desc : new LinkedHashSet<>(List.of(descs))) {
			MethodVisitor helper = cw.visitMethod(Opcodes.ACC_PUBLIC, "helper", desc, null, null);
			helper.visitCode();
			helper.visitInsn(Opcodes.RETURN);
			helper.visitMaxs(0, 0);
			helper.visitEnd();
		}
		cw.visitEnd();
		return cw.toByteArray();
	}

	@Test
	void injectionPointsPickedByOrdinalAreReadAgainstVanillasCode() throws Exception {
		// VANILLA-1: as NeoForge's Camera.alignWithEntity, where vanilla calls setRotation(FF) four times and NeoForge's
		// patch made the second and third calls of setRotation(FFF), passing a roll: Do a Barrel Roll's hooks pick the
		// calls by ordinal. Without vanilla's code, the second hook lands on vanilla's fourth call and the last two fail.
		String thing = "L" + TARGET + ";";
		String overload = "(ILjava/lang/String;)V";
		Map<String, byte[]> vanilla = Map.of(TARGET, numberedHelperCalls("(I)V", "(I)V", "(I)V", "(I)V"));
		ClassIndex neoForge = new ClassIndex(Map.of(TARGET, numberedHelperCalls("(I)V", overload, overload, "(I)V"))::get);
		byte[] original = mixin("com/example/mixin/Ordinals", false, cw -> {
			atHelperCall(cw, WRAP_OPERATION, "first", "(" + thing + "I" + OPERATION + ")V", "(I)V", 0, null);
			atHelperCall(cw, "Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;", "second", "(" + thing + "I)Z", "(I)V", 1, null);
			atHelperCall(cw, INJECT, "third", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", "(I)V", 2, null);
			atHelperCall(cw, REDIRECT, "fourth", "(" + thing + "I)V", "(I)V", 3, null);
		});
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Ordinals", original));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Ordinals"));
		assertEquals(2, preflight(neoForge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).size());

		List<PreflightFinding> notes = new ArrayList<>();
		MixinPreflight withVanilla = new MixinPreflight(neoForge, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, vanilla::get);
		Map<String, byte[]> repaired = withVanilla.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);

		assertEquals(3, notes.size(), notes.toString());
		assertTrue(notes.stream().allMatch(n -> n.message().contains("in vanilla's code")), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get("com/example/mixin/Ordinals.class")).accept(node, 0);
		Map<String, AnnotationNode> points = new HashMap<>();
		for (MethodNode m : node.methods) {
			for (AnnotationNode ann : Annotations.all(m)) {
				List<AnnotationNode> ats = Annotations.list(Annotations.value(ann, "at"));
				if (!ats.isEmpty()) points.put(m.name, ats.getFirst());
			}
		}
		assertEquals(4, points.size(), points.keySet().toString());
		assertFalse(points.containsKey("second"), "the condition's injector moved to a bridge: " + points.keySet());
		assertEquals(0, Annotations.value(points.get("first"), "ordinal"));
		AnnotationNode bridged = points.entrySet().stream().filter(e -> !List.of("first", "third", "fourth", "second").contains(e.getKey()))
				.findFirst().orElseThrow().getValue();
		assertEquals("L" + TARGET + ";helper" + overload, Annotations.value(bridged, "target"), "vanilla's second call is the overload's first");
		assertEquals(0, Annotations.value(bridged, "ordinal"));
		assertEquals("L" + TARGET + ";helper" + overload, Annotations.value(points.get("third"), "target"));
		assertEquals(1, Annotations.value(points.get("third"), "ordinal"), "vanilla's third call is the overload's second");
		assertEquals("L" + TARGET + ";helper(I)V", Annotations.value(points.get("fourth"), "target"));
		assertEquals(1, Annotations.value(points.get("fourth"), "ordinal"), "vanilla's fourth call is this game build's second");
		Map<String, byte[]> after = new HashMap<>(mod);
		repaired.forEach((path, data) -> after.put(path.substring(0, path.length() - ".class".length()), data));
		assertEquals(List.of(), preflight(neoForge, after::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()),
				"each hook applies where vanilla's call went");
	}

	@Test
	void anInjectionAtEveryCallVanillaMakesOnceIsPinnedToItWhereThePatchAddsOneOnTheSamePath() throws Exception {
		// VANILLA-4: as Forge 52's Gui constructor, which reads layers where vanilla does and again to resolve Forge's
		// layers: Apoli's @Inject at every read ran twice. A call the patch adds behind a branch, which a pass may skip,
		// and a hook other than an @Inject, are left as they are.
		Map<String, byte[]> vanilla = Map.of(TARGET, helperCallAddedLater(false, false));
		for (boolean branch : new boolean[] {false, true}) {
			ClassIndex forge = new ClassIndex(Map.of(TARGET, helperCallAddedLater(true, branch))::get);
			Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Every", mixin("com/example/mixin/Every", false, cw -> {
				atHelperCall(cw, INJECT, "hook", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null);
				atHelperCall(cw, REDIRECT, "redirect", "(L" + TARGET + ";I)V", null);
			})));
			MixinConfigFile config = MixinConfigFile.parse(config(true, "Every"));
			List<PreflightFinding> notes = new ArrayList<>();
			Map<String, byte[]> repaired = new MixinPreflight(forge, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
					.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);

			if (branch) {
				assertEquals(List.of(), notes, "a pass may skip the added call");
				continue;
			}
			assertEquals(1, notes.size(), notes.toString());
			assertTrue(notes.get(0).message().contains("@Inject hook") && notes.get(0).message().contains("pinned to vanilla's (ordinal 0 here)"), notes.toString());
			ClassNode node = new ClassNode();
			new ClassReader(repaired.get("com/example/mixin/Every.class")).accept(node, 0);
			Map<String, AnnotationNode> points = new HashMap<>();
			for (MethodNode m : node.methods) {
				for (AnnotationNode ann : Annotations.all(m)) {
					Object at = Annotations.value(ann, "at");
					if (at != null) points.put(m.name, at instanceof AnnotationNode one ? one : Annotations.list(at).getFirst());
				}
			}
			assertEquals(0, Annotations.value(points.get("hook"), "ordinal"));
			assertNull(Annotations.value(points.get("redirect"), "ordinal"), "a redirect takes the patch's call as well");
		}
	}

	/**
	 * Game class: {@code tick()} calls {@code helper(0)} and {@code done()}; with {@code patched} it calls {@code helper(5)}
	 * after them, as a patch adds a call at a method's end (behind a jump over it with {@code branch}).
	 */
	private static byte[] helperCallAddedLater(boolean patched, boolean branch) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor tick = cw.visitMethod(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
		tick.visitCode();
		tick.visitVarInsn(Opcodes.ALOAD, 0);
		tick.visitInsn(Opcodes.ICONST_0);
		tick.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "helper", "(I)V", false);
		tick.visitVarInsn(Opcodes.ALOAD, 0);
		tick.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "done", "()V", false);
		Label end = new Label();
		if (patched) {
			if (branch) {
				tick.visitInsn(Opcodes.ICONST_1);
				tick.visitJumpInsn(Opcodes.IFEQ, end);
			}
			tick.visitVarInsn(Opcodes.ALOAD, 0);
			tick.visitInsn(Opcodes.ICONST_5);
			tick.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "helper", "(I)V", false);
		}
		tick.visitLabel(end);
		tick.visitInsn(Opcodes.RETURN);
		tick.visitMaxs(0, 0);
		tick.visitEnd();
		for (String name : List.of("helper(I)V", "done()V")) {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, name.substring(0, name.indexOf('(')), name.substring(name.indexOf('(')), null, null);
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * Game class: {@code tick()} as Camera.setup's mirrored rotation, {@code helper(-x)} in vanilla and, with {@code patched},
	 * {@code helper(-x, -roll)} as NeoForge 21.1's patch made it, between statements the two differ in and before one
	 * starting with a load of {@code this} ({@code done()}); {@code helper(F)} forwards to {@code helper(FF)} with a 0 roll.
	 */
	private static byte[] mirroredHelperCall(boolean patched) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		cw.visitField(Opcodes.ACC_PRIVATE, "x", "F", null, null).visitEnd();
		cw.visitField(Opcodes.ACC_PRIVATE, "roll", "F", null, null).visitEnd();
		MethodVisitor tick = cw.visitMethod(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
		tick.visitCode();
		tick.visitInsn(patched ? Opcodes.ICONST_3 : Opcodes.ICONST_1);
		tick.visitInsn(Opcodes.POP);
		tick.visitVarInsn(Opcodes.ALOAD, 0);
		tick.visitVarInsn(Opcodes.ALOAD, 0);
		tick.visitFieldInsn(Opcodes.GETFIELD, TARGET, "x", "F");
		tick.visitInsn(Opcodes.FNEG);
		if (patched) {
			tick.visitVarInsn(Opcodes.ALOAD, 0);
			tick.visitFieldInsn(Opcodes.GETFIELD, TARGET, "roll", "F");
			tick.visitInsn(Opcodes.FNEG);
		}
		tick.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "helper", patched ? "(FF)V" : "(F)V", false);
		tick.visitVarInsn(Opcodes.ALOAD, 0);
		tick.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "done", "()V", false);
		// A later call of helper(F) both keep, as the bed's setRotation(FF): the hook's ordinal 0 there would be this one.
		tick.visitVarInsn(Opcodes.ALOAD, 0);
		tick.visitInsn(Opcodes.FCONST_1);
		tick.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "helper", "(F)V", false);
		tick.visitInsn(patched ? Opcodes.ICONST_4 : Opcodes.ICONST_2);
		tick.visitInsn(Opcodes.POP);
		tick.visitInsn(Opcodes.RETURN);
		tick.visitMaxs(0, 0);
		tick.visitEnd();
		MethodVisitor one = cw.visitMethod(Opcodes.ACC_PUBLIC, "helper", "(F)V", null, null);
		one.visitCode();
		if (patched) {
			one.visitVarInsn(Opcodes.ALOAD, 0);
			one.visitVarInsn(Opcodes.FLOAD, 1);
			one.visitInsn(Opcodes.FCONST_0);
			one.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "helper", "(FF)V", false);
		}
		one.visitInsn(Opcodes.RETURN);
		one.visitMaxs(0, 0);
		one.visitEnd();
		for (String name : patched ? List.of("helper(FF)V", "done()V") : List.of("done()V")) {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, name.substring(0, name.indexOf('(')), name.substring(name.indexOf('(')), null, null);
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		cw.visitEnd();
		return cw.toByteArray();
	}

	@Test
	void aCallThePatchGaveAnArgumentBetweenInstructionsItRepeatsIsFoundAgainstVanillasCode() throws Exception {
		// Do a Barrel Roll's hook at Camera.setup's mirrored setRotation(FF) on NeoForge 21.1, which became setRotation(FFF):
		// the patch repeats the FNEG before the call and the ALOAD after it, so the two alignments place nothing alike
		// between them, and the hook was left out (at ordinal 0, as Do a Barrel Roll's main roll hook, it took the bed's
		// setRotation(FF) instead, which the patch keeps).
		String thing = "L" + TARGET + ";";
		Map<String, byte[]> vanilla = Map.of(TARGET, mirroredHelperCall(false));
		ClassIndex neoForge = new ClassIndex(Map.of(TARGET, mirroredHelperCall(true))::get);
		byte[] original = mixin("com/example/mixin/Mirrored", false, cw -> atHelperCall(cw, "Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;",
				"roll", "(" + thing + "F)Z", "(F)V", 0, null));
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Mirrored", original));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Mirrored"));

		List<PreflightFinding> notes = new ArrayList<>();
		MixinPreflight withVanilla = new MixinPreflight(neoForge, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, vanilla::get);
		Map<String, byte[]> repaired = withVanilla.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);

		assertEquals(1, notes.size(), notes.toString());
		assertTrue(notes.getFirst().message().contains("replaced with a call of helper(FF)V"), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get("com/example/mixin/Mirrored.class")).accept(node, 0);
		AnnotationNode point = node.methods.stream().flatMap(m -> Annotations.all(m).stream())
				.map(ann -> Annotations.list(Annotations.value(ann, "at"))).filter(ats -> !ats.isEmpty()).map(List::getFirst).findFirst().orElseThrow();
		assertEquals("L" + TARGET + ";helper(FF)V", Annotations.value(point, "target"));
		assertEquals(0, Annotations.value(point, "ordinal"));
		Map<String, byte[]> after = new HashMap<>(mod);
		repaired.forEach((path, data) -> after.put(path.substring(0, path.length() - ".class".length()), data));
		assertEquals(List.of(), preflight(neoForge, after::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()));
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

		assertTrue(preflight(new ClassIndex(Map.of(TARGET, callingHelper("(I)V", "(ILjava/lang/String;)V"))::get), Map.of("com/example/mixin/AtHelper", original)::get,
				ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty(), "the named call still made");
		for (byte[] game : List.of(callingHelper("(II)V"), callingHelper("(ILjava/lang/String;Z)V"))) {
			// The handlers taking the arguments can't be bridged (an added argument that can't be told apart, or two added);
			// the @Inject's point moves to the overload all the same (PRE-13): it sees none of them.
			List<PreflightFinding> moved = new ArrayList<>();
			preflight(new ClassIndex(Map.of(TARGET, game)::get), Map.of("com/example/mixin/AtHelper", original)::get, ClassBytesSource.EMPTY, false)
					.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), moved);
			assertEquals(1, moved.size(), moved.toString());
			assertTrue(moved.getFirst().message().startsWith("@Inject inject expects a call to"), moved.toString());
		}
	}

	/**
	 * One more float among floats can't be told by types: NeoForge's patched {@code Camera} keeps {@code setRotation(FF)},
	 * forwarding {@code (yRot, xRot, 0F)} to {@code setRotation(FFF)}, which says the roll is the added one (VANILLA-1, Do a
	 * Barrel Roll's camera hooks).
	 */
	@Test
	void anAddedArgumentOfTheSameTypeIsToldByTheMethodForwardingToTheOverload() {
		ClassNode camera = new ClassNode();
		camera.name = TARGET;
		camera.methods.add(forwarder(Opcodes.ALOAD, 0, Opcodes.FLOAD, 1, Opcodes.FLOAD, 2, Opcodes.FCONST_0, -1));
		assertEquals(-1, CallRepairs.insertedArgument("(FF)V", "(FFF)V"));
		assertEquals(2, CallRepairs.insertedArgument(camera, "setRotation", "(FF)V", "(FFF)V"));

		camera.methods.set(0, forwarder(Opcodes.ALOAD, 0, Opcodes.FCONST_0, -1, Opcodes.FLOAD, 1, Opcodes.FLOAD, 2));
		assertEquals(0, CallRepairs.insertedArgument(camera, "setRotation", "(FF)V", "(FFF)V"), "the default first");
		camera.methods.set(0, forwarder(Opcodes.ALOAD, 0, Opcodes.FLOAD, 2, Opcodes.FLOAD, 1, Opcodes.FCONST_0, -1));
		assertEquals(-1, CallRepairs.insertedArgument(camera, "setRotation", "(FF)V", "(FFF)V"), "its parameters out of order");
		assertEquals(-1, CallRepairs.insertedArgument(new ClassNode(), "setRotation", "(FF)V", "(FFF)V"), "no such method");
	}

	/** {@code Camera.setRotation(FF)} forwarding to {@code setRotation(FFF)} with these (opcode, operand) pushes; -1: none. */
	private static MethodNode forwarder(int... pushes) {
		MethodNode method = new MethodNode(Opcodes.ACC_PROTECTED, "setRotation", "(FF)V", null, null);
		for (int i = 0; i < pushes.length; i += 2) {
			method.instructions.add(pushes[i + 1] < 0 ? new InsnNode(pushes[i]) : new VarInsnNode(pushes[i], pushes[i + 1]));
		}
		method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "setRotation", "(FFF)V"));
		method.instructions.add(new InsnNode(Opcodes.RETURN));
		return method;
	}

	/** MixinExtras reads an expression point's {@code @Expression} and {@code @Definition}s from the injector's method. */
	@Test
	void aBridgedHandlersExpressionMovesWithItsInjector() {
		MethodNode handler = new MethodNode(Opcodes.ACC_PRIVATE, "onTick", "(" + CALLBACK_INFO + ")V", null, null);
		AnnotationNode inject = new AnnotationNode(INJECT);
		AnnotationNode expression = new AnnotationNode("Lcom/llamalad7/mixinextras/expression/Expression;");
		AnnotationNode definition = new AnnotationNode("Lcom/llamalad7/mixinextras/expression/Definition;");
		handler.invisibleAnnotations = new ArrayList<>(List.of(inject, expression, definition));
		MethodNode bridge = new MethodNode(Opcodes.ACC_PRIVATE, "onTick$fabricated", handler.desc, null, null);

		HandlerBridges.moveInjector(handler, inject, bridge);

		assertEquals(List.of(inject, expression, definition), bridge.invisibleAnnotations);
		assertTrue(handler.invisibleAnnotations.isEmpty(), handler.invisibleAnnotations.toString());
	}

	/**
	 * Whether a handler shares the mixin's state with its other injectors is judged on the mixin as the mod wrote it: a
	 * repair moving an earlier handler's injector to a bridge doesn't make a later handler sharing its field independent.
	 * Here the condition at {@code helper(I)V} moves to the overload NeoForge calls instead, and the {@code @Inject} at
	 * {@code other(I)V}, a call NeoForge moved into its hook, reads what the condition keeps: it stays.
	 */
	@Test
	void aHandlerSharingStateWithOneAnEarlierRepairBridgedStays() throws Exception {
		String thing = "L" + TARGET + ";";
		ClassWriter target = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		target.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor tick = target.visitMethod(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
		tick.visitCode();
		tick.visitVarInsn(Opcodes.ALOAD, 0);
		tick.visitInsn(Opcodes.ICONST_0);
		tick.visitInsn(Opcodes.ACONST_NULL);
		tick.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "helper", "(ILjava/lang/String;)V", false);
		tick.visitVarInsn(Opcodes.ALOAD, 0);
		tick.visitInsn(Opcodes.ICONST_0);
		tick.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "draw", "(" + thing + "I)V", false);
		tick.visitInsn(Opcodes.RETURN);
		tick.visitMaxs(0, 0);
		tick.visitEnd();
		for (String[] member : new String[][] {{"helper", "(ILjava/lang/String;)V"}, {"other", "(I)V"}}) {
			MethodVisitor mv = target.visitMethod(Opcodes.ACC_PUBLIC, member[0], member[1], null, null);
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		target.visitEnd();
		ClassWriter hooks = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		hooks.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, HOOKS, null, "java/lang/Object", null);
		MethodVisitor draw = hooks.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "draw", "(" + thing + "I)V", null, null);
		draw.visitCode();
		draw.visitVarInsn(Opcodes.ALOAD, 0);
		draw.visitVarInsn(Opcodes.ILOAD, 1);
		draw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "other", "(I)V", false);
		draw.visitInsn(Opcodes.RETURN);
		draw.visitMaxs(0, 0);
		draw.visitEnd();
		hooks.visitEnd();
		ClassIndex neoForge = new ClassIndex(Map.of(TARGET, target.toByteArray(), HOOKS, hooks.toByteArray())::get);

		byte[] mixin = mixin("com/example/mixin/Shared", false, cw -> {
			cw.visitField(Opcodes.ACC_PRIVATE, "context", "Ljava/lang/Object;", null, null).visitEnd();
			atHelperCall(cw, "Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;", "keep", "(" + thing + "I)Z", mv -> {
				mv.visitVarInsn(Opcodes.ALOAD, 0);
				mv.visitInsn(Opcodes.ACONST_NULL);
				mv.visitFieldInsn(Opcodes.PUTFIELD, "com/example/mixin/Shared", "context", "Ljava/lang/Object;");
				mv.visitInsn(Opcodes.ICONST_1);
				mv.visitInsn(Opcodes.IRETURN);
				mv.visitMaxs(2, 3);
				mv.visitEnd();
			});
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "read", "(" + CALLBACK_INFO + ")V", null, null);
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "tick");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", "INVOKE");
			at.visit("target", "L" + TARGET + ";other(I)V");
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitFieldInsn(Opcodes.GETFIELD, "com/example/mixin/Shared", "context", "Ljava/lang/Object;");
			mv.visitInsn(Opcodes.POP);
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(1, 2);
			mv.visitEnd();
		});
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Shared"));
		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight(neoForge, Map.of("com/example/mixin/Shared", mixin)::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);

		assertEquals(1, notes.size(), notes.toString());
		assertTrue(notes.getFirst().message().contains("helper"), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get("com/example/mixin/Shared.class")).accept(node, 0);
		MethodNode read = node.methods.stream().filter(m -> m.name.equals("read")).findFirst().orElseThrow();
		AnnotationNode point = Annotations.list(Annotations.value(Annotations.find(read, INJECT), "at")).getFirst();
		assertEquals("L" + TARGET + ";other(I)V", Annotations.value(point, "target"), "the hook's call isn't taken for it");
	}

	@Test
	void anInjectionAtACallNeoForgeMovedIntoAHookIsPointedAtTheHook() throws Exception {
		// HOOK-2: architectury, FancyMenu and Inventory Profiles Next hook the game's call rendering the screen, which
		// NeoForge's patches make inside ClientHooks.drawScreen(screen, ...) (26.2 on: extractScreen, taking one more).
		String thing = "L" + TARGET + ";";
		Consumer<MethodVisitor> returns = mv -> {
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 6);
			mv.visitEnd();
		};
		byte[] original = mixin("com/example/mixin/AtHelper", false, cw -> {
			atHelperCall(cw, INJECT, "inject", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", returns);
			atHelperCall(cw, REDIRECT, "redirect", "(" + thing + "I)V", returns);
			atHelperCall(cw, WRAP_OPERATION, "wrap", "(" + thing + "I" + OPERATION + ")V", returns);
			atHelperCall(cw, "Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;", "condition", "(" + thing + "I)Z", mv -> {
				mv.visitInsn(Opcodes.ICONST_1);
				mv.visitInsn(Opcodes.IRETURN);
				mv.visitMaxs(1, 3);
				mv.visitEnd();
			});
		});
		MixinConfigFile config = MixinConfigFile.parse(config(true, "AtHelper"));
		String withLayers = "(" + thing + "Ljava/lang/String;I)V";
		ClassIndex neoForge = new ClassIndex(Map.of(TARGET, callingHook(withLayers), HOOKS, hooks(withLayers, true))::get);

		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/AtHelper", original));
		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight(neoForge, mod::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		assertEquals(4, notes.size(), notes.toString());
		assertTrue(notes.stream().allMatch(n -> n.message().contains("inside NeoForge's hook net.neoforged.Hooks.draw" + withLayers)), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get("com/example/mixin/AtHelper.class")).accept(node, 0);
		Map<String, MethodNode> methods = new HashMap<>();
		node.methods.forEach(m -> methods.put(m.name, m));
		assertEquals("L" + HOOKS + ";draw" + withLayers,
				Annotations.value((AnnotationNode) ((List<?>) Annotations.value(methods.get("inject").invisibleAnnotations.getFirst(), "at")).getFirst(), "target"));
		assertEquals(withLayers, methods.get("redirect$fabricated").desc, "the bridge takes the hook's arguments");
		assertEquals("(" + thing + "Ljava/lang/String;I" + OPERATION + ")V", methods.get("wrap$fabricated").desc);
		mod.put("com/example/mixin/AtHelper", repaired.get("com/example/mixin/AtHelper.class"));
		List<PreflightFinding> after = preflight(neoForge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());

		// A hook taking just the call's receiver and arguments (1.21.11 to 26.1) takes the handlers as they are.
		String plain = "(" + thing + "I)V";
		ClassIndex older = new ClassIndex(Map.of(TARGET, callingHook(plain), HOOKS, hooks(plain, true))::get);
		Map<String, byte[]> mod2 = new HashMap<>(Map.of("com/example/mixin/AtHelper", original));
		List<PreflightFinding> moved = new ArrayList<>();
		byte[] retargeted = preflight(older, mod2::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), moved).get("com/example/mixin/AtHelper.class");
		assertEquals(4, moved.size(), moved.toString());
		ClassNode plainNode = new ClassNode();
		new ClassReader(retargeted).accept(plainNode, 0);
		assertTrue(plainNode.methods.stream().noneMatch(m -> m.name.contains("$fabricated")), "no bridge");
		mod2.put("com/example/mixin/AtHelper", retargeted);
		assertTrue(preflight(older, mod2::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());

		// A handler sharing the mixin's own state with another injector stays, as jGui's (ReplayMod) @Inject after the
		// screen render: it reads what its @ModifyArg at the call keeps, which can't move with it.
		Consumer<MethodVisitor> readsContext = mv -> {
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitFieldInsn(Opcodes.GETFIELD, "com/example/mixin/AtHelper", "context", "Ljava/lang/Object;");
			mv.visitInsn(Opcodes.POP);
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(1, 2);
			mv.visitEnd();
		};
		byte[] stateful = mixin("com/example/mixin/AtHelper", false, cw -> {
			cw.visitField(Opcodes.ACC_PRIVATE, "context", "Ljava/lang/Object;", null, null).visitEnd();
			atHelperCall(cw, INJECT, "inject", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", readsContext);
			atHelperCall(cw, "Lorg/spongepowered/asm/mixin/injection/ModifyArg;", "keep", "(I)I", mv -> {
				mv.visitVarInsn(Opcodes.ALOAD, 0);
				mv.visitInsn(Opcodes.ACONST_NULL);
				mv.visitFieldInsn(Opcodes.PUTFIELD, "com/example/mixin/AtHelper", "context", "Ljava/lang/Object;");
				mv.visitVarInsn(Opcodes.ILOAD, 1);
				mv.visitInsn(Opcodes.IRETURN);
				mv.visitMaxs(2, 2);
				mv.visitEnd();
			});
		});
		assertTrue(preflight(older, Map.of("com/example/mixin/AtHelper", stateful)::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
		// State it shares with the mixin's other members alone (a duck interface's method sets it) moves with it (SPLIT-2).
		byte[] ownState = mixin("com/example/mixin/AtHelper", false, cw -> {
			cw.visitField(Opcodes.ACC_PRIVATE, "context", "Ljava/lang/Object;", null, null).visitEnd();
			atHelperCall(cw, INJECT, "inject", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", readsContext);
			MethodVisitor attach = cw.visitMethod(Opcodes.ACC_PUBLIC, "attach", "(Ljava/lang/Object;)V", null, null);
			attach.visitCode();
			attach.visitVarInsn(Opcodes.ALOAD, 0);
			attach.visitVarInsn(Opcodes.ALOAD, 1);
			attach.visitFieldInsn(Opcodes.PUTFIELD, "com/example/mixin/AtHelper", "context", "Ljava/lang/Object;");
			attach.visitInsn(Opcodes.RETURN);
			attach.visitMaxs(2, 2);
			attach.visitEnd();
		});
		List<PreflightFinding> alone = new ArrayList<>();
		preflight(older, Map.of("com/example/mixin/AtHelper", ownState)::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), alone);
		assertEquals(1, alone.size(), alone.toString());

		// A static method of that shape that doesn't make the call isn't taken for the hook.
		ClassIndex unrelated = new ClassIndex(Map.of(TARGET, callingHook(plain), HOOKS, hooks(plain, false))::get);
		List<PreflightFinding> none = new ArrayList<>();
		assertTrue(preflight(unrelated, Map.of("com/example/mixin/AtHelper", original)::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), none).isEmpty(), none.toString());
	}

	@Test
	void aHookAtASpawnCallNeoForgesTransformerPointsAtItsEventHookMovesToTheHook() throws Exception {
		// CORPUS-61: NeoForge's code transformer points each finalizeSpawn call in NaturalSpawner, PatrolSpawner and the other
		// listed classes at EventHooks.finalizeMobSpawn as they load, before Mixin applies a mod's mixins: Carpet's @Redirect of
		// the call in NaturalSpawner, and Friends & Foes' @Inject at a PatrollingMonster's in PatrolSpawner, found nothing there.
		String mob = PlatformCoremodsTest.MOB;
		String hooks = PlatformCoremodsTest.HOOKS;
		String finalize = PlatformCoremodsTest.FINALIZE;
		String hookDesc = PlatformCoremodsTest.FINALIZE_HOOK;
		byte[] carpet = spawnerMixin("carpet/mixins/NaturalSpawnerMixin", PlatformCoremodsTest.SPAWNER, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "spawnEntity", hookDesc, null, null);
			AnnotationVisitor ann = mv.visitAnnotation(REDIRECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "spawnCategoryForPosition");
			methods.visitEnd();
			AnnotationVisitor at = ann.visitAnnotation("at", AT);
			at.visit("value", "INVOKE");
			at.visit("target", "L" + mob + ";finalizeSpawn" + finalize);
			at.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			for (int i = 0; i < 5; i++) mv.visitVarInsn(Opcodes.ALOAD, i);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, mob, "finalizeSpawn", finalize, false);
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitMaxs(5, 5);
			mv.visitEnd();
		});
		byte[] friendsAndFoes = spawnerMixin("com/faboslav/friendsandfoes/mixin/PatrolSpawnerMixin", PlatformCoremodsTest.PATROLS, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "onSpawnEntity",
					"(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V", null, null);
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "spawnPatrolMember");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", "INVOKE");
			at.visit("target", "L" + PlatformCoremodsTest.PATROLLING + ";finalizeSpawn" + finalize);
			at.visitEnd();
			ats.visitEnd();
			ann.visit("cancellable", true);
			ann.visitEnd();
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 1);
			mv.visitEnd();
		});
		String carpetConfig = "{\"required\":true,\"package\":\"carpet.mixins\",\"mixins\":[\"NaturalSpawnerMixin\"]}";
		String fnfConfig = "{\"required\":true,\"package\":\"com.faboslav.friendsandfoes.mixin\",\"mixins\":[\"PatrolSpawnerMixin\"]}";
		Map<String, byte[]> jars = PlatformCoremodsTest.game();
		Map<String, byte[]> mod = new HashMap<>(Map.of("carpet/mixins/NaturalSpawnerMixin", carpet,
				"com/faboslav/friendsandfoes/mixin/PatrolSpawnerMixin", friendsAndFoes));

		// The game as its jars have it makes both calls: pre-flight passed both, and Mixin then failed them.
		ClassIndex asJars = new ClassIndex(jars::get);
		assertTrue(preflight(asJars, mod::get, ClassBytesSource.EMPTY, false).check("carpet.mixins.json", MixinConfigFile.parse(carpetConfig), Refmap.EMPTY, Set.of()).isEmpty());
		// As NeoForge's transformer leaves it, it makes neither, and moves both to its hook's call.
		ClassIndex asLoaded = new ClassIndex(PlatformCoremods.neoForge("1.21.1").over(jars::get));
		for (String[] each : List.of(new String[] {"carpet.mixins.json", carpetConfig, "carpet/mixins/NaturalSpawnerMixin"},
				new String[] {"friendsandfoes.mixins.json", fnfConfig, "com/faboslav/friendsandfoes/mixin/PatrolSpawnerMixin"})) {
			MixinConfigFile config = MixinConfigFile.parse(each[1]);
			assertFalse(preflight(asLoaded, mod::get, ClassBytesSource.EMPTY, false).check(each[0], config, Refmap.EMPTY, Set.of()).isEmpty(), each[2]);
			List<PreflightFinding> notes = new ArrayList<>();
			Map<String, byte[]> repaired = preflight(asLoaded, mod::get, ClassBytesSource.EMPTY, false).repairs(each[0], config, Refmap.EMPTY, Set.of(), notes);
			assertEquals(1, notes.size(), notes.toString());
			assertTrue(notes.getFirst().message().contains("inside NeoForge's hook " + hooks.replace('/', '.') + ".finalizeMobSpawn" + hookDesc), notes.toString());
			ClassNode node = new ClassNode();
			new ClassReader(repaired.get(each[2] + ".class")).accept(node, 0);
			MethodNode handler = node.methods.stream().filter(m -> Annotations.find(m, REDIRECT) != null || Annotations.find(m, INJECT) != null).findFirst().orElseThrow();
			AnnotationNode injector = Annotations.find(handler, REDIRECT) != null ? Annotations.find(handler, REDIRECT) : Annotations.find(handler, INJECT);
			Object at = Annotations.value(injector, "at");
			AnnotationNode point = at instanceof AnnotationNode single ? single : Annotations.list(at).getFirst();
			assertEquals("L" + hooks + ";finalizeMobSpawn" + hookDesc, Annotations.value(point, "target"), each[2]);
			assertTrue(node.methods.stream().noneMatch(m -> m.name.contains("$fabricated")), "no bridge: " + each[2]);
			mod.put(each[2], repaired.get(each[2] + ".class"));
			List<PreflightFinding> after = preflight(asLoaded, mod::get, ClassBytesSource.EMPTY, false).check(each[0], config, Refmap.EMPTY, Set.of());
			assertTrue(after.isEmpty(), after.toString());
		}
		// Carpet's handler makes the call itself when it doesn't mock the spawn: through the hook, which fires NeoForge's
		// spawn event, as the call it replaced did.
		assertTrue(callsIn(mod.get("carpet/mixins/NaturalSpawnerMixin")).contains(hooks + ".finalizeMobSpawn" + hookDesc));
		assertFalse(callsIn(mod.get("carpet/mixins/NaturalSpawnerMixin")).contains(mob + ".finalizeSpawn"));
	}

	@Test
	void aHookAtASpawnCallForge61To66sTransformerPointsAtItsEventHookMovesToTheHook() throws Exception {
		// CORPUS-78: Forge 64's and 65's MethodRedirector points PatrolSpawner.spawnPatrolMember's finalizeSpawn call at
		// ForgeEventFactory.onFinalizeSpawn before Mixin applies a mod's mixins: Friends & Foes' @Inject at the
		// PatrollingMonster's call found nothing there, and its required config stopped the server.
		String hookDesc = PlatformCoremodsTest.FINALIZE_REASON_HOOK;
		String hook = PlatformCoremodsTest.FORGE_HOOKS;
		String name = "com/faboslav/friendsandfoes/fabric/mixin/PatrolSpawnerMixin";
		byte[] friendsAndFoes = spawnerMixin(name, PlatformCoremodsTest.PATROLS, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "friendsandfoes$onSpawnEntity", "(Lnet/minecraft/server/level/ServerLevel;"
					+ "Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;ZLorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V", null, null);
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "spawnPatrolMember");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", "INVOKE");
			at.visit("target", "L" + PlatformCoremodsTest.PATROLLING + ";finalizeSpawn" + PlatformCoremodsTest.FINALIZE_REASON);
			at.visitEnd();
			ats.visitEnd();
			ann.visit("cancellable", true);
			ann.visitEnd();
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 6);
			mv.visitEnd();
		});
		String config = "{\"required\":true,\"package\":\"com.faboslav.friendsandfoes.fabric.mixin\",\"mixins\":[\"PatrolSpawnerMixin\"]}";
		MixinConfigFile parsed = MixinConfigFile.parse(config);
		Map<String, byte[]> jars = PlatformCoremodsTest.forgeGame();
		Map<String, byte[]> mod = new HashMap<>(Map.of(name, friendsAndFoes));
		Function<ClassIndex, MixinPreflight> onForge = game -> new MixinPreflight(game, InstalledClasses.NONE, mod::get,
				ClassBytesSource.EMPTY, false, true, ClassBytesSource.EMPTY, "Forge");

		// The game as its jars have it makes the call: pre-flight passed the mixin, and Mixin then failed it.
		List<PreflightFinding> asJars = onForge.apply(new ClassIndex(jars::get)).check("friendsandfoes.mixins.json", parsed, Refmap.EMPTY, Set.of());
		assertTrue(asJars.isEmpty(), asJars.toString());
		// As Forge's transformer leaves it, it doesn't, and the injection moves to its hook's call.
		ClassIndex asLoaded = new ClassIndex(io.github.cmdralph.fabricated.core.translate.PlatformDescriptor.FORGE.coremods("26.1.2").over(jars::get));
		assertFalse(onForge.apply(asLoaded).check("friendsandfoes.mixins.json", parsed, Refmap.EMPTY, Set.of()).isEmpty());
		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = onForge.apply(asLoaded).repairs("friendsandfoes.mixins.json", parsed, Refmap.EMPTY, Set.of(), notes);
		assertEquals(1, notes.size(), notes.toString());
		assertTrue(notes.getFirst().message().contains("inside Forge's hook " + hook.replace('/', '.') + ".onFinalizeSpawn" + hookDesc), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get(name + ".class")).accept(node, 0);
		MethodNode handler = node.methods.stream().filter(m -> Annotations.find(m, INJECT) != null).findFirst().orElseThrow();
		AnnotationNode point = Annotations.list(Annotations.value(Annotations.find(handler, INJECT), "at")).getFirst();
		assertEquals("L" + hook + ";onFinalizeSpawn" + hookDesc, Annotations.value(point, "target"));
		mod.put(name, repaired.get(name + ".class"));
		List<PreflightFinding> after = onForge.apply(asLoaded).check("friendsandfoes.mixins.json", parsed, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());
	}

	@Test
	void aWrapAtASpawnCallMovesToForgesHookOnlyWhereThatForgesOwnListHasTheTransformerPointItThere() throws Exception {
		// CORPUS-78: Friends & Foes' static @WrapOperation of a structure mob's finalizeSpawn call, in the lambda body of
		// StructureTemplate.placeEntities, which takes that lambda's parameters after the Operation. Forge 61's list names
		// StructureTemplate, so its transformer points the call at ForgeEventFactory.onFinalizeSpawn, and the wrap moves
		// there; Forge 64's own list (its 26.1.2 branch) doesn't, and the wrap stays where it is. 26.1's list, which names
		// it, moved the wrap on Forge 64 too, and Mixin found nothing there: the corpus server's world generation stopped.
		String template = PlatformCoremodsTest.STRUCTURE_TEMPLATE;
		String lambdaDesc = PlatformCoremodsTest.PLACE_ENTITY;
		String hookDesc = PlatformCoremodsTest.FINALIZE_REASON_HOOK;
		String hook = PlatformCoremodsTest.FORGE_HOOKS;
		String call = "L" + PlatformCoremodsTest.MOB + ";finalizeSpawn" + PlatformCoremodsTest.FINALIZE_REASON;
		String name = "com/faboslav/friendsandfoes/fabric/mixin/StructureTemplateEntitySpawnMixin";
		String handlerDesc = hookDesc.substring(0, hookDesc.indexOf(')')) + OPERATION + lambdaDesc.substring(1, lambdaDesc.indexOf(')')) + ")"
				+ Type.getReturnType(hookDesc).getDescriptor();
		byte[] friendsAndFoes = spawnerMixin(name, template, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "friendsandfoes$onFinalizeSpawn", handlerDesc, null, null);
			AnnotationVisitor ann = mv.visitAnnotation(WRAP_OPERATION, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "lambda$placeEntities$0");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", "INVOKE");
			at.visit("target", call);
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			mv.visitInsn(Opcodes.ACONST_NULL);
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitMaxs(1, 12);
			mv.visitEnd();
		});
		String config = "{\"required\":true,\"package\":\"com.faboslav.friendsandfoes.fabric.mixin\",\"injectors\":{\"defaultRequire\":1},"
				+ "\"mixins\":[\"StructureTemplateEntitySpawnMixin\"]}";
		MixinConfigFile parsed = MixinConfigFile.parse(config);
		Map<String, byte[]> jars = PlatformCoremodsTest.forgeGame();
		Map<String, byte[]> mod = new HashMap<>(Map.of(name, friendsAndFoes));
		Function<ClassIndex, MixinPreflight> onForge = game -> new MixinPreflight(game, InstalledClasses.NONE, mod::get,
				ClassBytesSource.EMPTY, false, true, ClassBytesSource.EMPTY, "Forge");
		ClassIndex forge61 = new ClassIndex(io.github.cmdralph.fabricated.core.translate.PlatformDescriptor.FORGE.coremods("1.21.11").over(jars::get));
		ClassIndex forge64 = new ClassIndex(io.github.cmdralph.fabricated.core.translate.PlatformDescriptor.FORGE.coremods("26.1.2").over(jars::get));

		// Forge 64 makes the call where the mod wraps it: nothing to report, nothing to repair.
		assertTrue(onForge.apply(forge64).check("friendsandfoes-fabric.mixins.json", parsed, Refmap.EMPTY, Set.of()).isEmpty());
		List<PreflightFinding> none = new ArrayList<>();
		assertTrue(onForge.apply(forge64).repairs("friendsandfoes-fabric.mixins.json", parsed, Refmap.EMPTY, Set.of(), none).isEmpty(), none.toString());
		assertTrue(none.isEmpty(), none.toString());

		// Forge 61 makes it inside its hook: the wrap moves to the hook's call, which takes the mob first, so the handler
		// takes it as it is.
		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = onForge.apply(forge61).repairs("friendsandfoes-fabric.mixins.json", parsed, Refmap.EMPTY, Set.of(), notes);
		assertEquals(1, notes.size(), notes.toString());
		assertTrue(notes.getFirst().message().contains("inside Forge's hook " + hook.replace('/', '.') + ".onFinalizeSpawn" + hookDesc), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get(name + ".class")).accept(node, 0);
		assertEquals(List.of("friendsandfoes$onFinalizeSpawn"), node.methods.stream().map(m -> m.name).toList(), "no bridge");
		AnnotationNode point = Annotations.list(Annotations.value(Annotations.find(node.methods.getFirst(), WRAP_OPERATION), "at")).getFirst();
		assertEquals("L" + hook + ";onFinalizeSpawn" + hookDesc, Annotations.value(point, "target"));
		mod.put(name, repaired.get(name + ".class"));
		List<PreflightFinding> after = onForge.apply(forge61).check("friendsandfoes-fabric.mixins.json", parsed, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());
	}

	/** The calls a class's methods make, as owner.name+desc. */
	private static List<String> callsIn(byte[] data) {
		return PlatformCoremodsTest.calls(data).stream().map(c -> c.owner + "." + c.name + c.desc).toList();
	}

	/** A mixin into {@code target}; {@code body} adds members. */
	private static byte[] spawnerMixin(String name, String target, Consumer<ClassWriter> body) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, name, null, "java/lang/Object", null);
		AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor values = m.visitArray("value");
		values.visit(null, Type.getObjectType(target));
		values.visitEnd();
		m.visitEnd();
		body.accept(cw);
		cw.visitEnd();
		return cw.toByteArray();
	}

	@Test
	void anInjectionTakingTheParametersVanillasMethodHasGetsABridgeTakingNeoForgesOneMore() throws Exception {
		// Ledger's hooks into FireBlock.checkBurnOut take vanilla's parameters, then a @Local after the callback info, which
		// SIG-1's bridge doesn't carry; NeoForge's build adds the side the fire burns from.
		String vanilla = "(ILjava/lang/String;I)V";
		String neoForge = "(ILjava/lang/String;ILjava/lang/Long;)V";
		ClassWriter target = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		target.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor burn = target.visitMethod(Opcodes.ACC_PRIVATE, "burn", neoForge, null, null);
		burn.visitCode();
		burn.visitInsn(Opcodes.RETURN);
		burn.visitMaxs(0, 0);
		burn.visitEnd();
		target.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(TARGET, target.toByteArray())::get);
		String handlerDesc = "(ILjava/lang/String;ILorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;Ljava/lang/Object;)V";
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Fire", mixin("com/example/mixin/Fire", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "onBurn", handlerDesc, null, null);
			mv.visitParameterAnnotation(4, "Lcom/llamalad7/mixinextras/sugar/Local;", false).visitEnd();
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "burn");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", "HEAD");
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			mv.visitVarInsn(Opcodes.ILOAD, 1);
			mv.visitVarInsn(Opcodes.ILOAD, 3);
			mv.visitInsn(Opcodes.IADD);
			mv.visitInsn(Opcodes.POP);
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(2, 6);
			mv.visitEnd();
		})));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Fire"));
		List<PreflightFinding> before = preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(before.getFirst().message().contains("does not match the parameters of burn" + neoForge), before.toString());

		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight(game, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		assertEquals(1, notes.size(), notes.toString());
		assertTrue(notes.getFirst().message().contains("burn" + vanilla + ", which this game build declares with one more parameter, burn" + neoForge), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get("com/example/mixin/Fire.class")).accept(node, 0);
		MethodNode bridge = node.methods.stream().filter(m -> m.name.equals("onBurn$fabricated")).findFirst().orElseThrow();
		assertEquals("(ILjava/lang/String;ILjava/lang/Long;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;Ljava/lang/Object;)V", bridge.desc);
		assertEquals("Lcom/llamalad7/mixinextras/sugar/Local;", bridge.invisibleParameterAnnotations[5].getFirst().desc, "the sugar moves to the bridge");
		mod.put("com/example/mixin/Fire", repaired.get("com/example/mixin/Fire.class"));
		List<PreflightFinding> after = preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());
	}

	@Test
	void anInjectionWhoseSelectorNamesVanillasDescriptorIsBridgedIntoTheMethodTheBuildGaveMoreParameters() throws Exception {
		// SIG-3: Forge 61's LevelRenderer.extractBlockOutline also takes whether to draw the outline, and Fabric API's refmap
		// names vanilla's descriptor, so its outline hook found no method where a selector naming the method alone is bridged.
		String vanilla = "(Ljava/util/List;Ljava/lang/String;)V";
		String forge = "(Ljava/util/List;Ljava/lang/String;Z)V";
		ClassIndex game = new ClassIndex(Map.of(TARGET, withMethods("extractBlockOutline", forge))::get);
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Outline"));
		for (boolean sugar : new boolean[] {false, true}) {
			Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Outline", outlineMixin("extractBlockOutline" + vanilla, sugar)));
			List<PreflightFinding> before = preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
			assertTrue(before.getFirst().message().contains("targets method extractBlockOutline" + vanilla + ", which does not exist"), before.toString());
			List<PreflightFinding> notes = new ArrayList<>();
			byte[] repaired = preflight(game, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes)
					.get("com/example/mixin/Outline.class");
			assertNotNull(repaired, "sugar=" + sugar + ": " + notes);
			assertTrue(notes.getFirst().message().contains(sugar ? "one more parameter, extractBlockOutline" + forge : "which this game build declares with more parameters"),
					notes.toString());
			ClassNode node = new ClassNode();
			new ClassReader(repaired).accept(node, 0);
			MethodNode bridge = node.methods.stream().filter(m -> m.name.equals("h$fabricated")).findFirst().orElseThrow();
			assertEquals(List.of("extractBlockOutline" + forge), Annotations.value(Annotations.find(bridge, INJECT), "method"),
					"the bridge's selector names the method this build declares");
			mod.put("com/example/mixin/Outline", repaired);
			List<PreflightFinding> after = preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
			assertTrue(after.isEmpty(), "sugar=" + sugar + ": " + after);
		}

		// Two methods of the name could be the one meant: neither is guessed.
		ClassIndex two = new ClassIndex(Map.of(TARGET, withMethods("extractBlockOutline", forge, "(Ljava/util/List;Ljava/lang/String;I)V"))::get);
		Map<String, byte[]> mod = Map.of("com/example/mixin/Outline", outlineMixin("extractBlockOutline" + vanilla, false));
		assertTrue(preflight(two, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
	}

	/** {@link #TARGET} with an empty private instance method {@code name} of each descriptor. */
	private static byte[] withMethods(String name, String... descs) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		for (String desc : descs) {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, name, desc, null, null);
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * A mixin {@code Outline} whose {@code @Inject} handler {@code h} at the return of {@code selector} takes a list, a
	 * string and the callback info, and with {@code sugar} a {@code @Local} after it, as upstream's outline hook (and with
	 * sugar, Ledger's fire hooks) does.
	 */
	private static byte[] outlineMixin(String selector, boolean sugar) {
		return mixin("com/example/mixin/Outline", false, cw -> {
			String desc = "(Ljava/util/List;Ljava/lang/String;" + CALLBACK_INFO + (sugar ? "Ljava/lang/Object;" : "") + ")V";
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "h", desc, null, null);
			if (sugar) mv.visitParameterAnnotation(3, "Lcom/llamalad7/mixinextras/sugar/Local;", false).visitEnd();
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, selector);
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", "RETURN");
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			mv.visitVarInsn(Opcodes.ALOAD, 1);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;", false);
			mv.visitInsn(Opcodes.POP);
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(1, sugar ? 5 : 4);
			mv.visitEnd();
		});
	}

	@Test
	void anInjectionIntoCodeNeoForgeSplitOffIsPointedAtTheMethodItIsIn() throws Exception {
		// SPLIT-1: Carpet hooks Level.setBlock's neighbour updates, which NeoForge's build moved into markAndNotifyBlock.
		String thing = "L" + TARGET + ";";
		ClassWriter target = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		target.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor set = target.visitMethod(Opcodes.ACC_PUBLIC, "set", "(Ljava/lang/String;)Z", null, null);
		set.visitCode();
		set.visitVarInsn(Opcodes.ALOAD, 0);
		set.visitInsn(Opcodes.ICONST_0);
		set.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "notifyAll", "(I)V", false);
		set.visitInsn(Opcodes.ICONST_1);
		set.visitInsn(Opcodes.IRETURN);
		set.visitMaxs(0, 0);
		set.visitEnd();
		MethodVisitor notifyAll = target.visitMethod(Opcodes.ACC_PUBLIC, "notifyAll", "(I)V", null, null);
		notifyAll.visitCode();
		notifyAll.visitVarInsn(Opcodes.ALOAD, 0);
		notifyAll.visitVarInsn(Opcodes.ILOAD, 1);
		notifyAll.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "helper", "(I)V", false);
		notifyAll.visitInsn(Opcodes.RETURN);
		notifyAll.visitMaxs(0, 0);
		notifyAll.visitEnd();
		MethodVisitor helper = target.visitMethod(Opcodes.ACC_PUBLIC, "helper", "(I)V", null, null);
		helper.visitCode();
		helper.visitInsn(Opcodes.RETURN);
		helper.visitMaxs(0, 0);
		helper.visitEnd();
		target.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(TARGET, target.toByteArray())::get);
		java.util.function.BiConsumer<ClassWriter, String[]> atHelperInSet = (cw, handler) -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, handler[1], handler[2], null, null);
			AnnotationVisitor ann = mv.visitAnnotation(handler[0], false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "set");
			methods.visitEnd();
			boolean single = handler[0].equals(REDIRECT);
			AnnotationVisitor ats = single ? null : ann.visitArray("at");
			AnnotationVisitor at = single ? ann.visitAnnotation("at", AT) : ats.visitAnnotation(null, AT);
			at.visit("value", "INVOKE");
			at.visit("target", "L" + TARGET + ";helper(I)V");
			at.visitEnd();
			if (ats != null) ats.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 3);
			mv.visitEnd();
		};
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Updates", mixin("com/example/mixin/Updates", false, cw -> {
			atHelperInSet.accept(cw, new String[] {REDIRECT, "redirect", "(" + thing + "I)V"});
			atHelperInSet.accept(cw, new String[] {INJECT, "inject", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V"});
			atHelperInSet.accept(cw, new String[] {INJECT, "returning", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V"});
		})));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Updates"));

		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight(game, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		assertEquals(2, notes.size(), "the returnable callback info doesn't suit notifyAll: " + notes);
		assertTrue(notes.stream().allMatch(n -> n.message().contains("set(Ljava/lang/String;)Z, the code of which there NeoForge split off into notifyAll(I)V")), notes.toString());
		mod.put("com/example/mixin/Updates", repaired.get("com/example/mixin/Updates.class"));
		List<PreflightFinding> after = preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertEquals(1, after.size(), after.toString());
		assertEquals("returning(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V", after.getFirst().handler());
	}

	@Test
	void aLocalTakenByTypeAtACallInAnOverloadWithAParameterOfThatTypeIsNamedByItsOrdinal() throws Exception {
		// architectury's explosion hook takes the entities' list at the loop over it in ServerExplosion.hurtEntities, whose
		// code NeoForge's build moved into an overload that also takes the exploded blocks' list.
		ClassWriter target = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		target.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor hurt = target.visitMethod(Opcodes.ACC_PRIVATE, "hurt", "()V", null, null);
		hurt.visitCode();
		hurt.visitVarInsn(Opcodes.ALOAD, 0);
		hurt.visitInsn(Opcodes.ACONST_NULL);
		hurt.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "hurt", "(Ljava/util/List;)V", false);
		hurt.visitInsn(Opcodes.RETURN);
		hurt.visitMaxs(0, 0);
		hurt.visitEnd();
		MethodVisitor overload = target.visitMethod(Opcodes.ACC_PRIVATE, "hurt", "(Ljava/util/List;)V", null, null);
		org.objectweb.asm.Label start = new org.objectweb.asm.Label();
		org.objectweb.asm.Label listStart = new org.objectweb.asm.Label();
		org.objectweb.asm.Label end = new org.objectweb.asm.Label();
		overload.visitCode();
		overload.visitLabel(start);
		overload.visitInsn(Opcodes.ACONST_NULL);
		overload.visitVarInsn(Opcodes.ASTORE, 2);
		overload.visitLabel(listStart);
		overload.visitVarInsn(Opcodes.ALOAD, 2);
		overload.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;", true);
		overload.visitInsn(Opcodes.POP);
		overload.visitInsn(Opcodes.RETURN);
		overload.visitLabel(end);
		overload.visitLocalVariable("this", "L" + TARGET + ";", null, start, end, 0);
		overload.visitLocalVariable("blocks", "Ljava/util/List;", null, start, end, 1);
		overload.visitLocalVariable("list", "Ljava/util/List;", null, listStart, end, 2);
		overload.visitMaxs(0, 0);
		overload.visitEnd();
		target.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(TARGET, target.toByteArray())::get);
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Explosion", mixin("com/example/mixin/Explosion", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "explodePost", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;Ljava/util/List;)V", null, null);
			mv.visitParameterAnnotation(1, "Lcom/llamalad7/mixinextras/sugar/Local;", false).visitEnd();
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "hurt");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", "INVOKE");
			at.visit("target", "Ljava/util/List;iterator()Ljava/util/Iterator;");
			at.visit("ordinal", 0);
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 3);
			mv.visitEnd();
		})));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Explosion"));
		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = preflight(game, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes)
				.get("com/example/mixin/Explosion.class");
		assertNotNull(repaired, notes.toString());
		assertTrue(notes.getFirst().message().contains("hurt()V, whose code NeoForge moved into hurt(Ljava/util/List;)V"), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired).accept(node, 0);
		MethodNode handler = node.methods.stream().filter(m -> m.name.equals("explodePost")).findFirst().orElseThrow();
		assertEquals(1, Annotations.value(Annotations.ofParameter(handler, 1).getFirst(), "ordinal"), "the second list in scope, not the blocks'");
		assertTrue(node.methods.stream().noneMatch(m -> m.name.contains("$fabricated")), "no bridge");
	}

	/**
	 * MODEL-4: Fabric API's before- and after-bake model hook wraps {@code bakeUncached(model, state)} in
	 * {@code ModelBakerImpl.bake(id, state)}, taking the id, the first of the method's parameters. NeoForge 21.1's
	 * {@code bake(id, state)} only calls an overload also taking the sprite getter, which calls {@code bakeUncached}'s
	 * overload also taking it; the two-argument {@code bakeUncached} passes the baker's own getter. The wrapper moves to
	 * the overload, which takes the id first too, and wraps the other call through a bridge.
	 */
	@Test
	void aWrapperTakingTheFirstOfTheMethodsParametersMovesWithTheOverloadThatTakesThemFirstToo() throws Exception {
		String function = "Ljava/util/function/Function;";
		String bake = "(Ljava/lang/String;Ljava/lang/Integer;)Ljava/lang/Object;";
		String bakeAll = "(Ljava/lang/String;Ljava/lang/Integer;" + function + ")Ljava/lang/Object;";
		String uncached = "(Ljava/lang/Number;Ljava/lang/Integer;)Ljava/lang/Object;";
		String uncachedAll = "(Ljava/lang/Number;Ljava/lang/Integer;" + function + ")Ljava/lang/Object;";
		ClassWriter target = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		target.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		target.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "sprites", function, null, null).visitEnd();
		BiConsumer<String[], Integer> forwarding = (names, parameters) -> {
			MethodVisitor mv = target.visitMethod(Opcodes.ACC_PUBLIC, names[0], names[1], null, null);
			mv.visitCode();
			for (int i = 0; i <= parameters; i++) mv.visitVarInsn(Opcodes.ALOAD, i);
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitFieldInsn(Opcodes.GETFIELD, TARGET, "sprites", function);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, names[0], names[2], false);
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		};
		forwarding.accept(new String[] {"bake", bake, bakeAll}, 2);
		MethodVisitor all = target.visitMethod(Opcodes.ACC_PUBLIC, "bake", bakeAll, null, null);
		all.visitCode();
		all.visitVarInsn(Opcodes.ALOAD, 0);
		all.visitInsn(Opcodes.ACONST_NULL);
		all.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Number");
		all.visitVarInsn(Opcodes.ALOAD, 2);
		all.visitVarInsn(Opcodes.ALOAD, 3);
		all.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "bakeUncached", uncachedAll, false);
		all.visitInsn(Opcodes.ARETURN);
		all.visitMaxs(0, 0);
		all.visitEnd();
		forwarding.accept(new String[] {"bakeUncached", uncached, uncachedAll}, 2);
		MethodVisitor uncachedCode = target.visitMethod(Opcodes.ACC_PUBLIC, "bakeUncached", uncachedAll, null, null);
		uncachedCode.visitCode();
		uncachedCode.visitInsn(Opcodes.ACONST_NULL);
		uncachedCode.visitInsn(Opcodes.ARETURN);
		uncachedCode.visitMaxs(0, 0);
		uncachedCode.visitEnd();
		target.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(TARGET, target.toByteArray())::get);

		byte[] wrapper = mixin("com/example/mixin/Baker", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "wrapInnerBake",
					"(Ljava/lang/Object;Ljava/lang/Number;Ljava/lang/Integer;" + OPERATION + "Ljava/lang/String;)Ljava/lang/Object;", null, null);
			mv.visitParameterAnnotation(0, "Lorg/spongepowered/asm/mixin/injection/Coerce;", false).visitEnd();
			AnnotationVisitor ann = mv.visitAnnotation(WRAP_OPERATION, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "bake" + bake);
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", "INVOKE");
			at.visit("target", "L" + TARGET + ";bakeUncached" + uncached);
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			mv.visitInsn(Opcodes.ACONST_NULL);
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitMaxs(1, 6);
			mv.visitEnd();
		});
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Baker", wrapper));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Baker"));
		assertEquals(1, preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).size());

		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = preflight(game, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes)
				.get("com/example/mixin/Baker.class");
		assertNotNull(repaired, notes.toString());
		assertEquals(2, notes.size(), notes.toString());
		assertTrue(notes.get(0).message().contains("bake" + bake + ", whose code NeoForge moved into bake" + bakeAll), notes.toString());
		assertTrue(notes.get(1).message().contains("which this game build makes through an overload with one more argument, bakeUncached" + uncachedAll), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired).accept(node, 0);
		MethodNode bridge = node.methods.stream().filter(m -> m.name.equals("wrapInnerBake$fabricated")).findFirst().orElseThrow();
		assertEquals("(Ljava/lang/Object;Ljava/lang/Number;Ljava/lang/Integer;" + function + OPERATION + "Ljava/lang/String;)Ljava/lang/Object;", bridge.desc,
				"the id still taken from the method's parameters, first in the overload too");
		assertTrue(Annotations.ofParameter(bridge, 0).stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/injection/Coerce;")),
				"the baker coerced as the handler coerces it");
		mod.put("com/example/mixin/Baker", repaired);
		List<PreflightFinding> after = preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());
	}

	@Test
	void aWrapperOfACallTheOverloadMakesThroughAnOverloadMovesWithBoth() throws Exception {
		// OVERLOAD-1: PolyPlus wraps Language.loadFromJson(stream, output) in ClientLanguage.appendFrom, taking the
		// resource there and the language code. NeoForge's appendFrom only calls an overload also taking a components map,
		// whose loop calls loadFromJson's overload also taking a components consumer; loadFromJson(stream, output) passes a
		// lambda capturing nothing for it (the types can't tell which of the two consumers is the added one).
		String lang = "net/minecraft/locale/Lang";
		String read = "(Ljava/lang/String;Ljava/lang/Runnable;)V";
		String readAll = "(Ljava/lang/String;Ljava/lang/Runnable;Ljava/lang/Runnable;)V";
		ClassWriter language = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		language.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, lang, null, "java/lang/Object", null);
		MethodVisitor forward = language.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "read", read, null, null);
		forward.visitCode();
		forward.visitVarInsn(Opcodes.ALOAD, 0);
		forward.visitVarInsn(Opcodes.ALOAD, 1);
		forward.visitInvokeDynamicInsn("run", "()Ljava/lang/Runnable;", new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory",
				"metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;"
						+ "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false),
				org.objectweb.asm.Type.getType("()V"), new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC, lang, "lambda$read$0", "()V", false),
				org.objectweb.asm.Type.getType("()V"));
		forward.visitMethodInsn(Opcodes.INVOKESTATIC, lang, "read", readAll, false);
		forward.visitInsn(Opcodes.RETURN);
		forward.visitMaxs(0, 0);
		forward.visitEnd();
		MethodVisitor all = language.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "read", readAll, null, null);
		all.visitCode();
		all.visitInsn(Opcodes.RETURN);
		all.visitMaxs(0, 0);
		all.visitEnd();
		language.visitEnd();

		ClassWriter target = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		target.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor append = target.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_DEPRECATED, "append", "(Ljava/lang/String;Ljava/util/Map;)V", null, null);
		append.visitCode();
		append.visitVarInsn(Opcodes.ALOAD, 0);
		append.visitVarInsn(Opcodes.ALOAD, 1);
		append.visitTypeInsn(Opcodes.NEW, "java/util/HashMap");
		append.visitInsn(Opcodes.DUP);
		append.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false);
		append.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET, "append", "(Ljava/lang/String;Ljava/util/Map;Ljava/util/Map;)V", false);
		append.visitInsn(Opcodes.RETURN);
		append.visitMaxs(0, 0);
		append.visitEnd();
		MethodVisitor overload = target.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "append", "(Ljava/lang/String;Ljava/util/Map;Ljava/util/Map;)V", null, null);
		org.objectweb.asm.Label start = new org.objectweb.asm.Label();
		org.objectweb.asm.Label resourceStart = new org.objectweb.asm.Label();
		org.objectweb.asm.Label end = new org.objectweb.asm.Label();
		overload.visitCode();
		overload.visitLabel(start);
		overload.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
		overload.visitInsn(Opcodes.DUP);
		overload.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
		overload.visitVarInsn(Opcodes.ASTORE, 3);
		overload.visitLabel(resourceStart);
		overload.visitVarInsn(Opcodes.ALOAD, 0);
		overload.visitInsn(Opcodes.ACONST_NULL);
		overload.visitInsn(Opcodes.ACONST_NULL);
		overload.visitMethodInsn(Opcodes.INVOKESTATIC, lang, "read", readAll, false);
		overload.visitInsn(Opcodes.RETURN);
		overload.visitLabel(end);
		overload.visitLocalVariable("code", "Ljava/lang/String;", null, start, end, 0);
		overload.visitLocalVariable("map", "Ljava/util/Map;", null, start, end, 1);
		overload.visitLocalVariable("components", "Ljava/util/Map;", null, start, end, 2);
		overload.visitLocalVariable("resource", "Ljava/lang/StringBuilder;", null, resourceStart, end, 3);
		overload.visitMaxs(0, 0);
		overload.visitEnd();
		target.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(TARGET, target.toByteArray(), lang, language.toByteArray())::get);

		byte[] wrapper = mixin("com/example/mixin/Translations", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "skipMalformed",
					"(Ljava/lang/String;Ljava/lang/Runnable;" + OPERATION + "Ljava/lang/String;Ljava/lang/StringBuilder;)V", null, null);
			AnnotationVisitor code = mv.visitParameterAnnotation(3, "Lcom/llamalad7/mixinextras/sugar/Local;", false);
			code.visit("argsOnly", true);
			code.visitEnd();
			mv.visitParameterAnnotation(4, "Lcom/llamalad7/mixinextras/sugar/Local;", false).visitEnd();
			AnnotationVisitor ann = mv.visitAnnotation(WRAP_OPERATION, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "append");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", "INVOKE");
			at.visit("target", "L" + lang + ";read" + read);
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 5);
			mv.visitEnd();
		});
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Translations", wrapper));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Translations"));
		assertEquals(1, preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).size());

		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = preflight(game, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes)
				.get("com/example/mixin/Translations.class");
		assertNotNull(repaired, notes.toString());
		assertEquals(2, notes.size(), notes.toString());
		assertTrue(notes.get(0).message().contains("append(Ljava/lang/String;Ljava/util/Map;)V, whose code NeoForge moved into append(Ljava/lang/String;Ljava/util/Map;Ljava/util/Map;)V"),
				notes.toString());
		assertTrue(notes.get(1).message().contains("which this game build makes through an overload with one more argument, read" + readAll), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired).accept(node, 0);
		MethodNode bridge = node.methods.stream().filter(m -> m.name.equals("skipMalformed$fabricated")).findFirst().orElseThrow();
		assertEquals("(Ljava/lang/String;Ljava/lang/Runnable;Ljava/lang/Runnable;" + OPERATION + "Ljava/lang/String;Ljava/lang/StringBuilder;)V", bridge.desc);
		AnnotationNode moved = Annotations.find(bridge, WRAP_OPERATION);
		assertNotNull(moved, "the injector moved to the bridge");
		assertEquals(List.of("append(Ljava/lang/String;Ljava/util/Map;Ljava/util/Map;)V"), Annotations.value(moved, "method"));
		assertEquals("L" + lang + ";read" + readAll, Annotations.value(Annotations.list(Annotations.value(moved, "at")).getFirst(), "target"));
		assertEquals(0, Annotations.value(Annotations.ofParameter(bridge, 5).getFirst(), "ordinal"), "the one resource in scope at the call");
		mod.put("com/example/mixin/Translations", repaired);
		List<PreflightFinding> after = preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());

		// Without the class whose method says which consumer is the added one, nothing moves: the injector isn't moved to
		// an overload where its point can't be pointed.
		ClassIndex unclear = new ClassIndex(Map.of(TARGET, target.toByteArray())::get);
		List<PreflightFinding> none = new ArrayList<>();
		assertTrue(preflight(unclear, Map.of("com/example/mixin/Translations", wrapper)::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), none).isEmpty(), none.toString());
		assertEquals(List.of(), none);
	}

	@Test
	void aHandlerInARegistrysFreezeReturnsAtOnceInAFreezeBeforeTheMainEntrypoints() throws Exception {
		// REG-8: Flashback's @Inject at freeze's HEAD calls Flashback while the registry isn't frozen, which starts it; at
		// NeoForge's bootstrap freeze its start-up found no client. On Fabric no freeze comes before Fabric mods' main
		// entrypoints.
		assertOnlyTheFreezeHandlerReturnsEarly("freeze", "size");
	}

	@Test
	void aHandlerInARegistrysFreezeReturnsAtOnceWhereTheGameRunsWithSrgNames() throws Exception {
		// Forge 47 runs MappedRegistry.freeze as m_203521_, the class's one method taking nothing and returning a registry:
		// a Fabric mod's handler there ran in Forge's freezes before the main entrypoints (the shared test mod's REG-8 check).
		assertOnlyTheFreezeHandlerReturnsEarly("m_203521_", "m_13562_");
	}

	private void assertOnlyTheFreezeHandlerReturnsEarly(String freeze, String size) throws Exception {
		String registry = "net/minecraft/core/MappedRegistry";
		String callback = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;";
		ClassWriter target = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		target.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, registry, null, "java/lang/Object", null);
		target.visitField(Opcodes.ACC_PRIVATE, "frozen", "Z", null, null).visitEnd();
		for (String[] method : new String[][] {{freeze, "()Lnet/minecraft/core/Registry;"}, {size, "()I"}}) {
			MethodVisitor mv = target.visitMethod(Opcodes.ACC_PUBLIC, method[0], method[1], null, null);
			mv.visitCode();
			mv.visitInsn(method[0].equals(size) ? Opcodes.ICONST_0 : Opcodes.ACONST_NULL);
			mv.visitInsn(method[0].equals(size) ? Opcodes.IRETURN : Opcodes.ARETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		target.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(registry, target.toByteArray())::get);

		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "com/example/mixin/Lenient", null, "java/lang/Object", null);
		AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor values = m.visitArray("value");
		values.visit(null, Type.getObjectType(registry));
		values.visitEnd();
		m.visitEnd();
		cw.visitField(Opcodes.ACC_PRIVATE, "frozen", "Z", null, null).visitAnnotation(SHADOW, false).visitEnd();
		for (String[] handler : new String[][] {{"onFreeze", freeze, callback}, {"onSize", size, callback}}) {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, handler[0], "(" + handler[2] + ")V", null, null);
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, handler[1]);
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			ats.visitAnnotation(null, AT).visit("value", "HEAD");
			ats.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			// if (!this.frozen) count(); as Flashback's handler reads its registry's field first
			org.objectweb.asm.Label done = new org.objectweb.asm.Label();
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitFieldInsn(Opcodes.GETFIELD, "com/example/mixin/Lenient", "frozen", "Z");
			mv.visitJumpInsn(Opcodes.IFNE, done);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/example/Mod", "start", "()V", false);
			mv.visitLabel(done);
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		cw.visitEnd();
		byte[] lenient = cw.toByteArray();
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Lenient", lenient));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Lenient"));

		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = preflight(game, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes)
				.get("com/example/mixin/Lenient.class");
		assertNotNull(repaired, notes.toString());
		assertEquals(1, notes.size(), notes.toString());
		assertEquals(PreflightFinding.Severity.INFO, notes.get(0).severity());
		assertTrue(notes.get(0).message().startsWith("@Inject onFreeze runs as a registry freezes"), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired).accept(node, 0);
		MethodNode onFreeze = node.methods.stream().filter(mn -> mn.name.equals("onFreeze")).findFirst().orElseThrow();
		AbstractInsnNode first = onFreeze.instructions.getFirst();
		while (first.getOpcode() < 0) first = first.getNext();
		assertTrue(first instanceof MethodInsnNode call && call.owner.equals("io/github/cmdralph/fabricated/loader/EarlyFreeze")
				&& call.name.equals("beforeMainEntrypoints") && call.desc.equals("()Z"), "the check comes first");
		assertEquals(Opcodes.IFEQ, first.getNext().getOpcode());
		assertEquals(Opcodes.RETURN, first.getNext().getNext().getOpcode());
		MethodNode onSize = node.methods.stream().filter(mn -> mn.name.equals("onSize")).findFirst().orElseThrow();
		for (AbstractInsnNode insn : onSize.instructions) {
			assertFalse(insn instanceof MethodInsnNode call && call.owner.equals("io/github/cmdralph/fabricated/loader/EarlyFreeze"), "only freeze's handler");
		}
		// The frames still fit the code: the JVM verifies the class as it initialises it.
		ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				if (!name.equals("com.example.mixin.Lenient")) throw new ClassNotFoundException(name);
				return defineClass(name, repaired, 0, repaired.length);
			}
		};
		Class.forName("com.example.mixin.Lenient", true, loader);
		mod.put("com/example/mixin/Lenient", repaired);
		List<PreflightFinding> after = preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());
	}

	@Test
	void aHandlerIntoTheOptionsIsSkippedWhereItThrowsBeforeTheEntrypoints() throws Exception {
		// ENTRY-2: Bobby's @ModifyArg in Options' constructor reads its config, which its client initializer sets up. On
		// Fabric the options are created after Fabric mods' entrypoints; NeoForge 21.1 creates them before it loads mods.
		String options = "net/minecraft/client/Options";
		ClassWriter target = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		target.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, options, null, "java/lang/Object", null);
		MethodVisitor init = target.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		init.visitCode();
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitInsn(Opcodes.ICONST_2);
		init.visitIntInsn(Opcodes.BIPUSH, 32);
		init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, options, "range", "(II)V", false);
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(0, 0);
		init.visitEnd();
		MethodVisitor range = target.visitMethod(Opcodes.ACC_PUBLIC, "range", "(II)V", null, null);
		range.visitCode();
		range.visitInsn(Opcodes.RETURN);
		range.visitMaxs(0, 0);
		range.visitEnd();
		target.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(options, target.toByteArray())::get);

		String rangeCall = "L" + options + ";range(II)V";
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "com/example/mixin/Opts", null, "java/lang/Object", null);
		AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor values = m.visitArray("value");
		values.visit(null, Type.getObjectType(options));
		values.visitEnd();
		m.visitEnd();
		// {handler, injector, descriptor, point, the call it reads its mod's state with}
		String[][] handlers = {
				{"maxDistance", "Lorg/spongepowered/asm/mixin/injection/ModifyArg;", "(I)I", "INVOKE"},
				{"created", INJECT, "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", "TAIL"},
				{"keepRange", "Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;", "(L" + options + ";II)Z", "INVOKE"},
				{"replaceRange", REDIRECT, "(L" + options + ";II)V", "INVOKE"}};
		for (String[] handler : handlers) {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, handler[0], handler[2], null, null);
			AnnotationVisitor ann = mv.visitAnnotation(handler[1], false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "<init>");
			methods.visitEnd();
			boolean single = handler[1].equals(REDIRECT) || handler[1].endsWith("/ModifyArg;");
			AnnotationVisitor ats = single ? null : ann.visitArray("at");
			AnnotationVisitor at = single ? ann.visitAnnotation("at", AT) : ats.visitAnnotation(null, AT);
			at.visit("value", handler[3]);
			if (handler[3].equals("INVOKE")) at.visit("target", rangeCall);
			at.visitEnd();
			if (ats != null) ats.visitEnd();
			if (handler[1].endsWith("/ModifyArg;")) ann.visit("index", 1);
			ann.visitEnd();
			mv.visitCode();
			// As Bobby's: Bobby.getInstance().getConfig(), null before its client initializer has run.
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/example/Mod", "config", "()Lcom/example/Config;", false);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "com/example/Config", "maxDistance", "()I", false);
			switch (handler[2].charAt(handler[2].length() - 1)) {
				case 'I' -> mv.visitInsn(Opcodes.IRETURN);
				case 'Z' -> mv.visitInsn(Opcodes.IRETURN);
				default -> {
					mv.visitInsn(Opcodes.POP);
					mv.visitInsn(Opcodes.RETURN);
				}
			}
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		cw.visitEnd();
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Opts", cw.toByteArray()));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Opts"));

		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = preflight(game, mod::get, ClassBytesSource.EMPTY, true).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes)
				.get("com/example/mixin/Opts.class");
		assertNotNull(repaired, notes.toString());
		assertEquals(3, notes.size(), notes.toString());
		assertTrue(notes.stream().allMatch(n -> n.severity() == PreflightFinding.Severity.INFO && n.message().contains("hooks into the game's options")),
				notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired).accept(node, 0);
		Map<String, MethodNode> byName = new HashMap<>();
		node.methods.forEach(mn -> byName.put(mn.name, mn));
		// {handler, what it returns when skipped}
		for (Object[] expected : new Object[][] {{"maxDistance", Opcodes.ILOAD}, {"created", Opcodes.RETURN}, {"keepRange", Opcodes.ICONST_1}}) {
			MethodNode wrapped = byName.get((String) expected[0]);
			assertEquals(1, wrapped.tryCatchBlocks.size(), wrapped.name);
			assertEquals("java/lang/RuntimeException", wrapped.tryCatchBlocks.getFirst().type, wrapped.name);
			AbstractInsnNode insn = wrapped.tryCatchBlocks.getFirst().handler;
			while (insn.getOpcode() < 0) insn = insn.getNext();
			assertEquals(Opcodes.LDC, insn.getOpcode(), wrapped.name);
			assertEquals("com.example.mixin.Opts." + wrapped.name, ((LdcInsnNode) insn).cst, wrapped.name);
			insn = insn.getNext();
			assertTrue(insn instanceof MethodInsnNode call && call.owner.equals("io/github/cmdralph/fabricated/loader/EarlyHandlers") && call.name.equals("failed"),
					wrapped.name);
			insn = insn.getNext();
			assertEquals((int) expected[1], insn.getOpcode(), wrapped.name);
			if (insn instanceof VarInsnNode load) assertEquals(1, load.var, "the value the modifier was given");
		}
		assertTrue(byName.get("replaceRange").tryCatchBlocks.isEmpty(), "a redirect makes the call itself: left as it is");
		// The frames still fit the code: the JVM verifies the class as it defines it.
		ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				if (!name.equals("com.example.mixin.Opts")) throw new ClassNotFoundException(name);
				return defineClass(name, repaired, 0, repaired.length);
			}
		};
		Class.forName("com.example.mixin.Opts", true, loader);
		mod.put("com/example/mixin/Opts", repaired);
		List<PreflightFinding> again = new ArrayList<>();
		preflight(game, mod::get, ClassBytesSource.EMPTY, true).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), again);
		assertEquals(List.of(), again, "a wrapped handler isn't wrapped again");
		List<PreflightFinding> after = preflight(game, mod::get, ClassBytesSource.EMPTY, true).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());
	}

	@Test
	void aHandlerIntoTheClientsConstructorIsSkippedWhereItThrowsBeforeTheEntrypoints() throws Exception {
		// ENTRY-3: Sodium's @Inject into Minecraft's constructor, at the debug screen entries' construction, reads its config,
		// which its client initializer loads. Fabric runs the entrypoints near the constructor's start; Forge loads mods part
		// way through it, after that point. A handler into another of the client's methods runs after the entrypoints.
		String minecraft = "net/minecraft/client/Minecraft";
		ClassWriter target = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		target.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, minecraft, null, "java/lang/Object", null);
		MethodVisitor init = target.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		init.visitCode();
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, minecraft, "debugEntries", "()V", false);
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(0, 0);
		init.visitEnd();
		for (String name : new String[] {"debugEntries", "tick"}) {
			MethodVisitor mv = target.visitMethod(Opcodes.ACC_PUBLIC, name, "()V", null, null);
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		target.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(minecraft, target.toByteArray())::get);

		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "com/example/mixin/Client", null, "java/lang/Object", null);
		AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor values = m.visitArray("value");
		values.visit(null, Type.getObjectType(minecraft));
		values.visitEnd();
		m.visitEnd();
		// {handler, method, point}
		for (String[] handler : new String[][] {{"setFullscreen", "<init>", "INVOKE"}, {"onTick", "tick", "HEAD"}}) {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, handler[0], "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null);
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, handler[1]);
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", handler[2]);
			if (handler[2].equals("INVOKE")) at.visit("target", "L" + minecraft + ";debugEntries()V");
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			// As Sodium's: SodiumClientMod.options(), which throws before its client initializer has run.
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/example/Mod", "options", "()Lcom/example/Options;", false);
			mv.visitInsn(Opcodes.POP);
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		cw.visitEnd();
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Client", cw.toByteArray()));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Client"));

		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = preflight(game, mod::get, ClassBytesSource.EMPTY, true).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes)
				.get("com/example/mixin/Client.class");
		assertNotNull(repaired, notes.toString());
		assertEquals(1, notes.size(), notes.toString());
		assertTrue(notes.getFirst().severity() == PreflightFinding.Severity.INFO && notes.getFirst().message().contains("hooks into the client's constructor"),
				notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired).accept(node, 0);
		Map<String, MethodNode> byName = new HashMap<>();
		node.methods.forEach(mn -> byName.put(mn.name, mn));
		MethodNode wrapped = byName.get("setFullscreen");
		assertEquals(1, wrapped.tryCatchBlocks.size());
		AbstractInsnNode insn = wrapped.tryCatchBlocks.getFirst().handler;
		while (insn.getOpcode() < 0) insn = insn.getNext();
		assertEquals("com.example.mixin.Client.setFullscreen", ((LdcInsnNode) insn).cst);
		insn = insn.getNext();
		assertTrue(insn instanceof MethodInsnNode call && call.owner.equals("io/github/cmdralph/fabricated/loader/EarlyHandlers") && call.name.equals("failed"));
		assertEquals(Opcodes.RETURN, insn.getNext().getOpcode());
		assertTrue(byName.get("onTick").tryCatchBlocks.isEmpty(), "a handler into another of the client's methods runs after the entrypoints");
		ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				if (!name.equals("com.example.mixin.Client")) throw new ClassNotFoundException(name);
				return defineClass(name, repaired, 0, repaired.length);
			}
		};
		Class.forName("com.example.mixin.Client", true, loader);
		mod.put("com/example/mixin/Client", repaired);
		List<PreflightFinding> after = preflight(game, mod::get, ClassBytesSource.EMPTY, true).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());
	}

	@Test
	void aHandlerIntoTheWindowsCreationIsSkippedWhereItThrowsBeforeTheEntrypoints() throws Exception {
		// ENTRY-4: Sodium's @Inject into 26.3's GlBackend.createWindow, at SDL's window creation, reads its config to set the
		// context's hints. Fabric creates the window after the entrypoints; Forge 66 creates it before it loads mods. Up to
		// 26.2 the window's constructor creates it with GLFW. A handler into code not creating the window is left alone.
		String backend = "com/mojang/renderpearl/backend/opengl/GlBackend";
		String sdl = "org/lwjgl/sdl/SDLVideo";
		ClassWriter target = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		target.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, backend, null, "java/lang/Object", null);
		// {method, the window's creation it calls: owner, name, descriptor}
		String[][] methods = {
				{"createWindow", sdl, "SDL_CreateWindow", "(Ljava/lang/CharSequence;IIJ)J"},
				{"createGlfwWindow", "org/lwjgl/glfw/GLFW", "glfwCreateWindow", "(IILjava/lang/CharSequence;JJ)J"},
				{"present", null, null, null}};
		for (String[] method : methods) {
			MethodVisitor mv = target.visitMethod(Opcodes.ACC_PUBLIC, method[0], "()J", null, null);
			mv.visitCode();
			if (method[1] == null) {
				mv.visitInsn(Opcodes.LCONST_0);
			} else if (method[1].equals(sdl)) {
				mv.visitLdcInsn("Minecraft");
				mv.visitInsn(Opcodes.ICONST_0);
				mv.visitInsn(Opcodes.ICONST_0);
				mv.visitInsn(Opcodes.LCONST_0);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, method[1], method[2], method[3], false);
			} else {
				mv.visitInsn(Opcodes.ICONST_0);
				mv.visitInsn(Opcodes.ICONST_0);
				mv.visitLdcInsn("Minecraft");
				mv.visitInsn(Opcodes.LCONST_0);
				mv.visitInsn(Opcodes.LCONST_0);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, method[1], method[2], method[3], false);
			}
			mv.visitInsn(Opcodes.LRETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		target.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(backend, target.toByteArray())::get);

		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "com/example/mixin/Window", null, "java/lang/Object", null);
		AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor values = m.visitArray("value");
		values.visit(null, Type.getObjectType(backend));
		values.visitEnd();
		m.visitEnd();
		// {handler, method, point}
		String[][] handlers = {{"setAdditionalWindowHints", "createWindow", "INVOKE"}, {"setHints", "createGlfwWindow", "HEAD"}, {"onPresent", "present", "HEAD"}};
		for (String[] handler : handlers) {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, handler[0], "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V", null, null);
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor selectors = ann.visitArray("method");
			selectors.visit(null, handler[1]);
			selectors.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", handler[2]);
			if (handler[2].equals("INVOKE")) at.visit("target", "L" + sdl + ";SDL_CreateWindow(Ljava/lang/CharSequence;IIJ)J");
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			// As Sodium's: SodiumClientMod.options(), which throws before its client initializer has run.
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/example/Mod", "options", "()Lcom/example/Options;", false);
			mv.visitInsn(Opcodes.POP);
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		cw.visitEnd();
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Window", cw.toByteArray()));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Window"));

		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = preflight(game, mod::get, ClassBytesSource.EMPTY, true).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes)
				.get("com/example/mixin/Window.class");
		assertNotNull(repaired, notes.toString());
		assertEquals(2, notes.size(), notes.toString());
		assertTrue(notes.stream().allMatch(n -> n.severity() == PreflightFinding.Severity.INFO && n.message().contains("hooks into the creation of the client's window")),
				notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired).accept(node, 0);
		Map<String, MethodNode> byName = new HashMap<>();
		node.methods.forEach(mn -> byName.put(mn.name, mn));
		for (String name : new String[] {"setAdditionalWindowHints", "setHints"}) {
			MethodNode wrapped = byName.get(name);
			assertEquals(1, wrapped.tryCatchBlocks.size(), name);
			AbstractInsnNode insn = wrapped.tryCatchBlocks.getFirst().handler;
			while (insn.getOpcode() < 0) insn = insn.getNext();
			assertEquals("com.example.mixin.Window." + name, ((LdcInsnNode) insn).cst);
			insn = insn.getNext();
			assertTrue(insn instanceof MethodInsnNode call && call.owner.equals("io/github/cmdralph/fabricated/loader/EarlyHandlers") && call.name.equals("failed"),
					name);
			assertEquals(Opcodes.RETURN, insn.getNext().getOpcode(), name);
		}
		assertTrue(byName.get("onPresent").tryCatchBlocks.isEmpty(), "a handler into code not creating the window runs after the entrypoints");
		ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				if (!name.equals("com.example.mixin.Window")) throw new ClassNotFoundException(name);
				return defineClass(name, repaired, 0, repaired.length);
			}
		};
		Class.forName("com.example.mixin.Window", true, loader);
		mod.put("com/example/mixin/Window", repaired);
		List<PreflightFinding> after = preflight(game, mod::get, ClassBytesSource.EMPTY, true).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());
	}

	@Test
	void anInjectionIntoAMethodNeoForgeDeprecatedIsPointedAtItsReplacement() throws Exception {
		// DEPRECATED-1: FrozenLib hooks BoneMealItem.growCrop, which NeoForge's build deprecates for applyBonemeal, taking
		// the player too, and keeps only to call that on a server level (a jump, so no plain overload).
		java.util.function.Function<Boolean, byte[]> target = deprecated -> {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
			MethodVisitor grow = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | (deprecated ? Opcodes.ACC_DEPRECATED : 0), "grow", "(Ljava/lang/String;)Z", null, null);
			if (deprecated) grow.visitAnnotation("Ljava/lang/Deprecated;", true).visitEnd();
			org.objectweb.asm.Label otherwise = new org.objectweb.asm.Label();
			grow.visitCode();
			grow.visitVarInsn(Opcodes.ALOAD, 0);
			grow.visitJumpInsn(Opcodes.IFNULL, otherwise);
			grow.visitVarInsn(Opcodes.ALOAD, 0);
			grow.visitInsn(Opcodes.ACONST_NULL);
			grow.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET, "apply", "(Ljava/lang/String;Ljava/lang/Object;)Z", false);
			grow.visitInsn(Opcodes.IRETURN);
			grow.visitLabel(otherwise);
			grow.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			grow.visitInsn(Opcodes.ICONST_0);
			grow.visitInsn(Opcodes.IRETURN);
			grow.visitMaxs(0, 0);
			grow.visitEnd();
			MethodVisitor apply = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "apply", "(Ljava/lang/String;Ljava/lang/Object;)Z", null, null);
			apply.visitCode();
			apply.visitInsn(Opcodes.ICONST_0);
			apply.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET, "helper", "(I)V", false);
			apply.visitInsn(Opcodes.ICONST_1);
			apply.visitInsn(Opcodes.IRETURN);
			apply.visitMaxs(0, 0);
			apply.visitEnd();
			MethodVisitor helper = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "helper", "(I)V", null, null);
			helper.visitCode();
			helper.visitInsn(Opcodes.RETURN);
			helper.visitMaxs(0, 0);
			helper.visitEnd();
			// useOn: where vanilla's calls growCrop, NeoForge's calls applyBonemeal with the player.
			MethodVisitor use = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "use", "()V", null, null);
			use.visitCode();
			use.visitLdcInsn("pos");
			use.visitInsn(Opcodes.ACONST_NULL);
			use.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET, "apply", "(Ljava/lang/String;Ljava/lang/Object;)Z", false);
			use.visitInsn(Opcodes.POP);
			use.visitInsn(Opcodes.RETURN);
			use.visitMaxs(0, 0);
			use.visitEnd();
			cw.visitEnd();
			return cw.toByteArray();
		};
		String handlerDesc = "(Ljava/lang/String;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V";
		byte[] mixin = mixin("com/example/mixin/BoneMeal", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "onGrow", handlerDesc, null, null);
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "grow");
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
			mv.visitMaxs(0, 2);
			mv.visitEnd();
			MethodVisitor atCall = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "beforeGrow", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null);
			AnnotationVisitor callAnn = atCall.visitAnnotation(INJECT, false);
			AnnotationVisitor callMethods = callAnn.visitArray("method");
			callMethods.visit(null, "use");
			callMethods.visitEnd();
			AnnotationVisitor callAts = callAnn.visitArray("at");
			AnnotationVisitor callAt = callAts.visitAnnotation(null, AT);
			callAt.visit("value", "INVOKE");
			callAt.visit("target", "L" + TARGET + ";grow(Ljava/lang/String;)Z");
			callAt.visit("ordinal", 0);
			callAt.visitEnd();
			callAts.visitEnd();
			callAnn.visitEnd();
			atCall.visitCode();
			atCall.visitInsn(Opcodes.RETURN);
			atCall.visitMaxs(0, 1);
			atCall.visitEnd();
		});
		MixinConfigFile config = MixinConfigFile.parse(config(true, "BoneMeal"));
		ClassIndex game = new ClassIndex(Map.of(TARGET, target.apply(true))::get);
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/BoneMeal", mixin));
		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight(game, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		assertEquals(2, notes.size(), notes.toString());
		assertTrue(notes.getFirst().message().contains("grow(Ljava/lang/String;)Z, which NeoForge deprecated for apply(Ljava/lang/String;Ljava/lang/Object;)Z"), notes.toString());
		assertTrue(notes.get(1).message().contains("grow(Ljava/lang/String;)Z, which NeoForge deprecated: this game build calls its replacement "
				+ TARGET.replace('/', '.') + ".apply(Ljava/lang/String;Ljava/lang/Object;)Z instead"), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get("com/example/mixin/BoneMeal.class")).accept(node, 0);
		MethodNode bridge = node.methods.stream().filter(m -> m.name.equals("onGrow$fabricated")).findFirst().orElseThrow();
		assertEquals("(Ljava/lang/String;Ljava/lang/Object;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V", bridge.desc);
		mod.put("com/example/mixin/BoneMeal", repaired.get("com/example/mixin/BoneMeal.class"));
		List<PreflightFinding> after = preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());

		// A vanilla method (never deprecated) calling such a method is no sign its code moved there.
		assertTrue(preflight(new ClassIndex(Map.of(TARGET, target.apply(false))::get), Map.of("com/example/mixin/BoneMeal", mixin)::get,
				ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
	}

	@Test
	void anInjectionIntoADeprecatedMethodThatCallsItselfIsPointedAtTheOverloadOfItsNameTakingOneMore() throws Exception {
		// IRIS-2: Iris redirects RenderSystem.setShader in ParticleEngine.render. Forge 52 keeps vanilla's render,
		// deprecated, calling itself where Forge meant its overload taking a frustum, which holds vanilla's code and which
		// the game calls instead.
		java.util.function.Function<Boolean, byte[]> target = deprecated -> {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
			MethodVisitor old = cw.visitMethod(Opcodes.ACC_PUBLIC | (deprecated ? Opcodes.ACC_DEPRECATED : 0), "tick", "(I)V", null, null);
			if (deprecated) old.visitAnnotation("Ljava/lang/Deprecated;", true).visitEnd();
			old.visitCode();
			old.visitVarInsn(Opcodes.ALOAD, 0);
			old.visitVarInsn(Opcodes.ILOAD, 1);
			old.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "tick", "(I)V", false);
			old.visitInsn(Opcodes.RETURN);
			old.visitMaxs(0, 0);
			old.visitEnd();
			MethodVisitor current = cw.visitMethod(Opcodes.ACC_PUBLIC, "tick", "(ILjava/lang/Object;)V", null, null);
			current.visitCode();
			current.visitVarInsn(Opcodes.ILOAD, 1);
			current.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET, "helper", "(I)V", false);
			current.visitInsn(Opcodes.RETURN);
			current.visitMaxs(0, 0);
			current.visitEnd();
			MethodVisitor helper = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "helper", "(I)V", null, null);
			helper.visitCode();
			helper.visitInsn(Opcodes.RETURN);
			helper.visitMaxs(0, 0);
			helper.visitEnd();
			cw.visitEnd();
			return cw.toByteArray();
		};
		byte[] mixin = mixin("com/example/mixin/Shader", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "setShader", "(I)V", null, null);
			AnnotationVisitor ann = mv.visitAnnotation(REDIRECT, true);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "tick");
			methods.visitEnd();
			AnnotationVisitor at = ann.visitAnnotation("at", AT);
			at.visit("value", "INVOKE");
			at.visit("target", "L" + TARGET + ";helper(I)V");
			at.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 2);
			mv.visitEnd();
		});
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Shader"));
		ClassIndex game = new ClassIndex(Map.of(TARGET, target.apply(true))::get);
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Shader", mixin));
		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = preflight(game, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes)
				.get("com/example/mixin/Shader.class");
		assertNotNull(repaired, notes.toString());
		assertTrue(notes.stream().anyMatch(n -> n.message().contains("tick(I)V, which NeoForge deprecated for tick(ILjava/lang/Object;)V")), notes.toString());
		mod.put("com/example/mixin/Shader", repaired);
		assertTrue(preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());

		// A vanilla method (never deprecated) calling itself is no sign its code is in another of its name.
		assertTrue(preflight(new ClassIndex(Map.of(TARGET, target.apply(false))::get), Map.of("com/example/mixin/Shader", mixin)::get,
				ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
	}

	@Test
	void anInjectionIntoCodeNeoForgeSplitOffNextToAnEntryPointWithoutItsEventIsPointedThere() throws Exception {
		// SPLIT-2: architectury hooks PersistentEntitySectionManager.addEntity for its entity add event, keeping the level
		// its duck interface is given. NeoForge's build posts its own event there, then calls addEntityWithoutEvent, which
		// has vanilla's code and parameters, and which its addNewEntityWithoutEvent calls too.
		String desc = "(Ljava/lang/Object;Z)Z";
		ClassWriter target = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		target.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor add = target.visitMethod(Opcodes.ACC_PRIVATE, "addEntity", desc, null, null);
		add.visitCode();
		add.visitMethodInsn(Opcodes.INVOKESTATIC, "net/neoforged/Events", "post", "()V", false);
		add.visitVarInsn(Opcodes.ALOAD, 0);
		add.visitVarInsn(Opcodes.ALOAD, 1);
		add.visitVarInsn(Opcodes.ILOAD, 2);
		add.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "addEntityWithoutEvent", desc, false);
		add.visitInsn(Opcodes.IRETURN);
		add.visitMaxs(0, 0);
		add.visitEnd();
		MethodVisitor without = target.visitMethod(Opcodes.ACC_PRIVATE, "addEntityWithoutEvent", desc, null, null);
		without.visitCode();
		without.visitVarInsn(Opcodes.ALOAD, 0);
		without.visitInsn(Opcodes.ICONST_0);
		without.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "helper", "(I)V", false);
		without.visitInsn(Opcodes.ICONST_1);
		without.visitInsn(Opcodes.IRETURN);
		without.visitMaxs(0, 0);
		without.visitEnd();
		MethodVisitor entry = target.visitMethod(Opcodes.ACC_PUBLIC, "addNewEntityWithoutEvent", "(Ljava/lang/Object;)Z", null, null);
		entry.visitCode();
		entry.visitVarInsn(Opcodes.ALOAD, 0);
		entry.visitVarInsn(Opcodes.ALOAD, 1);
		entry.visitInsn(Opcodes.ICONST_0);
		entry.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "addEntityWithoutEvent", desc, false);
		entry.visitInsn(Opcodes.IRETURN);
		entry.visitMaxs(0, 0);
		entry.visitEnd();
		MethodVisitor helper = target.visitMethod(Opcodes.ACC_PUBLIC, "helper", "(I)V", null, null);
		helper.visitCode();
		helper.visitInsn(Opcodes.RETURN);
		helper.visitMaxs(0, 0);
		helper.visitEnd();
		target.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(TARGET, target.toByteArray())::get);
		String handlerDesc = "(Ljava/lang/Object;ZLorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V";
		java.util.function.Function<Boolean, byte[]> mixin = shared -> mixin("com/example/mixin/Entities", false, cw -> {
			cw.visitField(Opcodes.ACC_PRIVATE, "level", "Ljava/lang/Object;", null, null).visitEnd();
			MethodVisitor attach = cw.visitMethod(Opcodes.ACC_PUBLIC, "attach", "(Ljava/lang/Object;)V", null, null);
			attach.visitCode();
			attach.visitVarInsn(Opcodes.ALOAD, 0);
			attach.visitVarInsn(Opcodes.ALOAD, 1);
			attach.visitFieldInsn(Opcodes.PUTFIELD, "com/example/mixin/Entities", "level", "Ljava/lang/Object;");
			attach.visitInsn(Opcodes.RETURN);
			attach.visitMaxs(2, 2);
			attach.visitEnd();
			for (String name : shared ? List.of("onAdd", "alsoOnAdd") : List.of("onAdd")) {
				MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, name, handlerDesc, null, null);
				AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
				AnnotationVisitor methods = ann.visitArray("method");
				methods.visit(null, "addEntity");
				methods.visitEnd();
				AnnotationVisitor ats = ann.visitArray("at");
				AnnotationVisitor at = ats.visitAnnotation(null, AT);
				at.visit("value", "INVOKE");
				at.visit("target", "L" + TARGET + ";helper(I)V");
				at.visitEnd();
				ats.visitEnd();
				ann.visitEnd();
				mv.visitCode();
				mv.visitVarInsn(Opcodes.ALOAD, 0);
				mv.visitInsn(Opcodes.ACONST_NULL);
				mv.visitFieldInsn(Opcodes.PUTFIELD, "com/example/mixin/Entities", "level", "Ljava/lang/Object;");
				mv.visitInsn(Opcodes.RETURN);
				mv.visitMaxs(2, 4);
				mv.visitEnd();
			}
		});
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Entities"));
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Entities", mixin.apply(false)));
		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight(game, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		assertEquals(1, notes.size(), notes.toString());
		assertTrue(notes.getFirst().message().contains("addEntity" + desc + ", the code of which there NeoForge split off into addEntityWithoutEvent" + desc), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get("com/example/mixin/Entities.class")).accept(node, 0);
		assertTrue(node.methods.stream().noneMatch(m -> m.name.contains("$fabricated")), "the same parameters need no bridge");
		mod.put("com/example/mixin/Entities", repaired.get("com/example/mixin/Entities.class"));
		List<PreflightFinding> after = preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());

		// Two injectors keeping the same state stay where they are: moving one alone could part them.
		assertTrue(preflight(game, Map.of("com/example/mixin/Entities", mixin.apply(true))::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
	}

	@Test
	void aConstantModifierInCodeNeoForgeSplitOffIsPointedThere() throws Exception {
		// SPLIT-2: Carpet's fill updates rule changes the shape update flag (16) in Level.setBlock, which NeoForge's build
		// tests in markAndNotifyBlock.
		ClassWriter target = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		target.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor set = target.visitMethod(Opcodes.ACC_PUBLIC, "setBlock", "(I)Z", null, null);
		set.visitCode();
		set.visitVarInsn(Opcodes.ALOAD, 0);
		set.visitVarInsn(Opcodes.ILOAD, 1);
		set.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "markAndNotifyBlock", "(I)V", false);
		set.visitInsn(Opcodes.ICONST_1);
		set.visitInsn(Opcodes.IRETURN);
		set.visitMaxs(0, 0);
		set.visitEnd();
		MethodVisitor notify = target.visitMethod(Opcodes.ACC_PUBLIC, "markAndNotifyBlock", "(I)V", null, null);
		notify.visitCode();
		notify.visitVarInsn(Opcodes.ILOAD, 1);
		notify.visitIntInsn(Opcodes.BIPUSH, 16);
		notify.visitInsn(Opcodes.IAND);
		notify.visitInsn(Opcodes.POP);
		notify.visitInsn(Opcodes.RETURN);
		notify.visitMaxs(0, 0);
		notify.visitEnd();
		target.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(TARGET, target.toByteArray())::get);
		java.util.function.Function<String, byte[]> mixin = handlerDesc -> mixin("com/example/mixin/Fill", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "skipUpdates", handlerDesc, null, null);
			AnnotationVisitor ann = mv.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/ModifyConstant;", false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "setBlock(I)Z");
			methods.visitEnd();
			AnnotationVisitor constants = ann.visitArray("constant");
			AnnotationVisitor constant = constants.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/Constant;");
			constant.visit("intValue", 16);
			constant.visitEnd();
			constants.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			mv.visitVarInsn(Opcodes.ILOAD, 1);
			mv.visitInsn(Opcodes.IRETURN);
			mv.visitMaxs(1, 3);
			mv.visitEnd();
		});
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Fill"));
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Fill", mixin.apply("(I)I")));
		List<PreflightFinding> before = preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(before.getFirst().message().contains("expects the constant 16"), before.toString());
		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight(game, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		assertEquals(1, notes.size(), notes.toString());
		assertTrue(notes.getFirst().message().contains("setBlock(I)Z, the code of which there NeoForge split off into markAndNotifyBlock(I)V"), notes.toString());
		mod.put("com/example/mixin/Fill", repaired.get("com/example/mixin/Fill.class"));
		List<PreflightFinding> after = preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());

		// A handler also taking the method's parameters stays: the other method's differ.
		assertTrue(preflight(game, Map.of("com/example/mixin/Fill", mixin.apply("(II)I"))::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
	}

	@Test
	void aLambdaNamedWithoutDescriptorIsFoundByTheParametersItsHandlerTakes() throws Exception {
		// LAMBDA-4: Ledger's portal hook names PortalShape's lambda by Mojang's 1.21.11 numbering, without a descriptor;
		// NeoForge's recompiled build numbers the lambdas of createPortalBlocks otherwise.
		ClassWriter target = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		target.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		for (String[] lambda : new String[][] {{"lambda$create$5", "(Ljava/lang/Object;Ljava/lang/String;)V"}, {"lambda$create$6", "(I)V"},
				{"lambda$other$2", "(Ljava/lang/Object;Ljava/lang/String;)V"}}) {
			MethodVisitor mv = target.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, lambda[0], lambda[1], null, null);
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 2);
			mv.visitEnd();
		}
		target.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(TARGET, target.toByteArray())::get);
		java.util.function.Function<String, byte[]> mixin = handlerDesc -> mixin("com/example/mixin/Portal", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "logPortal", handlerDesc, null, null);
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "lambda$create$2");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", "HEAD");
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 3);
			mv.visitEnd();
		});
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Portal"));
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Portal",
				mixin.apply("(Ljava/lang/Object;Ljava/lang/String;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V")));
		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight(game, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		assertEquals(1, notes.size(), notes.toString());
		assertTrue(notes.getFirst().message().contains("targets lambda$create$2, which this game build numbers differently"), notes.toString());
		mod.put("com/example/mixin/Portal", repaired.get("com/example/mixin/Portal.class"));
		assertTrue(preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());

		// A handler taking no parameter tells none of the lambdas apart.
		assertTrue(preflight(game, Map.of("com/example/mixin/Portal", mixin.apply("(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V"))::get,
				ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
	}

	@Test
	void aMixinIntoAnAnonymousClassNumberedOtherwiseIsPointedAtTheOneThatFits() throws Exception {
		// ANON-1: Polymer's hook into ByteBufCodecs' registry codec names its anonymous class by Mojang's numbering;
		// NeoForge's recompiled build numbers it otherwise.
		String outer = "net/minecraft/world/Codecs";
		java.util.function.BiFunction<String, String, byte[]> anonymous = (name, encodeDesc) -> {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cw.visit(Opcodes.V21, Opcodes.ACC_FINAL, name, null, "java/lang/Object", null);
			cw.visitOuterClass(outer, "registry", "()V");
			cw.visitInnerClass(name, null, null, Opcodes.ACC_FINAL);
			if (encodeDesc.contains("Integer")) cw.visitField(Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, "val$key", "Ljava/lang/String;", null, null).visitEnd();
			MethodVisitor encode = cw.visitMethod(Opcodes.ACC_PUBLIC, "encode", encodeDesc, null, null);
			encode.visitCode();
			encode.visitInsn(Opcodes.RETURN);
			encode.visitMaxs(0, 0);
			encode.visitEnd();
			cw.visitEnd();
			return cw.toByteArray();
		};
		java.util.function.Function<Integer, byte[]> outerClass = count -> {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, outer, null, "java/lang/Object", null);
			for (int n = 1; n <= count; n++) cw.visitInnerClass(outer + "$" + n, null, null, Opcodes.ACC_FINAL);
			cw.visitEnd();
			return cw.toByteArray();
		};
		ClassWriter mixin = new ClassWriter(0);
		mixin.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "com/example/mixin/Registry", null, "java/lang/Object", null);
		AnnotationVisitor m = mixin.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor targets = m.visitArray("targets");
		targets.visit(null, outer + "$1");
		targets.visitEnd();
		m.visitEnd();
		mixin.visitField(Opcodes.ACC_PRIVATE, "val$key", "Ljava/lang/String;", null, null).visitAnnotation(SHADOW, false).visitEnd();
		MethodVisitor mv = mixin.visitMethod(Opcodes.ACC_PRIVATE, "onEncode", "(Ljava/lang/Integer;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null);
		AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
		AnnotationVisitor methods = ann.visitArray("method");
		methods.visit(null, "encode(Ljava/lang/Integer;)V");
		methods.visitEnd();
		AnnotationVisitor ats = ann.visitArray("at");
		AnnotationVisitor at = ats.visitAnnotation(null, AT);
		at.visit("value", "HEAD");
		at.visitEnd();
		ats.visitEnd();
		ann.visitEnd();
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitFieldInsn(Opcodes.GETFIELD, "com/example/mixin/Registry", "val$key", "Ljava/lang/String;");
		mv.visitInsn(Opcodes.POP);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(1, 3);
		mv.visitEnd();
		mixin.visitEnd();
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Registry", mixin.toByteArray()));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Registry"));
		ClassIndex game = new ClassIndex(Map.of(outer, outerClass.apply(2), outer + "$1", anonymous.apply(outer + "$1", "(Ljava/lang/String;)V"),
				outer + "$2", anonymous.apply(outer + "$2", "(Ljava/lang/Integer;)V"))::get);
		assertFalse(preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());

		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight(game, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		assertEquals(1, notes.size(), notes.toString());
		assertTrue(notes.getFirst().message().contains("net.minecraft.world.Codecs$1 doesn't fit the mixin in this game build, whose anonymous classes are numbered "
				+ "differently; net.minecraft.world.Codecs$2 does"), notes.toString());
		mod.put("com/example/mixin/Registry", repaired.get("com/example/mixin/Registry.class"));
		assertTrue(preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());

		// ANON-2: a refmap names the selector's owner, the class the mixin no longer targets, which Mixin refuses; the
		// selector is written out with the class it was pointed at.
		Refmap owned = Refmap.parse("{\"mappings\":{\"com/example/mixin/Registry\":{\"encode(Ljava/lang/Integer;)V\":"
				+ "\"L" + outer + "$1;encode(Ljava/lang/Integer;)V\"}}}");
		Map<String, byte[]> ownedMod = new HashMap<>(Map.of("com/example/mixin/Registry", mixin.toByteArray()));
		List<PreflightFinding> ownedNotes = new ArrayList<>();
		byte[] ownedRepaired = preflight(game, ownedMod::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, owned, Set.of(), ownedNotes).get("com/example/mixin/Registry.class");
		assertNotNull(ownedRepaired, ownedNotes.toString());
		assertTrue(ownedNotes.getFirst().message().endsWith("with the 1 selector(s) of its injectors naming net.minecraft.world.Codecs$1 as their owner"),
				ownedNotes.toString());
		ClassNode ownedNode = new ClassNode();
		new ClassReader(ownedRepaired).accept(ownedNode, 0);
		AnnotationNode ownedInject = ownedNode.methods.stream().filter(method -> method.name.equals("onEncode")).findFirst().orElseThrow().invisibleAnnotations.getFirst();
		assertEquals(List.of("L" + outer + "$2;encode(Ljava/lang/Integer;)V"), Annotations.value(ownedInject, "method"));
		assertEquals(List.of(outer + "$2"), Annotations.value(ownedNode.invisibleAnnotations.getFirst(), "targets"));
		ownedMod.put("com/example/mixin/Registry", ownedRepaired);
		assertTrue(preflight(game, ownedMod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, owned, Set.of()).isEmpty());

		// Two that fit: which one the mod meant can't be told.
		ClassIndex twice = new ClassIndex(Map.of(outer, outerClass.apply(3), outer + "$1", anonymous.apply(outer + "$1", "(Ljava/lang/String;)V"),
				outer + "$2", anonymous.apply(outer + "$2", "(Ljava/lang/Integer;)V"), outer + "$3", anonymous.apply(outer + "$3", "(Ljava/lang/Integer;)V"))::get);
		assertTrue(preflight(twice, Map.of("com/example/mixin/Registry", mixin.toByteArray())::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());

		// CAPTURE-1: NeoForge's 1.21.11 build names the captured value after its own parameter name; the one value of the
		// shadow's type is taken for it, and the shadow renamed.
		byte[] otherName = anonymous.apply(outer + "$2", "(Ljava/lang/Integer;)V");
		ClassNode otherNode = new ClassNode();
		new ClassReader(otherName).accept(otherNode, 0);
		otherNode.fields.getFirst().name = "val$p_1_";
		ClassWriter otherWriter = new ClassWriter(0);
		otherNode.accept(otherWriter);
		ClassIndex renamedGame = new ClassIndex(Map.of(outer, outerClass.apply(2), outer + "$1", anonymous.apply(outer + "$1", "(Ljava/lang/String;)V"),
				outer + "$2", otherWriter.toByteArray())::get);
		Map<String, byte[]> renamedMod = new HashMap<>(Map.of("com/example/mixin/Registry", mixin.toByteArray()));
		List<PreflightFinding> renamedNotes = new ArrayList<>();
		byte[] renamed = preflight(renamedGame, renamedMod::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), renamedNotes).get("com/example/mixin/Registry.class");
		assertNotNull(renamed, renamedNotes.toString());
		assertTrue(renamedNotes.getFirst().message().contains("Codecs$2 does, naming the values it captures otherwise {val$key=val$p_1_}"), renamedNotes.toString());
		ClassNode renamedMixin = new ClassNode();
		new ClassReader(renamed).accept(renamedMixin, 0);
		assertEquals("val$p_1_", renamedMixin.fields.getFirst().name);
		MethodNode handler = renamedMixin.methods.stream().filter(method -> method.name.equals("onEncode")).findFirst().orElseThrow();
		assertEquals("val$p_1_", ((org.objectweb.asm.tree.FieldInsnNode) handler.instructions.get(1)).name, "its read of the value too");
		renamedMod.put("com/example/mixin/Registry", renamed);
		assertTrue(preflight(renamedGame, renamedMod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());
	}

	@Test
	void capturedValuesOfOneTypeAreToldApartByTheirPlaceInVanillasClass() throws Exception {
		// CAPTURE-2: Ledger's and malilib's hooks into ChestBlock's menu provider (ChestBlock$2$1) shadow the two chest block
		// entities it captures, val$first and val$second, which NeoForge's 1.21.11 build names after its own parameters.
		String outer = "net/minecraft/world/Chests";
		String anonymous = outer + "$1";
		String chest = "Ljava/lang/String;";
		// An anonymous class capturing two values of one type, whose menu() reads them in the order given.
		java.util.function.BiFunction<List<String>, List<String>, byte[]> provider = (fields, readOrder) -> {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cw.visit(Opcodes.V21, Opcodes.ACC_FINAL, anonymous, null, "java/lang/Object", null);
			cw.visitOuterClass(outer, "combiner", "()V");
			cw.visitInnerClass(anonymous, null, null, Opcodes.ACC_FINAL);
			for (String field : fields) cw.visitField(Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, field, chest, null, null).visitEnd();
			MethodVisitor menu = cw.visitMethod(Opcodes.ACC_PUBLIC, "menu", "()V", null, null);
			menu.visitCode();
			for (String field : readOrder) {
				menu.visitVarInsn(Opcodes.ALOAD, 0);
				menu.visitFieldInsn(Opcodes.GETFIELD, anonymous, field, chest);
				menu.visitInsn(Opcodes.POP);
			}
			menu.visitInsn(Opcodes.RETURN);
			menu.visitMaxs(0, 0);
			menu.visitEnd();
			cw.visitEnd();
			return cw.toByteArray();
		};
		ClassWriter outerClass = new ClassWriter(0);
		outerClass.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, outer, null, "java/lang/Object", null);
		outerClass.visitInnerClass(anonymous, null, null, Opcodes.ACC_FINAL);
		outerClass.visitEnd();
		byte[] outerBytes = outerClass.toByteArray();

		// The mixin shadows the second one and reads it from a hook into menu().
		ClassWriter mixin = new ClassWriter(0);
		mixin.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "com/example/mixin/Chest", null, "java/lang/Object", null);
		AnnotationVisitor m = mixin.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor targets = m.visitArray("targets");
		targets.visit(null, anonymous);
		targets.visitEnd();
		m.visitEnd();
		mixin.visitField(Opcodes.ACC_PRIVATE, "val$second", chest, null, null).visitAnnotation(SHADOW, false).visitEnd();
		MethodVisitor mv = mixin.visitMethod(Opcodes.ACC_PRIVATE, "onMenu", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null);
		AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
		AnnotationVisitor methods = ann.visitArray("method");
		methods.visit(null, "menu()V");
		methods.visitEnd();
		AnnotationVisitor ats = ann.visitArray("at");
		AnnotationVisitor at = ats.visitAnnotation(null, AT);
		at.visit("value", "HEAD");
		at.visitEnd();
		ats.visitEnd();
		ann.visitEnd();
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitFieldInsn(Opcodes.GETFIELD, "com/example/mixin/Chest", "val$second", chest);
		mv.visitInsn(Opcodes.POP);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(1, 2);
		mv.visitEnd();
		mixin.visitEnd();
		byte[] mixinBytes = mixin.toByteArray();
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Chest"));

		Map<String, byte[]> vanilla = Map.of(anonymous, provider.apply(List.of("val$first", "val$second"), List.of("val$first", "val$second", "val$first")));
		ClassIndex neoForge = new ClassIndex(Map.of(outer, outerBytes,
				anonymous, provider.apply(List.of("val$p_1_", "val$p_2_"), List.of("val$p_1_", "val$p_2_", "val$p_1_")))::get);

		// Without vanilla's class, two of the type can't be told apart.
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Chest", mixinBytes));
		assertTrue(preflight(neoForge, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());

		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = new MixinPreflight(neoForge, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes).get("com/example/mixin/Chest.class");
		assertNotNull(repaired, notes.toString());
		assertTrue(notes.getFirst().message().contains("names the values it captures otherwise in this game build {val$second=val$p_2_}"), notes.toString());
		ClassNode renamed = new ClassNode();
		new ClassReader(repaired).accept(renamed, 0);
		assertEquals("val$p_2_", renamed.fields.getFirst().name, "vanilla's second value of the type is this build's second");
		mod.put("com/example/mixin/Chest", repaired);
		assertTrue(preflight(neoForge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());

		// A method reading them in another order than vanilla's, matched by place, leaves the place unproven.
		ClassIndex reordered = new ClassIndex(Map.of(outer, outerBytes,
				anonymous, provider.apply(List.of("val$p_1_", "val$p_2_"), List.of("val$p_2_", "val$p_1_", "val$p_2_")))::get);
		assertTrue(new MixinPreflight(reordered, InstalledClasses.NONE, Map.of("com/example/mixin/Chest", mixinBytes)::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
	}

	@Test
	void shadowsOfCapturedValuesInSrgNamesFindThemUnderJavacsNames() throws Exception {
		// CAPTURE-3: Fabric API's interaction hook into ServerGamePacketListenerImpl$1 shadows the entity it captures and its
		// outer instance, which SRG names as any field (f_143670_); Forge 47's build, recompiled, has javac's val$entity
		// and this$0. Vanilla's class marks SRG's synthetic.
		String outer = "net/minecraft/server/Listener";
		String anonymous = outer + "$1";
		String entity = "Ljava/lang/Integer;";
		String listener = "L" + outer + ";";
		java.util.function.BiFunction<String[], Integer, byte[]> handler = (names, access) -> {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cw.visit(Opcodes.V17, Opcodes.ACC_FINAL, anonymous, null, "java/lang/Object", null);
			cw.visitOuterClass(outer, "handle", "()V");
			cw.visitInnerClass(anonymous, null, null, Opcodes.ACC_FINAL);
			cw.visitField(Opcodes.ACC_FINAL | access, names[0], entity, null, null).visitEnd();
			cw.visitField(Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, names[1], listener, null, null).visitEnd();
			MethodVisitor interact = cw.visitMethod(Opcodes.ACC_PUBLIC, "interact", "()V", null, null);
			interact.visitCode();
			interact.visitVarInsn(Opcodes.ALOAD, 0);
			interact.visitFieldInsn(Opcodes.GETFIELD, anonymous, names[0], entity);
			interact.visitInsn(Opcodes.POP);
			interact.visitInsn(Opcodes.RETURN);
			interact.visitMaxs(0, 0);
			interact.visitEnd();
			cw.visitEnd();
			return cw.toByteArray();
		};
		ClassWriter outerClass = new ClassWriter(0);
		outerClass.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, outer, null, "java/lang/Object", null);
		outerClass.visitInnerClass(anonymous, null, null, Opcodes.ACC_FINAL);
		outerClass.visitEnd();
		ClassIndex forge = new ClassIndex(Map.of(outer, outerClass.toByteArray(),
				anonymous, handler.apply(new String[] {"val$entity", "this$0"}, Opcodes.ACC_SYNTHETIC))::get);

		ClassWriter mixin = new ClassWriter(0);
		mixin.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "com/example/mixin/Interact", null, "java/lang/Object", null);
		AnnotationVisitor m = mixin.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor targets = m.visitArray("targets");
		targets.visit(null, anonymous);
		targets.visitEnd();
		m.visitEnd();
		mixin.visitField(0, "f_10_", entity, null, null).visitAnnotation(SHADOW, false).visitEnd();
		mixin.visitField(0, "f_11_", listener, null, null).visitAnnotation(SHADOW, false).visitEnd();
		MethodVisitor mv = mixin.visitMethod(Opcodes.ACC_PRIVATE, "onInteract", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null);
		AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
		AnnotationVisitor methods = ann.visitArray("method");
		methods.visit(null, "interact()V");
		methods.visitEnd();
		AnnotationVisitor ats = ann.visitArray("at");
		AnnotationVisitor at = ats.visitAnnotation(null, AT);
		at.visit("value", "HEAD");
		at.visitEnd();
		ats.visitEnd();
		ann.visitEnd();
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitFieldInsn(Opcodes.GETFIELD, "com/example/mixin/Interact", "f_10_", entity);
		mv.visitInsn(Opcodes.POP);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(1, 2);
		mv.visitEnd();
		mixin.visitEnd();
		byte[] mixinBytes = mixin.toByteArray();
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Interact"));

		// Without vanilla's class, a shadow in SRG's names can't be told for a captured value.
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Interact", mixinBytes));
		assertTrue(preflight(forge, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
		// Nor where vanilla's field is one the class declares.
		Map<String, byte[]> declared = Map.of(anonymous, handler.apply(new String[] {"f_10_", "f_11_"}, 0));
		assertTrue(new MixinPreflight(forge, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, declared::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());

		Map<String, byte[]> vanilla = Map.of(anonymous, handler.apply(new String[] {"f_10_", "f_11_"}, Opcodes.ACC_SYNTHETIC));
		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = new MixinPreflight(forge, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes).get("com/example/mixin/Interact.class");
		assertNotNull(repaired, notes.toString());
		assertTrue(notes.getFirst().message().contains("names the values it captures otherwise in this game build {f_10_=val$entity, f_11_=this$0}"),
				notes.toString());
		ClassNode renamed = new ClassNode();
		new ClassReader(repaired).accept(renamed, 0);
		assertEquals(List.of("val$entity", "this$0"), renamed.fields.stream().map(f -> f.name).toList());
		mod.put("com/example/mixin/Interact", repaired);
		assertTrue(preflight(forge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());
	}

	@Test
	void aLocalTakenByTypeWhereNeoForgesPatchAddedOneOfThatTypeIsNamedByVanillasStore() throws Exception {
		// LOCAL-2: Ledger's item pickup hooks take the stack (@Local ItemStack) at Inventory.add in ItemEntity.playerTouch,
		// where NeoForge's patch also keeps a copy of it for its pickup event: two locals of the type in scope there. Here
		// the patch's local comes first, so vanilla's is the second.
		String stack = "Ljava/lang/String;";
		java.util.function.Function<Boolean, byte[]> target = patched -> {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
			MethodVisitor touch = cw.visitMethod(Opcodes.ACC_PUBLIC, "touch", "()V", null, null);
			org.objectweb.asm.Label start = new org.objectweb.asm.Label();
			org.objectweb.asm.Label copyStart = new org.objectweb.asm.Label();
			org.objectweb.asm.Label itemStart = new org.objectweb.asm.Label();
			org.objectweb.asm.Label end = new org.objectweb.asm.Label();
			int item = patched ? 2 : 1;
			touch.visitCode();
			touch.visitLabel(start);
			if (patched) {
				touch.visitVarInsn(Opcodes.ALOAD, 0);
				touch.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "pickupCopy", "()" + stack, false);
				touch.visitVarInsn(Opcodes.ASTORE, 1);
				touch.visitLabel(copyStart);
			}
			touch.visitVarInsn(Opcodes.ALOAD, 0);
			touch.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "item", "()" + stack, false);
			touch.visitVarInsn(Opcodes.ASTORE, item);
			touch.visitLabel(itemStart);
			touch.visitVarInsn(Opcodes.ALOAD, item);
			touch.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET, "add", "(" + stack + ")Z", false);
			touch.visitInsn(Opcodes.POP);
			touch.visitInsn(Opcodes.RETURN);
			touch.visitLabel(end);
			touch.visitLocalVariable("this", "L" + TARGET + ";", null, start, end, 0);
			if (patched) touch.visitLocalVariable("copy", stack, null, copyStart, end, 1);
			touch.visitLocalVariable("item", stack, null, itemStart, end, item);
			touch.visitMaxs(0, 0);
			touch.visitEnd();
			cw.visitEnd();
			return cw.toByteArray();
		};
		byte[] mixin = mixin("com/example/mixin/Pickup", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "store", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;" + stack + ")V", null, null);
			mv.visitParameterAnnotation(1, "Lcom/llamalad7/mixinextras/sugar/Local;", false).visitEnd();
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "touch");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", "INVOKE");
			at.visit("target", "L" + TARGET + ";add(" + stack + ")Z");
			at.visit("shift", "AFTER");
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 3);
			mv.visitEnd();
		});
		ClassIndex neoForge = new ClassIndex(Map.of(TARGET, target.apply(true))::get);
		Map<String, byte[]> vanilla = Map.of(TARGET, target.apply(false));
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Pickup", mixin));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Pickup"));
		List<PreflightFinding> found = preflight(neoForge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(found.stream().anyMatch(f -> f.message().contains("has more than one of there")), found.toString());
		// Without vanilla's code, which of them is meant can't be told.
		assertTrue(preflight(neoForge, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());

		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = new MixinPreflight(neoForge, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes).get("com/example/mixin/Pickup.class");
		assertNotNull(repaired, notes.toString());
		assertTrue(notes.getFirst().message().contains("NeoForge's patch having added one; vanilla's is the one with ordinal 1 here"), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired).accept(node, 0);
		MethodNode handler = node.methods.stream().filter(m -> m.name.equals("store")).findFirst().orElseThrow();
		assertEquals(1, Annotations.value(Annotations.ofParameter(handler, 1).getFirst(), "ordinal"), "vanilla's stack, not the patch's copy");
		mod.put("com/example/mixin/Pickup", repaired);
		assertTrue(preflight(neoForge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());
	}

	@Test
	void aLocalTakenByTypeAndOrdinalWhereThePatchAddedOneOfThatTypeBeforeItGetsVanillasOrdinal() throws Exception {
		// LOCAL-4: Indigo takes @Local(ordinal = 0) Map in SectionCompiler.compile, vanilla's map of buffers by layer, where
		// Forge 61's patch first keeps the section's model data in a map: ordinal 0 here is the patch's.
		String map = "Ljava/util/Map;";
		java.util.function.Function<Boolean, byte[]> target = patched -> {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
			MethodVisitor compile = cw.visitMethod(Opcodes.ACC_PUBLIC, "compile", "()V", null, null);
			org.objectweb.asm.Label start = new org.objectweb.asm.Label();
			org.objectweb.asm.Label dataStart = new org.objectweb.asm.Label();
			org.objectweb.asm.Label buffersStart = new org.objectweb.asm.Label();
			org.objectweb.asm.Label end = new org.objectweb.asm.Label();
			int buffers = patched ? 2 : 1;
			compile.visitCode();
			compile.visitLabel(start);
			if (patched) {
				compile.visitVarInsn(Opcodes.ALOAD, 0);
				compile.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "modelData", "()" + map, false);
				compile.visitVarInsn(Opcodes.ASTORE, 1);
				compile.visitLabel(dataStart);
			}
			compile.visitVarInsn(Opcodes.ALOAD, 0);
			compile.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "buffers", "()" + map, false);
			compile.visitVarInsn(Opcodes.ASTORE, buffers);
			compile.visitLabel(buffersStart);
			compile.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET, "positions", "()V", false);
			compile.visitInsn(Opcodes.RETURN);
			compile.visitLabel(end);
			compile.visitLocalVariable("this", "L" + TARGET + ";", null, start, end, 0);
			if (patched) compile.visitLocalVariable("modelData", map, null, dataStart, end, 1);
			compile.visitLocalVariable("buffers", map, null, buffersStart, end, buffers);
			compile.visitMaxs(0, 0);
			compile.visitEnd();
			cw.visitEnd();
			return cw.toByteArray();
		};
		byte[] mixin = mixin("com/example/mixin/Build", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "build", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;" + map + ")V", null, null);
			AnnotationVisitor local = mv.visitParameterAnnotation(1, "Lcom/llamalad7/mixinextras/sugar/Local;", false);
			local.visit("ordinal", 0);
			local.visitEnd();
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "compile");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", "INVOKE");
			at.visit("target", "L" + TARGET + ";positions()V");
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 3);
			mv.visitEnd();
		});
		Map<String, byte[]> vanilla = Map.of(TARGET, target.apply(false));
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Build", mixin));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Build"));
		ClassIndex forge = new ClassIndex(Map.of(TARGET, target.apply(true))::get);
		// A capture by its ordinal is valid as it stands: only vanilla's code tells it names the wrong local.
		assertTrue(preflight(forge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());
		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = new MixinPreflight(forge, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes).get("com/example/mixin/Build.class");
		assertNotNull(repaired, notes.toString());
		assertTrue(notes.getFirst().message().contains("with ordinal 0, vanilla's, which has ordinal 1"), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired).accept(node, 0);
		MethodNode handler = node.methods.stream().filter(m -> m.name.equals("build")).findFirst().orElseThrow();
		assertEquals(1, Annotations.value(Annotations.ofParameter(handler, 1).getFirst(), "ordinal"), "vanilla's map, not the patch's");
		// Where the build declares vanilla's locals alone, the ordinal stays.
		ClassIndex unpatched = new ClassIndex(vanilla::get);
		assertTrue(new MixinPreflight(unpatched, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
	}

	@Test
	void aVariableModifierOfAnArgumentByItsTypeIsLeftOutWhereTheMethodTakesNoneOfThatType() throws Exception {
		// CORPUS-57: Architectury's char-typed screen hooks modify the GuiEventListener vanilla's KeyboardHandler.charTyped
		// lambdas take (@ModifyVariable(at = @At("HEAD"), ordinal = 0, argsOnly = true)); NeoForge's 1.21.1 build passes
		// them a Screen, and Mixin found no argument of the handler's type ("(0/1) succeeded").
		String listener = "Lcom/example/Listener;";
		java.util.function.Function<String, byte[]> target = argument -> {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
			MethodVisitor lambda = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, "lambda$charTyped$6",
					"(" + argument + "II)V", null, null);
			lambda.visitCode();
			lambda.visitInsn(Opcodes.RETURN);
			lambda.visitMaxs(0, 3);
			lambda.visitEnd();
			cw.visitEnd();
			return cw.toByteArray();
		};
		java.util.function.BiFunction<Integer, Integer, byte[]> mixin = (ordinal, require) -> mixin("com/example/mixin/Keys", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "wrapCharTypedFirst", "(" + listener + ")" + listener, null, null);
			AnnotationVisitor ann = mv.visitAnnotation(MODIFY_VARIABLE, true);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "lambda$charTyped$6");
			methods.visitEnd();
			AnnotationVisitor at = ann.visitAnnotation("at", AT);
			at.visit("value", "HEAD");
			at.visitEnd();
			if (ordinal != null) ann.visit("ordinal", ordinal);
			if (require != null) ann.visit("require", require);
			ann.visit("argsOnly", true);
			ann.visitEnd();
			mv.visitCode();
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitMaxs(1, 1);
			mv.visitEnd();
		});
		MixinConfigFile required = MixinConfigFile.parse(config(true, "Keys"));
		MixinConfigFile optional = MixinConfigFile.parse(config(false, "Keys"));
		java.util.function.BiFunction<byte[], Boolean, List<PreflightFinding>> check = (keys, isRequired) -> {
			Map<String, byte[]> mod = Map.of("com/example/mixin/Keys", keys);
			return preflight(new ClassIndex(Map.of(TARGET, target.apply("Lcom/example/Screen;"))::get), mod::get, ClassBytesSource.EMPTY, false)
					.check("test.mixins.json", isRequired ? required : optional, Refmap.EMPTY, Set.of());
		};

		// By its ordinal: the injector injects nothing, which its require judges.
		List<PreflightFinding> found = check.apply(mixin.apply(0, 1), true);
		assertEquals(1, found.size(), found.toString());
		assertEquals(Severity.LEFT_OUT, found.get(0).severity(), found.toString());
		assertEquals("wrapCharTypedFirst(Lcom/example/Listener;)Lcom/example/Listener;", found.get(0).handler());
		assertTrue(found.get(0).message().contains("modifies argument 0 among those of type com.example.Listener of lambda$charTyped$6"
				+ "(Lcom/example/Screen;II)V in net.minecraft.world.Thing, which that method doesn't have"), found.toString());
		assertEquals(Severity.WARN, check.apply(mixin.apply(0, 0), true).get(0).severity(), "not required, nothing fails");
		// Without one Mixin throws as it applies the mixin, whatever its require: only a config that isn't required survives it.
		List<PreflightFinding> implicit = check.apply(mixin.apply(null, 0), true);
		assertEquals(Severity.LEFT_OUT, implicit.get(0).severity(), implicit.toString());
		assertTrue(implicit.get(0).message().contains("modifies the only argument of type com.example.Listener"), implicit.toString());
		assertEquals(Severity.WARN, check.apply(mixin.apply(null, 0), false).get(0).severity());

		// Where the method takes the handler's type, as vanilla's lambda does, nothing is reported.
		Map<String, byte[]> mod = Map.of("com/example/mixin/Keys", mixin.apply(0, 1));
		assertEquals(List.of(), preflight(new ClassIndex(Map.of(TARGET, target.apply(listener))::get), mod::get, ClassBytesSource.EMPTY, false)
				.check("test.mixins.json", required, Refmap.EMPTY, Set.of()));
	}

	@Test
	void aVariableModifierOfAnArgumentAtItsStoreFailsWhereTheMethodNoLongerStoresIt() throws Exception {
		// PRE-16: Fabric API's shader import hook modifies an import's name where vanilla stores it back into its argument
		// (@ModifyVariable(at = @At("STORE"), argsOnly = true)); Forge 47's patch looks the import up without that store,
		// though the method still stores other locals.
		java.util.function.Function<Boolean, byte[]> target = patched -> {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
			MethodVisitor load = cw.visitMethod(Opcodes.ACC_PUBLIC, "loadImport", "(ZLjava/lang/String;)Ljava/lang/String;", null, null);
			load.visitCode();
			load.visitVarInsn(Opcodes.ALOAD, 2);
			load.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET, "normalize", "(Ljava/lang/String;)Ljava/lang/String;", false);
			load.visitVarInsn(Opcodes.ASTORE, patched ? 3 : 2);
			load.visitVarInsn(Opcodes.ALOAD, patched ? 3 : 2);
			load.visitInsn(Opcodes.ARETURN);
			load.visitMaxs(0, 0);
			load.visitEnd();
			cw.visitEnd();
			return cw.toByteArray();
		};
		byte[] mixin = mixin("com/example/mixin/Imports", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "modifyImportId", "(Ljava/lang/String;Z)Ljava/lang/String;", null, null);
			AnnotationVisitor ann = mv.visitAnnotation(MODIFY_VARIABLE, true);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "loadImport");
			methods.visitEnd();
			AnnotationVisitor at = ann.visitAnnotation("at", AT);
			at.visit("value", "STORE");
			at.visitEnd();
			ann.visit("ordinal", 0);
			ann.visit("argsOnly", true);
			ann.visitEnd();
			mv.visitCode();
			mv.visitVarInsn(Opcodes.ALOAD, 1);
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitMaxs(1, 3);
			mv.visitEnd();
		});
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Imports"));
		Map<String, byte[]> mod = Map.of("com/example/mixin/Imports", mixin);
		List<PreflightFinding> found = preflight(new ClassIndex(Map.of(TARGET, target.apply(true))::get), mod::get, ClassBytesSource.EMPTY, false)
				.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(found.stream().anyMatch(f -> f.message().contains("expects @At(\"STORE\"), which does not occur")), found.toString());
		assertTrue(preflight(new ClassIndex(Map.of(TARGET, target.apply(false))::get), mod::get, ClassBytesSource.EMPTY, false)
				.check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());
	}

	@Test
	void aVariableModifierAtALoadFailsWhereTheMethodLoadsNoLocalOfItsType() throws Exception {
		// PRE-17: Puzzles Lib's break speed hook modifies the speed, a float, at its second load after the onGround() call in
		// Player.getDestroySpeed, by its ordinal among the float locals; Forge 47 reduced the method to a call of
		// getDigSpeed, which loads no float, so the slice runs from the method's head (its start is gone, as in Mixin) and
		// Mixin found nothing there.
		java.util.function.Function<Boolean, byte[]> target = patched -> {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
			MethodVisitor speed = cw.visitMethod(Opcodes.ACC_PUBLIC, "getDestroySpeed", "(Ljava/lang/Object;)F", null, null);
			speed.visitCode();
			if (patched) {
				speed.visitVarInsn(Opcodes.ALOAD, 0);
				speed.visitVarInsn(Opcodes.ALOAD, 1);
				speed.visitInsn(Opcodes.ACONST_NULL);
				speed.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "getDigSpeed", "(Ljava/lang/Object;Ljava/lang/Object;)F", false);
			} else {
				speed.visitInsn(Opcodes.FCONST_1);
				speed.visitVarInsn(Opcodes.FSTORE, 2);
				speed.visitVarInsn(Opcodes.ALOAD, 0);
				speed.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "onGround", "()Z", false);
				speed.visitInsn(Opcodes.POP);
				speed.visitVarInsn(Opcodes.FLOAD, 2);
				speed.visitInsn(Opcodes.POP);
				speed.visitVarInsn(Opcodes.FLOAD, 2);
			}
			speed.visitInsn(Opcodes.FRETURN);
			speed.visitMaxs(0, 0);
			speed.visitEnd();
			MethodVisitor dig = cw.visitMethod(Opcodes.ACC_PUBLIC, "getDigSpeed", "(Ljava/lang/Object;Ljava/lang/Object;)F", null, null);
			dig.visitCode();
			dig.visitInsn(Opcodes.FCONST_1);
			dig.visitInsn(Opcodes.FRETURN);
			dig.visitMaxs(0, 0);
			dig.visitEnd();
			MethodVisitor onGround = cw.visitMethod(Opcodes.ACC_PUBLIC, "onGround", "()Z", null, null);
			onGround.visitCode();
			onGround.visitInsn(Opcodes.ICONST_1);
			onGround.visitInsn(Opcodes.IRETURN);
			onGround.visitMaxs(0, 0);
			onGround.visitEnd();
			cw.visitEnd();
			return cw.toByteArray();
		};
		byte[] mixin = mixin("com/example/mixin/BreakSpeed", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "getDestroySpeed", "(FLjava/lang/Object;)F", null, null);
			AnnotationVisitor ann = mv.visitAnnotation(MODIFY_VARIABLE, true);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "getDestroySpeed");
			methods.visitEnd();
			AnnotationVisitor at = ann.visitAnnotation("at", AT);
			at.visit("value", "LOAD");
			at.visit("ordinal", 1);
			at.visitEnd();
			AnnotationVisitor slice = ann.visitAnnotation("slice", "Lorg/spongepowered/asm/mixin/injection/Slice;");
			AnnotationVisitor from = slice.visitAnnotation("from", AT);
			from.visit("value", "INVOKE");
			from.visit("target", "L" + TARGET + ";onGround()Z");
			from.visitEnd();
			slice.visitEnd();
			ann.visit("ordinal", 0);
			ann.visitEnd();
			mv.visitCode();
			mv.visitVarInsn(Opcodes.FLOAD, 1);
			mv.visitInsn(Opcodes.FRETURN);
			mv.visitMaxs(1, 3);
			mv.visitEnd();
		});
		MixinConfigFile config = MixinConfigFile.parse(config(true, "BreakSpeed"));
		Map<String, byte[]> mod = Map.of("com/example/mixin/BreakSpeed", mixin);
		List<PreflightFinding> found = preflight(new ClassIndex(Map.of(TARGET, target.apply(true))::get), mod::get, ClassBytesSource.EMPTY, false)
				.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(found.stream().anyMatch(f -> f.message().contains("expects @At(\"LOAD\", ordinal=1), which does not occur")), found.toString());
		assertTrue(preflight(new ClassIndex(Map.of(TARGET, target.apply(false))::get), mod::get, ClassBytesSource.EMPTY, false)
				.check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty(), "vanilla's method loads the float twice after onGround()");
	}

	/** CORPUS-54's game classes: vanilla's screen effect method, NeoForge's, and NeoForge's with locals pre-flight can't type. */
	private enum ScreenEffectBuild {
		VANILLA, NEOFORGE, NEOFORGE_WITHOUT_TABLE, NEOFORGE_WITH_AN_UNTYPED_LOCAL
	}

	/**
	 * ScreenEffectRenderer.renderScreenEffect, a String standing in for the block state: vanilla's stores the state it
	 * draws; NeoForge's keeps the state and its position in a pair (a List here), casting the state out of it where it
	 * draws it, and stores no state. Both keep a local variable table, as NeoForge's build of the game does; one of
	 * NeoForge's has none, as Mojang ships the game, and one also stores a value read from an array without a table
	 * entry for it.
	 */
	private static byte[] screenEffect(ScreenEffectBuild build) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "renderScreenEffect", "(Ljava/lang/Object;Ljava/lang/Object;)V", null, null);
		m.visitCode();
		Label start = new Label();
		Label stored = new Label();
		Label end = new Label();
		m.visitLabel(start);
		m.visitVarInsn(Opcodes.ALOAD, 0);
		if (build == ScreenEffectBuild.VANILLA) {
			m.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET, "viewBlockingState", "(Ljava/lang/Object;)Ljava/lang/String;", false);
			m.visitVarInsn(Opcodes.ASTORE, 2);
			m.visitLabel(stored);
			m.visitVarInsn(Opcodes.ALOAD, 2);
		} else {
			m.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET, "overlayBlock", "(Ljava/lang/Object;)Ljava/util/List;", false);
			m.visitVarInsn(Opcodes.ASTORE, 2);
			m.visitLabel(stored);
			if (build == ScreenEffectBuild.NEOFORGE_WITH_AN_UNTYPED_LOCAL) {
				m.visitVarInsn(Opcodes.ALOAD, 1);
				m.visitTypeInsn(Opcodes.CHECKCAST, "[Ljava/lang/String;");
				m.visitInsn(Opcodes.ICONST_0);
				m.visitInsn(Opcodes.AALOAD);
				m.visitVarInsn(Opcodes.ASTORE, 3);
			}
			m.visitVarInsn(Opcodes.ALOAD, 2);
			m.visitInsn(Opcodes.ICONST_0);
			m.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true);
			m.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String");
		}
		m.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET, "renderTex", "(Ljava/lang/String;)V", false);
		m.visitInsn(Opcodes.RETURN);
		m.visitLabel(end);
		if (build != ScreenEffectBuild.NEOFORGE_WITHOUT_TABLE) {
			m.visitLocalVariable("p_110719_", "Ljava/lang/Object;", null, start, end, 0);
			m.visitLocalVariable("p_110720_", "Ljava/lang/Object;", null, start, end, 1);
			if (build == ScreenEffectBuild.VANILLA) m.visitLocalVariable("blockstate", "Ljava/lang/String;", null, stored, end, 2);
			else m.visitLocalVariable("overlay", "Ljava/util/List;", null, stored, end, 2);
		}
		m.visitMaxs(0, 0);
		m.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	@Test
	void aVariableModifierByTypeFailsWhereTheMethodHasNoLocalOfThatType() throws Exception {
		// CORPUS-54: Puzzles Lib's screen effect hook modifies, as the only local of its type, the block state vanilla's
		// ScreenEffectRenderer.renderScreenEffect stores; NeoForge's 1.21.1 build keeps it in a pair instead, Mixin found no
		// candidate at any store, and the corpus client stopped as Mixin applied the hook.
		byte[] mixin = mixin("com/example/mixin/ScreenEffect", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "renderScreenEffect", "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/String;", null, null);
			AnnotationVisitor ann = mv.visitAnnotation(MODIFY_VARIABLE, true);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "renderScreenEffect");
			methods.visitEnd();
			AnnotationVisitor at = ann.visitAnnotation("at", AT);
			at.visit("value", "STORE");
			at.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitMaxs(1, 3);
			mv.visitEnd();
		});
		MixinConfigFile config = MixinConfigFile.parse(config(true, "ScreenEffect"));
		Map<String, byte[]> mod = Map.of("com/example/mixin/ScreenEffect", mixin);
		Function<ScreenEffectBuild, List<PreflightFinding>> check = build -> {
			try {
				return preflight(new ClassIndex(Map.of(TARGET, screenEffect(build))::get), mod::get, ClassBytesSource.EMPTY, false)
						.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
			} catch (Exception e) {
				throw new AssertionError(e);
			}
		};
		List<PreflightFinding> found = check.apply(ScreenEffectBuild.NEOFORGE);
		assertTrue(found.stream().anyMatch(f -> f.message().contains("renderScreenEffect modifies a local of type java.lang.String in renderScreenEffect(Ljava/lang/Object;Ljava/lang/Object;)V"
				+ " in net.minecraft.world.Thing, which that method doesn't have")), found.toString());
		assertTrue(check.apply(ScreenEffectBuild.VANILLA).isEmpty(), "vanilla's method stores the state");
		// Where Mixin would type a local itself, pre-flight doesn't judge: no table, or a value stored without a table entry
		// whose type the code doesn't show.
		assertTrue(check.apply(ScreenEffectBuild.NEOFORGE_WITHOUT_TABLE).isEmpty());
		assertTrue(check.apply(ScreenEffectBuild.NEOFORGE_WITH_AN_UNTYPED_LOCAL).isEmpty());
	}

	/** CORPUS-54's crop growth methods: vanilla's, Forge's, and Forge's with the state's slot not taken again. */
	private enum GrowthBuild {
		VANILLA, FORGE, FORGE_WITHOUT_REUSE
	}

	private static final String STATE = "net/minecraft/world/State";
	private static final String GROWTH = "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)F";

	/**
	 * CropBlock.getGrowthSpeed, shortened (its block, level and position; a String for the position): it reads the
	 * state below into a local and asks it whether it is farmland with {@code is}; Forge's asks {@code canSustainPlant}
	 * instead. Then, the state's local gone, it stores the position north into a local (in the state's slot, as javac
	 * gives a freed slot again, or in a new one) and asks the state there {@code is}, which is the method's first call of
	 * it in Forge's build. Forge's patched classes keep a local variable table, as javac compiled them.
	 */
	private static byte[] growth(GrowthBuild build) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor m = cw.visitMethod(Opcodes.ACC_PROTECTED | Opcodes.ACC_STATIC, "getGrowthSpeed", GROWTH, null, null);
		m.visitCode();
		Label start = new Label();
		Label stateStart = new Label();
		Label stateEnd = new Label();
		Label northStart = new Label();
		Label end = new Label();
		m.visitLabel(start);
		m.visitVarInsn(Opcodes.ALOAD, 1);
		m.visitVarInsn(Opcodes.ALOAD, 2);
		m.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET, "getBlockState", "(Ljava/lang/Object;Ljava/lang/String;)L" + STATE + ";", false);
		m.visitVarInsn(Opcodes.ASTORE, 3);
		m.visitLabel(stateStart);
		m.visitVarInsn(Opcodes.ALOAD, 3);
		m.visitInsn(Opcodes.ACONST_NULL);
		m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STATE, build == GrowthBuild.VANILLA ? "is" : "canSustainPlant", "(Ljava/lang/Object;)Z", false);
		m.visitInsn(Opcodes.POP);
		m.visitLabel(stateEnd);
		int north = build == GrowthBuild.FORGE_WITHOUT_REUSE ? 4 : 3;
		m.visitVarInsn(Opcodes.ALOAD, 2);
		m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false);
		m.visitVarInsn(Opcodes.ASTORE, north);
		m.visitLabel(northStart);
		m.visitVarInsn(Opcodes.ALOAD, 1);
		m.visitVarInsn(Opcodes.ALOAD, north);
		m.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET, "getBlockState", "(Ljava/lang/Object;Ljava/lang/String;)L" + STATE + ";", false);
		m.visitVarInsn(Opcodes.ALOAD, 0);
		m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STATE, "is", "(Ljava/lang/Object;)Z", false);
		m.visitInsn(Opcodes.POP);
		m.visitInsn(Opcodes.FCONST_1);
		m.visitInsn(Opcodes.FRETURN);
		m.visitLabel(end);
		m.visitLocalVariable("p_52273_", "Ljava/lang/Object;", null, start, end, 0);
		m.visitLocalVariable("p_52274_", "Ljava/lang/Object;", null, start, end, 1);
		m.visitLocalVariable("p_52275_", "Ljava/lang/String;", null, start, end, 2);
		m.visitLocalVariable("blockstate", "L" + STATE + ";", null, stateStart, stateEnd, 3);
		m.visitLocalVariable("blockpos1", "Ljava/lang/String;", null, northStart, end, north);
		m.visitMaxs(0, 0);
		m.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	@Test
	void aVariableModifierByTypeFailsWhereTheLocalIsOutOfScopeAtItsPoint() throws Exception {
		// CORPUS-54: Balm's crop growth hook modifies, as the only local of its type, the block state at the first call of
		// BlockState.is(Block) in CropBlock.getGrowthSpeed, vanilla's farmland check in its loop. Forge's build asks
		// canSustainPlant there, so the first call is the one after the loop, where the state's local is gone: Mixin found
		// no candidate, and the Forge 47 and 61 corpus servers stopped as it applied the hook.
		Function<Integer, byte[]> mixin = ordinal -> mixin("com/example/mixin/Growth", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "captureLocals", "(L" + STATE + ";)L" + STATE + ";", null, null);
			AnnotationVisitor ann = mv.visitAnnotation(MODIFY_VARIABLE, true);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "getGrowthSpeed");
			methods.visitEnd();
			if (ordinal != null) ann.visit("ordinal", ordinal);
			AnnotationVisitor at = ann.visitAnnotation("at", AT);
			at.visit("value", "INVOKE");
			at.visit("target", "L" + STATE + ";is(Ljava/lang/Object;)Z");
			at.visit("ordinal", 0);
			at.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitMaxs(1, 1);
			mv.visitEnd();
		});
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Growth"));
		java.util.function.BiFunction<GrowthBuild, Integer, List<PreflightFinding>> check = (build, ordinal) -> {
			try {
				return preflight(new ClassIndex(Map.of(TARGET, growth(build))::get), Map.of("com/example/mixin/Growth", mixin.apply(ordinal))::get,
						ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
			} catch (Exception e) {
				throw new AssertionError(e);
			}
		};
		List<PreflightFinding> found = check.apply(GrowthBuild.FORGE, null);
		// Left out (a self-contained mixin, LEAVE-1), as it would stop the game otherwise.
		assertTrue(found.stream().anyMatch(f -> f.severity() == Severity.LEFT_OUT && f.message().contains("captureLocals modifies a local of type "
				+ STATE.replace('/', '.') + " in getGrowthSpeed" + GROWTH + " in net.minecraft.world.Thing, which that method doesn't have where its @At points are")),
				found.toString());
		// By an ordinal among the state locals there, the point is only skipped, which require judges.
		List<PreflightFinding> ordinal = check.apply(GrowthBuild.FORGE, 0);
		assertTrue(ordinal.stream().anyMatch(f -> f.message().contains("where its @At points all are")), ordinal.toString());
		assertTrue(check.apply(GrowthBuild.VANILLA, null).isEmpty(), "vanilla's first call is the state's own, in its scope");
		// Where nothing takes the state's slot after its scope, the slot may still hold it as Mixin builds its locals
		// (from the last stack map frame), so pre-flight doesn't judge.
		assertTrue(check.apply(GrowthBuild.FORGE_WITHOUT_REUSE, null).isEmpty());
	}

	@Test
	void anInjectorSharingALocalWithOneLeftOutGoesWithIt() throws Exception {
		// SHARE-1: Balm's crop growth hooks for 1.21.11 share the block state below through MixinExtras' @Share: the first
		// captures it (left out on Forge, CORPUS-54), the second reads it. Left alone, the second read no state, and here
		// failed its own injection check besides, stopping the Forge 61 corpus client.
		String localRef = "Lcom/llamalad7/mixinextras/sugar/ref/LocalRef;";
		String share = "Lcom/llamalad7/mixinextras/sugar/Share;";
		byte[] mixin = mixin("com/example/mixin/Growth", false, cw -> {
			MethodVisitor capture = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "captureLocals", "(L" + STATE + ";" + localRef + ")L" + STATE + ";",
					null, null);
			AnnotationVisitor ann = capture.visitAnnotation(MODIFY_VARIABLE, true);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "getGrowthSpeed");
			methods.visitEnd();
			AnnotationVisitor at = ann.visitAnnotation("at", AT);
			at.visit("value", "INVOKE");
			at.visit("target", "L" + STATE + ";is(Ljava/lang/Object;)Z");
			at.visit("ordinal", 0);
			at.visitEnd();
			ann.visitEnd();
			capture.visitParameterAnnotation(1, share, false).visit("value", "farmBlock");
			capture.visitCode();
			capture.visitVarInsn(Opcodes.ALOAD, 0);
			capture.visitInsn(Opcodes.ARETURN);
			capture.visitMaxs(1, 2);
			capture.visitEnd();
			for (String name : List.of("readFarmBlock", "unrelated")) {
				MethodVisitor read = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, name,
						"(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;" + localRef + ")V", null, null);
				AnnotationVisitor inject = read.visitAnnotation(INJECT, true);
				AnnotationVisitor targets = inject.visitArray("method");
				targets.visit(null, "getGrowthSpeed");
				targets.visitEnd();
				AnnotationVisitor ats = inject.visitArray("at");
				AnnotationVisitor head = ats.visitAnnotation(null, AT);
				head.visit("value", "HEAD");
				head.visitEnd();
				ats.visitEnd();
				inject.visitEnd();
				read.visitParameterAnnotation(1, share, false).visit("value", name.equals("unrelated") ? "other" : "farmBlock");
				read.visitCode();
				read.visitInsn(Opcodes.RETURN);
				read.visitMaxs(0, 2);
				read.visitEnd();
			}
		});
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Growth"));
		java.util.function.Function<GrowthBuild, List<PreflightFinding>> check = build -> {
			try {
				return preflight(new ClassIndex(Map.of(TARGET, growth(build))::get), Map.of("com/example/mixin/Growth", mixin)::get,
						ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
			} catch (Exception e) {
				throw new AssertionError(e);
			}
		};
		List<PreflightFinding> found = check.apply(GrowthBuild.FORGE);
		String reader = "readFarmBlock(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;" + localRef + ")V";
		assertTrue(found.stream().anyMatch(f -> f.severity() == Severity.LEFT_OUT && reader.equals(f.handler()) && f.message().equals("The injector of "
				+ reader + " goes with that of captureLocals(L" + STATE + ";" + localRef + ")L" + STATE + ";, which can't apply: they share the local"
				+ " farmBlock (@Share), which it would read without the other's value")), found.toString());
		assertTrue(found.stream().noneMatch(f -> f.handler() != null && f.handler().startsWith("unrelated")), "a local shared under another name stays");
		assertTrue(check.apply(GrowthBuild.VANILLA).isEmpty(), "all apply where the first does");
	}

	/**
	 * CORPUS-55's effect drawing methods: vanilla's, with the effect in slot 3 and the draw position in slot 6; Forge's,
	 * recompiled, with the iterator in slot 3, the effect in slot 4, and two locals of its patch before the counter and
	 * the position (slots 7 and 8); Forge's with slot 3 holding a string before the iterator; and one where slot 3 holds
	 * an effect of a subclass.
	 */
	private enum EffectsBuild {
		VANILLA, FORGE, FORGE_REUSED, SUBCLASS
	}

	private static final String EFFECT = "net/minecraft/world/Effect";
	private static final String SPECIAL_EFFECT = "net/minecraft/world/SpecialEffect";

	/**
	 * Gui.renderEffects, shortened: it takes the effects into a list, then the first of them from the list's iterator,
	 * counts it and works out where to draw it, and adds it to the list of what it draws, the call Porting Lib's hook
	 * wraps. Forge's patched classes keep a local variable table, as javac compiled them; vanilla's here does too, though
	 * Mojang's has none, so that both builds can be told apart by it.
	 */
	private static Map<String, byte[]> effects(EffectsBuild build) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor m = cw.visitMethod(Opcodes.ACC_PROTECTED, "renderEffects", "(Ljava/lang/Object;)V", null, null);
		m.visitCode();
		Label start = new Label();
		Label listStart = new Label();
		Label textStart = new Label();
		Label textEnd = new Label();
		Label iteratorStart = new Label();
		Label effectStart = new Label();
		Label patchStart = new Label();
		Label countStart = new Label();
		Label xStart = new Label();
		Label end = new Label();
		boolean vanilla = build == EffectsBuild.VANILLA;
		int iterator = vanilla ? 4 : 3;
		int effect = vanilla ? 3 : 4;
		int count = vanilla ? 5 : 7;
		int x = vanilla ? 6 : 8;
		m.visitLabel(start);
		m.visitVarInsn(Opcodes.ALOAD, 0);
		m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "effects", "()Ljava/util/List;", false);
		m.visitVarInsn(Opcodes.ASTORE, 2);
		m.visitLabel(listStart);
		if (build == EffectsBuild.FORGE_REUSED) {
			m.visitLdcInsn("effects");
			m.visitVarInsn(Opcodes.ASTORE, 3);
			m.visitLabel(textStart);
			m.visitVarInsn(Opcodes.ALOAD, 3);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false);
			m.visitInsn(Opcodes.POP);
			m.visitLabel(textEnd);
		}
		if (build == EffectsBuild.SUBCLASS) {
			m.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET, "special", "()L" + SPECIAL_EFFECT + ";", false);
			m.visitVarInsn(Opcodes.ASTORE, 3);
			m.visitLabel(textStart);
			m.visitVarInsn(Opcodes.ALOAD, 3);
			m.visitInsn(Opcodes.POP);
			m.visitLabel(textEnd);
		}
		m.visitVarInsn(Opcodes.ALOAD, 2);
		m.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;", true);
		m.visitVarInsn(Opcodes.ASTORE, iterator);
		m.visitLabel(iteratorStart);
		m.visitVarInsn(Opcodes.ALOAD, iterator);
		m.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
		m.visitTypeInsn(Opcodes.CHECKCAST, EFFECT);
		m.visitVarInsn(Opcodes.ASTORE, effect);
		m.visitLabel(effectStart);
		if (!vanilla) {
			// The patch's: the effect's renderer and what it draws with.
			m.visitVarInsn(Opcodes.ALOAD, effect);
			m.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET, "renderer", "(L" + EFFECT + ";)Ljava/lang/String;", false);
			m.visitVarInsn(Opcodes.ASTORE, 5);
			m.visitLdcInsn("gui");
			m.visitVarInsn(Opcodes.ASTORE, 6);
			m.visitLabel(patchStart);
		}
		m.visitInsn(Opcodes.ICONST_0);
		m.visitVarInsn(Opcodes.ISTORE, count);
		m.visitLabel(countStart);
		m.visitIntInsn(Opcodes.BIPUSH, 25);
		m.visitVarInsn(Opcodes.ISTORE, x);
		m.visitLabel(xStart);
		m.visitVarInsn(Opcodes.ALOAD, 2);
		m.visitVarInsn(Opcodes.ALOAD, effect);
		m.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
		m.visitInsn(Opcodes.POP);
		m.visitInsn(Opcodes.RETURN);
		m.visitLabel(end);
		m.visitLocalVariable("this", "L" + TARGET + ";", null, start, end, 0);
		m.visitLocalVariable("p_282812_", "Ljava/lang/Object;", null, start, end, 1);
		m.visitLocalVariable("list", "Ljava/util/List;", null, listStart, end, 2);
		if (build == EffectsBuild.FORGE_REUSED) m.visitLocalVariable("text", "Ljava/lang/String;", null, textStart, textEnd, 3);
		if (build == EffectsBuild.SUBCLASS) m.visitLocalVariable("special", "L" + SPECIAL_EFFECT + ";", null, textStart, textEnd, 3);
		m.visitLocalVariable("iterator", "Ljava/util/Iterator;", null, iteratorStart, end, iterator);
		m.visitLocalVariable("mobeffectinstance", "L" + EFFECT + ";", null, effectStart, end, effect);
		if (!vanilla) {
			m.visitLocalVariable("renderer", "Ljava/lang/String;", null, patchStart, end, 5);
			m.visitLocalVariable("layer", "Ljava/lang/String;", null, patchStart, end, 6);
		}
		m.visitLocalVariable("i", "I", null, countStart, end, count);
		m.visitLocalVariable("k", "I", null, xStart, end, x);
		m.visitMaxs(0, 0);
		m.visitEnd();
		cw.visitEnd();
		Map<String, byte[]> classes = new HashMap<>(Map.of(TARGET, cw.toByteArray()));
		for (String[] type : new String[][] {{EFFECT, "java/lang/Object"}, {SPECIAL_EFFECT, EFFECT}}) {
			ClassWriter c = new ClassWriter(0);
			c.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, type[0], null, type[1], null);
			c.visitEnd();
			classes.put(type[0], c.toByteArray());
		}
		return classes;
	}

	@Test
	void aLocalTakenByItsIndexMovesToTheSlotThisBuildKeepsItInWhereItsSlotNeverHoldsOne() throws Exception {
		// CORPUS-55: Porting Lib Entity's GuiMixin takes @Local(index = 8) MobEffectInstance, @Local(index = 10) int and
		// more in Gui.renderEffects, vanilla's slots of the effect it draws and where. Forge 47's build, recompiled from
		// decompiled source, keeps other locals there: MixinExtras found no matching local ("Unable to find matching
		// local!") and the Forge 47 corpus client stopped.
		byte[] mixin = mixin("com/example/mixin/Effects", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "drawn", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;L" + EFFECT + ";I)V",
					null, null);
			AnnotationVisitor local = mv.visitParameterAnnotation(1, LOCAL, false);
			local.visit("index", 3);
			local.visitEnd();
			AnnotationVisitor position = mv.visitParameterAnnotation(2, LOCAL, false);
			position.visit("index", 6);
			position.visitEnd();
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "renderEffects");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", "INVOKE");
			at.visit("target", "Ljava/util/List;add(Ljava/lang/Object;)Z");
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 4);
			mv.visitEnd();
		});
		Map<String, byte[]> mod = Map.of("com/example/mixin/Effects", mixin);
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Effects"));
		Function<EffectsBuild, List<PreflightFinding>> check = build -> {
			try {
				return preflight(new ClassIndex(effects(build)::get), mod::get, ClassBytesSource.EMPTY, false)
						.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
			} catch (Exception e) {
				throw new AssertionError(e);
			}
		};
		List<PreflightFinding> found = check.apply(EffectsBuild.FORGE);
		assertTrue(found.stream().anyMatch(f -> f.message().contains("captures the local variable of type net.minecraft.world.Effect at slot 3, which"
				+ " renderEffects in net.minecraft.world.Thing never holds there")), found.toString());
		List<PreflightFinding> reused = check.apply(EffectsBuild.FORGE_REUSED);
		assertTrue(reused.stream().anyMatch(f -> f.message().contains("Effect at slot 3, which renderEffects")), "a string, then an iterator: " + reused);
		assertTrue(check.apply(EffectsBuild.VANILLA).isEmpty(), "vanilla's slots hold the effect and the position");
		// A subclass's value: the verifier, merging values, may type the slot by its superclass, so pre-flight doesn't judge.
		List<PreflightFinding> subclass = check.apply(EffectsBuild.SUBCLASS);
		assertTrue(subclass.stream().noneMatch(f -> f.message().contains("Effect at slot 3")), subclass.toString());
		assertTrue(subclass.stream().anyMatch(f -> f.message().contains("of type int at slot 6, which renderEffects")), "a string's slot: " + subclass);

		Map<String, byte[]> vanillaClasses = effects(EffectsBuild.VANILLA);
		for (EffectsBuild build : List.of(EffectsBuild.FORGE, EffectsBuild.FORGE_REUSED)) {
			ClassIndex forge = new ClassIndex(effects(build)::get);
			// Without vanilla's code, the effect, the only one where the hook applies, is taken; of the two ints there,
			// which is vanilla's can't be told, and the hook is judged as it stands.
			List<PreflightFinding> notes = new ArrayList<>();
			byte[] effectOnly = new MixinPreflight(forge, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, ClassBytesSource.EMPTY)
					.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes).get("com/example/mixin/Effects.class");
			assertNotNull(effectOnly, build + ": " + notes);
			assertTrue(notes.getFirst().message().contains("captures the local of type net.minecraft.world.Effect at slot 3, which renderEffects in"
					+ " net.minecraft.world.Thing never holds there; the only one of the type where the injector applies is at slot 4"), notes.toString());
			assertEquals(List.of(4, 6), indexes(effectOnly));
			List<PreflightFinding> left = preflight(forge, Map.of("com/example/mixin/Effects", effectOnly)::get, ClassBytesSource.EMPTY, false)
					.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
			assertTrue(left.stream().anyMatch(f -> f.message().contains("of type int at slot 6, which renderEffects")), left.toString());
			// With vanilla's code, the position takes the slot this build stores vanilla's local to. (Where the effect's
			// store is can't be told: from the back, vanilla's matches the patch's last store; it is the only effect.)
			notes.clear();
			byte[] repaired = new MixinPreflight(forge, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, vanillaClasses::get)
					.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes).get("com/example/mixin/Effects.class");
			assertNotNull(repaired, build + ": " + notes);
			assertTrue(notes.stream().anyMatch(n -> n.message().contains("of type int at slot 6, which renderEffects in net.minecraft.world.Thing never"
					+ " holds there; this build keeps vanilla's local there in slot 8")), notes.toString());
			assertEquals(List.of(4, 8), indexes(repaired));
			List<PreflightFinding> after = preflight(forge, Map.of("com/example/mixin/Effects", repaired)::get, ClassBytesSource.EMPTY, false)
					.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
			assertTrue(after.isEmpty(), after.toString());
		}
		ClassIndex vanilla = new ClassIndex(vanillaClasses::get);
		assertTrue(new MixinPreflight(vanilla, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, vanillaClasses::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
	}

	/** The index each of the {@code drawn} handler's {@code @Local}s takes, by parameter. */
	private static List<Object> indexes(byte[] mixin) {
		ClassNode node = new ClassNode();
		new ClassReader(mixin).accept(node, 0);
		MethodNode handler = node.methods.stream().filter(h -> h.name.equals("drawn")).findFirst().orElseThrow();
		return List.of(Annotations.value(Annotations.ofParameter(handler, 1).getFirst(), "index"),
				Annotations.value(Annotations.ofParameter(handler, 2).getFirst(), "index"));
	}

	/** PRE-19's game classes: vanilla's, Forge 47's, and Forge 47's with a float local of the patch's before vanilla's. */
	private enum BreakSpeedBuild {
		VANILLA, FORGE, FORGE_WITH_A_FLOAT_FIRST
	}

	private static final String SPEED = "(Ljava/lang/String;)F";
	private static final String DIG_SPEED = "(Ljava/lang/String;Ljava/lang/Integer;)F";

	/**
	 * Player.getDestroySpeed as vanilla has it (its speed in a float, divided where the player is off the ground, then
	 * returned; no local variable table, as Mojang ships it), or as Forge 47 has it: deprecated, calling getDigSpeed with
	 * a position, whose code is vanilla's but for the break speed event the speed goes through before it is returned.
	 */
	private static byte[] breakSpeedTarget(BreakSpeedBuild build) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		boolean forge = build != BreakSpeedBuild.VANILLA;
		MethodVisitor speed = cw.visitMethod(Opcodes.ACC_PUBLIC | (forge ? Opcodes.ACC_DEPRECATED : 0), "getDestroySpeed", SPEED, null, null);
		speed.visitCode();
		if (forge) {
			speed.visitVarInsn(Opcodes.ALOAD, 0);
			speed.visitVarInsn(Opcodes.ALOAD, 1);
			speed.visitInsn(Opcodes.ACONST_NULL);
			speed.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "getDigSpeed", DIG_SPEED, false);
			speed.visitInsn(Opcodes.FRETURN);
		} else {
			breakSpeedBody(speed, 2, -1, false);
		}
		speed.visitMaxs(0, 0);
		speed.visitEnd();
		if (forge) {
			MethodVisitor dig = cw.visitMethod(Opcodes.ACC_PUBLIC, "getDigSpeed", DIG_SPEED, null, null);
			dig.visitCode();
			boolean extra = build == BreakSpeedBuild.FORGE_WITH_A_FLOAT_FIRST;
			breakSpeedBody(dig, extra ? 4 : 3, extra ? 3 : -1, true);
			dig.visitMaxs(0, 0);
			dig.visitEnd();
		}
		for (String[] method : new String[][] {{"base", SPEED}, {"bonus", "()F"}, {"onGround", "()Z"}}) {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, method[0], method[1], null, null);
			mv.visitCode();
			mv.visitInsn(method[1].endsWith("Z") ? Opcodes.ICONST_1 : Opcodes.FCONST_1);
			mv.visitInsn(method[1].endsWith("Z") ? Opcodes.IRETURN : Opcodes.FRETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		MethodVisitor event = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "breakSpeed", "(Ljava/lang/Object;Ljava/lang/String;FLjava/lang/Integer;)F", null, null);
		event.visitCode();
		event.visitVarInsn(Opcodes.FLOAD, 2);
		event.visitInsn(Opcodes.FRETURN);
		event.visitMaxs(0, 0);
		event.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** The speed's code, its float in {@code slot}; Forge's with its event and a local variable table, after a float of its own in {@code extra}. */
	private static void breakSpeedBody(MethodVisitor mv, int slot, int extra, boolean forge) {
		org.objectweb.asm.Label start = new org.objectweb.asm.Label();
		org.objectweb.asm.Label extraStart = new org.objectweb.asm.Label();
		org.objectweb.asm.Label speedStart = new org.objectweb.asm.Label();
		org.objectweb.asm.Label onGround = new org.objectweb.asm.Label();
		org.objectweb.asm.Label end = new org.objectweb.asm.Label();
		mv.visitLabel(start);
		if (extra >= 0) {
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "bonus", "()F", false);
			mv.visitVarInsn(Opcodes.FSTORE, extra);
			mv.visitLabel(extraStart);
		}
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "base", SPEED, false);
		mv.visitVarInsn(Opcodes.FSTORE, slot);
		mv.visitLabel(speedStart);
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "onGround", "()Z", false);
		mv.visitJumpInsn(Opcodes.IFNE, onGround);
		mv.visitVarInsn(Opcodes.FLOAD, slot);
		mv.visitLdcInsn(5.0F);
		mv.visitInsn(Opcodes.FDIV);
		mv.visitVarInsn(Opcodes.FSTORE, slot);
		mv.visitLabel(onGround);
		if (forge) {
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitVarInsn(Opcodes.ALOAD, 1);
			mv.visitVarInsn(Opcodes.FLOAD, slot);
			mv.visitVarInsn(Opcodes.ALOAD, 2);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET, "breakSpeed", "(Ljava/lang/Object;Ljava/lang/String;FLjava/lang/Integer;)F", false);
			mv.visitVarInsn(Opcodes.FSTORE, slot);
		}
		mv.visitVarInsn(Opcodes.FLOAD, slot);
		mv.visitInsn(Opcodes.FRETURN);
		mv.visitLabel(end);
		if (forge) {
			mv.visitLocalVariable("this", "L" + TARGET + ";", null, start, end, 0);
			mv.visitLocalVariable("state", "Ljava/lang/String;", null, start, end, 1);
			mv.visitLocalVariable("pos", "Ljava/lang/Integer;", null, start, end, 2);
			if (extra >= 0) mv.visitLocalVariable("bonus", "F", null, extraStart, end, extra);
			mv.visitLocalVariable("f", "F", null, speedStart, end, slot);
		}
	}

	/** Puzzles Lib's break speed hook: the first float, at its second load after onGround() (any of them with a negative {@code loadOrdinal}). */
	private static byte[] breakSpeedHook(int loadOrdinal) {
		return mixin("com/example/mixin/BreakSpeed", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "getDestroySpeed", "(FLjava/lang/String;)F", null, null);
			AnnotationVisitor ann = mv.visitAnnotation(MODIFY_VARIABLE, true);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "getDestroySpeed");
			methods.visitEnd();
			AnnotationVisitor at = ann.visitAnnotation("at", AT);
			at.visit("value", "LOAD");
			if (loadOrdinal >= 0) at.visit("ordinal", loadOrdinal);
			at.visitEnd();
			AnnotationVisitor slice = ann.visitAnnotation("slice", "Lorg/spongepowered/asm/mixin/injection/Slice;");
			AnnotationVisitor from = slice.visitAnnotation("from", AT);
			from.visit("value", "INVOKE");
			from.visit("target", "L" + TARGET + ";onGround()Z");
			from.visitEnd();
			slice.visitEnd();
			ann.visit("ordinal", 0);
			ann.visitEnd();
			mv.visitCode();
			mv.visitVarInsn(Opcodes.FLOAD, 1);
			mv.visitInsn(Opcodes.FRETURN);
			mv.visitMaxs(1, 3);
			mv.visitEnd();
		});
	}

	@Test
	void aVariableModifierByOrdinalMovesToTheMethodTheDeprecatedOneCallsAtVanillasLoad() throws Exception {
		// PRE-19: Puzzles Lib's break speed hook (PRE-17) moves to getDigSpeed, the method Forge 47 deprecated
		// getDestroySpeed for, where vanilla's code shows the first float is vanilla's speed there. Forge's code passes the
		// speed to its event before returning it, so vanilla's second load after onGround() is the third there.
		MixinConfigFile config = MixinConfigFile.parse(config(true, "BreakSpeed"));
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/BreakSpeed", breakSpeedHook(1)));
		Map<String, byte[]> vanilla = Map.of(TARGET, breakSpeedTarget(BreakSpeedBuild.VANILLA));
		ClassIndex forge = new ClassIndex(Map.of(TARGET, breakSpeedTarget(BreakSpeedBuild.FORGE))::get);
		assertFalse(preflight(forge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());
		// Without vanilla's code, which load and which float the hook meant can't be told.
		assertTrue(preflight(forge, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());

		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = new MixinPreflight(forge, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes).get("com/example/mixin/BreakSpeed.class");
		assertNotNull(repaired, notes.toString());
		assertTrue(notes.getFirst().message().contains("getDestroySpeed" + SPEED + ", which NeoForge deprecated for getDigSpeed" + DIG_SPEED
				+ "; the injector is pointed there, at its load with ordinal 2 there, vanilla's with ordinal 1"), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired).accept(node, 0);
		MethodNode handler = node.methods.stream().filter(m -> m.name.equals("getDestroySpeed")).findFirst().orElseThrow();
		AnnotationNode injector = Annotations.find(handler, MODIFY_VARIABLE);
		assertEquals(List.of("getDigSpeed" + DIG_SPEED), Annotations.value(injector, "method"));
		assertEquals(2, Annotations.value(Annotations.list(Annotations.value(injector, "at")).getFirst(), "ordinal"), "vanilla's return, not the event's argument");
		assertEquals(0, Annotations.value(injector, "ordinal"));
		mod.put("com/example/mixin/BreakSpeed", repaired);
		assertTrue(preflight(forge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());
	}

	@Test
	void aVariableModifierByOrdinalStaysWhereThePatchAddsALocalOfItsTypeOrALoadOfIt() throws Exception {
		// PRE-19's limits: where the other method has a float of the patch's first, the hook's ordinal names that one; where
		// the hook takes every load after onGround(), Forge's event argument would be one more.
		MixinConfigFile config = MixinConfigFile.parse(config(true, "BreakSpeed"));
		Map<String, byte[]> vanilla = Map.of(TARGET, breakSpeedTarget(BreakSpeedBuild.VANILLA));
		Map<String, byte[]> secondLoad = Map.of("com/example/mixin/BreakSpeed", breakSpeedHook(1));
		ClassIndex floatFirst = new ClassIndex(Map.of(TARGET, breakSpeedTarget(BreakSpeedBuild.FORGE_WITH_A_FLOAT_FIRST))::get);
		assertTrue(new MixinPreflight(floatFirst, InstalledClasses.NONE, secondLoad::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
		Map<String, byte[]> everyLoad = Map.of("com/example/mixin/BreakSpeed", breakSpeedHook(-1));
		ClassIndex forge = new ClassIndex(Map.of(TARGET, breakSpeedTarget(BreakSpeedBuild.FORGE))::get);
		assertTrue(new MixinPreflight(forge, InstalledClasses.NONE, everyLoad::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
	}

	@Test
	void anArgumentModifierWithAnInstanceHandlerBeforeTheDelegateConstructorCallFails() throws Exception {
		// CTOR-3: Fabric API's shader program hook modifies vanilla's ResourceLocation construction in ShaderInstance's
		// constructor, which Forge 47's patch moved into the arguments of a this(...) call: Mixin takes only a static
		// handler there.
		java.util.function.Function<Boolean, byte[]> target = patched -> {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
			MethodVisitor named = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/String;)V", null, null);
			named.visitCode();
			named.visitVarInsn(Opcodes.ALOAD, 0);
			if (patched) {
				named.visitVarInsn(Opcodes.ALOAD, 1);
				named.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET, "location", "(Ljava/lang/String;)Ljava/lang/Object;", false);
				named.visitMethodInsn(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(Ljava/lang/Object;)V", false);
			} else {
				named.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
				named.visitVarInsn(Opcodes.ALOAD, 1);
				named.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET, "location", "(Ljava/lang/String;)Ljava/lang/Object;", false);
				named.visitInsn(Opcodes.POP);
			}
			named.visitInsn(Opcodes.RETURN);
			named.visitMaxs(0, 0);
			named.visitEnd();
			if (patched) {
				MethodVisitor located = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/Object;)V", null, null);
				located.visitCode();
				located.visitVarInsn(Opcodes.ALOAD, 0);
				located.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
				located.visitInsn(Opcodes.RETURN);
				located.visitMaxs(0, 0);
				located.visitEnd();
			}
			cw.visitEnd();
			return cw.toByteArray();
		};
		java.util.function.Function<Boolean, byte[]> modifier = isStatic -> mixin("com/example/mixin/Program", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | (isStatic ? Opcodes.ACC_STATIC : 0), "modifyId", "(Ljava/lang/String;)Ljava/lang/String;", null, null);
			AnnotationVisitor ann = mv.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/ModifyArg;", true);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "<init>");
			methods.visitEnd();
			AnnotationVisitor at = ann.visitAnnotation("at", AT);
			at.visit("value", "INVOKE");
			at.visit("target", "L" + TARGET + ";location(Ljava/lang/String;)Ljava/lang/Object;");
			at.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			mv.visitVarInsn(Opcodes.ALOAD, isStatic ? 0 : 1);
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitMaxs(1, 2);
			mv.visitEnd();
		});
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Program"));
		ClassIndex forge = new ClassIndex(Map.of(TARGET, target.apply(true))::get);
		List<PreflightFinding> found = preflight(forge, Map.of("com/example/mixin/Program", modifier.apply(false))::get, ClassBytesSource.EMPTY, false)
				.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(found.stream().anyMatch(f -> f.message().contains("before its this() or super() call, where Mixin takes only a static handler")), found.toString());
		// A static handler, or the point after super(), as vanilla has it, is fine.
		assertTrue(preflight(forge, Map.of("com/example/mixin/Program", modifier.apply(true))::get, ClassBytesSource.EMPTY, false)
				.check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());
		assertTrue(preflight(new ClassIndex(Map.of(TARGET, target.apply(false))::get), Map.of("com/example/mixin/Program", modifier.apply(false))::get,
				ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());
	}

	@Test
	void anArgumentTakenByNameIsTakenByItsSlotWhereMixinDoesntKnowArgumentNames() throws Exception {
		// ARGS-1: Fabric API's resource loader hook takes @ModifyVariable(argsOnly = true, name = "enableProfiling") in
		// SimpleReloadInstance.create; SpongePowered's Mixin (Forge 61 to 66) names arguments arg and their slot there, so
		// the name never matches and Forge 65's server stopped. Fabric's Mixin takes their names from 0.17.0.
		java.util.function.BiFunction<Boolean, Boolean, byte[]> target = (names, secondFlag) -> {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
			String desc = secondFlag ? "(Ljava/lang/String;ZZ)I" : "(Ljava/lang/String;Z)I";
			MethodVisitor make = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "make", desc, null, null);
			org.objectweb.asm.Label start = new org.objectweb.asm.Label();
			org.objectweb.asm.Label end = new org.objectweb.asm.Label();
			make.visitCode();
			make.visitLabel(start);
			make.visitVarInsn(Opcodes.ILOAD, 1);
			make.visitInsn(Opcodes.IRETURN);
			make.visitLabel(end);
			if (names) {
				make.visitLocalVariable("id", "Ljava/lang/String;", null, start, end, 0);
				make.visitLocalVariable("enableProfiling", "Z", null, start, end, 1);
				if (secondFlag) make.visitLocalVariable("other", "Z", null, start, end, 2);
			}
			make.visitMaxs(0, 0);
			make.visitEnd();
			cw.visitEnd();
			return cw.toByteArray();
		};
		java.util.function.Function<Boolean, byte[]> mixinOf = secondFlag -> mixin("com/example/mixin/Reload", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "adjust", "(Z)Z", null, null);
			AnnotationVisitor ann = mv.visitAnnotation(MODIFY_VARIABLE, true);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, secondFlag ? "make(Ljava/lang/String;ZZ)I" : "make(Ljava/lang/String;Z)I");
			methods.visitEnd();
			AnnotationVisitor at = ann.visitAnnotation("at", AT);
			at.visit("value", "LOAD");
			at.visit("ordinal", 0);
			at.visitEnd();
			ann.visit("argsOnly", true);
			AnnotationVisitor names = ann.visitArray("name");
			names.visit(null, "enableProfiling");
			names.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			mv.visitVarInsn(Opcodes.ILOAD, 0);
			mv.visitInsn(Opcodes.IRETURN);
			mv.visitMaxs(1, 1);
			mv.visitEnd();
		});
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Reload"));
		java.util.function.BiFunction<ClassIndex, Map<String, byte[]>, MixinPreflight> forge = (game, mod) -> new MixinPreflight(game,
				InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, ClassBytesSource.EMPTY, "Forge", true,
				MixinPreflight.ConstructorInjects.UNSAFE_POINTS, false);

		// Fabric's Mixin knows the name: nothing to do.
		ClassIndex named = new ClassIndex(Map.of(TARGET, target.apply(true, false))::get);
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Reload", mixinOf.apply(false)));
		assertTrue(preflight(named, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
		assertTrue(preflight(named, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());

		// SpongePowered's: reported before the repair, taken by its slot after it.
		assertTrue(forge.apply(named, mod).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).stream()
				.anyMatch(f -> f.message().contains("takes the argument enableProfiling of make") && f.message().contains("by its name")));
		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = forge.apply(named, mod).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes).get("com/example/mixin/Reload.class");
		assertNotNull(repaired, notes.toString());
		assertTrue(notes.getFirst().message().contains("takes the argument enableProfiling by its name, which Forge's Mixin doesn't know; it takes the argument's slot, 1, instead"),
				notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired).accept(node, 0);
		AnnotationNode modify = Annotations.find(node.methods.stream().filter(m -> m.name.equals("adjust")).findFirst().orElseThrow(), MODIFY_VARIABLE);
		assertEquals(1, Annotations.value(modify, "index"));
		assertNull(Annotations.value(modify, "name"));
		mod.put("com/example/mixin/Reload", repaired);
		assertTrue(forge.apply(named, mod).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());

		// Without local variable names, the only argument of the handler's type is it.
		ClassIndex unnamed = new ClassIndex(Map.of(TARGET, target.apply(false, false))::get);
		Map<String, byte[]> fresh = Map.of("com/example/mixin/Reload", mixinOf.apply(false));
		assertNotNull(forge.apply(unnamed, fresh).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).get("com/example/mixin/Reload.class"));
		// Two of the type and no names: which one is meant can't be told, and the injector is reported.
		ClassIndex two = new ClassIndex(Map.of(TARGET, target.apply(false, true))::get);
		Map<String, byte[]> twoMod = Map.of("com/example/mixin/Reload", mixinOf.apply(true));
		assertTrue(forge.apply(two, twoMod).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
		assertFalse(forge.apply(two, twoMod).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());
	}

	@Test
	void aLocalArgumentTakenByNameIsTakenByItsSlotWhereMixinDoesntKnowArgumentNames() throws Exception {
		// PRE-18: ImmediatelyFast's map atlas hook takes @Local(name = "mapRenderState", argsOnly = true) in
		// GuiGraphicsExtractor.map; MixinExtras finds it with Mixin's discriminator, which on SpongePowered's Mixin (Forge 61
		// to 66) names arguments arg and their slot, as for ARGS-1, so Forge 65's client stopped with "(0/1) succeeded".
		String ref = "Lcom/llamalad7/mixinextras/sugar/ref/LocalIntRef;";
		java.util.function.BiFunction<Boolean, Boolean, byte[]> target = (names, secondState) -> {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
			String desc = secondState ? "(Ljava/lang/String;Ljava/lang/String;I)V" : "(Ljava/lang/String;I)V";
			MethodVisitor map = cw.visitMethod(Opcodes.ACC_PUBLIC, "map", desc, null, null);
			org.objectweb.asm.Label start = new org.objectweb.asm.Label();
			org.objectweb.asm.Label end = new org.objectweb.asm.Label();
			map.visitCode();
			map.visitLabel(start);
			map.visitInsn(Opcodes.RETURN);
			map.visitLabel(end);
			if (names) {
				map.visitLocalVariable("this", "L" + TARGET + ";", null, start, end, 0);
				map.visitLocalVariable("mapRenderState", "Ljava/lang/String;", null, start, end, 1);
				map.visitLocalVariable("color", "I", null, start, end, 2);
			}
			map.visitMaxs(0, 0);
			map.visitEnd();
			cw.visitEnd();
			return cw.toByteArray();
		};
		// hook(ci, @Local(argsOnly = true, name = "mapRenderState") String state, @Local(argsOnly = true, name = "color") LocalIntRef color)
		java.util.function.Function<Boolean, byte[]> mixinOf = secondState -> mixin("com/example/mixin/Atlas", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "hook", "(" + CALLBACK_INFO + "Ljava/lang/String;" + ref + ")V", null, null);
			String[][] locals = {{"mapRenderState"}, {"color"}};
			for (int i = 0; i < locals.length; i++) {
				AnnotationVisitor sugar = mv.visitParameterAnnotation(i + 1, LOCAL, false);
				sugar.visit("argsOnly", true);
				AnnotationVisitor names = sugar.visitArray("name");
				names.visit(null, locals[i][0]);
				names.visitEnd();
				sugar.visitEnd();
			}
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, secondState ? "map(Ljava/lang/String;Ljava/lang/String;I)V" : "map(Ljava/lang/String;I)V");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", "HEAD");
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 4);
			mv.visitEnd();
		});
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Atlas"));
		java.util.function.BiFunction<ClassIndex, Map<String, byte[]>, MixinPreflight> forge = (game, mod) -> new MixinPreflight(game,
				InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, ClassBytesSource.EMPTY, "Forge", true,
				MixinPreflight.ConstructorInjects.UNSAFE_POINTS, false);

		// Fabric's Mixin knows the names: nothing to do.
		ClassIndex named = new ClassIndex(Map.of(TARGET, target.apply(true, false))::get);
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Atlas", mixinOf.apply(false)));
		assertTrue(preflight(named, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
		assertTrue(preflight(named, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());

		// SpongePowered's: reported before the repair, taken by their slots after it (a ref of an int finds the int).
		List<PreflightFinding> before = forge.apply(named, mod).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(before.stream().anyMatch(f -> f.message().contains("captures the argument mapRenderState of map") && f.message().contains("by its name")),
				before.toString());
		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = forge.apply(named, mod).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes).get("com/example/mixin/Atlas.class");
		assertNotNull(repaired, notes.toString());
		assertTrue(notes.stream().anyMatch(n -> n.message().contains("@Inject hook captures the argument mapRenderState by its name, which Forge's Mixin doesn't know;"
				+ " it takes the argument's slot, 1, instead")), notes.toString());
		assertTrue(notes.stream().anyMatch(n -> n.message().contains("the argument color by its name") && n.message().contains("slot, 2,")), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired).accept(node, 0);
		MethodNode hook = node.methods.stream().filter(m -> m.name.equals("hook")).findFirst().orElseThrow();
		for (int p = 1; p <= 2; p++) {
			AnnotationNode sugar = Annotations.ofParameter(hook, p).stream().filter(a -> a.desc.equals(LOCAL)).findFirst().orElseThrow();
			assertEquals(p, Annotations.value(sugar, "index"));
			assertNull(Annotations.value(sugar, "name"));
			assertEquals(Boolean.TRUE, Annotations.value(sugar, "argsOnly"));
		}
		mod.put("com/example/mixin/Atlas", repaired);
		assertTrue(forge.apply(named, mod).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());

		// Without local variable names, the only argument of the type is it, for the String and the int alike.
		ClassIndex unnamed = new ClassIndex(Map.of(TARGET, target.apply(false, false))::get);
		Map<String, byte[]> fresh = Map.of("com/example/mixin/Atlas", mixinOf.apply(false));
		assertNotNull(forge.apply(unnamed, fresh).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).get("com/example/mixin/Atlas.class"));
		// Two Strings and no names: the int is taken by its slot, the String can't be told, and the hook is reported.
		ClassIndex two = new ClassIndex(Map.of(TARGET, target.apply(false, true))::get);
		Map<String, byte[]> twoMod = new HashMap<>(Map.of("com/example/mixin/Atlas", mixinOf.apply(true)));
		byte[] partly = forge.apply(two, twoMod).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).get("com/example/mixin/Atlas.class");
		assertNotNull(partly);
		twoMod.put("com/example/mixin/Atlas", partly);
		assertTrue(forge.apply(two, twoMod).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).stream()
				.anyMatch(f -> f.message().contains("captures the argument mapRenderState of map")));
	}

	@Test
	void anExpressionsArgumentDefinedByNameIsTakenByItsSlotWhereMixinDoesntKnowArgumentNames() throws Exception {
		// PRE-21: Debugify's cactus fix wraps ?.getBlockState(pos.relative(direction)) in CactusBlock.canSurvive, defining pos
		// as @Local(type = BlockPos.class, name = "pos", argsOnly = true). MixinExtras finds a definition's local with Mixin's
		// discriminator, which on SpongePowered's Mixin (Forge 61 to 66) names arguments arg and their slot, as for PRE-18: the
		// expression matched nothing, and the Forge 64 to 66 servers stopped in Blocks' initializer, "(0/1) succeeded".
		String pos = "net/minecraft/core/BlockPos";
		String direction = "net/minecraft/core/Direction";
		String canSurvive = "(Ljava/lang/Object;L" + TARGET + ";L" + pos + ";)Z";
		// canSurvive(state, level, pos): level.getBlockState(pos.relative(direction)), the direction a local named as given.
		BiFunction<Boolean, String, byte[]> target = (names, directionName) -> {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PROTECTED, "canSurvive", canSurvive, null, null);
			Label start = new Label();
			Label loop = new Label();
			Label end = new Label();
			mv.visitCode();
			mv.visitLabel(start);
			mv.visitInsn(Opcodes.ACONST_NULL);
			mv.visitVarInsn(Opcodes.ASTORE, 4);
			mv.visitLabel(loop);
			mv.visitVarInsn(Opcodes.ALOAD, 2);
			mv.visitVarInsn(Opcodes.ALOAD, 3);
			mv.visitVarInsn(Opcodes.ALOAD, 4);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, pos, "relative", "(L" + direction + ";)L" + pos + ";", false);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "getBlockState", "(L" + pos + ";)Ljava/lang/Object;", false);
			mv.visitInsn(Opcodes.POP);
			mv.visitInsn(Opcodes.ICONST_1);
			mv.visitInsn(Opcodes.IRETURN);
			mv.visitLabel(end);
			if (names) {
				mv.visitLocalVariable("this", "L" + TARGET + ";", null, start, end, 0);
				mv.visitLocalVariable("state", "Ljava/lang/Object;", null, start, end, 1);
				mv.visitLocalVariable("level", "L" + TARGET + ";", null, start, end, 2);
				mv.visitLocalVariable("pos", "L" + pos + ";", null, start, end, 3);
				mv.visitLocalVariable(directionName, "L" + direction + ";", null, loop, end, 4);
			}
			mv.visitMaxs(0, 0);
			mv.visitEnd();
			cw.visitEnd();
			return cw.toByteArray();
		};
		byte[] mixin = mixin("com/example/mixin/Cactus", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "replaceBlockState", "(L" + TARGET + ";L" + pos + ";" + OPERATION + ")Ljava/lang/Object;",
					null, null);
			AnnotationVisitor wrap = mv.visitAnnotation(WRAP_OPERATION, true);
			AnnotationVisitor methods = wrap.visitArray("method");
			methods.visit(null, "canSurvive");
			methods.visitEnd();
			AnnotationVisitor ats = wrap.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", "MIXINEXTRAS:EXPRESSION");
			at.visitEnd();
			ats.visitEnd();
			wrap.visit("require", 1); // Debugify's config: defaultRequire 1
			wrap.visitEnd();
			AnnotationVisitor definitionsAnnotation = mv.visitAnnotation("Lcom/llamalad7/mixinextras/expression/Definitions;", false);
			AnnotationVisitor definitions = definitionsAnnotation.visitArray("value");
			String[][] members = {{"getBlockState", "L" + TARGET + ";getBlockState(L" + pos + ";)Ljava/lang/Object;"},
					{"relative", "L" + pos + ";relative(L" + direction + ";)L" + pos + ";"}};
			for (String[] member : members) {
				AnnotationVisitor definition = definitions.visitAnnotation(null, "Lcom/llamalad7/mixinextras/expression/Definition;");
				definition.visit("id", member[0]);
				AnnotationVisitor method = definition.visitArray("method");
				method.visit(null, member[1]);
				method.visitEnd();
				definition.visitEnd();
			}
			for (String[] local : new String[][] {{"pos", pos, "true"}, {"direction", direction, "false"}}) {
				AnnotationVisitor definition = definitions.visitAnnotation(null, "Lcom/llamalad7/mixinextras/expression/Definition;");
				definition.visit("id", local[0]);
				AnnotationVisitor locals = definition.visitArray("local");
				AnnotationVisitor sugar = locals.visitAnnotation(null, LOCAL);
				sugar.visit("type", Type.getObjectType(local[1]));
				AnnotationVisitor name = sugar.visitArray("name");
				name.visit(null, local[0]);
				name.visitEnd();
				if (Boolean.parseBoolean(local[2])) sugar.visit("argsOnly", true);
				sugar.visitEnd();
				locals.visitEnd();
				definition.visitEnd();
			}
			definitions.visitEnd();
			definitionsAnnotation.visitEnd();
			AnnotationVisitor expression = mv.visitAnnotation("Lcom/llamalad7/mixinextras/expression/Expression;", false);
			AnnotationVisitor value = expression.visitArray("value");
			value.visit(null, "?.getBlockState(pos.relative(direction))");
			value.visitEnd();
			expression.visitEnd();
			mv.visitCode();
			mv.visitInsn(Opcodes.ACONST_NULL);
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitMaxs(1, 4);
			mv.visitEnd();
		});
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Cactus"));
		BiFunction<ClassIndex, Map<String, byte[]>, MixinPreflight> forge = (game, mod) -> new MixinPreflight(game,
				InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, ClassBytesSource.EMPTY, "Forge", true,
				MixinPreflight.ConstructorInjects.UNSAFE_POINTS, false);
		ClassIndex named = new ClassIndex(Map.of(TARGET, target.apply(true, "direction"))::get);

		// Fabric's Mixin knows the names: nothing to do.
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Cactus", mixin));
		assertTrue(preflight(named, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
		assertTrue(preflight(named, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());

		// SpongePowered's: reported before the repair, the argument taken by its slot after it; the other local stays named.
		List<PreflightFinding> before = forge.apply(named, mod).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(before.stream().anyMatch(f -> f.message().contains("replaceBlockState's expression takes the argument pos of canSurvive")
				&& f.message().contains("by its name")), before.toString());
		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = forge.apply(named, mod).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes).get("com/example/mixin/Cactus.class");
		assertNotNull(repaired, notes.toString());
		assertTrue(notes.stream().anyMatch(n -> n.message().contains("@WrapOperation replaceBlockState captures in its expression the argument pos by its name,"
				+ " which Forge's Mixin doesn't know; it takes the argument's slot, 3, instead")), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired).accept(node, 0);
		MethodNode handler = node.methods.stream().filter(m -> m.name.equals("replaceBlockState")).findFirst().orElseThrow();
		List<AnnotationNode> locals = InjectionPoints.definitionLocals(handler);
		assertEquals(2, locals.size());
		assertEquals(3, Annotations.value(locals.get(0), "index"));
		assertNull(Annotations.value(locals.get(0), "name"));
		assertEquals(Boolean.TRUE, Annotations.value(locals.get(0), "argsOnly"));
		assertEquals(List.of("direction"), Annotations.value(locals.get(1), "name"));
		assertNull(Annotations.value(locals.get(1), "index"));
		mod.put("com/example/mixin/Cactus", repaired);
		assertTrue(forge.apply(named, mod).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());

		// Without local variable names, the only argument of the type is it.
		ClassIndex unnamed = new ClassIndex(Map.of(TARGET, target.apply(false, "direction"))::get);
		Map<String, byte[]> fresh = Map.of("com/example/mixin/Cactus", mixin);
		assertNotNull(forge.apply(unnamed, fresh).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).get("com/example/mixin/Cactus.class"));

		// A local the expression defines by a name the method's table doesn't declare: the expression can't match.
		ClassIndex renamed = new ClassIndex(Map.of(TARGET, target.apply(true, "dir"))::get);
		List<PreflightFinding> unmatched = preflight(renamed, fresh::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertEquals(1, unmatched.size(), unmatched.toString());
		assertEquals(Severity.LEFT_OUT, unmatched.getFirst().severity());
		assertTrue(unmatched.getFirst().message().contains("MIXINEXTRAS:EXPRESSION"), unmatched.toString());
	}

	@Test
	void aWrappersLongOrDoubleThatMixinWouldStoreOverASharedLocalFails() throws Exception {
		// PRE-22: ShulkerBoxTooltip's three @WrapOperations into BundleMouseActions.onMouseScrolled share two locals. Forge's
		// Mixin (SpongePowered's 0.8.7) stores the first call's ItemStack in slot 12, MixinExtras then allocates the shared
		// locals (13 and 14), the third call's four values go in 12, 15, 16 and 17, and the second call's double in 12 and 13,
		// over a shared local: Target.allocateArgMapLocal takes the slot after a reused local unchecked. The Forge 64 to 66
		// clients stopped with "Bad local variable type ... double_2nd (locals[13])" as the test mod's menu opened.
		String ref = "Lcom/llamalad7/mixinextras/sugar/ref/LocalRef;";
		String share = "Lcom/llamalad7/mixinextras/sugar/Share;";
		// scroll(double, String): count(String), then next(double, int, int), then this.toggle(String, int, int).
		ClassWriter tw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		tw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor scroll = tw.visitMethod(Opcodes.ACC_PUBLIC, "scroll", "(DLjava/lang/String;)Z", null, null);
		scroll.visitCode();
		scroll.visitVarInsn(Opcodes.ALOAD, 3);
		scroll.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET, "count", "(Ljava/lang/String;)I", false);
		scroll.visitVarInsn(Opcodes.ISTORE, 4);
		scroll.visitVarInsn(Opcodes.DLOAD, 1);
		scroll.visitVarInsn(Opcodes.ILOAD, 4);
		scroll.visitVarInsn(Opcodes.ILOAD, 4);
		scroll.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET, "next", "(DII)I", false);
		scroll.visitVarInsn(Opcodes.ISTORE, 5);
		scroll.visitVarInsn(Opcodes.ALOAD, 0);
		scroll.visitVarInsn(Opcodes.ALOAD, 3);
		scroll.visitVarInsn(Opcodes.ILOAD, 4);
		scroll.visitVarInsn(Opcodes.ILOAD, 5);
		scroll.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "toggle", "(Ljava/lang/String;II)V", false);
		scroll.visitInsn(Opcodes.ICONST_1);
		scroll.visitInsn(Opcodes.IRETURN);
		scroll.visitMaxs(0, 0);
		scroll.visitEnd();
		tw.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(TARGET, tw.toByteArray())::get);
		// The handlers: the wrapped call's values, the Operation, then two @Share LocalRefs, "context" and "provider".
		Map<String, String[]> wrappers = Map.of(
				"count", new String[] {"changeCount", "(Ljava/lang/String;" + OPERATION + ref + ref + ")I", "L" + TARGET + ";count(Ljava/lang/String;)I"},
				"next", new String[] {"nextSelection", "(DII" + OPERATION + ref + ref + ")I", "L" + TARGET + ";next(DII)I"},
				"toggle", new String[] {"toggleItems", "(L" + TARGET + ";Ljava/lang/String;II" + OPERATION + ref + ref + ")V",
						"L" + TARGET + ";toggle(Ljava/lang/String;II)V"});
		Function<List<String>, byte[]> mixinOf = order -> mixin("com/example/mixin/Bundle", false, cw -> {
			for (String call : order) {
				String[] w = wrappers.get(call);
				MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, w[0], w[1], null, null);
				int params = Type.getArgumentTypes(w[1]).length;
				String[] shared = {"context", "provider"};
				for (int s = 0; s < 2; s++) {
					AnnotationVisitor sugar = mv.visitParameterAnnotation(params - 2 + s, share, false);
					sugar.visit("value", shared[s]);
					sugar.visitEnd();
				}
				AnnotationVisitor wrap = mv.visitAnnotation(WRAP_OPERATION, true);
				AnnotationVisitor methods = wrap.visitArray("method");
				methods.visit(null, "scroll(DLjava/lang/String;)Z");
				methods.visitEnd();
				AnnotationVisitor ats = wrap.visitArray("at");
				AnnotationVisitor at = ats.visitAnnotation(null, AT);
				at.visit("value", "INVOKE");
				at.visit("target", w[2]);
				at.visitEnd();
				ats.visitEnd();
				wrap.visitEnd();
				mv.visitEnd();
			}
		});
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Bundle"));
		BiFunction<Boolean, byte[], List<PreflightFinding>> check = (overlaps, mixin) -> new MixinPreflight(game, InstalledClasses.NONE,
				Map.of("com/example/mixin/Bundle", mixin)::get, ClassBytesSource.EMPTY, true, true, ClassBytesSource.EMPTY, "Forge", true,
				MixinPreflight.ConstructorInjects.UNSAFE_POINTS, false, false, false, true, false, false, overlaps)
				.check("test.mixins.json", config, Refmap.EMPTY, Set.of());

		// ShulkerBoxTooltip's order: the double's wrapper fails, and the two sharing its locals go with it.
		List<PreflightFinding> findings = check.apply(true, mixinOf.apply(List.of("count", "toggle", "next")));
		assertEquals(3, findings.size(), findings.toString());
		assertTrue(findings.stream().allMatch(f -> f.severity() == Severity.LEFT_OUT), findings.toString());
		PreflightFinding overlap = findings.getFirst();
		assertEquals("nextSelection(DII" + OPERATION + ref + ref + ")I", overlap.handler());
		assertTrue(overlap.message().contains("@WrapOperation nextSelection wraps the call to net.minecraft.world.Thing.next(DII)I in scroll"
				+ "(DLjava/lang/String;)Z in net.minecraft.world.Thing, whose double Forge's Mixin would store over the local this mixin shares"
				+ " as context (@Share)"), overlap.message());
		assertTrue(findings.stream().anyMatch(f -> f.message().startsWith("The injector of changeCount") && f.message().contains("goes with that of nextSelection")),
				findings.toString());
		assertTrue(findings.stream().anyMatch(f -> f.message().startsWith("The injector of toggleItems")), findings.toString());
		// Wrapped first, the double takes locals of its own: nothing to report.
		assertTrue(check.apply(true, mixinOf.apply(List.of("count", "next", "toggle"))).isEmpty());
		// Fabric's Mixin checks the second slot.
		assertTrue(check.apply(false, mixinOf.apply(List.of("count", "toggle", "next"))).isEmpty());
	}

	@Test
	void anInjectorNamingSeveralMethodsNeedsOnlyOneOfThemAsMixinDoes() throws Exception {
		// PRE-23: Emotecraft's @Inject(method = {"tick", "method_5773"}, remap = false) into Avatar, which declares no tick:
		// Player Animation Library's mixin adds one (an @Intrinsic). Mixin fails an injector only where none of its selectors
		// finds a method (TargetSelectors.validate); pre-flight judged the last name alone and disabled Emotecraft on Forge 64
		// to 66.
		String adder = "com/example/animation/AvatarMixin";
		ClassWriter aw = new ClassWriter(0);
		aw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, adder, null, "java/lang/Object", null);
		AnnotationVisitor am = aw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor targets = am.visitArray("value");
		targets.visit(null, Type.getObjectType(TARGET));
		targets.visitEnd();
		am.visitEnd();
		MethodVisitor added = aw.visitMethod(Opcodes.ACC_PUBLIC, "aiStep", "()V", null, null);
		added.visitAnnotation("Lorg/spongepowered/asm/mixin/Intrinsic;", false).visitEnd();
		added.visitEnd();
		aw.visitEnd();
		byte[] adderClass = aw.toByteArray();
		InstalledClasses installed = new InstalledClasses(List.of(new InstalledClasses.Jar("Player Animation Library", adder::equals,
				name -> name.equals(adder) ? adderClass : null, () -> List.of(adder), true)));
		byte[] emote = mixin("com/example/mixin/Emote", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "onTick", "(" + CALLBACK_INFO + ")V", null, null);
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, true);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "aiStep");
			methods.visit(null, "method_6007");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", "TAIL");
			at.visitEnd();
			ats.visitEnd();
			ann.visit("remap", false);
			ann.visit("require", 1); // Emotecraft's config: defaultRequire 1
			ann.visitEnd();
			mv.visitEnd();
		});
		ClassIndex game = new ClassIndex(Map.of(TARGET, targetClass())::get);
		Map<String, byte[]> mod = Map.of("com/example/mixin/Emote", emote);
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Emote"));

		List<PreflightFinding> findings = new MixinPreflight(game, installed, mod::get, ClassBytesSource.EMPTY, true, true, ClassBytesSource.EMPTY)
				.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertEquals(1, findings.size(), findings.toString());
		assertEquals(Severity.INFO, findings.getFirst().severity());
		assertTrue(findings.getFirst().message().contains("targets method aiStep, which is not in net.minecraft.world.Thing; Player Animation Library's mixin"
				+ " com.example.animation.AvatarMixin adds it, so it was not verified"), findings.toString());

		// Where nothing adds either, the injector fails, naming both.
		List<PreflightFinding> none = preflight(game, mod::get, ClassBytesSource.EMPTY, true).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertEquals(1, none.size(), none.toString());
		assertEquals(Severity.LEFT_OUT, none.getFirst().severity());
		assertTrue(none.getFirst().message().contains("@Inject targets methods aiStep, method_6007, none of which exists in net.minecraft.world.Thing"),
				none.toString());
	}

	@Test
	void aRedirectsLocalArgumentTakenByNameIsTakenByItsSlotWhereMixinDoesntKnowArgumentNames() throws Exception {
		// CORPUS-82: ImmediatelyFast's buffer upload hook, a @Redirect of DirectStateAccess.bufferSubData in 26.3's
		// GlCommandEncoder.writeToBuffer(GpuBufferSlice, ByteBuffer), takes @Local(name = "slice", argsOnly = true)
		// GpuBufferSlice. Pre-flight didn't take com.mojang.renderpearl for the game's, judged nothing of the mixin, and Forge
		// 66's client stopped: "Unable to find matching local!". Its mixin names its target by a string.
		String encoder = "com/mojang/renderpearl/backend/opengl/GlCommandEncoder";
		String access = "com/mojang/renderpearl/backend/opengl/DirectStateAccess";
		String slice = "com/mojang/renderpearl/api/buffers/GpuBufferSlice";
		String upload = "(IJLjava/nio/ByteBuffer;I)V";
		ClassWriter target = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		target.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, encoder, null, "java/lang/Object", null);
		target.visitField(Opcodes.ACC_PRIVATE, "access", "L" + access + ";", null, null).visitEnd();
		MethodVisitor write = target.visitMethod(Opcodes.ACC_PUBLIC, "writeToBuffer", "(L" + slice + ";Ljava/nio/ByteBuffer;)V", null, null);
		org.objectweb.asm.Label start = new org.objectweb.asm.Label();
		org.objectweb.asm.Label end = new org.objectweb.asm.Label();
		write.visitCode();
		write.visitLabel(start);
		write.visitVarInsn(Opcodes.ALOAD, 0);
		write.visitFieldInsn(Opcodes.GETFIELD, encoder, "access", "L" + access + ";");
		write.visitInsn(Opcodes.ICONST_0);
		write.visitInsn(Opcodes.LCONST_0);
		write.visitVarInsn(Opcodes.ALOAD, 2);
		write.visitInsn(Opcodes.ICONST_0);
		write.visitMethodInsn(Opcodes.INVOKEVIRTUAL, access, "bufferSubData", upload, false);
		write.visitInsn(Opcodes.RETURN);
		write.visitLabel(end);
		write.visitLocalVariable("this", "L" + encoder + ";", null, start, end, 0);
		write.visitLocalVariable("slice", "L" + slice + ";", null, start, end, 1);
		write.visitLocalVariable("data", "Ljava/nio/ByteBuffer;", null, start, end, 2);
		write.visitMaxs(0, 0);
		write.visitEnd();
		target.visitEnd();
		ClassWriter accessClass = new ClassWriter(0);
		accessClass.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, access, null, "java/lang/Object", null);
		accessClass.visitMethod(Opcodes.ACC_PUBLIC, "bufferSubData", upload, null, null).visitEnd();
		accessClass.visitEnd();
		ClassWriter sliceClass = new ClassWriter(0);
		sliceClass.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, slice, null, "java/lang/Object", null);
		sliceClass.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(encoder, target.toByteArray(), access, accessClass.toByteArray(), slice, sliceClass.toByteArray())::get);

		for (boolean byString : new boolean[] {false, true}) {
			ClassWriter cw = new ClassWriter(0);
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "com/example/mixin/Upload", null, "java/lang/Object", null);
			AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
			AnnotationVisitor values = m.visitArray(byString ? "targets" : "value");
			values.visit(null, byString ? encoder.replace('/', '.') : Type.getObjectType(encoder));
			values.visitEnd();
			m.visitEnd();
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "fix", "(L" + access + ";IJLjava/nio/ByteBuffer;IL" + slice + ";)V", null, null);
			AnnotationVisitor sugar = mv.visitParameterAnnotation(5, LOCAL, false);
			AnnotationVisitor names = sugar.visitArray("name");
			names.visit(null, "slice");
			names.visitEnd();
			sugar.visit("argsOnly", true);
			sugar.visitEnd();
			AnnotationVisitor ann = mv.visitAnnotation(REDIRECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "writeToBuffer");
			methods.visitEnd();
			AnnotationVisitor at = ann.visitAnnotation("at", AT);
			at.visit("value", "INVOKE");
			at.visit("target", "L" + access + ";bufferSubData" + upload);
			at.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 8);
			mv.visitEnd();
			cw.visitEnd();
			Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Upload", cw.toByteArray()));
			MixinConfigFile config = MixinConfigFile.parse(config(true, "Upload"));
			MixinPreflight forge = new MixinPreflight(game, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, ClassBytesSource.EMPTY,
					"Forge", true, MixinPreflight.ConstructorInjects.UNSAFE_POINTS, false);
			List<PreflightFinding> notes = new ArrayList<>();
			byte[] repaired = forge.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes).get("com/example/mixin/Upload.class");
			assertNotNull(repaired, "by string " + byString + ": " + notes + " " + forge.check("test.mixins.json", config, Refmap.EMPTY, Set.of()));
			ClassNode node = new ClassNode();
			new ClassReader(repaired).accept(node, 0);
			MethodNode fix = node.methods.stream().filter(mn -> mn.name.equals("fix")).findFirst().orElseThrow();
			AnnotationNode local = Annotations.ofParameter(fix, 5).stream().filter(a -> a.desc.equals(LOCAL)).findFirst().orElseThrow();
			assertEquals(1, Annotations.value(local, "index"), "by string " + byString);
			assertNull(Annotations.value(local, "name"));
		}
	}

	@Test
	void aLocalTakenByNameFindsItUnderTheNameARecompiledBuildGivesIt() throws Exception {
		// LOCAL-3: Fabric API's lifecycle hook takes @Local(name = "level") ServerLevel at ServerLevel.close() in
		// MinecraftServer.stopServer; Forge 64's build, recompiled from decompiled source, names that loop's variable levelx,
		// as the method declares another level before it. MixinExtras looks among the locals in scope at the call.
		String level = "Ljava/lang/String;";
		java.util.function.Function<Boolean, byte[]> target = second -> {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
			MethodVisitor stop = cw.visitMethod(Opcodes.ACC_PUBLIC, "stop", "()V", null, null);
			org.objectweb.asm.Label start = new org.objectweb.asm.Label();
			org.objectweb.asm.Label saveStart = new org.objectweb.asm.Label();
			org.objectweb.asm.Label saveEnd = new org.objectweb.asm.Label();
			org.objectweb.asm.Label closeStart = new org.objectweb.asm.Label();
			org.objectweb.asm.Label end = new org.objectweb.asm.Label();
			stop.visitCode();
			stop.visitLabel(start);
			stop.visitVarInsn(Opcodes.ALOAD, 0);
			stop.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "overworld", "()" + level, false);
			stop.visitVarInsn(Opcodes.ASTORE, 1);
			stop.visitLabel(saveStart);
			stop.visitVarInsn(Opcodes.ALOAD, 1);
			stop.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET, "save", "(" + level + ")V", false);
			stop.visitLabel(saveEnd);
			stop.visitVarInsn(Opcodes.ALOAD, 0);
			stop.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "overworld", "()" + level, false);
			stop.visitVarInsn(Opcodes.ASTORE, 1);
			if (second) {
				stop.visitVarInsn(Opcodes.ALOAD, 0);
				stop.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "overworld", "()" + level, false);
				stop.visitVarInsn(Opcodes.ASTORE, 2);
			}
			stop.visitLabel(closeStart);
			stop.visitVarInsn(Opcodes.ALOAD, 1);
			stop.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET, "close", "(" + level + ")V", false);
			stop.visitInsn(Opcodes.RETURN);
			stop.visitLabel(end);
			stop.visitLocalVariable("this", "L" + TARGET + ";", null, start, end, 0);
			stop.visitLocalVariable("level", level, null, saveStart, saveEnd, 1);
			stop.visitLocalVariable("levelx", level, null, closeStart, end, 1);
			if (second) stop.visitLocalVariable("other", level, null, closeStart, end, 2);
			stop.visitMaxs(0, 0);
			stop.visitEnd();
			cw.visitEnd();
			return cw.toByteArray();
		};
		byte[] mixin = mixin("com/example/mixin/Unload", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "onUnload", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;" + level + ")V", null, null);
			AnnotationVisitor local = mv.visitParameterAnnotation(1, "Lcom/llamalad7/mixinextras/sugar/Local;", false);
			AnnotationVisitor names = local.visitArray("name");
			names.visit(null, "level");
			names.visitEnd();
			local.visitEnd();
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "stop");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", "INVOKE");
			at.visit("target", "L" + TARGET + ";close(" + level + ")V");
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnd();
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 3);
			mv.visitEnd();
		});
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Unload"));
		ClassIndex forge = new ClassIndex(Map.of(TARGET, target.apply(false))::get);
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Unload", mixin));
		List<PreflightFinding> found = preflight(forge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(found.stream().anyMatch(f -> f.message().contains("captures the local variable level, which stop in")
				&& f.message().contains("doesn't have in scope where the injector applies")), found.toString());

		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = preflight(forge, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes)
				.get("com/example/mixin/Unload.class");
		assertNotNull(repaired, notes.toString());
		assertTrue(notes.getFirst().message().contains("the local level of type java.lang.String, which stop in " + TARGET.replace('/', '.')
				+ " names levelx where the injector applies; the @Local takes that name too"), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired).accept(node, 0);
		MethodNode handler = node.methods.stream().filter(m -> m.name.equals("onUnload")).findFirst().orElseThrow();
		assertEquals(List.of("level", "levelx"), Annotations.value(Annotations.ofParameter(handler, 1).getFirst(), "name"));
		mod.put("com/example/mixin/Unload", repaired);
		assertTrue(preflight(forge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());

		// Two of the type in scope there: which one is meant can't be told, and the injector is reported.
		ClassIndex two = new ClassIndex(Map.of(TARGET, target.apply(true))::get);
		Map<String, byte[]> original = Map.of("com/example/mixin/Unload", mixin);
		assertTrue(preflight(two, original::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
		assertFalse(preflight(two, original::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());
	}

	@Test
	void anInjectionAtACallMadeThroughNeoForgesExtensionsIsPointedAtTheCallLeadingThere() throws Exception {
		// HOOK-3: Ledger logs blocks an explosion removes at BlockBehaviour.onExplosionHit's setBlock, which NeoForge's build
		// makes through BlockState.onBlockExploded, a default method of its extension interfaces.
		String thing = "L" + TARGET + ";";
		java.util.function.Function<String, Map<String, byte[]>> game = extension -> {
			ClassWriter target = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			target.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", new String[] {extension});
			MethodVisitor tick = target.visitMethod(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
			tick.visitCode();
			tick.visitVarInsn(Opcodes.ALOAD, 0);
			tick.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "exploded", "()V", false);
			tick.visitInsn(Opcodes.RETURN);
			tick.visitMaxs(0, 0);
			tick.visitEnd();
			MethodVisitor helper = target.visitMethod(Opcodes.ACC_PUBLIC, "helper", "(I)V", null, null);
			helper.visitCode();
			helper.visitInsn(Opcodes.RETURN);
			helper.visitMaxs(0, 0);
			helper.visitEnd();
			target.visitEnd();
			ClassWriter itf = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			itf.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, extension, null, "java/lang/Object", null);
			MethodVisitor exploded = itf.visitMethod(Opcodes.ACC_PUBLIC, "exploded", "()V", null, null);
			exploded.visitCode();
			exploded.visitVarInsn(Opcodes.ALOAD, 0);
			exploded.visitTypeInsn(Opcodes.CHECKCAST, TARGET);
			exploded.visitInsn(Opcodes.ICONST_0);
			exploded.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "helper", "(I)V", false);
			exploded.visitInsn(Opcodes.RETURN);
			exploded.visitMaxs(0, 0);
			exploded.visitEnd();
			itf.visitEnd();
			return Map.of(TARGET, target.toByteArray(), extension, itf.toByteArray());
		};
		byte[] original = mixin("com/example/mixin/Explosions", false, cw -> atHelperCall(cw, INJECT, "inject",
				"(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", mv -> {
					mv.visitInsn(Opcodes.RETURN);
					mv.visitMaxs(0, 2);
					mv.visitEnd();
				}));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Explosions"));
		ClassIndex neoForge = new ClassIndex(game.apply("net/neoforged/extensions/IThingExtension")::get);
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Explosions", original));

		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight(neoForge, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		assertEquals(1, notes.size(), notes.toString());
		assertTrue(notes.getFirst().message().contains(TARGET.replace('/', '.') + ".helper(I)V, which this game build makes through NeoForge's "
				+ TARGET.replace('/', '.') + ".exploded()V"), notes.toString());
		mod.put("com/example/mixin/Explosions", repaired.get("com/example/mixin/Explosions.class"));
		assertTrue(preflight(neoForge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());

		// A default method of the game's own isn't taken for NeoForge's.
		ClassIndex vanilla = new ClassIndex(game.apply("net/minecraft/world/ThingExtension")::get);
		assertTrue(preflight(vanilla, Map.of("com/example/mixin/Explosions", original)::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
	}

	@Test
	void aMixinReplacingAFieldThePlatformGivesItsOwnObjectIsReported() throws Exception {
		// POI-2: NeoForge's and Forge's PoiTypes.TYPE_BY_STATE is their registry callbacks' map; Lithium's static initializer
		// replaces it with a copy, which the callbacks' later entries never reach.
		java.util.function.Function<Boolean, byte[]> target = platform -> {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
			cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "TYPES", "Ljava/util/Map;", null, null).visitEnd();
			MethodVisitor init = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
			init.visitCode();
			if (platform) {
				init.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraftforge/registries/GameData$Callbacks", "stateMap", "()Ljava/util/Map;", false);
			} else {
				init.visitTypeInsn(Opcodes.NEW, "java/util/HashMap");
				init.visitInsn(Opcodes.DUP);
				init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false);
			}
			init.visitFieldInsn(Opcodes.PUTSTATIC, TARGET, "TYPES", "Ljava/util/Map;");
			init.visitInsn(Opcodes.RETURN);
			init.visitMaxs(0, 0);
			init.visitEnd();
			cw.visitEnd();
			return cw.toByteArray();
		};
		byte[] mixin = mixin("com/example/mixin/Types", false, cw -> {
			FieldVisitor field = cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "TYPES", "Ljava/util/Map;", null, null);
			field.visitAnnotation(MixinNames.SHADOW, true).visitEnd();
			field.visitAnnotation(MixinNames.MUTABLE, true).visitEnd();
			field.visitEnd();
			MethodVisitor init = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
			init.visitCode();
			init.visitTypeInsn(Opcodes.NEW, "java/util/HashMap");
			init.visitInsn(Opcodes.DUP);
			init.visitFieldInsn(Opcodes.GETSTATIC, "com/example/mixin/Types", "TYPES", "Ljava/util/Map;");
			init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "(Ljava/util/Map;)V", false);
			init.visitFieldInsn(Opcodes.PUTSTATIC, "com/example/mixin/Types", "TYPES", "Ljava/util/Map;");
			init.visitInsn(Opcodes.RETURN);
			init.visitMaxs(0, 0);
			init.visitEnd();
		});
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Types"));
		ClassIndex platform = new ClassIndex(Map.of(TARGET, target.apply(true))::get);
		List<PreflightFinding> findings = preflight(platform, Map.of("com/example/mixin/Types", mixin)::get, ClassBytesSource.EMPTY, false)
				.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertEquals(1, findings.size(), findings.toString());
		assertTrue(findings.getFirst().message().contains("replaces " + TARGET.replace('/', '.') + ".TYPES, which is GameData.Callbacks.stateMap"),
				findings.toString());
		// The target's own map, as vanilla's: replacing it is the mod's business.
		ClassIndex vanilla = new ClassIndex(Map.of(TARGET, target.apply(false))::get);
		assertTrue(preflight(vanilla, Map.of("com/example/mixin/Types", mixin)::get, ClassBytesSource.EMPTY, false)
				.check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());
	}

	@Test
	void anInjectorSelectingEveryMethodIsJudgedByTheMethodsMixinScans() throws Exception {
		// WILD-1: a bare "*" selects each method of the target but its static ones, for a handler that isn't static.
		byte[] found = mixin("com/example/mixin/Every", false, cw -> inject(cw, "every", "*", "INVOKE", "L" + TARGET + ";helper(I)V", 1, null));
		assertTrue(run(config(true, "Every"), null, Map.of("com/example/mixin/Every", found), false).isEmpty(), "tick makes the call");
		byte[] missing = mixin("com/example/mixin/Every", false, cw -> inject(cw, "every", "*", "INVOKE", "L" + TARGET + ";gone()V", 1, null));
		List<PreflightFinding> findings = run(config(true, "Every"), null, Map.of("com/example/mixin/Every", missing), false);
		assertEquals(1, findings.size(), findings.toString());
		assertTrue(findings.getFirst().message().contains("by \"*\" and applies 0 time(s) where it requires 1"), findings.toString());
		byte[] optional = mixin("com/example/mixin/Every", false, cw -> inject(cw, "every", "*", "INVOKE", "L" + TARGET + ";gone()V", 0, null));
		assertTrue(run(config(true, "Every"), null, Map.of("com/example/mixin/Every", optional), false).isEmpty(), "nothing required");

		// Only a static method makes the call: not scanned for this handler.
		ClassWriter onlyStatic = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		onlyStatic.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor statics = onlyStatic.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "tick", "(L" + TARGET + ";)V", null, null);
		statics.visitCode();
		statics.visitVarInsn(Opcodes.ALOAD, 0);
		statics.visitInsn(Opcodes.ICONST_0);
		statics.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "helper", "(I)V", false);
		statics.visitInsn(Opcodes.RETURN);
		statics.visitMaxs(0, 0);
		statics.visitEnd();
		onlyStatic.visitEnd();
		ClassIndex staticGame = new ClassIndex(Map.of(TARGET, onlyStatic.toByteArray())::get);
		assertEquals(1, preflight(staticGame, Map.of("com/example/mixin/Every", found)::get, ClassBytesSource.EMPTY, false)
				.check("test.mixins.json", MixinConfigFile.parse(config(true, "Every")), Refmap.EMPTY, Set.of()).size());
	}

	@Test
	void anInjectorSelectingEveryMethodFollowsACallIntoThePlatformsHook() throws Exception {
		// WILD-1: Balm's drag hooks select MouseHandler's methods by "*" at Screen.mouseDragged, which Forge 52's patch moved
		// into ForgeHooksClient.onScreenMouseDrag, a void hook firing its events around it (HOOK-3 then points them there).
		String hooks = "net/minecraftforge/client/ThingHooks";
		ClassWriter target = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		target.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor drag = target.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC, "lambda$onMove$0", "(I)V", null, null);
		drag.visitCode();
		drag.visitVarInsn(Opcodes.ALOAD, 0);
		drag.visitVarInsn(Opcodes.ILOAD, 1);
		drag.visitMethodInsn(Opcodes.INVOKESTATIC, hooks, "onDrag", "(L" + TARGET + ";I)V", false);
		drag.visitInsn(Opcodes.RETURN);
		drag.visitMaxs(0, 0);
		drag.visitEnd();
		MethodVisitor dragged = target.visitMethod(Opcodes.ACC_PUBLIC, "dragged", "(I)Z", null, null);
		dragged.visitCode();
		dragged.visitInsn(Opcodes.ICONST_1);
		dragged.visitInsn(Opcodes.IRETURN);
		dragged.visitMaxs(0, 0);
		dragged.visitEnd();
		target.visitEnd();
		ClassWriter hook = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		hook.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, hooks, null, "java/lang/Object", null);
		MethodVisitor onDrag = hook.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "onDrag", "(L" + TARGET + ";I)V", null, null);
		onDrag.visitCode();
		onDrag.visitVarInsn(Opcodes.ALOAD, 0);
		onDrag.visitVarInsn(Opcodes.ILOAD, 1);
		onDrag.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "dragged", "(I)Z", false);
		onDrag.visitInsn(Opcodes.POP);
		onDrag.visitInsn(Opcodes.RETURN);
		onDrag.visitMaxs(0, 0);
		onDrag.visitEnd();
		hook.visitEnd();
		ClassIndex forge = new ClassIndex(Map.of(TARGET, target.toByteArray(), hooks, hook.toByteArray())::get);
		byte[] original = mixin("com/example/mixin/Drag", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "onDrag", "(ILorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null);
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "*");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", "INVOKE");
			at.visit("target", "L" + TARGET + ";dragged(I)Z");
			at.visitEnd();
			ats.visitEnd();
			ann.visit("require", 1);
			ann.visitEnd();
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 3);
			mv.visitEnd();
		});
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Drag"));
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Drag", original));
		assertEquals(1, preflight(forge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).size(),
				"unrepaired, the call occurs in none of the methods");

		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight(forge, mod::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		assertEquals(1, notes.size(), notes.toString());
		assertTrue(notes.getFirst().message().contains("which this game build makes through NeoForge's " + hooks.replace('/', '.') + ".onDrag"), notes.toString());
		mod.put("com/example/mixin/Drag", repaired.get("com/example/mixin/Drag.class"));
		assertTrue(preflight(forge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());
	}

	/**
	 * The game class's {@code tick} as Forge 47's {@code ServerPlayerGameMode.destroyBlock}, {@code helper} as vanilla's
	 * {@code playerWillDestroy}: vanilla calls it, then {@code remove} and {@code destroyed}, after the restriction check
	 * and before the creative branch; Forge's patch adds a check looking like it ({@code startBreak}) and moves the three
	 * calls into a {@code removeBlock} of its own, called in each branch, which reaches {@code helper} through its
	 * extension's {@code onDestroyed}. With {@code damageFirst} the patch damages the tool before the creative branch;
	 * without {@code reaches}, the extension doesn't call {@code helper}.
	 */
	private static Map<String, byte[]> destroyingThing(Path dir, boolean patched, boolean damageFirst, boolean reaches) throws Exception {
		String members = "Thing level; Thing player;"
				+ " public boolean restricted(Thing level, Thing player) { return false; }"
				+ " public boolean startBreak(Thing level, Thing player) { return false; }"
				+ " public boolean creative() { return false; }"
				+ " public void helper(Thing level, Thing player) { }"
				+ " public boolean remove(boolean drop) { return true; }"
				+ " public void destroyed() { }"
				+ " public void damage() { }";
		String vanilla = "package net.minecraft.world; public class Thing { " + members
				+ " public boolean tick() {"
				+ "   if (this.restricted(this.level, this.player)) { return false; } else {"
				+ "     this.helper(this.level, this.player);"
				+ "     boolean removed = this.remove(false);"
				+ "     if (removed) { this.destroyed(); }"
				+ "     if (this.creative()) { return true; } else { this.damage(); return true; } } } }";
		String forge = "package net.minecraft.world; public class Thing implements net.minecraftforge.common.IThingExtension { " + members
				+ " public boolean tick() {"
				+ "   if (this.startBreak(this.level, this.player)) { return false; }"
				+ "   else if (this.restricted(this.level, this.player)) { return false; } else {"
				+ (damageFirst ? " this.damage();" : "")
				+ "     if (this.creative()) { this.removeBlock(false); return true; }"
				+ "     else { " + (damageFirst ? "" : "this.damage(); ") + "boolean removed = this.removeBlock(true); return removed || true; } } }"
				+ " private boolean removeBlock(boolean drop) {"
				+ "   boolean removed = this.onDestroyed(this.level, this.player, drop);"
				+ "   if (removed) { this.destroyed(); }"
				+ "   return removed; } }";
		String extension = "package net.minecraftforge.common; import net.minecraft.world.Thing; public interface IThingExtension {"
				+ " private Thing self() { return (Thing) this; }"
				+ " default boolean onDestroyed(Thing level, Thing player, boolean drop) {"
				+ (reaches ? " self().helper(level, player);" : "")
				+ "   return self().remove(drop); } }";
		return patched
				? TestSources.compile(dir, Map.of("net/minecraft/world/Thing.java", forge, "net/minecraftforge/common/IThingExtension.java", extension))
				: TestSources.compile(dir, Map.of("net/minecraft/world/Thing.java", vanilla));
	}

	@Test
	void anInjectionAtACallThePatchMovedIntoAMethodOfItsOwnStaysAtVanillasPlace(@TempDir Path dir) throws Exception {
		// CORPUS-72: Magna's hammers break their area from an @Inject at ServerPlayerGameMode.destroyBlock's
		// playerWillDestroy, cancelling the break; Forge 47's patch moved that call into a removeBlock of its own, called in
		// the creative and the survival branch (after the tool's damage there). The hook stays before the creative branch.
		String thing = "L" + TARGET + ";";
		Map<String, byte[]> vanilla = destroyingThing(dir.resolve("vanilla"), false, false, true);
		Function<Map<String, byte[]>, ClassIndex> index = classes -> new ClassIndex(classes::get);
		byte[] original = mixin("com/example/mixin/Breaks", false, cw -> atHelperCall(cw, INJECT, "inject",
				"(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V", "(" + thing + thing + ")V", mv -> {
					mv.visitInsn(Opcodes.RETURN);
					mv.visitMaxs(0, 2);
					mv.visitEnd();
				}));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Breaks"));
		ClassIndex forge = index.apply(destroyingThing(dir.resolve("forge"), true, false, true));
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Breaks", original));
		assertEquals(1, preflight(forge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).size(),
				"the call isn't in the patched method");

		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = new MixinPreflight(forge, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		assertEquals(1, notes.size(), notes.toString());
		assertTrue(notes.getFirst().message().contains("'s patch moved out of this method into " + TARGET.replace('/', '.') + ".removeBlock(Z)Z")
				&& notes.getFirst().message().contains("before the call of " + TARGET.replace('/', '.') + ".creative()Z after it"), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get("com/example/mixin/Breaks.class")).accept(node, 0);
		MethodNode handler = node.methods.stream().filter(m -> m.name.equals("inject")).findFirst().orElseThrow();
		AnnotationNode at = Annotations.list(Annotations.value(Annotations.find(handler, INJECT), "at")).getFirst();
		assertEquals(thing + "creative()Z", Annotations.value(at, "target"));
		mod.put("com/example/mixin/Breaks", repaired.get("com/example/mixin/Breaks.class"));
		assertTrue(preflight(forge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());

		Map<String, byte[]> unrepaired = Map.of("com/example/mixin/Breaks", original);
		// Without vanilla's code, its place can't be told.
		assertTrue(preflight(forge, unrepaired::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
		// The patch damaging the tool before the creative branch: before that call isn't vanilla's place.
		ClassIndex damaging = index.apply(destroyingThing(dir.resolve("damaging"), true, true, true));
		assertTrue(new MixinPreflight(damaging, InstalledClasses.NONE, unrepaired::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
		// The patch taking the call out rather than moving it: the hook would run for a call never made.
		ClassIndex removing = index.apply(destroyingThing(dir.resolve("removing"), true, false, false));
		assertTrue(new MixinPreflight(removing, InstalledClasses.NONE, unrepaired::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
	}

	private static final String HOOKS = "net/neoforged/Hooks";

	/** The game class with {@code helper(I)V}, whose {@code tick} calls {@code Hooks.draw} instead, with {@code hookDesc}. */
	private static byte[] callingHook(String hookDesc) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor tick = cw.visitMethod(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
		tick.visitCode();
		tick.visitVarInsn(Opcodes.ALOAD, 0);
		for (Type arg : Type.getArgumentTypes(hookDesc)) {
			if (arg.getSort() == Type.INT) tick.visitInsn(Opcodes.ICONST_0);
			else if (!arg.getInternalName().equals(TARGET)) tick.visitInsn(Opcodes.ACONST_NULL);
		}
		tick.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "draw", hookDesc, false);
		tick.visitInsn(Opcodes.RETURN);
		tick.visitMaxs(0, 0);
		tick.visitEnd();
		MethodVisitor helper = cw.visitMethod(Opcodes.ACC_PUBLIC, "helper", "(I)V", null, null);
		helper.visitCode();
		helper.visitInsn(Opcodes.RETURN);
		helper.visitMaxs(0, 0);
		helper.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** NeoForge's hooks class: {@code draw} calls a private static method that makes the game's call, if {@code calls}. */
	private static byte[] hooks(String drawDesc, boolean calls) {
		String thing = "L" + TARGET + ";";
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, HOOKS, null, "java/lang/Object", null);
		MethodVisitor draw = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "draw", drawDesc, null, null);
		draw.visitCode();
		draw.visitVarInsn(Opcodes.ALOAD, 0);
		draw.visitVarInsn(Opcodes.ILOAD, Type.getArgumentTypes(drawDesc).length - 1);
		draw.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "drawInternal", "(" + thing + "I)V", false);
		draw.visitInsn(Opcodes.RETURN);
		draw.visitMaxs(0, 0);
		draw.visitEnd();
		MethodVisitor inner = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "drawInternal", "(" + thing + "I)V", null, null);
		inner.visitCode();
		if (calls) {
			inner.visitVarInsn(Opcodes.ALOAD, 0);
			inner.visitVarInsn(Opcodes.ILOAD, 1);
			inner.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "helper", "(I)V", false);
		}
		inner.visitInsn(Opcodes.RETURN);
		inner.visitMaxs(0, 0);
		inner.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
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
	 * An injector in a group with others is one of the mod's alternatives (Iris's {@code @Group(min = 1, max = 1)} pairs):
	 * pre-flight neither points it elsewhere, which could make two of a group apply, nor requires it, as Mixin doesn't.
	 */
	@Test
	void anInjectorInAGroupIsLeftToItsGroup() throws Exception {
		Map<String, byte[]> mod = Map.of("com/example/mixin/Grouped", mixin("com/example/mixin/Grouped", false, cw -> {
			// {handler, the call it injects at}: the first's isn't in this build (its overload taking a string is), the second's is.
			for (String[] member : new String[][] {{"inject", "(I)V"}, {"alternative", "(ILjava/lang/String;)V"}}) {
				grouped(cw, member[0], "alternatives", -1, 1, "L" + TARGET + ";helper" + member[1]);
			}
		}));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Grouped"));
		ClassIndex neoForge = new ClassIndex(Map.of(TARGET, callingHelper("(ILjava/lang/String;)V"))::get);

		assertTrue(preflight(neoForge, mod::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty());
		List<PreflightFinding> findings = preflight(neoForge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(findings.stream().noneMatch(f -> f.severity() == Severity.FAIL || f.severity() == Severity.LEFT_OUT), findings.toString());
	}

	/**
	 * GROUP-2: Mixin needs at least one injection of every group, whatever its minimum, so a group's only member is required
	 * as an injector alone is, and repaired as one. Essential's key and character hooks, each alone in a group declaring
	 * no minimum, are at calls Forge's keyboard handler makes inside its own hooks; they stopped the Forge 61 to 66 clients.
	 */
	@Test
	void aGroupsOnlyInjectorIsRepairedAndRequiredAsAnInjectorAlone() throws Exception {
		String thing = "L" + TARGET + ";";
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Keys", mixin("com/example/mixin/Keys", false,
				cw -> grouped(cw, "onKeyTyped", "onKeyTyped", -1, -1, "L" + TARGET + ";helper(I)V"))));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Keys"));

		// Forge's hook takes the call's receiver and arguments, and makes the call: the injector is pointed at the hook's call.
		String plain = "(" + thing + "I)V";
		ClassIndex forge = new ClassIndex(Map.of(TARGET, callingHook(plain), HOOKS, hooks(plain, true))::get);
		List<PreflightFinding> moved = new ArrayList<>();
		byte[] repaired = preflight(forge, mod::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), moved).get("com/example/mixin/Keys.class");
		assertNotNull(repaired, moved.toString());
		assertEquals(1, moved.size(), moved.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired).accept(node, 0);
		MethodNode handler = node.methods.stream().filter(m -> m.name.equals("onKeyTyped")).findFirst().orElseThrow();
		assertEquals("L" + HOOKS + ";draw" + plain, Annotations.value(Annotations.list(Annotations.value(Annotations.find(handler, INJECT), "at")).getFirst(), "target"));
		assertEquals("onKeyTyped", PreflightAsm.group(handler), "it stays in its group");
		mod.put("com/example/mixin/Keys", repaired);
		List<PreflightFinding> after = preflight(forge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());

		// Where nothing makes the call, the group fails as an injector alone would, and the self-contained mixin loses it.
		mod.put("com/example/mixin/Keys", mixin("com/example/mixin/Keys", false, cw -> grouped(cw, "onKeyTyped", "", -1, -1, "L" + TARGET + ";helper(I)V")));
		ClassIndex neither = new ClassIndex(Map.of(TARGET, callingHelper("(J)V"))::get);
		List<PreflightFinding> findings = preflight(neither, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		List<PreflightFinding> group = findings.stream().filter(f -> f.message().startsWith("@Group default needs 1 of its 1 injectors to apply, and none can")).toList();
		assertEquals(1, group.size(), findings.toString());
		assertEquals(Severity.LEFT_OUT, group.getFirst().severity(), group.toString());
	}

	/** {@code handler}, an {@code @Inject} on {@code tick} at {@code call}, in {@code @Group(name, min, max)} ({@code -1}: not declared). */
	private static void grouped(ClassWriter cw, String handler, String name, int min, int max, String call) {
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, handler, "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null);
		AnnotationVisitor group = mv.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Group;", false);
		if (!name.isEmpty()) group.visit("name", name);
		if (min >= 0) group.visit("min", min);
		if (max >= 0) group.visit("max", max);
		group.visitEnd();
		AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
		AnnotationVisitor methods = ann.visitArray("method");
		methods.visit(null, "tick");
		methods.visitEnd();
		AnnotationVisitor ats = ann.visitArray("at");
		AnnotationVisitor at = ats.visitAnnotation(null, AT);
		at.visit("value", "INVOKE");
		at.visit("target", call);
		at.visitEnd();
		ats.visitEnd();
		ann.visitEnd();
		mv.visitCode();
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 2);
		mv.visitEnd();
	}

	/** GROUP-1: Mixin stops the game when fewer of a group's injectors apply than its minimum, as owo-lib's did. */
	@Test
	void aGroupWhoseInjectorsCantReachItsMinimumFails() throws Exception {
		Map<String, byte[]> mod = Map.of("com/example/mixin/Grouped", mixin("com/example/mixin/Grouped", false, cw -> {
			for (String handler : List.of("viaFabric", "viaQuilt")) {
				MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, handler, "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null);
				AnnotationVisitor group = mv.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Group;", false);
				group.visit("name", "hooks");
				group.visit("min", 1);
				group.visit("max", 1);
				group.visitEnd();
				AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
				AnnotationVisitor methods = ann.visitArray("method");
				methods.visit(null, "tick");
				methods.visitEnd();
				AnnotationVisitor ats = ann.visitArray("at");
				AnnotationVisitor at = ats.visitAnnotation(null, AT);
				at.visit("value", "INVOKE");
				at.visit("target", "Lcom/example/" + handler + "/Hooks;start()V");
				at.visitEnd();
				ats.visitEnd();
				ann.visitEnd();
				mv.visitEnd();
			}
		}));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Grouped"));
		ClassIndex neoForge = new ClassIndex(Map.of(TARGET, callingHelper("(I)V"))::get);

		List<PreflightFinding> findings = preflight(neoForge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		List<PreflightFinding> group = findings.stream().filter(f -> f.message().startsWith("@Group hooks needs 1 of its 2 injectors to apply, and none can")).toList();
		assertEquals(2, group.size(), findings.toString());
		assertTrue(group.stream().allMatch(f -> f.severity() == Severity.LEFT_OUT), "the mixin is self-contained: the group is left out " + group);
		assertEquals(Set.of("viaFabric(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",
				"viaQuilt(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V"), group.stream().map(PreflightFinding::handler).collect(java.util.stream.Collectors.toSet()));
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
		assertEquals(1, CallRepairs.insertedArgument("(I)V", "(ILjava/lang/String;)V"));
		assertEquals(0, CallRepairs.insertedArgument("(I)V", "(Ljava/lang/String;I)V"));
		assertEquals(1, CallRepairs.insertedArgument("(ILjava/lang/String;)Z", "(IJLjava/lang/String;)Z"));
		assertEquals(-1, CallRepairs.insertedArgument("(I)V", "(II)V"), "either int may be the added one");
		assertEquals(-1, CallRepairs.insertedArgument("(I)V", "(IJ)Z"), "another result");
		assertEquals(-1, CallRepairs.insertedArgument("(I)V", "(IJJ)V"));
		assertEquals(-1, CallRepairs.insertedArgument("(I)V", "(J)V"));
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
			AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
			at.visit("value", "HEAD");
			at.visitEnd();
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

	/**
	 * Debugify's container screen mixin on 1.21.1: its capture fails soft, but its config requires each injection, so
	 * where NeoForge's patch added a local Mixin skipped it and then stopped the game for the injection it missed.
	 * It is no longer required; one that isn't required already is left as it is.
	 */
	@Test
	void aSoftLocalCaptureThatIsRequiredIsNoLongerRequired() throws Exception {
		ClassIndex game = new ClassIndex(Map.of(TARGET, targetClass())::get);
		java.util.function.Function<Integer, byte[]> capturing = require -> mixin("com/example/mixin/Capturing", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "h", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;I)V", null, null);
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "tick");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
			at.visit("value", "HEAD");
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnum("locals", "Lorg/spongepowered/asm/mixin/injection/callback/LocalCapture;", "CAPTURE_FAILSOFT");
			if (require != null) ann.visit("require", require);
			ann.visitEnd();
			mv.visitEnd();
		});
		String requiring = "{\"required\":true,\"package\":\"com.example.mixin\",\"mixins\":[\"Capturing\"],\"injectors\":{\"defaultRequire\":1}}";
		for (String config : List.of(requiring, config(true, "Capturing"))) {
			for (Integer require : java.util.Arrays.asList(null, 1, 0)) {
				boolean required = require == null ? config.equals(requiring) : require > 0;
				List<PreflightFinding> notes = new ArrayList<>();
				byte[] repaired = preflight(game, Map.of("com/example/mixin/Capturing", capturing.apply(require))::get, ClassBytesSource.EMPTY, false)
						.repairs("test.mixins.json", MixinConfigFile.parse(config), Refmap.EMPTY, Set.of(), notes).get("com/example/mixin/Capturing.class");
				String what = "require " + require + " in " + config;
				if (!required) {
					assertNull(repaired, what);
					assertTrue(notes.isEmpty(), what + ": " + notes);
					continue;
				}
				assertNotNull(repaired, what + ": " + notes);
				ClassNode node = new ClassNode();
				new ClassReader(repaired).accept(node, 0);
				AnnotationNode inject = node.methods.stream().filter(m -> m.name.equals("h")).findFirst().orElseThrow().invisibleAnnotations.getFirst();
				assertEquals("CAPTURE_FAILSOFT", ((String[]) Annotations.value(inject, "locals"))[1], what);
				assertEquals(0, Annotations.value(inject, "require"), what);
				assertEquals(Severity.INFO, notes.getFirst().severity(), what + ": " + notes);
			}
		}
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
	void anInstanceofConstantsHandlerTakesTheObjectAndTheClass() throws Exception {
		// PRE-29: Lithium's collision context hooks modify vanilla's instanceof LivingEntity checks in EntityCollisionContext's
		// constructor, with handlers taking the object and the class (Mixin's RedirectInjector.injectAtInstanceOf).
		ClassWriter target = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		target.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor init = target.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/Object;)V", null, null);
		init.visitCode();
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		// An instanceof (ordinal 0), a cast (1), an instanceof again (2), a class literal (3).
		for (int op : new int[] {Opcodes.INSTANCEOF, Opcodes.CHECKCAST, Opcodes.INSTANCEOF}) {
			init.visitVarInsn(Opcodes.ALOAD, 1);
			init.visitTypeInsn(op, "java/lang/String");
			init.visitInsn(Opcodes.POP);
		}
		init.visitLdcInsn(Type.getType(String.class));
		init.visitInsn(Opcodes.POP);
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(0, 0);
		init.visitEnd();
		target.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(TARGET, target.toByteArray())::get);
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Constants"));
		for (String fits : List.of("(Ljava/lang/Object;Ljava/lang/Class;)Z/0", "(Ljava/lang/Object;Ljava/lang/Class;)Z/2",
				"(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/Object;)Z/0", "(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Class;/0",
				"(Ljava/lang/Class;)Ljava/lang/Class;/3")) {
			List<PreflightFinding> findings = instanceofCheck(game, config, fits, true);
			assertTrue(findings.isEmpty(), fits + ": " + findings);
		}
		for (String refused : List.of("(Ljava/lang/Object;Ljava/lang/Class;)I/0", "(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;)Z/0")) {
			List<PreflightFinding> findings = instanceofCheck(game, config, refused, true);
			assertTrue(findings.stream().anyMatch(f -> f.message().contains("does not match the parameters of <init>(Ljava/lang/Object;)V")), refused + ": " + findings);
		}
		List<PreflightFinding> notAClass = instanceofCheck(game, config, "(Ljava/lang/Object;Ljava/lang/Class;)Z/0", false);
		assertTrue(notAClass.stream().anyMatch(f -> f.message().contains("does not match the parameters")), notAClass.toString());
		// The casts and the class literal count among the class's constants, as Mixin counts them: there are four.
		List<PreflightFinding> beyond = instanceofCheck(game, config, "(Ljava/lang/Object;Ljava/lang/Class;)Z/4", true);
		assertTrue(beyond.stream().anyMatch(f -> f.message().contains("expects the constant java.lang.String.class, which does not occur")), beyond.toString());
		// Mixin refuses a cast, an instanceof's handler at a class literal and a class literal's at an instanceof, and without
		// an ordinal looks at each: the mixin is left out, saying which.
		Map<String, String> mismatched = Map.of(
				"(Ljava/lang/Object;Ljava/lang/Class;)Z/1", "(ordinal 1) of <init>(Ljava/lang/Object;)V, a cast there",
				"(Ljava/lang/Object;Ljava/lang/Class;)Z/-1", "(ordinal 1) of <init>(Ljava/lang/Object;)V, a cast there",
				"(Ljava/lang/Object;Ljava/lang/Class;)Z/3", "(ordinal 3) of <init>(Ljava/lang/Object;)V, a class literal there",
				"(Ljava/lang/Class;)Ljava/lang/Class;/0", "(ordinal 0) of <init>(Ljava/lang/Object;)V, an instanceof there");
		mismatched.forEach((shape, why) -> {
			List<PreflightFinding> findings = instanceofCheck(game, config, shape, true);
			assertTrue(findings.stream().anyMatch(f -> f.severity() == Severity.LEFT_OUT && f.handler() == null
					&& f.message().contains("handler h" + shape.substring(0, shape.lastIndexOf('/')) + " modifies the constant java.lang.String.class " + why)),
					shape + ": " + findings);
		});
	}

	/**
	 * Pre-flight's findings for a mixin {@code Constants} whose static {@code @ModifyConstant} handler, of the descriptor
	 * {@code shape} names before its slash, modifies {@link #TARGET}'s constructor's {@code String.class} (or 0, without
	 * {@code classValue}) of the ordinal after it.
	 */
	private static List<PreflightFinding> instanceofCheck(ClassIndex game, MixinConfigFile config, String shape, boolean classValue) {
		String desc = shape.substring(0, shape.lastIndexOf('/'));
		int ordinal = Integer.parseInt(shape.substring(shape.lastIndexOf('/') + 1));
		byte[] mixin = mixin("com/example/mixin/Constants", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "h", desc, null, null);
			AnnotationVisitor ann = mv.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/ModifyConstant;", false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "<init>(Ljava/lang/Object;)V");
			methods.visitEnd();
			AnnotationVisitor constants = ann.visitArray("constant");
			AnnotationVisitor constant = constants.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/Constant;");
			if (classValue) constant.visit("classValue", Type.getType(String.class));
			else constant.visit("intValue", 0);
			if (ordinal >= 0) constant.visit("ordinal", ordinal);
			constant.visitEnd();
			constants.visitEnd();
			ann.visitEnd();
			mv.visitEnd();
		});
		return preflight(game, Map.of("com/example/mixin/Constants", mixin)::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY,
				Set.of());
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
	void aReturnValueModifierIsPinnedToTheOverloadItsHandlerTakesTheParametersOf() throws Exception {
		// LambDynamicLights' hook into LevelRenderer.getLightColor taking the block state, whose overload without it comes
		// first: Mixin refuses the handler there, and LevelRenderer fails for every mod (CORPUS-45).
		ClassWriter target = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		target.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		for (String desc : List.of("(Ljava/lang/Object;Ljava/lang/String;)I", "(Ljava/lang/Object;Ljava/lang/Integer;Ljava/lang/String;)I")) {
			MethodVisitor light = target.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "light", desc, null, null);
			light.visitCode();
			light.visitInsn(Opcodes.ICONST_0);
			light.visitInsn(Opcodes.IRETURN);
			light.visitMaxs(0, 0);
			light.visitEnd();
		}
		target.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(TARGET, target.toByteArray())::get);
		java.util.function.Function<String, byte[]> modifying = handlerDesc -> mixin("com/example/mixin/Light", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "h", handlerDesc, null, null);
			AnnotationVisitor ann = mv.visitAnnotation("Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;", false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "light");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
			at.visit("value", "RETURN");
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnd();
			mv.visitEnd();
		});
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Light"));
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Light",
				modifying.apply("(ILjava/lang/Object;Ljava/lang/Integer;Ljava/lang/String;)I")));
		List<PreflightFinding> before = preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(before.size() == 1 && before.getFirst().message().contains("does not match the parameters of light(Ljava/lang/Object;Ljava/lang/String;)I"),
				"Mixin picks the overload without the Integer: " + before);
		List<PreflightFinding> notes = new ArrayList<>();
		mod.put("com/example/mixin/Light", preflight(game, mod::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes).get("com/example/mixin/Light.class"));
		assertTrue(notes.size() == 1 && notes.getFirst().message().contains("targets light(Ljava/lang/Object;Ljava/lang/Integer;Ljava/lang/String;)I")
				&& notes.getFirst().message().contains("pinned"), notes.toString());
		List<PreflightFinding> after = preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.isEmpty(), after.toString());
		// Taking the first parameter only, the handler suits both: the first stays Mixin's pick. Taking the value alone too.
		for (String desc : List.of("(ILjava/lang/Object;)I", "(I)I")) {
			Map<String, byte[]> either = Map.of("com/example/mixin/Light", modifying.apply(desc));
			List<PreflightFinding> unpinned = new ArrayList<>();
			preflight(game, either::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), unpinned);
			assertTrue(unpinned.isEmpty(), desc + ": " + unpinned);
			assertTrue(preflight(game, either::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty(), desc);
		}
	}

	@Test
	void anInjectIsPinnedToTheFirstOverloadItTakesTheParametersOfSoAnOverwriteDoesntMoveIt() throws Exception {
		// Sodium's Fabric build's @Inject on ModelBlockRenderer.renderModel, which NeoForge 1.21.11 gave an overload taking
		// more: the class declares vanilla's first, but Indigo's @Overwrite of it makes Mixin put it last, so Mixin picked
		// the overload and refused the handler ("Invalid descriptor", OVERLOAD-2).
		String vanilla = "(Ljava/lang/Object;Ljava/lang/String;)V";
		String neoForge = "(Ljava/lang/Object;Ljava/lang/Integer;Ljava/lang/String;)V";
		java.util.function.Function<List<String>, byte[]> declaring = descs -> {
			ClassWriter target = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			target.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
			for (String desc : descs) {
				MethodVisitor render = target.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "renderModel", desc, null, null);
				render.visitCode();
				render.visitInsn(Opcodes.RETURN);
				render.visitMaxs(0, 0);
				render.visitEnd();
			}
			target.visitEnd();
			return target.toByteArray();
		};
		java.util.function.Function<String, byte[]> injecting = handlerDesc -> mixin("com/example/mixin/Render", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "h", handlerDesc, null, null);
			AnnotationVisitor ann = mv.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "renderModel");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
			at.visit("value", "HEAD");
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnd();
			mv.visitEnd();
		});
		ClassIndex game = new ClassIndex(Map.of(TARGET, declaring.apply(List.of(vanilla, neoForge)))::get);
		// As an overwrite of vanilla's method leaves the class.
		ClassIndex overwritten = new ClassIndex(Map.of(TARGET, declaring.apply(List.of(neoForge, vanilla)))::get);
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Render"));
		String handler = "(Ljava/lang/Object;Ljava/lang/String;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Render", injecting.apply(handler)));
		assertTrue(preflight(game, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());
		assertFalse(preflight(overwritten, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty(),
				"Mixin picks the overload once vanilla's method is last");
		List<PreflightFinding> notes = new ArrayList<>();
		mod.put("com/example/mixin/Render", preflight(game, mod::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes).get("com/example/mixin/Render.class"));
		assertTrue(notes.size() == 1 && notes.getFirst().severity() == Severity.INFO
				&& notes.getFirst().message().contains("@Inject h targets renderModel" + vanilla + ", one of several methods of that name")
				&& notes.getFirst().message().contains("pinned"), notes.toString());
		for (ClassIndex order : List.of(game, overwritten)) {
			List<PreflightFinding> after = preflight(order, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
			assertTrue(after.isEmpty(), after.toString());
		}
		// Taking the callback info alone, any overload suits it: nothing is pinned. Nor where the method has no overload.
		Map<String, byte[]> callbackOnly = Map.of("com/example/mixin/Render", injecting.apply("(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V"));
		List<PreflightFinding> unpinned = new ArrayList<>();
		preflight(game, callbackOnly::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), unpinned);
		assertTrue(unpinned.isEmpty(), unpinned.toString());
		ClassIndex single = new ClassIndex(Map.of(TARGET, declaring.apply(List.of(vanilla)))::get);
		Map<String, byte[]> fitting = Map.of("com/example/mixin/Render", injecting.apply(handler));
		preflight(single, fitting::get, ClassBytesSource.EMPTY, false).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), unpinned);
		assertTrue(unpinned.isEmpty(), unpinned.toString());
	}

	@Test
	void aConstantModifierTakingParametersNoTargetHasIsLeftOutWithItsClass() throws Exception {
		ClassIndex game = new ClassIndex(Map.of(TARGET, overloadedTarget())::get);
		Map<String, byte[]> mod = Map.of("com/example/mixin/Constants", mixin("com/example/mixin/Constants", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "h", "(ILjava/lang/String;)I", null, null);
			AnnotationVisitor ann = mv.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/ModifyConstant;", false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "tick(I)V");
			methods.visitEnd();
			AnnotationVisitor constants = ann.visitArray("constant");
			AnnotationVisitor constant = constants.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/Constant;");
			constant.visit("intValue", 0);
			constant.visitEnd();
			constants.visitEnd();
			ann.visitEnd();
			mv.visitEnd();
		}));
		List<PreflightFinding> findings = preflight(game, mod::get, ClassBytesSource.EMPTY, false)
				.check("test.mixins.json", MixinConfigFile.parse(config(true, "Constants")), Refmap.EMPTY, Set.of());
		assertTrue(findings.size() == 1 && findings.getFirst().message().contains("does not match the parameters of tick(I)V"), findings.toString());
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

	@Test
	void aModifyVariableWhoseHandlerTakesOtherParametersThanTheTargetsFirstFails() throws Exception {
		// Farmer's Delight's hook into CropBlock.getGrowthSpeed(Block, BlockGetter, BlockPos) takes the method's
		// parameters after the variable; NeoForge's build takes a block state first, and Mixin refuses the handler.
		// Balm's into actuallyHurt takes the first two of its three, which Mixin accepts.
		ClassWriter target = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		target.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor render = target.visitMethod(Opcodes.ACC_PUBLIC, "render", "(Ljava/lang/String;Ljava/lang/Integer;)V", null, null);
		render.visitCode();
		render.visitInsn(Opcodes.RETURN);
		render.visitMaxs(0, 0);
		render.visitEnd();
		target.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(TARGET, target.toByteArray())::get);
		java.util.function.Function<String, byte[]> modifying = desc -> mixin("com/example/mixin/Modify", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "h", desc, null, null);
			AnnotationVisitor ann = mv.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/ModifyVariable;", false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "render");
			methods.visitEnd();
			AnnotationVisitor at = ann.visitAnnotation("at", "Lorg/spongepowered/asm/mixin/injection/At;");
			at.visit("value", "HEAD");
			at.visitEnd();
			ann.visitEnd();
			mv.visitEnd();
		});
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Modify"));
		for (String desc : List.of("(Ljava/lang/String;Ljava/lang/Integer;)Ljava/lang/String;",
				"(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/Integer;)Ljava/lang/String;")) {
			List<PreflightFinding> wrong = preflight(game, Map.of("com/example/mixin/Modify", modifying.apply(desc))::get,
					ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of());
			assertEquals(1, wrong.size(), desc + ": " + wrong);
			assertEquals(Severity.LEFT_OUT, wrong.getFirst().severity());
			assertTrue(wrong.getFirst().message().contains("does not match the parameters of render(Ljava/lang/String;Ljava/lang/Integer;)V"), wrong.getFirst().message());
		}
		for (String desc : List.of("(Ljava/lang/String;)Ljava/lang/String;", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
				"(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Integer;)Ljava/lang/String;")) {
			List<PreflightFinding> right = preflight(game, Map.of("com/example/mixin/Modify", modifying.apply(desc))::get, ClassBytesSource.EMPTY, false)
					.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
			assertTrue(right.isEmpty(), desc + ": " + right);
		}
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

	@Test
	void requireCountsInjectionsInEveryTargetMethod() throws Exception {
		// REQ-1: FrozenLib wraps a call in two methods with require = 2; NeoForge moved it out of one, so Mixin makes one
		// injection and stops the game, though each point is found somewhere.
		for (int require : new int[] {1, 2}) {
			byte[] both = mixin("com/example/mixin/Both", false, cw -> {
				MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "h", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null);
				AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
				AnnotationVisitor methods = ann.visitArray("method");
				methods.visit(null, "tick");
				methods.visit(null, "helper");
				methods.visitEnd();
				AnnotationVisitor ats = ann.visitArray("at");
				AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
				at.visit("value", "INVOKE");
				at.visit("target", "Lnet/minecraft/world/Thing;helper(I)V");
				at.visitEnd();
				ats.visitEnd();
				ann.visit("require", require);
				ann.visitEnd();
				mv.visitEnd();
			});
			List<PreflightFinding> findings = run(config(true, "Both"), null, Map.of("com/example/mixin/Both", both), false);
			if (require == 1) {
				assertTrue(findings.isEmpty(), findings.toString());
			} else {
				assertEquals(1, findings.size(), findings.toString());
				assertTrue(findings.getFirst().message().contains("applies 1 time(s) where it requires 2: its point does not occur in helper"),
						findings.getFirst().message());
			}
		}
	}

	@Test
	void allowCapsInjectionsAsMixinDoes() throws Exception {
		// ALLOW-1: Fabric API's furnace hook allows one injection; Forge 47's patch makes the call it hooks twice, and
		// Mixin stops the game ("2 succeeded of 1 allowed"). Here a hook at the head of two methods.
		for (int allow : new int[] {1, 2}) {
			byte[] capped = mixin("com/example/mixin/Capped", false, cw -> {
				MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "h", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null);
				AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
				AnnotationVisitor methods = ann.visitArray("method");
				methods.visit(null, "tick");
				methods.visit(null, "helper");
				methods.visitEnd();
				AnnotationVisitor ats = ann.visitArray("at");
				AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
				at.visit("value", "HEAD");
				at.visitEnd();
				ats.visitEnd();
				ann.visit("allow", allow);
				ann.visitEnd();
				mv.visitEnd();
			});
			List<PreflightFinding> findings = run(config(true, "Capped"), null, Map.of("com/example/mixin/Capped", capped), false);
			if (allow == 2) {
				assertTrue(findings.isEmpty(), findings.toString());
			} else {
				assertEquals(1, findings.size(), findings.toString());
				assertNotEquals(PreflightFinding.Severity.WARN, findings.getFirst().severity(), findings.toString());
				assertTrue(findings.getFirst().message().contains("applies 2 time(s) where it allows 1: its point occurs that often in helper, tick"),
						findings.getFirst().message());
			}
		}

		// Mixin keys the targets by method: LambDynamicLights names its hook twice, by its Intermediary and its Mojang
		// name, which select one method once remapped, and Mixin injects once.
		byte[] twice = mixin("com/example/mixin/Twice", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "h", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null);
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "tick");
			methods.visit(null, "tick()V");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
			at.visit("value", "HEAD");
			at.visitEnd();
			ats.visitEnd();
			ann.visit("allow", 1);
			ann.visit("require", 1);
			ann.visitEnd();
			mv.visitEnd();
		});
		List<PreflightFinding> once = run(config(true, "Twice"), null, Map.of("com/example/mixin/Twice", twice), false);
		assertTrue(once.isEmpty(), once.toString());
	}

	@Test
	void anInterfaceMixinsInjectorsAreLeftOutWhereMixinDoesntApplyThem() throws Exception {
		// INTERFACE-1: Fabric API's recipe remainder, data provider and oxidation hooks are interface mixins of injectors
		// alone; Forge 47's Mixin 0.8.5 dropped them whole, logging an error. Their injectors are left out, the rest
		// applies; Mixin from 0.8.6 applies them.
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE, "com/example/mixin/Hooks", null, "java/lang/Object", null);
		AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor values = m.visitArray("value");
		values.visit(null, Type.getObjectType(TARGET));
		values.visitEnd();
		m.visitEnd();
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "h", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null);
		AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
		AnnotationVisitor methods = ann.visitArray("method");
		methods.visit(null, "tick");
		methods.visitEnd();
		AnnotationVisitor ats = ann.visitArray("at");
		AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
		at.visit("value", "HEAD");
		at.visitEnd();
		ats.visitEnd();
		ann.visitEnd();
		mv.visitEnd();
		cw.visitEnd();
		Map<String, byte[]> mod = Map.of("com/example/mixin/Hooks", cw.toByteArray());
		ClassIndex game = new ClassIndex(Map.of(TARGET, targetClass())::get);

		for (boolean interfaceInjectors : new boolean[] {true, false}) {
			List<PreflightFinding> findings = new MixinPreflight(game, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true,
					ClassBytesSource.EMPTY, "Forge", interfaceInjectors).check("test.mixins.json", MixinConfigFile.parse(config(true, "Hooks")), Refmap.EMPTY, Set.of());
			if (interfaceInjectors) {
				assertTrue(findings.isEmpty(), findings.toString());
				continue;
			}
			assertEquals(1, findings.size(), findings.toString());
			PreflightFinding f = findings.getFirst();
			assertEquals(PreflightFinding.Severity.LEFT_OUT, f.severity(), "the mixin is self-contained: " + f);
			assertEquals("h(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", f.handler(), "only the handler: " + f);
			assertTrue(f.message().contains("is an injector in an interface mixin, which Forge's Mixin doesn't apply"), f.message());
		}
	}

	/** An {@code @Inject} handler taking {@code params} and the callback info, into {@code method} at {@code point} (and {@code target}). */
	private static void constructorHook(ClassWriter cw, String handler, boolean isStatic, String params, String method, String point, String target) {
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | (isStatic ? Opcodes.ACC_STATIC : 0), handler,
				"(" + params + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null);
		AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
		AnnotationVisitor methods = ann.visitArray("method");
		methods.visit(null, method);
		methods.visitEnd();
		AnnotationVisitor ats = ann.visitArray("at");
		AnnotationVisitor at = ats.visitAnnotation(null, AT);
		at.visit("value", point);
		if (target != null) at.visit("target", target);
		at.visitEnd();
		ats.visitEnd();
		ann.visitEnd();
		mv.visitCode();
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 3);
		mv.visitEnd();
	}

	@Test
	void constructorHooksAtTheirHeadsOrMovedIntoAnotherConstructorAreMarkedUnsafeWhereMixinWantsThat() throws Exception {
		// CTOR-7: SpongePowered's 0.8.7 (Forge 61 to 66) checks constructor points as Fabric's Mixin does, but its points
		// aren't unsafe by default. CTOR_HEAD (FrozenLib's shelf hook, ImmediatelyFast's font texture hook) finds an
		// instruction after super(), and 0.8.7 refuses to parse it without unsafe; HEAD with a static handler (Wover's) is
		// taken before super(). A hook the overload repair moves into the constructor the platform moved the code into
		// (FrozenLib's into ServerAdvancementManager) is marked there. A point that finds nothing in the constructor (owo's
		// after Fabric Loader's client start hook) is reported missing, as Mixin judges only what a point finds.
		String owner = "net/minecraft/world/Built";
		ClassWriter target = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		target.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
		MethodVisitor kept = target.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/Object;)V", null, null);
		kept.visitVarInsn(Opcodes.ALOAD, 0);
		kept.visitVarInsn(Opcodes.ALOAD, 1);
		kept.visitLdcInsn("empty");
		kept.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", "(Ljava/lang/Object;Ljava/lang/String;)V", false);
		kept.visitInsn(Opcodes.RETURN);
		kept.visitMaxs(0, 0);
		kept.visitEnd();
		MethodVisitor init = target.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/Object;Ljava/lang/String;)V", null, null);
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, "setup", "()V", false);
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(0, 0);
		init.visitEnd();
		MethodVisitor setup = target.visitMethod(Opcodes.ACC_PUBLIC, "setup", "()V", null, null);
		setup.visitInsn(Opcodes.RETURN);
		setup.visitMaxs(0, 1);
		setup.visitEnd();
		target.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(owner, target.toByteArray())::get);
		String full = "<init>(Ljava/lang/Object;Ljava/lang/String;)V";
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "com/example/mixin/Hooks", null, "java/lang/Object", null);
		AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor values = m.visitArray("value");
		values.visit(null, Type.getObjectType(owner));
		values.visitEnd();
		m.visitEnd();
		constructorHook(cw, "atCtorHead", false, "", full, "CTOR_HEAD", null);
		constructorHook(cw, "staticHead", true, "", full, "HEAD", null);
		constructorHook(cw, "instanceHead", false, "", full, "HEAD", null);
		constructorHook(cw, "afterLoaderHook", false, "", full, "INVOKE", "Lnet/fabricmc/loader/impl/game/minecraft/Hooks;startClient()V");
		constructorHook(cw, "atSetup", false, "Ljava/lang/Object;", "<init>(Ljava/lang/Object;)V", "INVOKE", "L" + owner + ";setup()V");
		cw.visitEnd();
		MixinConfigFile config = MixinConfigFile.parse(config(false, "Hooks"));

		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Hooks", cw.toByteArray()));
		MixinPreflight preflight = new MixinPreflight(game, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, ClassBytesSource.EMPTY,
				"Forge", true, MixinPreflight.ConstructorInjects.UNSAFE_POINTS);
		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		repaired.forEach((path, bytes) -> mod.put(path.substring(0, path.length() - ".class".length()), bytes));
		ClassNode node = new ClassNode();
		new ClassReader(mod.get("com/example/mixin/Hooks")).accept(node, 0);
		Function<String, Object> unsafe = handler -> Annotations.value(Annotations.list(Annotations.value(Annotations.find(node.methods.stream()
				.filter(x -> x.name.equals(handler)).findFirst().orElseThrow(), INJECT), "at")).getFirst(), "unsafe");
		assertEquals(true, unsafe.apply("atCtorHead"), notes.toString());
		assertEquals(true, unsafe.apply("staticHead"), notes.toString());
		assertNull(unsafe.apply("instanceHead"), notes.toString());
		assertEquals(true, unsafe.apply("atSetup$fabricated"), notes.toString());
		assertTrue(notes.stream().anyMatch(n -> n.message().contains("@Inject atSetup injects into the constructor of net.minecraft.world.Built"
				+ " at @At(\"INVOKE\", target=\"Lnet/minecraft/world/Built;setup()V\") after its super() call")), notes.toString());
		List<PreflightFinding> findings = preflight.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		Function<String, List<String>> about = handler -> findings.stream().filter(f -> f.severity() != Severity.INFO && f.message().contains(" " + handler + " ")
				|| f.handler() != null && f.handler().startsWith(handler + "(")).map(PreflightFinding::message).toList();
		assertEquals(List.of(), about.apply("atCtorHead"), findings.toString());
		assertEquals(List.of(), about.apply("staticHead"), findings.toString());
		assertEquals(List.of(), about.apply("atSetup$fabricated"), findings.toString());
		assertTrue(about.apply("instanceHead").stream().anyMatch(f -> f.contains("at @At(\"HEAD\"), which Forge's Mixin takes only at a return or a point marked unsafe")),
				findings.toString());
		assertTrue(about.apply("afterLoaderHook").stream().anyMatch(f -> f.contains("which does not occur in that method")), findings.toString());
		assertTrue(findings.stream().noneMatch(f -> f.message().contains("@Inject afterLoaderHook injects into the constructor")), findings.toString());

		// 0.8.5 (Forge 47) has neither CTOR_HEAD nor unsafe: those stay as they are, reported.
		Map<String, byte[]> legacyMod = new HashMap<>(Map.of("com/example/mixin/Hooks", cw.toByteArray()));
		MixinPreflight legacy = new MixinPreflight(game, InstalledClasses.NONE, legacyMod::get, ClassBytesSource.EMPTY, false, true, ClassBytesSource.EMPTY,
				"Forge", true, MixinPreflight.ConstructorInjects.RETURNS_ONLY);
		legacy.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).forEach((path, bytes) ->
				legacyMod.put(path.substring(0, path.length() - ".class".length()), bytes));
		List<PreflightFinding> legacyFindings = legacy.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		for (String handler : List.of("atCtorHead", "staticHead")) {
			assertTrue(legacyFindings.stream().anyMatch(f -> f.message().contains("@Inject " + handler + " injects into the constructor")), legacyFindings.toString());
		}
	}

	@Test
	void anInjectIntoAConstructorAfterItsSuperCallIsMarkedUnsafeWhereMixinWantsThat() throws Exception {
		// CTOR-2: fabric-command-api-v2 hooks a call in Commands' constructor, after super(); Fabric's Mixin takes that,
		// SpongePowered's 0.8.7 (Forge 61 to 66) only at a return or a point marked unsafe, and stopped the server. 0.8.5
		// (Forge 47) has no unsafe points: Fabricated's point stands for it there (CORPUS-103).
		String owner = "net/minecraft/world/Built";
		ClassWriter target = new ClassWriter(0);
		target.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
		MethodVisitor init = target.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, "setup", "()V", false);
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(1, 1);
		init.visitEnd();
		MethodVisitor setup = target.visitMethod(Opcodes.ACC_PUBLIC, "setup", "()V", null, null);
		setup.visitInsn(Opcodes.RETURN);
		setup.visitMaxs(0, 1);
		setup.visitEnd();
		target.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(TARGET, targetClass(), owner, target.toByteArray())::get);

		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "com/example/mixin/Hook", null, "java/lang/Object", null);
		AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor values = m.visitArray("value");
		values.visit(null, Type.getObjectType(owner));
		values.visitEnd();
		m.visitEnd();
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "afterSetup", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null);
		AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
		AnnotationVisitor methods = ann.visitArray("method");
		methods.visit(null, "<init>");
		methods.visitEnd();
		AnnotationVisitor ats = ann.visitArray("at");
		AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
		at.visit("value", "INVOKE");
		at.visit("target", "L" + owner + ";setup()V");
		at.visitEnd();
		ats.visitEnd();
		ann.visitEnd();
		mv.visitEnd();
		cw.visitEnd();
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Hook"));

		for (MixinPreflight.ConstructorInjects rule : MixinPreflight.ConstructorInjects.values()) {
			Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Hook", cw.toByteArray()));
			MixinPreflight preflight = new MixinPreflight(game, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true,
					ClassBytesSource.EMPTY, "Forge", true, rule);
			List<PreflightFinding> notes = new ArrayList<>();
			Map<String, byte[]> repaired = preflight.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
			repaired.forEach((path, bytes) -> mod.put(path.substring(0, path.length() - ".class".length()), bytes));
			List<PreflightFinding> findings = preflight.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
			switch (rule) {
				case AFTER_DELEGATE -> {
					assertTrue(repaired.isEmpty() && findings.isEmpty(), rule + ": " + notes + findings);
				}
				case UNSAFE_POINTS -> {
					assertTrue(findings.isEmpty(), rule + ": " + findings);
					ClassNode node = new ClassNode();
					new ClassReader(repaired.get("com/example/mixin/Hook.class")).accept(node, 0);
					AnnotationNode point = Annotations.list(Annotations.value(Annotations.find(node.methods.getFirst(), INJECT), "at")).getFirst();
					assertEquals(true, Annotations.value(point, "unsafe"), "marked unsafe");
					assertTrue(notes.stream().anyMatch(n -> n.message().contains("after its super() call")), notes.toString());
				}
				case RETURNS_ONLY -> {
					assertTrue(findings.isEmpty(), rule + ": " + findings);
					ClassNode node = new ClassNode();
					new ClassReader(repaired.get("com/example/mixin/Hook.class")).accept(node, 0);
					AnnotationNode point = Annotations.list(Annotations.value(Annotations.find(node.methods.getFirst(), INJECT), "at")).getFirst();
					assertEquals(ConstructorPoint.AT, Annotations.value(point, "value"), "Fabricated's point stands for it");
					assertEquals(List.of(ConstructorPoint.POINT + "=INVOKE", ConstructorPoint.SHIFT + "=0"), Annotations.value(point, "args"));
					assertTrue(notes.stream().anyMatch(n -> n.message().contains("Fabricated's injection point stands for it")), notes.toString());
				}
			}
		}
	}

	@Test
	void anInjectJustAfterAConstructionInAConstructorIsTakenWhereMixinTakesOnlyReturns() throws Exception {
		// CORPUS-103: Dripstone Fluid Lib registers its particles' providers in Minecraft's constructor, at the call that
		// constructs the particle engine moved by two (shift = BY), just after the engine is stored. SpongePowered's 0.8.5
		// (Forge 47) takes an @Inject into a constructor only at a return, so pre-flight left the hook out, and the mod's
		// reload hook then found no sprite set for its particles. There the point becomes Fabricated's, standing for it and
		// its shift; on 0.8.7 it is marked unsafe. A point at the delegate constructor call stays left out on both: Mixin
		// takes an instance handler only after it.
		String owner = "net/minecraft/world/Built";
		String engine = "net/minecraft/world/Engine";
		ClassWriter target = new ClassWriter(0);
		target.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
		target.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "engine", "L" + engine + ";", null, null).visitEnd();
		MethodVisitor init = target.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/String;)V", null, null);
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitTypeInsn(Opcodes.NEW, engine);
		init.visitInsn(Opcodes.DUP);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, engine, "<init>", "()V", false);
		init.visitFieldInsn(Opcodes.PUTFIELD, owner, "engine", "L" + engine + ";");
		Label next = new Label();
		init.visitLabel(next);
		init.visitLineNumber(12, next);
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(3, 2);
		init.visitEnd();
		target.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(TARGET, targetClass(), owner, target.toByteArray())::get);

		String hook = "com/example/mixin/Hook";
		String handlerDesc = "(Ljava/lang/String;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, hook, null, "java/lang/Object", null);
		AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor values = m.visitArray("value");
		values.visit(null, Type.getObjectType(owner));
		values.visitEnd();
		m.visitEnd();
		for (String handler : List.of("afterEngine", "atSuper")) {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, handler, handlerDesc, null, null);
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "<init>");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", "INVOKE");
			if (handler.equals("afterEngine")) {
				at.visit("target", "L" + engine + ";<init>()V");
				at.visitEnum("shift", "Lorg/spongepowered/asm/mixin/injection/At$Shift;", "BY");
				at.visit("by", 2);
			} else {
				at.visit("target", "Ljava/lang/Object;<init>()V");
			}
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnd();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 3);
			mv.visitEnd();
		}
		cw.visitEnd();
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Hook"));

		for (MixinPreflight.ConstructorInjects rule : List.of(MixinPreflight.ConstructorInjects.UNSAFE_POINTS, MixinPreflight.ConstructorInjects.RETURNS_ONLY)) {
			Map<String, byte[]> mod = new HashMap<>(Map.of(hook, cw.toByteArray()));
			MixinPreflight preflight = new MixinPreflight(game, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true,
					ClassBytesSource.EMPTY, "Forge", true, rule);
			List<PreflightFinding> notes = new ArrayList<>();
			Map<String, byte[]> repaired = preflight.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
			repaired.forEach((path, bytes) -> mod.put(path.substring(0, path.length() - ".class".length()), bytes));
			List<PreflightFinding> findings = preflight.check("test.mixins.json", config, Refmap.EMPTY, Set.of());

			assertEquals(1, findings.size(), rule + ": " + findings);
			assertTrue(findings.getFirst().handler().startsWith("atSuper"), rule + ": the point at super() is left out: " + findings);
			assertTrue(findings.getFirst().message().contains("injects into the constructor of net.minecraft.world.Built at @At(\"INVOKE\")"),
					findings.getFirst().message());
			ClassNode node = new ClassNode();
			new ClassReader(mod.get(hook)).accept(node, 0);
			MethodNode afterEngine = node.methods.stream().filter(mn -> mn.name.equals("afterEngine")).findFirst().orElseThrow();
			AnnotationNode point = Annotations.list(Annotations.value(Annotations.find(afterEngine, INJECT), "at")).getFirst();
			assertEquals("L" + engine + ";<init>()V", Annotations.value(point, "target"), rule + ": the target stays");
			if (rule == MixinPreflight.ConstructorInjects.UNSAFE_POINTS) {
				assertEquals("INVOKE", Annotations.value(point, "value"));
				assertEquals(2, Annotations.value(point, "by"), "the shift stays");
				assertEquals(true, Annotations.value(point, "unsafe"), "marked unsafe");
			} else {
				assertEquals(ConstructorPoint.AT, Annotations.value(point, "value"), "Fabricated's point");
				assertNull(Annotations.value(point, "shift"), "the point moves what it finds itself: Mixin's shift would take it only in methods");
				assertNull(Annotations.value(point, "by"));
				assertEquals(List.of(ConstructorPoint.POINT + "=INVOKE", ConstructorPoint.SHIFT + "=2"), Annotations.value(point, "args"));
			}
			assertTrue(notes.stream().anyMatch(n -> n.message().contains("afterEngine injects into the constructor of net.minecraft.world.Built")
					&& n.message().contains("after its super() call")), rule + ": " + notes);
		}
	}

	@Test
	void anInjectIntoAConstructorThePlatformKeepsOnlyToCallAnotherIsPointedThereWithTheFirstsArguments() throws Exception {
		// IRIS-1: Forge 47 keeps ShaderInstance's constructor taking a shader's name as this(provider, new
		// ResourceLocation(name), format) and moves its code into the one taking the location, where Iris's hook at the
		// JSON parse now is; its handler takes the name, which the location can't give back. Where the platform hands
		// constructors' arguments on, the injector moves to the other constructor through a bridge reading them (and on
		// Forge 47's Mixin, Fabricated's point stands for the call there); where it doesn't, pre-flight fails it as before.
		String owner = "net/minecraft/client/renderer/Shader";
		String location = "net/minecraft/resources/Location";
		String named = "(Ljava/lang/String;I)V";
		String located = "(L" + location + ";I)V";
		ClassWriter target = new ClassWriter(0);
		target.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
		MethodVisitor first = target.visitMethod(Opcodes.ACC_PUBLIC, "<init>", named, null, null);
		first.visitVarInsn(Opcodes.ALOAD, 0);
		first.visitTypeInsn(Opcodes.NEW, location);
		first.visitInsn(Opcodes.DUP);
		first.visitVarInsn(Opcodes.ALOAD, 1);
		first.visitMethodInsn(Opcodes.INVOKESPECIAL, location, "<init>", "(Ljava/lang/String;)V", false);
		first.visitVarInsn(Opcodes.ILOAD, 2);
		first.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", located, false);
		first.visitInsn(Opcodes.RETURN);
		first.visitMaxs(5, 3);
		first.visitEnd();
		MethodVisitor other = target.visitMethod(Opcodes.ACC_PUBLIC, "<init>", located, null, null);
		other.visitVarInsn(Opcodes.ALOAD, 0);
		other.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		other.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "parse", "()V", false);
		other.visitInsn(Opcodes.RETURN);
		other.visitMaxs(1, 3);
		other.visitEnd();
		MethodVisitor parse = target.visitMethod(Opcodes.ACC_STATIC, "parse", "()V", null, null);
		parse.visitInsn(Opcodes.RETURN);
		parse.visitMaxs(0, 0);
		parse.visitEnd();
		target.visitEnd();
		ClassWriter locationClass = new ClassWriter(0);
		locationClass.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, location, null, "java/lang/Object", null);
		locationClass.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(TARGET, targetClass(), owner, target.toByteArray(), location, locationClass.toByteArray())::get);

		String hook = "com/example/mixin/Geometry";
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, hook, null, "java/lang/Object", null);
		AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor values = m.visitArray("value");
		values.visit(null, Type.getObjectType(owner));
		values.visitEnd();
		m.visitEnd();
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "setup", "(Ljava/lang/String;ILorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",
				null, null);
		AnnotationVisitor ann = mv.visitAnnotation(INJECT, true);
		AnnotationVisitor methods = ann.visitArray("method");
		methods.visit(null, "<init>" + named);
		methods.visitEnd();
		AnnotationVisitor ats = ann.visitArray("at");
		AnnotationVisitor at = ats.visitAnnotation(null, AT);
		at.visit("value", "INVOKE");
		at.visit("target", "L" + owner + ";parse()V");
		at.visitEnd();
		ats.visitEnd();
		ann.visitEnd();
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 4);
		mv.visitEnd();
		cw.visitEnd();
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Geometry"));

		for (boolean handsOff : List.of(false, true)) {
			for (MixinPreflight.ConstructorInjects rule : List.of(MixinPreflight.ConstructorInjects.AFTER_DELEGATE, MixinPreflight.ConstructorInjects.RETURNS_ONLY)) {
				String run = (handsOff ? "handing arguments on" : "not handing them on") + ", " + rule;
				Map<String, byte[]> mod = new HashMap<>(Map.of(hook, cw.toByteArray()));
				MixinPreflight preflight = new MixinPreflight(game, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, true, true,
						ClassBytesSource.EMPTY, "Forge", true, rule, true, false, true, false, true, handsOff);
				List<PreflightFinding> notes = new ArrayList<>();
				Map<String, byte[]> repaired = preflight.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
				repaired.forEach((path, bytes) -> mod.put(path.substring(0, path.length() - ".class".length()), bytes));
				List<PreflightFinding> findings = preflight.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
				if (!handsOff) {
					assertTrue(repaired.isEmpty(), run + ": " + notes);
					assertEquals(1, findings.size(), run + ": " + findings);
					continue;
				}
				assertTrue(findings.isEmpty(), run + ": " + findings);
				assertTrue(notes.stream().anyMatch(n -> n.message().contains("setup targets <init>" + named + ", which Forge keeps only to call <init>" + located)
						&& n.message().contains("its handler is given the arguments the first was called with")), run + ": " + notes);
				ClassNode node = new ClassNode();
				new ClassReader(mod.get(hook)).accept(node, 0);
				MethodNode setup = node.methods.stream().filter(mn -> mn.name.equals("setup")).findFirst().orElseThrow();
				assertNull(Annotations.find(setup, INJECT), run + ": the handler is a plain method now");
				MethodNode bridge = node.methods.stream().filter(mn -> Annotations.find(mn, INJECT) != null).findFirst().orElseThrow();
				assertEquals("(L" + location + ";ILorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", bridge.desc,
						run + ": the bridge takes the other constructor's parameters");
				AnnotationNode inject = Annotations.find(bridge, INJECT);
				assertEquals(List.of("<init>" + located), Annotations.value(inject, "method"), run);
				FieldNode arguments = node.fields.stream().filter(f -> f.invisibleAnnotations != null
						&& Annotations.find(f.invisibleAnnotations, ConstructorHandoffs.MARKER) != null).findFirst().orElseThrow();
				assertEquals(named, Annotations.value(Annotations.find(arguments.invisibleAnnotations, ConstructorHandoffs.MARKER), "value"),
						run + ": the field names the constructor whose arguments it holds");
				boolean reads = false;
				for (AbstractInsnNode insn : bridge.instructions) reads |= insn instanceof FieldInsnNode get && get.name.equals(arguments.name);
				assertTrue(reads, run + ": the bridge reads them");
				AnnotationNode point = Annotations.list(Annotations.value(inject, "at")).getFirst();
				assertEquals(rule == MixinPreflight.ConstructorInjects.RETURNS_ONLY ? ConstructorPoint.AT : "INVOKE", Annotations.value(point, "value"),
						run + ": Fabricated's point stands for the call where Mixin takes a constructor's returns only");
			}
		}
	}

	@Test
	void aRedirectOfAListThePlatformLoopsOverAsAMapsKeysPicksAmongTheMapsKeys() throws Exception {
		// IRIS-1: Iris redirects the read of ParticleEngine.RENDER_ORDER in render, the list vanilla's loop goes over, to
		// pick the particle types each of its passes renders: a copy without the opaque ones, or a list of its own. Forge 47
		// keeps vanilla's render only to call an overload taking a frustum, whose loop goes over the keys of the particles'
		// map instead, a map it orders by RENDER_ORDER with Forge mods' own types after. The redirect moves to that loop,
		// and the handler's reads of the list see the map's other keys after its elements: a copy of the list keeps them.
		// NeoForge 21.1 names its overload as vanilla's method. Forge 47 runs vanilla's code under its SRG names and its
		// own under theirs, so there the overload, "render", is the method it deprecated vanilla's m_107336_ for. IRIS-2:
		// Forge 52's vanilla render, deprecated, calls itself where Forge meant the overload, which the game calls instead.
		keyedLoopRepair("render", false);
		keyedLoopRepair("m_107336_", false);
		keyedLoopRepair("render", true);
	}

	/**
	 * The IRIS-1 particle engine, its vanilla method named {@code vanilla} (deprecated where not the overload's name, or
	 * where it calls itself, {@code callsItself}, in place of the overload).
	 */
	private void keyedLoopRepair(String vanilla, boolean callsItself) throws Exception {
		boolean deprecated = !vanilla.equals("render") || callsItself;
		String engine = "net/minecraft/client/particle/Engine";
		String list = "Ljava/util/List;";
		String map = "Ljava/util/Map;";
		String overload = "(ILjava/lang/Object;)V";
		ClassWriter target = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		target.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, engine, null, "java/lang/Object", null);
		target.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "ORDER", list, null, null).visitEnd();
		target.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "rendered", list, null, null).visitEnd();
		target.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "particles", map, null, null).visitEnd();
		MethodVisitor mv = target.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
		mv.visitCode();
		for (String type : new String[] {"terrain", "opaque", "translucent", "custom"}) mv.visitLdcInsn(type);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/List", "of", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)" + list, true);
		mv.visitFieldInsn(Opcodes.PUTSTATIC, engine, "ORDER", list);
		mv.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
		mv.visitInsn(Opcodes.DUP);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
		mv.visitFieldInsn(Opcodes.PUTSTATIC, engine, "rendered", list);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		// this.particles = ordered(ORDER), as Forge's Maps.newTreeMap(makeParticleRenderTypeComparator(RENDER_ORDER)).
		mv = target.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitFieldInsn(Opcodes.GETSTATIC, engine, "ORDER", list);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, engine, "ordered", "(" + list + ")" + map, false);
		mv.visitFieldInsn(Opcodes.PUTFIELD, engine, "particles", map);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		// The map's keys in the order they are put in, which the test puts in the list's order, Forge's own last.
		mv = target.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "ordered", "(" + list + ")" + map, null, null);
		mv.visitCode();
		mv.visitTypeInsn(Opcodes.NEW, "java/util/LinkedHashMap");
		mv.visitInsn(Opcodes.DUP);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/LinkedHashMap", "<init>", "()V", false);
		mv.visitInsn(Opcodes.ARETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		mv = target.visitMethod(Opcodes.ACC_PUBLIC | (deprecated ? Opcodes.ACC_DEPRECATED : 0), vanilla, "(I)V", null, null);
		if (deprecated) mv.visitAnnotation("Ljava/lang/Deprecated;", true).visitEnd();
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitVarInsn(Opcodes.ILOAD, 1);
		if (!callsItself) mv.visitInsn(Opcodes.ACONST_NULL);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, engine, "render", callsItself ? "(I)V" : overload, false);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		// for (Object type : this.particles.keySet()) if (this.particles.get(type) != null) rendered.add(type);
		mv = target.visitMethod(Opcodes.ACC_PUBLIC, "render", overload, null, null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitFieldInsn(Opcodes.GETFIELD, engine, "particles", map);
		mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "keySet", "()Ljava/util/Set;", true);
		mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Set", "iterator", "()Ljava/util/Iterator;", true);
		mv.visitVarInsn(Opcodes.ASTORE, 3);
		Label loop = new Label();
		Label done = new Label();
		mv.visitLabel(loop);
		mv.visitVarInsn(Opcodes.ALOAD, 3);
		mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
		mv.visitJumpInsn(Opcodes.IFEQ, done);
		mv.visitVarInsn(Opcodes.ALOAD, 3);
		mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
		mv.visitVarInsn(Opcodes.ASTORE, 4);
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitFieldInsn(Opcodes.GETFIELD, engine, "particles", map);
		mv.visitVarInsn(Opcodes.ALOAD, 4);
		mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
		mv.visitJumpInsn(Opcodes.IFNULL, loop);
		mv.visitFieldInsn(Opcodes.GETSTATIC, engine, "rendered", list);
		mv.visitVarInsn(Opcodes.ALOAD, 4);
		mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
		mv.visitInsn(Opcodes.POP);
		mv.visitJumpInsn(Opcodes.GOTO, loop);
		mv.visitLabel(done);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		target.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(engine, target.toByteArray())::get);

		// Iris's translucent pass: a copy of the list without the opaque types.
		String phases = "com/example/mixin/Phases";
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, phases, null, "java/lang/Object", null);
		AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor values = m.visitArray("value");
		values.visit(null, Type.getObjectType(engine));
		values.visitEnd();
		m.visitEnd();
		FieldVisitor shadow = cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "ORDER", list, null, null);
		shadow.visitAnnotation(SHADOW, true).visitEnd();
		shadow.visitEnd();
		mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "selectParticles", "()" + list, null, null);
		AnnotationVisitor ann = mv.visitAnnotation(REDIRECT, true);
		AnnotationVisitor selectors = ann.visitArray("method");
		selectors.visit(null, vanilla + "(I)V");
		selectors.visitEnd();
		AnnotationVisitor at = ann.visitAnnotation("at", AT);
		at.visit("value", "FIELD");
		at.visit("target", "L" + engine + ";ORDER:" + list);
		at.visitEnd();
		ann.visitEnd();
		mv.visitCode();
		mv.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
		mv.visitInsn(Opcodes.DUP);
		mv.visitFieldInsn(Opcodes.GETSTATIC, phases, "ORDER", list);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "(Ljava/util/Collection;)V", false);
		mv.visitInsn(Opcodes.DUP);
		mv.visitLdcInsn("opaque");
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "remove", "(Ljava/lang/Object;)Z", false);
		mv.visitInsn(Opcodes.POP);
		mv.visitInsn(Opcodes.ARETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		Map<String, byte[]> mod = new HashMap<>(Map.of(phases, cw.toByteArray()));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Phases"));

		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = preflight(game, mod::get, ClassBytesSource.EMPTY, true).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes)
				.get(phases + ".class");
		assertNotNull(repaired, notes.toString());
		assertTrue(notes.stream().anyMatch(n -> n.severity() == Severity.WARN && n.message().contains("@Redirect selectParticles reads net.minecraft.client.particle"
				+ ".Engine.ORDER in " + vanilla + "(I)V, where NeoForge goes over the keys of its map particles in render" + overload + " instead")), notes.toString());
		mod.put(phases, repaired);
		assertTrue(preflight(game, mod::get, ClassBytesSource.EMPTY, true).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).isEmpty());
		ClassNode mixin = new ClassNode();
		new ClassReader(repaired).accept(mixin, 0);
		MethodNode bridge = mixin.methods.stream().filter(mn -> Annotations.find(mn, REDIRECT) != null).findFirst().orElseThrow();
		assertEquals("(" + map + ")Ljava/util/Set;", bridge.desc, "the bridge redirects the map's keySet()");
		AnnotationNode redirect = Annotations.find(bridge, REDIRECT);
		assertEquals(List.of("render" + overload), Annotations.value(redirect, "method"));
		AnnotationNode point = Annotations.list(Annotations.value(redirect, "at")).getFirst();
		assertEquals("INVOKE", Annotations.value(point, "value"));
		assertEquals("Ljava/util/Map;keySet()Ljava/util/Set;", Annotations.value(point, "target"));

		// As Mixin applies it: the mixin's methods and its own field merged into the target (its references to itself
		// pointed there), and the keySet() call redirected to the bridge, which then takes the receiver after this.
		ClassNode merged = new ClassNode();
		new ClassReader(target.toByteArray()).accept(merged, 0);
		for (FieldNode field : mixin.fields) {
			if (Annotations.find(field, SHADOW) == null) merged.fields.add(field);
		}
		for (MethodNode method : mixin.methods) {
			if (method.name.equals("<init>")) continue;
			for (AbstractInsnNode insn : method.instructions) {
				if (insn instanceof FieldInsnNode f && f.owner.equals(phases)) f.owner = engine;
				if (insn instanceof MethodInsnNode c && c.owner.equals(phases)) c.owner = engine;
			}
			merged.methods.add(method);
		}
		MethodNode loopOver = merged.methods.stream().filter(mn -> mn.name.equals("render") && mn.desc.equals(overload)).findFirst().orElseThrow();
		MethodInsnNode keySet = null;
		for (AbstractInsnNode insn : loopOver.instructions) {
			if (insn instanceof MethodInsnNode call && call.name.equals("keySet")) keySet = call;
		}
		loopOver.instructions.insertBefore(keySet.getPrevious().getPrevious(), new VarInsnNode(Opcodes.ALOAD, 0));
		loopOver.instructions.set(keySet, new MethodInsnNode(Opcodes.INVOKESPECIAL, engine, bridge.name, bridge.desc, false));
		loopOver.maxStack++;
		ClassWriter writer = new ClassWriter(0);
		merged.accept(writer);
		Class<?> type = new BytesClassLoader(Map.of(engine, writer.toByteArray())).loadClass(engine.replace('/', '.'));
		Object instance = type.getConstructor().newInstance();
		@SuppressWarnings("unchecked")
		Map<Object, Object> particles = (Map<Object, Object>) type.getField("particles").get(instance);
		for (String held : new String[] {"terrain", "opaque", "translucent", "forge"}) particles.put(held, List.of(held + " particle"));
		// The game calls the overload where vanilla's method calls itself.
		if (callsItself) type.getMethod("render", int.class, Object.class).invoke(instance, 0, null);
		else type.getMethod(vanilla, int.class).invoke(instance, 0);
		assertEquals(List.of("terrain", "translucent", "forge"), type.getField("rendered").get(null),
				"the pass goes over the types the handler kept, Forge's own among them, in order; custom holds no particles");
		java.lang.reflect.Field keys = type.getDeclaredField(mixin.fields.stream().filter(f -> Annotations.find(f, SHADOW) == null).findFirst().orElseThrow().name);
		keys.setAccessible(true);
		assertNull(keys.get(instance), "nothing outlives the call");
		java.lang.reflect.Method handler = type.getDeclaredMethod("selectParticles");
		handler.setAccessible(true);
		assertEquals(List.of("terrain", "translucent", "custom"), handler.invoke(instance), "called otherwise, it reads the list as it is");
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

	/**
	 * CORPUS-51: FrozenLib's entry added event captures vanilla's reference and id in {@code MappedRegistry.register} at
	 * its return. NeoForge 21.1 reduced that method to a call of an overload taking the id, which keeps the id in a local
	 * before the reference: Mixin could capture nothing in the method that is left ("Invalid descriptor").
	 */
	@Test
	void aLocalCaptureInAMethodReducedToACallOfItsOverloadMovesThereWithItsLocals() throws Exception {
		String register = "register(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;";
		String withId = "(ILjava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;";
		String cir = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;";
		// The overload's locals past its parameters: the id (slot 4), then the reference, or with twoReferences a second Object.
		java.util.function.Function<Boolean, byte[]> target = twoReferences -> {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
			MethodVisitor delegate = cw.visitMethod(Opcodes.ACC_PUBLIC, "register", "(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;", null, null);
			delegate.visitCode();
			delegate.visitVarInsn(Opcodes.ALOAD, 0);
			delegate.visitVarInsn(Opcodes.ALOAD, 0);
			delegate.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "size", "()I", false);
			delegate.visitVarInsn(Opcodes.ALOAD, 1);
			delegate.visitVarInsn(Opcodes.ALOAD, 2);
			delegate.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "register", withId, false);
			delegate.visitInsn(Opcodes.ARETURN);
			delegate.visitMaxs(0, 0);
			delegate.visitEnd();
			MethodVisitor overload = cw.visitMethod(Opcodes.ACC_PUBLIC, "register", withId, null, null);
			overload.visitCode();
			Label start = new Label();
			Label idStored = new Label();
			Label referenceStored = new Label();
			Label end = new Label();
			overload.visitLabel(start);
			overload.visitVarInsn(Opcodes.ILOAD, 1);
			overload.visitVarInsn(Opcodes.ISTORE, 4);
			overload.visitLabel(idStored);
			overload.visitVarInsn(Opcodes.ALOAD, 3);
			overload.visitVarInsn(Opcodes.ASTORE, 5);
			if (twoReferences) {
				overload.visitVarInsn(Opcodes.ALOAD, 3);
				overload.visitVarInsn(Opcodes.ASTORE, 6);
			}
			overload.visitLabel(referenceStored);
			overload.visitVarInsn(Opcodes.ALOAD, 5);
			overload.visitInsn(Opcodes.ARETURN);
			overload.visitLabel(end);
			overload.visitLocalVariable("this", "L" + TARGET + ";", null, start, end, 0);
			overload.visitLocalVariable("id", "I", null, start, end, 1);
			overload.visitLocalVariable("key", "Ljava/lang/String;", null, start, end, 2);
			overload.visitLocalVariable("value", "Ljava/lang/Object;", null, start, end, 3);
			overload.visitLocalVariable("i", "I", null, idStored, end, 4);
			overload.visitLocalVariable("reference", "Ljava/lang/Object;", null, referenceStored, end, 5);
			if (twoReferences) overload.visitLocalVariable("other", "Ljava/lang/Object;", null, referenceStored, end, 6);
			overload.visitMaxs(0, 0);
			overload.visitEnd();
			MethodVisitor size = cw.visitMethod(Opcodes.ACC_PUBLIC, "size", "()I", null, null);
			size.visitCode();
			size.visitInsn(Opcodes.ICONST_0);
			size.visitInsn(Opcodes.IRETURN);
			size.visitMaxs(0, 0);
			size.visitEnd();
			cw.visitEnd();
			return cw.toByteArray();
		};
		// invokeEntryAddEvent(key, entry, cir, reference, i), as vanilla's locals come: the reference, then the id.
		String handlerDesc = "(Ljava/lang/String;Ljava/lang/Object;" + cir + "Ljava/lang/Object;I)V";
		byte[] original = mixin("com/example/mixin/Registry", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "invokeEntryAddEvent", handlerDesc, null, null);
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, register);
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, AT);
			at.visit("value", "RETURN");
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnum("locals", "Lorg/spongepowered/asm/mixin/injection/callback/LocalCapture;", "CAPTURE_FAILEXCEPTION");
			ann.visitEnd();
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 6);
			mv.visitEnd();
		});
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Registry"));
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Registry", original));
		MixinPreflight preflight = preflight(new ClassIndex(Map.of(TARGET, target.apply(false))::get), mod::get, ClassBytesSource.EMPTY, false);
		List<PreflightFinding> before = preflight.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(before.stream().anyMatch(f -> f.message().contains("captures local variables of " + register + " in net.minecraft.world.Thing,"
				+ " which has none past its parameters")), before.toString());

		List<PreflightFinding> notes = new ArrayList<>();
		byte[] repaired = preflight.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes).get("com/example/mixin/Registry.class");
		assertTrue(notes.stream().anyMatch(n -> n.message().contains("@Inject invokeEntryAddEvent captures locals in " + register
				+ ", which NeoForge reduced to a call to register" + withId + "; the injector is pointed there")), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired).accept(node, 0);
		MethodNode bridge = node.methods.stream().filter(m -> m.name.equals("invokeEntryAddEvent$fabricated")).findFirst().orElseThrow();
		// The overload's parameters, the callback info and its locals as Mixin passes them: the id, then the reference.
		assertEquals("(ILjava/lang/String;Ljava/lang/Object;" + cir + "ILjava/lang/Object;)V", bridge.desc);
		AnnotationNode inject = Annotations.find(bridge, INJECT);
		assertEquals(List.of("register" + withId), Annotations.value(inject, "method"));
		List<String> loads = new ArrayList<>();
		for (AbstractInsnNode insn : bridge.instructions) {
			if (insn instanceof VarInsnNode v) loads.add(v.getOpcode() + ":" + v.var);
		}
		// this, the key and entry (not the id), the callback info, the reference (slot 6), then the id (slot 5).
		assertEquals(List.of(Opcodes.ALOAD + ":0", Opcodes.ALOAD + ":2", Opcodes.ALOAD + ":3", Opcodes.ALOAD + ":4", Opcodes.ALOAD + ":6",
				Opcodes.ILOAD + ":5"), loads);
		mod.put("com/example/mixin/Registry", repaired);
		List<PreflightFinding> after = preflight.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(after.stream().noneMatch(f -> f.message().contains("has none past its parameters")), after.toString());

		// Two locals of the reference's type there: which one vanilla's is can't be told, and the check leaves it out.
		Map<String, byte[]> unchanged = Map.of("com/example/mixin/Registry", original);
		MixinPreflight ambiguous = preflight(new ClassIndex(Map.of(TARGET, target.apply(true))::get), unchanged::get, ClassBytesSource.EMPTY, false);
		List<PreflightFinding> none = new ArrayList<>();
		Map<String, byte[]> left = ambiguous.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), none);
		assertTrue(none.stream().noneMatch(n -> n.message().contains("captures locals in")), none.toString());
		Map<String, byte[]> softened = Map.of("com/example/mixin/Registry", left.getOrDefault("com/example/mixin/Registry.class", original));
		List<PreflightFinding> refused = preflight(new ClassIndex(Map.of(TARGET, target.apply(true))::get), softened::get, ClassBytesSource.EMPTY, false)
				.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(refused.stream().anyMatch(f -> f.message().contains("which has none past its parameters")), refused.toString());
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

	@Test
	void aLambdaNamedByVanillasNumberingIsTheOneCreatedInItsPlace() throws Exception {
		// LAMBDA-5: NeoForge's Blocks patch makes the powered rail's constructor reference a lambda, so each later lambda
		// of Blocks is numbered one more; Wilder Wild's hooks into the ice blocks' spawn predicates name vanilla's numbers.
		// Where a lambda of the same shape has the name, the hook would apply to it without a word.
		Map<String, byte[]> vanilla = Map.of(TARGET, lambdaCreatingTarget(1, 2, 2, 3));
		ClassIndex neoForge = new ClassIndex(Map.of(TARGET, lambdaCreatingTarget(99, 1, 2, 2, 3))::get);
		String handlerDesc = "(I" + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V";
		byte[] original = mixin("com/example/mixin/Spawn", false, cw -> {
			for (String[] hook : new String[][] {{"ice", "lambda$static$3"}, {"packedIce", "lambda$static$0(I)Z"}}) {
				MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, hook[0], handlerDesc, null, null);
				AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
				AnnotationVisitor methods = ann.visitArray("method");
				methods.visit(null, hook[1]);
				methods.visitEnd();
				AnnotationVisitor ats = ann.visitArray("at");
				AnnotationVisitor at = ats.visitAnnotation(null, AT);
				at.visit("value", "HEAD");
				at.visitEnd();
				ats.visitEnd();
				ann.visitEnd();
				mv.visitCode();
				mv.visitInsn(Opcodes.RETURN);
				mv.visitMaxs(0, 2);
				mv.visitEnd();
			}
		});
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Spawn", original));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Spawn"));
		assertEquals(List.of(), preflight(neoForge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()),
				"the names fit lambdas of the same shape");
		assertTrue(preflight(neoForge, mod::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty(), "without vanilla's code, nothing tells");

		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = new MixinPreflight(neoForge, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		assertEquals(2, notes.size(), notes.toString());
		assertTrue(notes.stream().allMatch(n -> n.message().contains("which this game build numbers differently")), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get("com/example/mixin/Spawn.class")).accept(node, 0);
		Map<String, Object> selected = new HashMap<>();
		for (MethodNode m : node.methods) {
			AnnotationNode inject = Annotations.find(m, INJECT);
			if (inject != null) selected.put(m.name, Annotations.value(inject, "method"));
		}
		assertEquals(List.of("lambda$static$4(I)Z"), selected.get("ice"), "vanilla's last lambda, after the one the patch added");
		assertEquals(List.of("lambda$static$1(I)Z"), selected.get("packedIce"));

		// A lambda the patch changed has no place in vanilla's order: it is left to the other checks.
		ClassIndex changed = new ClassIndex(Map.of(TARGET, lambdaCreatingTarget(99, 1, 2, 2, 7))::get);
		List<PreflightFinding> none = new ArrayList<>();
		new MixinPreflight(changed, InstalledClasses.NONE, Map.of("com/example/mixin/Spawn", original)::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), none);
		assertTrue(none.stream().noneMatch(n -> n.message().contains("lambda$static$3")), none.toString());
	}

	@Test
	void aLambdaNamedByVanillasSrgNameIsTheOneForgesPatchedClassCreatesInItsPlace() throws Exception {
		// LAMBDA-6: Forge 47 names vanilla's lambdas by their SRG names (m_289088_), which its patched classes, compiled
		// from Forge's sources, don't keep: theirs carry javac's names, numbered after the lambdas the patch adds.
		Map<String, byte[]> vanilla = Map.of(TARGET, lambdaCreatingTarget(n -> "m_" + (1000 + n) + "_", 1, 2, 3));
		ClassIndex forge = new ClassIndex(Map.of(TARGET, lambdaCreatingTarget(n -> "lambda$static$" + n, 99, 1, 2, 3))::get);
		String handlerDesc = "(I" + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V";
		byte[] original = mixin("com/example/mixin/Bed", false, cw -> {
			for (String[] hook : new String[][] {{"third", "m_1002_"}, {"first", "m_1000_(I)Z"}}) {
				MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, hook[0], handlerDesc, null, null);
				AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
				AnnotationVisitor methods = ann.visitArray("method");
				methods.visit(null, hook[1]);
				methods.visitEnd();
				AnnotationVisitor ats = ann.visitArray("at");
				AnnotationVisitor at = ats.visitAnnotation(null, AT);
				at.visit("value", "HEAD");
				at.visitEnd();
				ats.visitEnd();
				ann.visitEnd();
				mv.visitCode();
				mv.visitInsn(Opcodes.RETURN);
				mv.visitMaxs(0, 2);
				mv.visitEnd();
			}
		});
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Bed", original));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Bed"));
		assertTrue(preflight(forge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).stream()
				.anyMatch(f -> f.message().contains("m_1002_, which does not exist")), "Forge's class has no method of vanilla's name");
		assertTrue(preflight(forge, mod::get, ClassBytesSource.EMPTY, false)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), new ArrayList<>()).isEmpty(), "without vanilla's code, nothing tells");

		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = new MixinPreflight(forge, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		assertEquals(2, notes.size(), notes.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get("com/example/mixin/Bed.class")).accept(node, 0);
		Map<String, Object> selected = new HashMap<>();
		for (MethodNode m : node.methods) {
			AnnotationNode inject = Annotations.find(m, INJECT);
			if (inject != null) selected.put(m.name, Annotations.value(inject, "method"));
		}
		assertEquals(List.of("lambda$static$3(I)Z"), selected.get("third"), "vanilla's last lambda, after the one the patch added");
		assertEquals(List.of("lambda$static$1(I)Z"), selected.get("first"));
		mod.put("com/example/mixin/Bed", repaired.get("com/example/mixin/Bed.class"));
		assertTrue(preflight(forge, mod::get, ClassBytesSource.EMPTY, false).check("test.mixins.json", config, Refmap.EMPTY, Set.of()).stream()
				.noneMatch(f -> f.message().contains("does not exist")));

		// A lambda of that name the build has in vanilla's place is the one meant: nothing is moved.
		ClassIndex kept = new ClassIndex(Map.of(TARGET, lambdaCreatingTarget(n -> "m_" + (1000 + n) + "_", 1, 2, 3))::get);
		List<PreflightFinding> none = new ArrayList<>();
		new MixinPreflight(kept, InstalledClasses.NONE, Map.of("com/example/mixin/Bed", original)::get, ClassBytesSource.EMPTY, false, true, vanilla::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), none);
		assertTrue(none.stream().noneMatch(n -> n.message().contains("numbers differently")), none.toString());

		// Forge changed the lambda's code (checkBedExists asks isBed): it is the one lambda of its interface and
		// descriptor the method makes, in vanilla's class and in Forge's.
		byte[] first = mixin("com/example/mixin/Bed", false, cw -> inject(cw, "h", "m_1000_", "HEAD", null, 1, null));
		Map<String, byte[]> single = Map.of(TARGET, lambdaCreatingTarget(n -> "m_" + (1000 + n) + "_", 5));
		List<PreflightFinding> patched = new ArrayList<>();
		Map<String, byte[]> moved = new MixinPreflight(new ClassIndex(Map.of(TARGET, lambdaCreatingTarget(n -> "lambda$static$" + n, 6))::get),
				InstalledClasses.NONE, Map.of("com/example/mixin/Bed", first)::get, ClassBytesSource.EMPTY, false, true, single::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), patched);
		assertEquals(1, patched.size(), patched.toString());
		ClassNode movedNode = new ClassNode();
		new ClassReader(moved.get("com/example/mixin/Bed.class")).accept(movedNode, 0);
		assertEquals(List.of("lambda$static$0(I)Z"), Annotations.value(Annotations.find(movedNode.methods.getFirst(), INJECT), "method"));
		// Forge changed both lambdas (MouseHandler's press and release): the method makes the same kinds in the same order.
		byte[] second = mixin("com/example/mixin/Bed", false, cw -> inject(cw, "h", "m_1001_", "HEAD", null, 1, null));
		Map<String, byte[]> pair = Map.of(TARGET, lambdaCreatingTarget(n -> "m_" + (1000 + n) + "_", 5, 6));
		List<PreflightFinding> both = new ArrayList<>();
		Map<String, byte[]> byPlace = new MixinPreflight(new ClassIndex(Map.of(TARGET, lambdaCreatingTarget(n -> "lambda$static$" + n, 7, 8))::get),
				InstalledClasses.NONE, Map.of("com/example/mixin/Bed", second)::get, ClassBytesSource.EMPTY, false, true, pair::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), both);
		assertEquals(1, both.size(), both.toString());
		ClassNode byPlaceNode = new ClassNode();
		new ClassReader(byPlace.get("com/example/mixin/Bed.class")).accept(byPlaceNode, 0);
		assertEquals(List.of("lambda$static$1(I)Z"), Annotations.value(Annotations.find(byPlaceNode.methods.getFirst(), INJECT), "method"));
		// With two of them in Forge's class where vanilla's makes one, which one it is can't be told.
		List<PreflightFinding> two = new ArrayList<>();
		new MixinPreflight(new ClassIndex(Map.of(TARGET, lambdaCreatingTarget(n -> "lambda$static$" + n, 6, 7))::get), InstalledClasses.NONE,
				Map.of("com/example/mixin/Bed", first)::get, ClassBytesSource.EMPTY, false, true, single::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), two);
		assertTrue(two.isEmpty(), two.toString());

		// A lambda another lambda creates (TagsProvider's in its run lambda) is found in its creator's place, here with
		// Forge's creator making one more lambda before it.
		byte[] nestedHook = mixin("com/example/mixin/Bed", false, cw -> inject(cw, "h", "m_3_", "HEAD", null, 1, null));
		Map<String, byte[]> nestedVanilla = Map.of(TARGET, nestedLambdaTarget("m_2_", "m_3_", false));
		List<PreflightFinding> nested = new ArrayList<>();
		Map<String, byte[]> nestedRepaired = new MixinPreflight(
				new ClassIndex(Map.of(TARGET, nestedLambdaTarget("lambda$m_1_$0", "lambda$m_1_$2", true))::get), InstalledClasses.NONE,
				Map.of("com/example/mixin/Bed", nestedHook)::get, ClassBytesSource.EMPTY, false, true, nestedVanilla::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), nested);
		assertEquals(1, nested.size(), nested.toString());
		ClassNode nestedNode = new ClassNode();
		new ClassReader(nestedRepaired.get("com/example/mixin/Bed.class")).accept(nestedNode, 0);
		assertEquals(List.of("lambda$m_1_$2(I)Z"), Annotations.value(Annotations.find(nestedNode.methods.getFirst(), INJECT), "method"));

		// Forge's build gives vanilla's SRG name to the lambda javac numbered as Mojang's names number vanilla's, here the
		// one its patch added (MouseHandler's m_168078_): the name is followed to vanilla's lambda all the same.
		Map<String, byte[]> twoVanilla = Map.of(TARGET, lambdaCreatingTarget(n -> "m_" + (1000 + n) + "_", 1, 2));
		ClassIndex renamed = new ClassIndex(Map.of(TARGET, lambdaCreatingTarget(n -> n == 0 ? "m_1000_" : "lambda$static$" + n, 99, 1, 2))::get);
		byte[] firstHook = mixin("com/example/mixin/Bed", false, cw -> inject(cw, "h", "m_1000_", "HEAD", null, 1, null));
		List<PreflightFinding> repointed = new ArrayList<>();
		Map<String, byte[]> repointedClass = new MixinPreflight(renamed, InstalledClasses.NONE, Map.of("com/example/mixin/Bed", firstHook)::get,
				ClassBytesSource.EMPTY, false, true, twoVanilla::get).repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), repointed);
		assertEquals(1, repointed.size(), repointed.toString());
		ClassNode repointedNode = new ClassNode();
		new ClassReader(repointedClass.get("com/example/mixin/Bed.class")).accept(repointedNode, 0);
		assertEquals(List.of("lambda$static$1(I)Z"), Annotations.value(Annotations.find(repointedNode.methods.getFirst(), INJECT), "method"));

		// Vanilla's lambda is static, Forge's takes this (its patch calls Forge's hooks there): they aren't the same lambda.
		List<PreflightFinding> instance = new ArrayList<>();
		new MixinPreflight(new ClassIndex(Map.of(TARGET, oneLambdaTarget("lambda$m_5_$0", false))::get), InstalledClasses.NONE,
				Map.of("com/example/mixin/Bed", firstHook)::get, ClassBytesSource.EMPTY, false, true, Map.of(TARGET, oneLambdaTarget("m_1000_", true))::get)
				.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), instance);
		assertTrue(instance.isEmpty(), instance.toString());
	}

	@Test
	void anInjectorWhoseHandlerIsStaticWhereItsTargetIsntIsReported() throws Exception {
		// LAMBDA-6: Mixin refuses an @Inject whose handler isn't static where its target is, and stops the game.
		Map<String, byte[]> mod = Map.of("com/example/mixin/Bed", mixin("com/example/mixin/Bed", false, cw -> inject(cw, "h", "m_1000_", "HEAD", null, 1, null)));
		List<PreflightFinding> findings = preflight(new ClassIndex(Map.of(TARGET, oneLambdaTarget("m_1000_", true))::get), mod::get, ClassBytesSource.EMPTY, false)
				.check("test.mixins.json", MixinConfigFile.parse(config(true, "Bed")), Refmap.EMPTY, Set.of());
		assertTrue(findings.stream().anyMatch(f -> f.message().contains("@Inject handler h isn't static where m_1000_(I)Z")), findings.toString());

		// A static handler on a method that takes this: SpongePowered's 0.8.5 (Forge 47) refuses it; from 0.8.6, as in
		// Fabric's Mixin, it applies (Fabric API's furnace hook on Forge 66, whose consumeFuel takes this).
		Map<String, byte[]> staticHook = Map.of("com/example/mixin/Bed", mixin("com/example/mixin/Bed", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "h", "(ILorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V",
					null, null);
			AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
			AnnotationVisitor methods = ann.visitArray("method");
			methods.visit(null, "m_1000_");
			methods.visitEnd();
			AnnotationVisitor ats = ann.visitArray("at");
			AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
			at.visit("value", "HEAD");
			at.visitEnd();
			ats.visitEnd();
			ann.visitEnd();
			mv.visitEnd();
		}));
		ClassIndex instanceTarget = new ClassIndex(Map.of(TARGET, oneLambdaTarget("m_1000_", false))::get);
		List<PreflightFinding> on085 = new MixinPreflight(instanceTarget, InstalledClasses.NONE, staticHook::get, ClassBytesSource.EMPTY, false, true,
				ClassBytesSource.EMPTY, "Forge", false, MixinPreflight.ConstructorInjects.RETURNS_ONLY, false, true)
				.check("test.mixins.json", MixinConfigFile.parse(config(true, "Bed")), Refmap.EMPTY, Set.of());
		assertTrue(on085.stream().anyMatch(f -> f.message().contains("@Inject handler h is static where m_1000_(I)Z")), on085.toString());
		List<PreflightFinding> on087 = new MixinPreflight(instanceTarget, InstalledClasses.NONE, staticHook::get, ClassBytesSource.EMPTY, false, true,
				ClassBytesSource.EMPTY, "Forge", true, MixinPreflight.ConstructorInjects.UNSAFE_POINTS, false, false)
				.check("test.mixins.json", MixinConfigFile.parse(config(true, "Bed")), Refmap.EMPTY, Set.of());
		assertTrue(on087.isEmpty(), on087.toString());
	}

	/**
	 * {@link #TARGET} whose {@code m_5_()V} creates one {@code IntPredicate} lambda {@code name}, static or taking
	 * {@code this}.
	 */
	private static byte[] oneLambdaTarget(String name, boolean isStatic) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		Handle metafactory = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
				"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;"
						+ "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
		MethodVisitor creator = cw.visitMethod(Opcodes.ACC_PUBLIC, "m_5_", "()V", null, null);
		creator.visitCode();
		if (!isStatic) creator.visitVarInsn(Opcodes.ALOAD, 0);
		creator.visitInvokeDynamicInsn("test", isStatic ? "()Ljava/util/function/IntPredicate;" : "(L" + TARGET + ";)Ljava/util/function/IntPredicate;",
				metafactory, Type.getType("(I)Z"), new Handle(isStatic ? Opcodes.H_INVOKESTATIC : Opcodes.H_INVOKESPECIAL, TARGET, name, "(I)Z", false),
				Type.getType("(I)Z"));
		creator.visitInsn(Opcodes.POP);
		creator.visitInsn(Opcodes.RETURN);
		creator.visitMaxs(1, 1);
		creator.visitEnd();
		MethodVisitor body = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC | (isStatic ? Opcodes.ACC_STATIC : 0), name, "(I)Z", null, null);
		body.visitCode();
		body.visitInsn(Opcodes.ICONST_1);
		body.visitInsn(Opcodes.IRETURN);
		body.visitMaxs(1, 2);
		body.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * {@link #TARGET} whose {@code m_1_()V} creates a {@code Runnable} lambda {@code outer}, which creates an
	 * {@code IntPredicate} lambda {@code inner}, after a {@code Supplier} lambda {@code lambda$m_1_$1} when {@code extra}.
	 */
	private static byte[] nestedLambdaTarget(String outer, String inner, boolean extra) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		Handle metafactory = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
				"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;"
						+ "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
		MethodVisitor m1 = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "m_1_", "()V", null, null);
		m1.visitCode();
		m1.visitInvokeDynamicInsn("run", "()Ljava/lang/Runnable;", metafactory, Type.getType("()V"),
				new Handle(Opcodes.H_INVOKESTATIC, TARGET, outer, "()V", false), Type.getType("()V"));
		m1.visitInsn(Opcodes.POP);
		m1.visitInsn(Opcodes.RETURN);
		m1.visitMaxs(1, 0);
		m1.visitEnd();
		MethodVisitor out = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, outer, "()V", null, null);
		out.visitCode();
		if (extra) {
			out.visitInvokeDynamicInsn("get", "()Ljava/util/function/Supplier;", metafactory, Type.getType("()Ljava/lang/Object;"),
					new Handle(Opcodes.H_INVOKESTATIC, TARGET, "lambda$m_1_$1", "()Ljava/lang/Object;", false), Type.getType("()Ljava/lang/Object;"));
			out.visitInsn(Opcodes.POP);
			MethodVisitor supplier = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, "lambda$m_1_$1", "()Ljava/lang/Object;", null, null);
			supplier.visitCode();
			supplier.visitInsn(Opcodes.ACONST_NULL);
			supplier.visitInsn(Opcodes.ARETURN);
			supplier.visitMaxs(1, 0);
			supplier.visitEnd();
		}
		out.visitInvokeDynamicInsn("test", "()Ljava/util/function/IntPredicate;", metafactory, Type.getType("(I)Z"),
				new Handle(Opcodes.H_INVOKESTATIC, TARGET, inner, "(I)Z", false), Type.getType("(I)Z"));
		out.visitInsn(Opcodes.POP);
		out.visitInsn(Opcodes.RETURN);
		out.visitMaxs(1, 0);
		out.visitEnd();
		MethodVisitor in = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, inner, "(I)Z", null, null);
		in.visitCode();
		in.visitInsn(Opcodes.ICONST_1);
		in.visitInsn(Opcodes.IRETURN);
		in.visitMaxs(1, 1);
		in.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * {@link #TARGET} whose static initializer creates a lambda {@code lambda$static$N} for each constant, in order, each
	 * an {@code IntPredicate} body {@code (I)Z} returning its constant.
	 */
	private static byte[] lambdaCreatingTarget(int... constants) {
		return lambdaCreatingTarget(n -> "lambda$static$" + n, constants);
	}

	/** {@link #lambdaCreatingTarget(int...)} with the {@code N}th lambda named {@code name.apply(N)}. */
	private static byte[] lambdaCreatingTarget(java.util.function.IntFunction<String> name, int... constants) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		Handle metafactory = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
				"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;"
						+ "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
		MethodVisitor clinit = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
		clinit.visitCode();
		for (int n = 0; n < constants.length; n++) {
			clinit.visitInvokeDynamicInsn("test", "()Ljava/util/function/IntPredicate;", metafactory, Type.getType("(I)Z"),
					new Handle(Opcodes.H_INVOKESTATIC, TARGET, name.apply(n), "(I)Z", false), Type.getType("(I)Z"));
			clinit.visitInsn(Opcodes.POP);
			MethodVisitor body = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, name.apply(n), "(I)Z", null, null);
			body.visitCode();
			body.visitIntInsn(Opcodes.BIPUSH, constants[n]);
			body.visitInsn(Opcodes.IRETURN);
			body.visitMaxs(1, 1);
			body.visitEnd();
		}
		clinit.visitInsn(Opcodes.RETURN);
		clinit.visitMaxs(1, 0);
		clinit.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
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

	/**
	 * An invoker of a method the target already declares uses that method instead of having Mixin generate one; one the
	 * target declares privately (as JourneyMap's accessor clashes with vanilla's {@code EditBox.isEditable()}) is made
	 * public by the mod's access transformer (ACC-1).
	 */
	@Test
	void accessorsDuplicatingAMethodOfTheTargetUseIt() throws Exception {
		Map<String, byte[]> mod = new HashMap<>(Map.of(
				"com/example/mixin/Public", invokerInterface("com/example/mixin/Public", "tick", "()V"),
				"com/example/mixin/Private", invokerInterface("com/example/mixin/Private", "helper", "(I)V")));
		ClassIndex game = new ClassIndex(Map.of(TARGET, targetClass())::get);
		MixinPreflight preflight = preflight(game, mod::get, ClassBytesSource.EMPTY, false);
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Public", "Private"));
		List<PreflightFinding> repairs = new java.util.ArrayList<>();
		Map<String, byte[]> repaired = preflight.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), repairs);
		assertEquals(Set.of("com/example/mixin/Public.class", "com/example/mixin/Private.class"), repaired.keySet());
		assertEquals(2, repairs.size(), repairs.toString());
		assertTrue(repairs.stream().allMatch(f -> f.severity() == Severity.WARN), repairs.toString());
		assertTrue(repairs.stream().anyMatch(f -> f.message().contains("helper(I)V already exists in") && f.message().contains("made public")), repairs.toString());
		assertEquals(Set.of("public net.minecraft.world.Thing helper(I)V"), preflight.accessTransformers(), "only the private one is made public");

		org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
		new org.objectweb.asm.ClassReader(repaired.get("com/example/mixin/Public.class")).accept(node, 0);
		org.objectweb.asm.tree.MethodNode tick = node.methods.getFirst();
		assertTrue((tick.access & Opcodes.ACC_SYNTHETIC) != 0, "synthetic, so Mixin still treats the interface as an accessor mixin");
		assertEquals(List.of("Lorg/spongepowered/asm/mixin/Shadow;"), tick.visibleAnnotations.stream().map(a -> a.desc).toList());
		mod.put("com/example/mixin/Public", repaired.get("com/example/mixin/Public.class"));
		mod.put("com/example/mixin/Private", repaired.get("com/example/mixin/Private.class"));
		List<PreflightFinding> findings = preflight.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(findings.isEmpty(), findings.toString());
	}

	/**
	 * CORPUS-37: Forge 47 keeps holders of vanilla's values in a brewing mix's {@code from}, where Porting Lib's accessor
	 * reads vanilla's. Where Fabricated's in-game mod adds a bridge getting (or setting) the field's value as vanilla's
	 * type, the accessor invokes it, under its own name and descriptor, which a mod calling it (Create) still finds; where
	 * it adds none, pre-flight reports the field's type as before.
	 */
	@Test
	void anAccessorOfAFieldThePlatformRetypedInvokesFabricatedsBridge() throws Exception {
		String mix = "net/minecraft/world/Mix";
		java.util.function.Function<Boolean, byte[]> target = bridged -> {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, mix, null, "java/lang/Object", null);
			cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "from", "Lnet/minecraft/core/Holder;", null, null).visitEnd();
			if (bridged) {
				MethodVisitor get = cw.visitMethod(Opcodes.ACC_PUBLIC, "fabricated$get$from", "()Ljava/lang/Object;", null, null);
				get.visitCode();
				get.visitInsn(Opcodes.ACONST_NULL);
				get.visitInsn(Opcodes.ARETURN);
				get.visitMaxs(0, 0);
				get.visitEnd();
				MethodVisitor set = cw.visitMethod(Opcodes.ACC_PUBLIC, "fabricated$set$from", "(Ljava/lang/Object;)V", null, null);
				set.visitCode();
				set.visitInsn(Opcodes.RETURN);
				set.visitMaxs(0, 0);
				set.visitEnd();
			}
			cw.visitEnd();
			return cw.toByteArray();
		};
		ClassWriter mw = new ClassWriter(0);
		mw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE, "com/example/mixin/MixAccessor", null,
				"java/lang/Object", null);
		AnnotationVisitor m = mw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor targets = m.visitArray("value");
		targets.visit(null, Type.getObjectType(mix));
		targets.visitEnd();
		m.visitEnd();
		MethodVisitor getter = mw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "port$from", "()Ljava/lang/Object;", null, null);
		AnnotationVisitor getAccessor = getter.visitAnnotation("Lorg/spongepowered/asm/mixin/gen/Accessor;", true);
		getAccessor.visit("value", "from");
		getAccessor.visitEnd();
		getter.visitEnd();
		MethodVisitor setter = mw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "port$setFrom", "(Ljava/lang/Object;)V", null, null);
		AnnotationVisitor setAccessor = setter.visitAnnotation("Lorg/spongepowered/asm/mixin/gen/Accessor;", true);
		setAccessor.visit("value", "from");
		setAccessor.visitEnd();
		setter.visitEnd();
		mw.visitEnd();
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/MixAccessor", mw.toByteArray()));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "MixAccessor"));

		MixinPreflight bridged = preflight(new ClassIndex(Map.of(mix, target.apply(true))::get), mod::get, ClassBytesSource.EMPTY, false);
		List<PreflightFinding> repairs = new java.util.ArrayList<>();
		Map<String, byte[]> repaired = bridged.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), repairs);
		assertEquals(Set.of("com/example/mixin/MixAccessor.class"), repaired.keySet());
		assertEquals(2, repairs.size(), repairs.toString());
		assertTrue(repairs.stream().allMatch(f -> f.severity() == Severity.INFO && f.message().contains("CORPUS-37")), repairs.toString());
		org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
		new org.objectweb.asm.ClassReader(repaired.get("com/example/mixin/MixAccessor.class")).accept(node, 0);
		Map<String, List<Object>> invokers = new HashMap<>();
		for (org.objectweb.asm.tree.MethodNode method : node.methods) {
			assertEquals(1, method.visibleAnnotations.size(), method.name);
			org.objectweb.asm.tree.AnnotationNode ann = method.visibleAnnotations.getFirst();
			assertEquals("Lorg/spongepowered/asm/mixin/gen/Invoker;", ann.desc, method.name);
			invokers.put(method.name + method.desc, ann.values);
		}
		assertEquals(Map.of("port$from()Ljava/lang/Object;", List.of("value", "fabricated$get$from", "remap", false),
				"port$setFrom(Ljava/lang/Object;)V", List.of("value", "fabricated$set$from", "remap", false)), invokers);
		mod.put("com/example/mixin/MixAccessor", repaired.get("com/example/mixin/MixAccessor.class"));
		List<PreflightFinding> findings = bridged.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(findings.isEmpty(), findings.toString());

		mod.put("com/example/mixin/MixAccessor", mw.toByteArray());
		MixinPreflight plain = preflight(new ClassIndex(Map.of(mix, target.apply(false))::get), mod::get, ClassBytesSource.EMPTY, false);
		List<PreflightFinding> none = new java.util.ArrayList<>();
		assertTrue(plain.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), none).isEmpty(), none.toString());
		List<PreflightFinding> refused = plain.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(refused.stream().anyMatch(f -> f.severity() == Severity.FAIL && f.message().contains("has a different type than")), refused.toString());
	}

	/**
	 * CORPUS-94: Forge 47 replaces a mob bucket's entity type field with a supplier of the type, where Supplementaries'
	 * accessor reads the type. Where Fabricated's in-game mod adds a bridge getting the type, the accessor of the field the
	 * class no longer has invokes it; where it adds none, pre-flight reports the field missing as before.
	 */
	@Test
	void anAccessorOfAFieldThePlatformReplacedInvokesFabricatedsBridge() throws Exception {
		String bucket = "net/minecraft/world/Bucket";
		String type = "()Lnet/minecraft/world/EntityType;";
		java.util.function.Function<Boolean, byte[]> target = bridged -> {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, bucket, null, "java/lang/Object", null);
			cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "entityTypeSupplier", "Ljava/util/function/Supplier;", null, null).visitEnd();
			if (bridged) {
				MethodVisitor get = cw.visitMethod(Opcodes.ACC_PUBLIC, "fabricated$get$type", type, null, null);
				get.visitCode();
				get.visitInsn(Opcodes.ACONST_NULL);
				get.visitInsn(Opcodes.ARETURN);
				get.visitMaxs(0, 0);
				get.visitEnd();
			}
			cw.visitEnd();
			return cw.toByteArray();
		};
		ClassWriter mw = new ClassWriter(0);
		mw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE, "com/example/mixin/BucketAccessor", null,
				"java/lang/Object", null);
		AnnotationVisitor m = mw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor targets = m.visitArray("value");
		targets.visit(null, Type.getObjectType(bucket));
		targets.visitEnd();
		m.visitEnd();
		MethodVisitor getter = mw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "getType", type, null, null);
		AnnotationVisitor accessor = getter.visitAnnotation("Lorg/spongepowered/asm/mixin/gen/Accessor;", true);
		accessor.visit("value", "type");
		accessor.visitEnd();
		getter.visitEnd();
		mw.visitEnd();
		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/BucketAccessor", mw.toByteArray()));
		MixinConfigFile config = MixinConfigFile.parse(config(true, "BucketAccessor"));

		MixinPreflight bridged = preflight(new ClassIndex(Map.of(bucket, target.apply(true))::get), mod::get, ClassBytesSource.EMPTY, false);
		List<PreflightFinding> repairs = new java.util.ArrayList<>();
		Map<String, byte[]> repaired = bridged.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), repairs);
		assertEquals(Set.of("com/example/mixin/BucketAccessor.class"), repaired.keySet());
		assertEquals(1, repairs.size(), repairs.toString());
		assertTrue(repairs.getFirst().severity() == Severity.INFO && repairs.getFirst().message().contains("gave another type or replaced")
				&& repairs.getFirst().message().contains("fabricated$get$type"), repairs.toString());
		org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
		new org.objectweb.asm.ClassReader(repaired.get("com/example/mixin/BucketAccessor.class")).accept(node, 0);
		org.objectweb.asm.tree.AnnotationNode invoker = node.methods.getFirst().visibleAnnotations.getFirst();
		assertEquals("Lorg/spongepowered/asm/mixin/gen/Invoker;", invoker.desc);
		assertEquals(List.of("value", "fabricated$get$type", "remap", false), invoker.values);
		mod.put("com/example/mixin/BucketAccessor", repaired.get("com/example/mixin/BucketAccessor.class"));
		List<PreflightFinding> findings = bridged.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(findings.isEmpty(), findings.toString());

		mod.put("com/example/mixin/BucketAccessor", mw.toByteArray());
		MixinPreflight plain = preflight(new ClassIndex(Map.of(bucket, target.apply(false))::get), mod::get, ClassBytesSource.EMPTY, false);
		List<PreflightFinding> none = new java.util.ArrayList<>();
		assertTrue(plain.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), none).isEmpty(), none.toString());
		List<PreflightFinding> refused = plain.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(refused.stream().anyMatch(f -> f.message().contains("does not exist")), refused.toString());
	}

	/**
	 * CORPUS-69: an {@code @Intrinsic} of a class mixin that only calls the superclass's method (Lithium's
	 * {@code setCachedState} of the chest, there so its {@code @Inject} has a method to inject into) is dropped where the
	 * target already declares the method, as Forge 47's patched chest does: Mixin rejects such an {@code @Intrinsic}
	 * (taking it for a renamed interface method, and finding no rename), and keeping the target's method is what
	 * {@code @Intrinsic} means. The injector then injects into the target's own. One with code of its own, or displacing
	 * the target's, isn't repaired, and pre-flight reports that Mixin rejects the mixin.
	 */
	@Test
	void anIntrinsicOnlyCallingSuperIsDroppedWhereTheTargetDeclaresIt() throws Exception {
		BiFunction<Boolean, Consumer<MethodVisitor>, byte[]> intrinsic = (displace, body) -> mixin("com/example/mixin/Intr", false, cw -> {
			inject(cw, "afterTick", "tick", "RETURN", null, 1, null);
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
			AnnotationVisitor ann = mv.visitAnnotation("Lorg/spongepowered/asm/mixin/Intrinsic;", false);
			if (displace) ann.visit("displace", true);
			ann.visitEnd();
			mv.visitCode();
			Label start = new Label();
			mv.visitLabel(start);
			mv.visitLineNumber(12, start);
			body.accept(mv);
			mv.visitMaxs(1, 1);
			mv.visitEnd();
		});
		Consumer<MethodVisitor> superOnly = mv -> {
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "tick", "()V", false);
			mv.visitInsn(Opcodes.RETURN);
		};
		ClassIndex game = new ClassIndex(Map.of(TARGET, targetClass())::get);
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Intr"));

		Map<String, byte[]> mod = new HashMap<>(Map.of("com/example/mixin/Intr", intrinsic.apply(false, superOnly)));
		MixinPreflight preflight = preflight(game, mod::get, ClassBytesSource.EMPTY, false);
		List<PreflightFinding> repairs = new ArrayList<>();
		Map<String, byte[]> repaired = preflight.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), repairs);
		assertEquals(Set.of("com/example/mixin/Intr.class"), repaired.keySet());
		assertEquals(1, repairs.size(), repairs.toString());
		assertEquals(Severity.INFO, repairs.getFirst().severity());
		assertTrue(repairs.getFirst().message().contains("@Intrinsic tick()V only calls the superclass's method"), repairs.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get("com/example/mixin/Intr.class")).accept(node, 0);
		assertEquals(List.of("afterTick"), node.methods.stream().map(m -> m.name).toList(), "the injector stays; the intrinsic goes");
		mod.put("com/example/mixin/Intr", repaired.get("com/example/mixin/Intr.class"));
		List<PreflightFinding> findings = preflight.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(findings.isEmpty(), findings.toString());

		// Code of its own, or displacing the target's method: Mixin rejects the mixin, which pre-flight reports.
		Consumer<MethodVisitor> own = mv -> mv.visitInsn(Opcodes.RETURN);
		for (byte[] other : List.of(intrinsic.apply(false, own), intrinsic.apply(true, superOnly))) {
			mod.put("com/example/mixin/Intr", other);
			preflight = preflight(game, mod::get, ClassBytesSource.EMPTY, false);
			repairs = new ArrayList<>();
			assertTrue(preflight.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), repairs).isEmpty(), repairs.toString());
			findings = preflight.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
			assertTrue(findings.stream().anyMatch(f -> (f.severity() == Severity.FAIL || f.severity() == Severity.LEFT_OUT)
					&& f.message().startsWith("@Intrinsic tick()V already exists in net.minecraft.world.Thing")), findings.toString());
		}
	}

	/**
	 * Where the running Mixin looks up the class of each local in a mixin method's debug table and stops on one it can't
	 * find (SpongePowered's, Fabric's before 0.17.0), a local of a class this side lacks (owo's client-only rectangle on a
	 * dedicated server) leaves the table (LVT-1); the others stay, and a Mixin that looks none up keeps them all.
	 */
	@Test
	void aLocalOfAClassThisSideLacksLeavesTheDebugTableWhereMixinLooksItUp() throws Exception {
		String rect = "net/minecraft/client/gui/Rect";
		byte[] keeps = mixin("com/example/mixin/Loc", false, cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "setArea", "(L" + rect + ";L" + TARGET + ";I[L" + rect + ";)V", null, null);
			mv.visitCode();
			Label start = new Label();
			Label end = new Label();
			mv.visitLabel(start);
			mv.visitInsn(Opcodes.RETURN);
			mv.visitLabel(end);
			mv.visitLocalVariable("this", "Lcom/example/mixin/Loc;", null, start, end, 0);
			mv.visitLocalVariable("area", "L" + rect + ";", null, start, end, 1);
			mv.visitLocalVariable("thing", "L" + TARGET + ";", null, start, end, 2);
			mv.visitLocalVariable("count", "I", null, start, end, 3);
			mv.visitLocalVariable("areas", "[L" + rect + ";", null, start, end, 4);
			mv.visitMaxs(0, 5);
			mv.visitEnd();
		});
		ClassIndex game = new ClassIndex(Map.of(TARGET, targetClass())::get);
		MixinConfigFile config = MixinConfigFile.parse(config(true, "Loc"));
		Map<String, byte[]> mod = Map.of("com/example/mixin/Loc", keeps);

		MixinPreflight looksUp = new MixinPreflight(game, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, ClassBytesSource.EMPTY,
				"Forge", true, MixinPreflight.ConstructorInjects.UNSAFE_POINTS, false, false, false, true);
		List<PreflightFinding> repairs = new ArrayList<>();
		Map<String, byte[]> repaired = looksUp.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), repairs);
		assertEquals(Set.of("com/example/mixin/Loc.class"), repaired.keySet());
		assertEquals(1, repairs.size(), repairs.toString());
		assertEquals(Severity.INFO, repairs.getFirst().severity());
		assertTrue(repairs.getFirst().message().contains("local variables of net.minecraft.client.gui.Rect, not found on this side"), repairs.toString());
		ClassNode node = new ClassNode();
		new ClassReader(repaired.get("com/example/mixin/Loc.class")).accept(node, 0);
		MethodNode method = node.methods.stream().filter(m -> m.name.equals("setArea")).findFirst().orElseThrow();
		assertEquals(List.of("this", "thing", "count"), method.localVariables.stream().map(l -> l.name).toList());

		MixinPreflight looksUpNone = new MixinPreflight(game, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, ClassBytesSource.EMPTY,
				"NeoForge", true, MixinPreflight.ConstructorInjects.AFTER_DELEGATE, true, false, true, false);
		repairs = new ArrayList<>();
		assertTrue(looksUpNone.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), repairs).isEmpty(), repairs.toString());
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
		assertEquals(Severity.FAIL, withInterface.getFirst().severity(), "a handler without code to judge");
		assertEquals("broken()V", withInterface.getFirst().handler());

		// LEAVE-3: in a mixin adding an interface the mod's code uses, a handler using nothing of the mixin's own is left
		// out alone (owo-lib's GuiRendererMixin); the interface still applies. One keeping state of the mixin's own, which
		// the interface's methods may read, still disables the mod.
		java.util.function.BiFunction<String, Boolean, byte[]> handlerWithCode = (name, keepsState) -> mixin("com/example/mixin/" + name, false,
				List.of("com/example/api/Duck"), cw -> {
					cw.visitField(Opcodes.ACC_PRIVATE, "seen", "Z", null, null).visitEnd();
					MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "broken", "()V", null, null);
					AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
					AnnotationVisitor methods = ann.visitArray("method");
					methods.visit(null, "removedMethod");
					methods.visitEnd();
					AnnotationVisitor ats = ann.visitArray("at");
					AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
					at.visit("value", "HEAD");
					at.visitEnd();
					ats.visitEnd();
					ann.visit("require", 1);
					ann.visitEnd();
					mv.visitCode();
					if (keepsState) {
						mv.visitVarInsn(Opcodes.ALOAD, 0);
						mv.visitInsn(Opcodes.ICONST_1);
						mv.visitFieldInsn(Opcodes.PUTFIELD, "com/example/mixin/" + name, "seen", "Z");
					}
					mv.visitInsn(Opcodes.RETURN);
					mv.visitMaxs(2, 1);
					mv.visitEnd();
				});
		List<PreflightFinding> stateless = run(config(true, "Plain"), null, Map.of("com/example/mixin/Plain", handlerWithCode.apply("Plain", false)), false);
		assertEquals(1, stateless.size(), stateless.toString());
		assertEquals(Severity.LEFT_OUT, stateless.getFirst().severity());
		assertEquals("broken()V", stateless.getFirst().handler(), "only the injector: the interface stays");
		byte[] keeping = handlerWithCode.apply("Keeping", true);
		List<PreflightFinding> withState = run(config(true, "Keeping"), null, Map.of("com/example/mixin/Keeping", keeping), false);
		assertEquals(1, withState.size(), withState.toString());
		assertEquals(Severity.FAIL, withState.getFirst().severity());
		assertEquals("broken()V", withState.getFirst().handler());
	}

	/**
	 * STATE-1: in a self-contained mixin adding fields, a failing injector goes with the injectors keeping state with it
	 * in the mixin's own fields, and the others stay: Fabric API's {@code FluidRendererMixin} on NeoForge 1.21.1, whose
	 * hooks into {@code tesselate} NeoForge's patches rewrote, keeps its hook into the sprite reload, which hands the water
	 * and lava sprites to Fabric API's fluid handlers. Where the state is shared with code that runs without an injector
	 * (an {@code @Overwrite}, a method that isn't private, a field that isn't), or with a class nested in the mixin, the
	 * whole mixin still goes.
	 */
	@Test
	void aFailingInjectorGoesWithTheInjectorsKeepingStateWithItAndTheOthersStay() throws Exception {
		String name = "com/example/mixin/Fluid";
		Function<Consumer<ClassWriter>, byte[]> fluid = extra -> mixin(name, false, cw -> {
			cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "neighbor", "Ljava/lang/ThreadLocal;", null, null).visitEnd();
			handlerWithCode(cw, name, "reload", "HEAD", null, false); // applies, uses nothing of the mixin's own
			handlerWithCode(cw, name, "remember", "INVOKE", "Lnet/minecraft/world/Thing;removed()V", true); // fails
			handlerWithCode(cw, name, "recall", "INVOKE", "Lnet/minecraft/world/Thing;helper(I)V", true); // applies, shares neighbor
			handlerWithCode(cw, name, "overlay", "FIELD", "Lnet/minecraft/world/Thing;overlay:I", false); // fails, alone
			extra.accept(cw);
		});
		List<PreflightFinding> findings = run(config(true, "Fluid"), null, Map.of(name, fluid.apply(cw -> { })), false);
		assertTrue(findings.stream().allMatch(f -> f.severity() == Severity.LEFT_OUT && f.handler() != null), findings.toString());
		assertEquals(Set.of("remember()V", "recall()V", "overlay()V"), findings.stream().map(PreflightFinding::handler).collect(Collectors.toSet()),
				"the reload hook stays: " + findings);
		PreflightFinding together = findings.stream().filter(f -> f.handler().equals("recall()V")).findFirst().orElseThrow();
		assertEquals("The injector of recall()V goes with that of remember()V, which can't apply: they keep state together in this mixin's field neighbor",
				together.message());
		assertEquals(3, findings.size(), findings.toString());

		// The state reaches code running without an injector, or code pre-flight doesn't follow: the whole mixin goes.
		Map<String, Consumer<ClassWriter>> shared = Map.of(
				"an @Overwrite", cw -> {
					MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "helper", "(I)V", null, null);
					mv.visitAnnotation("Lorg/spongepowered/asm/mixin/Overwrite;", false).visitEnd();
					readNeighbor(mv, name);
				},
				"a method that isn't private", cw -> readNeighbor(cw.visitMethod(Opcodes.ACC_PUBLIC, "read", "()V", null, null), name),
				"a field that isn't private", cw -> cw.visitField(Opcodes.ACC_PUBLIC, "shown", "I", null, null).visitEnd(),
				"a class nested in the mixin", cw -> cw.visitInnerClass(name + "$1", null, null, 0));
		for (Map.Entry<String, Consumer<ClassWriter>> e : shared.entrySet()) {
			Consumer<ClassWriter> extra = e.getKey().equals("a field that isn't private") ? cw -> {
				e.getValue().accept(cw);
				MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "count", "()V", null, null);
				mv.visitCode();
				mv.visitVarInsn(Opcodes.ALOAD, 0);
				mv.visitFieldInsn(Opcodes.GETFIELD, name, "shown", "I");
				mv.visitInsn(Opcodes.POP);
				mv.visitVarInsn(Opcodes.ALOAD, 0);
				mv.visitFieldInsn(Opcodes.GETFIELD, name, "neighbor", "Ljava/lang/ThreadLocal;");
				mv.visitInsn(Opcodes.POP);
				mv.visitInsn(Opcodes.RETURN);
				mv.visitMaxs(1, 1);
				mv.visitEnd();
			} : e.getValue();
			byte[] bytes = fluid.apply(extra);
			if (e.getKey().equals("a field that isn't private")) bytes = callCountFromRemember(bytes, name);
			List<PreflightFinding> whole = run(config(true, "Fluid"), null, Map.of(name, bytes), false);
			assertTrue(whole.stream().anyMatch(f -> f.severity() == Severity.LEFT_OUT && f.handler() == null), e.getKey() + ": " + whole);
			assertTrue(whole.stream().noneMatch(f -> "recall()V".equals(f.handler())), e.getKey() + ": " + whole);
		}
	}

	/**
	 * ITEM-2: injectors handing state to each other through a static {@code ThreadLocal} of the mod's own classes go
	 * together, whether a handler uses it itself, through the mixin's own method or through the mod's static method, and
	 * the mixin's other injectors stay: Fabric API's {@code RecipeMixin} on Forge 47 and 52, two of whose four hooks into
	 * {@code Recipe.getRemainingItems} have no call to hook there, and whose other two pass a stack's remainder through
	 * {@code RecipeRemainderHandler.REMAINDER_STACK}.
	 */
	@Test
	void aFailingInjectorTakesTheInjectorsSharingAThreadLocalOfTheModWithIt() throws Exception {
		String name = "com/example/mixin/Remainders";
		byte[] mixin = mixin(name, false, cw -> {
			injectWith(cw, "lost", "INVOKE", "Lnet/minecraft/world/Thing;removed()V", mv -> readState(mv, "HOLDER", "Ljava/lang/ThreadLocal;")); // fails
			injectWith(cw, "capture", "HEAD", null, mv -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, STATE_CLASS, "remember", "()V", false));
			injectWith(cw, "store", "INVOKE", "Lnet/minecraft/world/Thing;helper(I)V", mv -> {
				mv.visitVarInsn(Opcodes.ALOAD, 0);
				mv.visitMethodInsn(Opcodes.INVOKESPECIAL, name, "read", "()V", false);
			});
			injectWith(cw, "plain", "HEAD", null, mv -> readState(mv, "enabled", "Z")); // shares only a config's field
			MethodVisitor read = cw.visitMethod(Opcodes.ACC_PRIVATE, "read", "()V", null, null);
			read.visitCode();
			readState(read, "HOLDER", "Ljava/lang/ThreadLocal;");
			read.visitInsn(Opcodes.RETURN);
			read.visitMaxs(1, 1);
			read.visitEnd();
		});
		List<PreflightFinding> findings = run(config(true, "Remainders"), null, Map.of(name, mixin, STATE_CLASS, stateClass()), false);
		assertTrue(findings.stream().allMatch(f -> f.severity() == Severity.LEFT_OUT && f.handler() != null), findings.toString());
		assertEquals(Set.of("lost()V", "capture()V", "store()V"), findings.stream().map(PreflightFinding::handler).collect(Collectors.toSet()),
				"the hook reading only a config's field stays: " + findings);
		assertEquals("The injector of capture()V goes with that of lost()V: it shares the ThreadLocal com.example.State.HOLDER with lost()V, which"
				+ " can't apply (ITEM-2)", findings.stream().filter(f -> f.handler().equals("capture()V")).findFirst().orElseThrow().message());
	}

	/**
	 * ITEM-2: injectors sharing only a plain static field of the mod (a config's) don't go together, nor do injectors that
	 * only read a {@code ThreadLocal} of the mod, a context its other code sets (Fabric API 0.116's fluid render hooks).
	 */
	@Test
	void injectorsSharingAModsPlainStaticFieldOrOnlyReadingAThreadLocalStayApart() throws Exception {
		for (String[] shared : List.of(new String[] {"enabled", "Z"}, new String[] {"HOLDER", "Ljava/lang/ThreadLocal;"})) {
			String name = "com/example/mixin/Configured";
			byte[] mixin = mixin(name, false, cw -> {
				injectWith(cw, "lost", "INVOKE", "Lnet/minecraft/world/Thing;removed()V", mv -> readState(mv, shared[0], shared[1])); // fails
				injectWith(cw, "kept", "INVOKE", "Lnet/minecraft/world/Thing;helper(I)V", mv -> readState(mv, shared[0], shared[1]));
			});
			List<PreflightFinding> findings = run(config(true, "Configured"), null, Map.of(name, mixin, STATE_CLASS, stateClass()), false);
			assertEquals(List.of("lost()V"), findings.stream().map(PreflightFinding::handler).toList(), shared[0] + ": " + findings);
			assertEquals(Severity.LEFT_OUT, findings.getFirst().severity());
		}
	}

	/**
	 * ITEM-2 never disables a mod pre-flight would only have left a hook out of: in a mixin adding an interface, without a
	 * plugin, a failing handler using nothing of the mixin's own is left out alone (LEAVE-3), and of its partners through
	 * a {@code ThreadLocal} the one using nothing of the mixin's own goes with it, where the one going through the mixin's
	 * own method stays, as it did before, the report saying so: its own failure would disable the mod.
	 */
	@Test
	void aThreadLocalPartnerWhoseOwnFailureWouldDisableTheModStays() throws Exception {
		List<PreflightFinding> findings = duckRemainders(1);
		assertTrue(findings.stream().noneMatch(f -> f.severity() == Severity.FAIL), findings.toString());
		assertEquals(Set.of("lost()V", "capture()V"), findings.stream().filter(f -> f.severity() == Severity.LEFT_OUT).map(PreflightFinding::handler)
				.collect(Collectors.toSet()), findings.toString());
		assertEquals(List.of("The injector of store()V shares the ThreadLocal com.example.State.HOLDER with lost()V, which can't apply, but stays:"
				+ " leaving it out would disable the mod (ITEM-2)"), findings.stream().filter(f -> f.severity() == Severity.INFO).map(PreflightFinding::message)
				.toList());
	}

	/** ITEM-2: a partner requiring no injection, whose own failure would only be warned of, goes with the failing handler. */
	@Test
	void aThreadLocalPartnerRequiringNothingGoesWithTheFailingInjector() throws Exception {
		List<PreflightFinding> findings = duckRemainders(0);
		assertTrue(findings.stream().allMatch(f -> f.severity() == Severity.LEFT_OUT), findings.toString());
		assertEquals(Set.of("lost()V", "capture()V", "store()V"), findings.stream().map(PreflightFinding::handler).collect(Collectors.toSet()));
	}

	/**
	 * A required config's mixin adding an interface, without a plugin: {@code lost} fails, reading {@code HOLDER};
	 * {@code capture} sets it through the mod's static method; {@code store}, requiring {@code storeRequires}, reads it
	 * through the mixin's own method.
	 */
	private static List<PreflightFinding> duckRemainders(int storeRequires) throws Exception {
		String name = "com/example/mixin/DuckRemainders";
		byte[] mixin = mixin(name, false, List.of("com/example/api/Duck"), cw -> {
			injectWith(cw, "lost", "INVOKE", "Lnet/minecraft/world/Thing;removed()V", 1, mv -> readState(mv, "HOLDER", "Ljava/lang/ThreadLocal;")); // fails
			injectWith(cw, "capture", "HEAD", null, 1, mv -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, STATE_CLASS, "remember", "()V", false));
			injectWith(cw, "store", "INVOKE", "Lnet/minecraft/world/Thing;helper(I)V", storeRequires, mv -> {
				mv.visitVarInsn(Opcodes.ALOAD, 0);
				mv.visitMethodInsn(Opcodes.INVOKESPECIAL, name, "read", "()V", false);
			});
			MethodVisitor read = cw.visitMethod(Opcodes.ACC_PRIVATE, "read", "()V", null, null);
			read.visitCode();
			readState(read, "HOLDER", "Ljava/lang/ThreadLocal;");
			read.visitInsn(Opcodes.RETURN);
			read.visitMaxs(1, 1);
			read.visitEnd();
		});
		return run(config(true, "DuckRemainders"), null, Map.of(name, mixin, STATE_CLASS, stateClass()), false);
	}

	/**
	 * ITEM-2: a {@code ThreadLocal} is stored into where its own value, on the stack or through a local, is the receiver
	 * of a {@code set}: {@code HOLDER} read into a local before another's is set isn't, nor is {@code OTHER} read for
	 * {@code HOLDER.set(OTHER.get())}.
	 */
	@Test
	void aThreadLocalIsStoredIntoWhereItsOwnValueIsSet() throws Exception {
		Map<Consumer<MethodVisitor>, List<String>> cases = new LinkedHashMap<>();
		cases.put(mv -> { // HOLDER read into a local, OTHER set: HOLDER only read
			mv.visitFieldInsn(Opcodes.GETSTATIC, STATE_CLASS, "HOLDER", "Ljava/lang/ThreadLocal;");
			mv.visitVarInsn(Opcodes.ASTORE, 1);
			mv.visitFieldInsn(Opcodes.GETSTATIC, STATE_CLASS, "OTHER", "Ljava/lang/ThreadLocal;");
			mv.visitInsn(Opcodes.ACONST_NULL);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/ThreadLocal", "set", "(Ljava/lang/Object;)V", false);
		}, List.of("lost()V"));
		cases.put(mv -> { // HOLDER set through a local
			mv.visitFieldInsn(Opcodes.GETSTATIC, STATE_CLASS, "HOLDER", "Ljava/lang/ThreadLocal;");
			mv.visitVarInsn(Opcodes.ASTORE, 1);
			mv.visitVarInsn(Opcodes.ALOAD, 1);
			mv.visitInsn(Opcodes.ACONST_NULL);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/ThreadLocal", "set", "(Ljava/lang/Object;)V", false);
		}, List.of("lost()V", "kept()V"));
		cases.put(mv -> { // HOLDER.set(OTHER.get())
			mv.visitFieldInsn(Opcodes.GETSTATIC, STATE_CLASS, "HOLDER", "Ljava/lang/ThreadLocal;");
			mv.visitFieldInsn(Opcodes.GETSTATIC, STATE_CLASS, "OTHER", "Ljava/lang/ThreadLocal;");
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/ThreadLocal", "get", "()Ljava/lang/Object;", false);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/ThreadLocal", "set", "(Ljava/lang/Object;)V", false);
		}, List.of("lost()V", "kept()V"));
		for (Map.Entry<Consumer<MethodVisitor>, List<String>> c : cases.entrySet()) {
			String name = "com/example/mixin/Stores";
			byte[] mixin = mixin(name, false, cw -> {
				injectWith(cw, "lost", "INVOKE", "Lnet/minecraft/world/Thing;removed()V", 1, mv -> readState(mv, "HOLDER", "Ljava/lang/ThreadLocal;")); // fails
				injectWith(cw, "kept", "INVOKE", "Lnet/minecraft/world/Thing;helper(I)V", 1, c.getKey());
			});
			List<PreflightFinding> findings = run(config(true, "Stores"), null, Map.of(name, mixin, STATE_CLASS, stateClass()), false);
			assertEquals(c.getValue(), findings.stream().map(PreflightFinding::handler).toList(), findings.toString());
		}
	}

	private static final String STATE_CLASS = "com/example/State";

	/**
	 * A class of the mod: static {@code ThreadLocal}s HOLDER and OTHER, a config's static {@code enabled}, and remember(),
	 * which sets HOLDER.
	 */
	private static byte[] stateClass() {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, STATE_CLASS, null, "java/lang/Object", null);
		cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "HOLDER", "Ljava/lang/ThreadLocal;", null, null).visitEnd();
		cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "OTHER", "Ljava/lang/ThreadLocal;", null, null).visitEnd();
		cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "enabled", "Z", null, null).visitEnd();
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "remember", "()V", null, null);
		mv.visitCode();
		mv.visitFieldInsn(Opcodes.GETSTATIC, STATE_CLASS, "HOLDER", "Ljava/lang/ThreadLocal;");
		mv.visitInsn(Opcodes.ACONST_NULL);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/ThreadLocal", "set", "(Ljava/lang/Object;)V", false);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(2, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** Reads a static field of {@link #STATE_CLASS} and drops it. */
	private static void readState(MethodVisitor mv, String field, String desc) {
		mv.visitFieldInsn(Opcodes.GETSTATIC, STATE_CLASS, field, desc);
		mv.visitInsn(Opcodes.POP);
	}

	/** A required {@code @Inject} into {@code tick} whose handler's code is {@code body}, then a return. */
	private static void injectWith(ClassWriter cw, String handler, String atValue, String atTarget, Consumer<MethodVisitor> body) {
		injectWith(cw, handler, atValue, atTarget, 1, body);
	}

	/** An {@code @Inject} into {@code tick} requiring {@code require} whose handler's code is {@code body}, then a return. */
	private static void injectWith(ClassWriter cw, String handler, String atValue, String atTarget, int require, Consumer<MethodVisitor> body) {
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, handler, "()V", null, null);
		AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
		AnnotationVisitor methods = ann.visitArray("method");
		methods.visit(null, "tick");
		methods.visitEnd();
		AnnotationVisitor ats = ann.visitArray("at");
		AnnotationVisitor at = ats.visitAnnotation(null, AT);
		at.visit("value", atValue);
		if (atTarget != null) at.visit("target", atTarget);
		at.visitEnd();
		ats.visitEnd();
		ann.visit("require", require);
		ann.visitEnd();
		mv.visitCode();
		body.accept(mv);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(2, 2);
		mv.visitEnd();
	}

	/** An {@code @Inject} handler with code, using the mixin's {@code neighbor} field where {@code keepsState}. */
	private static void handlerWithCode(ClassWriter cw, String mixin, String handler, String atValue, String atTarget, boolean keepsState) {
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, handler, "()V", null, null);
		AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
		AnnotationVisitor methods = ann.visitArray("method");
		methods.visit(null, "tick");
		methods.visitEnd();
		AnnotationVisitor ats = ann.visitArray("at");
		AnnotationVisitor at = ats.visitAnnotation(null, AT);
		at.visit("value", atValue);
		if (atTarget != null) at.visit("target", atTarget);
		at.visitEnd();
		ats.visitEnd();
		ann.visit("require", 1);
		ann.visitEnd();
		if (keepsState) {
			readNeighbor(mv, mixin);
			return;
		}
		mv.visitCode();
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 1);
		mv.visitEnd();
	}

	/** {@code mv}'s code: reads the mixin's {@code neighbor} field. */
	private static void readNeighbor(MethodVisitor mv, String mixin) {
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitFieldInsn(Opcodes.GETFIELD, mixin, "neighbor", "Ljava/lang/ThreadLocal;");
		mv.visitInsn(Opcodes.POP);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(1, 1);
		mv.visitEnd();
	}

	/** {@code bytes} with the {@code remember} handler calling the mixin's private {@code count()}. */
	private static byte[] callCountFromRemember(byte[] bytes, String mixin) {
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, 0);
		MethodNode remember = node.methods.stream().filter(m -> m.name.equals("remember")).findFirst().orElseThrow();
		InsnList call = new InsnList();
		call.add(new VarInsnNode(Opcodes.ALOAD, 0));
		call.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, mixin, "count", "()V", false));
		remember.instructions.insert(call);
		ClassWriter cw = new ClassWriter(0);
		node.accept(cw);
		return cw.toByteArray();
	}

	/**
	 * An interface mixin whose methods are its handlers, owo's into DataFixerUpper's {@code DataResult}, adds nothing the
	 * mod's code calls; one with an abstract method (an accessor, an invoker, a shadow) may.
	 */
	@Test
	void anInterfaceMixinWithOnlyHandlersIsSelfContained() {
		ClassNode handlers = new ClassNode();
		handlers.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT;
		handlers.methods.add(new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "wrap", "(Ljava/lang/Object;)V", null, null));
		assertTrue(MixinPreflight.selfContained(handlers));
		handlers.methods.add(new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "message", "()Ljava/lang/Object;", null, null));
		assertFalse(MixinPreflight.selfContained(handlers));
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

	/** Polymer's {@code @At(value = "RETURN", ordinal = 1)} in a method with one return failed only once the game ran. */
	@Test
	void aReturnPointsOrdinalCountsTheMethodsReturns() throws Exception {
		String cfg = "{\"required\":true,\"package\":\"com.example.mixin\",\"mixins\":[\"Ret\"],\"injectors\":{\"defaultRequire\":1}}";
		byte[] second = mixin("com/example/mixin/Ret", false, cw -> inject(cw, "h", "tick", "RETURN", null, null, 1));
		List<PreflightFinding> findings = run(cfg, null, Map.of("com/example/mixin/Ret", second), false);
		assertEquals(1, findings.size(), findings.toString());
		assertEquals(Severity.LEFT_OUT, findings.getFirst().severity(), "tick returns once, so there is no second return");
		byte[] first = mixin("com/example/mixin/Ret", false, cw -> inject(cw, "h", "tick", "RETURN", null, null, 0));
		assertTrue(run(cfg, null, Map.of("com/example/mixin/Ret", first), false).isEmpty());
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
		// The messages name the platform's loader (PLAT-3).
		PreflightFinding onNeoForge = run(config(true, "Missing"), null, Map.of("com/example/mixin/Missing", missing), false).getFirst();
		assertTrue(onNeoForge.message().contains("this Minecraft/NeoForge version"), onNeoForge.message());
		PreflightFinding onForge = new MixinPreflight(new ClassIndex(Map.of(TARGET, targetClass())::get), InstalledClasses.NONE,
				Map.of("com/example/mixin/Missing", missing)::get, ClassBytesSource.EMPTY, false, true, ClassBytesSource.EMPTY, "Forge")
				.check("test.mixins.json", MixinConfigFile.parse(config(true, "Missing")), Refmap.EMPTY, Set.of()).getFirst();
		assertTrue(onForge.message().contains("this Minecraft/Forge version"), onForge.message());
		assertTrue(onForge.suggestion().contains("or Forge removed this class"), onForge.suggestion());
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
		// As Mixin's AccessorName: a name all in upper case keeps its case, any other has its first letter lowered. Fabric
		// API's registry sync sets DebugLevelSource's constants through setX_SIDE_LENGTH, which pre-flight read as
		// x_SIDE_LENGTH and so disabled the module (forge-legacy's run on #113).
		assertEquals("X_SIDE_LENGTH", MixinPreflight.inferAccessorName("setX_SIDE_LENGTH"));
		assertEquals("uRLFoo", MixinPreflight.inferAccessorName("getURLFoo"));
		assertEquals("A", MixinPreflight.inferAccessorName("isA"));
		assertNull(MixinPreflight.inferAccessorName("get_foo"));
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
			AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
			at.visit("value", "HEAD");
			at.visitEnd();
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

	private static final String GUI = "net/minecraft/client/gui/Gui";
	private static final String CALLBACK = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
	/** A handler taking the HUD method's parameters (in these tests two objects), then its callback info. */
	private static final String HUD_HOOK = "(Ljava/lang/Object;Ljava/lang/Object;" + CALLBACK + ")V";
	private static final String TWO = "(Ljava/lang/Object;Ljava/lang/Object;)V";

	/**
	 * A GUI as Forge 61 to 66 patch vanilla's ({@code bypass}): its HUD method hands the drawing to Forge's layers behind
	 * {@code if (Boolean.valueOf(true))} and returns, before vanilla's calls, which the layers make themselves, but one.
	 * {@code earlyReturn} returns before the patch's condition too; {@code tickCalls} calls the crosshair from another method.
	 */
	private static Map<String, byte[]> layeredGui(Path dir, boolean bypass, boolean earlyReturn, boolean tickCalls) throws Exception {
		String gui = "package net.minecraft.client.gui; public class Gui { boolean loading; boolean hidden;"
				+ " public void render(Object g, Object dt) {"
				+ (earlyReturn ? " if (this.hidden) return;" : "")
				+ "  if (!this.loading) {"
				+ (bypass ? "   if (Boolean.valueOf(true)) { net.minecraftforge.client.Layers.beginRender(g, dt); return; }" : "")
				+ "   if (!this.hidden) { this.renderCrosshair(g, dt); this.renderChat(g, dt); this.renderTitle(dt, g); this.renderUnlayered(g, dt); }"
				+ "   this.renderStatus(g);"
				+ "  } }"
				+ " public void tick(Object g, Object dt) {" + (tickCalls ? " this.renderCrosshair(g, dt);" : "") + " }"
				+ " public void renderCrosshair(Object g, Object dt) { }"
				+ " public void renderChat(Object g, Object dt) { }"
				+ " public void renderTitle(Object dt, Object g) { }"
				+ " public void renderUnlayered(Object g, Object dt) { }"
				+ " public void renderStatus(Object g) { } }";
		String layers = "package net.minecraftforge.client; import java.util.List; import java.util.function.BiConsumer;"
				+ " public final class Layers { static net.minecraft.client.gui.Gui gui;"
				+ " public static void beginRender(Object g, Object dt) {"
				+ "  List<BiConsumer<Object, Object>> layers = List.of(gui::renderCrosshair, (a, b) -> gui.renderChat(a, b), (a, b) -> gui.renderTitle(b, a),"
				+ "   (a, b) -> gui.renderStatus(a));"
				+ "  for (BiConsumer<Object, Object> layer : layers) layer.accept(g, dt); } }";
		return TestSources.compile(dir, Map.of("net/minecraft/client/gui/Gui.java", gui, "net/minecraftforge/client/Layers.java", layers));
	}

	/** A mixin of {@link #GUI} whose one {@code @Inject} handler {@code hook} goes into {@code method}, at the point {@code at} writes, with what {@code more} adds. */
	private static byte[] guiMixin(String name, String handlerDesc, String method, Consumer<AnnotationVisitor> at, Consumer<AnnotationVisitor> more) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, name, null, "java/lang/Object", null);
		AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor values = m.visitArray("value");
		values.visit(null, Type.getObjectType(GUI));
		values.visitEnd();
		m.visitEnd();
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "hook", handlerDesc, null, null);
		AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
		AnnotationVisitor methods = ann.visitArray("method");
		methods.visit(null, method);
		methods.visitEnd();
		AnnotationVisitor ats = ann.visitArray("at");
		AnnotationVisitor point = ats.visitAnnotation(null, AT);
		at.accept(point);
		point.visitEnd();
		ats.visitEnd();
		if (more != null) more.accept(ann);
		ann.visitEnd();
		mv.visitCode();
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, Type.getArgumentsAndReturnSizes(handlerDesc) >> 2);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** The {@code @Inject} of a mixin's {@code hook} handler. */
	private static AnnotationNode hookInjector(byte[] mixin) {
		ClassNode node = new ClassNode();
		new ClassReader(mixin).accept(node, 0);
		MethodNode handler = node.methods.stream().filter(mm -> mm.name.equals("hook")).findFirst().orElseThrow();
		return Annotations.find(handler, INJECT);
	}

	/** The repairs of {@code mixin} (named Hook) on {@code game}, their notes in {@code notes}. */
	private static Map<String, byte[]> hookRepairs(Map<String, byte[]> game, byte[] mixin, List<PreflightFinding> notes) throws Exception {
		Map<String, byte[]> mod = Map.of("com/example/mixin/Hook", mixin);
		return preflight(new ClassIndex(game::get), mod::get, ClassBytesSource.EMPTY, true)
				.repairs("test.mixins.json", MixinConfigFile.parse(config(true, "Hook")), Refmap.EMPTY, Set.of(), notes);
	}

	private static Consumer<AnnotationVisitor> at(String point) {
		return at -> at.visit("value", point);
	}

	private static Consumer<AnnotationVisitor> atCall(String method, String desc, String shift) {
		return at -> {
			at.visit("value", "INVOKE");
			at.visit("target", "L" + GUI + ";" + method + desc);
			if (shift != null) at.visitEnum("shift", "Lorg/spongepowered/asm/mixin/injection/At$Shift;", shift);
		};
	}

	@Test
	void anInjectionAtTheEndOfAMethodThePatchReturnsBeforeIsPointedAtItsReturns(@TempDir Path dir) throws Exception {
		// HUD-4: malilib's HUD hook (MiniHUD, Tweakeroo and Litematica draw from it) is at the end of Gui.render, which Forge
		// 61 to 66's patch returns before, handing the drawing to its layers: the hook moves to the method's returns.
		Map<String, byte[]> forge = layeredGui(dir.resolve("forge"), true, false, false);
		byte[] tail = guiMixin("com/example/mixin/Hook", HUD_HOOK, "render", at("TAIL"), ann -> {
			AnnotationVisitor slices = ann.visitArray("slice");
			AnnotationVisitor slice = slices.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/Slice;");
			AnnotationVisitor from = slice.visitAnnotation("from", AT);
			from.visit("value", "INVOKE");
			from.visit("target", "L" + GUI + ";renderChat" + TWO);
			from.visitEnd();
			slice.visitEnd();
			slices.visitEnd();
		});
		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = hookRepairs(forge, tail, notes);
		assertEquals(1, notes.size(), notes.toString());
		assertTrue(notes.get(0).message().contains("returns before (it hands the method's work to net.minecraftforge.client.Layers.beginRender"), notes.toString());
		AnnotationNode injector = hookInjector(repaired.get("com/example/mixin/Hook.class"));
		assertEquals("RETURN", Annotations.value(Annotations.list(Annotations.value(injector, "at")).get(0), "value"));
		assertNull(Annotations.value(injector, "slice"), "at the end, the slice only told Mixin where to look");
		Map<String, byte[]> mod = Map.of("com/example/mixin/Hook", repaired.get("com/example/mixin/Hook.class"));
		assertTrue(preflight(new ClassIndex(forge::get), mod::get, ClassBytesSource.EMPTY, true)
				.check("test.mixins.json", MixinConfigFile.parse(config(true, "Hook")), Refmap.EMPTY, Set.of()).isEmpty());

		// Vanilla's method ends where it returns.
		assertTrue(hookRepairs(layeredGui(dir.resolve("vanilla"), false, false, false), tail, new ArrayList<>()).isEmpty());
		// Another return still reached: at the returns, the hook would run where vanilla's end doesn't.
		assertTrue(hookRepairs(layeredGui(dir.resolve("early"), true, true, false), tail, new ArrayList<>()).isEmpty());
		// Locals differ at the patch's return (the capture is only softened).
		byte[] capturing = guiMixin("com/example/mixin/Hook", HUD_HOOK, "render", at("TAIL"),
				ann -> ann.visitEnum("locals", "Lorg/spongepowered/asm/mixin/injection/callback/LocalCapture;", "CAPTURE_FAILHARD"));
		AnnotationNode softened = hookInjector(hookRepairs(forge, capturing, new ArrayList<>()).get("com/example/mixin/Hook.class"));
		assertEquals("TAIL", Annotations.value(Annotations.list(Annotations.value(softened, "at")).get(0), "value"));
	}

	@Test
	void anInjectionAtACallInCodeThePatchSkipsIsPointedAtTheCalledMethodWhereThePlatformMakesTheCall(@TempDir Path dir) throws Exception {
		// HUD-4: a hook at one of vanilla's calls in Gui.render never ran on Forge 61 to 66, whose layers make the call.
		Map<String, byte[]> forge = layeredGui(dir.resolve("forge"), true, false, false);
		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = hookRepairs(forge, guiMixin("com/example/mixin/Hook", HUD_HOOK, "render", atCall("renderCrosshair", TWO, null), null), notes);
		assertEquals(1, notes.size(), notes.toString());
		assertTrue(notes.get(0).message().contains("which net.minecraftforge.client.Layers makes itself; the injection point is pointed at the start of renderCrosshair"),
				notes.toString());
		AnnotationNode injector = hookInjector(repaired.get("com/example/mixin/Hook.class"));
		assertEquals(List.of("renderCrosshair" + TWO), Annotations.value(injector, "method"));
		AnnotationNode at = Annotations.list(Annotations.value(injector, "at")).get(0);
		assertEquals("HEAD", Annotations.value(at, "value"));
		assertNull(Annotations.value(at, "target"));
		Map<String, byte[]> mod = Map.of("com/example/mixin/Hook", repaired.get("com/example/mixin/Hook.class"));
		assertTrue(preflight(new ClassIndex(forge::get), mod::get, ClassBytesSource.EMPTY, true)
				.check("test.mixins.json", MixinConfigFile.parse(config(true, "Hook")), Refmap.EMPTY, Set.of()).isEmpty());

		// After the call (a layer's lambda makes it): the called method's returns.
		AnnotationNode after = hookInjector(hookRepairs(forge, guiMixin("com/example/mixin/Hook", HUD_HOOK, "render", atCall("renderChat", TWO, "AFTER"), null),
				new ArrayList<>()).get("com/example/mixin/Hook.class"));
		assertEquals(List.of("renderChat" + TWO), Annotations.value(after, "method"));
		AnnotationNode afterAt = Annotations.list(Annotations.value(after, "at")).get(0);
		assertEquals("RETURN", Annotations.value(afterAt, "value"));
		assertNull(Annotations.value(afterAt, "shift"));
		// A handler that takes nothing but its callback info moves to a method taking other parameters.
		AnnotationNode status = hookInjector(hookRepairs(forge, guiMixin("com/example/mixin/Hook", "(" + CALLBACK + ")V", "render",
				atCall("renderStatus", "(Ljava/lang/Object;)V", null), null), new ArrayList<>()).get("com/example/mixin/Hook.class"));
		assertEquals(List.of("renderStatus(Ljava/lang/Object;)V"), Annotations.value(status, "method"));

		// Left as they are: the parameters there differ, or are passed in another order; no layer makes the call; cancelling
		// skipped the rest of the HUD; the call is made elsewhere too; vanilla's code runs.
		assertTrue(hookRepairs(forge, guiMixin("com/example/mixin/Hook", HUD_HOOK, "render", atCall("renderStatus", "(Ljava/lang/Object;)V", null), null),
				new ArrayList<>()).isEmpty());
		assertTrue(hookRepairs(forge, guiMixin("com/example/mixin/Hook", HUD_HOOK, "render", atCall("renderTitle", TWO, null), null), new ArrayList<>()).isEmpty());
		assertTrue(hookRepairs(forge, guiMixin("com/example/mixin/Hook", HUD_HOOK, "render", atCall("renderUnlayered", TWO, null), null), new ArrayList<>()).isEmpty());
		assertTrue(hookRepairs(forge, guiMixin("com/example/mixin/Hook", HUD_HOOK, "render", atCall("renderCrosshair", TWO, null), ann -> ann.visit("cancellable", true)),
				new ArrayList<>()).isEmpty());
		byte[] crosshair = guiMixin("com/example/mixin/Hook", HUD_HOOK, "render", atCall("renderCrosshair", TWO, null), null);
		assertTrue(hookRepairs(layeredGui(dir.resolve("ticking"), true, false, true), crosshair, new ArrayList<>()).isEmpty());
		assertTrue(hookRepairs(layeredGui(dir.resolve("vanilla"), false, false, false), crosshair, new ArrayList<>()).isEmpty());
	}

	@Test
	void anInjectionAtTheStartOrEndOfAMethodThePlatformNeverCallsIsPointedAtFabricatedsSeam(@TempDir Path dir) throws Exception {
		// HUD-4: Forge 47's GUI overrides Gui.render and never calls it; Fabricated's mixins add seams for its start and
		// end, called where the override starts and ends.
		String desc = "(Ljava/lang/Object;F)V";
		String hook = "(Ljava/lang/Object;F" + CALLBACK + ")V";
		String seams = " public void fabricated$start$render(Object g, float pt) { } public void fabricated$end$render(Object g, float pt) { }";
		Function<Boolean, String> gui = withSeams -> "package net.minecraft.client.gui; public class Gui {"
				+ " public void render(Object g, float pt) { this.renderHotbar(g); } public void renderHotbar(Object g) { }" + (withSeams ? seams : "") + " }";
		Map<String, byte[]> forge = TestSources.compile(dir.resolve("forge"), Map.of("net/minecraft/client/gui/Gui.java", gui.apply(true)));

		List<PreflightFinding> notes = new ArrayList<>();
		byte[] head = guiMixin("com/example/mixin/Hook", hook, "render", at("HEAD"), ann -> ann.visit("cancellable", true));
		AnnotationNode start = hookInjector(hookRepairs(forge, head, notes).get("com/example/mixin/Hook.class"));
		assertEquals(1, notes.size(), notes.toString());
		assertTrue(notes.get(0).message().contains("pointed at Fabricated's seam fabricated$start$render, which runs where that override starts"), notes.toString());
		assertEquals(List.of("fabricated$start$render" + desc), Annotations.value(start, "method"));
		assertEquals("HEAD", Annotations.value(Annotations.list(Annotations.value(start, "at")).get(0), "value"));
		for (String end : List.of("TAIL", "RETURN")) {
			Map<String, byte[]> repaired = hookRepairs(forge, guiMixin("com/example/mixin/Hook", hook, "render", at(end), null), new ArrayList<>());
			AnnotationNode injector = hookInjector(repaired.get("com/example/mixin/Hook.class"));
			assertEquals(List.of("fabricated$end$render" + desc), Annotations.value(injector, "method"), end);
			assertEquals("RETURN", Annotations.value(Annotations.list(Annotations.value(injector, "at")).get(0), "value"), end);
			Map<String, byte[]> mod = Map.of("com/example/mixin/Hook", repaired.get("com/example/mixin/Hook.class"));
			assertTrue(preflight(new ClassIndex(forge::get), mod::get, ClassBytesSource.EMPTY, true)
					.check("test.mixins.json", MixinConfigFile.parse(config(true, "Hook")), Refmap.EMPTY, Set.of()).isEmpty(), end);
		}
		// A call inside the method has no seam; nor has a method without them.
		byte[] call = guiMixin("com/example/mixin/Hook", hook, "render", at -> {
			at.visit("value", "INVOKE");
			at.visit("target", "L" + GUI + ";renderHotbar(Ljava/lang/Object;)V");
		}, null);
		assertTrue(hookRepairs(forge, call, new ArrayList<>()).isEmpty());
		Map<String, byte[]> vanilla = TestSources.compile(dir.resolve("vanilla"), Map.of("net/minecraft/client/gui/Gui.java", gui.apply(false)));
		assertTrue(hookRepairs(vanilla, head, new ArrayList<>()).isEmpty());
	}

	/** Pre-flight with no installed jars, and missing targets fatal unless said otherwise. */
	private static MixinPreflight preflight(ClassIndex game, ClassBytesSource mod, ClassBytesSource untransformable, boolean clientSide) {
		return preflight(game, mod, untransformable, clientSide, true);
	}

	private static MixinPreflight preflight(ClassIndex game, ClassBytesSource mod, ClassBytesSource untransformable, boolean clientSide,
			boolean missingTargetsFatal) {
		return new MixinPreflight(game, InstalledClasses.NONE, mod, untransformable, clientSide, missingTargetsFatal, ClassBytesSource.EMPTY);
	}
}
