package io.github.cmdralph.fabricated.forgetest;

import java.util.HashSet;
import java.util.Set;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Reports the test mod's HUD checks, each the first time it passes: its own hooks into vanilla's HUD method
 * ({@code mixin.HudHooksMixin}, every version) and, on Forge 61, Fabric API's HUD ({@link ForgeHudTest}). The client
 * smoke reports one never reported as missing (ci/forge/smoke-client.sh).
 */
@Environment(EnvType.CLIENT)
public final class ForgeHudChecks {
	/** Passed on the render thread only. */
	private static final Set<String> PASSED = new HashSet<>();

	private ForgeHudChecks() {
	}

	public static void passed(String check) {
		if (PASSED.add(check)) ForgeTestMod.LOGGER.info("FABRICATED-TEST {} OK", check);
	}
}
