package io.github.cmdralph.fabricated.testmod;

import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;

/**
 * A language adapter declared only to run code before Mixin starts, as some mods' are: Fabric Loader creates every
 * declared adapter first. {@link TestMixinPlugin} checks that it ran before the test mod's mixin plugin loaded.
 */
public final class EarlyAdapter implements LanguageAdapter {
	static final String PROPERTY = "fabricated.test.earlyAdapter";

	public EarlyAdapter() {
		System.setProperty(PROPERTY, "created");
	}

	@Override
	public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
		throw new LanguageAdapterException("The test mod's early adapter creates no entrypoints");
	}
}
