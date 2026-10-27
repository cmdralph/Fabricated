package io.github.cmdralph.fabricated.forge.transfer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

/**
 * Forge's fluid handler capability of a Fabric mod's item stack, there once Fabric's lookup finds a fluid storage for
 * the stack. Fabric mods register storages for their items while they initialize, but a stack may be made before then,
 * so a stack without one is asked again each time; once found, the handler stays, as Forge's own item handlers do.
 */
final class FabricItemFluidProvider implements ICapabilityProvider {
	private final ItemStack stack;
	private @Nullable FabricItemFluidHandler handler;
	private LazyOptional<IFluidHandlerItem> capability = LazyOptional.empty();

	FabricItemFluidProvider(ItemStack stack) {
		this.stack = stack;
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
		if (capability != ForgeCapabilities.FLUID_HANDLER_ITEM) return LazyOptional.empty();
		if (!this.capability.isPresent()) {
			if (handler == null) handler = new FabricItemFluidHandler(stack);
			if (handler.storage() == null) return LazyOptional.empty();
			FabricItemFluidHandler found = handler;
			this.capability = LazyOptional.of(() -> found);
		}
		return this.capability.cast();
	}
}
