package io.github.cmdralph.fabricated.testmod.broken;

import net.fabricmc.api.ModInitializer;

/** Fails on purpose: CI checks that one failing Fabric mod does not stop the server (policy "disable"). */
public final class ThrowingMod implements ModInitializer {
	@Override
	public void onInitialize() {
		throw new IllegalStateException("intentional failure for Fabricated CI");
	}
}
