package io.github.cmdralph.fabricated.neoforge.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import net.fabricmc.fabric.api.item.v1.FabricItemStack;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.neoforged.neoforge.common.extensions.ItemInstanceExtension;

/**
 * Fabric's {@link FabricItemStack} and NeoForge's {@link ItemInstanceExtension} both give item stacks a default
 * {@code getCraftingRemainder()}; with both, calling it throws ({@code IncompatibleClassChangeError}). The stack
 * declares it, as NeoForge's does: through the item's hook, which also answers Fabric's stack-aware remainder.
 */
@Mixin(ItemStack.class)
abstract class ItemStackRemainderMixin implements FabricItemStack, ItemInstanceExtension {
	@Override
	public @Nullable ItemStackTemplate getCraftingRemainder() {
		ItemStack self = (ItemStack) (Object) this;
		return self.getItem().getCraftingRemainder((ItemInstance) self);
	}
}
