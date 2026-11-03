package io.github.cmdralph.fabricated.core.mixin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;

/** Pre-flight's view of the game as NeoForge's code transformers leave it (CORPUS-61). */
class PlatformCoremodsTest {
	static final String MOB = "net/minecraft/world/entity/Mob";
	static final String PATROLLING = "net/minecraft/world/entity/monster/PatrollingMonster";
	static final String HOOKS = "net/neoforged/neoforge/event/EventHooks";
	static final String SPAWNER = "net/minecraft/world/level/NaturalSpawner";
	static final String PATROLS = "net/minecraft/world/level/levelgen/PatrolSpawner";
	static final String SPAWN_TYPE = "Lnet/minecraft/world/entity/MobSpawnType;";
	static final String FINALIZE = "(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;" + SPAWN_TYPE
			+ "Lnet/minecraft/world/entity/SpawnGroupData;)Lnet/minecraft/world/entity/SpawnGroupData;";
	static final String FINALIZE_HOOK = "(L" + MOB + ";" + FINALIZE.substring(1);
	static final String BIOME = "net/minecraft/world/level/biome/Biome";
	static final String CLIMATE = "Lnet/minecraft/world/level/biome/Biome$ClimateSettings;";

	/** The game's classes of a 1.21.1 build, as its jars have them: spawners calling a mob's {@code finalizeSpawn}. */
	static Map<String, byte[]> game() {
		Map<String, byte[]> classes = new HashMap<>();
		classes.put(MOB, mob(MOB, "java/lang/Object"));
		classes.put(PATROLLING, mob(PATROLLING, MOB));
		classes.put(HOOKS, hooks());
		classes.put(SPAWNER, spawner(SPAWNER, MOB, "spawnCategoryForPosition"));
		classes.put(PATROLS, spawner(PATROLS, PATROLLING, "spawnPatrolMember"));
		// A class NeoForge's transformer doesn't list keeps its call.
		classes.put("net/minecraft/world/level/Other", spawner("net/minecraft/world/level/Other", MOB, "spawn"));
		for (String type : List.of("net/minecraft/world/level/ServerLevelAccessor", "net/minecraft/world/DifficultyInstance",
				"net/minecraft/world/entity/MobSpawnType", "net/minecraft/world/entity/SpawnGroupData")) {
			ClassWriter cw = new ClassWriter(0);
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, type, null, "java/lang/Object", null);
			cw.visitEnd();
			classes.put(type, cw.toByteArray());
		}
		return classes;
	}

	/** A mob class whose {@code finalizeSpawn} returns its spawn data. */
	private static byte[] mob(String name, String superName) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, superName, null);
		MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		init.visitCode();
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(0, 0);
		init.visitEnd();
		if (name.equals(MOB)) {
			MethodVisitor finalize = cw.visitMethod(Opcodes.ACC_PUBLIC, "finalizeSpawn", FINALIZE, null, null);
			finalize.visitCode();
			finalize.visitVarInsn(Opcodes.ALOAD, 4);
			finalize.visitInsn(Opcodes.ARETURN);
			finalize.visitMaxs(0, 0);
			finalize.visitEnd();
		}
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** NeoForge's hooks: {@code finalizeMobSpawn} counts its calls and makes the mob's. */
	private static byte[] hooks() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, HOOKS, null, "java/lang/Object", null);
		cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "events", "I", null, null).visitEnd();
		MethodVisitor hook = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "finalizeMobSpawn", FINALIZE_HOOK, null, null);
		hook.visitCode();
		hook.visitFieldInsn(Opcodes.GETSTATIC, HOOKS, "events", "I");
		hook.visitInsn(Opcodes.ICONST_1);
		hook.visitInsn(Opcodes.IADD);
		hook.visitFieldInsn(Opcodes.PUTSTATIC, HOOKS, "events", "I");
		for (int i = 0; i < 5; i++) hook.visitVarInsn(Opcodes.ALOAD, i);
		hook.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MOB, "finalizeSpawn", FINALIZE, false);
		hook.visitInsn(Opcodes.ARETURN);
		hook.visitMaxs(0, 0);
		hook.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** A spawner whose static {@code method} makes a new mob of {@code mob} and finalizes its spawn, returning the data. */
	private static byte[] spawner(String name, String mob, String method) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, method, "()Lnet/minecraft/world/entity/SpawnGroupData;", null, null);
		mv.visitCode();
		mv.visitTypeInsn(Opcodes.NEW, mob);
		mv.visitInsn(Opcodes.DUP);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, mob, "<init>", "()V", false);
		for (int i = 0; i < 4; i++) mv.visitInsn(Opcodes.ACONST_NULL);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, mob, "finalizeSpawn", FINALIZE, false);
		mv.visitInsn(Opcodes.ARETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	static List<MethodInsnNode> calls(byte[] data) {
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, 0);
		List<MethodInsnNode> calls = new ArrayList<>();
		for (MethodNode m : node.methods) {
			for (AbstractInsnNode insn : m.instructions) {
				if (insn instanceof MethodInsnNode call && !call.name.equals("<init>")) calls.add(call);
			}
		}
		return calls;
	}

	@Test
	void theListedClassesCallNeoForgesHookAsItsTransformerLeavesThemAndRunSo() throws Exception {
		Map<String, byte[]> game = game();
		PlatformCoremods coremods = PlatformCoremods.neoForge("1.21.1");
		ClassBytesSource seen = coremods.over(game::get);
		for (String spawner : List.of(SPAWNER, PATROLS)) {
			MethodInsnNode call = calls(seen.read(spawner)).getFirst();
			assertEquals(Opcodes.INVOKESTATIC, call.getOpcode(), spawner);
			assertEquals(HOOKS + ".finalizeMobSpawn" + FINALIZE_HOOK, call.owner + "." + call.name + call.desc, "whatever the call's owner: " + spawner);
		}
		assertArrayEquals(game.get("net/minecraft/world/level/Other"), seen.read("net/minecraft/world/level/Other"), "not listed: as its jar has it");
		assertArrayEquals(game.get(HOOKS), seen.read(HOOKS), "the hook's own call stays");

		// The classes as the transformer leaves them load and run, the hook's event fired once for each spawn.
		Map<String, byte[]> loaded = new HashMap<>(game);
		loaded.put(SPAWNER, seen.read(SPAWNER));
		loaded.put(PATROLS, seen.read(PATROLS));
		ClassLoader loader = new ClassLoader(null) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] data = loaded.get(name.replace('.', '/'));
				if (data == null) throw new ClassNotFoundException(name);
				return defineClass(name, data, 0, data.length);
			}
		};
		loader.loadClass(SPAWNER.replace('/', '.')).getMethod("spawnCategoryForPosition").invoke(null);
		loader.loadClass(PATROLS.replace('/', '.')).getMethod("spawnPatrolMember").invoke(null);
		assertEquals(2, loader.loadClass(HOOKS.replace('/', '.')).getField("events").getInt(null));
	}

	@Test
	void eachVersionHasItsListAndDescriptor() throws Exception {
		Map<String, byte[]> game = game();
		// 21.11 on: finalizeSpawn takes an EntitySpawnReason; a 21.1 call isn't the one redirected there.
		ClassBytesSource later = PlatformCoremods.neoForge("1.21.11").over(game::get);
		assertEquals(Opcodes.INVOKEVIRTUAL, calls(later.read(SPAWNER)).getFirst().getOpcode());
		assertTrue(PlatformCoremods.neoForge("1.21.11").redirectsIn(SPAWNER).getFirst().desc().contains("EntitySpawnReason"));
		assertTrue(PlatformCoremods.neoForge("1.21.1").redirectsIn("net/minecraft/gametest/framework/GameTestHelper").isEmpty());
		assertEquals(1, PlatformCoremods.neoForge("26.1.2").redirectsIn("net/minecraft/gametest/framework/GameTestHelper").size());
		assertTrue(PlatformCoremods.neoForge("26.2").redirectsIn("net/minecraft/gametest/framework/GameTestHelper").isEmpty());
		assertEquals(1, PlatformCoremods.neoForge("26.3").redirectsIn("net/minecraft/gametest/framework/GameTestEntityBuilder").size());
		assertEquals(22, PlatformCoremods.neoForge("1.21.1").redirectedClasses());
		assertEquals(26, PlatformCoremods.neoForge("26.2").redirectedClasses());
		// 1.21.10 isn't 1.21.1; an unknown version leaves the game as its jars have it.
		assertTrue(PlatformCoremods.neoForge("1.21.10").isEmpty());
		ClassBytesSource none = PlatformCoremods.neoForge("27.1").over(game::get);
		assertArrayEquals(game.get(SPAWNER), none.read(SPAWNER));
	}

	@Test
	void aFieldTheTransformerReadsThroughItsGetterIsReadSoWhereTheClassHasTheGetter() throws Exception {
		byte[] biome = biome(true);
		ClassBytesSource seen = PlatformCoremods.neoForge("1.21.1").over(Map.of(BIOME, biome)::get);
		ClassNode node = new ClassNode();
		new ClassReader(seen.read(BIOME)).accept(node, 0);
		for (MethodNode m : node.methods) {
			boolean reads = false;
			boolean calls = false;
			for (AbstractInsnNode insn : m.instructions) {
				reads |= insn instanceof FieldInsnNode f && f.getOpcode() == Opcodes.GETFIELD && f.name.equals("climateSettings");
				calls |= insn instanceof MethodInsnNode c && c.name.equals("getModifiedClimateSettings");
			}
			if (m.name.equals("getModifiedClimateSettings")) {
				assertTrue(reads && !calls, "the getter reads the field");
			} else if (m.name.equals("hasPrecipitation")) {
				assertTrue(calls && !reads, "the others read the getter");
			}
		}
		// Without the getter (a build whose patches don't add it), the transformer doesn't apply.
		byte[] plain = biome(false);
		assertArrayEquals(plain, PlatformCoremods.neoForge("1.21.1").over(Map.of(BIOME, plain)::get).read(BIOME));
	}

	private static byte[] biome(boolean getter) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, BIOME, null, "java/lang/Object", null);
		cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "climateSettings", CLIMATE, null, null).visitEnd();
		if (getter) {
			MethodVisitor get = cw.visitMethod(Opcodes.ACC_PUBLIC, "getModifiedClimateSettings", "()" + CLIMATE, null, null);
			get.visitCode();
			get.visitVarInsn(Opcodes.ALOAD, 0);
			get.visitFieldInsn(Opcodes.GETFIELD, BIOME, "climateSettings", CLIMATE);
			get.visitInsn(Opcodes.ARETURN);
			get.visitMaxs(0, 0);
			get.visitEnd();
		}
		MethodVisitor uses = cw.visitMethod(Opcodes.ACC_PUBLIC, "hasPrecipitation", "()Z", null, null);
		uses.visitCode();
		uses.visitVarInsn(Opcodes.ALOAD, 0);
		uses.visitFieldInsn(Opcodes.GETFIELD, BIOME, "climateSettings", CLIMATE);
		uses.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getType(CLIMATE).getInternalName(), "hasPrecipitation", "()Z", false);
		uses.visitInsn(Opcodes.IRETURN);
		uses.visitMaxs(0, 0);
		uses.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	@Test
	void forgeDescribesNoneYet() {
		assertSame(PlatformCoremods.NONE, io.github.cmdralph.fabricated.core.translate.PlatformDescriptor.FORGE.coremods("1.20.1"));
		assertEquals(22, io.github.cmdralph.fabricated.core.translate.PlatformDescriptor.NEOFORGE.coremods("1.21.1").redirectedClasses());
	}
}
