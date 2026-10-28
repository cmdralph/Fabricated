package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.fabricmc.fabric.impl.resource.loader.FabricResource;
import net.fabricmc.fabric.impl.resource.loader.ModResourcePackCreator;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.Resource;

/**
 * Makes every resource Fabric's {@link FabricResource}: its pack source is the source of the pack it came from, except
 * that NeoForge's mod resource packs ({@code mod/<mod ids>}, source {@code DEFAULT}) report Fabric's mod source, as on
 * Fabric. Only adds a method; no Minecraft code is changed.
 */
@Mixin(Resource.class)
abstract class ResourceMixin implements FabricResource {
	@Override
	public PackSource getFabricPackSource() {
		PackLocationInfo location = ((Resource) (Object) this).source().location();
		if (location.source() == PackSource.DEFAULT && location.id().startsWith("mod/")) return ModResourcePackCreator.RESOURCE_PACK_SOURCE;
		return location.source();
	}
}
