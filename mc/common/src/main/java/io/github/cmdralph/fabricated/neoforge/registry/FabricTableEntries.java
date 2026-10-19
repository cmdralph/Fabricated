package io.github.cmdralph.fabricated.neoforge.registry;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;

/**
 * Entries Fabric code put into vanilla's lookup tables. Fabric's registries (composting chances, vibration
 * frequencies, oxidation and waxing) write those tables, which NeoForge replaced with data maps; where a data map has
 * no entry, Fabricated looks there. Only what Fabric code added or changed counts: vanilla's own entries are the data
 * maps' business, so a data pack that removes one still removes it.
 *
 * <p>Vanilla's tables are recorded just before Fabric mods' initializers run; until then (and without Fabric mods)
 * nothing counts.
 */
public final class FabricTableEntries {
	private record Vanilla(Map<ResourceKey<GameEvent>, Integer> vibrations, Map<ItemLike, Float> compostables,
			Map<Block, Block> nextStages, Map<Block, Block> previousStages, Map<Block, Block> waxed, Map<Block, Block> unwaxed) {
	}

	/** Vanilla's composting chances; up to 26.2 only (26.3's are data-driven, with no table). */
	private static final Map<ItemLike, Float> COMPOSTABLES = compostables();
	private static volatile @Nullable Vanilla vanilla;

	private FabricTableEntries() {
	}

	/** Records vanilla's tables; called once, before Fabric mods' initializers. */
	public static void recordVanilla() {
		vanilla = new Vanilla(new HashMap<>(vibrationTable()), new HashMap<>(COMPOSTABLES),
				new HashMap<>(WeatheringCopper.NEXT_BY_BLOCK.get()), new HashMap<>(WeatheringCopper.PREVIOUS_BY_BLOCK.get()),
				new HashMap<>(HoneycombItem.WAXABLES.get()), new HashMap<>(HoneycombItem.WAX_OFF_BY_BLOCK.get()));
	}

	/** The vibration frequency Fabric code gave {@code event}, or 0. */
	public static int vibrationFrequency(Holder<GameEvent> event) {
		Vanilla v = vanilla;
		if (v == null || !(event instanceof Holder.Reference<GameEvent> reference)) return 0;
		Integer frequency = added(vibrationTable(), v.vibrations(), reference.key());
		return frequency != null ? frequency : 0;
	}

	/** The composting chance Fabric code gave {@code stack}'s item, or -1. */
	public static float compostChance(ItemStack stack) {
		Vanilla v = vanilla;
		if (v == null) return -1f;
		Float chance = added(COMPOSTABLES, v.compostables(), stack.getItem());
		return chance != null ? chance : -1f;
	}

	public static @Nullable Block nextOxidationStage(Block block) {
		Vanilla v = vanilla;
		return v == null ? null : added(WeatheringCopper.NEXT_BY_BLOCK.get(), v.nextStages(), block);
	}

	public static @Nullable Block previousOxidationStage(Block block) {
		Vanilla v = vanilla;
		return v == null ? null : added(WeatheringCopper.PREVIOUS_BY_BLOCK.get(), v.previousStages(), block);
	}

	public static @Nullable Block waxed(Block block) {
		Vanilla v = vanilla;
		return v == null ? null : added(HoneycombItem.WAXABLES.get(), v.waxed(), block);
	}

	public static @Nullable Block unwaxed(Block block) {
		Vanilla v = vanilla;
		return v == null ? null : added(HoneycombItem.WAX_OFF_BY_BLOCK.get(), v.unwaxed(), block);
	}

	/** {@code table}'s value for {@code key} if it isn't vanilla's, else null. */
	private static <K, V> @Nullable V added(Map<? super K, ? extends V> table, Map<? super K, ? extends V> vanilla, K key) {
		V value = table.get(key);
		return value != null && !value.equals(vanilla.get(key)) ? value : null;
	}

	@SuppressWarnings("unchecked")
	private static Map<ItemLike, Float> compostables() {
		try {
			return (Map<ItemLike, Float>) ComposterBlock.class.getField("COMPOSTABLES").get(null);
		} catch (NoSuchFieldException e) {
			return Map.of();
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Vanilla's frequency table, a {@code Reference2IntOpenHashMap} behind a {@code ToIntFunction} (as upstream relies on). */
	@SuppressWarnings("unchecked")
	private static Map<ResourceKey<GameEvent>, Integer> vibrationTable() {
		return (Map<ResourceKey<GameEvent>, Integer>) (Object) VibrationSystem.VIBRATION_FREQUENCY_FOR_EVENT;
	}
}
