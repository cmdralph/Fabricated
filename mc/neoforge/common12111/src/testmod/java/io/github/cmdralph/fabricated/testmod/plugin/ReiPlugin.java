package io.github.cmdralph.fabricated.testmod.plugin;

import me.shedaniel.rei.api.common.plugins.REICommonPlugin;

/** {@code rei_common} entrypoint (see {@link OtherModPlugins}). */
public final class ReiPlugin implements REICommonPlugin {
	public ReiPlugin() {
		OtherModPlugins.log("rei", "constructed");
	}

	/** Overrides REI's default, by name and descriptor. */
	public double getPriority() {
		OtherModPlugins.log("rei", "called");
		return 0;
	}
}
