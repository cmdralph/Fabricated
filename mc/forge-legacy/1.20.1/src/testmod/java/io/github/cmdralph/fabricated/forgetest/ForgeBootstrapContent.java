package io.github.cmdralph.fabricated.forgetest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.class_1792;
import net.minecraft.class_2378;
import net.minecraft.class_2960;
import net.minecraft.class_7923;

/**
 * Content built while vanilla's bootstrap runs, before any mod is constructed, and registered by the main initializer, as
 * BetterNether's entity types are: its mixin into {@code DefaultAttributes} loads the class that builds them (CORPUS-31).
 * On Fabric nothing checks it in between, since Fabric API leaves the registries open until the main initializers have
 * run; Forge 47 freezes them during the bootstrap. An item here, which has an intrusive holder as an entity type does.
 *
 * <p>Fabric API also moves the built-in registries' freeze ({@code BuiltInRegistries.freeze}) after the main
 * initializers, where Wover makes the item definitions BetterEnd's main initializer subscribes to (CORPUS-32). On Forge
 * that is still to be done: the freeze is only reported.
 */
public final class ForgeBootstrapContent {
	// Not ForgeTestMod's logger: the registries' freeze may come during vanilla's bootstrap, before the mod is constructed.
	private static final Logger LOGGER = LoggerFactory.getLogger(ForgeTestMod.MOD_ID);
	private static class_1792 item;
	private static boolean mainInitializerRan;

	private ForgeBootstrapContent() {
	}

	/** Called as vanilla's bootstrap has created the registries' contents: the item's intrusive holder waits for its registration. */
	public static void build() {
		if (item == null) item = new class_1792(new class_1792.class_1793());
	}

	/** Called as the built-in registries are frozen, from a mixin into {@code BuiltInRegistries.freeze}, as Wover's. */
	public static void frozen() {
		LOGGER.info(mainInitializerRan
				? "FABRICATED-FORGE-TEST built-in registries frozen after the main initializers OK"
				: "FABRICATED-FORGE-TEST built-in registries frozen before the main initializers");
	}

	/** Called from the main initializer: registers the item, as BetterNether registers its entity types. */
	static void register() {
		mainInitializerRan = true;
		if (item == null) {
			LOGGER.error("FABRICATED-FORGE-TEST content built at bootstrap: nothing was built");
			return;
		}
		class_2960 id = new class_2960(ForgeTestMod.MOD_ID, "bootstrap_item");
		class_2378<class_1792> items = class_7923.field_41178;
		class_2378.method_10230(items, id, item);
		if (id.equals(items.method_10221(item))) LOGGER.info("FABRICATED-FORGE-TEST content built at bootstrap registered OK");
	}
}
