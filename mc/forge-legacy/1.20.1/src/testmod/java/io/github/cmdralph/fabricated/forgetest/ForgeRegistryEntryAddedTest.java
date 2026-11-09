package io.github.cmdralph.fabricated.forgetest;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The forge-legacy test mod's part for fabric-registry-sync-v0's entry added event (with Fabric API's jar only,
 * CORPUS-108). A listener on the item registry, registered before the entries it waits for, as Architectury's registry
 * listeners are, must hear of an item registered through vanilla's API, as a Fabric mod registers one, and of an item
 * registered through Forge's ({@code ForgeRegistries.ITEMS.register}, reached by reflection, as a Forge mod registers
 * one), each once, by its name and its raw id, with the registry already holding it.
 */
public final class ForgeRegistryEntryAddedTest {
	private static final ResourceLocation VANILLA_API = new ResourceLocation(ForgeTestMod.NAMESPACE, "entry_added_vanilla");
	private static final ResourceLocation FORGE_API = new ResourceLocation(ForgeTestMod.NAMESPACE, "entry_added_forge");
	/** How another mod's module answers, as its source reads (Forgified Fabric API's, read in its 1.20.1 build). */
	private static final Map<String, String> PROVIDERS_ANSWERS = Map.of(
			"vanilla_api", "its event fires from Forge's registry wrapper (NamespacedWrapperMixin, as registerMapping returns) with the id the"
					+ " registration asked for, which one by name leaves -1 for Forge's registry to pick",
			"forge_api", "its event fires from Forge's registry wrapper only, which a registration through Forge's own API doesn't go through");

	private ForgeRegistryEntryAddedTest() {
	}

	/** From the main entrypoint, with the registries open, as Fabric mods register their entries and listeners. */
	static void register() {
		if (!FabricLoader.getInstance().isModLoaded("fabric-registry-sync-v0")) return;
		Registry<Item> items = BuiltInRegistries.ITEM;
		List<String> heard = new ArrayList<>();
		try {
			RegistryEntryAddedCallback.event(items).register((rawId, id, item) -> {
				if (id.equals(VANILLA_API) || id.equals(FORGE_API)) heard.add(id + " " + rawId + (item == items.get(id) ? "" : " not yet registered"));
			});
			Item vanilla = Registry.register(items, VANILLA_API, new Item(new Item.Properties()));
			Item forge = new Item(new Item.Properties());
			Object forgeItems = Class.forName("net.minecraftforge.registries.ForgeRegistries").getField("ITEMS").get(null);
			forgeItems.getClass().getMethod("register", ResourceLocation.class, Object.class).invoke(forgeItems, FORGE_API, forge);
			List<String> failed = new ArrayList<>();
			if (!heardOnce(heard, VANILLA_API, items.getId(vanilla))) failed.add("vanilla_api");
			if (!heardOnce(heard, FORGE_API, items.getId(forge))) failed.add("forge_api");
			if (!failed.isEmpty()) ForgeTestMod.LOGGER.warn("FABRICATED-TEST registry entry added events heard {}", heard);
			ForgeTestMod.report("registry entry added events", failed, "fabric-registry-sync-v0", PROVIDERS_ANSWERS);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST registry entry added events FAILED", e);
		}
	}

	/** Whether the listener heard of the entry once, by its name and raw id, with the registry holding it. */
	private static boolean heardOnce(List<String> heard, ResourceLocation id, int rawId) {
		return heard.stream().filter(line -> line.startsWith(id + " ")).toList().equals(List.of(id + " " + rawId));
	}
}
