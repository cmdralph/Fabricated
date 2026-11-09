package io.github.cmdralph.fabricated.forgetest;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.event.registry.FabricRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2378;
import net.minecraft.class_2960;
import net.minecraft.class_5321;
import net.minecraft.class_6862;
import net.minecraft.class_6880;
import net.minecraft.class_7923;

/**
 * The forge-legacy test mod's part for fabric-registry-sync-v0's aliases (with Fabric API's jar only, CORPUS-75): an
 * item registered under its new name keeps its old one as an alias, as Waystones keeps its renamed waystones', and
 * another alias names vanilla's stone, as Fabric API's own test does. The registry must answer to each alias by name
 * and by key, the lookup a data pack's references go through, and the test mod's tag {@code aliased} lists the item by
 * its old name only (optional, so a run without Fabric API reads the tag without it). In Intermediary's names (the
 * stand-ins in src/testmodstubs): {@code method_10223} is {@code get(ResourceLocation)}, {@code method_10250} {@code
 * containsKey}, {@code method_40264} {@code getHolder(ResourceKey)}, {@code comp_349} {@code Holder.value()}.
 */
public final class ForgeRegistryAliasTest {
	private static final class_2960 NEW_NAME = new class_2960(ForgeTestMod.MOD_ID, "renamed_item");
	private static final class_2960 OLD_NAME = new class_2960(ForgeTestMod.MOD_ID, "item_before_renaming");
	private static final class_2960 OLD_STONE = new class_2960(ForgeTestMod.MOD_ID, "old_stone");
	private static class_1792 renamed;

	private ForgeRegistryAliasTest() {
	}

	/** From the main entrypoint, with the registries open, as Fabric mods register their entries and aliases. */
	static void register() {
		if (!FabricLoader.getInstance().isModLoaded("fabric-registry-sync-v0")) return;
		try {
			renamed = class_2378.method_10230(class_7923.field_41178, NEW_NAME, new class_1792(new class_1792.class_1793()));
			FabricRegistry items = (FabricRegistry) class_7923.field_41178;
			items.addAlias(OLD_NAME, NEW_NAME);
			items.addAlias(OLD_STONE, new class_2960("minecraft", "stone"));
		} catch (RuntimeException | LinkageError e) {
			ForgeTestMod.LOGGER.warn("FABRICATED-TEST registry aliases not added", e);
		}
	}

	/** At the first server tick, the server's data loaded. */
	public static void check() {
		if (!FabricLoader.getInstance().isModLoaded("fabric-registry-sync-v0")) return;
		List<String> failed = new ArrayList<>();
		try {
			class_2378<class_1792> items = class_7923.field_41178;
			if (items.method_10223(OLD_NAME) != renamed) failed.add("its old name is " + items.method_10223(OLD_NAME));
			if (!items.method_10250(OLD_NAME)) failed.add("the registry doesn't contain its old name");
			class_6880.class_6883<class_1792> holder = items.method_40264(class_5321.method_29179(items.method_30517(), OLD_NAME)).orElse(null);
			if (holder == null || holder.comp_349() != renamed) failed.add("the key of its old name holds " + holder);
			class_6862<class_1792> aliased = class_6862.method_40092(items.method_30517(), new class_2960(ForgeTestMod.MOD_ID, "aliased"));
			if (renamed == null || !new class_1799(renamed).method_31573(aliased)) failed.add("the tag listing it by its old name doesn't hold it");
			if (items.method_10223(OLD_STONE) != class_1802.field_20391) failed.add("the alias of stone is " + items.method_10223(OLD_STONE));
			if (!NEW_NAME.equals(items.method_10221(renamed))) failed.add("its name is " + items.method_10221(renamed));
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
