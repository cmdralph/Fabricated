package io.github.cmdralph.fabricated.forgetest;

import java.util.Collection;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * A Fabric mod's built-in resource pack that is enabled by default is switched on the first time the game sees it, as
 * Fabric API does on Fabric (CORPUS-104, with Fabric API's jar). Upstream does that as the options load, which Forge
 * does before the Fabric mods' entrypoints register their packs, so Fabricated runs it once they have run. The pack is
 * the test mod's {@code resourcepacks/default_pack}, registered from its client entrypoint, as a mod registers one.
 */
@Environment(EnvType.CLIENT)
public final class ForgeDefaultPackTest {
	private static final Identifier PACK = Identifier.fromNamespaceAndPath(ForgeTestMod.MOD_ID, "default_pack");
	private static volatile boolean registered;

	private ForgeDefaultPackTest() {
	}

	public static void register() {
		var container = FabricLoader.getInstance().getModContainer(ForgeTestMod.MOD_ID).orElseThrow();
		registered = ResourceLoader.registerBuiltinPack(PACK, container, PackActivationType.DEFAULT_ENABLED);
		if (!registered) ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST builtin resource pack enabled by default FAILED: {} wasn't registered", PACK);
	}

	/** Once the integrated server ticks, the game has selected its packs: the check runs on the render thread. */
	public static void check() {
		if (registered) Minecraft.getInstance().execute(ForgeDefaultPackTest::checkNow);
	}

	private static void checkNow() {
		Collection<String> selected = Minecraft.getInstance().getResourcePackRepository().getSelectedIds();
		if (selected.contains(PACK.toString())) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST builtin resource pack enabled by default OK");
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST builtin resource pack enabled by default FAILED: {} isn't among the game's packs {}", PACK, selected);
		}
	}
}
