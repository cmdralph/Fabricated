package io.github.cmdralph.fabricated.forge.keymapping;

import net.fabricmc.fabric.impl.client.keybinding.KeyBindingRegistryImpl;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;

/**
 * {@code fabric-key-binding-api-v1} on Forge 61's client: Fabric mods' key bindings are registered with Forge's key
 * mapping event, where Forge mods register theirs (KEYMAP-3, see {@link KeyBindingRegistryImpl}). Client only: the
 * class names client classes.
 */
public final class KeyMappingBridge {
	/** The Fabric API module this bridge serves on this version. */
	public static final String MODULE = "fabric-key-binding-api-v1";

	private KeyMappingBridge() {
	}

	public static void register() {
		RegisterKeyMappingsEvent.BUS.addListener(KeyBindingRegistryImpl::register);
	}
}
