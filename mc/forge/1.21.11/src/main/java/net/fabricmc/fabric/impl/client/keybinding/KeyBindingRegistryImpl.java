package net.fabricmc.fabric.impl.client.keybinding;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;

/**
 * Fabricated's key binding registry on Forge 61 ({@code fabric-key-binding-api-v1}, KEYMAP-3). Upstream keeps Fabric
 * mods' key bindings until the game's options load and refuses one once the options exist, which on Fabric is after the
 * client entrypoints. Forge creates the options before it loads mods, and the client entrypoints run in its mod
 * loading, so upstream refused every Fabric mod's key binding and failed its client entrypoint.
 *
 * <p>Here the key bindings are kept until Forge's {@link RegisterKeyMappingsEvent}, where Forge mods register theirs,
 * and registered there; Forge loads the keys from the options file again once mods have loaded
 * ({@code ClientModLoader.finishModLoading}), which gives each the key the player saved. As upstream, a key binding is
 * refused twice and once they have been registered, and its category takes upstream's place in the controls screen's
 * order ({@code KeyMappingCategoryMixin}, upstream's). Upstream's {@code process}, which its mixin calls as the options
 * load, isn't needed: Forge's event adds the key bindings after the game's.
 */
public final class KeyBindingRegistryImpl {
	/** In the order they were registered; told apart by identity, as upstream's list. */
	private static final List<KeyMapping> MODDED_KEY_BINDINGS = new ArrayList<>();
	private static boolean registered;

	private KeyBindingRegistryImpl() {
	}

	public static synchronized KeyMapping registerKeyBinding(KeyMapping binding) {
		if (registered) throw new IllegalStateException("GameOptions has already been initialised");
		for (KeyMapping existing : MODDED_KEY_BINDINGS) {
			if (existing == binding) {
				throw new IllegalArgumentException("Attempted to register a key binding twice: " + binding.getName());
			} else if (existing.getName().equals(binding.getName())) {
				throw new IllegalArgumentException("Attempted to register two key bindings with equal ID: " + binding.getName() + "!");
			}
		}
		MODDED_KEY_BINDINGS.add(binding);
		return binding;
	}

	/** Forge's key mapping registration (once, on the client): the key bindings registered until now. */
	public static synchronized void register(RegisterKeyMappingsEvent event) {
		registered = true;
		MODDED_KEY_BINDINGS.forEach(event::register);
	}
}
