package io.github.cmdralph.fabricated.forgetest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.class_304;
import net.minecraft.class_310;
import net.minecraft.class_315;
import net.minecraft.class_3675;

/**
 * Native fabric-key-binding-api-v1 on Forge 47 (KEYMAP-3), with Fabric API's jar and without it: a key binding the test
 * mod registers from its client entrypoint, as Fabric mods register theirs, must be in the game's options once the game
 * has started, its category sorted after the game's; registering it twice, or once Forge's key mapping event has added
 * the key bindings, must throw as upstream's does; and it must have the key CI's options file saved for it
 * (ci/forge-legacy/smoke-client.sh), which Forge reads again once mods have loaded. Where another mod provides the module
 * (Forgified Fabric API), a late registration is that mod's to refuse ({@link ForgeTestMod#report}). In Intermediary's
 * names: {@code class_304} is {@code KeyMapping}, {@code class_315} {@code Options}, {@code class_3675} {@code
 * InputConstants}.
 */
@Environment(EnvType.CLIENT)
public final class ForgeKeyBindingClient {
	/** {@code InputConstants.KEY_J}. */
	private static final int KEY_J = 74;
	private static final String LATE_AS_DUPLICATE = "registering once Forge's event added the key bindings was refused as a duplicate, not as late";
	private static class_304 key;
	private static String failure;

	private ForgeKeyBindingClient() {
	}

	/** From the client entrypoint. */
	static void register() {
		class_304 mapping = new class_304("key." + ForgeTestMod.MOD_ID + ".check", KEY_J, "key.categories." + ForgeTestMod.MOD_ID);
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
	static void check() {
		if (key != null) class_310.method_1551().execute(ForgeKeyBindingClient::checkNow);
	}

	private static void checkNow() {
		class_315 options = class_310.method_1551().field_1690;
		List<String> failed = new ArrayList<>();
		if (failure != null) failed.add(failure);
		class_304[] all = options.field_1839;
		if (!Arrays.asList(all).contains(key)) failed.add("the game's options don't list it");
		try {
			Arrays.sort(all.clone());
			if (key.compareTo(options.field_1894) <= 0) failed.add("its category doesn't sort after the game's");
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
		class_3675.class_306 saved = class_3675.method_15981("key.keyboard.k");
		class_3675.class_306 bound = KeyBindingHelper.getBoundKeyOf(key);
		if (!saved.equals(bound)) failed.add("bound to " + bound.method_1441() + ", not the key saved for it, " + saved.method_1441());
		ForgeTestMod.report("key binding", failed, "fabric-key-binding-api-v1", Map.of(LATE_AS_DUPLICATE,
				"its registerKeyBinding leaves out upstream's refusal once the game's options exist, which on Forge is before mods load"));
	}
}
