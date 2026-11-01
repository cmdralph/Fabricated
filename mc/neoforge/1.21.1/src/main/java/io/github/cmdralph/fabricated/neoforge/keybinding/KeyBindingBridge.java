package io.github.cmdralph.fabricated.neoforge.keybinding;

import net.fabricmc.fabric.impl.client.keybinding.KeyBindingRegistryImpl;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * {@code fabric-key-binding-api-v1} on NeoForge 21.1's client: Fabric mods' key bindings are registered with NeoForge's
 * key mapping event, where NeoForge mods register theirs (KEYMAP-2). Client only: the class names client classes.
 */
public final class KeyBindingBridge {
	private KeyBindingBridge() {
	}

	public static void register(IEventBus modBus) {
		modBus.addListener(RegisterKeyMappingsEvent.class, KeyBindingRegistryImpl::register);
	}
}
