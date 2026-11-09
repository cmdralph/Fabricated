package io.github.cmdralph.fabricated.loader;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.Opcodes;

import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.mixin.PlatformCoremods;
import io.github.cmdralph.fabricated.core.translate.ModsTomlWriter;
import io.github.cmdralph.fabricated.core.translate.PlatformDescriptor;

/**
 * Forge 47's descriptor: Forge's ({@link PlatformDescriptor.Forge}, its dialect, packages and loot modifiers among the
 * rest), with Forge Config API Port among its replaced mods (CORPUS-81), Forge 47's coremods and private fields, and a
 * {@code pack.mcmeta} in Minecraft 1.20.1's format. It extends Forge's, so it answers as Forge's does for everything it
 * doesn't change, the methods added to the descriptor later among them (PLAT-6, CORPUS-98). Forge 47 builds each mod's
 * pack with vanilla's {@code Pack.readMetaAndCreate} (its {@code ServerLifecycleHooks}), which gives no pack for metadata
 * without a {@code pack_format}, the field 1.20.1 requires where Forge 61 to 66's versions read {@code min_format} and
 * {@code max_format}.
 */
final class ForgeDescriptor extends PlatformDescriptor.Forge {
	/** Minecraft 1.20.1's resource and data pack format. */
	static final int PACK_FORMAT = 15;
	private static final Map<String, String> REPLACED_MODS = withConfigPort(FORGE.replacedMods());
	static final PlatformDescriptor INSTANCE = new ForgeDescriptor();
	private static final Map<String, String> PRIVATE_FIELDS = Map.of(
			"net/minecraft/world/level/biome/Biome f_47437_", "getModifiedClimateSettings",
			"net/minecraft/world/level/biome/Biome f_47443_", "getModifiedSpecialEffects",
			"net/minecraft/world/level/levelgen/structure/Structure f_226555_", "getModifiedStructureSettings",
			"net/minecraft/world/effect/MobEffectInstance f_19502_", "m_19544_",
			"net/minecraft/world/level/block/LiquidBlock f_54689_", "getFluid",
			"net/minecraft/world/item/BucketItem f_40687_", "getFluid",
			"net/minecraft/world/level/block/StairBlock f_56858_", "getModelBlock",
			"net/minecraft/world/level/block/StairBlock f_56859_", "getModelState",
			"net/minecraft/world/level/block/FlowerPotBlock f_53525_", "m_53560_",
			"net/minecraft/world/item/ItemStack f_41589_", "m_41720_");

	/**
	 * The classes Forge 47's {@code method_redirector.js} points each mob's {@code finalizeSpawn} call in at
	 * {@code ForgeEventFactory.onFinalizeSpawn}, its {@code finalize_spawn_targets.json} (read in Forge 47.4's sources,
	 * {@code aff05a4}): NeoForge 21.1's list, which kept Forge's.
	 */
	private static final List<String> SPAWN_TARGETS = List.of(
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
	/** {@code Mob.finalizeSpawn} on 1.20.1, in SRG's name: with the spawn's {@code MobSpawnType} and the entity's tag. */
	private static final String FINALIZE_SPAWN = "(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;"
			+ "Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/world/entity/SpawnGroupData;Lnet/minecraft/nbt/CompoundTag;)"
			+ "Lnet/minecraft/world/entity/SpawnGroupData;";
	private static final PlatformCoremods COREMODS = coremods();

	private ForgeDescriptor() {
	}

	/**
	 * Forge 47's coremods that change game classes, read in Forge 47.4's sources ({@code META-INF/coremods.json}): its
	 * {@code method_redirector.js}, which replaces every {@code INVOKEVIRTUAL} of {@code m_6518_} ({@code finalizeSpawn})
	 * with that descriptor, whatever its owner, in {@link #SPAWN_TARGETS}, with a call of
	 * {@code ForgeEventFactory.onFinalizeSpawn} taking the mob first; and its {@code field_to_method.js}, whose
	 * {@code redirectFieldToMethod} reads {@link #PRIVATE_FIELDS} through their getters, as NeoForge's does. Its
	 * {@code field_to_instanceof.js} has no targets on 1.20.1 and {@code add_bouncer_method.js} targets a class 1.20.1
	 * doesn't have. Pre-flight reads the game's classes as they leave them (CORPUS-61).
	 */
	private static PlatformCoremods coremods() {
		PlatformCoremods.Redirect finalizeSpawn = new PlatformCoremods.Redirect(Opcodes.INVOKEVIRTUAL, "m_6518_", FINALIZE_SPAWN,
				"net/minecraftforge/event/ForgeEventFactory", "onFinalizeSpawn", "(Lnet/minecraft/world/entity/Mob;" + FINALIZE_SPAWN.substring(1));
		Map<String, List<PlatformCoremods.Redirect>> redirects = new HashMap<>();
		for (String target : SPAWN_TARGETS) redirects.put(target, List.of(finalizeSpawn));
		return PlatformCoremods.of(redirects, PRIVATE_FIELDS);
	}

	/**
	 * Forge's (Mod Menu's jar, which Forge's mod list stands in for: REPL-1) and Forge Config API Port's: its version for
	 * 1.20.1 copies Forge 47's own config system, classes and packages included, so its copies were left out for Forge's
	 * and its registry, which makes configs with constructors only its copy has, failed to link (CORPUS-81). The in-game
	 * mod provides its Fabric API on Forge's config system, as NeoForge's does on NeoForge's. (The port's versions for
	 * Forge 61 to 66's Minecraft copy NeoForge's config system, which Forge lacks, and load there: PLAT-6.)
	 */
	@Override
	public Map<String, String> replacedMods() {
		return REPLACED_MODS;
	}

	private static Map<String, String> withConfigPort(Map<String, String> forge) {
		Map<String, String> replaced = new LinkedHashMap<>(forge);
		replaced.put("forgeconfigapiport", "Fabricated provides Forge Config API Port's Fabric API on Forge's own config system, which the port copies");
		return Map.copyOf(replaced);
	}

	/** Forge 47's ({@link #coremods()}); none for a version this descriptor isn't for. */
	@Override
	public PlatformCoremods coremods(String minecraftVersion) {
		return "1.20.1".equals(minecraftVersion) ? COREMODS : PlatformCoremods.NONE;
	}

	/**
	 * The fields Forge 47's {@code field_to_method.js} coremod redirects to a getter, in SRG's names as Forge 47 runs the
	 * game (read in Forge 47.4's sources): its {@code redirectFieldToMethod} needs them private, as Forge 61 to 66's
	 * transformer does (PLAT-4).
	 */
	@Override
	public Map<String, String> privateFields() {
		return PRIVATE_FIELDS;
	}

	@Override
	public byte[] packMetadata(ModsTomlWriter.Spec spec) {
		Map<String, Object> pack = new LinkedHashMap<>();
		pack.put("description", spec.displayName());
		pack.put("pack_format", PACK_FORMAT);
		return (Json.write(Map.of("pack", pack), true) + "\n").getBytes(StandardCharsets.UTF_8);
	}
}
