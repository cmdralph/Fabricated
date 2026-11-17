package net.minecraftforge.api;

/**
 * Forge Config API Port's config directory names (its version 4, for 1.19.2), provided by Fabricated: those Forge 43
 * keeps its configs in, as the port's copy of Forge's config system does (a world's {@code serverconfig}, the game
 * directory's {@code defaultconfigs}).
 */
public final class ConfigPaths {
	public static final String SERVER_CONFIG_PATH = "serverconfig";
	public static final String DEFAULT_CONFIGS_PATH = "defaultconfigs";

	private ConfigPaths() {
	}
}
