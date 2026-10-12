package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.fabricmc.fabric.api.item.v1.FabricTooltipFlag;
import net.minecraft.world.item.TooltipFlag;

/** Makes every tooltip flag Fabric's {@link FabricTooltipFlag} (whose one method has a default), as upstream does. */
@Mixin(TooltipFlag.class)
interface TooltipFlagMixin extends FabricTooltipFlag {
}
