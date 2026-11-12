package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.api.Fabricated;
import io.github.cmdralph.fabricated.api.FabricatedApi;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * CI test mod part for vanilla's item checks, which NeoForge and Forge make through the stack's {@code canPerformAction}
 * of an action, and Fabricated puts back for the stacks whose item keeps the platform's default (SEAM-6): the test mod's
 * {@code mixin.ShearsCheckMixin} wraps the tripwire's check for shears in {@code playerWillDestroy}, as BCLib's and
 * Porting Lib's wrap it, letting a stick disarm a tripwire while the check asks it to. Once the server has started, a fake
 * player holding a stick, then dirt, then shears, takes a tripwire: the stick must disarm it through the hook, the dirt
 * must not, each asking the hook once; vanilla's shears, which override the platform's default, must disarm it as the
 * platform has it, without the hook. With Fabric API's fake player, so on Forge only with Fabric API. Where a native mod's
 * mixin hooks the platform's code there, Fabricated keeps the class as the platform has it, as its report says and the
 * class shows (no helper of Fabricated's in it): the check is then SKIPPED, naming the mixin.
 */
public final class VanillaItemCheckTest implements ModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("fabricated_testmod");
	private static final AtomicInteger ASKED = new AtomicInteger();
	/** The report's line where a native mod's mixin keeps the class as the platform has it (SeamConflicts). */
	private static final Pattern KEPT = Pattern.compile("Vanilla's calls not put back in TripWireBlock:\\s*((?:NeoForge|Forge) mod [^\\n]*)");
	/** Whether the hook lets a stick count as shears: only while the check asks it to. */
	private static volatile boolean answering;

	/** The hook of the tripwire's check for shears: counts it, and lets a stick count as shears while asked to. */
	public static boolean shears(ItemStack stack, boolean vanilla) {
		ASKED.incrementAndGet();
		return vanilla || answering && stack.is(Items.STICK);
	}

	@Override
	public void onInitialize() {
		if (!FabricLoader.getInstance().isModLoaded("fabric-events-interaction-v0")) return;
		ServerLifecycleEvents.SERVER_STARTED.register(Disarming::check);
	}

	/**
	 * Why Fabricated keeps TripWireBlock as the platform has it, where it does for a native mod's mixin: the class has none
	 * of the helpers its rewrite adds, and the report names the mixin. Null otherwise.
	 */
	private static String keptByANativeMod() {
		try {
			TripWireBlock.class.getDeclaredMethod("fabricated$keepsActionDefault", ItemStack.class);
			return null;
		} catch (NoSuchMethodException e) {
			Matcher kept = KEPT.matcher(Fabricated.get().map(FabricatedApi::renderReport).orElse(""));
			return kept.find() ? kept.group(1).trim() : null;
		}
	}

	/** The checks, apart: only this class names Fabric API's fake player. */
	private static final class Disarming {
		static void check(MinecraftServer server) {
			String kept = keptByANativeMod();
			if (kept != null) {
				LOGGER.info("FABRICATED-TEST vanilla item check SKIPPED: Fabricated keeps TripWireBlock as the platform has it: {}", kept);
				return;
			}
			ServerLevel level = server.overworld();
			BlockPos pos = new BlockPos(-96, 250, -64);
			FakePlayer player = FakePlayer.get(level);
			try {
				List<String> failures = new ArrayList<>();
				answering = true;
				// A stick, which keeps the platform's default: vanilla's check refuses it, the hook lets it disarm the tripwire.
				int asked = ASKED.get();
				if (!disarms(level, pos, player, Items.STICK)) failures.add("a stick didn't disarm the tripwire, though the hook let it count as shears");
				if (ASKED.get() != asked + 1) failures.add("the hook of vanilla's shears check ran " + (ASKED.get() - asked) + " times for the stick");
				// Dirt: the hook passes vanilla's refusal on.
				asked = ASKED.get();
				if (disarms(level, pos, player, Items.DIRT)) failures.add("dirt disarmed the tripwire");
				if (ASKED.get() != asked + 1) failures.add("the hook of vanilla's shears check ran " + (ASKED.get() - asked) + " times for the dirt");
				// Shears, which override the platform's default: the platform's own check disarms it, without the hook.
				asked = ASKED.get();
				if (!disarms(level, pos, player, Items.SHEARS)) failures.add("shears didn't disarm the tripwire");
				if (ASKED.get() != asked) failures.add("the hook of vanilla's shears check ran for the shears, which the platform asks itself");
				if (failures.isEmpty()) {
					LOGGER.info("FABRICATED-TEST vanilla item check OK (with vanilla's shears asked as the platform has them)");
				} else {
					LOGGER.error("FABRICATED-TEST vanilla item check FAILED: {}", String.join("; ", failures));
				}
			} catch (RuntimeException | LinkageError e) {
				LOGGER.error("FABRICATED-TEST vanilla item check FAILED", e);
			} finally {
				answering = false;
				player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			}
		}

		/** Whether a player holding the item, about to take a tripwire there, disarms it. */
		private static boolean disarms(ServerLevel level, BlockPos pos, FakePlayer player, Item item) {
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
			BlockState state = Blocks.TRIPWIRE.defaultBlockState();
			level.setBlock(pos, state, 2);
			Blocks.TRIPWIRE.playerWillDestroy(level, pos, state, player);
			BlockState after = level.getBlockState(pos);
			return after.is(Blocks.TRIPWIRE) && after.getValue(TripWireBlock.DISARMED);
		}
	}
}
