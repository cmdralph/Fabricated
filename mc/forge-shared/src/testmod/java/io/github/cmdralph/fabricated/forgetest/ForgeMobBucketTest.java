package io.github.cmdralph.fabricated.forgetest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import io.github.cmdralph.fabricated.forgetest.mixin.MobBucketItemAccessor;

/**
 * CORPUS-94 and LEAVE-5: a Fabric mod's accessor of a mob bucket's entity type reads the type, which Forge 47 to 66 keep
 * behind a supplier. Supplementaries' is such an accessor (its dispenser behaviours, which Moonlight registers after a
 * reload, call it), and so is Inventory Profiles Next's, which pre-flight left out on Forge 61. The accessor invokes the
 * in-game mod's bridge; without it pre-flight would refuse the accessor, and the test mod with it. The pufferfish bucket
 * must hold a pufferfish.
 */
public final class ForgeMobBucketTest {
	private ForgeMobBucketTest() {
	}

	/** At the first server tick. */
	public static void check() {
		Item bucket = Items.PUFFERFISH_BUCKET;
		if (!(bucket instanceof MobBucketItemAccessor accessor)) {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST mob bucket accessor FAILED: the pufferfish bucket is {}", bucket.getClass().getName());
			return;
		}
		EntityType<?> type = accessor.fabricatedtest$type();
		// By its id: 26.2 moved the entity types' constants to EntityTypes.
		String id = type == null ? null : String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(type));
		if ("minecraft:pufferfish".equals(id)) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST mob bucket accessor OK ({})", id);
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST mob bucket accessor FAILED: the pufferfish bucket holds {}", type);
		}
	}
}
