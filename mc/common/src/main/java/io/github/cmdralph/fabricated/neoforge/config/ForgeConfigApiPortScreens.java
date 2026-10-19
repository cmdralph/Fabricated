package io.github.cmdralph.fabricated.neoforge.config;

import java.util.function.BiFunction;

import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Forge Config API Port's config screens, behind the config button of NeoForge's mod list. Client only. */
public final class ForgeConfigApiPortScreens {
	private ForgeConfigApiPortScreens() {
	}

	/** The port's config screen for a mod, unless the mod registered one with NeoForge itself. */
	public static void register(String modId, BiFunction<String, Screen, Screen> factory) {
		ForgeConfigApiPortBridge.container(modId).ifPresent(container -> {
			if (container.getCustomExtension(IConfigScreenFactory.class).isEmpty()) {
				container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> factory.apply(modId, parent));
			}
		});
	}
}
