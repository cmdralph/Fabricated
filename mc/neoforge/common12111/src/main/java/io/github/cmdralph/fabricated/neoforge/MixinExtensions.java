package io.github.cmdralph.fabricated.neoforge;

/**
 * Fabricated's Mixin extensions: none from NeoForge 21.11 on, where a class processor of Fabricated's runs after Mixin on
 * every class and declares default bridges there (the loader's {@code DefaultBridgeProcessor}). NeoForge 21.1 has one
 * (its {@code MixinExtensions}).
 */
final class MixinExtensions {
	private MixinExtensions() {
	}

	/** Nothing to register. */
	static void install() {
	}
}
