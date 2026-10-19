package io.github.cmdralph.fabricated.testmod.frapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * CI test mod for Fabric's renderer API (26.x, client smoke test with Fabric API): a block whose model is a Fabric
 * model ({@link FrapiClientTestMod}), placed next to the player when they join, so the client meshes it in the world.
 */
public final class FrapiTestMod implements ModInitializer {
	static final Logger LOGGER = LoggerFactory.getLogger("fabricated-testmod-frapi");
	static final Identifier TERRAIN_BLOCK_ID = Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "terrain_block");

	@Override
	public void onInitialize() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, TERRAIN_BLOCK_ID);
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, new Block(BlockBehaviour.Properties.of().setId(key)));
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> server.execute(() -> {
			BlockPos pos = listener.player.blockPosition().relative(Direction.NORTH, 2).above();
			listener.player.level().setBlockAndUpdate(pos, block.defaultBlockState());
		}));
	}
}
