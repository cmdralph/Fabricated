package mezz.jei.api;

import net.minecraft.resources.Identifier;

/** Compile-time stand-in for JEI's plugin interface (the real one is loaded at runtime); never packaged. */
public interface IModPlugin {
	Identifier getPluginUid();
}
