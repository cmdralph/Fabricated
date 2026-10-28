package io.github.cmdralph.fabricated.testmod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * run; NeoForge freezes them during the bootstrap.
 *
 * <p>Fabric API also moves the built-in registries' freeze ({@code BuiltInRegistries.freeze}) after the main
 * initializers, and a Fabric mod's mixin into it runs there: Wover makes its item definitions there, which BetterEnd's
 * main initializer subscribes to first (CORPUS-32).
 */
public final class BootstrapContent {
	// Not TestMod's logger: the registries' freeze may come during vanilla's bootstrap, before the mod is constructed.
	private static final Logger LOGGER = LoggerFactory.getLogger(TestMod.MOD_ID);
	private static final Identifier ID = Identifier.fromNamespaceAndPath("fabricated_testmod", "bootstrap_entity");
	private static EntityType<ItemEntity> type;
	private static boolean mainInitializerRan;

	private BootstrapContent() {
	}

	/** Called as vanilla's bootstrap has created the registries' contents: the type's intrusive holder waits for its registration. */
	public static void build() {
		if (type == null) {
			type = TestCompat.build(EntityType.Builder.<ItemEntity>of(ItemEntity::new, MobCategory.MISC).noSave().noSummon(),
					ResourceKey.create(Registries.ENTITY_TYPE, ID));
		}
	}

	/** Called as the built-in registries are frozen, from a mixin into {@code BuiltInRegistries.freeze}, as Wover's. */
	public static void frozen() {
		if (mainInitializerRan) LOGGER.info("FABRICATED-TEST built-in registries frozen after the main initializers OK");
		else LOGGER.error("FABRICATED-TEST built-in registries frozen before the main initializers FAILED");
	}

	/** Called from the main initializer: registers the type, as BetterNether's does. */
	static void register() {
		mainInitializerRan = true;
		if (type == null) {
			LOGGER.error("FABRICATED-TEST content built at bootstrap FAILED: nothing was built");
			return;
		}
		Registry.register(BuiltInRegistries.ENTITY_TYPE, ID, type);
		if (ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type))) LOGGER.info("FABRICATED-TEST content built at bootstrap registered OK");
	}
}
