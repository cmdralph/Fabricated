package fuzs.forgeconfigapiport.fabric.api.v5;

import io.github.cmdralph.fabricated.neoforge.config.ForgeConfigApiPortBridge;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;

/**
 * Forge Config API Port's Fabric registry, provided by Fabricated on NeoForge's own config system (which the port
 * copies). Configs are registered with the Fabric mod's NeoForge container, under the file names the port uses.
 */
public interface ConfigRegistry {
	ConfigRegistry INSTANCE = ForgeConfigApiPortBridge.REGISTRY;

	void register(String modId, ModConfig.Type type, IConfigSpec spec);

	void register(String modId, ModConfig.Type type, IConfigSpec spec, String fileName);
}
