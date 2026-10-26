package io.github.cmdralph.fabricated.forgetest;

import java.util.ArrayList;
import java.util.List;

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
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryManager;

/**
 * The forge test mod's part for Fabric mods' fluids on Forge, which asks every fluid for a fluid type and throws for a
 * mod's fluid without one ("Mod fluids must override getFluidType"). Two fluids, one of them tagged
 * {@code minecraft:water} by the test mod's data: each family has one registered type, the in-game mod's, read from the
 * fluid's block (lava's light, its name), water to entities only for the tagged one. Placing the other in the world asks
 * Forge about it as its neighbours update, and a pig falls through a column of it as through air.
 */
public final class ForgeFluidTest {
	private static Family dry;
	private static Family wet;

	private ForgeFluidTest() {
	}

	/** From the main entrypoint, with the registries open, as Fabric mods register their fluids. */
	public static void register() {
		// Lava's block properties: its light (15) is the fluid's, by Fabric's default attributes.
		dry = new Family("dry_fluid", Blocks.LAVA);
		wet = new Family("wet_fluid", Blocks.WATER);
	}

	public static void check(ServerLevel level) {
		if (dry == null) return;
		List<String> failed = new ArrayList<>();
		try {
			check(level, failed);
		} catch (RuntimeException | LinkageError e) {
			failed.add(e.toString());
		}
		if (failed.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST fluid types OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST fluid types: {}", failed);
		}
	}

	private static void check(ServerLevel level, List<String> failed) {
		FluidType type = dry.source.getFluidType();
		if (dry.flowing.getFluidType() != type) failed.add("the flowing fluid has another type");
		Identifier key = RegistryManager.ACTIVE.getRegistry(ForgeRegistries.Keys.FLUID_TYPES).getResourceKey(type).map(ResourceKey::identifier).orElse(null);
		if (!dry.id.equals(key)) failed.add("registered as " + key);
		if (type.getLightLevel() != 15) failed.add("light " + type.getLightLevel());
		if (!type.getDescriptionId().equals(dry.block.getDescriptionId())) failed.add("description id " + type.getDescriptionId());
		if (!type.getDescription().getString().equals(dry.block.getName().getString())) failed.add("name " + type.getDescription().getString());
		boolean dryWater = type.canSwim(null);
		boolean wetWater = wet.source.getFluidType().canSwim(null);
		if (dryWater || !wetWater) failed.add("water to entities: untagged " + dryWater + ", tagged " + wetWater);

		// Placing liquid blocks updates their neighbours, which asks Forge's fluid interactions about them (their type).
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
		// About 4 blocks through air in 10 ticks; far less where Forge moves it as in lava.
		if (fell < 2.0) failed.add("a pig fell " + fell + " blocks through the fluid in 10 ticks");
	}

	/** A source, its flowing fluid and its liquid block, registered under {@code fabricated-forge-testmod:<name>}. */
	static final class Family {
		final Identifier id;
		final ForgeTestFluid source;
		final ForgeTestFluid flowing;
		final LiquidBlock block;

		Family(String name, Block properties) {
			id = Identifier.fromNamespaceAndPath(ForgeTestMod.MOD_ID, name);
			source = Registry.register(BuiltInRegistries.FLUID, id, new ForgeTestFluid.Source(this));
			flowing = Registry.register(BuiltInRegistries.FLUID, Identifier.fromNamespaceAndPath(ForgeTestMod.MOD_ID, name + "_flowing"), new ForgeTestFluid.Flowing(this));
			ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
			block = Registry.register(BuiltInRegistries.BLOCK, key, new LiquidBlock(source, BlockBehaviour.Properties.ofFullCopy(properties).setId(key)) {
			});
		}
	}
}
