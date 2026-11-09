package io.github.cmdralph.fabricated.testmod.plugin;

import me.shedaniel.rei.api.common.plugins.REIServerPlugin;

/**
 * {@code rei_common} entrypoint (see {@link OtherModPlugins}): REI 12 and 16, for 1.20.1 and 1.21.1, name their common
 * plugins this way.
 */
public final class ReiPlugin implements REIServerPlugin {
	public ReiPlugin() {
		OtherModPlugins.log("rei", "constructed");
	}

	/** Overrides REI's default, by name and descriptor. */
	public double getPriority() {
		OtherModPlugins.log("rei", "called");
		return 0;
	}
}
