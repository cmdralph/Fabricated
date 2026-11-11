package io.github.cmdralph.fabricated.testmod;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Lifecycle;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
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

	/**
	 * The first byte of an empty recipe slot on the network ({@link #slot}): the holder set codec's length prefix of an
	 * empty item list.
	 */
	public static final int EMPTY_SLOT = 1;

	/**
	 * Vanilla's lambda in {@code ChiseledBookShelfBlock.createBlockStateDefinition} that adds each slot's property, by its
	 * name and descriptor in Minecraft 1.21.11 and later: it takes a {@code Property}, where NeoForge's recompiled game takes
	 * the {@code BooleanProperty} each is (LAMBDA-11).
	 */
	public static final String PROPERTY_LAMBDA = "lambda$createBlockStateDefinition$0(Lnet/minecraft/world/level/block/state/StateDefinition$Builder;"
			+ "Lnet/minecraft/world/level/block/state/properties/Property;)V";

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

	public static @Nullable Entity create(EntityType<?> type, Level level) {
		return type.create(level, EntitySpawnReason.TRIGGERED);
	}

	/** Hurts the entity as the game does, on the server; whether it was hurt. */
	public static boolean hurt(LivingEntity entity, ServerLevel level, DamageSource source, float amount) {
		return entity.hurtServer(level, source, amount);
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

	/** An ingredient of the tag's items. */
	public static Ingredient ingredient(TagKey<Item> tag) {
		return Ingredient.of(BuiltInRegistries.ITEM.getOrThrow(tag));
	}

	/** A recipe's slot on the network, as a recipe's codec writes its slots, an empty one as an empty item list. */
	public static StreamCodec<RegistryFriendlyByteBuf, Optional<Ingredient>> slot() {
		return Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC;
	}

	/** The overlays the game reads from the metadata of the data pack in this folder, or null where it reads none. */
	static @Nullable List<String> packOverlays(String id, Path folder) {
		Pack.Metadata metadata = Pack.readPackMetadata(new PackLocationInfo(id, Component.literal(id), PackSource.DEFAULT, Optional.empty()),
				new PathPackResources.PathResourcesSupplier(folder), SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA), PackType.SERVER_DATA);
		return metadata == null ? null : metadata.overlays();
	}
}
