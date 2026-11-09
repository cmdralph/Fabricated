package io.github.cmdralph.fabricated.testmod;

import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Lifecycle;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistrationInfo;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Game calls the shared test mod parts make, as Minecraft 1.21.11 and later name them (1.21.1 has its own). Public for
 * the checks of Forge's own hooks (mc/forge-shared), in another package.
 */
public final class TestCompat {
	/**
	 * Why another mod's fabric-resource-loader-v0 leaves a built-in resource pack its mod enables by default unselected, as
	 * its source reads (PROVIDER-8); null where it doesn't.
	 */
	static final @Nullable String PROVIDERS_DEFAULT_PACK = null;

	private TestCompat() {
	}

	static Identifier id(String namespace, String path) {
		return Identifier.fromNamespaceAndPath(namespace, path);
	}

	public static <T> T value(Registry<T> registry, Identifier id) {
		return registry.getValue(id);
	}

	/** The entry's holder in the registry, if it has one. */
	public static <T> Optional<Holder.Reference<T>> holder(Registry<T> registry, ResourceKey<T> key) {
		return registry.get(key);
	}

	public static Identifier location(ResourceKey<?> key) {
		return key.identifier();
	}

	public static <T> Registry<T> registry(RegistryAccess registries, ResourceKey<? extends Registry<? extends T>> key) {
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

	public static Item.Properties withId(Item.Properties properties, ResourceKey<Item> key) {
		return properties.setId(key);
	}

	/** A block entity type of these blocks. */
	static <T extends BlockEntity> BlockEntityType<T> blockEntityType(BlockEntityType.BlockEntitySupplier<T> factory, Block... blocks) {
		return new BlockEntityType<>(factory, Set.of(blocks));
	}

	static <T extends Entity> EntityType<T> build(EntityType.Builder<T> builder, ResourceKey<EntityType<?>> key) {
		return builder.build(key);
	}

	/** The lifecycle the registry gave the entry, null for none. */
	public static <T> @Nullable Lifecycle lifecycle(Registry<T> registry, ResourceKey<T> key) {
		return registry.registrationInfo(key).map(RegistrationInfo::lifecycle).orElse(null);
	}
}
