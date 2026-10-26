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
	private static boolean mainInitializerRan;

	private ForgeBootstrapContent() {
	}

	/**
	 * Called as the registries are frozen: during vanilla's bootstrap, the type's intrusive holder waits for its
	 * registration. Nothing is built at a freeze after the main initializer, where Fabric API's registry sync, passed
	 * through, moves the bootstrap's.
	 */
	public static void build() {
		if (type == null && !mainInitializerRan) {
			type = EntityType.Builder.<ItemEntity>of(ItemEntity::new, MobCategory.MISC).noSave().noSummon()
					.build(ResourceKey.create(Registries.ENTITY_TYPE, ID));
		}
	}

	/** Called from the main initializer: registers the type, as BetterNether's does. */
	static void register() {
		mainInitializerRan = true;
		if (type == null) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST content built at bootstrap: none, as no freeze came before the main initializers");
			return;
		}
		Registry.register(BuiltInRegistries.ENTITY_TYPE, ID, type);
		if (ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type))) ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST content built at bootstrap registered OK");
	}
}
