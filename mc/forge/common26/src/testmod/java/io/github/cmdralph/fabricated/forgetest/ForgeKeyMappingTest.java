package io.github.cmdralph.fabricated.forgetest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Native fabric-key-mapping-api-v1 on Forge 64 to 66 (KEYMAP-3), with Fabric API's jar and without it: a key mapping
 * the test mod registers from its client entrypoint, as Fabric mods register theirs, must be in the game's options once
 * the game has started, its category sorted after the game's; registering it twice, or once Forge's key mapping event
 * has added the key mappings, must throw as upstream's does; and it must have the key CI's options file saved for it
 * (ci/forge/smoke-client.sh), which Forge reads again once mods have loaded. Client only: the class names client
 * classes.
 */
public final class ForgeKeyMappingTest {
	private static KeyMapping key;
	private static String failure;

	private ForgeKeyMappingTest() {
	}

	/** From the client entrypoint. */
	public static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ForgeTestMod.NAMESPACE, "checks"));
		KeyMapping mapping = new KeyMapping("key.fabricated_forge_testmod.check", InputConstants.KEY_J, category);
		if (KeyMappingHelper.registerKeyMapping(mapping) != mapping) failure = "registering didn't return the key mapping";
		try {
			KeyMappingHelper.registerKeyMapping(mapping);
			failure = "registering it twice didn't throw";
		} catch (IllegalArgumentException expected) {
			// As upstream.
		}
		key = mapping;
	}

	/** Once the integrated server ticks, the game started: the checks run on the render thread. */
	public static void check() {
		if (key != null) Minecraft.getInstance().execute(ForgeKeyMappingTest::checkNow);
	}

	private static void checkNow() {
		Minecraft client = Minecraft.getInstance();
		List<String> failed = new ArrayList<>();
		if (failure != null) failed.add(failure);
		KeyMapping[] all = client.options.keyMappings;
		if (!Arrays.asList(all).contains(key)) failed.add("the game's options don't list it");
		try {
			Arrays.sort(all.clone());
			if (key.compareTo(client.options.keyUp) <= 0) failed.add("its category doesn't sort after the game's");
		} catch (RuntimeException e) {
			failed.add("the controls screen's sort threw " + e);
		}
		try {
			// Upstream checks for the options first, so this is refused as late, not as a duplicate.
			KeyMappingHelper.registerKeyMapping(key);
			failed.add("registering once Forge's event added the key mappings didn't throw");
		} catch (IllegalStateException expected) {
			// As upstream.
		} catch (IllegalArgumentException e) {
			failed.add("registering once Forge's event added the key mappings was refused as a duplicate, not as late");
		}
		InputConstants.Key saved = InputConstants.getKey("key.keyboard.k");
		InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(key);
		if (!saved.equals(bound)) failed.add("bound to " + bound.getName() + ", not the key saved for it, " + saved.getName());
		if (failed.isEmpty()) ForgeTestMod.LOGGER.info("FABRICATED-TEST key mapping OK");
		else ForgeTestMod.LOGGER.error("FABRICATED-TEST key mapping FAILED: {}", String.join("; ", failed));
	}
}
