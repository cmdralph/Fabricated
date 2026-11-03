package io.github.cmdralph.fabricated.forgetest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.class_1747;
import net.minecraft.class_1792;
import net.minecraft.class_1813;
import net.minecraft.class_2248;
import net.minecraft.class_2378;
import net.minecraft.class_2960;
import net.minecraft.class_3414;
import net.minecraft.class_4970;
import net.minecraft.class_7923;

/**
 * The forge-legacy test mod's part for the order Fabric mods register in (CORPUS-73). Fabric lets a mod make its
 * objects first and register them after, in any order; Forge 47 looked a block's, fluid's or sound's delegate up as a
 * block item was registered or a music disc made, and threw when it wasn't registered yet (Lootr registers block items
 * before their blocks). From the main entrypoint: a block item registered before its block, which then is its item, and
 * a music disc made before its sound is registered, which then plays it. {@link ForgeFluidTest}'s fluids have the
 * same for their liquid blocks and buckets. In Intermediary's names (the stand-ins in src/testmodstubs).
 */
final class ForgeRegistrationOrderTest {
	private ForgeRegistrationOrderTest() {
	}

	static void register() {
		List<String> failed = new ArrayList<>();
		try {
			class_2960 id = new class_2960(ForgeTestMod.MOD_ID, "item_first_block");
			class_2248 block = new class_2248(class_4970.class_2251.method_9630(class_7923.field_41175.method_10223(new class_2960("minecraft", "stone"))));
			class_1747 item = class_2378.method_10230(class_7923.field_41178, id, new class_1747(block, new class_1792.class_1793()));
			class_2378.method_10230(class_7923.field_41175, id, block);
			if (item.method_7711() != block) failed.add("the block item's block is " + item.method_7711());
			if (class_1792.method_7867(block) != item) failed.add("the block's item is " + class_1792.method_7867(block));
		} catch (RuntimeException | LinkageError e) {
			failed.add("the block item registered before its block: " + e);
		}
		try {
			class_2960 id = new class_2960(ForgeTestMod.MOD_ID, "sound_later_disc");
			class_3414 sound = class_3414.method_47908(id);
			class_1813 disc = new class_1813(5, sound, new class_1792.class_1793(), 60);
			class_2378.method_10230(class_7923.field_41172, id, sound);
			class_2378.method_10230(class_7923.field_41178, id, disc);
			if (disc.method_8009() != sound) failed.add("the disc's sound is " + disc.method_8009());
		} catch (RuntimeException | LinkageError e) {
			failed.add("the music disc made before its sound was registered: " + e);
		}
		if (failed.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST registration order OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST registration order: {}", failed);
		}
	}
}
