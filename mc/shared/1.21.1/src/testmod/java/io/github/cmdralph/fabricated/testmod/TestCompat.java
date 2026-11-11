package io.github.cmdralph.fabricated.testmod;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Lifecycle;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
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
 * Game calls the shared test mod parts make, as Minecraft 1.21.1 names them. Public for the checks of Forge's own hooks
 * (mc/forge-shared), in another package.
 */
public final class TestCompat {
	/**
	 * Why another mod's fabric-resource-loader-v0 leaves a built-in resource pack its mod enables by default unselected, as
	 * its source reads (PROVIDER-8); null where it doesn't.
	 */
	static final @Nullable String PROVIDERS_DEFAULT_PACK = "Forgified Fabric API selects such a pack as the game's options load, as upstream does, which NeoForge 21.1 does before"
			+ " mods' initializers register their packs";

	/**
	 * Whether the version's Fabric API has the tag of the blocks that make an open trapdoor above them climbable, as a
	 * ladder does ({@code fabric:can_climb_trapdoor_above}): Fabric API 0.92, 1.20.1's, has none.
	 */
	static final boolean TRAPDOOR_CLIMBING_TAG = true;

	/** The first byte of an empty recipe slot on the network ({@link #slot}): an empty item list's length. */
	public static final int EMPTY_SLOT = 0;

	private TestCompat() {
	}

	static ResourceLocation id(String namespace, String path) {
		return ResourceLocation.fromNamespaceAndPath(namespace, path);
	}

	public static <T> T value(Registry<T> registry, ResourceLocation id) {
		return registry.get(id);
	}

	/** The entry's holder in the registry, if it has one. */
	public static <T> Optional<Holder.Reference<T>> holder(Registry<T> registry, ResourceKey<T> key) {
		return registry.getHolder(key);
	}

	public static ResourceLocation location(ResourceKey<?> key) {
		return key.location();
	}

	public static <T> Registry<T> registry(RegistryAccess registries, ResourceKey<? extends Registry<? extends T>> key) {
		return registries.registryOrThrow(key);
	}

	public static @Nullable Entity create(EntityType<?> type, Level level) {
		return type.create(level);
	}

	/** Hurts the entity as the game does, on the server; whether it was hurt. */
	public static boolean hurt(LivingEntity entity, ServerLevel level, DamageSource source, float amount) {
		return entity.hurt(source, amount);
	}

	/** The highest block y in the level. */
	static int maxY(LevelHeightAccessor level) {
		return level.getMaxBuildHeight() - 1;
	}

	/** Properties carry no id before 1.21.2. */
	static BlockBehaviour.Properties withId(BlockBehaviour.Properties properties, ResourceKey<Block> key) {
		return properties;
	}

	public static Item.Properties withId(Item.Properties properties, ResourceKey<Item> key) {
		return properties;
	}

	/** A block entity type of these blocks, without a data fixer type. */
	static <T extends BlockEntity> BlockEntityType<T> blockEntityType(BlockEntityType.BlockEntitySupplier<T> factory, Block... blocks) {
		return BlockEntityType.Builder.of(factory, blocks).build(null);
	}

	static <T extends Entity> EntityType<T> build(EntityType.Builder<T> builder, ResourceKey<EntityType<?>> key) {
		return builder.build(key.location().toString());
	}

	/** The lifecycle the registry gave the entry, null for none. */
	public static <T> @Nullable Lifecycle lifecycle(Registry<T> registry, ResourceKey<T> key) {
		return registry.registrationInfo(key).map(RegistrationInfo::lifecycle).orElse(null);
	}

	/** Whether the entry came from a pack the client knows, which then reads its own copy. */
	static <T> boolean fromKnownPack(Registry<T> registry, ResourceKey<T> key) {
		return registry.registrationInfo(key).flatMap(RegistrationInfo::knownPackInfo).isPresent();
	}

	/** An ingredient of the tag's items. */
	public static Ingredient ingredient(TagKey<Item> tag) {
		return Ingredient.of(tag);
	}

	/**
	 * A recipe's slot on the network, as a recipe's codec writes its slots: 1.21.1 writes an empty one as the empty
	 * ingredient, an empty item list.
	 */
	public static StreamCodec<RegistryFriendlyByteBuf, Optional<Ingredient>> slot() {
		return Ingredient.CONTENTS_STREAM_CODEC.map(ingredient -> ingredient.isEmpty() ? Optional.empty() : Optional.of(ingredient),
				slot -> slot.orElse(Ingredient.EMPTY));
	}

	/** Runs the listener after an entity was damaged. */
	static void afterDamage(Consumer<LivingEntity> listener) {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> listener.accept(entity));
	}

	/** The overlays the game reads from the metadata of the data pack in this folder, or null where it reads none. */
	static @Nullable List<String> packOverlays(String id, Path folder) {
		Pack.Metadata metadata = Pack.readPackMetadata(new PackLocationInfo(id, Component.literal(id), PackSource.DEFAULT, Optional.empty()),
				new PathPackResources.PathResourcesSupplier(folder), SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA));
		return metadata == null ? null : metadata.overlays();
	}
}
