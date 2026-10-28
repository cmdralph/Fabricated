package io.github.cmdralph.fabricated.testmod;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.fluids.FluidType;

/** Game calls the shared test mod parts make, as Minecraft 1.21.11 and later name them (1.21.1 has its own). */
final class TestCompat {
	private TestCompat() {
	}

	static <T> T value(Registry<T> registry, Identifier id) {
		return registry.getValue(id);
	}

	static Identifier location(ResourceKey<?> key) {
		return key.identifier();
	}

	static <T> Registry<T> registry(RegistryAccess registries, ResourceKey<? extends Registry<? extends T>> key) {
		return registries.lookupOrThrow(key);
	}

	static @Nullable Entity create(EntityType<?> type, Level level) {
		return type.create(level, EntitySpawnReason.TRIGGERED);
	}

	/** The highest block y in the level. */
	static int maxY(LevelHeightAccessor level) {
		return level.getMaxY();
	}

	static BlockBehaviour.Properties withId(BlockBehaviour.Properties properties, ResourceKey<Block> key) {
		return properties.setId(key);
	}

	static Item.Properties withId(Item.Properties properties, ResourceKey<Item> key) {
		return properties.setId(key);
	}

	static <T extends Entity> EntityType<T> build(EntityType.Builder<T> builder, ResourceKey<EntityType<?>> key) {
		return builder.build(key);
	}

	static boolean waterLike(FluidType type) {
		return type.getIsWaterLike();
	}
}
