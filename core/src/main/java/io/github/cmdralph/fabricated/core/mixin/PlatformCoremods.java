package io.github.cmdralph.fabricated.core.mixin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.core.accesswidener.AccessTransformerConverter;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;

/**
 * The game's classes as the platform's own code transformers leave them before Mixin applies any mod's mixins:
 * pre-flight reads them so, as Mixin will see them (CORPUS-61). Only NeoForge 21.1's change them first: its coremods
 * ({@code NeoForgeCoreMod}, ModLauncher {@code ITransformer}s, which run before Mixin's launch plugin applies mods'
 * mixins) make two kinds of change:
 * <ul>
 * <li>{@code MethodRedirector}: in the classes NeoForge's {@code finalize_spawn_targets.json} lists, every
 * {@code INVOKEVIRTUAL} of {@code finalizeSpawn} with {@code Mob}'s descriptor, whatever its owner, becomes a call of
 * {@code EventHooks.finalizeMobSpawn}, a static method taking the mob first, which fires NeoForge's
 * {@code FinalizeSpawnEvent} around the call. A Fabric mod's hook at that call (Carpet's spawn redirect in
 * {@code NaturalSpawner}, Friends &amp; Foes' in {@code PatrolSpawner}) then found nothing: the game's patched jar still
 * has the call, so pre-flight passed it, and Mixin stopped the world-generation worker or the server thread loading the
 * class.</li>
 * <li>{@code ReplaceFieldWithGetterAccess}: in {@code Biome}, {@code Structure} and {@code FlowerPotBlock}, every
 * {@code GETFIELD} of the fields {@link AccessTransformerConverter#NEOFORGE_PRIVATE_FIELDS} names becomes a call of the
 * getter NeoForge's patches add, except in the getter and in methods with its descriptor; only where the class has
 * the getter, as that list's rule is.</li>
 * </ul>
 * From NeoForge 21.11 on (FML 10 and later, which load classes without ModLauncher) the same coremods are
 * {@code SimpleClassProcessor}s, which FML runs after its {@code SimpleProcessorsGroup}, itself after Mixin's processor:
 * Mixin applies mods' mixins to the classes as the patched jar has them, and the coremods change the result, the
 * handlers Mixin merged in included, so a handler's own {@code finalizeSpawn} call goes through NeoForge's hook there
 * too. Describing them there moved hooks to calls Mixin never sees (CORPUS-67: Debugify's cat spawner hook and Friends
 * &amp; Foes' patrol hook crashed the server thread on 21.11 to 26.3). The 21.1 list was read in NeoForge's
 * {@code coremods} sources, branch 1.21.1; the order in FancyModLoader's branches 10.x, 11.x and main, and NeoForge's
 * coremods on 1.21.x, 26.1.x, 26.2.x and 26.3.x. Forge's coremods are the Forge branches' to describe.
 */
public final class PlatformCoremods {
	/** A call the platform's code points at a static hook taking the call's receiver first, in the classes listed. */
	public record Redirect(int opcode, String name, String desc, String hookOwner, String hookName, String hookDesc) {
	}

	/** None: the classes as the platform's jars have them. */
	public static final PlatformCoremods NONE = new PlatformCoremods(Map.of(), Map.of());

	private static final String MOB = "net/minecraft/world/entity/Mob";
	private static final String EVENT_HOOKS = "net/neoforged/neoforge/event/EventHooks";

	/** {@code finalize_spawn_targets.json} of NeoForge 21.1. */
	private static final List<String> SPAWN_TARGETS_21_1 = List.of(
			"net/minecraft/server/commands/RaidCommand",
			"net/minecraft/server/commands/SummonCommand",
			"net/minecraft/world/entity/EntityType",
			"net/minecraft/world/entity/ai/village/VillageSiege",
			"net/minecraft/world/entity/animal/frog/Tadpole",
			"net/minecraft/world/entity/animal/horse/SkeletonTrapGoal",
			"net/minecraft/world/entity/monster/Evoker$EvokerSummonSpellGoal",
			"net/minecraft/world/entity/monster/Spider",
			"net/minecraft/world/entity/monster/Strider",
			"net/minecraft/world/entity/monster/Zombie",
			"net/minecraft/world/entity/monster/ZombieVillager",
			"net/minecraft/world/entity/npc/CatSpawner",
			"net/minecraft/world/entity/npc/Villager",
			"net/minecraft/world/entity/raid/Raid",
			"net/minecraft/world/level/NaturalSpawner",
			"net/minecraft/world/level/levelgen/PatrolSpawner",
			"net/minecraft/world/level/levelgen/PhantomSpawner",
			"net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces$OceanMonumentPiece",
			"net/minecraft/world/level/levelgen/structure/structures/OceanRuinPieces$OceanRuinPiece",
			"net/minecraft/world/level/levelgen/structure/structures/SwampHutPiece",
			"net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionPieces$WoodlandMansionPiece",
			"net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate");

	/** {@code Mob.finalizeSpawn}'s descriptor on 21.1, with the spawn's {@code MobSpawnType}. */
	private static final String FINALIZE_SPAWN = "(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;"
			+ "Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/world/entity/SpawnGroupData;)Lnet/minecraft/world/entity/SpawnGroupData;";

	/** The redirects in each class they apply in. */
	private final Map<String, List<Redirect>> redirects;

	/** The fields read through a getter in each class, to the getter. */
	private final Map<String, Map<String, String>> getters;

	private PlatformCoremods(Map<String, List<Redirect>> redirects, Map<String, Map<String, String>> getters) {
		this.redirects = redirects;
		this.getters = getters;
	}

	/** Given redirects, by the classes they apply in, and fields read through getters ({@code owner field} to the getter). */
	public static PlatformCoremods of(Map<String, List<Redirect>> redirects, Map<String, String> getterFields) {
		Map<String, Map<String, String>> getters = new HashMap<>();
		getterFields.forEach((field, getter) -> {
			int space = field.indexOf(' ');
			getters.computeIfAbsent(field.substring(0, space), k -> new HashMap<>()).put(field.substring(space + 1), getter);
		});
		return new PlatformCoremods(Map.copyOf(redirects), Map.copyOf(getters));
	}

	/**
	 * NeoForge's, for a Minecraft version: 21.1's, the one whose coremods run before Mixin; {@link #NONE} for the others,
	 * whose coremods run after it (CORPUS-67), and for a version they aren't known for.
	 */
	public static PlatformCoremods neoForge(String minecraftVersion) {
		if (minecraftVersion == null || !(minecraftVersion.equals("1.21.1") || minecraftVersion.startsWith("1.21.1."))) return NONE;
		Redirect finalizeSpawn = new Redirect(Opcodes.INVOKEVIRTUAL, "finalizeSpawn", FINALIZE_SPAWN, EVENT_HOOKS, "finalizeMobSpawn",
				"(L" + MOB + ";" + FINALIZE_SPAWN.substring(1));
		Map<String, List<Redirect>> redirects = new HashMap<>();
		for (String target : SPAWN_TARGETS_21_1) redirects.put(target, List.of(finalizeSpawn));
		return of(redirects, AccessTransformerConverter.NEOFORGE_PRIVATE_FIELDS);
	}

	/** Whether this changes no class. */
	public boolean isEmpty() {
		return redirects.isEmpty() && getters.isEmpty();
	}

	/** How many classes the redirects apply in, for the report. */
	public int redirectedClasses() {
		return redirects.size();
	}

	/** The redirects that apply in a class (its internal name); none for most. */
	public List<Redirect> redirectsIn(String internalName) {
		return redirects.getOrDefault(internalName, List.of());
	}

	/** {@code game}'s classes as these transformers leave them. */
	public ClassBytesSource over(ClassBytesSource game) {
		if (isEmpty()) return game;
		Set<String> changed = new HashSet<>(redirects.keySet());
		changed.addAll(getters.keySet());
		return name -> {
			byte[] data = game.read(name);
			return data == null || !changed.contains(name) ? data : transform(name, data);
		};
	}

	/**
	 * The class as the platform's transformers leave it. The class's stack map frames stay as they are: each change swaps
	 * one instruction for another that takes and leaves the same values on the stack.
	 */
	byte[] transform(String name, byte[] data) {
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, 0);
		boolean changed = redirect(node, redirectsIn(name));
		Map<String, String> fields = getters.get(name);
		if (fields != null) {
			for (Map.Entry<String, String> field : fields.entrySet()) changed |= readThroughGetter(node, field.getKey(), field.getValue());
		}
		if (!changed) return data;
		ClassWriter writer = new ClassWriter(0);
		node.accept(writer);
		return writer.toByteArray();
	}

	private static boolean redirect(ClassNode node, List<Redirect> redirects) {
		boolean changed = false;
		for (MethodNode method : node.methods) {
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (!(insn instanceof MethodInsnNode call)) continue;
				for (Redirect r : redirects) {
					if (call.getOpcode() != r.opcode() || !call.name.equals(r.name()) || !call.desc.equals(r.desc())) continue;
					MethodInsnNode hook = new MethodInsnNode(Opcodes.INVOKESTATIC, r.hookOwner(), r.hookName(), r.hookDesc(), false);
					method.instructions.set(call, hook);
					insn = hook;
					changed = true;
					break;
				}
			}
		}
		return changed;
	}

	/**
	 * As NeoForge's {@code ReplaceFieldWithGetterAccess}: every {@code GETFIELD} of a field of that name, in the class's
	 * methods but the getter and those with its descriptor, reads the getter instead. Nothing where the class lacks the
	 * field or the getter (the platform's transformer applies only where both are, or the class fails to load).
	 */
	private static boolean readThroughGetter(ClassNode node, String fieldName, String getterName) {
		FieldNode field = null;
		for (FieldNode f : node.fields) {
			if (f.name.equals(fieldName)) field = f;
		}
		if (field == null || (field.access & Opcodes.ACC_STATIC) != 0) return false;
		String getterDesc = "()" + field.desc;
		MethodNode getter = PreflightAsm.declared(node, getterName, getterDesc);
		if (getter == null) return false;
		boolean changed = false;
		for (MethodNode method : node.methods) {
			if (method == getter || Objects.equals(method.desc, getterDesc)) continue;
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.GETFIELD || !((FieldInsnNode) insn).name.equals(fieldName)) continue;
				MethodInsnNode read = new MethodInsnNode(Opcodes.INVOKEVIRTUAL, node.name, getter.name, getter.desc, false);
				method.instructions.set(insn, read);
				insn = read;
				changed = true;
			}
		}
		return changed;
	}
}
