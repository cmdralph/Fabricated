package io.github.cmdralph.fabricated.forgetest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import io.github.cmdralph.fabricated.forgetest.mixin.MobBucketItemAccessor;

/**
 * CORPUS-94: a Fabric mod's accessor of a mob bucket's entity type reads the type, which Forge 47 and 52 keep behind a
 * supplier. Supplementaries' is such an accessor (its dispenser behaviours, which Moonlight registers after a reload,
 * call it), and so is Inventory Profiles Next's. The accessor invokes the in-game mod's bridge; without it pre-flight
 * would refuse the accessor, and the test mod with it. The pufferfish bucket must hold a pufferfish.
 */
public final class ForgeMobBucketTest {
	private ForgeMobBucketTest() {
	}

	/** At the first server tick. */
	public static void check() {
		Item bucket = Items.PUFFERFISH_BUCKET;
		EntityType<?> pufferfish = EntityType.PUFFERFISH;
		if (!(bucket instanceof MobBucketItemAccessor accessor)) {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST mob bucket accessor FAILED: the pufferfish bucket is {}", bucket.getClass().getName());
			return;
		}
		EntityType<?> type = accessor.fabricatedtest$type();
		if (type == pufferfish) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST mob bucket accessor OK ({})", BuiltInRegistries.ENTITY_TYPE.getKey(type));
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST mob bucket accessor FAILED: the pufferfish bucket holds {}", type);
		}
	}
}
