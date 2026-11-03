package io.github.cmdralph.fabricated.forgetest;

import java.util.List;

import io.github.cmdralph.fabricated.forgetest.mixin.PotionBrewingAccessor;
import io.github.cmdralph.fabricated.forgetest.mixin.PotionBrewingMixAccessor;
import net.minecraft.class_1842;
import net.minecraft.class_7923;

/**
 * CORPUS-37: Fabric mods' accessors of a brewing mix's {@code from} and {@code to} read vanilla's potions, which Forge 47
 * keeps as holders. Porting Lib's are such accessors (Create and Farmer's Delight need them), and so are EMI's. The
 * accessors invoke the in-game mod's bridges; without them pre-flight would refuse the accessors, and the test mod with
 * them. The first of vanilla's potion mixes must give two registered potions.
 */
public final class ForgeBrewingMixTest {
	private ForgeBrewingMixTest() {
	}

	/** At the first server tick. */
	public static void check() {
		List<?> mixes = PotionBrewingAccessor.fabricatedtest$potionMixes();
		if (mixes.isEmpty()) {
			ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST brewing mix accessors FAILED: the game has no potion mixes");
			return;
		}
		PotionBrewingMixAccessor mix = (PotionBrewingMixAccessor) mixes.get(0);
		Object from = mix.fabricatedtest$from();
		Object to = mix.fabricatedtest$to();
		if (from instanceof class_1842 in && to instanceof class_1842 out && class_7923.field_41179.method_10221(in) != null
				&& class_7923.field_41179.method_10221(out) != null) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST brewing mix accessors OK ({} to {})", class_7923.field_41179.method_10221(in),
					class_7923.field_41179.method_10221(out));
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST brewing mix accessors FAILED: the first potion mix's from and to are {} and {}",
					from == null ? null : from.getClass().getName(), to == null ? null : to.getClass().getName());
		}
	}
}
