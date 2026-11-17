package io.github.cmdralph.fabricated.forgetest;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.registry.FabricRegistry;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The forge-legacy test mod's part for fabric-registry-sync-v0's aliases (with Fabric API's jar only, CORPUS-75): an
 * item registered under its new name keeps its old one as an alias, as Waystones keeps its renamed waystones', and
 * another alias names vanilla's stone, as Fabric API's own test does. The registry must answer to each alias by name
 * and by key, the lookup a data pack's references go through, and the test mod's tag {@code aliased} lists the item by
 * its old name only (optional, so a run without Fabric API reads the tag without it). An entrypoint of 1.20.1's own:
 * Fabric API 0.77 has no aliases.
 */
public final class ForgeRegistryAliasTest implements ModInitializer {
	private static final ResourceLocation NEW_NAME = new ResourceLocation(ForgeTestMod.NAMESPACE, "renamed_item");
	private static final ResourceLocation OLD_NAME = new ResourceLocation(ForgeTestMod.NAMESPACE, "item_before_renaming");
	private static final ResourceLocation OLD_STONE = new ResourceLocation(ForgeTestMod.NAMESPACE, "old_stone");
	private static Item renamed;

	/** With the registries open, as Fabric mods register their entries and aliases; checked once the server has started. */
	@Override
	public void onInitialize() {
		if (!FabricLoader.getInstance().isModLoaded("fabric-registry-sync-v0")) return;
		ServerLifecycleEvents.SERVER_STARTED.register(server -> ForgeTestMod.run("registry alias check", ForgeRegistryAliasTest::check));
		try {
			renamed = Registry.register(BuiltInRegistries.ITEM, NEW_NAME, new Item(new Item.Properties()));
			FabricRegistry items = (FabricRegistry) BuiltInRegistries.ITEM;
			items.addAlias(OLD_NAME, NEW_NAME);
			items.addAlias(OLD_STONE, new ResourceLocation("minecraft", "stone"));
		} catch (RuntimeException | LinkageError e) {
			ForgeTestMod.LOGGER.warn("FABRICATED-TEST registry aliases not added", e);
		}
	}

	/** The server's data loaded. */
	private static void check() {
		List<String> failed = new ArrayList<>();
		try {
			Registry<Item> items = BuiltInRegistries.ITEM;
			if (items.get(OLD_NAME) != renamed) failed.add("its old name is " + items.get(OLD_NAME));
			if (!items.containsKey(OLD_NAME)) failed.add("the registry doesn't contain its old name");
			Holder.Reference<Item> holder = items.getHolder(ResourceKey.create(items.key(), OLD_NAME)).orElse(null);
			if (holder == null || holder.value() != renamed) failed.add("the key of its old name holds " + holder);
			TagKey<Item> aliased = TagKey.create(items.key(), new ResourceLocation(ForgeTestMod.NAMESPACE, "aliased"));
			if (renamed == null || !new ItemStack(renamed).is(aliased)) failed.add("the tag listing it by its old name doesn't hold it");
			if (items.get(OLD_STONE) != Items.STONE) failed.add("the alias of stone is " + items.get(OLD_STONE));
			if (!NEW_NAME.equals(items.getKey(renamed))) failed.add("its name is " + items.getKey(renamed));
		} catch (RuntimeException | LinkageError e) {
			failed.add(e.toString());
		}
		if (failed.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST registry aliases OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-TEST registry aliases not resolved: {}", String.join("; ", failed));
		}
	}
}
