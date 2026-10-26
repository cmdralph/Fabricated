package com.terraformersmc.modmenu.api;

import java.util.Map;
import java.util.function.Consumer;

/**
 * The entrypoint interface of Mod Menu's API (key {@code modmenu}), for the Fabric mods that offer a config screen
 * through it. Clean-room, with Mod Menu's names; Fabricated shows these screens through NeoForge's mod list.
 */
public interface ModMenuApi {
	/** This mod's config screen, or null when it has none. */
	default ConfigScreenFactory<?> getModConfigScreenFactory() {
		return null;
	}

	/** Config screens this mod provides for other mods, by mod id. */
	default Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
		return Map.of();
	}

	/** Mod Menu's modpack badges; not shown by NeoForge's mod list. */
	default void attachModpackBadges(Consumer<String> consumer) {
	}
}
