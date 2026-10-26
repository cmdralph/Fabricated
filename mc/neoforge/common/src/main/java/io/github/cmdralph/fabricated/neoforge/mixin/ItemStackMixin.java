package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.fabricmc.fabric.api.item.v1.FabricItemStack;
import net.minecraft.world.item.ItemStack;

/** Makes every item stack Fabric's {@link FabricItemStack}, whose methods all have defaults. No Minecraft code is changed. */
@Mixin(ItemStack.class)
abstract class ItemStackMixin implements FabricItemStack {
}
