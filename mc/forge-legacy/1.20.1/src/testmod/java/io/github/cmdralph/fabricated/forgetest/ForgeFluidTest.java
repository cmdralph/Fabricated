package io.github.cmdralph.fabricated.forgetest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;


/**
 * The forge-legacy test mod's part for Fabric mods' fluids on Forge 47, which asks every fluid for a fluid type and
 * throws for a mod's fluid without one ("Mod fluids must override getFluidType"). Two fluids, one of them tagged
 * {@code minecraft:water} by the test mod's data: each family has one registered type, the in-game mod's, lit like the
 * fluid's block (lava's light) and named by it, water to entities only for the tagged one. Placing the other in the world
 * asks Forge about it as its neighbours update, and a pig falls through a column of it as through air. A Fabric mod
 * doesn't compile against Forge: the types are read reflectively, by Forge's own (unobfuscated) names.
 */
public final class ForgeFluidTest {
	private static Family dry;
	private static Family wet;

	private ForgeFluidTest() {
	}

	/** From the main entrypoint, with the registries open, as Fabric mods register their fluids. */
	public static void register() {
		// Lava's block properties: its light (15) is the fluid's, by Fabric's default attributes.
		dry = new Family("dry_fluid", block("lava"));
		wet = new Family("wet_fluid", block("water"));
	}

	/** On the server thread, after the first tick. */
	public static void check(ServerLevel level) {
		if (dry == null) return;
		List<String> failed = new ArrayList<>();
		try {
			check(level, failed);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			failed.add(e instanceof InvocationTargetException ite ? ite.getCause().toString() : e.toString());
		}
		if (failed.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST fluid types OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-TEST fluid types: {}", failed);
		}
	}

	private static void check(ServerLevel level, List<String> failed) throws ReflectiveOperationException {
		Object type = fluidType(dry.source);
		if (fluidType(dry.flowing) != type) failed.add("the flowing fluid has another type");
		Class<?> forgeRegistries = Class.forName("net.minecraftforge.registries.ForgeRegistries", true, ForgeFluidTest.class.getClassLoader());
		Object fluidTypes = ((Supplier<?>) forgeRegistries.getField("FLUID_TYPES").get(null)).get();
		Object key = call(fluidTypes, "getKey", type);
		if (!dry.id.toString().equals(String.valueOf(key))) failed.add("registered as " + key);
		Object light = call(type, "getLightLevel");
		if (!Integer.valueOf(15).equals(light)) failed.add("light " + light);
		// The block's name: Block.getDescriptionId is block.<namespace>.<path> for a registered block.
		Object descriptionId = call(type, "getDescriptionId");
		if (!("block." + ForgeTestMod.NAMESPACE + ".dry_fluid").equals(descriptionId)) failed.add("description id " + descriptionId);
		// Made before the fluid was registered: the fluid itself (Forge's getFluid, which a Fabric mod can't name).
		Object bucketFluid = call(dry.bucket, "getFluid");
		if (bucketFluid != dry.source) failed.add("the bucket holds " + bucketFluid);
		Object blockFluid = call(dry.block, "getFluid");
		if (blockFluid != dry.source) failed.add("the liquid block holds " + blockFluid);
		Object dryWater = call(type, "canSwim", (Object) null);
		Object wetWater = call(fluidType(wet.source), "canSwim", (Object) null);
		if (!Boolean.FALSE.equals(dryWater) || !Boolean.TRUE.equals(wetWater)) failed.add("water to entities: untagged " + dryWater + ", tagged " + wetWater);

		// Placing liquid blocks updates their neighbours, which asks Forge's fluid interactions about them (their type).
		BlockPos bottom = ForgeTestMod.testSite(level, 4, 280, 4);
		int x = bottom.getX();
		int y = bottom.getY();
		int z = bottom.getZ();
		for (int i = 0; i < 8; i++) level.setBlock(new BlockPos(x, y + i, z), dry.block.defaultBlockState(), 3);
		Registry<EntityType<?>> entityTypes = BuiltInRegistries.ENTITY_TYPE;
		Entity pig = entityTypes.get(new ResourceLocation("minecraft", "pig")).create(level);
		if (pig == null) {
			failed.add("could not create a pig");
		} else {
			pig.moveTo(x + 0.5, y + 7, z + 0.5, 0.0F, 0.0F);
			level.addFreshEntity(pig);
			double start = pig.getY();
			for (int i = 0; i < 10; i++) pig.tick();
			double fell = start - pig.getY();
			pig.discard();
			// About 4 blocks through air in 10 ticks; a pig floats in what Forge 47 moves it through as water.
			if (fell < 2.0) failed.add("a pig fell " + fell + " blocks through the fluid in 10 ticks");
		}
		Block air = block("air");
		for (int i = 0; i < 8; i++) level.setBlock(new BlockPos(x, y + i, z), air.defaultBlockState(), 3);
	}

	private static Block block(String path) {
		return BuiltInRegistries.BLOCK.get(new ResourceLocation("minecraft", path));
	}

	/**
	 * Forge's {@code getFluidType}. Porting Lib's fluids module adds a method of that name to {@code Fluid} that returns its
	 * own fluid type, null for a mod's fluid it has none of, so the name alone can find either (CORPUS-110).
	 */
	private static Object fluidType(Object fluid) throws ReflectiveOperationException {
		for (Method method : fluid.getClass().getMethods()) {
			if (method.getName().equals("getFluidType") && method.getParameterCount() == 0
					&& method.getReturnType().getName().equals("net.minecraftforge.fluids.FluidType")) return method.invoke(fluid);
		}
		throw new NoSuchMethodException(fluid.getClass().getName() + ".getFluidType");
	}

	/** Calls {@code target}'s public method of that name taking {@code arguments} (Forge's, which a Fabric mod can't name). */
	private static Object call(Object target, String name, Object... arguments) throws ReflectiveOperationException {
		for (Method method : target.getClass().getMethods()) {
			if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
			Class<?>[] parameters = method.getParameterTypes();
			boolean fits = true;
			for (int i = 0; i < parameters.length; i++) {
				if (parameters[i].isPrimitive() || arguments[i] != null && !parameters[i].isInstance(arguments[i])) fits = false;
			}
			if (fits) return method.invoke(target, arguments);
		}
		throw new NoSuchMethodException(target.getClass().getName() + "." + name);
	}

	/**
	 * A source, its flowing fluid, its liquid block and its bucket, registered under {@code fabricated-forge-testmod:<name>}
	 * ({@code <name>_bucket} for the bucket). All are made first and registered after, as Fabric lets a mod and as
	 * TechReborn does: Forge 47 looked the fluid up as its block and bucket were made, and threw (CORPUS-73).
	 */
	static final class Family {
		final ResourceLocation id;
		final ForgeTestFluid source;
		final ForgeTestFluid flowing;
		final LiquidBlock block;
		final BucketItem bucket;

		Family(String name, Block properties) {
			id = new ResourceLocation(ForgeTestMod.NAMESPACE, name);
			source = new ForgeTestFluid.Source(this);
			flowing = new ForgeTestFluid.Flowing(this);
			block = new LiquidBlock(source, BlockBehaviour.Properties.copy(properties));
			bucket = new BucketItem(source, new Item.Properties());
			Registry.register(BuiltInRegistries.FLUID, id, source);
			Registry.register(BuiltInRegistries.FLUID, new ResourceLocation(ForgeTestMod.NAMESPACE, name + "_flowing"), flowing);
			Registry.register(BuiltInRegistries.BLOCK, id, block);
			Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(ForgeTestMod.NAMESPACE, name + "_bucket"), bucket);
		}
	}
}
