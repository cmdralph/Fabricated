package io.github.cmdralph.fabricated.testmod;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvironmentInterface;
import net.minecraft.client.gui.components.events.GuiEventListener;

/**
 * CI check for environment stripping: implements a client-only interface, which Fabric Loader removes on a dedicated
 * server. Without the stripping, loading this class there fails.
 */
@EnvironmentInterface(value = EnvType.CLIENT, itf = GuiEventListener.class)
public abstract class SidedThing implements GuiEventListener {
	@Environment(EnvType.CLIENT)
	public abstract void clientOnly();
}
