package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.forge.EarlyRegistryObjects;
import net.minecraft.core.Holder;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * A block item whose block isn't registered yet ({@link EarlyRegistryObjects}, CORPUS-73): Forge 47's {@code getBlock}
 * gives its block's delegate's value, and threw for a block without one, as Forge registers the item and maps its
 * block to it, so a Fabric mod registering its block items before their blocks failed (Lootr), and the item it left
 * registered stopped Forge's snapshot. Such an item gives its block itself, as vanilla's does; the item of a registered
 * block keeps Forge's delegate. Forge 61 gives the block itself.
 */
@Mixin(BlockItem.class)
abstract class BlockItemEarlyBlockMixin {
	@Shadow
	@Final
	private Block block;

	@WrapOperation(method = "getBlock", at = @At(value = "INVOKE",
			target = "Lnet/minecraftforge/registries/IForgeRegistry;getDelegateOrThrow(Ljava/lang/Object;)Lnet/minecraft/core/Holder$Reference;", remap = false))
	private Holder.Reference<?> fabricated$delegateIfRegistered(IForgeRegistry<?> blocks, Object block, Operation<Holder.Reference<?>> getDelegate) {
		return EarlyRegistryObjects.delegate(blocks, block);
	}

	@WrapOperation(method = "getBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Holder$Reference;get()Ljava/lang/Object;", remap = false))
	private Object fabricated$blockItselfIfUnregistered(Holder.Reference<?> delegate, Operation<Object> get) {
		return delegate == null ? block : get.call(delegate);
	}
}
