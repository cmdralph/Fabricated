package io.github.cmdralph.fabricated.core.mixin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import io.github.cmdralph.fabricated.core.translate.PlatformDescriptor;

/** Pre-flight's view of the game as NeoForge's and Forge's code transformers leave it (CORPUS-61, CORPUS-78). */
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
	static final String FORGE_HOOKS = "net/minecraftforge/event/ForgeEventFactory";
	/** {@code Mob.finalizeSpawn} from 1.21.3 on, as Forge 61 to 66's games have it. */
	static final String FINALIZE_REASON = "(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;"
			+ "Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/world/entity/SpawnGroupData;)Lnet/minecraft/world/entity/SpawnGroupData;";
	static final String FINALIZE_REASON_HOOK = "(L" + MOB + ";" + FINALIZE_REASON.substring(1);
	/** The one method of {@code PatrolSpawner} Forge's list names. */
	static final String SPAWN_PATROL_MEMBER = "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;Z)Z";
	/** A class Forge's list names with no method: only its synthetic methods are changed. */
	static final String TADPOLE = "net/minecraft/world/entity/animal/frog/Tadpole";
	static final String SPAWN_DATA = "()Lnet/minecraft/world/entity/SpawnGroupData;";

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
		return mob(name, superName, FINALIZE);
	}

	private static byte[] mob(String name, String superName, String finalizeDesc) {
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
			MethodVisitor finalize = cw.visitMethod(Opcodes.ACC_PUBLIC, "finalizeSpawn", finalizeDesc, null, null);
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
		return hooks(HOOKS, "finalizeMobSpawn", FINALIZE);
	}

	/** The platform's hooks: {@code hookName} counts its calls and makes the mob's {@code finalizeSpawn}. */
	private static byte[] hooks(String owner, String hookName, String finalizeDesc) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
		cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "events", "I", null, null).visitEnd();
		MethodVisitor hook = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, hookName, "(L" + MOB + ";" + finalizeDesc.substring(1), null, null);
		hook.visitCode();
		hook.visitFieldInsn(Opcodes.GETSTATIC, owner, "events", "I");
		hook.visitInsn(Opcodes.ICONST_1);
		hook.visitInsn(Opcodes.IADD);
		hook.visitFieldInsn(Opcodes.PUTSTATIC, owner, "events", "I");
		for (int i = 0; i < 5; i++) hook.visitVarInsn(Opcodes.ALOAD, i);
		hook.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MOB, "finalizeSpawn", finalizeDesc, false);
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
		spawnIn(cw, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, method, SPAWN_DATA, mob, FINALIZE);
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * A method, its parameters unused, that makes a new mob of {@code mob} and finalizes its spawn, returning the
	 * data, or true for a method returning a boolean.
	 */
	private static void spawnIn(ClassWriter cw, int access, String method, String desc, String mob, String finalizeDesc) {
		MethodVisitor mv = cw.visitMethod(access, method, desc, null, null);
		mv.visitCode();
		mv.visitTypeInsn(Opcodes.NEW, mob);
		mv.visitInsn(Opcodes.DUP);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, mob, "<init>", "()V", false);
		for (int i = 0; i < 4; i++) mv.visitInsn(Opcodes.ACONST_NULL);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, mob, "finalizeSpawn", finalizeDesc, false);
		if (Type.getReturnType(desc).getSort() == Type.BOOLEAN) {
			mv.visitInsn(Opcodes.POP);
			mv.visitInsn(Opcodes.ICONST_1);
			mv.visitInsn(Opcodes.IRETURN);
		} else {
			mv.visitInsn(Opcodes.ARETURN);
		}
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	/**
	 * The game's classes of a Forge 61 to 66 build (26.1.2's), as its jars have them: {@code PatrolSpawner} finalizing a
	 * patrol member's spawn in the method Forge's list names, in a lambda body and in a method the list doesn't name;
	 * {@code Tadpole}, which the list names with no method, in a lambda body and in another method.
	 */
	static Map<String, byte[]> forgeGame() {
		Map<String, byte[]> classes = new HashMap<>();
		classes.put(MOB, mob(MOB, "java/lang/Object", FINALIZE_REASON));
		classes.put(PATROLLING, mob(PATROLLING, MOB, FINALIZE_REASON));
		classes.put(FORGE_HOOKS, hooks(FORGE_HOOKS, "onFinalizeSpawn", FINALIZE_REASON));
		int lambda = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC;
		ClassWriter patrols = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		patrols.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, PATROLS, null, "java/lang/Object", null);
		MethodVisitor init = patrols.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		init.visitCode();
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(0, 0);
		init.visitEnd();
		// As the game has it: private, and not static.
		spawnIn(patrols, Opcodes.ACC_PRIVATE, "spawnPatrolMember", SPAWN_PATROL_MEMBER, PATROLLING, FINALIZE_REASON);
		spawnIn(patrols, lambda, "lambda$tick$0", SPAWN_DATA, PATROLLING, FINALIZE_REASON);
		spawnIn(patrols, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "spawnLeader", SPAWN_DATA, PATROLLING, FINALIZE_REASON);
		patrols.visitEnd();
		classes.put(PATROLS, patrols.toByteArray());
		ClassWriter tadpole = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		tadpole.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TADPOLE, null, "java/lang/Object", null);
		spawnIn(tadpole, lambda, "lambda$ageUp$0", SPAWN_DATA, MOB, FINALIZE_REASON);
		spawnIn(tadpole, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "ageUp", SPAWN_DATA, MOB, FINALIZE_REASON);
		tadpole.visitEnd();
		classes.put(TADPOLE, tadpole.toByteArray());
		for (String type : List.of("net/minecraft/world/level/ServerLevelAccessor", "net/minecraft/world/DifficultyInstance",
				"net/minecraft/world/entity/EntitySpawnReason", "net/minecraft/world/entity/SpawnGroupData", "net/minecraft/server/level/ServerLevel",
				"net/minecraft/core/BlockPos", "net/minecraft/util/RandomSource")) {
			ClassWriter cw = new ClassWriter(0);
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, type, null, "java/lang/Object", null);
			cw.visitEnd();
			classes.put(type, cw.toByteArray());
		}
		return classes;
	}

	/** Each method's first call but a constructor's, as owner.name+desc, by the method's name. */
	static Map<String, String> callByMethod(byte[] data) {
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, 0);
		Map<String, String> calls = new LinkedHashMap<>();
		for (MethodNode m : node.methods) {
			for (AbstractInsnNode insn : m.instructions) {
				if (insn instanceof MethodInsnNode call && !call.name.equals("<init>")) {
					calls.putIfAbsent(m.name, call.owner + "." + call.name + call.desc);
				}
			}
		}
		return calls;
	}

	/** A class loader defining the classes given, and nothing else. */
	private static ClassLoader loading(Map<String, byte[]> classes) {
		return new ClassLoader(null) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] data = classes.get(name.replace('.', '/'));
				if (data == null) throw new ClassNotFoundException(name);
				return defineClass(name, data, 0, data.length);
			}
		};
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
		ClassLoader loader = loading(loaded);
		loader.loadClass(SPAWNER.replace('/', '.')).getMethod("spawnCategoryForPosition").invoke(null);
		loader.loadClass(PATROLS.replace('/', '.')).getMethod("spawnPatrolMember").invoke(null);
		assertEquals(2, loader.loadClass(HOOKS.replace('/', '.')).getField("events").getInt(null));
	}

	@Test
	void onlyNeoForge21_1sCoremodsAreDescribedTheOthersRunAfterMixin() throws Exception {
		Map<String, byte[]> game = game();
		assertEquals(22, PlatformCoremods.neoForge("1.21.1").redirectedClasses());
		assertTrue(PlatformCoremods.neoForge("1.21.1").redirectsIn("net/minecraft/gametest/framework/GameTestHelper").isEmpty());
		// CORPUS-67: from 21.11 on, FML runs NeoForge's coremods after Mixin, which sees the classes as the jars have them
		// (Debugify's hook at a cat's finalizeSpawn call in CatSpawner, moved to NeoForge's hook, found nothing on 26.3).
		// 1.21.10 isn't 1.21.1; an unknown version leaves the game as its jars have it too.
		for (String version : List.of("1.21.11", "26.1.2", "26.2", "26.3", "1.21.10", "27.1")) {
			assertSame(PlatformCoremods.NONE, PlatformCoremods.neoForge(version), version);
			assertSame(PlatformCoremods.NONE, PlatformDescriptor.NEOFORGE.coremods(version), version);
		}
		assertArrayEquals(game.get(SPAWNER), PlatformCoremods.neoForge("26.3").over(game::get).read(SPAWNER));
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
		return biome(getter, false);
	}

	/** A biome with its climate field, maybe its getter, and maybe another method of the getter's descriptor reading the field. */
	private static byte[] biome(boolean getter, boolean getterShaped) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, BIOME, null, "java/lang/Object", null);
		cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "climateSettings", CLIMATE, null, null).visitEnd();
		for (String reader : getterShaped ? List.of("getModifiedClimateSettings", "climate") : getter ? List.of("getModifiedClimateSettings") : List.<String>of()) {
			MethodVisitor get = cw.visitMethod(Opcodes.ACC_PUBLIC, reader, "()" + CLIMATE, null, null);
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
	void forge61To66PointTheCallAtTheirEventHookInTheMethodsTheirListNamesAndTheListedClassesLambdasAndRunSo() throws Exception {
		// CORPUS-78: Forge's MethodRedirector, a ModLauncher transformer, so before Mixin, points PatrolSpawner's call at
		// ForgeEventFactory.onFinalizeSpawn: Friends & Foes' @Inject at a PatrollingMonster's call there found nothing.
		Map<String, byte[]> game = forgeGame();
		ClassBytesSource seen = PlatformCoremods.forge("26.1.2").over(game::get);
		String hook = FORGE_HOOKS + ".onFinalizeSpawn" + FINALIZE_REASON_HOOK;
		Map<String, String> patrols = callByMethod(seen.read(PATROLS));
		assertEquals(hook, patrols.get("spawnPatrolMember"), "the method the list names");
		assertEquals(hook, patrols.get("lambda$tick$0"), "a synthetic method of a listed class");
		assertEquals(PATROLLING + ".finalizeSpawn" + FINALIZE_REASON, patrols.get("spawnLeader"), "a method the list doesn't name keeps its call");
		Map<String, String> tadpole = callByMethod(seen.read(TADPOLE));
		assertEquals(hook, tadpole.get("lambda$ageUp$0"), "listed with no method: its synthetic methods only");
		assertEquals(MOB + ".finalizeSpawn" + FINALIZE_REASON, tadpole.get("ageUp"));
		assertArrayEquals(game.get(FORGE_HOOKS), seen.read(FORGE_HOOKS), "the hook's own call stays");

		// The classes as the transformer leaves them load and run, the hook's event fired once for each redirected spawn.
		Map<String, byte[]> loaded = new HashMap<>(game);
		loaded.put(PATROLS, seen.read(PATROLS));
		loaded.put(TADPOLE, seen.read(TADPOLE));
		ClassLoader loader = loading(loaded);
		Class<?> patrolSpawner = loader.loadClass(PATROLS.replace('/', '.'));
		Class<?>[] memberParameters = {loader.loadClass("net.minecraft.server.level.ServerLevel"), loader.loadClass("net.minecraft.core.BlockPos"),
				loader.loadClass("net.minecraft.util.RandomSource"), boolean.class};
		java.lang.reflect.Method spawnPatrolMember = patrolSpawner.getDeclaredMethod("spawnPatrolMember", memberParameters);
		spawnPatrolMember.setAccessible(true);
		assertEquals(true, spawnPatrolMember.invoke(patrolSpawner.getConstructor().newInstance(), null, null, null, false));
		patrolSpawner.getMethod("spawnLeader").invoke(null);
		java.lang.reflect.Method patrolLambda = patrolSpawner.getDeclaredMethod("lambda$tick$0");
		patrolLambda.setAccessible(true);
		patrolLambda.invoke(null);
		Class<?> tadpoles = loader.loadClass(TADPOLE.replace('/', '.'));
		tadpoles.getMethod("ageUp").invoke(null);
		java.lang.reflect.Method tadpoleLambda = tadpoles.getDeclaredMethod("lambda$ageUp$0");
		tadpoleLambda.setAccessible(true);
		tadpoleLambda.invoke(null);
		assertEquals(3, loader.loadClass(FORGE_HOOKS.replace('/', '.')).getField("events").getInt(null));
	}

	@Test
	void forge61To66sTransformersAreDescribedForTheirVersionsAndNotForge47sOrAnother() {
		// Each Forge branch's finalize_spawn_targets.json, as this build ships it: 1.21.11's and 26.1's list 27 classes,
		// 26.2's and 26.3's 23.
		Map<String, Integer> classes = Map.of("1.21.11", 27, "26.1", 27, "26.1.2", 27, "26.2", 23, "26.2.1", 23, "26.3", 23);
		classes.forEach((version, count) -> {
			assertEquals(count, PlatformCoremods.forge(version).redirectedClasses(), version);
			assertEquals(count, PlatformDescriptor.FORGE.coremods(version).redirectedClasses(), version);
			PlatformCoremods.Redirect patrols = PlatformCoremods.forge(version).redirectsIn(PATROLS).getFirst();
			assertEquals(Set.of("spawnPatrolMember" + SPAWN_PATROL_MEMBER), patrols.methods(), version);
			assertTrue(patrols.synthetic(), version);
			assertEquals(FORGE_HOOKS, patrols.hookOwner(), version);
		});
		for (String line : List.of("1.21.11", "26.1", "26.2", "26.3")) {
			PlatformCoremods.forgeSpawnTargets(line).forEach((target, methods) -> {
				assertTrue(target.startsWith("net/minecraft/"), target);
				methods.forEach(method -> assertTrue(method.indexOf('(') > 0 && method.contains(")"), target + " " + method));
			});
		}
		// Forge 47's 1.20.1 is described on its branch; another version's transformers aren't known, so the game is as its
		// jars have it.
		for (String version : List.of("1.20.1", "1.21.1", "1.21.10", "1.21.110", "26.10", "26.4", "27.1")) {
			assertSame(PlatformCoremods.NONE, PlatformCoremods.forge(version), version);
			assertSame(PlatformCoremods.NONE, PlatformDescriptor.FORGE.coremods(version), version);
		}
		assertSame(PlatformCoremods.NONE, PlatformCoremods.forge(null));
		assertEquals(22, PlatformDescriptor.NEOFORGE.coremods("1.21.1").redirectedClasses());
	}

	@Test
	void forge61To66ReadAFieldThroughItsGetterInTheGettersShapeTooAndNotBiomesOn26_3() throws Exception {
		byte[] biome = biome(true, true);
		// NeoForge's transformer leaves a method of the getter's descriptor reading the field; Forge's check meant to do so
		// compares a string with a method, so it reads the getter there too.
		assertTrue(readsField(PlatformCoremods.neoForge("1.21.1").over(Map.of(BIOME, biome)::get).read(BIOME), "climate"));
		byte[] onForge = PlatformCoremods.forge("26.1.2").over(Map.of(BIOME, biome)::get).read(BIOME);
		assertFalse(readsField(onForge, "climate"));
		assertEquals(BIOME + ".getModifiedClimateSettings()" + CLIMATE, callByMethod(onForge).get("climate"));
		assertEquals(BIOME + ".getModifiedClimateSettings()" + CLIMATE, callByMethod(onForge).get("hasPrecipitation"));
		assertTrue(readsField(onForge, "getModifiedClimateSettings"), "the getter reads the field");
		// Forge 66 no longer redirects Biome's fields.
		assertArrayEquals(biome, PlatformCoremods.forge("26.3").over(Map.of(BIOME, biome)::get).read(BIOME));
	}

	/** Whether the class's method of that name reads the climate field itself. */
	private static boolean readsField(byte[] data, String method) {
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, 0);
		for (MethodNode m : node.methods) {
			if (!m.name.equals(method)) continue;
			for (AbstractInsnNode insn : m.instructions) {
				if (insn instanceof FieldInsnNode f && f.getOpcode() == Opcodes.GETFIELD && f.name.equals("climateSettings")) return true;
			}
		}
		return false;
	}
}
