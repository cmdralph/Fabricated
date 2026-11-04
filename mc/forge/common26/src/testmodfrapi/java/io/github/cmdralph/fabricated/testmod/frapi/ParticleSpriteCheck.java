package io.github.cmdralph.fabricated.testmod.frapi;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The terrain block's Fabric model gives a level-aware particle material ({@code FabricBlockStateModel.particleMaterial}
 * with the level), which Forge 64 to 66 ask a model with its model data instead of (PART-4): the model calls
 * {@link #asked} as it gives it, which logs once for each place the game asked from. Once the player is in the world,
 * this client makes the block's breaking particles (whose sprite Forge sets again in {@code TerrainParticle.updateSprite})
 * and a block marker of it, and shows the screen effect inside it: the block is put at the player's eye on this client
 * only, at the end of each client tick until the effect asked, and taken away at the start of the next, before the
 * player moves (a block there would push them out), so only the frames drawn between have it. Where the effect never
 * asked, what the check met is logged as the game stops: how often the block was put there, whether the effect asked
 * the model's sprite without the level (the game's own call, past Fabricated's), and on Forge 66 what its extraction
 * met ({@code mixin/OverlayProbeMixin}).
 */
public final class ParticleSpriteCheck {
	/** Client ticks the screen effect is tried for: 20 seconds. */
	private static final int SCREEN_TICKS = 400;
	private static final String SCREEN = "screen effect";
	private static final Map<String, AtomicBoolean> LOGGED = Map.of(SCREEN, new AtomicBoolean(), "block marker", new AtomicBoolean(), "block particle",
			new AtomicBoolean());
	private static final AtomicBoolean SCREEN_WITHOUT_LEVEL = new AtomicBoolean();
	private static final AtomicBoolean ASKED_ELSEWHERE = new AtomicBoolean();
	// Client thread only.
	private static boolean particlesMade;
	private static int screenTicks;
	private static int puts;
	/** The tick tried the block was first put at the eye on, or -1. */
	private static int firstPut = -1;
	private static @Nullable BlockPos placed;
	private static @Nullable BlockState replaced;
	/** Forge 66's extractions of the player's state, those with the block at the eye, and what its lookup found ({@code mixin/OverlayProbeMixin}). */
	private static int extractions;
	private static int extractionsWithBlock;
	private static int lookups;
	private static int overlaysFound;
	private static @Nullable String firstOverlay;
	/** What the first of Forge 66's lookups with the block at the eye tested there. */
	private static @Nullable String lookupMet;

	private ParticleSpriteCheck() {
	}

	static void register() {
		ClientTickEvents.START_CLIENT_TICK.register(client -> takeAway(client.level));
		ClientTickEvents.END_CLIENT_TICK.register(ParticleSpriteCheck::put);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			if (!LOGGED.get(SCREEN).get()) {
				FrapiTestMod.LOGGER.warn("FABRICATED-TEST frapi screen effect not asked: the block put at the player's eye {} time(s) in {} tick(s) tried,"
						+ " first at tick {}, its sprite asked without the level {}; the player's state extracted {} time(s), {} with the block there,"
						+ " Forge's lookup run {} time(s), finding a block {} time(s) (first {}); the first with the block there: {}", puts, screenTicks,
						firstPut, SCREEN_WITHOUT_LEVEL.get(), extractions, extractionsWithBlock, lookups, overlaysFound, firstOverlay, lookupMet);
			}
		});
	}

	/** Called by the terrain block's model as it gives its level-aware particle material. */
	static void asked(BlockState state, BlockPos pos) {
		String asker = asker();
		if (asker != null) {
			if (!LOGGED.get(asker).getAndSet(true)) FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi {} sprite OK ({} at {})", asker, state, pos);
		} else if (puts > 0 && !LOGGED.get(SCREEN).get() && !ASKED_ELSEWHERE.getAndSet(true)) {
			// Once the block was put at the eye: where else the game asked, for a screen effect the check doesn't recognize.
			FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi screen effect: the level-aware sprite asked at {} from {}", pos, callers());
		}
	}

	/** The innermost frames past this class's: test code only. */
	private static List<String> callers() {
		return StackWalker.getInstance().walk(frames -> frames.filter(frame -> !frame.getClassName().equals(ParticleSpriteCheck.class.getName())).limit(8)
				.map(frame -> frame.getClassName() + "." + frame.getMethodName()).toList());
	}

	/** Forge 66's extraction of the player's state begins (render thread). */
	public static void extracting() {
		extractions++;
		ClientLevel level = Minecraft.getInstance().level;
		BlockPos pos = placed;
		if (level != null && pos != null && FrapiTestMod.terrainBlock != null && level.getBlockState(pos).is(FrapiTestMod.terrainBlock)) extractionsWithBlock++;
	}

	/**
	 * Forge 66's lookup of the block the camera is in begins (render thread): the first time the block is at the eye,
	 * what the lookup tests there, its bounds and whether the block blocks the view within them. Its methods taking those
	 * bounds are 26.3's, reached reflectively as this class builds on Forge 64 and 65 too: test code only.
	 */
	public static void lookingUp(LocalPlayer player, Frustum frustum) {
		lookups++;
		BlockPos pos = placed;
		if (lookupMet != null || pos == null) return;
		BlockState state = player.level().getBlockState(pos);
		String bounds;
		String blocking;
		try {
			AABB near = ((AABB) Frustum.class.getMethod("getNearPlaneBounds").invoke(frustum)).move(player.getEyePosition());
			bounds = near.toString();
			blocking = String.valueOf(BlockState.class.getMethod("isViewBlocking", BlockGetter.class, BlockPos.class, AABB.class).invoke(state, player.level(), pos, near));
		} catch (ReflectiveOperationException | ClassCastException e) {
			bounds = e.toString();
			blocking = "unknown";
		}
		lookupMet = "player without physics " + player.noPhysics + ", eye " + player.getEyePosition() + ", near plane " + bounds + "; at " + pos + " " + state
				+ ", render shape " + state.getRenderShape() + ", view blocking there " + blocking;
	}

	/** What Forge 66's lookup of the block the camera is in returned: null, or the block and its position (render thread). */
	public static void overlayFound(@Nullable Object overlay) {
		if (overlay == null) return;
		if (overlaysFound++ == 0) firstOverlay = overlay.toString();
	}

	/** Called by the terrain block's model as it gives its particle material without the level. */
	static void askedWithoutLevel() {
		if (!LOGGED.get(SCREEN).get() && !SCREEN_WITHOUT_LEVEL.get() && SCREEN.equals(asker())) SCREEN_WITHOUT_LEVEL.set(true);
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
		// Not where the player's chunk hasn't arrived yet.
		if (!there.isAir() || !level.setBlockAndUpdate(eye, terrain)) return;
		if (puts++ == 0) firstPut = screenTicks;
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
