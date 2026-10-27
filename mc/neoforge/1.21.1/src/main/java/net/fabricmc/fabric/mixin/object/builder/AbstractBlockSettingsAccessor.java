/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.mixin.object.builder;

import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlockBehaviour.Properties.class)
public interface AbstractBlockSettingsAccessor {
	/* GETTERS */
	@Accessor
	float getDestroyTime();

	@Accessor
	float getExplosionResistance();

	@Accessor
	boolean getHasCollision();

	@Accessor
	boolean getIsRandomlyTicking();

	@Accessor("lightEmission")
	ToIntFunction<BlockState> getLuminance();

	@Accessor
	Function<BlockState, MapColor> getMapColor();

	@Accessor
	SoundType getSoundType();

	@Accessor
	float getFriction();

	@Accessor
	float getSpeedFactor();

	@Accessor
	float getJumpFactor();

	@Accessor
	boolean getDynamicShape();

	@Accessor
	boolean getCanOcclude();

	@Accessor
	boolean getIsAir();

	@Accessor
	boolean isRequiresCorrectToolForDrops();

	@Accessor
	BlockBehaviour.StateArgumentPredicate<EntityType<?>> getIsValidSpawn();

	@Accessor
	BlockBehaviour.StatePredicate getIsRedstoneConductor();

	@Accessor
	BlockBehaviour.StatePredicate getIsSuffocating();

	@Accessor
	BlockBehaviour.StatePredicate getIsViewBlocking();

	@Accessor
	BlockBehaviour.StatePredicate getHasPostProcess();

	@Accessor
	BlockBehaviour.StatePredicate getEmissiveRendering();

	@Accessor
	BlockBehaviour.OffsetFunction getOffsetFunction();

	@Accessor
	ResourceKey<LootTable> getDrops();

	@Accessor
	boolean getSpawnTerrainParticles();

	@Accessor
	FeatureFlagSet getRequiredFeatures();

	@Accessor
	boolean getIgnitedByLava();

	@Accessor
	boolean getLiquid();

	@Accessor
	boolean getForceSolidOff();

	@Accessor
	boolean getForceSolidOn();

	@Accessor
	PushReaction getPushReaction();

	@Accessor
	NoteBlockInstrument getInstrument();

	@Accessor
	boolean getReplaceable();

	/* SETTERS */
	@Accessor
	void setHasCollision(boolean collidable);

	@Accessor
	void setIsRandomlyTicking(boolean ticksRandomly);

	@Accessor
	void setMapColor(Function<BlockState, MapColor> mapColorProvider);

	@Accessor
	void setDynamicShape(boolean dynamicBounds);

	@Accessor
	void setCanOcclude(boolean opaque);

	@Accessor
	void setIsAir(boolean isAir);

	@Accessor
	void setDrops(ResourceKey<LootTable> lootTableKey);

	@Accessor
	void setRequiresCorrectToolForDrops(boolean toolRequired);

	@Accessor
	void setSpawnTerrainParticles(boolean blockBreakParticles);

	@Accessor
	void setRequiredFeatures(FeatureFlagSet requiredFeatures);

	@Accessor
	void setOffsetFunction(BlockBehaviour.OffsetFunction offsetter);

	@Accessor
	void setIgnitedByLava(boolean burnable);

	@Accessor
	void setLiquid(boolean liquid);

	@Accessor
	void setForceSolidOff(boolean forceNotSolid);

	@Accessor
	void setForceSolidOn(boolean forceSolid);

	@Accessor
	void setReplaceable(boolean replaceable);
}
