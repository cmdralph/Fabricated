package io.github.cmdralph.fabricated.forgetest;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import java.util.ArrayList;
import java.util.List;


/**
 * The forge-legacy test mod's part for the order Fabric mods register in (CORPUS-73). Fabric lets a mod make its
 * objects first and register them after, in any order; Forge 47 looked a block's, fluid's or sound's delegate up as a
 * block item was registered or a music disc made, and threw when it wasn't registered yet (Lootr registers block items
 * before their blocks). From the main entrypoint: a block item registered before its block, which then is its item, and
 * a music disc made before its sound is registered, which then plays it. {@link ForgeFluidTest}'s fluids have the
 * same for their liquid blocks and buckets.
 */
final class ForgeRegistrationOrderTest {
	private ForgeRegistrationOrderTest() {
	}

	static void register() {
		List<String> failed = new ArrayList<>();
		try {
			ResourceLocation id = new ResourceLocation(ForgeTestMod.NAMESPACE, "item_first_block");
			Block block = new Block(BlockBehaviour.Properties.copy(BuiltInRegistries.BLOCK.get(new ResourceLocation("minecraft", "stone"))));
			BlockItem item = Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(block, new Item.Properties()));
			Registry.register(BuiltInRegistries.BLOCK, id, block);
			if (item.getBlock() != block) failed.add("the block item's block is " + item.getBlock());
			if (Item.byBlock(block) != item) failed.add("the block's item is " + Item.byBlock(block));
		} catch (RuntimeException | LinkageError e) {
			failed.add("the block item registered before its block: " + e);
		}
		try {
			ResourceLocation id = new ResourceLocation(ForgeTestMod.NAMESPACE, "sound_later_disc");
			SoundEvent sound = SoundEvent.createVariableRangeEvent(id);
			RecordItem disc = new RecordItem(5, sound, new Item.Properties(), 60);
			Registry.register(BuiltInRegistries.SOUND_EVENT, id, sound);
			Registry.register(BuiltInRegistries.ITEM, id, disc);
			if (disc.getSound() != sound) failed.add("the disc's sound is " + disc.getSound());
		} catch (RuntimeException | LinkageError e) {
			failed.add("the music disc made before its sound was registered: " + e);
		}
		if (failed.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST registration order OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-TEST registration order: {}", failed);
		}
	}
}
