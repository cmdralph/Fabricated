package net.fabricmc.fabric.impl.client.keybinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * Fabricated's key binding registry for NeoForge 21.1 ({@code fabric-key-binding-api-v1}, KEYMAP-2). Upstream keeps
 * Fabric mods' key bindings until the game's options load and refuses one once the options exist, which on Fabric is
 * after the client entrypoints. NeoForge 21.1 creates the options before it loads mods, so upstream refused every Fabric
 * mod's key binding and failed its client entrypoint (JEI, Jade, Simple Voice Chat, Entity Culling and five more in the
 * 1.21.1 corpus).
 *
 * <p>Here the key bindings are kept until NeoForge's {@link RegisterKeyMappingsEvent}, where NeoForge mods register
 * theirs, and registered there; NeoForge then loads the options again, which gives each the key the player saved. As
 * upstream, a key binding is refused once they have been registered, and its category joins the order the controls
 * screen sorts by, after those already there. Upstream's {@code process}, which its mixin calls as the options load,
 * isn't needed: NeoForge's event adds the key bindings after the game's.
 */
public final class KeyBindingRegistryImpl {
	/** In the order they were registered; told apart by identity, as upstream's list. */
	private static final List<KeyMapping> MODDED_KEY_BINDINGS = new ArrayList<>();
	private static boolean registered;

	private KeyBindingRegistryImpl() {
	}

	/** Adds a category after the others in the controls screen's order; false when it has a place already. */
	public static synchronized boolean addCategory(String categoryTranslationKey) {
		Map<String, Integer> order = KeyMapping.CATEGORY_SORT_ORDER;
		if (order.containsKey(categoryTranslationKey)) return false;
		int last = order.values().stream().max(Integer::compareTo).orElse(0);
		order.put(categoryTranslationKey, last + 1);
		return true;
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
		addCategory(binding.getCategory());
		MODDED_KEY_BINDINGS.add(binding);
		return binding;
	}

	/** NeoForge's key mapping registration (the mod bus, once): the key bindings registered until now. */
	public static synchronized void register(RegisterKeyMappingsEvent event) {
		registered = true;
		MODDED_KEY_BINDINGS.forEach(event::register);
	}
}
