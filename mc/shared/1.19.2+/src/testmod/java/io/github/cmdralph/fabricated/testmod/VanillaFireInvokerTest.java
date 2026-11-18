package io.github.cmdralph.fabricated.testmod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.testmod.mixin.FireBlockInvoker;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * CI test mod part for an invoker of a vanilla method the platform gave a parameter vanilla's never had (LEAVE-6): the
 * test mod's {@code mixin.FireBlockInvoker} invokes vanilla's private {@code FireBlock.checkBurnOut(level, pos, chance,
 * random, age)}, as Supplementaries' lit lumisene does, where NeoForge and Forge 52 to 66 take the side the fire comes from
 * too (Forge 43 and 47 in their {@code tryCatchFire}), which they only pass to the block state's flammability and
 * caught-fire extensions, whose defaults ignore it: pre-flight points the invoker at the bridge Fabricated declares in
 * {@code FireBlock}. Once the server has started, the fire is asked to burn oak planks with a chance of 1, which it does
 * every time (setting them alight or burning them away): they must be gone, the call failing neither for a missing
 * method nor otherwise.
 */
public final class VanillaFireInvokerTest implements ModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("fabricated_testmod");

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(VanillaFireInvokerTest::check);
	}

	private static void check(MinecraftServer server) {
		ServerLevel level = server.overworld();
		BlockPos pos = new BlockPos(-112, 250, -96);
		try {
			level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), 2);
			((FireBlockInvoker) Blocks.FIRE).invokeCheckBurnOut(level, pos, 1, level.getRandom(), 0);
			BlockState after = level.getBlockState(pos);
			if (after.is(Blocks.OAK_PLANKS)) {
				LOGGER.error("FABRICATED-TEST vanilla fire invoker FAILED: the fire left the oak planks as they were");
			} else {
				LOGGER.info("FABRICATED-TEST vanilla fire invoker OK (the oak planks became {})", after);
			}
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("FABRICATED-TEST vanilla fire invoker FAILED", e);
		} finally {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
	}
}
