package io.github.cmdralph.fabricated.core.accesswidener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;

class AccessWidenerTest {
	private static final Map<String, String> NEOFORGE = AccessTransformerConverter.NEOFORGE_PRIVATE_FIELDS;

	@Test
	void parsesV1V2AndClassTweakers() throws Exception {
		AccessWidenerFile v1 = AccessWidenerParser.parse("""
				accessWidener v1 named
				# comment
				accessible class net/minecraft/A   # trailing
				accessible method net/minecraft/A foo (I)V
				mutable field net/minecraft/A bar I
				""");
		assertEquals("named", v1.namespace());
		assertEquals(3, v1.entries().size());

		AccessWidenerFile v2 = AccessWidenerParser.parse("accessWidener v2 intermediary\ntransitive-extendable class net/minecraft/class_1\n");
		assertTrue(v2.entries().getFirst().transitive());

		AccessWidenerFile ct = AccessWidenerParser.parse("""
				classTweaker v1 official
				inject-interface net/minecraft/A com/example/Iface<Lnet/minecraft/B;>
				transitive-inject-interface net/minecraft/A com/example/Other
				""");
		assertEquals(2, ct.injectedInterfaces().size());
		assertEquals("com/example/Iface", ct.injectedInterfaces().getFirst().interfaceName());
		assertEquals("com/example/Iface<Lnet/minecraft/B;>", ct.injectedInterfaces().getFirst().genericSignature());

		AccessWidenerFile ct2 = AccessWidenerParser.parse("""
				classTweaker	v2	official
				accessible field net/minecraft/A bar I
				extend-enum net/minecraft/E NEW_ONE
				transitive-extend-enum net/minecraft/E NEW_TWO
				""");
		assertEquals(1, ct2.entries().size());
		assertEquals(List.of("NEW_ONE", "NEW_TWO"), ct2.enumExtensions().stream().map(AccessWidenerFile.EnumExtension::constant).toList());
		assertTrue(ct2.enumExtensions().getLast().transitive());
	}

	@Test
	void rejectsMalformedFiles() {
		assertThrows(AccessWidenerException.class, () -> AccessWidenerParser.parse("accessWidener v3 named\n"));
		assertThrows(AccessWidenerException.class, () -> AccessWidenerParser.parse("nonsense\n"));
		assertThrows(AccessWidenerException.class, () -> AccessWidenerParser.parse("accessWidener v1 named\naccessible class a.b.C\n"));
		assertThrows(AccessWidenerException.class, () -> AccessWidenerParser.parse("accessWidener v1 named\nmutable class a/b/C\n"));
		assertThrows(AccessWidenerException.class, () -> AccessWidenerParser.parse("accessWidener v1 named\naccessible method a/b/C foo\n"));
		assertThrows(AccessWidenerException.class, () -> AccessWidenerParser.parse("accessWidener v2 named\n accessible class a/b/C\n"));
		assertThrows(AccessWidenerException.class, () -> AccessWidenerParser.parse("accessWidener v1 named\ninject-interface a/B c/D\n"));
		assertThrows(AccessWidenerException.class, () -> AccessWidenerParser.parse("classTweaker v3 official\n"));
		assertThrows(AccessWidenerException.class, () -> AccessWidenerParser.parse("classTweaker v1 official\nextend-enum a/E X\n"));
	}

	@Test
	void convertsToAccessTransformersWithFabricSemantics() throws Exception {
		AccessWidenerFile file = AccessWidenerParser.parse("""
				accessWidener v2 named
				accessible class net/minecraft/A$Inner
				extendable class net/minecraft/A
				accessible method net/minecraft/A privateMethod ()V
				accessible method net/minecraft/A staticPrivate ()V
				extendable method net/minecraft/A finalMethod ()V
				accessible field net/minecraft/A count I
				mutable field net/minecraft/A count I
				mutable field net/minecraft/A constant J
				accessible method net/minecraft/A missing ()V
				accessible class net/minecraft/Missing
				""");
		ClassIndex game = new ClassIndex(Map.of(
				"net/minecraft/A", gameClass(),
				"net/minecraft/A$Inner", emptyGameClass("net/minecraft/A$Inner"))::get);
		AccessTransformerConverter.Result result = AccessTransformerConverter.convert(file, game, NEOFORGE, "NeoForge");
		List<String> lines = result.accessTransformer().lines().filter(l -> !l.startsWith("#")).toList();
		assertEquals(List.of(
				"public-f net.minecraft.A",
				"private-f net.minecraft.A constant",
				"public-f net.minecraft.A count",
				"protected-f net.minecraft.A finalMethod()V",
				"public net.minecraft.A privateMethod()V",
				"public net.minecraft.A staticPrivate()V",
				"public net.minecraft.A$Inner"), lines);
		assertEquals(2, result.warnings().size(), result.warnings().toString());
	}

	@Test
	void convertsWithoutGameIndex() throws Exception {
		AccessWidenerFile file = AccessWidenerParser.parse("accessWidener v1 named\naccessible method a/B m ()V\n");
		AccessTransformerConverter.Result result = AccessTransformerConverter.convert(file, null, NEOFORGE, "NeoForge");
		assertTrue(result.accessTransformer().contains("public a.B m()V"));
		assertTrue(result.accessTransformer().contains("public a.B\n"));
	}

	private static byte[] gameClass() {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "net/minecraft/A", null, "java/lang/Object", null);
		cw.visitMethod(Opcodes.ACC_PRIVATE, "privateMethod", "()V", null, null).visitEnd();
		cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "staticPrivate", "()V", null, null).visitEnd();
		cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "finalMethod", "()V", null, null).visitEnd();
		cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "count", "I", null, null).visitEnd();
		cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "constant", "J", null, null).visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static byte[] emptyGameClass(String name) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PRIVATE, name, null, "java/lang/Object", null);
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * Forge's transformer throws unless the fields it redirects to getters are private, and Forge's list isn't NeoForge's:
	 * a fluid mod's widened {@code BucketItem.content} stays private on Forge and is widened on NeoForge, and the
	 * warnings name the platform's loader (PLAT-4).
	 */
	@Test
	void keepsTheFieldsOfThePlatformsOwnCoremodPrivate() throws Exception {
		AccessWidenerFile file = AccessWidenerParser.parse("""
				accessWidener v2 named
				accessible field net/minecraft/world/item/BucketItem content Lnet/minecraft/world/level/material/Fluid;
				accessible class net/minecraft/Missing
				""");
		ClassIndex game = new ClassIndex(Map.of("net/minecraft/world/item/BucketItem", bucketItem())::get);
		AccessTransformerConverter.Result onForge = AccessTransformerConverter.convert(file, game,
				AccessTransformerConverter.FORGE_PRIVATE_FIELDS, "Forge");
		assertTrue(!onForge.accessTransformer().contains("content"), onForge.accessTransformer());
		assertEquals(List.of(
				"line 2: field net/minecraft/world/item/BucketItem.content must stay private on Forge (a Forge coremod replaces it with a getter); "
						+ "not widened. Code reading it directly fails when it runs.",
				"line 3: class net/minecraft/Missing does not exist on this Minecraft/Forge version; ignored"), onForge.warnings());
		AccessTransformerConverter.Result onNeoForge = AccessTransformerConverter.convert(file, game, NEOFORGE, "NeoForge");
		assertTrue(onNeoForge.accessTransformer().contains("public net.minecraft.world.item.BucketItem content"), onNeoForge.accessTransformer());
	}

	private static byte[] bucketItem() {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "net/minecraft/world/item/BucketItem", null, "java/lang/Object", null);
		cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "content", "Lnet/minecraft/world/level/material/Fluid;", null, null).visitEnd();
		cw.visitMethod(Opcodes.ACC_PUBLIC, "getFluid", "()Lnet/minecraft/world/level/material/Fluid;", null, null).visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	@Test
	void keepsFieldsNeoForgeCoremodsNeedPrivate() throws Exception {
		AccessWidenerFile file = AccessWidenerParser.parse("""
				accessWidener v2 named
				accessible field net/minecraft/world/level/biome/Biome climateSettings Lnet/minecraft/world/level/biome/Biome$ClimateSettings;
				mutable field net/minecraft/world/level/biome/Biome specialEffects Lnet/minecraft/world/level/biome/BiomeSpecialEffects;
				""");
		AccessTransformerConverter.Result result = AccessTransformerConverter.convert(file, null, NEOFORGE, "NeoForge");
		assertTrue(!result.accessTransformer().contains("climateSettings"), result.accessTransformer());
		assertTrue(result.accessTransformer().contains("private-f net.minecraft.world.level.biome.Biome specialEffects"), result.accessTransformer());
		assertEquals(1, result.warnings().size());
		assertTrue(result.warnings().getFirst().contains("must stay private"));
	}

	/**
	 * A field stays private only where NeoForge's patches add the getter its coremod reads it through: NeoForge 21.11,
	 * 26.1 and 26.2 leave {@code Biome.attributes} a plain field, which Fabric API's biome module widens (BIOME-2).
	 */
	@Test
	void widensAFieldNeoForgeLeavesPlain() throws Exception {
		String biome = "net/minecraft/world/level/biome/Biome";
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, biome, null, "java/lang/Object", null);
		cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "climateSettings", "Lnet/minecraft/world/level/biome/Biome$ClimateSettings;", null, null).visitEnd();
		cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "attributes", "Lnet/minecraft/world/attribute/EnvironmentAttributeMap;", null, null).visitEnd();
		cw.visitMethod(Opcodes.ACC_PUBLIC, "getModifiedClimateSettings", "()Lnet/minecraft/world/level/biome/Biome$ClimateSettings;", null, null).visitEnd();
		cw.visitEnd();
		ClassIndex game = new ClassIndex(Map.of(biome, cw.toByteArray())::get);
		AccessWidenerFile file = AccessWidenerParser.parse("""
				accessWidener v2 named
				accessible field net/minecraft/world/level/biome/Biome climateSettings Lnet/minecraft/world/level/biome/Biome$ClimateSettings;
				accessible field net/minecraft/world/level/biome/Biome attributes Lnet/minecraft/world/attribute/EnvironmentAttributeMap;
				mutable field net/minecraft/world/level/biome/Biome attributes Lnet/minecraft/world/attribute/EnvironmentAttributeMap;
				""");
		AccessTransformerConverter.Result result = AccessTransformerConverter.convert(file, game, NEOFORGE, "NeoForge");
		assertTrue(result.accessTransformer().contains("public-f net.minecraft.world.level.biome.Biome attributes"), result.accessTransformer());
		assertTrue(!result.accessTransformer().contains("climateSettings"), "its coremod's getter is there: " + result.accessTransformer());
		assertEquals(1, result.warnings().size(), result.warnings().toString());
	}
}
