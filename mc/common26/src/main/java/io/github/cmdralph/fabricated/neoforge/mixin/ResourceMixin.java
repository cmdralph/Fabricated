package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.fabricmc.fabric.api.resource.v1.FabricResource;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.Resource;

/**
 * Makes every resource Fabric's {@link FabricResource}: its pack source is the source of the pack it came from.
 * Only adds a method; no Minecraft code is changed.
 */
@Mixin(Resource.class)
abstract class ResourceMixin implements FabricResource {
	@Override
	public PackSource getFabricPackSource() {
		return ((Resource) (Object) this).source().location().source();
	}
}
