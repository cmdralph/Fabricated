package fuzs.forgeconfigapiport.api.config.v2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.loading.FMLConfig;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Forge Config API Port's config directories (its version 8, for 1.20.1), provided by Fabricated: Forge's own. Forge has
 * no setting for global server configs, so a server's are in its world's {@code serverconfig} directory, where Forge
 * keeps them (its {@code ServerLifecycleHooks}).
 */
public interface ForgeConfigPaths {
	ForgeConfigPaths INSTANCE = new ForgeConfigPaths() {
		@Override
		public Path getClientConfigDirectory() {
			return FMLPaths.CONFIGDIR.get();
		}

		@Override
		public Path getCommonConfigDirectory() {
			return FMLPaths.CONFIGDIR.get();
		}

		@Override
		public Path getServerConfigDirectory(MinecraftServer server) {
			Path directory = server.getWorldPath(new LevelResource("serverconfig"));
			try {
				Files.createDirectories(directory);
			} catch (IOException e) {
				throw new UncheckedIOException("Problem creating the server config directory " + directory, e);
			}
			return directory;
		}

		/** Forge has no such setting: server configs are per world. */
		@Override
		public boolean forceGlobalServerConfigs() {
			return false;
		}

		@Override
		public Path getDefaultConfigsDirectory() {
			return FMLPaths.getOrCreateGameRelativePath(Path.of(FMLConfig.defaultConfigPath()));
		}
	};

	Path getClientConfigDirectory();

	Path getCommonConfigDirectory();

	Path getServerConfigDirectory(MinecraftServer server);

	boolean forceGlobalServerConfigs();

	Path getDefaultConfigsDirectory();
}
