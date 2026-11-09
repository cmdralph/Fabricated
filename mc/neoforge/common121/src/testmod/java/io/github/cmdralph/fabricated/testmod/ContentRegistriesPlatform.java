package io.github.cmdralph.fabricated.testmod;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ComposterBlock;

/** The platform's part of the content registries check where composting has a chance per item (before 26.3). */
final class ContentRegistriesPlatform {
	private ContentRegistriesPlatform() {
	}

	/** The chance an item stack fills a composter by, as the platform reads it: NeoForge's data map. */
	static float compostChance(ItemStack stack) {
		return ComposterBlock.getValue(stack);
	}
}
