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

package net.fabricmc.fabric.api.object.builder.v1.block;

import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.fabricmc.fabric.mixin.object.builder.AbstractBlockAccessor;
import net.fabricmc.fabric.mixin.object.builder.AbstractBlockSettingsAccessor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * @deprecated replace with {@link BlockBehaviour.Properties}
 */
@Deprecated
public class FabricBlockSettings extends BlockBehaviour.Properties {
	protected FabricBlockSettings() {
		super();
	}

	protected FabricBlockSettings(BlockBehaviour.Properties settings) {
		this();
		// Mostly Copied from vanilla's copy method
		// Note: If new methods are added to Block settings, an accessor must be added here
		AbstractBlockSettingsAccessor thisAccessor = (AbstractBlockSettingsAccessor) this;
		AbstractBlockSettingsAccessor otherAccessor = (AbstractBlockSettingsAccessor) settings;

		// Copied in vanilla: sorted by vanilla copy order
		this.destroyTime(otherAccessor.getDestroyTime());
		this.explosionResistance(otherAccessor.getExplosionResistance());
		this.collidable(otherAccessor.getHasCollision());
		thisAccessor.setIsRandomlyTicking(otherAccessor.getIsRandomlyTicking());
		this.lightLevel(otherAccessor.getLuminance());
		thisAccessor.setMapColor(otherAccessor.getMapColor());
		this.sound(otherAccessor.getSoundType());
		this.friction(otherAccessor.getFriction());
		this.speedFactor(otherAccessor.getSpeedFactor());
		thisAccessor.setDynamicShape(otherAccessor.getDynamicShape());
		thisAccessor.setCanOcclude(otherAccessor.getCanOcclude());
		thisAccessor.setIsAir(otherAccessor.getIsAir());
		thisAccessor.setIgnitedByLava(otherAccessor.getIgnitedByLava());
		thisAccessor.setLiquid(otherAccessor.getLiquid());
		thisAccessor.setForceSolidOff(otherAccessor.getForceSolidOff());
		thisAccessor.setForceSolidOn(otherAccessor.getForceSolidOn());
		this.pushReaction(otherAccessor.getPushReaction());
		thisAccessor.setRequiresCorrectToolForDrops(otherAccessor.isRequiresCorrectToolForDrops());
		thisAccessor.setOffsetFunction(otherAccessor.getOffsetFunction());
		thisAccessor.setSpawnTerrainParticles(otherAccessor.getSpawnTerrainParticles());
		thisAccessor.setRequiredFeatures(otherAccessor.getRequiredFeatures());
		this.emissiveRendering(otherAccessor.getEmissiveRendering());
		this.instrument(otherAccessor.getInstrument());
		thisAccessor.setReplaceable(otherAccessor.getReplaceable());

		// Vanilla did not copy those fields until 23w45a, which introduced
		// copyShallow method (maintaining the behavior previously used by the copy method)
		// and the copy method that copies those fields as well. copyShallow is now
		// deprecated. To maintain compatibility and since this behavior seems to be the
		// more proper way, this copies all the fields, not just the shallow ones.
		// Fields are added by field definition order.
		this.jumpFactor(otherAccessor.getJumpFactor());
		this.drops(otherAccessor.getDrops());
		this.isValidSpawn(otherAccessor.getIsValidSpawn());
		this.isRedstoneConductor(otherAccessor.getIsRedstoneConductor());
		this.isSuffocating(otherAccessor.getIsSuffocating());
		this.isViewBlocking(otherAccessor.getIsViewBlocking());
		this.hasPostProcess(otherAccessor.getHasPostProcess());
	}

	/**
	 * @deprecated replace with {@link BlockBehaviour.Properties#of()}
	 */
	@Deprecated
	public static FabricBlockSettings of() {
		return new FabricBlockSettings();
	}

	/**
	 * @deprecated replace with {@link BlockBehaviour.Properties#of()}
	 */
	@Deprecated
	public static FabricBlockSettings create() {
		return new FabricBlockSettings();
	}

	// Fabricated: create() and of() both stay: neither matches a vanilla method's descriptor (they return
	// FabricBlockSettings), so each keeps Fabric's own name in Intermediary, and mods call both by those names.

	/**
	 * @deprecated replace with {@link BlockBehaviour.Properties#ofFullCopy(BlockBehaviour)}
	 */
	@Deprecated
	public static FabricBlockSettings copyOf(BlockBehaviour block) {
		return new FabricBlockSettings(((AbstractBlockAccessor) block).getProperties());
	}

	/**
	 * @deprecated replace with {@link BlockBehaviour.Properties#ofFullCopy(BlockBehaviour)}
	 */
	@Deprecated
	public static FabricBlockSettings copyOf(BlockBehaviour.Properties settings) {
		return new FabricBlockSettings(settings);
	}

	@Deprecated
	@Override
	public FabricBlockSettings noCollission() {
		super.noCollission();
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings noOcclusion() {
		super.noOcclusion();
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings friction(float value) {
		super.friction(value);
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings speedFactor(float velocityMultiplier) {
		super.speedFactor(velocityMultiplier);
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings jumpFactor(float jumpVelocityMultiplier) {
		super.jumpFactor(jumpVelocityMultiplier);
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings sound(SoundType group) {
		super.sound(group);
		return this;
	}

	// Fabricated: upstream's luminance override takes its Mojang name, lightLevel, which upstream's lightLevel alias has
	// too; luminance itself is among Fabric's names below.

	@Deprecated
	@Override
	public FabricBlockSettings lightLevel(ToIntFunction<BlockState> luminanceFunction) {
		super.lightLevel(luminanceFunction);
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings strength(float hardness, float resistance) {
		super.strength(hardness, resistance);
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings instabreak() {
		super.instabreak();
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings strength(float strength) {
		super.strength(strength);
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings randomTicks() {
		super.randomTicks();
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings dynamicShape() {
		super.dynamicShape();
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings noLootTable() {
		super.noLootTable();
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings dropsLike(Block block) {
		super.dropsLike(block);
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings air() {
		super.air();
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings isValidSpawn(BlockBehaviour.StateArgumentPredicate<EntityType<?>> predicate) {
		super.isValidSpawn(predicate);
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings isRedstoneConductor(BlockBehaviour.StatePredicate predicate) {
		super.isRedstoneConductor(predicate);
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings isSuffocating(BlockBehaviour.StatePredicate predicate) {
		super.isSuffocating(predicate);
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings isViewBlocking(BlockBehaviour.StatePredicate predicate) {
		super.isViewBlocking(predicate);
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings hasPostProcess(BlockBehaviour.StatePredicate predicate) {
		super.hasPostProcess(predicate);
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings emissiveRendering(BlockBehaviour.StatePredicate predicate) {
		super.emissiveRendering(predicate);
		return this;
	}

	/**
	 * Make the block require tool to drop and slows down mining speed if the incorrect tool is used.
	 */
	@Deprecated
	@Override
	public FabricBlockSettings requiresCorrectToolForDrops() {
		super.requiresCorrectToolForDrops();
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings mapColor(MapColor color) {
		super.mapColor(color);
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings destroyTime(float hardness) {
		super.destroyTime(hardness);
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings explosionResistance(float resistance) {
		super.explosionResistance(resistance);
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings offsetType(BlockBehaviour.OffsetType offsetType) {
		super.offsetType(offsetType);
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings noTerrainParticles() {
		super.noTerrainParticles();
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings requiredFeatures(FeatureFlag... features) {
		super.requiredFeatures(features);
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings mapColor(Function<BlockState, MapColor> mapColorProvider) {
		super.mapColor(mapColorProvider);
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings ignitedByLava() {
		super.ignitedByLava();
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings liquid() {
		super.liquid();
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings forceSolidOn() {
		super.forceSolidOn();
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings forceSolidOff() {
		super.forceSolidOff();
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings pushReaction(PushReaction pistonBehavior) {
		super.pushReaction(pistonBehavior);
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings instrument(NoteBlockInstrument instrument) {
		super.instrument(instrument);
		return this;
	}

	@Deprecated
	@Override
	public FabricBlockSettings replaceable() {
		super.replaceable();
		return this;
	}

	/* FABRIC ADDITIONS*/

	/**
	 * @deprecated Please use {@link FabricBlockSettings#luminance(int)}.
	 */
	@Deprecated
	public FabricBlockSettings lightLevel(int lightLevel) {
		this.luminance(lightLevel);
		return this;
	}

	/**
	 * @deprecated replace with {@link BlockBehaviour.Properties#lightLevel(ToIntFunction)}
	 */
	@Deprecated
	public FabricBlockSettings luminance(int luminance) {
		this.lightLevel(ignored -> luminance);
		return this;
	}

	@Deprecated
	public FabricBlockSettings drops(ResourceKey<LootTable> dropTableId) {
		((AbstractBlockSettingsAccessor) this).setDrops(dropTableId);
		return this;
	}

	/* FABRIC DELEGATE WRAPPERS */

	/**
	 * @deprecated Please migrate to {@link BlockBehaviour.Properties#mapColor(MapColor)}
	 */
	@Deprecated
	public FabricBlockSettings materialColor(MapColor color) {
		return this.mapColor(color);
	}

	/**
	 * @deprecated Please migrate to {@link BlockBehaviour.Properties#mapColor(DyeColor)}
	 */
	@Deprecated
	public FabricBlockSettings materialColor(DyeColor color) {
		return this.mapColor(color);
	}

	/**
	 * @deprecated Please migrate to {@link BlockBehaviour.Properties#mapColor(DyeColor)}
	 */
	@Deprecated
	public FabricBlockSettings mapColor(DyeColor color) {
		return this.mapColor(color.getMapColor());
	}

	@Deprecated
	public FabricBlockSettings collidable(boolean collidable) {
		((AbstractBlockSettingsAccessor) this).setHasCollision(collidable);
		return this;
	}

	// Fabricated: upstream's methods under Fabric's own names. They return FabricBlockSettings, so no vanilla method's
	// descriptor matches them and each keeps its Fabric name in Intermediary, where mods call it (CORPUS-39); each
	// does what the override under Mojang's name above does.

	@Deprecated
	public FabricBlockSettings luminance(ToIntFunction<BlockState> luminanceFunction) {
		return lightLevel(luminanceFunction);
	}

	@Deprecated
	public FabricBlockSettings noCollision() {
		return noCollission();
	}

	@Deprecated
	public FabricBlockSettings nonOpaque() {
		return noOcclusion();
	}

	@Deprecated
	public FabricBlockSettings slipperiness(float value) {
		return friction(value);
	}

	@Deprecated
	public FabricBlockSettings velocityMultiplier(float value) {
		return speedFactor(value);
	}

	@Deprecated
	public FabricBlockSettings jumpVelocityMultiplier(float value) {
		return jumpFactor(value);
	}

	@Deprecated
	public FabricBlockSettings sounds(SoundType group) {
		return sound(group);
	}

	@Deprecated
	public FabricBlockSettings breakInstantly() {
		return instabreak();
	}

	@Deprecated
	public FabricBlockSettings ticksRandomly() {
		return randomTicks();
	}

	@Deprecated
	public FabricBlockSettings dynamicBounds() {
		return dynamicShape();
	}

	@Deprecated
	public FabricBlockSettings dropsNothing() {
		return noLootTable();
	}

	@Deprecated
	public FabricBlockSettings allowsSpawning(BlockBehaviour.StateArgumentPredicate<EntityType<?>> predicate) {
		return isValidSpawn(predicate);
	}

	@Deprecated
	public FabricBlockSettings solidBlock(BlockBehaviour.StatePredicate predicate) {
		return isRedstoneConductor(predicate);
	}

	@Deprecated
	public FabricBlockSettings suffocates(BlockBehaviour.StatePredicate predicate) {
		return isSuffocating(predicate);
	}

	@Deprecated
	public FabricBlockSettings blockVision(BlockBehaviour.StatePredicate predicate) {
		return isViewBlocking(predicate);
	}

	@Deprecated
	public FabricBlockSettings postProcess(BlockBehaviour.StatePredicate predicate) {
		return hasPostProcess(predicate);
	}

	@Deprecated
	public FabricBlockSettings emissiveLighting(BlockBehaviour.StatePredicate predicate) {
		return emissiveRendering(predicate);
	}

	@Deprecated
	public FabricBlockSettings requiresTool() {
		return requiresCorrectToolForDrops();
	}

	@Deprecated
	public FabricBlockSettings hardness(float value) {
		return destroyTime(value);
	}

	@Deprecated
	public FabricBlockSettings resistance(float value) {
		return explosionResistance(value);
	}

	@Deprecated
	public FabricBlockSettings offset(BlockBehaviour.OffsetType offsetType) {
		return offsetType(offsetType);
	}

	@Deprecated
	public FabricBlockSettings noBlockBreakParticles() {
		return noTerrainParticles();
	}

	@Deprecated
	public FabricBlockSettings requires(FeatureFlag... features) {
		return requiredFeatures(features);
	}

	@Deprecated
	public FabricBlockSettings burnable() {
		return ignitedByLava();
	}

	@Deprecated
	public FabricBlockSettings solid() {
		return forceSolidOn();
	}

	@Deprecated
	public FabricBlockSettings notSolid() {
		return forceSolidOff();
	}

	@Deprecated
	public FabricBlockSettings pistonBehavior(PushReaction behavior) {
		return pushReaction(behavior);
	}
}
