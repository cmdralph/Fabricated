package io.github.cmdralph.fabricated.forgetest;

import net.minecraft.class_1792;
import net.minecraft.class_2378;
import net.minecraft.class_2960;
import net.minecraft.class_7923;

/**
 * Content built while vanilla's bootstrap runs, before any mod is constructed, and registered by the main initializer, as
 * BetterNether's entity types are: its mixin into {@code DefaultAttributes} loads the class that builds them (CORPUS-31).
 * On Fabric nothing checks it in between, since Fabric API leaves the registries open until the main initializers have
 * run; Forge 47 freezes them during the bootstrap. An item here, which has an intrusive holder as an entity type does.
 */
public final class ForgeBootstrapContent {
	private static class_1792 item;
	private static boolean mainInitializerRan;

	private ForgeBootstrapContent() {
	}

	/**
	 * Called as the registries are frozen: during vanilla's bootstrap, the item's intrusive holder waits for its
	 * registration. Nothing is built at a freeze after the main initializer, where Fabric API's registry sync, passed
	 * through, moves the bootstrap's (seen on the reproduction's run with Fabric API, where building it there failed:
	 * "Registry is already frozen").
	 */
	public static void build() {
		if (item == null && !mainInitializerRan) item = new class_1792(new class_1792.class_1793());
	}

	/** Called from the main initializer: registers the item, as BetterNether registers its entity types. */
	static void register() {
		mainInitializerRan = true;
		if (item == null) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST content built at bootstrap: none, as no freeze came before the main initializers");
			return;
		}
		class_2960 id = new class_2960(ForgeTestMod.MOD_ID, "bootstrap_item");
		class_2378<class_1792> items = class_7923.field_41178;
		class_2378.method_10230(items, id, item);
		if (id.equals(items.method_10221(item))) ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST content built at bootstrap registered OK");
	}
}
