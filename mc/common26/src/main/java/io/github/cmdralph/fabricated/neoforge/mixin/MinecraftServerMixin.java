package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.fabricmc.fabric.api.resource.v1.DataResourceStore;
import net.fabricmc.fabric.impl.resource.FabricDataResourceStoreHolder;
import net.minecraft.server.MinecraftServer;

/**
 * Makes the server Fabric's {@link DataResourceStore}, reading from its current server resources, as Fabric
 * API's resource loader does with an injected interface. Only adds a method; no Minecraft code is changed.
 */
@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin implements DataResourceStore {
	@Override
	public <T> T getOrThrow(Key<T> key) {
		return ((FabricDataResourceStoreHolder) ((MinecraftServer) (Object) this).getServerResources().managers())
				.fabric$getDataResourceStore()
				.getOrThrow(key);
	}
}
