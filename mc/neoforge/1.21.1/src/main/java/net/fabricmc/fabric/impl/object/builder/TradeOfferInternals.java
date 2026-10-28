package net.fabricmc.fabric.impl.object.builder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.event.village.WandererTradesEvent;

/**
 * Fabric's trade registration (1.21.1) on NeoForge. NeoForge rebuilds the villager and wandering trader tables from
 * vanilla's on every data load and fires {@link VillagerTradesEvent}/{@link WandererTradesEvent}, so anything written
 * straight into them (as upstream does) would be lost. Fabricated records the registrations and replays them in those
 * events instead.
 *
 * <p>Rebalanced trades (the experimental "villager trade rebalance") are vanilla's own tables, which NeoForge leaves
 * alone: a profession's table there is rebuilt from its original contents on every load, as upstream adds to it; the
 * professions without one use the normal tables in rebalanced worlds too, so their rebalanced-only additions are not used.
 * The rebalanced wandering trader's pools are added to as upstream does.
 */
public final class TradeOfferInternals {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");

	private record VillagerOffers(int level, List<VillagerTrades.ItemListing> normal, List<VillagerTrades.ItemListing> rebalanced) {
	}

	private record WandererOffers(int level, List<VillagerTrades.ItemListing> offers) {
	}

	private static final Map<VillagerProfession, List<VillagerOffers>> VILLAGER_OFFERS = new LinkedHashMap<>();
	private static final List<WandererOffers> WANDERER_OFFERS = new ArrayList<>();
	/** Vanilla's rebalanced tables as they were before Fabricated first added to them. */
	private static final Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> REBALANCED_ORIGINALS = new HashMap<>();
	private static boolean warnedWandererLevel;

	private TradeOfferInternals() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(VillagerTradesEvent.class, TradeOfferInternals::onVillagerTrades);
		NeoForge.EVENT_BUS.addListener(WandererTradesEvent.class, TradeOfferInternals::onWandererTrades);
	}

	public static synchronized void registerVillagerOffers(VillagerProfession profession, int level, TradeOfferHelper.VillagerOffersAdder factory) {
		Objects.requireNonNull(profession, "VillagerProfession may not be null.");
		Objects.requireNonNull(factory, "factory may not be null.");
		List<VillagerTrades.ItemListing> normal = new ArrayList<>();
		List<VillagerTrades.ItemListing> rebalanced = new ArrayList<>();
		factory.onRegister(normal, false);
		factory.onRegister(rebalanced, true);
		VILLAGER_OFFERS.computeIfAbsent(profession, key -> new ArrayList<>()).add(new VillagerOffers(level, List.copyOf(normal), List.copyOf(rebalanced)));
	}

	public static synchronized void registerWanderingTraderOffers(int level, Consumer<List<VillagerTrades.ItemListing>> factory) {
		List<VillagerTrades.ItemListing> offers = new ArrayList<>();
		factory.accept(offers);
		WANDERER_OFFERS.add(new WandererOffers(level, List.copyOf(offers)));
	}

	public static void printRefreshOffersWarning() {
		LOGGER.warn("TradeOfferHelper#refreshOffers does not do anything, yet it was called! Stack trace:", new Throwable());
	}

	private static synchronized void onVillagerTrades(VillagerTradesEvent event) {
		List<VillagerOffers> offers = VILLAGER_OFFERS.getOrDefault(event.getType(), List.of());
		for (VillagerOffers offer : offers) {
			event.getTrades().computeIfAbsent(offer.level(), level -> new ArrayList<>()).addAll(offer.normal());
		}
		rebuildRebalanced(event.getType(), offers);
	}

	private static void rebuildRebalanced(VillagerProfession profession, List<VillagerOffers> offers) {
		Int2ObjectMap<VillagerTrades.ItemListing[]> table = VillagerTrades.EXPERIMENTAL_TRADES.get(profession);
		if (table == null || (offers.isEmpty() && !REBALANCED_ORIGINALS.containsKey(profession))) return;
		Int2ObjectMap<VillagerTrades.ItemListing[]> original = REBALANCED_ORIGINALS.computeIfAbsent(profession, key -> new Int2ObjectOpenHashMap<>(table));
		table.clear();
		table.putAll(original);
		for (VillagerOffers offer : offers) {
			VillagerTrades.ItemListing[] current = table.getOrDefault(offer.level(), new VillagerTrades.ItemListing[0]);
			table.put(offer.level(), ArrayUtils.addAll(current, offer.rebalanced().toArray(new VillagerTrades.ItemListing[0])));
		}
	}

	private static synchronized void onWandererTrades(WandererTradesEvent event) {
		for (WandererOffers offer : WANDERER_OFFERS) {
			switch (offer.level()) {
				case 1 -> event.getGenericTrades().addAll(offer.offers());
				case 2 -> event.getRareTrades().addAll(offer.offers());
				default -> {
					// Vanilla's wandering trader only offers levels 1 (generic) and 2 (rare); NeoForge rebuilds no others.
					if (!warnedWandererLevel) {
						warnedWandererLevel = true;
						LOGGER.warn("Fabric mods' wandering trader offers for level {} are not used: the wandering trader only offers levels 1 and 2", offer.level());
					}
				}
			}
		}
	}

	/** Upstream's builder of the rebalanced wandering trader's pools, which NeoForge leaves alone. */
	public static class WanderingTraderOffersBuilderImpl implements TradeOfferHelper.WanderingTraderOffersBuilder {
		private static final Map<ResourceLocation, Integer> ID_TO_INDEX = new HashMap<>(Map.of(BUY_ITEMS_POOL, 0, SELL_SPECIAL_ITEMS_POOL, 1,
				SELL_COMMON_ITEMS_POOL, 2));
		private static final Map<ResourceLocation, VillagerTrades.ItemListing[]> DELAYED_MODIFICATIONS = new HashMap<>();

		/** Makes the pool list modifiable (an access transformer lets it be replaced, as upstream's access widener does). */
		private static void initWanderingTraderTrades() {
			if (!(VillagerTrades.EXPERIMENTAL_WANDERING_TRADER_TRADES instanceof ArrayList)) {
				VillagerTrades.EXPERIMENTAL_WANDERING_TRADER_TRADES = new ArrayList<>(VillagerTrades.EXPERIMENTAL_WANDERING_TRADER_TRADES);
			}
		}

		@Override
		public TradeOfferHelper.WanderingTraderOffersBuilder pool(ResourceLocation id, int count, VillagerTrades.ItemListing... factories) {
			if (factories.length == 0) throw new IllegalArgumentException("cannot add empty pool");
			if (count <= 0) throw new IllegalArgumentException("count must be positive");
			Objects.requireNonNull(id, "id cannot be null");
			synchronized (TradeOfferInternals.class) {
				if (ID_TO_INDEX.containsKey(id)) throw new IllegalArgumentException("pool id %s is already registered".formatted(id));
				initWanderingTraderTrades();
				ID_TO_INDEX.put(id, VillagerTrades.EXPERIMENTAL_WANDERING_TRADER_TRADES.size());
				VillagerTrades.EXPERIMENTAL_WANDERING_TRADER_TRADES.add(Pair.of(Arrays.copyOf(factories, factories.length), count));
				VillagerTrades.ItemListing[] delayed = DELAYED_MODIFICATIONS.remove(id);
				if (delayed != null) addOffersToPool(id, delayed);
			}
			return this;
		}

		@Override
		public TradeOfferHelper.WanderingTraderOffersBuilder addOffersToPool(ResourceLocation pool, VillagerTrades.ItemListing... factories) {
			Objects.requireNonNull(pool, "pool cannot be null");
			synchronized (TradeOfferInternals.class) {
				Integer index = ID_TO_INDEX.get(pool);
				if (index == null) {
					// Upstream keeps additions to a pool registered later.
					DELAYED_MODIFICATIONS.merge(pool, factories, ArrayUtils::addAll);
					return this;
				}
				initWanderingTraderTrades();
				Pair<VillagerTrades.ItemListing[], Integer> current = VillagerTrades.EXPERIMENTAL_WANDERING_TRADER_TRADES.get(index);
				VillagerTrades.EXPERIMENTAL_WANDERING_TRADER_TRADES.set(index, Pair.of(ArrayUtils.addAll(current.getLeft(), factories), current.getRight()));
			}
			return this;
		}
	}
}
