package io.github.cmdralph.fabricated.forge.resource;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.impl.resource.loader.ModResourcePackUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.DataPackConfig;

/**
 * The data packs a new world starts with, as Fabric API's resource loader (fabric-resource-loader-v0, passed through)
 * gives them, on Forge 43's world creation screen. Fabric API 0.77 replaces vanilla's default packs where the screen
 * opens ({@code CreateWorldScreen.openFresh}) with its own ({@code ModResourcePackUtil.createDefaultDataPackSettings}):
 * the Fabric mods' own pack and their built-in data packs enabled by default go with vanilla's, and the built-in packs
 * a player enables themselves are disabled. Its hook reads vanilla's defaults ({@code DataPackConfig.DEFAULT}), which
 * Forge 43's screen no longer reads: it starts from a list of vanilla's pack of its own, and every pack it finds is
 * then enabled, a Fabric mod's optional built-in pack too. Fabricated's Fabric API plan leaves upstream's hook out, and
 * its mixin adds upstream's packs to Forge's list, both where the screen loads its first data and where it keeps the
 * packs for the world, as upstream's hook does with vanilla's.
 */
public final class FabricDataPackDefaults {
	/** Fabric API's resource loader is installed. Its classes are only touched then. */
	private static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("fabric-resource-loader-v0");

	private FabricDataPackDefaults() {
	}

	/** Forge's packs and upstream's: those it enables after Forge's enabled ones, those it disables after Forge's disabled ones. */
	public static DataPackConfig with(DataPackConfig forge) {
		if (!ENABLED) return forge;
		DataPackConfig fabric = Upstream.defaults();
		return new DataPackConfig(union(forge.getEnabled(), fabric.getEnabled()), union(forge.getDisabled(), fabric.getDisabled()));
	}

	private static List<String> union(List<String> first, List<String> then) {
		List<String> ids = new ArrayList<>(first);
		for (String id : then) {
			if (!ids.contains(id)) ids.add(id);
		}
		return ids;
	}

	/** Everything that names Fabric's resource loader: loaded only once {@link #ENABLED} is known to be true. */
	private static final class Upstream {
		private Upstream() {
		}

		static DataPackConfig defaults() {
			return ModResourcePackUtil.createDefaultDataPackSettings();
		}
	}
}
