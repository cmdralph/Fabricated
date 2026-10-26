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

	private ForgeBootstrapContent() {
	}

	/** Called as vanilla's bootstrap freezes the registries: the item's intrusive holder waits for its registration. */
	public static void build() {
		if (item == null) item = new class_1792(new class_1792.class_1793());
	}

	/** Called from the main initializer: registers the item, as BetterNether registers its entity types. */
	static void register() {
		if (item == null) {
			ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST content built at bootstrap FAILED: vanilla's bootstrap did not build it");
			return;
		}
		class_2960 id = new class_2960(ForgeTestMod.MOD_ID, "bootstrap_item");
		class_2378<class_1792> items = class_7923.field_41178;
		class_2378.method_10230(items, id, item);
		if (id.equals(items.method_10221(item))) ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST content built at bootstrap registered OK");
	}
}
