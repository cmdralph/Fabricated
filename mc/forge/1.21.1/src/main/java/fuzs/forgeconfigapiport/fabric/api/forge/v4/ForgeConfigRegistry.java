package fuzs.forgeconfigapiport.fabric.api.forge.v4;

import io.github.cmdralph.fabricated.forge.config.ForgeConfigApiPortBridge;
import net.minecraftforge.fml.config.IConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Forge Config API Port's Fabric registry for Forge's config system (its version 21, for 1.21.1), provided by Fabricated
 * on Forge 52's own, which the port copies ({@link ForgeConfigApiPortBridge}, CFG-4). The port's jar loads as on Fabric,
 * its NeoForge half included; its copy of this package is left out for this one. Configs are made with the Fabric mod's
 * Forge container, under the file names the port uses, and can be read right after registering, as with the port. A
 * config registered with one of NeoForge's types, which the port's NeoForge half defines, is made with Forge's type of
 * that name; NeoForge's startup configs, which the port loads as they are registered, with Forge's common type, which
 * is read then too.
 */
public interface ForgeConfigRegistry {
	ForgeConfigRegistry INSTANCE = new ForgeConfigRegistry() {
		@Override
		public ModConfig register(String modId, ModConfig.Type type, IConfigSpec<?> spec) {
			return ForgeConfigApiPortBridge.register(modId, type, spec);
		}

		@Override
		public void register(String modId, net.neoforged.fml.config.ModConfig.Type type, IConfigSpec<?> spec) {
			ForgeConfigApiPortBridge.register(modId, forgeType(type), spec);
		}

		@Override
		public ModConfig register(String modId, ModConfig.Type type, IConfigSpec<?> spec, String fileName) {
			return ForgeConfigApiPortBridge.register(modId, type, spec, fileName);
		}

		@Override
		public void register(String modId, net.neoforged.fml.config.ModConfig.Type type, IConfigSpec<?> spec, String fileName) {
			ForgeConfigApiPortBridge.register(modId, forgeType(type), spec, fileName);
		}

		private static ModConfig.Type forgeType(net.neoforged.fml.config.ModConfig.Type type) {
			return type == net.neoforged.fml.config.ModConfig.Type.STARTUP ? ModConfig.Type.COMMON : ModConfig.Type.valueOf(type.name());
		}
	};

	/** @throws IllegalArgumentException when no mod has the id, as with the port */
	ModConfig register(String modId, ModConfig.Type type, IConfigSpec<?> spec);

	/** @throws IllegalArgumentException when no mod has the id, as with the port */
	void register(String modId, net.neoforged.fml.config.ModConfig.Type type, IConfigSpec<?> spec);

	/** @throws IllegalArgumentException when no mod has the id, as with the port */
	ModConfig register(String modId, ModConfig.Type type, IConfigSpec<?> spec, String fileName);

	/** @throws IllegalArgumentException when no mod has the id, as with the port */
	void register(String modId, net.neoforged.fml.config.ModConfig.Type type, IConfigSpec<?> spec, String fileName);
}
