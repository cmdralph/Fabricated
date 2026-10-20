package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * CI test for Fabric mods' fluids under NeoForge, which asks every fluid for a fluid type and throws for a mod's fluid
 * without one. Two fluids, one of them tagged {@code minecraft:water} by the test mod's data: each family has one
 * registered type, read from Fabric's attributes (the block's light and name), water-like only for the tagged one.
 * Placing the other in the world asks NeoForge whether it meets another fluid, and a pig moves through a column of it
 * as through air.
 */
public final class FluidTest implements ModInitializer {
	private static Family dry;
	private static Family wet;

	@Override
	public void onInitialize() {
		// Lava's block properties: its light (15) reaches the type through Fabric's default attributes.
		dry = new Family("dry_fluid", Blocks.LAVA);
		wet = new Family("wet_fluid", Blocks.WATER);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			List<String> failed = new ArrayList<>();
			try {
				check(server.overworld(), failed);
			} catch (RuntimeException e) {
				failed.add(e.toString());
			}
			if (failed.isEmpty()) {
				TestMod.LOGGER.info("FABRICATED-TEST fluid types OK");
			} else {
				TestMod.LOGGER.error("FABRICATED-TEST fluid types FAILED: {}", failed);
			}
		});
	}

	private static void check(ServerLevel level, List<String> failed) {
		FluidType type = dry.source.getFluidType();
		if (dry.flowing.getFluidType() != type) failed.add("the flowing fluid has another type");
		if (!dry.id.equals(NeoForgeRegistries.FLUID_TYPES.getKey(type))) failed.add("registered as " + NeoForgeRegistries.FLUID_TYPES.getKey(type));
		if (type.getLightLevel() != 15) failed.add("light " + type.getLightLevel());
		if (!type.getDescriptionId().equals(dry.block.getDescriptionId())) failed.add("description id " + type.getDescriptionId());
		if (!type.getDescription().getString().equals(dry.block.getName().getString())) failed.add("name " + type.getDescription().getString());
		if (type.getIsWaterLike() || !wet.source.getFluidType().getIsWaterLike()) failed.add("water-like: untagged " + type.getIsWaterLike() + ", tagged " + wet.source.getFluidType().getIsWaterLike());

		// Placing a liquid block asks NeoForge's fluid interactions about it, which ask for its type.
		BlockPos bottom = new BlockPos(20, level.getMaxY() - 30, 20);
		for (int i = 0; i < 8; i++) level.setBlockAndUpdate(bottom.above(i), dry.block.defaultBlockState());
		EntityType<?> pigType = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("pig"));
		if (!(pigType.create(level, EntitySpawnReason.TRIGGERED) instanceof LivingEntity pig)) {
			failed.add("could not create a pig");
			return;
		}
		pig.setPos(bottom.getX() + 0.5, bottom.getY() + 7, bottom.getZ() + 0.5);
		level.addFreshEntity(pig);
		double start = pig.getY();
		for (int i = 0; i < 10; i++) pig.tick();
		double fell = start - pig.getY();
		pig.discard();
		for (int i = 0; i < 8; i++) level.setBlockAndUpdate(bottom.above(i), Blocks.AIR.defaultBlockState());
		// About 4 blocks through air in 10 ticks; none where NeoForge's movement for mod fluids applies (26.2 and later).
		if (fell < 2.0) failed.add("a pig fell " + fell + " blocks through the fluid in 10 ticks");
	}

	/** A source, its flowing fluid and its liquid block, registered under {@code fabricated_testmod:<name>}. */
	static final class Family {
		final Identifier id;
		final TestFluid source;
		final TestFluid flowing;
		final LiquidBlock block;

		Family(String name, Block properties) {
			id = Identifier.fromNamespaceAndPath("fabricated_testmod", name);
			source = Registry.register(BuiltInRegistries.FLUID, id, new TestFluid.Source(this));
			flowing = Registry.register(BuiltInRegistries.FLUID, Identifier.fromNamespaceAndPath("fabricated_testmod", name + "_flowing"), new TestFluid.Flowing(this));
			ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
			block = Registry.register(BuiltInRegistries.BLOCK, key, new LiquidBlock(source, BlockBehaviour.Properties.ofFullCopy(properties).setId(key)) {
			});
		}
	}
}
