package io.github.cmdralph.fabricated.forgetest;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.class_2246;

/**
 * The forge-legacy test mod's part for fabric-rendering-v1's colour provider lookup on Forge 47, which Fabricated's
 * in-game mod answers from Forge's map of block colours, upstream's reading vanilla's, which Forge replaced (COLOR-1;
 * with Fabric API's jar only): grass ({@code class_2246.field_10219}) must have vanilla's provider, and stone
 * ({@code field_10340}) none.
 */
@Environment(EnvType.CLIENT)
public final class ForgeColorTest {
	private ForgeColorTest() {
	}

	/** On a client, once the game's block colours exist. */
	public static void check() {
		boolean grass = ColorProviderRegistry.BLOCK.get(class_2246.field_10219) != null;
		boolean stone = ColorProviderRegistry.BLOCK.get(class_2246.field_10340) != null;
		if (grass && !stone) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST colour providers OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST colour providers: grass has one {}, stone {}", grass, stone);
		}
	}
}
