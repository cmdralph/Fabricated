package io.github.cmdralph.fabricated.testmod;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Game calls the shared test mod parts make, as Minecraft 1.21.1 names them. */
final class TestCompat {
	/**
	 * Why another mod's fabric-resource-loader-v0 leaves a built-in resource pack its mod enables by default unselected, as
	 * its source reads (PROVIDER-8); null where it doesn't.
	 */
	static final @Nullable String PROVIDERS_DEFAULT_PACK = "Forgified Fabric API selects such a pack as the game's options load, as upstream does, which NeoForge 21.1 does before"
			+ " mods' initializers register their packs";

	private TestCompat() {
	}

	static <T> T value(Registry<T> registry, ResourceLocation id) {
		return registry.get(id);
	}

	static ResourceLocation location(ResourceKey<?> key) {
		return key.location();
	}

	static <T> Registry<T> registry(RegistryAccess registries, ResourceKey<? extends Registry<? extends T>> key) {
		return registries.registryOrThrow(key);
	}

	static @Nullable Entity create(EntityType<?> type, Level level) {
		return type.create(level);
	}

	/** The highest block y in the level. */
	static int maxY(LevelHeightAccessor level) {
		return level.getMaxBuildHeight() - 1;
	}

	/** Properties carry no id before 1.21.2. */
	static BlockBehaviour.Properties withId(BlockBehaviour.Properties properties, ResourceKey<Block> key) {
		return properties;
	}

	static Item.Properties withId(Item.Properties properties, ResourceKey<Item> key) {
		return properties;
	}

	static <T extends Entity> EntityType<T> build(EntityType.Builder<T> builder, ResourceKey<EntityType<?>> key) {
		return builder.build(key.location().toString());
	}
}
