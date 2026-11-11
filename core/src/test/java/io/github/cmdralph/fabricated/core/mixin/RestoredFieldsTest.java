package io.github.cmdralph.fabricated.core.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;

class RestoredFieldsTest {
	private static final String MIX = "net/minecraft/world/item/alchemy/PotionBrewing$Mix";
	private static final String HOLDER = "Lnet/minecraft/core/Holder$Reference;";

	private static byte[] write(ClassNode node) {
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		node.accept(writer);
		return writer.toByteArray();
	}

	private static MethodNode method(int access, String name, String desc) {
		MethodNode m = new MethodNode(access, name, desc, null, null);
		m.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
		m.instructions.add(new InsnNode(Opcodes.ARETURN));
		return m;
	}

	/** The in-game mod's bridge mixin of Forge 47's brewing mix, as `forge-legacy` has it, in a directory of its own. */
	private static Path inGameMod(Path dir) throws Exception {
		ClassNode mixin = new ClassNode();
		mixin.visit(Opcodes.V17, Opcodes.ACC_ABSTRACT, "fabricated/mixin/MixBridge", null, "java/lang/Object", null);
		AnnotationNode target = new AnnotationNode(Annotations.MIXIN);
		target.values = new ArrayList<>(List.of("value", new ArrayList<>(List.of(Type.getObjectType(MIX)))));
		mixin.invisibleAnnotations = new ArrayList<>(List.of(target));
		FieldNode shadow = new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "f_43532_", HOLDER, null, null);
		shadow.visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode("Lorg/spongepowered/asm/mixin/Shadow;")));
		mixin.fields.add(shadow);
		mixin.methods.add(method(Opcodes.ACC_PUBLIC, "fabricated$get$f_43532_", "()Ljava/lang/Object;"));
		// A seam for a method the platform never calls, which Fabric mods' hooks into the method are pointed at (HUD-4).
		mixin.methods.add(method(Opcodes.ACC_PUBLIC, "fabricated$end$m_43535_", "()Ljava/lang/Object;"));
		// Not a bridge: a mixin's own helper, which a mod has no use for.
		mixin.methods.add(method(Opcodes.ACC_PUBLIC, "fabricated$holder", "()" + HOLDER));
		Files.createDirectories(dir.resolve("fabricated/mixin"));
		Files.write(dir.resolve("fabricated/mixin/MixBridge.class"), write(mixin));
		Files.writeString(dir.resolve("fabricated.mixins.json"), "{\"package\": \"fabricated.mixin\", \"mixins\": [\"MixBridge\"]}");
		return dir;
	}

	@Test
	void theBridgesOfFabricatedsMixinsAreTheGameClassesAndTheirFields(@TempDir Path dir) throws Exception {
		RestoredFields restored = RestoredFields.of(List.of(inGameMod(dir)));

		// A Fabric mod's own reads of the field call the bridge (CORPUS-107).
		assertEquals(Map.of(MIX + " f_43532_", "fabricated$get$f_43532_"), restored.bridgedFields());

		ClassNode mix = new ClassNode();
		mix.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, MIX, null, "java/lang/Object", null);
		mix.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "f_43532_", HOLDER, null, null));
		byte[] vanilla = write(mix);
		ClassBytesSource game = name -> MIX.equals(name) ? vanilla : null;
		ClassNode seen = new ClassNode();
		new ClassReader(restored.over(game).read(MIX)).accept(seen, 0);
		// Pre-flight sees the bridge declared on the game's class, which an accessor of the field invokes (CORPUS-37).
		assertTrue(seen.methods.stream().anyMatch(m -> m.name.equals("fabricated$get$f_43532_") && m.desc.equals("()Ljava/lang/Object;")));
		assertTrue(seen.methods.stream().anyMatch(m -> m.name.equals("fabricated$end$m_43535_")), "the seam is the game class's too");
		assertTrue(seen.methods.stream().noneMatch(m -> m.name.equals("fabricated$holder")));
		assertEquals(1, seen.fields.size(), "the shadowed field is the game's own");
	}

	@Test
	void aSeamForAReadKeepsItsCodeAsMixinMergesIt(@TempDir Path dir) throws Exception {
		// HUD-7: pre-flight finds the read, and vanilla's locals there, in the seam's code, which names the game's class,
		// and the priority of the mixin merging it, which Mixin compares a Fabric mixin's with there.
		String gui = "net/minecraft/client/gui/Gui";
		String mixinName = "fabricated/mixin/GuiSeam";
		ClassNode mixin = new ClassNode();
		mixin.visit(Opcodes.V21, Opcodes.ACC_ABSTRACT, mixinName, null, "java/lang/Object", null);
		AnnotationNode target = new AnnotationNode(Annotations.MIXIN);
		target.values = new ArrayList<>(List.of("value", new ArrayList<>(List.of(Type.getObjectType(gui))), "priority", Integer.MIN_VALUE));
		mixin.invisibleAnnotations = new ArrayList<>(List.of(target));
		MethodNode seam = new MethodNode(Opcodes.ACC_PUBLIC, "fabricated$read$layers", "(Ljava/lang/Object;)V", null, null);
		seam.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		seam.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, mixinName, "layers", "Ljava/lang/Object;"));
		seam.instructions.add(new InsnNode(Opcodes.POP));
		seam.instructions.add(new InsnNode(Opcodes.RETURN));
		mixin.methods.add(seam);
		Files.createDirectories(dir.resolve("fabricated/mixin"));
		Files.write(dir.resolve("fabricated/mixin/GuiSeam.class"), write(mixin));
		Files.writeString(dir.resolve("fabricated.mixins.json"), "{\"package\": \"fabricated.mixin\", \"client\": [\"GuiSeam\"]}");

		ClassNode game = new ClassNode();
		game.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, gui, null, "java/lang/Object", null);
		byte[] bytes = write(game);
		ClassNode seen = new ClassNode();
		new ClassReader(RestoredFields.of(List.of(dir)).over(name -> gui.equals(name) ? bytes : null).read(gui)).accept(seen, 0);
		MethodNode merged = seen.methods.stream().filter(m -> m.name.equals("fabricated$read$layers")).findFirst().orElseThrow();
		FieldInsnNode read = (FieldInsnNode) merged.instructions.get(1);
		assertEquals(gui, read.owner);
		assertEquals("layers", read.name);
		AnnotationNode mark = Annotations.find(merged, "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
		assertEquals("fabricated.mixin.GuiSeam", Annotations.value(mark, "mixin"));
		assertEquals(Integer.MIN_VALUE, Annotations.value(mark, "priority"));
	}

	@Test
	void withoutTheInGameModsMixinsThereAreNoBridges(@TempDir Path dir) {
		RestoredFields restored = RestoredFields.of(List.of(dir.resolve("missing.jar")));
		assertEquals(Map.of(), restored.bridgedFields());
		ClassBytesSource game = name -> null;
		assertTrue(restored.over(game) == game);
	}
}
