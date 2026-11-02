package io.github.cmdralph.fabricated.forgetest;

/**
 * fabric-rendering-v1's HUD on Forge 64 to 66 (HUD-2) is checked by the client API test mod
 * (src/testmodfrapi: elements attached after chat and before the hotbar), which the client run with Fabric API loads;
 * 26.x's module has no {@code HudRenderCallback}.
 */
public final class ForgeHudTest {
	private ForgeHudTest() {
	}

	public static void register() {
	}
}
