package net.fabricmc.fabric.api.client.keybinding.v1;

import java.util.Objects;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.impl.client.keybinding.KeyBindingRegistryImpl;
import net.minecraft.client.KeyMapping;

/**
 * Fabricated's {@code fabric-key-binding-api-v1} on NeoForge 21.1: upstream's API, Fabric mods' key bindings registered
 * as NeoForge mods register theirs ({@link KeyBindingRegistryImpl}).
 */
public final class KeyBindingHelper {
	private KeyBindingHelper() {
	}

	/**
	 * Registers a key binding, which then appears in the controls screen and keeps the key the player gives it; as
	 * upstream, only while mods load (from a client entrypoint), and once.
	 *
	 * @return the key binding
	 * @throws IllegalStateException    once mods have loaded
	 * @throws IllegalArgumentException for a key binding registered already, or another with its name
	 */
	public static KeyMapping registerKeyBinding(KeyMapping keyBinding) {
		Objects.requireNonNull(keyBinding, "key binding");
		return KeyBindingRegistryImpl.registerKeyBinding(keyBinding);
	}

	/** The key a key binding is bound to now: its default until the player rebinds it. */
	public static InputConstants.Key getBoundKeyOf(KeyMapping keyBinding) {
		return keyBinding.getKey();
	}
}
