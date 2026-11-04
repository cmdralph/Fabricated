package io.github.cmdralph.fabricated.forgetest;

import java.util.Collection;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_2960;
import net.minecraft.class_310;

/**
 * A Fabric mod's built-in resource pack that is enabled by default is switched on the first time the game sees it, as
 * Fabric API does on Fabric (CORPUS-104, with Fabric API's jar). Upstream does that as the options load, which Forge
 * does before the Fabric mods' entrypoints register their packs, so Fabricated does it once they have run. The pack is
 * the test mod's {@code resourcepacks/default_pack}, registered from its client entrypoint, as a mod registers one. In
 * Intermediary's names: {@code class_310} is {@code Minecraft}, {@code method_1520} {@code getResourcePackRepository}
 * and {@code method_29210} {@code getSelectedIds}.
 */
@Environment(EnvType.CLIENT)
public final class ForgeDefaultPackTest {
	private static final class_2960 PACK = new class_2960(ForgeTestMod.MOD_ID, "default_pack");
	private static volatile boolean registered;

	private ForgeDefaultPackTest() {
	}

	/** From the client entrypoint; only with Fabric API's resource loader. */
	static void register() {
		var container = FabricLoader.getInstance().getModContainer(ForgeTestMod.MOD_ID).orElseThrow();
		registered = ResourceManagerHelper.registerBuiltinResourcePack(PACK, container, ResourcePackActivationType.DEFAULT_ENABLED);
		if (!registered) ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST builtin resource pack enabled by default FAILED: {} wasn't registered", PACK);
	}

	/** Once the integrated server ticks, the game has selected its packs: the check runs on the render thread. */
	static void check() {
		if (registered) class_310.method_1551().execute(ForgeDefaultPackTest::checkNow);
	}

	private static void checkNow() {
		Collection<String> selected = class_310.method_1551().method_1520().method_29210();
		if (selected.contains(PACK.toString())) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST builtin resource pack enabled by default OK");
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST builtin resource pack enabled by default FAILED: {} isn't among the game's packs {}", PACK, selected);
		}
	}
}
