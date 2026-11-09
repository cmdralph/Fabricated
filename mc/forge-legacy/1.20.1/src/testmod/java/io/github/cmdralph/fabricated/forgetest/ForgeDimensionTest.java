package io.github.cmdralph.fabricated.forgetest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;

/**
 * The forge-legacy test mod's part for Fabricated's native fabric-dimensions-v1 on Forge 47 (L4): Forge moved vanilla's
 * dimension change into its own method and a teleporter, where upstream's hooks found it no more. An armor stand
 * teleported from the overworld to the End through {@code FabricDimensions.teleport} must land in the End at the target,
 * facing its way, and, as upstream leaves the End's special cases out of such a teleport, the End's obsidian platform
 * must not be built: a block where vanilla builds the platform's floor ({@code (100, 48, 0)}) must stay as it was.
 */
public final class ForgeDimensionTest {
	/** Where vanilla builds the End's obsidian platform floor (5 by 5 around it) when an entity arrives with a portal. */
	private static final int PLATFORM_X = 100;
	private static final int PLATFORM_Y = 48;
	private static final int PLATFORM_Z = 0;

	private ForgeDimensionTest() {
	}

	/** On the server thread, after the first tick, as Fabric mods teleport entities. */
	public static void check(ServerLevel overworld, ServerLevel end) {
		if (overworld == null || end == null) {
			ForgeTestMod.LOGGER.warn("FABRICATED-TEST dimensions: no overworld ({}) or End ({})", overworld, end);
			return;
		}
		try {
			Registry<Block> blocks = BuiltInRegistries.BLOCK;
			Block stone = blocks.get(new ResourceLocation("minecraft", "stone"));
			BlockPos floor = new BlockPos(PLATFORM_X, PLATFORM_Y, PLATFORM_Z);
			end.setBlock(floor, stone.defaultBlockState(), 3);

			Registry<EntityType<?>> entityTypes = BuiltInRegistries.ENTITY_TYPE;
			Entity entity = entityTypes.get(new ResourceLocation("minecraft", "armor_stand")).create(overworld);
			entity.moveTo(0.5, 100.0, 0.5, 0.0F, 0.0F);
			overworld.addFreshEntity(entity);
			Vec3 target = new Vec3(10.5, 80.0, 10.5);
			Entity moved = FabricDimensions.teleport(entity, end, new PortalInfo(target, Vec3.ZERO, 90.0F, 0.0F));

			List<String> failed = new ArrayList<>();
			if (moved == null) {
				failed.add("the teleport returned no entity");
			} else {
				if (moved.level() != end) failed.add("the entity is in " + moved.level() + ", not the End");
				if (moved.position().distanceToSqr(target) > 1.0E-6) failed.add("the entity is at " + moved.position());
				if (moved.getYRot() != 90.0F) failed.add("the entity faces " + moved.getYRot());
				moved.discard();
			}
			if (!end.getBlockState(floor).is(stone)) failed.add("the End's obsidian platform was built");
			if (failed.isEmpty()) {
				ForgeTestMod.LOGGER.info("FABRICATED-TEST dimensions OK");
			} else {
				ForgeTestMod.LOGGER.warn("FABRICATED-TEST dimensions: the teleport went wrong: {}", failed);
			}
		} catch (RuntimeException | LinkageError e) {
			ForgeTestMod.LOGGER.warn("FABRICATED-TEST dimensions: the teleport failed", e);
		}
	}
}
