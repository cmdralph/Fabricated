package net.fabricmc.fabric.impl.client.keymapping;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;

/**
 * Fabricated's key mapping registry on Forge 64 to 66 ({@code fabric-key-mapping-api-v1}, KEYMAP-3). Upstream keeps
 * Fabric mods' key mappings until the game's options load and refuses one once the options exist, which on Fabric is
 * after the client entrypoints. Forge creates the options before it loads mods, and the client entrypoints run in its
 * mod loading, so upstream refused every Fabric mod's key mapping and failed its client entrypoint.
 *
 * <p>Here the key mappings are kept until Forge's {@link RegisterKeyMappingsEvent}, where Forge mods register theirs,
 * and registered there; Forge loads the keys from the options file again once mods have loaded
 * ({@code ClientModLoader.finishModLoading}), which gives each the key the player saved. As upstream, a key mapping is
 * refused twice and once they have been registered, and its category takes upstream's place in the controls screen's
 * order ({@code KeyMappingCategoryMixin}, upstream's). Upstream's {@code process}, which its mixin calls as the options
 * load, isn't needed: Forge's event adds the key mappings after the game's.
 */
public final class KeyMappingRegistryImpl {
	/** In the order they were registered; told apart by identity, as upstream's list. */
	private static final List<KeyMapping> MODDED_KEY_MAPPINGS = new ArrayList<>();
	private static boolean registered;

	private KeyMappingRegistryImpl() {
	}

	public static synchronized KeyMapping registerKeyMapping(KeyMapping mapping) {
		if (registered) throw new IllegalStateException("GameOptions has already been initialised");
		for (KeyMapping existing : MODDED_KEY_MAPPINGS) {
			if (existing == mapping) {
				throw new IllegalArgumentException("Attempted to register a key mapping twice: " + mapping.getName());
			} else if (existing.getName().equals(mapping.getName())) {
				throw new IllegalArgumentException("Attempted to register two key mappings with equal ID: " + mapping.getName() + "!");
			}
		}
		MODDED_KEY_MAPPINGS.add(mapping);
		return mapping;
	}

	/** Forge's key mapping registration (once, on the client): the key mappings registered until now. */
	public static synchronized void register(RegisterKeyMappingsEvent event) {
		registered = true;
		MODDED_KEY_MAPPINGS.forEach(event::register);
	}
}
