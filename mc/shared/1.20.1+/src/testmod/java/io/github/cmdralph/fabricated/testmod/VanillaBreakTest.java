package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * CI test mod part for vanilla's block-breaking calls, which NeoForge and Forge make through the block state's
 * {@code onDestroyedByPlayer} and Fabricated puts back in {@code destroyBlock} for the blocks keeping its default
 * (SEAM-3): the test mod's {@code mixin.BlockRemovalMixin} wraps the server's {@code removeBlock} call there, and leaves
 * tuff where calcite breaks. Once the server has started, a fake player breaks calcite, which must turn to tuff through
 * the hook, and stone, which the hook passes on and which must be gone; and, where the platform's half provides one, a
 * block overriding the platform's extension ({@code fabricated_testmod:break_override}), which must break as its override
 * says (it leaves amethyst) without the hook. Fabric API's {@code PlayerBlockBreakEvents.AFTER} must fire once for each
 * break, with the block broken: Fabricated fires it on vanilla's path, the calcite's and the stone's (whose class the
 * hook has rewritten), and on the platform's, the overriding block's (on Forge 47, through its SRG-named hooks too).
 * With Fabric API's fake player, so on Forge only with Fabric API.
 */
public final class VanillaBreakTest implements ModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("fabricated_testmod");
	private static final AtomicInteger WRAPPED = new AtomicInteger();
	private static Block override;

	/** The mixin's wrapper of the removal: counts it; calcite is left as tuff (true, as removed); null to remove as vanilla. */
	public static Boolean removing(ServerLevel level, BlockPos pos) {
		WRAPPED.incrementAndGet();
		return level.getBlockState(pos).is(Blocks.CALCITE) ? level.setBlock(pos, Blocks.TUFF.defaultBlockState(), 3) : null;
	}

	@Override
	public void onInitialize() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, TestCompat.id("fabricated_testmod", "break_override"));
		Block block = TestPlatform.breakOverride(TestCompat.withId(BlockBehaviour.Properties.of(), key));
		if (block != null) override = Registry.register(BuiltInRegistries.BLOCK, key, block);
		if (!FabricLoader.getInstance().isModLoaded("fabric-events-interaction-v0")) return;
		ServerLifecycleEvents.SERVER_STARTED.register(Breaking::check);
	}

	/** The breaks, apart: only this class names Fabric API's fake player and events. */
	private static final class Breaking {
		/** The block break events' AFTER, as it fired: where, and the block broken. */
		private static final List<String> AFTER = new ArrayList<>();

		static void check(MinecraftServer server) {
			ServerLevel level = server.overworld();
			BlockPos calcite = new BlockPos(-96, 250, -80), stone = calcite.east(2), overriding = calcite.east(4);
			List<BlockPos> placed = new ArrayList<>(List.of(calcite, stone, overriding));
			PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
				if (placed.contains(pos)) AFTER.add(pos.toShortString() + " " + BuiltInRegistries.BLOCK.getKey(state.getBlock()));
			});
			try {
				List<String> failures = new ArrayList<>();
				FakePlayer player = FakePlayer.get(level);
				level.setBlock(calcite, Blocks.CALCITE.defaultBlockState(), 2);
				level.setBlock(stone, Blocks.STONE.defaultBlockState(), 2);
				int wrapped = WRAPPED.get();
				if (!player.gameMode.destroyBlock(calcite) || !level.getBlockState(calcite).is(Blocks.TUFF)) {
					failures.add("calcite broke to " + level.getBlockState(calcite) + ", not the hook's tuff");
				}
				if (!player.gameMode.destroyBlock(stone) || !level.getBlockState(stone).isAir()) failures.add("stone broke to " + level.getBlockState(stone));
				if (WRAPPED.get() != wrapped + 2) failures.add("the hook of vanilla's removeBlock ran " + (WRAPPED.get() - wrapped) + " times for 2 breaks");
				if (override != null) {
					level.setBlock(overriding, override.defaultBlockState(), 2);
					int before = WRAPPED.get();
					player.gameMode.destroyBlock(overriding);
					BlockState left = level.getBlockState(overriding);
					if (!left.is(Blocks.AMETHYST_BLOCK)) failures.add("the block overriding the platform's extension broke to " + left + ", not its amethyst");
					if (WRAPPED.get() != before) failures.add("the hook of vanilla's removeBlock ran for the block overriding the platform's extension");
				}
				List<String> expected = new ArrayList<>(List.of(calcite.toShortString() + " minecraft:calcite", stone.toShortString() + " minecraft:stone"));
				if (override != null) expected.add(overriding.toShortString() + " " + BuiltInRegistries.BLOCK.getKey(override));
				if (!AFTER.equals(expected)) failures.add("PlayerBlockBreakEvents.AFTER fired " + AFTER + ", not " + expected);
				if (failures.isEmpty()) {
					LOGGER.info("FABRICATED-TEST vanilla block breaking OK{}", override != null ? " (with a block overriding the platform's extension)" : "");
				} else {
					LOGGER.error("FABRICATED-TEST vanilla block breaking FAILED: {}", String.join("; ", failures));
				}
			} catch (RuntimeException | LinkageError e) {
				LOGGER.error("FABRICATED-TEST vanilla block breaking FAILED", e);
			} finally {
				for (BlockPos pos : placed) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}
}
