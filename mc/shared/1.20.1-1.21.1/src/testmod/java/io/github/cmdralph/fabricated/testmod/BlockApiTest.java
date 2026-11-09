package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.block.v1.FabricBlockState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1.20.1 and 1.21.1 part of the CI test mod: exercises fabric-block-api-v1 (Fabricated's native module on NeoForge 21.1):
 * the trapdoor climbing tag and appearance through Fabric's interface; these versions' API has nothing more.
 */
public final class BlockApiTest implements ModInitializer {
	static ResourceLocation id(String path) {
		return TestCompat.id("fabricated_testmod", path);
	}

	@Override
	public void onInitialize() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id("climbable_block"));
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of();
		// In data/fabric/tags/block/can_climb_trapdoor_above.json.
		Block climbable = Registry.register(BuiltInRegistries.BLOCK, key, new Block(properties));

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel level = server.overworld();
			List<String> failed = new ArrayList<>();
			BlockState trapdoor = BuiltInRegistries.BLOCK.get(TestCompat.id("minecraft", "oak_trapdoor")).defaultBlockState();
			BlockState stone = BuiltInRegistries.BLOCK.get(TestCompat.id("minecraft", "stone")).defaultBlockState();
			if (!climbable.makesOpenTrapdoorAboveClimbable(climbable.defaultBlockState(), level, BlockPos.ZERO, trapdoor)
					|| stone.getBlock().makesOpenTrapdoorAboveClimbable(stone, level, BlockPos.ZERO, trapdoor)) failed.add("trapdoor_tag");
			if (((FabricBlockState) stone).getAppearance(level, BlockPos.ZERO, Direction.UP, null, null) != stone) failed.add("appearance");
			TestMod.report("block api", failed, "fabric-block-api-v1", Map.of("trapdoor_tag",
					"Forgified Fabric API's tag hook is in vanilla's LivingEntity.trapdoorUsableAsLadder, which NeoForge 21.1's ladder check no longer calls"));
		});
	}
}
