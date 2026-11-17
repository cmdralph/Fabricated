package io.github.cmdralph.fabricated.forgetest;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

/**
 * Native fabric-key-binding-api-v1 on Forge 47 (KEYMAP-3), with Fabric API's jar and without it: a key binding the test
 * mod registers from its client entrypoint, as Fabric mods register theirs, must be in the game's options once the game
 * has started, its category sorted after the game's; registering it twice, or once Forge's key mapping event has added
 * the key bindings, must throw as upstream's does; and it must have the key CI's options file saved for it
 * (ci/smoke-client.sh), which Forge reads again once mods have loaded. Where another mod provides the module
 * (Forgified Fabric API), a late registration is that mod's to refuse ({@link ForgeTestMod#report}).
 */
@Environment(EnvType.CLIENT)
public final class ForgeKeyBindingClient {
	/** {@code InputConstants.KEY_J}. */
	private static final int KEY_J = 74;
	private static final String LATE_AS_DUPLICATE = "registering once Forge's event added the key bindings was refused as a duplicate, not as late";
	private static KeyMapping key;
	private static String failure;

	private ForgeKeyBindingClient() {
	}

	/** From the client entrypoint. */
	static void register() {
		KeyMapping mapping = new KeyMapping("key." + ForgeTestMod.NAMESPACE + ".check", KEY_J, "key.categories." + ForgeTestMod.NAMESPACE);
		if (KeyBindingHelper.registerKeyBinding(mapping) != mapping) failure = "registering didn't return the key binding";
		try {
			KeyBindingHelper.registerKeyBinding(mapping);
			failure = "registering it twice didn't throw";
		} catch (IllegalArgumentException expected) {
			// As upstream.
		}
		key = mapping;
	}

	/** After the first tick of the client's integrated server, when the game has started: the checks run on the render thread. */
	public static void check() {
		if (key != null) Minecraft.getInstance().execute(ForgeKeyBindingClient::checkNow);
	}

	private static void checkNow() {
		Options options = Minecraft.getInstance().options;
		List<String> failed = new ArrayList<>();
		if (failure != null) failed.add(failure);
		KeyMapping[] all = options.keyMappings;
		if (!Arrays.asList(all).contains(key)) failed.add("the game's options don't list it");
		try {
			Arrays.sort(all.clone());
			if (key.compareTo(options.keyUp) <= 0) failed.add("its category doesn't sort after the game's");
		} catch (RuntimeException e) {
			failed.add("the controls screen's sort threw " + e);
		}
		try {
			// Upstream checks for the options first, so this is refused as late, not as a duplicate.
			KeyBindingHelper.registerKeyBinding(key);
			failed.add("registering once Forge's event added the key bindings didn't throw");
		} catch (IllegalStateException expected) {
			// As upstream.
		} catch (IllegalArgumentException e) {
			failed.add(LATE_AS_DUPLICATE);
		}
		InputConstants.Key saved = InputConstants.getKey("key.keyboard.k");
		InputConstants.Key bound = KeyBindingHelper.getBoundKeyOf(key);
		if (!saved.equals(bound)) failed.add("bound to " + bound.getName() + ", not the key saved for it, " + saved.getName());
		ForgeTestMod.report("key binding", failed, "fabric-key-binding-api-v1", Map.of(LATE_AS_DUPLICATE,
				"its registerKeyBinding leaves out upstream's refusal once the game's options exist, which on Forge is before mods load"));
	}
}
