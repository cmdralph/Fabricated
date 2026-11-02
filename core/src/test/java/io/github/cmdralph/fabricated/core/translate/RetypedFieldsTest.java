package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.core.accesswidener.AccessTransformerConverter;
import io.github.cmdralph.fabricated.core.accesswidener.AccessWidenerParser;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;

/**
 * CORPUS-33: Forge 61 to 66 type {@code ChunkGenerator.featuresPerStep} as a {@code ClearableLazy}, vanilla's as a
 * {@code Supplier}. Lithostitched's accessor of it, and Fabric API's biome module writing it, must reach Forge's field.
 */
class RetypedFieldsTest {
	private static final String GENERATOR = "net/minecraft/world/level/chunk/ChunkGenerator";
	private static final String SUPPLIER = "Ljava/util/function/Supplier;";
	private static final String LAZY = "Lnet/minecraftforge/common/util/ClearableLazy;";
	private static final String ACCESSOR = "com/example/mixin/ChunkGeneratorAccessor";
	private static final String SHADOWING = "com/example/mixin/ChunkGeneratorMixin";

	private static byte[] generator(String fieldDesc) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, GENERATOR, null, "java/lang/Object", null);
		cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "featuresPerStep", fieldDesc, null, null).visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static ClassIndex game(String fieldDesc) {
		return new ClassIndex(Map.of(GENERATOR, generator(fieldDesc))::get);
	}

	/** {@code @Mixin(ChunkGenerator.class) interface ChunkGeneratorAccessor}: a getter by its value, a setter by its name. */
	private static byte[] accessor() {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, ACCESSOR, null, "java/lang/Object", null);
		AnnotationVisitor mixin = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor targets = mixin.visitArray("value");
		targets.visit(null, Type.getObjectType(GENERATOR));
		targets.visitEnd();
		mixin.visitEnd();
		MethodVisitor get = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "fabricated$features", "()" + SUPPLIER,
				"()Ljava/util/function/Supplier<Ljava/util/List<Ljava/lang/Object;>;>;", null);
		AnnotationVisitor a = get.visitAnnotation("Lorg/spongepowered/asm/mixin/gen/Accessor;", false);
		a.visit("value", "featuresPerStep");
		a.visitEnd();
		get.visitEnd();
		MethodVisitor set = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "setFeaturesPerStep", "(" + SUPPLIER + ")V", null, null);
		set.visitAnnotation("Lorg/spongepowered/asm/mixin/gen/Accessor;", false).visitEnd();
		set.visitAnnotation("Lorg/spongepowered/asm/mixin/Mutable;", false).visitEnd();
		set.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** {@code @Mixin(ChunkGenerator.class) class ChunkGeneratorMixin { @Shadow Supplier featuresPerStep; void reset(Supplier s) { featuresPerStep = s; } }}. */
	private static byte[] shadowing() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, SHADOWING, null, "java/lang/Object", null);
		AnnotationVisitor mixin = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor targets = mixin.visitArray("value");
		targets.visit(null, Type.getObjectType(GENERATOR));
		targets.visitEnd();
		mixin.visitEnd();
		org.objectweb.asm.FieldVisitor field = cw.visitField(Opcodes.ACC_PRIVATE, "featuresPerStep", SUPPLIER, null, null);
		field.visitAnnotation("Lorg/spongepowered/asm/mixin/Shadow;", false).visitEnd();
		field.visitEnd();
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "reset", "(" + SUPPLIER + ")V", null, null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitFieldInsn(Opcodes.PUTFIELD, SHADOWING, "featuresPerStep", SUPPLIER);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** Fabric API's biome module, as it writes the field: {@code generator.featuresPerStep = supplier}, and reads it. */
	private static byte[] writer() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/BiomeModifications", null, "java/lang/Object", null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "finish", "(L" + GENERATOR + ";" + SUPPLIER + ")Ljava/lang/Object;", null, null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitFieldInsn(Opcodes.PUTFIELD, GENERATOR, "featuresPerStep", SUPPLIER);
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitFieldInsn(Opcodes.GETFIELD, GENERATOR, "featuresPerStep", SUPPLIER);
		mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/function/Supplier", "get", "()Ljava/lang/Object;", true);
		mv.visitInsn(Opcodes.ARETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** A class of the mod calling the accessor, which doesn't name the field: get, then set, then a method reference to the getter. */
	private static byte[] caller(boolean setterReference) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/Features", null, "java/lang/Object", null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "swap", "(Ljava/lang/Object;)Ljava/lang/Object;", null, null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitTypeInsn(Opcodes.CHECKCAST, ACCESSOR);
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitTypeInsn(Opcodes.CHECKCAST, ACCESSOR);
		mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, ACCESSOR, "fabricated$features", "()" + SUPPLIER, true);
		mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, ACCESSOR, "setFeaturesPerStep", "(" + SUPPLIER + ")V", true);
		Handle metafactory = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
				"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;"
						+ "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
		if (setterReference) {
			mv.visitInvokeDynamicInsn("accept", "()Ljava/util/function/BiConsumer;", metafactory, Type.getType("(Ljava/lang/Object;Ljava/lang/Object;)V"),
					new Handle(Opcodes.H_INVOKEINTERFACE, ACCESSOR, "setFeaturesPerStep", "(" + SUPPLIER + ")V", true),
					Type.getType("(L" + ACCESSOR + ";" + SUPPLIER + ")V"));
		} else {
			mv.visitInvokeDynamicInsn("apply", "()Ljava/util/function/Function;", metafactory, Type.getType("(Ljava/lang/Object;)Ljava/lang/Object;"),
					new Handle(Opcodes.H_INVOKEINTERFACE, ACCESSOR, "fabricated$features", "()" + SUPPLIER, true),
					Type.getType("(L" + ACCESSOR + ";)" + SUPPLIER));
		}
		mv.visitInsn(Opcodes.ARETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static final String REFMAP = "{\"mappings\":{\"" + ACCESSOR + "\":{\"featuresPerStep\":\"featuresPerStep:" + SUPPLIER + "\"},"
			+ "\"" + SHADOWING + "\":{\"getFeatures\":\"L" + GENERATOR + ";featuresPerStep:" + SUPPLIER + "\"}}}";

	private static Map<String, byte[]> mod(boolean setterReference) {
		Map<String, byte[]> files = new TreeMap<>();
		files.put(ACCESSOR + ".class", accessor());
		files.put(SHADOWING + ".class", shadowing());
		files.put("com/example/BiomeModifications.class", writer());
		files.put("com/example/Features.class", caller(setterReference));
		files.put("example.refmap.json", REFMAP.getBytes(StandardCharsets.UTF_8));
		return files;
	}

	private static List<String> apply(ClassIndex game, Map<String, byte[]> files, Map<String, byte[]> out) throws Exception {
		return RetypedFields.of(game).apply(out, List.copyOf(files.keySet()), Set.of("example.refmap.json"), files::get);
	}

	private static ClassNode node(byte[] data) {
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, 0);
		return node;
	}

	private static MethodNode method(ClassNode node, String name) {
		return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
	}

	@Test
	void modsReachForgesRetypedFieldWithForgesType() throws Exception {
		Map<String, byte[]> files = mod(false);
		Map<String, byte[]> out = new HashMap<>();
		List<String> notes = apply(game(LAZY), files, out);
		assertFalse(notes.isEmpty());

		// A write makes the mod's value Forge's lazy, as Forge makes its own; a read takes Forge's as it is.
		List<AbstractInsnNode> code = List.of(method(node(out.get("com/example/BiomeModifications.class")), "finish").instructions.toArray());
		MethodInsnNode wrap = (MethodInsnNode) code.stream().filter(i -> i instanceof MethodInsnNode m && m.name.equals("concurrentOf")).findFirst().orElseThrow();
		assertEquals("net/minecraftforge/common/util/ClearableLazy", wrap.owner);
		assertEquals("(" + SUPPLIER + ")" + LAZY, wrap.desc);
		assertTrue(wrap.itf);
		assertEquals(Opcodes.PUTFIELD, wrap.getNext().getOpcode());
		assertTrue(code.stream().filter(i -> i instanceof FieldInsnNode).allMatch(i -> ((FieldInsnNode) i).desc.equals(LAZY)));

		// The accessor's methods take Forge's type, and lose the generic signature of vanilla's.
		ClassNode accessor = node(out.get(ACCESSOR + ".class"));
		assertEquals("()" + LAZY, method(accessor, "fabricated$features").desc);
		assertNull(method(accessor, "fabricated$features").signature);
		assertEquals("(" + LAZY + ")V", method(accessor, "setFeaturesPerStep").desc);

		// Its callers call those, the setter's value made Forge's type; a method reference to the getter names it too.
		List<AbstractInsnNode> calls = List.of(method(node(out.get("com/example/Features.class")), "swap").instructions.toArray());
		List<MethodInsnNode> invokes = calls.stream().filter(i -> i instanceof MethodInsnNode).map(i -> (MethodInsnNode) i).toList();
		assertEquals(List.of("fabricated$features", "concurrentOf", "setFeaturesPerStep"), invokes.stream().map(m -> m.name).toList());
		assertEquals("()" + LAZY, invokes.get(0).desc);
		assertEquals("(" + LAZY + ")V", invokes.get(2).desc);
		Handle ref = (Handle) calls.stream().filter(i -> i instanceof org.objectweb.asm.tree.InvokeDynamicInsnNode).map(i -> ((org.objectweb.asm.tree.InvokeDynamicInsnNode) i).bsmArgs[1])
				.findFirst().orElseThrow();
		assertEquals("()" + LAZY, ref.getDesc());

		// A shadow of the field has Forge's type, and the mixin's write wraps the value.
		ClassNode shadowing = node(out.get(SHADOWING + ".class"));
		FieldNode shadow = shadowing.fields.getFirst();
		assertEquals(LAZY, shadow.desc);
		List<AbstractInsnNode> reset = List.of(method(shadowing, "reset").instructions.toArray());
		assertTrue(reset.stream().anyMatch(i -> i instanceof MethodInsnNode m && m.name.equals("concurrentOf")));
		assertTrue(reset.stream().filter(i -> i instanceof FieldInsnNode).allMatch(i -> ((FieldInsnNode) i).desc.equals(LAZY)));

		// The accessor's refmap entry names Forge's type; an injection point's entry, with the owner, stays.
		String refmap = new String(out.get("example.refmap.json"), StandardCharsets.UTF_8);
		assertTrue(refmap.contains("\"featuresPerStep\":\"featuresPerStep:" + LAZY + "\""), refmap);
		assertTrue(refmap.contains("\"getFeatures\":\"L" + GENERATOR + ";featuresPerStep:" + SUPPLIER + "\""), refmap);
	}

	@Test
	void anAccessorSetterAMethodReferenceNamesKeepsVanillasType() throws Exception {
		Map<String, byte[]> files = mod(true);
		Map<String, byte[]> out = new HashMap<>();
		List<String> notes = apply(game(LAZY), files, out);
		assertTrue(notes.stream().anyMatch(n -> n.contains("setFeaturesPerStep") && n.contains("method reference")), notes.toString());
		// The mixin's accessors keep vanilla's type, and their refmap entry too; the rest is adapted.
		assertNull(out.get(ACCESSOR + ".class"));
		assertNull(out.get("example.refmap.json"));
		assertNotNull(out.get("com/example/BiomeModifications.class"));
	}

	@Test
	void nothingChangesWhereThePlatformKeepsVanillasType() throws Exception {
		Map<String, byte[]> out = new HashMap<>();
		assertTrue(apply(game(SUPPLIER), mod(false), out).isEmpty());
		assertTrue(out.isEmpty());
		assertTrue(RetypedFields.of(game(SUPPLIER)).isEmpty());
	}

	@Test
	void anAccessWidenerLineForTheFieldWidensForgesField() throws Exception {
		String aw = "accessWidener v2 named\naccessible field " + GENERATOR + " featuresPerStep " + SUPPLIER + "\nmutable field " + GENERATOR
				+ " featuresPerStep " + SUPPLIER + "\n";
		AccessTransformerConverter.Result forge = AccessTransformerConverter.convert(AccessWidenerParser.parse(aw), game(LAZY), Map.of(), "Forge");
		assertTrue(forge.warnings().isEmpty(), forge.warnings().toString());
		assertTrue(forge.accessTransformer().contains("public-f net.minecraft.world.level.chunk.ChunkGenerator featuresPerStep"), forge.accessTransformer());
		AccessTransformerConverter.Result other = AccessTransformerConverter.convert(AccessWidenerParser.parse(aw.replace(SUPPLIER, "Ljava/lang/Object;")),
				game(LAZY), Map.of(), "Forge");
		assertNotNull(other.warnings());
		assertFalse(other.warnings().isEmpty());
	}
}
