package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.fabricmc.fabric.api.screenhandler.v1.FabricScreenHandlerFactory;
import net.minecraft.world.MenuProvider;

/** fabric-screen-handler-api-v1: every menu provider is a {@link FabricScreenHandlerFactory}, as upstream. Adds the interface only. */
@Mixin(MenuProvider.class)
public interface MenuProviderMixin extends FabricScreenHandlerFactory {
}
