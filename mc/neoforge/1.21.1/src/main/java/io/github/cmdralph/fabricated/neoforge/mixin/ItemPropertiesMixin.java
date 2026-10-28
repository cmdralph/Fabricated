package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.minecraft.world.item.Item;

/**
 * Makes item properties Fabric's {@link FabricItem.Settings} (1.21.1), as upstream's mixin does: the interface's
 * methods are all defaults on this version, which keep what they set until {@code ItemMixin} hands it to the item.
 */
@Mixin(Item.Properties.class)
abstract class ItemPropertiesMixin implements FabricItem.Settings {
}
