package net.fabricmc.fabric.impl.item;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.component.BlockTransformer;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * Fabric's block transformer registrations (axe, hoe and shovel conversions, 26.3). Upstream adds them to the
 * {@code minecraft:axe}/{@code hoe}/{@code shovel} block transformers while the data pack loads; NeoForge has no hook
 * there yet, so on Fabricated they are accepted but not applied, and a warning names the first mod-facing call.
 */
public final class BlockTransformerHelperImpl {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final AtomicBoolean WARNED = new AtomicBoolean();

	private BlockTransformerHelperImpl() {
	}

	private static void notApplied(String what) {
		if (WARNED.compareAndSet(false, true)) {
			LOGGER.warn("A Fabric mod registered a block transformer ({}); Fabricated can't apply block transformer registrations on NeoForge yet, so it has no effect", what);
		}
	}

	public static void registerAxe(BlockTransformer transformer) {
		notApplied("axe");
	}

	public static void registerHoe(BlockTransformer transformer) {
		notApplied("hoe");
	}

	public static void registerShovel(BlockTransformer transformer) {
		notApplied("shovel");
	}

	public static void registerAxe(BlockTransformer.BlockTransformData transformData) {
		notApplied("axe");
	}

	public static void registerHoe(BlockTransformer.BlockTransformData transformData) {
		notApplied("hoe");
	}

	public static void registerShovel(BlockTransformer.BlockTransformData transformData) {
		notApplied("shovel");
	}

	public static void registerStripping(BlockPredicate fromBlockPredicate, BlockStateProvider toBlockState) {
		notApplied("stripping");
	}

	public static void registerTilling(BlockPredicate fromBlockPredicate, BlockStateProvider toBlockState) {
		notApplied("tilling");
	}

	public static void registerFlattening(BlockPredicate fromBlockPredicate, BlockStateProvider toBlockState) {
		notApplied("flattening");
	}
}
