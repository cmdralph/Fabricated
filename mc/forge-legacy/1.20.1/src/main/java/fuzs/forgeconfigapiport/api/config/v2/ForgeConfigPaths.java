package fuzs.forgeconfigapiport.api.config.v2;

import java.nio.file.Path;

import io.github.cmdralph.fabricated.forge.config.ForgeConfigApiPortBridge;
import net.minecraft.server.MinecraftServer;

/**
 * Forge Config API Port's config directories (its version 8, for 1.20.1), provided by Fabricated: Forge's own. Forge has
 * no setting for global server configs, so a server's are in its world's {@code serverconfig} directory.
 */
public interface ForgeConfigPaths {
	ForgeConfigPaths INSTANCE = ForgeConfigApiPortBridge.PATHS;

	Path getClientConfigDirectory();

	Path getCommonConfigDirectory();

	Path getServerConfigDirectory(MinecraftServer server);

	boolean forceGlobalServerConfigs();

	Path getDefaultConfigsDirectory();
}
