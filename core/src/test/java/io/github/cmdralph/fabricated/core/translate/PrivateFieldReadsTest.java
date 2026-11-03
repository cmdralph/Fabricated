package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;

class PrivateFieldReadsTest {
	private static final String BIOME = "net/minecraft/world/level/biome/Biome";
	private static final String CLIMATE = "Lnet/minecraft/world/level/biome/Biome$ClimateSettings;";
	private static final String STRUCTURE = "net/minecraft/world/level/levelgen/structure/Structure";
	private static final String SETTINGS = "Lnet/minecraft/world/level/levelgen/structure/Structure$StructureSettings;";

	private final Map<String, byte[]> game = new HashMap<>();

	private static byte[] write(ClassNode node) {
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		node.accept(writer);
		return writer.toByteArray();
	}

	/** A game class with a private field and, when {@code getter} isn't null, the getter returning it. */
	private void gameClass(String name, String superName, String field, String desc, String getter) {
		ClassNode node = new ClassNode();
		node.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, superName, null);
		if (field != null) node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, field, desc, null, null));
		if (getter != null) {
			MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC, getter, "()" + desc, null, null);
			m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
			m.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, name, field, desc));
			m.instructions.add(new InsnNode(Opcodes.ARETURN));
			node.methods.add(m);
		}
		game.put(name, write(node));
	}

	/** A mod class with a static method {@code read(owner)} returning {@code owner.field}, and one writing it. */
	private static ClassNode modClass(String name, String superName, String owner, String field, String desc) {
		ClassNode node = new ClassNode();
		node.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, superName, null);
		MethodNode read = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "read", "(L" + owner + ";)" + desc, null, null);
		read.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		read.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, owner, field, desc));
		read.instructions.add(new InsnNode(Opcodes.ARETURN));
		node.methods.add(read);
		MethodNode write = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "write", "(L" + owner + ";" + desc + ")V", null, null);
		write.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		write.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
		write.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, owner, field, desc));
		write.instructions.add(new InsnNode(Opcodes.RETURN));
		node.methods.add(write);
		return node;
	}

	private static List<String> calls(byte[] data) {
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, 0);
		List<String> calls = new ArrayList<>();
		for (MethodNode m : node.methods) {
			for (AbstractInsnNode insn : m.instructions) {
				if (insn instanceof FieldInsnNode f) calls.add(m.name + ": " + (f.getOpcode() == Opcodes.GETFIELD ? "get " : "put ") + f.owner + "." + f.name);
				if (insn instanceof MethodInsnNode c) calls.add(m.name + ": call " + c.owner + "." + c.name + c.desc);
			}
		}
		return calls;
	}

	private PrivateFieldReads reads() {
		Map<String, String> privateFields = Map.of(BIOME + " climateSettings", "getModifiedClimateSettings", STRUCTURE + " settings",
				"getModifiedStructureSettings", "net/minecraft/world/level/block/FlowerPotBlock potted", "getPotted");
		return PrivateFieldReads.of(new ClassIndex(name -> game.get(name)), privateFields);
	}

	@Test
	void aModsReadOfAFieldThePlatformKeepsPrivateCallsItsGetter() throws Exception {
		gameClass(BIOME, "java/lang/Object", "climateSettings", CLIMATE, "getModifiedClimateSettings");
		Map<String, byte[]> mod = new TreeMap<>();
		mod.put("mod/Seasons.class", write(modClass("mod/Seasons", "java/lang/Object", BIOME, "climateSettings", CLIMATE)));
		mod.put("mod/Unrelated.class", write(modClass("mod/Unrelated", "java/lang/Object", "mod/Other", "climateSettings", CLIMATE)));
		byte[] unrelated = mod.get("mod/Unrelated.class");

		List<String> notes = reads().apply(mod, mod.keySet(), name -> null);

		// The read calls the getter; the write is left as it was, and so is a field of the same name elsewhere.
		assertEquals(List.of("read: call " + BIOME + ".getModifiedClimateSettings()" + CLIMATE, "write: put " + BIOME + ".climateSettings"),
				calls(mod.get("mod/Seasons.class")));
		assertTrue(unrelated == mod.get("mod/Unrelated.class"), "a class reading no such field is left as it is");
		assertEquals(List.of("1 class(es) read net.minecraft.world.level.biome.Biome.climateSettings, which the platform keeps private; they read it"
				+ " through its getModifiedClimateSettings(), as the game's own code does: mod.Seasons"), notes);
	}

	@Test
	void onlyWhereTheGameHasTheGetter() throws Exception {
		// As 26.3 alone replacing Biome.attributes: a field without its getter stays a field the mod can widen.
		gameClass("net/minecraft/world/level/block/FlowerPotBlock", "java/lang/Object", "potted", "Lnet/minecraft/world/level/block/Block;", null);
		Map<String, byte[]> mod = new TreeMap<>();
		mod.put("mod/Pots.class", write(modClass("mod/Pots", "java/lang/Object", "net/minecraft/world/level/block/FlowerPotBlock", "potted",
				"Lnet/minecraft/world/level/block/Block;")));
		assertTrue(reads().isEmpty());
		assertEquals(List.of(), reads().apply(mod, mod.keySet(), name -> null));
	}

	@Test
	void aReadThroughASubclassOrTheModsOwnSubclassIsFound() throws Exception {
		gameClass(STRUCTURE, "java/lang/Object", "settings", SETTINGS, "getModifiedStructureSettings");
		gameClass("net/minecraft/world/level/levelgen/structure/structures/JigsawStructure", STRUCTURE, null, null, null);
		Map<String, byte[]> mod = new TreeMap<>();
		mod.put("mod/A.class", write(modClass("mod/A", "java/lang/Object", "net/minecraft/world/level/levelgen/structure/structures/JigsawStructure",
				"settings", SETTINGS)));
		// The mod's own structure, reading the field it inherits through itself.
		mod.put("mod/MyStructure.class", write(modClass("mod/MyStructure", STRUCTURE, "mod/MyStructure", "settings", SETTINGS)));
		reads().apply(mod, mod.keySet(), name -> null);
		assertEquals("read: call " + STRUCTURE + ".getModifiedStructureSettings()" + SETTINGS, calls(mod.get("mod/A.class")).getFirst());
		assertEquals("read: call " + STRUCTURE + ".getModifiedStructureSettings()" + SETTINGS, calls(mod.get("mod/MyStructure.class")).getFirst());
	}

	@Test
	void aMixinIntoTheFieldsClassIsLeftAlone() throws Exception {
		gameClass(BIOME, "java/lang/Object", "climateSettings", CLIMATE, "getModifiedClimateSettings");
		ClassNode mixin = modClass("mod/mixin/BiomeMixin", "java/lang/Object", BIOME, "climateSettings", CLIMATE);
		AnnotationNode annotation = new AnnotationNode(Annotations.MIXIN);
		annotation.values = new ArrayList<>(List.of("value", new ArrayList<>(List.of(Type.getObjectType(BIOME)))));
		mixin.invisibleAnnotations = new ArrayList<>(List.of(annotation));
		Map<String, byte[]> mod = new TreeMap<>();
		byte[] before = write(mixin);
		mod.put("mod/mixin/BiomeMixin.class", before);
		assertEquals(List.of(), reads().apply(mod, mod.keySet(), name -> null));
		assertTrue(before == mod.get("mod/mixin/BiomeMixin.class"));
	}
}
