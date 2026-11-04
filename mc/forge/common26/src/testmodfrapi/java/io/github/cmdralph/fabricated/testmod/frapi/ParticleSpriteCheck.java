package io.github.cmdralph.fabricated.testmod.frapi;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The terrain block's Fabric model gives a level-aware particle material ({@code FabricBlockStateModel.particleMaterial}
 * with the level), which Forge 64 to 66 ask a model with its model data instead of (PART-4): the model calls
 * {@link #asked} as it gives it, which logs once for each place the game asked from. Once the player is in the world,
 * this client makes the block's breaking particles (whose sprite Forge sets again in {@code TerrainParticle.updateSprite})
 * and a block marker of it, and shows the screen effect inside it: the block is put at the player's eye on this client
 * only, at the end of each client tick until the effect asked, and taken away at the start of the next, before the
 * player moves (a block there would push them out), so only the frames drawn between have it.
 */
final class ParticleSpriteCheck {
	/** Client ticks the screen effect is tried for: 20 seconds. */
	private static final int SCREEN_TICKS = 400;
	private static final String SCREEN = "screen effect";
	private static final Map<String, AtomicBoolean> LOGGED = Map.of(SCREEN, new AtomicBoolean(), "block marker", new AtomicBoolean(), "block particle",
			new AtomicBoolean());
	// Client thread only.
	private static boolean particlesMade;
	private static int screenTicks;
	private static @Nullable BlockPos placed;
	private static @Nullable BlockState replaced;

	private ParticleSpriteCheck() {
	}

	static void register() {
		ClientTickEvents.START_CLIENT_TICK.register(client -> takeAway(client.level));
		ClientTickEvents.END_CLIENT_TICK.register(ParticleSpriteCheck::put);
	}

	/** Called by the terrain block's model as it gives its level-aware particle material. */
	static void asked(BlockState state, BlockPos pos) {
		String asker = asker();
		if (asker != null && !LOGGED.get(asker).getAndSet(true)) FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi {} sprite OK ({} at {})", asker, state, pos);
	}

	/**
	 * Which of the checked places the game asks from, by the innermost of its frames on the stack that says: the screen
	 * effect (on 26.3 extracted with the player's state), a block marker, or a block particle's sprite set again from the
	 * block's position. Upstream's own hook as a block particle is made isn't one. Walks the stack: test code only.
	 */
	private static @Nullable String asker() {
		return StackWalker.getInstance().walk(frames -> frames.map(frame -> {
			String type = frame.getClassName();
			String method = frame.getMethodName();
			if (type.endsWith(".ScreenEffectRenderer") || type.endsWith(".LevelExtractor") && method.equals("extractPlayerState")) return SCREEN;
			if (type.endsWith(".BlockMarker")) return "block marker";
			return type.endsWith(".TerrainParticle") && method.equals("updateSprite") ? "block particle" : null;
		}).filter(Objects::nonNull).findFirst().orElse(null));
	}

	private static void put(Minecraft client) {
		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		Block block = FrapiTestMod.terrainBlock;
		if (level == null || player == null || block == null) return;
		BlockState terrain = block.defaultBlockState();
		if (!particlesMade) {
			particlesMade = true;
			level.addDestroyBlockEffect(player.blockPosition(), terrain);
			level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK_MARKER, terrain), player.getX(), player.getEyeY(), player.getZ(), 0, 0, 0);
		}
		if (LOGGED.get(SCREEN).get() || screenTicks++ >= SCREEN_TICKS) return;
		BlockPos eye = BlockPos.containing(player.getEyePosition());
		BlockState there = level.getBlockState(eye);
		if (!there.isAir()) return;
		level.setBlockAndUpdate(eye, terrain);
		placed = eye;
		replaced = there;
	}

	private static void takeAway(@Nullable ClientLevel level) {
		BlockPos pos = placed;
		BlockState state = replaced;
		placed = null;
		replaced = null;
		if (level != null && pos != null && state != null) level.setBlockAndUpdate(pos, state);
	}
}
