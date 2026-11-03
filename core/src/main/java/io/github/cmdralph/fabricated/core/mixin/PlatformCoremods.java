package io.github.cmdralph.fabricated.core.mixin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.json.JsonException;

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
 * coremods on 1.21.x, 26.1.x, 26.2.x and 26.3.x.
 *
 * <p>Forge 61 to 66 run theirs as ModLauncher {@code ITransformer}s too ({@code forge-transformers}' {@code
 * ForgeModTransformers}), so before Mixin, and pre-flight reads the game as they leave it ({@link #forge}, CORPUS-78):
 * their {@code MethodRedirector} points a mob's {@code finalizeSpawn} call at {@code ForgeEventFactory.onFinalizeSpawn}
 * only in the methods {@code finalize_spawn_targets.json} lists and in every synthetic method (lambda bodies) of the
 * classes it lists; their {@code FieldToMethodTransformer} reads fields through their getters everywhere in the class but
 * the getter itself (its check meant to skip methods with the getter's descriptor compares a string with a method, so it
 * never does). Forge 47's are described on its branch.
 */
public final class PlatformCoremods {
	/**
	 * A call the platform's code points at a static hook taking the call's receiver first, in the classes listed: in
	 * every method there, or, where {@code methods} names some ({@code name + descriptor}), only in those, and with
	 * {@code synthetic} in every synthetic method too (Forge 61 to 66's {@code MethodRedirector}).
	 */
	public record Redirect(int opcode, String name, String desc, String hookOwner, String hookName, String hookDesc, Set<String> methods,
			boolean synthetic) {
		public Redirect {
			methods = methods == null ? null : Set.copyOf(methods);
		}

		/** A redirect in every method of the classes it applies in (NeoForge 21.1's, Forge 47's). */
		public Redirect(int opcode, String name, String desc, String hookOwner, String hookName, String hookDesc) {
			this(opcode, name, desc, hookOwner, hookName, hookDesc, null, false);
		}

		/** Whether it applies in the method. */
		boolean appliesIn(MethodNode method) {
			return methods == null || methods.contains(method.name + method.desc) || synthetic && (method.access & Opcodes.ACC_SYNTHETIC) != 0;
		}
	}

	/** None: the classes as the platform's jars have them. */
	public static final PlatformCoremods NONE = new PlatformCoremods(Map.of(), Map.of(), true);

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

	/**
	 * Whether a field's reads stay in the methods with its getter's descriptor, as NeoForge's and Forge 47's transformers
	 * leave them; Forge 61 to 66's read it through the getter there too.
	 */
	private final boolean skipsGetterShaped;

	private PlatformCoremods(Map<String, List<Redirect>> redirects, Map<String, Map<String, String>> getters, boolean skipsGetterShaped) {
		this.redirects = redirects;
		this.getters = getters;
		this.skipsGetterShaped = skipsGetterShaped;
	}

	/**
	 * Given redirects, by the classes they apply in, and fields read through getters ({@code owner field} to the getter),
	 * except in the getter and in the methods with its descriptor (NeoForge's and Forge 47's rule).
	 */
	public static PlatformCoremods of(Map<String, List<Redirect>> redirects, Map<String, String> getterFields) {
		return of(redirects, getterFields, true);
	}

	/**
	 * Given redirects and fields read through getters, except in the getter and, with {@code skipsGetterShaped}, in the
	 * methods with its descriptor.
	 */
	public static PlatformCoremods of(Map<String, List<Redirect>> redirects, Map<String, String> getterFields, boolean skipsGetterShaped) {
		Map<String, Map<String, String>> getters = new HashMap<>();
		getterFields.forEach((field, getter) -> {
			int space = field.indexOf(' ');
			getters.computeIfAbsent(field.substring(0, space), k -> new HashMap<>()).put(field.substring(space + 1), getter);
		});
		return new PlatformCoremods(Map.copyOf(redirects), Map.copyOf(getters), skipsGetterShaped);
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

	/** {@code Mob.finalizeSpawn}'s descriptor from 1.21.3 on, with the spawn's {@code EntitySpawnReason}. */
	private static final String FINALIZE_SPAWN_REASON = "(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;"
			+ "Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/world/entity/SpawnGroupData;)Lnet/minecraft/world/entity/SpawnGroupData;";
	private static final String FORGE_EVENT_FACTORY = "net/minecraftforge/event/ForgeEventFactory";

	/**
	 * Forge 61 to 66's, for a Minecraft version (1.21.11, 26.1.x, 26.2.x, 26.3.x); {@link #NONE} for another, Forge 47's
	 * 1.20.1 among them (its branch describes its JavaScript coremods). Read in MinecraftForge's {@code forge-transformers}
	 * sources, branches 1.21.11, 26.1, 26.2 and 26.3: {@code MethodRedirector}'s one replacement, the {@code INVOKEVIRTUAL}
	 * of {@code finalizeSpawn} with {@link #FINALIZE_SPAWN_REASON}, whatever its owner, by
	 * {@code ForgeEventFactory.onFinalizeSpawn} taking the mob first, in the methods its {@code finalize_spawn_targets.json}
	 * lists (this package's {@code forge-finalize-spawn-targets-<version>.json}, copied from each branch) and every
	 * synthetic method of those classes; and {@code FieldToMethodTransformer}'s fields, those of
	 * {@link AccessTransformerConverter#FORGE_PRIVATE_FIELDS} but {@code Biome}'s on 26.3, which it no longer redirects.
	 */
	public static PlatformCoremods forge(String minecraftVersion) {
		String line = forgeLine(minecraftVersion);
		if (line == null) return NONE;
		Map<String, List<Redirect>> redirects = new HashMap<>();
		forgeSpawnTargets(line).forEach((target, methods) -> redirects.put(target, List.of(new Redirect(Opcodes.INVOKEVIRTUAL, "finalizeSpawn",
				FINALIZE_SPAWN_REASON, FORGE_EVENT_FACTORY, "onFinalizeSpawn", "(L" + MOB + ";" + FINALIZE_SPAWN_REASON.substring(1), methods, true))));
		Map<String, String> getters = new HashMap<>(AccessTransformerConverter.FORGE_PRIVATE_FIELDS);
		if (line.equals("26.3")) getters.keySet().removeIf(field -> field.startsWith("net/minecraft/world/level/biome/Biome "));
		return of(redirects, getters, false);
	}

	/** The version whose Forge transformers' description applies: {@code 1.21.11}, {@code 26.1}, {@code 26.2} or {@code 26.3}; else null. */
	static String forgeLine(String minecraftVersion) {
		if (minecraftVersion == null) return null;
		if (minecraftVersion.equals("1.21.11")) return minecraftVersion;
		for (String line : List.of("26.1", "26.2", "26.3")) {
			if (minecraftVersion.equals(line) || minecraftVersion.startsWith(line + ".")) return line;
		}
		return null;
	}

	/**
	 * The classes of Forge's {@code finalize_spawn_targets.json} for that version, each with the methods it names (empty
	 * where it names none: only its synthetic methods are changed then), as this build ships the file.
	 */
	static Map<String, Set<String>> forgeSpawnTargets(String line) {
		String file = "forge-finalize-spawn-targets-" + line + ".json";
		InputStream in = PlatformCoremods.class.getResourceAsStream(file);
		if (in == null) throw new IllegalStateException("This Fabricated build is missing its " + file);
		try (in) {
			Map<String, Set<String>> targets = new HashMap<>();
			if (!(Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)) instanceof List<?> entries)) throw new JsonException("not a list of classes");
			for (Object entry : entries) {
				if (!(entry instanceof Map<?, ?> target) || !(target.get("class") instanceof String owner) || !(target.get("methods") instanceof List<?> methods)) {
					throw new JsonException("an entry is not a class with its methods: " + entry);
				}
				Set<String> named = new LinkedHashSet<>();
				for (Object method : methods) {
					if (!(method instanceof String m) || m.indexOf('(') <= 0) throw new JsonException("not a method of " + owner + ": " + method);
					named.add(m);
				}
				// As MethodRedirector reads it: a class's first entry is the one that applies.
				targets.putIfAbsent(owner, named);
			}
			return targets;
		} catch (IOException | JsonException | RuntimeException e) {
			throw new IllegalStateException("This Fabricated build's " + file + " is broken: " + e.getMessage(), e);
		}
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
			for (Map.Entry<String, String> field : fields.entrySet()) changed |= readThroughGetter(node, field.getKey(), field.getValue(), skipsGetterShaped);
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
					if (call.getOpcode() != r.opcode() || !call.name.equals(r.name()) || !call.desc.equals(r.desc()) || !r.appliesIn(method)) continue;
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
	 * methods but the getter and, with {@code skipsGetterShaped}, those with its descriptor, reads the getter instead.
	 * Nothing where the class lacks the field or the getter (the platform's transformer applies only where both are, or
	 * the class fails to load).
	 */
	private static boolean readThroughGetter(ClassNode node, String fieldName, String getterName, boolean skipsGetterShaped) {
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
			if (method == getter || skipsGetterShaped && Objects.equals(method.desc, getterDesc)) continue;
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
