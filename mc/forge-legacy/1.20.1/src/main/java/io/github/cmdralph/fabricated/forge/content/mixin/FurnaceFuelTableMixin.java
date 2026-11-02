package io.github.cmdralph.fabricated.forge.content.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.forge.content.FabricFuels;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/**
 * Fabric's fuels in vanilla's fuel table, which Forge 47 reads its burn times from as the data packs' tags load
 * (FUEL-1, see {@link FabricFuels}): upstream's own hook at the same point, left out with the rest of its furnace mixin.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
abstract class FurnaceFuelTableMixin {
	@Inject(method = "getFuel", at = @At("RETURN"))
	private static void fabricated$addFabricFuels(CallbackInfoReturnable<Map<Item, Integer>> cir) {
		FabricFuels.addTo(cir.getReturnValue());
	}
}
