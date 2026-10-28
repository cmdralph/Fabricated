package io.github.cmdralph.fabricated.testmod.frapi;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.world.item.ItemStack;

/**
 * fabric-rendering-v1 on 1.21.1: color providers for the test block and its item, registered from the client
 * entrypoint as Fabric mods register theirs, must be the game's colors once it has started, and Fabric's lookups
 * ({@code ColorProviderRegistry.get}, which reads NeoForge's maps by block and by item) must return them. A HUD
 * callback must be called as the HUD is drawn.
 */
public final class FrapiClientTestMod implements ClientModInitializer {
	private static final int BLOCK_COLOR = 0x3366CC;
	private static final int ITEM_COLOR = 0xCC6633;
	private static final BlockColor BLOCK_PROVIDER = (state, level, pos, tintIndex) -> BLOCK_COLOR;
	private static final ItemColor ITEM_PROVIDER = (stack, tintIndex) -> ITEM_COLOR;
	// Render thread only.
	private static boolean hudDrawn;

	@Override
	public void onInitializeClient() {
		ColorProviderRegistry.BLOCK.register(BLOCK_PROVIDER, FrapiTestMod.tintedBlock);
		ColorProviderRegistry.ITEM.register(ITEM_PROVIDER, FrapiTestMod.tintedItem);
		HudRenderCallback.EVENT.register((graphics, delta) -> {
			if (!hudDrawn) {
				hudDrawn = true;
				FrapiTestMod.LOGGER.info("FABRICATED-TEST hud callback OK");
			}
		});
		ClientLifecycleEvents.CLIENT_STARTED.register(FrapiClientTestMod::checkColors);
	}

	private static void checkColors(Minecraft client) {
		List<String> failed = new ArrayList<>();
		if (ColorProviderRegistry.BLOCK.get(FrapiTestMod.tintedBlock) != BLOCK_PROVIDER) failed.add("Fabric's block lookup didn't return the provider");
		int block = client.getBlockColors().getColor(FrapiTestMod.tintedBlock.defaultBlockState(), null, null, 0);
		if (block != BLOCK_COLOR) failed.add("the block's color is " + Integer.toHexString(block));
		if (ColorProviderRegistry.ITEM.get(FrapiTestMod.tintedItem) != ITEM_PROVIDER) failed.add("Fabric's item lookup didn't return the provider");
		int item = client.getItemColors().getColor(new ItemStack(FrapiTestMod.tintedItem), 0);
		if (item != ITEM_COLOR) failed.add("the item's color is " + Integer.toHexString(item));
		if (failed.isEmpty()) FrapiTestMod.LOGGER.info("FABRICATED-TEST colors OK");
		else FrapiTestMod.LOGGER.error("FABRICATED-TEST colors FAILED: {}", String.join("; ", failed));
	}
}
