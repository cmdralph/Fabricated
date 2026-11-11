package io.github.cmdralph.fabricated.testmod;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Lifecycle;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Game calls the shared test mod parts make, as Minecraft 1.20.1 names them. Public for the test mod's mixins, in another package. */
public final class TestCompat {
	/**
	 * Why another mod's fabric-resource-loader-v0 leaves a built-in resource pack its mod enables by default unselected, as
	 * its source reads (PROVIDER-8); null where it doesn't.
	 */
	static final @Nullable String PROVIDERS_DEFAULT_PACK = "Forgified Fabric API selects such a pack as the game's options load, as upstream does, which Forge 47 does before"
			+ " mods' initializers register their packs (CORPUS-104)";

	/**
	 * Whether the version's Fabric API has the tag of the blocks that make an open trapdoor above them climbable, as a
	 * ladder does ({@code fabric:can_climb_trapdoor_above}): Fabric API 0.92, 1.20.1's, has none.
	 */
	static final boolean TRAPDOOR_CLIMBING_TAG = false;

	/**
	 * Vanilla's lambda in {@code ChiseledBookShelfBlock.createBlockStateDefinition} that adds each slot's property, by its
	 * name and descriptor in Minecraft 1.20.1, taking a {@code Property} (LAMBDA-11).
	 */
	public static final String PROPERTY_LAMBDA = "lambda$createBlockStateDefinition$0(Lnet/minecraft/world/level/block/state/StateDefinition$Builder;"
			+ "Lnet/minecraft/world/level/block/state/properties/Property;)V";

	private TestCompat() {
	}

	static ResourceLocation id(String namespace, String path) {
		return new ResourceLocation(namespace, path);
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

	/** The lifecycle the registry gave the entry, null for none. */
	static <T> @Nullable Lifecycle lifecycle(Registry<T> registry, ResourceKey<T> key) {
		T value = registry.get(key);
		return value == null ? null : registry.lifecycle(value);
	}

	/** Whether the entry came from a pack the client knows: 1.20.1's clients know none, and are sent every entry. */
	static <T> boolean fromKnownPack(Registry<T> registry, ResourceKey<T> key) {
		return false;
	}

	/** Runs the listener after an entity was damaged: Fabric API 0.92 has no such event, so never. */
	static void afterDamage(Consumer<LivingEntity> listener) {
	}
}
