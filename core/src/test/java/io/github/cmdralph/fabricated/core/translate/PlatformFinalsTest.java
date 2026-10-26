package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;

class PlatformFinalsTest {
	private static final String GAME = "net/minecraft/world/item/crafting/Ingredient";
	private static final String OTHER = "net/minecraft/world/item/Item";

	@Test
	void aGameMethodTheModOverridesIsFoundWhereOnlyThePlatformDeclaresItFinal() throws Exception {
		// FINAL-1: Forge 47 makes Ingredient.toNetwork final; Fabric API's recipe API overrides it, as vanilla's isn't.
		ClassIndex game = new ClassIndex(Map.of(
				GAME, gameClass(GAME, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, Opcodes.ACC_PROTECTED | Opcodes.ACC_FINAL),
				OTHER, gameClass(OTHER, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, Opcodes.ACC_PUBLIC))::get);
		Map<String, byte[]> vanilla = Map.of(
				GAME, gameClass(GAME, Opcodes.ACC_PUBLIC, Opcodes.ACC_PROTECTED),
				OTHER, gameClass(OTHER, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, Opcodes.ACC_PUBLIC));
		Map<String, byte[]> mod = Map.of(
				"com/example/CustomIngredient.class", modClass("com/example/CustomIngredient", GAME, "toNetwork", "check"),
				// Overrides through another of the mod's classes, which declares nothing itself.
				"com/example/Base.class", modClass("com/example/Base", GAME),
				"com/example/Derived.class", modClass("com/example/Derived", "com/example/Base", "check"),
				// Final in vanilla too: the mod is as broken on Fabric, and nothing changes.
				"com/example/CustomItem.class", modClass("com/example/CustomItem", OTHER, "toNetwork"),
				"fabric.mod.json", new byte[0]);
		List<String> notes = new ArrayList<>();
		Set<String> lines = ModTranslator.platformFinals(mod.keySet(), mod::get, game, vanilla::get, "Forge", notes);

		assertEquals(Set.of(GAME + " toNetwork(Ljava/lang/Object;)V", GAME + " check(I)Z"), lines);
		assertEquals(2, notes.size(), notes.toString());
		assertTrue(notes.stream().allMatch(n -> n.contains("which Forge declares final and vanilla doesn't")), notes.toString());

		// Without vanilla's code nothing tells the platform made it final.
		assertEquals(Set.of(), ModTranslator.platformFinals(mod.keySet(), mod::get, game, ClassBytesSource.EMPTY, "Forge", new ArrayList<>()));
	}

	/** A game class with {@code toNetwork(Object)} and {@code check(int)} of the given access. */
	private static byte[] gameClass(String name, int toNetwork, int check) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
		method(cw, toNetwork, "toNetwork", "(Ljava/lang/Object;)V");
		method(cw, check, "check", "(I)Z");
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** A mod class extending {@code superName}, overriding the named methods of {@link #gameClass}. */
	private static byte[] modClass(String name, String superName, String... overrides) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, superName, null);
		for (String override : overrides) {
			method(cw, Opcodes.ACC_PUBLIC, override, override.equals("check") ? "(I)Z" : "(Ljava/lang/Object;)V");
		}
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static void method(ClassWriter cw, int access, String name, String desc) {
		MethodVisitor mv = cw.visitMethod(access, name, desc, null, null);
		mv.visitCode();
		if (desc.endsWith("Z")) {
			mv.visitInsn(Opcodes.ICONST_0);
			mv.visitInsn(Opcodes.IRETURN);
		} else {
			mv.visitInsn(Opcodes.RETURN);
		}
		mv.visitMaxs(1, 2);
		mv.visitEnd();
	}
}
