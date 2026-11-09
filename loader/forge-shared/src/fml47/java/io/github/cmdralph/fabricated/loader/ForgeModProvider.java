package io.github.cmdralph.fabricated.loader;

import java.util.Map;

import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModProvider;

/**
 * What Fabricated's locators make Forge's mod files with: {@link AbstractJarFileModProvider}, whose class scan of the
 * files they hand over finds the in-game mod's {@code @Mod} class.
 */
public abstract class ForgeModProvider extends AbstractJarFileModProvider {
	/** Forge 47 leaves this to each provider (Forge 61's base class has it): no arguments are read. */
	@Override
	public void initArguments(Map<String, ?> arguments) {
	}
}
