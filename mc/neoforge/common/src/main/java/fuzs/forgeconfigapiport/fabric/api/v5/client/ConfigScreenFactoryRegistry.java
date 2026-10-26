package fuzs.forgeconfigapiport.fabric.api.v5.client;

import java.util.function.BiFunction;

import io.github.cmdralph.fabricated.neoforge.config.ForgeConfigApiPortScreens;
import net.minecraft.client.gui.screens.Screen;

/** Forge Config API Port's config screens: behind the config button of NeoForge's mod list. Client only. */
public interface ConfigScreenFactoryRegistry {
	ConfigScreenFactoryRegistry INSTANCE = ForgeConfigApiPortScreens::register;

	void register(String modId, BiFunction<String, Screen, Screen> factory);
}
