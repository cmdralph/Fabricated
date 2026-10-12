package net.fabricmc.fabric.impl.object.builder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerTrades;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.event.village.WandererTradesEvent;

/**
 * Fabric's trade registration (1.21.11) on NeoForge. NeoForge rebuilds the trade tables from vanilla's on every data
 * load and fires {@link VillagerTradesEvent}/{@link WandererTradesEvent}, so anything written straight into
 * {@code VillagerTrades} (as upstream does) would be lost. Fabricated records the registrations and replays them in
 * those events instead.
 *
 * <p>Rebalanced trades (the experimental "villager trade rebalance"): vanilla keeps separate tables for a few
 * professions only, and NeoForge leaves them alone. Fabricated rebuilds those from their original contents on every
 * load; the other professions use the normal tables in rebalanced worlds too, so their rebalanced-only additions are
 * not used.
 */
public final class TradeOfferInternals {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");

	private record VillagerOffers(int level, List<VillagerTrades.ItemListing> normal, List<VillagerTrades.ItemListing> rebalanced) {
	}

	private sealed interface WandererOp {
	}

	private record AddPool(Identifier id, int count, List<VillagerTrades.ItemListing> offers) implements WandererOp {
	}

	private record AddToPool(Identifier pool, List<VillagerTrades.ItemListing> offers) implements WandererOp {
	}

	private static final Map<ResourceKey<VillagerProfession>, List<VillagerOffers>> VILLAGER_OFFERS = new LinkedHashMap<>();
	private static final List<WandererOp> WANDERER_OPS = new ArrayList<>();
	private static final Set<Identifier> WANDERER_POOLS = new HashSet<>(Set.of(
			TradeOfferHelper.WanderingTraderOffersBuilder.BUY_ITEMS_POOL,
			TradeOfferHelper.WanderingTraderOffersBuilder.SELL_SPECIAL_ITEMS_POOL,
			TradeOfferHelper.WanderingTraderOffersBuilder.SELL_COMMON_ITEMS_POOL));
	/** Vanilla's rebalanced tables as they were before Fabricated first added to them. */
	private static final Map<ResourceKey<VillagerProfession>, Int2ObjectMap<VillagerTrades.ItemListing[]>> REBALANCED_ORIGINALS = new HashMap<>();
	private static boolean warnedRebalanced;

	private TradeOfferInternals() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(VillagerTradesEvent.class, TradeOfferInternals::onVillagerTrades);
		NeoForge.EVENT_BUS.addListener(WandererTradesEvent.class, TradeOfferInternals::onWandererTrades);
	}

	public static synchronized void registerVillagerOffers(ResourceKey<VillagerProfession> profession, int level, TradeOfferHelper.VillagerOffersAdder factory) {
		Objects.requireNonNull(profession, "VillagerProfession may not be null.");
		Objects.requireNonNull(factory, "factory may not be null.");
		List<VillagerTrades.ItemListing> normal = new ArrayList<>();
		List<VillagerTrades.ItemListing> rebalanced = new ArrayList<>();
		factory.onRegister(normal, false);
		factory.onRegister(rebalanced, true);
		VILLAGER_OFFERS.computeIfAbsent(profession, key -> new ArrayList<>()).add(new VillagerOffers(level, List.copyOf(normal), List.copyOf(rebalanced)));
	}

	private static synchronized void onVillagerTrades(VillagerTradesEvent event) {
		List<VillagerOffers> offers = VILLAGER_OFFERS.getOrDefault(event.getType(), List.of());
		for (VillagerOffers offer : offers) {
			List<VillagerTrades.ItemListing> trades = event.getTrades().get(offer.level());
			if (trades == null) {
				trades = new ArrayList<>();
				event.getTrades().put(offer.level(), trades);
			}
			trades.addAll(offer.normal());
		}
		rebuildRebalanced(event.getType(), offers);
	}

	private static void rebuildRebalanced(ResourceKey<VillagerProfession> profession, List<VillagerOffers> offers) {
		Int2ObjectMap<VillagerTrades.ItemListing[]> table = VillagerTrades.EXPERIMENTAL_TRADES.get(profession);
		if (table == null || (offers.isEmpty() && !REBALANCED_ORIGINALS.containsKey(profession))) return;
		Int2ObjectMap<VillagerTrades.ItemListing[]> original = REBALANCED_ORIGINALS.computeIfAbsent(profession, key -> new Int2ObjectOpenHashMap<>(table));
		try {
			table.clear();
			table.putAll(original);
			for (VillagerOffers offer : offers) {
				VillagerTrades.ItemListing[] current = table.getOrDefault(offer.level(), new VillagerTrades.ItemListing[0]);
				VillagerTrades.ItemListing[] combined = Arrays.copyOf(current, current.length + offer.rebalanced().size());
				for (int i = 0; i < offer.rebalanced().size(); i++) {
					combined[current.length + i] = offer.rebalanced().get(i);
				}
				table.put(offer.level(), combined);
			}
		} catch (UnsupportedOperationException e) {
			if (!warnedRebalanced) {
				warnedRebalanced = true;
				LOGGER.warn("Fabric mods' rebalanced villager trades can't be added: the table for {} is read-only", profession.identifier());
			}
		}
	}

	private static synchronized void onWandererTrades(WandererTradesEvent event) {
		Map<Identifier, List<VillagerTrades.ItemListing>> pools = new HashMap<>();
		pools.put(TradeOfferHelper.WanderingTraderOffersBuilder.BUY_ITEMS_POOL, event.getBuyingTrades());
		pools.put(TradeOfferHelper.WanderingTraderOffersBuilder.SELL_SPECIAL_ITEMS_POOL, event.getRareTrades());
		pools.put(TradeOfferHelper.WanderingTraderOffersBuilder.SELL_COMMON_ITEMS_POOL, event.getGenericTrades());
		Map<Identifier, List<VillagerTrades.ItemListing>> delayed = new HashMap<>();
		for (WandererOp op : WANDERER_OPS) {
			switch (op) {
				case AddPool pool -> {
					List<VillagerTrades.ItemListing> list = new ArrayList<>(pool.offers());
					List<VillagerTrades.ItemListing> waiting = delayed.remove(pool.id());
					if (waiting != null) list.addAll(waiting);
					pools.put(pool.id(), list);
					// NeoForge turns the list into the pool after every listener has run, so later additions count.
					event.addTrades(list, pool.count());
				}
				case AddToPool add -> {
					List<VillagerTrades.ItemListing> list = pools.get(add.pool());
					if (list != null) {
						list.addAll(add.offers());
					} else {
						// Upstream keeps additions to a pool registered later.
						delayed.computeIfAbsent(add.pool(), id -> new ArrayList<>()).addAll(add.offers());
					}
				}
			}
		}
	}

	public static class WanderingTraderOffersBuilderImpl implements TradeOfferHelper.WanderingTraderOffersBuilder {
		@Override
		public TradeOfferHelper.WanderingTraderOffersBuilder pool(Identifier id, int count, VillagerTrades.ItemListing... factories) {
			if (factories.length == 0) throw new IllegalArgumentException("cannot add empty pool");
			if (count <= 0) throw new IllegalArgumentException("count must be positive");
			Objects.requireNonNull(id, "id cannot be null");
			synchronized (TradeOfferInternals.class) {
				if (!WANDERER_POOLS.add(id)) throw new IllegalArgumentException("pool id %s is already registered".formatted(id));
				WANDERER_OPS.add(new AddPool(id, count, List.of(factories)));
			}
			return this;
		}

		@Override
		public TradeOfferHelper.WanderingTraderOffersBuilder addOffersToPool(Identifier pool, VillagerTrades.ItemListing... factories) {
			Objects.requireNonNull(pool, "pool cannot be null");
			synchronized (TradeOfferInternals.class) {
				WANDERER_OPS.add(new AddToPool(pool, List.of(factories)));
			}
			return this;
		}
	}
}
