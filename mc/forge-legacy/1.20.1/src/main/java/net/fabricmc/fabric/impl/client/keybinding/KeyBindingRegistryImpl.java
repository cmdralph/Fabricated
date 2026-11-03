/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.fabricmc.fabric.impl.client.keybinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;

import net.fabricmc.fabric.mixin.client.keybinding.KeyBindingAccessor;

/**
 * Fabricated's key binding registry on Forge 47 ({@code fabric-key-binding-api-v1}, KEYMAP-3, as on Forge 61). Upstream
 * keeps Fabric mods' key bindings until the game's options load and refuses one once the options exist, which on Fabric
 * is after the client entrypoints. Forge 47 creates the options in {@code Minecraft}'s constructor before it loads mods,
 * and the client entrypoints run in its mod loading, so upstream refused every Fabric mod's key binding and failed its
 * client entrypoint (the Forge 47 corpus client: Xaero's World Map, which then stopped the game as it tried again).
 *
 * <p>Here the key bindings are kept until Forge's {@link RegisterKeyMappingsEvent}, where Forge mods register theirs,
 * which Forge posts after the mods' registration events ({@code ForgeHooksClient.initClientHooks}), and registered
 * there; Forge loads the keys from the options file again once mods have loaded ({@code ClientModLoader.finishModLoading}),
 * which gives each the key the player saved. As upstream, a key binding is refused twice and once they have been
 * registered, and its category, new to the game, is sorted after the game's ({@code addCategory}, upstream's). Upstream's
 * {@code process}, which its options mixin calls as the options load, isn't needed: Forge's event adds the key bindings
 * after the game's.
 */
public final class KeyBindingRegistryImpl {
	/** In the order they were registered; told apart by identity, as upstream's list. */
	private static final List<KeyMapping> MODDED_KEY_BINDINGS = new ArrayList<>();
	private static boolean registered;

	private KeyBindingRegistryImpl() {
	}

	private static Map<String, Integer> getCategoryMap() {
		return KeyBindingAccessor.fabric_getCategoryMap();
	}

	public static boolean addCategory(String categoryTranslationKey) {
		Map<String, Integer> map = getCategoryMap();

		if (map.containsKey(categoryTranslationKey)) {
			return false;
		}

		Optional<Integer> largest = map.values().stream().max(Integer::compareTo);
		int largestInt = largest.orElse(0);
		map.put(categoryTranslationKey, largestInt + 1);
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
		// This will do nothing if the category already exists.
		addCategory(binding.getCategory());
		MODDED_KEY_BINDINGS.add(binding);
		return binding;
	}

	/** Forge's key mapping registration (once, on the client): the key bindings registered until now. */
	public static synchronized void register(RegisterKeyMappingsEvent event) {
		registered = true;
		MODDED_KEY_BINDINGS.forEach(event::register);
	}
}
