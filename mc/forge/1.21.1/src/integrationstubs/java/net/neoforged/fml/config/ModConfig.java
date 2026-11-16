package net.neoforged.fml.config;

/**
 * Compile-time stand-in for the copy of NeoForge's {@code ModConfig} that Forge Config API Port's Fabric jar for 1.21.1
 * holds, whose config types the Forge half of its registry also takes (as read in its 21.1.6 jar); never packaged: the
 * port's jar provides the class at run time, and nothing names it without the port.
 */
public class ModConfig {
	private ModConfig() {
	}

	public enum Type {
		COMMON,
		CLIENT,
		SERVER,
		STARTUP
	}
}
