package io.github.cmdralph.fabricated.forgetest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WeatheringCopper;
import java.util.Map;

import net.fabricmc.fabric.api.registry.OxidizableBlocksRegistry;

/**
 * The forge-legacy test mod's part for fabric-content-registries-v0's oxidizable blocks (CORPUS-91, INTERFACE-1; with
 * Fabric API's jar only): a pair {@code OxidizableBlocksRegistry} registers must be in vanilla's oxidation map
 * ({@code WeatheringCopper.NEXT_BY_BLOCK}, {@code NEXT_BY_BLOCK}), which stays immutable unless upstream's
 * {@code OxidizableMixin} made it a mutable copy. That mixin is an {@code @Inject} in an interface mixin, of which Forge
 * 47's Mixin applies none; Fabricated applies it, as Create's copper needs. The pair, two sandstone blocks no copper
 * becomes, is taken out again once checked.
 */
public final class ForgeOxidizableTest {
	private ForgeOxidizableTest() {
	}

	/** From the main entrypoint, where Create registers its copper; only with Fabric API's content registries module. */
	public static void check() {
		Block less = block("cut_sandstone");
		Block more = block("smooth_sandstone");
		Map<Block, Block> next = WeatheringCopper.NEXT_BY_BLOCK.get();
		try {
			OxidizableBlocksRegistry.registerOxidizableBlockPair(less, more);
			if (next.get(less) == more) {
				ForgeTestMod.LOGGER.info("FABRICATED-TEST oxidizable blocks OK");
			} else {
				ForgeTestMod.LOGGER.error("FABRICATED-TEST oxidizable blocks FAILED: the registered pair isn't in vanilla's oxidation map ({})", next.get(less));
			}
		} catch (UnsupportedOperationException e) {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST oxidizable blocks FAILED: vanilla's oxidation map is still immutable ({}), so upstream's interface"
					+ " mixin didn't apply", next.getClass().getName(), e);
		} finally {
			if (next.get(less) == more) next.remove(less);
		}
	}

	private static Block block(String path) {
		return BuiltInRegistries.BLOCK.get(new ResourceLocation("minecraft", path));
	}
}
