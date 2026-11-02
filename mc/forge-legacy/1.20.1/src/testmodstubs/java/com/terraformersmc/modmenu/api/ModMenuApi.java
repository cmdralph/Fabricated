package com.terraformersmc.modmenu.api;

import java.util.Map;

/** {@code ModMenuApi}: the methods the test mod's entrypoint and Fabricated call. */
public interface ModMenuApi {
	default ConfigScreenFactory<?> getModConfigScreenFactory() {
		return null;
	}

	default Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
		return Map.of();
	}
}
