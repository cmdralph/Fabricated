package io.github.cmdralph.fabricated.forgetest;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.event.registry.FabricRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The forge test mod's part for fabric-registry-sync-v0's aliases (with Fabric API's jar only, CORPUS-75): an item
 * registered under its new name keeps its old one as an alias, as Waystones keeps its renamed waystones', and another
 * alias names vanilla's stone, as Fabric API's own test does. The registry must answer to each alias by name and by
 * key, the lookup a data pack's references go through, and the test mod's tag {@code aliased} lists the item by its
 * old name only (optional, so a run without Fabric API reads the tag without it).
 */
public final class ForgeRegistryAliasTest {
	private static final Identifier NEW_NAME = Identifier.fromNamespaceAndPath(ForgeTestMod.NAMESPACE, "renamed_item");
	private static final Identifier OLD_NAME = Identifier.fromNamespaceAndPath(ForgeTestMod.NAMESPACE, "item_before_renaming");
	private static final Identifier OLD_STONE = Identifier.fromNamespaceAndPath(ForgeTestMod.NAMESPACE, "old_stone");
	private static final TagKey<Item> ALIASED = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(ForgeTestMod.NAMESPACE, "aliased"));
	private static Item renamed;

	private ForgeRegistryAliasTest() {
	}

	/** From the main entrypoint, with the registries open, as Fabric mods register their entries and aliases. */
	public static void register() {
		renamed = Registry.register(BuiltInRegistries.ITEM, NEW_NAME, new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, NEW_NAME))));
		FabricRegistry items = (FabricRegistry) BuiltInRegistries.ITEM;
		items.addAlias(OLD_NAME, NEW_NAME);
		items.addAlias(OLD_STONE, Identifier.withDefaultNamespace("stone"));
	}

	/** At the first server tick, the server's data loaded. */
	public static void check() {
		List<String> failed = new ArrayList<>();
		if (BuiltInRegistries.ITEM.getValue(OLD_NAME) != renamed) failed.add("its old name is " + BuiltInRegistries.ITEM.getValue(OLD_NAME));
		if (!BuiltInRegistries.ITEM.containsKey(OLD_NAME)) failed.add("the registry doesn't contain its old name");
		Holder.Reference<Item> holder = BuiltInRegistries.ITEM.get(ResourceKey.create(Registries.ITEM, OLD_NAME)).orElse(null);
		if (holder == null || holder.value() != renamed) failed.add("the key of its old name holds " + holder);
		if (!new ItemStack(renamed).is(ALIASED)) failed.add("the tag listing it by its old name doesn't hold it");
		if (BuiltInRegistries.ITEM.getValue(OLD_STONE) != Items.STONE) failed.add("the alias of stone is " + BuiltInRegistries.ITEM.getValue(OLD_STONE));
		if (!NEW_NAME.equals(BuiltInRegistries.ITEM.getKey(renamed))) failed.add("its name is " + BuiltInRegistries.ITEM.getKey(renamed));
		if (failed.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST registry aliases OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-TEST registry aliases not resolved: {}", String.join("; ", failed));
		}
	}
}
