package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.block.v1.FabricBlockState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1.21.11 part of the CI test mod: exercises Fabricated's native fabric-block-api-v1 (the trapdoor climbing tag and
 * appearance through Fabric's interface; 1.21.11's API has nothing more).
 */
public final class BlockApiTest implements ModInitializer {
	static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("fabricated_testmod", path);
	}

	@Override
	public void onInitialize() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id("climbable_block"));
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().setId(key);
		// In data/fabric/tags/block/can_climb_trapdoor_above.json.
		Block climbable = Registry.register(BuiltInRegistries.BLOCK, key, new Block(properties));

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel level = server.overworld();
			List<String> failed = new ArrayList<>();
			BlockState trapdoor = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("oak_trapdoor")).defaultBlockState();
			BlockState stone = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("stone")).defaultBlockState();
			if (!climbable.makesOpenTrapdoorAboveClimbable(climbable.defaultBlockState(), level, BlockPos.ZERO, trapdoor)
					|| stone.getBlock().makesOpenTrapdoorAboveClimbable(stone, level, BlockPos.ZERO, trapdoor)) failed.add("trapdoor_tag");
			if (((FabricBlockState) stone).getAppearance(level, BlockPos.ZERO, Direction.UP, null, null) != stone) failed.add("appearance");
			if (failed.isEmpty()) {
				TestMod.LOGGER.info("FABRICATED-TEST block api OK");
			} else {
				TestMod.LOGGER.error("FABRICATED-TEST block api FAILED: {}", failed);
			}
		});
	}
}
