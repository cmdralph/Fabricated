package io.github.cmdralph.fabricated.forgetest;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2378;
import net.minecraft.class_243;
import net.minecraft.class_2960;
import net.minecraft.class_3218;
import net.minecraft.class_5454;
import net.minecraft.class_7923;

/**
 * The forge-legacy test mod's part for Fabricated's native fabric-dimensions-v1 on Forge 47 (L4): Forge moved vanilla's
 * dimension change into its own method and a teleporter, where upstream's hooks found it no more. An armor stand
 * teleported from the overworld to the End through {@code FabricDimensions.teleport} must land in the End at the target,
 * facing its way, and, as upstream leaves the End's special cases out of such a teleport, the End's obsidian platform
 * must not be built: a block where vanilla builds the platform's floor ({@code (100, 48, 0)}) must stay as it was. The
 * game's classes are named as a Fabric mod for 1.20.1 names them (the stand-ins in src/testmodstubs).
 */
public final class ForgeDimensionTest {
	/** Where vanilla builds the End's obsidian platform floor (5 by 5 around it) when an entity arrives with a portal. */
	private static final int PLATFORM_X = 100;
	private static final int PLATFORM_Y = 48;
	private static final int PLATFORM_Z = 0;

	private ForgeDimensionTest() {
	}

	/** On the server thread, after the first tick, as Fabric mods teleport entities. */
	public static void check(class_3218 overworld, class_3218 end) {
		if (overworld == null || end == null) {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST dimensions: no overworld ({}) or End ({})", overworld, end);
			return;
		}
		try {
			class_2378<class_2248> blocks = class_7923.field_41175;
			class_2248 stone = blocks.method_10223(new class_2960("minecraft", "stone"));
			class_2338 floor = new class_2338(PLATFORM_X, PLATFORM_Y, PLATFORM_Z);
			end.method_8652(floor, stone.method_9564(), 3);

			class_2378<class_1299<?>> entityTypes = class_7923.field_41177;
			class_1297 entity = entityTypes.method_10223(new class_2960("minecraft", "armor_stand")).method_5883(overworld);
			entity.method_5808(0.5, 100.0, 0.5, 0.0F, 0.0F);
			overworld.method_8649(entity);
			class_243 target = new class_243(10.5, 80.0, 10.5);
			class_1297 moved = FabricDimensions.teleport(entity, end, new class_5454(target, class_243.field_1353, 90.0F, 0.0F));

			List<String> failed = new ArrayList<>();
			if (moved == null) {
				failed.add("the teleport returned no entity");
			} else {
				if (moved.method_37908() != end) failed.add("the entity is in " + moved.method_37908() + ", not the End");
				if (moved.method_19538().method_1025(target) > 1.0E-6) failed.add("the entity is at " + moved.method_19538());
				if (moved.method_36454() != 90.0F) failed.add("the entity faces " + moved.method_36454());
				moved.method_31472();
			}
			if (!end.method_8320(floor).method_27852(stone)) failed.add("the End's obsidian platform was built");
			if (failed.isEmpty()) {
				ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST dimensions OK");
			} else {
				ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST dimensions: the teleport went wrong: {}", failed);
			}
		} catch (RuntimeException | LinkageError e) {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST dimensions: the teleport failed", e);
		}
	}
}
