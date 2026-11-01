package io.github.cmdralph.fabricated.testmod.frapi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.world.item.ItemStack;

/**
 * fabric-rendering-v1 on 1.21.1: color providers for the test block and its item, registered from the client
 * entrypoint as Fabric mods register theirs, must be the game's colors once it has started, and Fabric's lookups
 * ({@code ColorProviderRegistry.get}, which reads NeoForge's maps by block and by item) must return them. A HUD
 * callback must be called as the HUD is drawn.
 *
 * <p>fabric-key-binding-api-v1 (KEYMAP-2): a key binding registered from the client entrypoint must be in the game's
 * options once it has started, its category sorted after the game's, registering it twice or once the options exist
 * must throw as upstream's does, and by the time the game stops it must have the key CI's options file saved for it
 * ({@code ci/neoforge/smoke-client.sh}).
 */
public final class FrapiClientTestMod implements ClientModInitializer {
	private static final int BLOCK_COLOR = 0x3366CC;
	private static final int ITEM_COLOR = 0xCC6633;
	private static final BlockColor BLOCK_PROVIDER = (state, level, pos, tintIndex) -> BLOCK_COLOR;
	private static final ItemColor ITEM_PROVIDER = (stack, tintIndex) -> ITEM_COLOR;
	private static final KeyMapping KEY = new KeyMapping("key.fabricated_testmod_frapi.check", InputConstants.Type.KEYSYM, InputConstants.KEY_J,
			"key.categories.fabricated_testmod_frapi");
	// Render thread only.
	private static boolean hudDrawn;
	private static String keyFailure;

	@Override
	public void onInitializeClient() {
		if (KeyBindingHelper.registerKeyBinding(KEY) != KEY) keyFailure = "registering didn't return the key binding";
		try {
			KeyBindingHelper.registerKeyBinding(KEY);
			keyFailure = "registering it twice didn't throw";
		} catch (IllegalArgumentException expected) {
			// As upstream.
		}
		ColorProviderRegistry.BLOCK.register(BLOCK_PROVIDER, FrapiTestMod.tintedBlock);
		ColorProviderRegistry.ITEM.register(ITEM_PROVIDER, FrapiTestMod.tintedItem);
		HudRenderCallback.EVENT.register((graphics, delta) -> {
			if (!hudDrawn) {
				hudDrawn = true;
				FrapiTestMod.LOGGER.info("FABRICATED-TEST hud callback OK");
			}
		});
		ClientLifecycleEvents.CLIENT_STARTED.register(FrapiClientTestMod::checkColors);
		ClientLifecycleEvents.CLIENT_STARTED.register(FrapiClientTestMod::checkKeyBinding);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			InputConstants.Key saved = InputConstants.getKey("key.keyboard.k");
			InputConstants.Key bound = KeyBindingHelper.getBoundKeyOf(KEY);
			if (saved.equals(bound)) FrapiTestMod.LOGGER.info("FABRICATED-TEST key binding saved key OK");
			else FrapiTestMod.LOGGER.error("FABRICATED-TEST key binding saved key FAILED: bound to {}, not the saved {}", bound.getName(), saved.getName());
		});
	}

	private static void checkKeyBinding(Minecraft client) {
		List<String> failed = new ArrayList<>();
		if (keyFailure != null) failed.add(keyFailure);
		KeyMapping[] all = client.options.keyMappings;
		if (!Arrays.asList(all).contains(KEY)) failed.add("the game's options don't list it");
		try {
			Arrays.sort(all.clone());
			if (KEY.compareTo(client.options.keyUp) <= 0) failed.add("its category doesn't sort after the game's");
		} catch (RuntimeException e) {
			failed.add("the controls screen's sort threw " + e);
		}
		try {
			// Upstream checks for the options first, so this is refused as late, not as a duplicate.
			KeyBindingHelper.registerKeyBinding(KEY);
			failed.add("registering once the options exist didn't throw");
		} catch (IllegalStateException expected) {
			// As upstream.
		} catch (IllegalArgumentException e) {
			failed.add("registering once the options exist was refused as a duplicate, not as late");
		}
		if (failed.isEmpty()) FrapiTestMod.LOGGER.info("FABRICATED-TEST key binding OK");
		else FrapiTestMod.LOGGER.error("FABRICATED-TEST key binding FAILED: {}", String.join("; ", failed));
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
