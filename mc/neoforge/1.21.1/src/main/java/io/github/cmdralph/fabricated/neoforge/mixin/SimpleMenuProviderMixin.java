package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuConstructor;

/** fabric-screen-handler-api-v1: as upstream, a {@link SimpleMenuProvider} around an {@link ExtendedScreenHandlerFactory} opens an extended menu. */
@Mixin(SimpleMenuProvider.class)
abstract class SimpleMenuProviderMixin implements MenuProvider {
	@Shadow
	@Final
	private MenuConstructor menuConstructor;

	@Override
	public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
		if (menuConstructor instanceof ExtendedScreenHandlerFactory<?> extended) extended.writeClientSideData(menu, buffer);
	}
}
