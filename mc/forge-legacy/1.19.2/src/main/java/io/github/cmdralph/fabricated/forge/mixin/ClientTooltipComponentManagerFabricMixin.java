package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.forge.rendering.FabricTooltipComponents;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraftforge.client.gui.ClientTooltipComponentManager;

/**
 * Asks Fabric API's {@code TooltipComponentCallback} for the client component of a tooltip's data before Forge's own
 * factories ({@link FabricTooltipComponents}). Forge's method, named as Forge names it.
 */
@Mixin(ClientTooltipComponentManager.class)
abstract class ClientTooltipComponentManagerFabricMixin {
	@Inject(method = "createClientTooltipComponent", at = @At("HEAD"), cancellable = true, remap = false)
	private static void fabricated$fabricComponent(TooltipComponent data, CallbackInfoReturnable<ClientTooltipComponent> cir) {
		ClientTooltipComponent component = FabricTooltipComponents.create(data);
		if (component != null) cir.setReturnValue(component);
	}
}
