package io.github.cmdralph.fabricated.forge.content;

import java.util.Map;

import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.fabricmc.fabric.impl.content.registry.FuelRegistryImpl;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.Item;

/**
 * Fabric API's fuels ({@code FuelRegistry}, fabric-content-registries-v0, passed through) in vanilla's fuel table on
 * Forge 47 and 52 (FUEL-1, FUEL-2). Forge's furnace asks its own burn times ({@code ForgeHooks.getBurnTime}) where
 * vanilla's {@code isFuel} and {@code getBurnDuration} build the fuel table, which upstream's two redirects look for, so
 * pre-flight leaves upstream's furnace mixin out, and with it the hook that adds Fabric's fuels to the table: a fuel
 * added through {@code FuelRegistry} never burned. Forge reads its burn times from that same table ({@code getFuel}) as
 * the data packs' tags load ({@code ForgeHooks.updateBurns}), so {@code FurnaceFuelTableMixin} adds Fabric's fuels
 * there, as upstream's hook did.
 */
public final class FabricFuels {
	/** Fabric API's content registries module is installed. Its classes are only touched then. */
	private static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("fabric-content-registries-v0");

	private FabricFuels() {
	}

	/** Adds Fabric's fuels to a fuel table vanilla just built, and removes the ones Fabric's registry removed. */
	public static void addTo(Map<Item, Integer> table) {
		if (ENABLED) Upstream.addTo(table);
	}

	/** Everything that names Fabric's content registries module: loaded only once {@link #ENABLED} is known to be true. */
	private static final class Upstream {
		private Upstream() {
		}

		static void addTo(Map<Item, Integer> table) {
			((FuelRegistryImpl) FuelRegistry.INSTANCE).apply(table);
		}
	}
}
