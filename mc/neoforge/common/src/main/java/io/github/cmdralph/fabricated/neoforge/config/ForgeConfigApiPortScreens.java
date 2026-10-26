package io.github.cmdralph.fabricated.neoforge.config;

import java.util.function.BiFunction;

import com.mojang.datafixers.util.Function4;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Forge Config API Port's config screens, behind the config button of NeoForge's mod list. Client only. */
public final class ForgeConfigApiPortScreens {
	private ForgeConfigApiPortScreens() {
	}

	/*
	 * NeoForge's config screen for the port's constructors, which take the mod's id where NeoForge's take its container.
	 * Translation points a Fabric mod's constructor references (ConfigurationScreen::new, typically the factory it
	 * registers below) at these.
	 */

	public static ConfigurationScreen configurationScreen(String modId, Screen parent) {
		return new ConfigurationScreen(ForgeConfigApiPortBridge.modContainer(modId), parent);
	}

	public static ConfigurationScreen configurationScreen(String modId, Screen parent, ConfigurationScreen.ConfigurationSectionScreen.Filter filter) {
		return new ConfigurationScreen(ForgeConfigApiPortBridge.modContainer(modId), parent, filter);
	}

	public static ConfigurationScreen configurationScreen(String modId, Screen parent,
			Function4<ConfigurationScreen, ModConfig.Type, ModConfig, Component, Screen> sectionScreen) {
		return new ConfigurationScreen(ForgeConfigApiPortBridge.modContainer(modId), parent, sectionScreen);
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
