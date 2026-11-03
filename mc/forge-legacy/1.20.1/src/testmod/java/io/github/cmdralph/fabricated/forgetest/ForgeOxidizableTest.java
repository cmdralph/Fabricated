package io.github.cmdralph.fabricated.forgetest;

import java.util.Map;

import net.fabricmc.fabric.api.registry.OxidizableBlocksRegistry;
import net.minecraft.class_2248;
import net.minecraft.class_2960;
import net.minecraft.class_5955;
import net.minecraft.class_7923;

/**
 * The forge-legacy test mod's part for fabric-content-registries-v0's oxidizable blocks (CORPUS-91, INTERFACE-1; with
 * Fabric API's jar only): a pair {@code OxidizableBlocksRegistry} registers must be in vanilla's oxidation map
 * ({@code WeatheringCopper.NEXT_BY_BLOCK}, {@code field_29564}), which stays immutable unless upstream's
 * {@code OxidizableMixin} made it a mutable copy. That mixin is an {@code @Inject} in an interface mixin, of which Forge
 * 47's Mixin applies none; Fabricated applies it, as Create's copper needs. The pair, two sandstone blocks no copper
 * becomes, is taken out again once checked.
 */
public final class ForgeOxidizableTest {
	private ForgeOxidizableTest() {
	}

	/** From the main entrypoint, where Create registers its copper; only with Fabric API's content registries module. */
	static void check() {
		class_2248 less = block("cut_sandstone");
		class_2248 more = block("smooth_sandstone");
		Map<class_2248, class_2248> next = class_5955.field_29564.get();
		try {
			OxidizableBlocksRegistry.registerOxidizableBlockPair(less, more);
			if (next.get(less) == more) {
				ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST oxidizable blocks OK");
			} else {
				ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST oxidizable blocks FAILED: the registered pair isn't in vanilla's oxidation map ({})", next.get(less));
			}
		} catch (UnsupportedOperationException e) {
			ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST oxidizable blocks FAILED: vanilla's oxidation map is still immutable ({}), so upstream's interface"
					+ " mixin didn't apply", next.getClass().getName(), e);
		} finally {
			if (next.get(less) == more) next.remove(less);
		}
	}

	private static class_2248 block(String path) {
		return class_7923.field_41175.method_10223(new class_2960("minecraft", path));
	}
}
