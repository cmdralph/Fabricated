package io.github.cmdralph.fabricated.forgetest;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientBundleTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.tooltip.BundleTooltip;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * The forge-legacy test mod's part for fabric-rendering-v1's {@code TooltipComponentCallback} (with Fabric API's jar
 * only): the client component the game makes of a Fabric mod's tooltip data ({@code ClientTooltipComponent.create},
 * through which Forge 43 and 47 make every tooltip's components) must be the one the mod's listener makes of it, and a
 * bundle's must stay vanilla's. On Forge 47 upstream's hook into {@code create} asks the callback (Fabric API 0.92); on
 * Forge 43 Fabricated's in-game mod asks it where Forge looks its mods' factories up, as Fabric API 0.77's hook is in
 * code Forge replaced.
 */
@Environment(EnvType.CLIENT)
public final class ForgeTooltipClient {
	private ForgeTooltipClient() {
	}

	/** A Fabric mod's tooltip data, which neither vanilla nor a Forge mod makes a component of. */
	private record TestTooltip() implements TooltipComponent {
	}

	/** The component the listener makes of it. */
	private static final class TestComponent implements ClientTooltipComponent {
		@Override
		public int getHeight() {
			return 0;
		}

		@Override
		public int getWidth(Font font) {
			return 0;
		}
	}

	/** From the client entrypoint; only with Fabric API's rendering module. */
	static void register() {
		TooltipComponentCallback.EVENT.register(data -> data instanceof TestTooltip ? new TestComponent() : null);
	}

	/** On a client, once Forge has its mods' tooltip factories. */
	static void check() {
		ClientTooltipComponent component = ClientTooltipComponent.create(new TestTooltip());
		ClientTooltipComponent bundle = ClientTooltipComponent.create(new BundleTooltip(NonNullList.create(), 0));
		if (component instanceof TestComponent && bundle instanceof ClientBundleTooltip) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST tooltip component callback OK");
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST tooltip component callback FAILED: the test data's component is {}, a bundle's {}",
					component.getClass().getName(), bundle.getClass().getName());
		}
	}
}
