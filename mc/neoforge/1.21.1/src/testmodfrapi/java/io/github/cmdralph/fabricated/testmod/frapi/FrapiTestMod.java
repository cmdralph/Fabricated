package io.github.cmdralph.fabricated.testmod.frapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Registers a block and its item for {@link FrapiClientTestMod}'s color providers (1.21.1). The test mod for Fabric
 * API's client modules on 1.21.1, which only the Fabric API jar provides: it joins CI's client run with Fabric API.
 */
public final class FrapiTestMod implements ModInitializer {
	static final Logger LOGGER = LoggerFactory.getLogger("fabricated-testmod-frapi");
	static Block tintedBlock;
	static Item tintedItem;

	@Override
	public void onInitialize() {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("fabricated_testmod_frapi", "tinted_block");
		tintedBlock = Registry.register(BuiltInRegistries.BLOCK, id, new Block(BlockBehaviour.Properties.of()));
		tintedItem = Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(tintedBlock, new Item.Properties()));
	}
}
