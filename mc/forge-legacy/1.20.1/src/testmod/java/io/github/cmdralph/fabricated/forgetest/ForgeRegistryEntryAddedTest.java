package io.github.cmdralph.fabricated.forgetest;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1792;
import net.minecraft.class_2378;
import net.minecraft.class_2960;
import net.minecraft.class_7923;

/**
 * The forge-legacy test mod's part for fabric-registry-sync-v0's entry added event (with Fabric API's jar only,
 * CORPUS-108). A listener on the item registry, registered before the entries it waits for, as Architectury's registry
 * listeners are, must hear of an item registered through vanilla's API, as a Fabric mod registers one, and of an item
 * registered through Forge's ({@code ForgeRegistries.ITEMS.register}, reached by reflection, as a Forge mod registers
 * one), each once, by its name and its raw id, with the registry already holding it. In Intermediary's names (the
 * stand-ins in src/testmodstubs): {@code method_10230} is {@code Registry.register}, {@code method_10223} {@code
 * get(ResourceLocation)}, {@code method_10206} {@code getId(T)}.
 */
public final class ForgeRegistryEntryAddedTest {
	private static final class_2960 VANILLA_API = new class_2960(ForgeTestMod.MOD_ID, "entry_added_vanilla");
	private static final class_2960 FORGE_API = new class_2960(ForgeTestMod.MOD_ID, "entry_added_forge");

	private ForgeRegistryEntryAddedTest() {
	}

	/** From the main entrypoint, with the registries open, as Fabric mods register their entries and listeners. */
	static void register() {
		if (!FabricLoader.getInstance().isModLoaded("fabric-registry-sync-v0")) return;
		class_2378<class_1792> items = class_7923.field_41178;
		List<String> heard = new ArrayList<>();
		try {
			RegistryEntryAddedCallback.event(items).register((rawId, id, item) -> {
				if (id.equals(VANILLA_API) || id.equals(FORGE_API)) heard.add(id + " " + rawId + (item == items.method_10223(id) ? "" : " not yet registered"));
			});
			class_1792 vanilla = class_2378.method_10230(items, VANILLA_API, new class_1792(new class_1792.class_1793()));
			class_1792 forge = new class_1792(new class_1792.class_1793());
			Object forgeItems = Class.forName("net.minecraftforge.registries.ForgeRegistries").getField("ITEMS").get(null);
			forgeItems.getClass().getMethod("register", class_2960.class, Object.class).invoke(forgeItems, FORGE_API, forge);
			List<String> expected = List.of(VANILLA_API + " " + items.method_10206(vanilla), FORGE_API + " " + items.method_10206(forge));
			if (heard.equals(expected)) {
				ForgeTestMod.LOGGER.info("FABRICATED-TEST registry entry added events OK");
			} else {
				ForgeTestMod.LOGGER.error("FABRICATED-TEST registry entry added events FAILED: heard {}, expected {}", heard, expected);
			}
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST registry entry added events FAILED", e);
		}
	}
}
