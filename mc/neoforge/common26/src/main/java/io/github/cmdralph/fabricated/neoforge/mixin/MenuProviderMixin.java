package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.fabricmc.fabric.api.menu.v1.FabricMenuProvider;
import net.minecraft.world.MenuProvider;

/** fabric-menu-api-v1: every menu provider is a {@link FabricMenuProvider}, as upstream. Adds the interface only. */
@Mixin(MenuProvider.class)
public interface MenuProviderMixin extends FabricMenuProvider {
}
