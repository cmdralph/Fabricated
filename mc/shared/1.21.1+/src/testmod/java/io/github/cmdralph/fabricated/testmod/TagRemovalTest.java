package io.github.cmdralph.fabricated.testmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * CI test mod part for tag files' removals: the test mod's item tag lists stone, dirt and gravel, and removes dirt in
 * Fabric's {@code fabric:remove} list (fabric-tag-api-v1, which Fabricated reads into NeoForge's) and gravel in
 * NeoForge's own {@code remove}. With Fabric API's tag module passed through, its mixin must leave NeoForge's list alone.
 */
public final class TagRemovalTest implements ModInitializer {
	private static final TagKey<Item> REMOVAL = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("fabricated_testmod", "removal"));

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			boolean stone = new ItemStack(Items.STONE).is(REMOVAL);
			boolean dirt = new ItemStack(Items.DIRT).is(REMOVAL);
			boolean gravel = new ItemStack(Items.GRAVEL).is(REMOVAL);
			if (stone && !dirt && !gravel) {
				TestMod.LOGGER.info("FABRICATED-TEST tag removal OK");
			} else {
				TestMod.LOGGER.error("FABRICATED-TEST tag removal FAILED: stone is in the tag {}, dirt (fabric:remove) {}, gravel (remove) {}", stone, dirt, gravel);
			}
		});
	}
}
