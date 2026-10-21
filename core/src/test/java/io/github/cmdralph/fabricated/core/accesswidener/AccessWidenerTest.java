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
		AccessTransformerConverter.Result result = AccessTransformerConverter.convert(file, game);
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
		AccessTransformerConverter.Result result = AccessTransformerConverter.convert(file, null);
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

	@Test
	void keepsFieldsNeoForgeCoremodsNeedPrivate() throws Exception {
		AccessWidenerFile file = AccessWidenerParser.parse("""
				accessWidener v2 named
				accessible field net/minecraft/world/level/biome/Biome climateSettings Lnet/minecraft/world/level/biome/Biome$ClimateSettings;
				mutable field net/minecraft/world/level/biome/Biome specialEffects Lnet/minecraft/world/level/biome/BiomeSpecialEffects;
				""");
		AccessTransformerConverter.Result result = AccessTransformerConverter.convert(file, null);
		assertTrue(!result.accessTransformer().contains("climateSettings"), result.accessTransformer());
		assertTrue(result.accessTransformer().contains("private-f net.minecraft.world.level.biome.Biome specialEffects"), result.accessTransformer());
		assertEquals(1, result.warnings().size());
		assertTrue(result.warnings().getFirst().contains("must stay private"));
	}
}
