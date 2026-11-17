package io.github.cmdralph.fabricated.forgetest;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * The forge-legacy test mod's part for fabric-resource-loader-v0's default data packs of a new world (with Fabric API's
 * jar only): a built-in data pack a Fabric mod registers for the player to enable ({@code NORMAL}) must be offered and
 * not enabled in a world made on the world creation screen, as Fabric API's hooks into the screen leave it, which mc-
 * runtime-test opens for the client's world. Forge 43 and 47 enable every pack their screen finds that its starting
 * packs don't disable: on Forge 47 upstream's hook (Fabric API 0.92) adds Fabric's disabled packs to them, and on Forge
 * 43 Fabricated's in-game mod does, as Fabric API 0.77's hook reads vanilla's starting packs, which Forge's screen no
 * longer reads.
 */
public final class ForgeOptionalPackTest {
	private static final String PATH = "optional";

	private ForgeOptionalPackTest() {
	}

	/** From the main entrypoint; only with Fabric API's resource loader. */
	static void register() {
		FabricLoader.getInstance().getModContainer("fabricated-testmod").ifPresent(container -> {
			if (!ResourceManagerHelper.registerBuiltinResourcePack(new ResourceLocation(ForgeTestMod.NAMESPACE, PATH), container, ResourcePackActivationType.NORMAL)) {
				ForgeTestMod.LOGGER.error("FABRICATED-TEST optional data pack FAILED: its registration was refused");
			}
		});
	}

	/** On a client, at the integrated server's first tick. */
	static void check(MinecraftServer server) {
		PackRepository packs = server.getPackRepository();
		String id = ForgeTestCompat.builtinPackId(ForgeTestMod.NAMESPACE, PATH);
		if (packs.getAvailableIds().contains(id) && !packs.getSelectedIds().contains(id)) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST optional data pack left disabled OK");
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST optional data pack left disabled FAILED: {} is {}offered and {}enabled (enabled: {})", id,
					packs.getAvailableIds().contains(id) ? "" : "not ", packs.getSelectedIds().contains(id) ? "" : "not ", packs.getSelectedIds());
		}
	}
}
