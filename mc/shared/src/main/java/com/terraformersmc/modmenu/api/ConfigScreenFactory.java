package com.terraformersmc.modmenu.api;

import net.minecraft.client.gui.screens.Screen;

/** Creates a mod's config screen; see {@link ModMenuApi}. */
@FunctionalInterface
public interface ConfigScreenFactory<S extends Screen> {
	S create(Screen parent);
}
