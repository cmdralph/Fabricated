package io.github.cmdralph.fabricated.forgetest;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * Content built while vanilla's bootstrap runs, before any mod is constructed, and registered by the main initializer, as
 * BetterNether's entity types are: its mixin into {@code DefaultAttributes} loads the class that builds them (CORPUS-31).
 * On Fabric nothing checks it in between, since Fabric API leaves the registries open until the main initializers have
 * run; Forge freezes them during the bootstrap.
 */
public final class ForgeBootstrapContent {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(ForgeTestMod.MOD_ID, "bootstrap_entity");
	private static EntityType<ItemEntity> type;

	private ForgeBootstrapContent() {
	}

	/** Called as vanilla's bootstrap freezes the registries: the type's intrusive holder waits for its registration. */
	public static void build() {
		if (type == null) {
			type = EntityType.Builder.<ItemEntity>of(ItemEntity::new, MobCategory.MISC).noSave().noSummon()
					.build(ResourceKey.create(Registries.ENTITY_TYPE, ID));
		}
	}

	/** Called from the main initializer: registers the type, as BetterNether's does. */
	static void register() {
		if (type == null) {
			ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST content built at bootstrap FAILED: vanilla's bootstrap did not build it");
			return;
		}
		Registry.register(BuiltInRegistries.ENTITY_TYPE, ID, type);
		if (ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type))) ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST content built at bootstrap registered OK");
	}
}
