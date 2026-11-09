package io.github.cmdralph.fabricated.forgetest;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The forge test mod's part for fabric-registry-sync-v0's entry added event (with Fabric API's jar only, CORPUS-108). A
 * listener on the item registry, registered before the entries it waits for, as Architectury's registry listeners are,
 * must hear of an item registered through vanilla's API, as a Fabric mod registers one, and of an item registered
 * through Forge's ({@code ForgeRegistries.ITEMS.register}, as a Forge mod registers one), each once, by its name and its
 * raw id, with the registry already holding it.
 */
public final class ForgeRegistryEntryAddedTest {
	private static final Identifier VANILLA_API = Identifier.fromNamespaceAndPath(ForgeTestMod.NAMESPACE, "entry_added_vanilla");
	private static final Identifier FORGE_API = Identifier.fromNamespaceAndPath(ForgeTestMod.NAMESPACE, "entry_added_forge");

	private ForgeRegistryEntryAddedTest() {
	}

	/** From the main entrypoint, with the registries open, as Fabric mods register their entries and listeners. */
	public static void register() {
		Registry<Item> items = BuiltInRegistries.ITEM;
		List<String> heard = new ArrayList<>();
		RegistryEntryAddedCallback.event(items).register((rawId, id, item) -> {
			if (id.equals(VANILLA_API) || id.equals(FORGE_API)) heard.add(id + " " + rawId + (item == items.getValue(id) ? "" : " not yet registered"));
		});
		Item vanilla = Registry.register(items, VANILLA_API, new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, VANILLA_API))));
		Item forge = new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, FORGE_API)));
		ForgeRegistries.ITEMS.register(FORGE_API, forge);
		List<String> expected = List.of(VANILLA_API + " " + items.getId(vanilla), FORGE_API + " " + items.getId(forge));
		if (heard.equals(expected)) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST registry entry added events OK");
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST registry entry added events FAILED: heard {}, expected {}", heard, expected);
		}
	}
}
