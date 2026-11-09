package io.github.cmdralph.fabricated.forge.keymapping;

import net.fabricmc.fabric.impl.client.keybinding.KeyBindingRegistryImpl;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * {@code fabric-key-binding-api-v1} on Forge 47's and 52's client: Fabric mods' key bindings are registered with Forge's key
 * mapping event, where Forge mods register theirs (KEYMAP-3, see {@link KeyBindingRegistryImpl}). Client only: the
 * class names client classes.
 */
public final class KeyMappingBridge {
	private KeyMappingBridge() {
	}

	/** On the in-game mod's own bus, where Forge posts its key mapping event to each mod. */
	public static void register(IEventBus modBus) {
		modBus.addListener(EventPriority.NORMAL, false, RegisterKeyMappingsEvent.class, KeyBindingRegistryImpl::register);
	}
}
