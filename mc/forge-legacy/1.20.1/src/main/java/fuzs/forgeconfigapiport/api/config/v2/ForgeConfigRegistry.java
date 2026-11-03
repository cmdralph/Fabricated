package fuzs.forgeconfigapiport.api.config.v2;

import io.github.cmdralph.fabricated.forge.config.ForgeConfigApiPortBridge;
import net.minecraftforge.fml.config.IConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Forge Config API Port's Fabric registry (its version 8, for 1.20.1), provided by Fabricated on Forge's own config
 * system, which the port copies. Configs are made with the Fabric mod's Forge container, under the file names the port
 * uses, and can be read right after registering, as with the port.
 */
public interface ForgeConfigRegistry {
	ForgeConfigRegistry INSTANCE = ForgeConfigApiPortBridge.REGISTRY;

	/** @throws IllegalArgumentException when no mod has the id, as with the port */
	ModConfig register(String modId, ModConfig.Type type, IConfigSpec<?> spec);

	/** @throws IllegalArgumentException when no mod has the id, as with the port */
	ModConfig register(String modId, ModConfig.Type type, IConfigSpec<?> spec, String fileName);
}
