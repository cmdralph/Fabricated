package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;

import io.github.cmdralph.fabricated.forgetest.ForgeTestMod;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryManager;

/**
 * CI test for Fabric mods' fluids on Forge, which asks every fluid for a fluid type and throws for a mod's fluid without
 * one ("Mod fluids must override getFluidType"). Two fluids, one of them tagged {@code minecraft:water} by the test
 * mod's data: each family has one registered type, the in-game mod's, read from the fluid's block (lava's light, its
 * name), water to entities only for the tagged one. Placing the other in the world asks Forge about it as its
 * neighbours update, and a pig falls through a column of it as through air.
 */
public final class FluidTest implements ModInitializer {
	private static final TagKey<Fluid> DRY_TAG = TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("fabricated_testmod", "dry"));
	private static final TagKey<Fluid> NO_FLUIDS = TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("fabricated_testmod", "no_fluids"));
	private static Family dry;
	private static Family wet;

	@Override
	public void onInitialize() {
		// Lava's block properties: its light (15) is the fluid's, by Fabric's default attributes.
		dry = new Family("dry_fluid", Blocks.LAVA);
		wet = new Family("wet_fluid", Blocks.WATER);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			List<String> failed = new ArrayList<>();
			try {
				check(server.overworld(), failed);
			} catch (RuntimeException | LinkageError e) {
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
		Identifier key = RegistryManager.ACTIVE.getRegistry(ForgeRegistries.Keys.FLUID_TYPES).getResourceKey(type).map(TestCompat::location).orElse(null);
		if (!dry.id.equals(key)) failed.add("registered as " + key);
		if (type.getLightLevel() != 15) failed.add("light " + type.getLightLevel());
		if (!type.getDescriptionId().equals(dry.block.getDescriptionId())) failed.add("description id " + type.getDescriptionId());
		if (!type.getDescription().getString().equals(dry.block.getName().getString())) failed.add("name " + type.getDescription().getString());
		// Made before the fluid was registered: the fluid itself.
		if (dry.bucket.getFluid() != dry.source) failed.add("the bucket holds " + dry.bucket.getFluid());
		boolean dryWater = type.canSwim(null);
		boolean wetWater = wet.source.getFluidType().canSwim(null);
		if (dryWater || !wetWater) failed.add("water to entities: untagged " + dryWater + ", tagged " + wetWater);

		// Placing liquid blocks updates their neighbours, which asks Forge's fluid interactions about them (their type).
		BlockPos bottom = ForgeTestMod.testSite(level, 4, 4);
		for (int i = 0; i < 8; i++) level.setBlockAndUpdate(bottom.above(i), dry.block.defaultBlockState());
		EntityType<?> pigType = TestCompat.value(BuiltInRegistries.ENTITY_TYPE, Identifier.withDefaultNamespace("pig"));
		if (!(TestCompat.create(pigType, level) instanceof LivingEntity pig)) {
			failed.add("could not create a pig");
			return;
		}
		pig.setPos(bottom.getX() + 0.5, bottom.getY() + 7, bottom.getZ() + 0.5);
		level.addFreshEntity(pig);
		double start = pig.getY();
		for (int i = 0; i < 10; i++) pig.tick();
		// Fabric mods ask with their own fluids' tags, as vanilla answers for any (FLUID-2).
		pig.isEyeInFluid(DRY_TAG);
		if (pig.isEyeInFluid(NO_FLUIDS)) failed.add("the pig's eyes are in a tag without fluids");
		double fell = start - pig.getY();
		pig.discard();
		for (int i = 0; i < 8; i++) level.setBlockAndUpdate(bottom.above(i), Blocks.AIR.defaultBlockState());
		// About 4 blocks through air in 10 ticks; far less where Forge moves it as in lava.
		if (fell < 2.0) failed.add("a pig fell " + fell + " blocks through the fluid in 10 ticks");
	}

	/**
	 * A source, its flowing fluid, its liquid block and its bucket, registered under {@code fabricated_testmod:<name>}
	 * ({@code <name>_bucket} for the bucket). All are made first and registered after, as Fabric lets a mod and as
	 * TechReborn does: Forge looked the fluid up as the bucket was made, and threw (CORPUS-73).
	 */
	static final class Family {
		final Identifier id;
		final TestFluid source;
		final TestFluid flowing;
		final LiquidBlock block;
		final BucketItem bucket;

		Family(String name, Block properties) {
			id = Identifier.fromNamespaceAndPath("fabricated_testmod", name);
			source = new TestFluid.Source(this);
			flowing = new TestFluid.Flowing(this);
			ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
			block = new LiquidBlock(source, TestCompat.withId(BlockBehaviour.Properties.ofFullCopy(properties), key)) {
			};
			ResourceKey<Item> bucketKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("fabricated_testmod", name + "_bucket"));
			bucket = new BucketItem(source, TestCompat.withId(new Item.Properties(), bucketKey));
			Registry.register(BuiltInRegistries.FLUID, id, source);
			Registry.register(BuiltInRegistries.FLUID, Identifier.fromNamespaceAndPath("fabricated_testmod", name + "_flowing"), flowing);
			Registry.register(BuiltInRegistries.BLOCK, key, block);
			Registry.register(BuiltInRegistries.ITEM, bucketKey, bucket);
		}
	}
}
