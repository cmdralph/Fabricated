package io.github.cmdralph.fabricated.forge.rendering;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * Fabric API's {@code TooltipComponentCallback} (fabric-rendering-v1, passed through) where Forge 43 makes the client
 * component of a tooltip's data. Fabric API 0.77 asks it from a hook into the lambda of vanilla's
 * {@code Screen.renderTooltip} that adds an item's tooltip data, which Forge 43 replaced with its own gathering
 * ({@code ForgeHooksClient.gatherTooltipComponents}): the hook found nothing, and a Fabric mod's tooltip data reached
 * {@code ClientTooltipComponent.create}, which throws for data that neither vanilla nor a Forge mod's factory makes a
 * component of. Fabricated's Fabric API plan leaves upstream's hook out, and its mixin asks the callback first where
 * Forge looks its mods' factories up ({@code ClientTooltipComponentManager}), which every tooltip Forge draws goes
 * through. A bundle's data is still vanilla's, which {@code create} makes before it asks Forge.
 */
public final class FabricTooltipComponents {
	/** Fabric API's rendering module is installed. Its classes are only touched then. */
	private static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("fabric-rendering-v1");

	private FabricTooltipComponents() {
	}

	/** The component a Fabric mod's listener makes of the data, or null for none. */
	@Nullable
	public static ClientTooltipComponent create(TooltipComponent data) {
		return ENABLED ? Upstream.create(data) : null;
	}

	/** Everything that names Fabric's rendering module: loaded only once {@link #ENABLED} is known to be true. */
	private static final class Upstream {
		private Upstream() {
		}

		static ClientTooltipComponent create(TooltipComponent data) {
			return TooltipComponentCallback.EVENT.invoker().getComponent(data);
		}
	}
}
