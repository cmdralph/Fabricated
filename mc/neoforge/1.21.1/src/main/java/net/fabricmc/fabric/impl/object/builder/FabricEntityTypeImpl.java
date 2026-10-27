package net.fabricmc.fabric.impl.object.builder;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.Nullable;

import io.github.cmdralph.fabricated.neoforge.objectbuilder.ObjectBuilderBridge;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Fabricated's implementation behind {@link FabricEntityType.Builder} (1.21.1). {@code EntityType.Builder} implements
 * {@link Builder} through Fabricated's mixin; the settings are applied when the type is built. Spawn placements go to
 * NeoForge's {@code RegisterSpawnPlacementsEvent} (see {@link ObjectBuilderBridge}).
 */
public interface FabricEntityTypeImpl {
	interface Builder {
		void fabric_setLivingEntityBuilder(Living<? extends LivingEntity> livingBuilder);

		void fabric_setMobEntityBuilder(Mob<? extends net.minecraft.world.entity.Mob> mobBuilder);

		static <T extends LivingEntity> EntityType.Builder<T> createLiving(EntityType.EntityFactory<T> factory, MobCategory mobCategory, UnaryOperator<FabricEntityType.Builder.Living<T>> livingBuilder) {
			EntityType.Builder<T> builder = EntityType.Builder.of(factory, mobCategory);
			Living<T> builderImpl = new Living<>();
			livingBuilder.apply(builderImpl);
			((Builder) builder).fabric_setLivingEntityBuilder(builderImpl);
			return builder;
		}

		static <T extends net.minecraft.world.entity.Mob> EntityType.Builder<T> createMob(EntityType.EntityFactory<T> factory, MobCategory mobCategory, UnaryOperator<FabricEntityType.Builder.Mob<T>> mobBuilder) {
			EntityType.Builder<T> builder = EntityType.Builder.of(factory, mobCategory);
			Mob<T> builderImpl = new Mob<>();
			mobBuilder.apply(builderImpl);
			((Builder) builder).fabric_setMobEntityBuilder(builderImpl);
			return builder;
		}

		sealed class Living<T extends LivingEntity> implements FabricEntityType.Builder.Living<T> permits Mob {
			private @Nullable Supplier<AttributeSupplier.Builder> defaultAttributeBuilder;

			@Override
			public FabricEntityType.Builder.Living<T> defaultAttributes(Supplier<AttributeSupplier.Builder> defaultAttributeBuilder) {
				this.defaultAttributeBuilder = Objects.requireNonNull(defaultAttributeBuilder, "Cannot set null attribute builder");
				return this;
			}

			public void onBuild(EntityType<T> type) {
				if (this.defaultAttributeBuilder != null) {
					// Built when NeoForge builds the attribute tables, as the builder can include attributes that
					// aren't registered yet while entity types are being built.
					ObjectBuilderBridge.addDefaultAttributes(type, this.defaultAttributeBuilder);
				}
			}
		}

		final class Mob<T extends net.minecraft.world.entity.Mob> extends Living<T> implements FabricEntityType.Builder.Mob<T> {
			private @Nullable SpawnPlacementType placementType;
			private Heightmap.@Nullable Types placementHeightmap;
			private SpawnPlacements.@Nullable SpawnPredicate<T> spawnPredicate;

			@Override
			public FabricEntityType.Builder.Mob<T> spawnRestriction(SpawnPlacementType placementType, Heightmap.Types heightmap, SpawnPlacements.SpawnPredicate<T> spawnPredicate) {
				this.placementType = Objects.requireNonNull(placementType, "Placement type cannot be null.");
				this.placementHeightmap = Objects.requireNonNull(heightmap, "Heightmap type cannot be null.");
				this.spawnPredicate = Objects.requireNonNull(spawnPredicate, "Spawn predicate cannot be null.");
				return this;
			}

			@Override
			public FabricEntityType.Builder.Mob<T> defaultAttributes(Supplier<AttributeSupplier.Builder> defaultAttributeBuilder) {
				super.defaultAttributes(defaultAttributeBuilder);
				return this;
			}

			@Override
			public void onBuild(EntityType<T> type) {
				super.onBuild(type);
				if (this.spawnPredicate != null) {
					ObjectBuilderBridge.addSpawnPlacement(type, this.placementType, this.placementHeightmap, this.spawnPredicate);
				}
			}
		}
	}
}
