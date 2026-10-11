package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.fabricmc.fabric.api.resource.v1.DataResourceStore;
import net.fabricmc.fabric.impl.resource.DataResourceStoreImpl;
import net.fabricmc.fabric.impl.resource.FabricDataResourceStoreHolder;
import net.minecraft.server.ReloadableServerResources;

/**
 * Gives each set of server resources the Fabric data store its reload fills ({@code DataResourceLoader}).
 * Only adds a field and a method; no Minecraft code is changed.
 */
@Mixin(ReloadableServerResources.class)
abstract class ReloadableServerResourcesMixin implements FabricDataResourceStoreHolder {
	@Unique
	private final DataResourceStore.Mutable fabricated$dataResourceStore = new DataResourceStoreImpl();

	@Override
	public DataResourceStore.Mutable fabric$getDataResourceStore() {
		return this.fabricated$dataResourceStore;
	}
}
