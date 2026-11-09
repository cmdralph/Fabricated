package io.github.cmdralph.fabricated.forgetest;

import io.github.cmdralph.fabricated.forgetest.mixin.MobBucketItemAccessor;
import net.minecraft.class_1299;
import net.minecraft.class_1792;
import net.minecraft.class_2960;
import net.minecraft.class_7923;

/**
 * CORPUS-94: a Fabric mod's accessor of a mob bucket's entity type reads the type, which Forge 47 keeps behind a
 * supplier. Supplementaries' is such an accessor (its dispenser behaviours, which Moonlight registers after a reload,
 * call it), and so is Inventory Profiles Next's. The accessor invokes the in-game mod's bridge; without it pre-flight
 * would refuse the accessor, and the test mod with it. The pufferfish bucket must hold a pufferfish.
 */
public final class ForgeMobBucketTest {
	private ForgeMobBucketTest() {
	}

	/** At the first server tick. */
	public static void check() {
		class_1792 bucket = class_7923.field_41178.method_10223(new class_2960("minecraft", "pufferfish_bucket"));
		class_1299<?> pufferfish = class_7923.field_41177.method_10223(new class_2960("minecraft", "pufferfish"));
		if (!(bucket instanceof MobBucketItemAccessor accessor)) {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST mob bucket accessor FAILED: the pufferfish bucket is {}", bucket.getClass().getName());
			return;
		}
		class_1299<?> type = accessor.fabricatedtest$type();
		if (type == pufferfish) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST mob bucket accessor OK ({})", class_7923.field_41177.method_10221(type));
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST mob bucket accessor FAILED: the pufferfish bucket holds {}", type);
		}
	}
}
