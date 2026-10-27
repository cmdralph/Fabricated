package io.github.cmdralph.fabricated.forgetest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2378;
import net.minecraft.class_2404;
import net.minecraft.class_2960;
import net.minecraft.class_3218;
import net.minecraft.class_4970;
import net.minecraft.class_7923;

/**
 * The forge-legacy test mod's part for Fabric mods' fluids on Forge 47, which asks every fluid for a fluid type and
 * throws for a mod's fluid without one ("Mod fluids must override getFluidType"). Two fluids, one of them tagged
 * {@code minecraft:water} by the test mod's data: each family has one registered type, the in-game mod's, lit like the
 * fluid's block (lava's light) and named by it, water to entities only for the tagged one. Placing the other in the world
 * asks Forge about it as its neighbours update, and a pig falls through a column of it as through air. A Fabric mod
 * doesn't compile against Forge: the types are read reflectively, by Forge's own (unobfuscated) names. The game's
 * classes are named as a Fabric mod for 1.20.1 names them (the stand-ins in src/testmodstubs).
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
	public static void check(class_3218 level) {
		if (dry == null) return;
		List<String> failed = new ArrayList<>();
		try {
			check(level, failed);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			failed.add(e instanceof InvocationTargetException ite ? ite.getCause().toString() : e.toString());
		}
		if (failed.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST fluid types OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST fluid types: {}", failed);
		}
	}

	private static void check(class_3218 level, List<String> failed) throws ReflectiveOperationException {
		Object type = call(dry.source, "getFluidType");
		if (call(dry.flowing, "getFluidType") != type) failed.add("the flowing fluid has another type");
		Class<?> forgeRegistries = Class.forName("net.minecraftforge.registries.ForgeRegistries", true, ForgeFluidTest.class.getClassLoader());
		Object fluidTypes = ((Supplier<?>) forgeRegistries.getField("FLUID_TYPES").get(null)).get();
		Object key = call(fluidTypes, "getKey", type);
		if (!dry.id.toString().equals(String.valueOf(key))) failed.add("registered as " + key);
		Object light = call(type, "getLightLevel");
		if (!Integer.valueOf(15).equals(light)) failed.add("light " + light);
		// The block's name: Block.getDescriptionId is block.<namespace>.<path> for a registered block.
		Object descriptionId = call(type, "getDescriptionId");
		if (!("block." + ForgeTestMod.MOD_ID + ".dry_fluid").equals(descriptionId)) failed.add("description id " + descriptionId);
		Object dryWater = call(type, "canSwim", (Object) null);
		Object wetWater = call(call(wet.source, "getFluidType"), "canSwim", (Object) null);
		if (!Boolean.FALSE.equals(dryWater) || !Boolean.TRUE.equals(wetWater)) failed.add("water to entities: untagged " + dryWater + ", tagged " + wetWater);

		// Placing liquid blocks updates their neighbours, which asks Forge's fluid interactions about them (their type).
		class_2338 bottom = ForgeTestMod.testSite(level, 4, 280, 4);
		int x = bottom.method_10263();
		int y = bottom.method_10264();
		int z = bottom.method_10260();
		for (int i = 0; i < 8; i++) level.method_8652(new class_2338(x, y + i, z), dry.block.method_9564(), 3);
		class_2378<class_1299<?>> entityTypes = class_7923.field_41177;
		class_1297 pig = entityTypes.method_10223(new class_2960("minecraft", "pig")).method_5883(level);
		if (pig == null) {
			failed.add("could not create a pig");
		} else {
			pig.method_5808(x + 0.5, y + 7, z + 0.5, 0.0F, 0.0F);
			level.method_8649(pig);
			double start = pig.method_23318();
			for (int i = 0; i < 10; i++) pig.method_5773();
			double fell = start - pig.method_23318();
			pig.method_31472();
			// About 4 blocks through air in 10 ticks; a pig floats in what Forge 47 moves it through as water.
			if (fell < 2.0) failed.add("a pig fell " + fell + " blocks through the fluid in 10 ticks");
		}
		class_2248 air = block("air");
		for (int i = 0; i < 8; i++) level.method_8652(new class_2338(x, y + i, z), air.method_9564(), 3);
	}

	private static class_2248 block(String path) {
		return class_7923.field_41175.method_10223(new class_2960("minecraft", path));
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

	/** A source, its flowing fluid and its liquid block, registered under {@code fabricated-forge-testmod:<name>}. */
	static final class Family {
		final class_2960 id;
		final ForgeTestFluid source;
		final ForgeTestFluid flowing;
		final class_2404 block;

		Family(String name, class_2248 properties) {
			id = new class_2960(ForgeTestMod.MOD_ID, name);
			source = class_2378.method_10230(class_7923.field_41173, id, new ForgeTestFluid.Source(this));
			flowing = class_2378.method_10230(class_7923.field_41173, new class_2960(ForgeTestMod.MOD_ID, name + "_flowing"), new ForgeTestFluid.Flowing(this));
			block = class_2378.method_10230(class_7923.field_41175, id, new class_2404(source, class_4970.class_2251.method_9630(properties)) {
			});
		}
	}
}
