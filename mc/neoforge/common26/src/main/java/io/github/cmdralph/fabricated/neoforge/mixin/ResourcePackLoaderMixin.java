package io.github.cmdralph.fabricated.neoforge.mixin;

import java.io.IOException;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.google.gson.JsonParseException;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import io.github.cmdralph.fabricated.loader.FabricatedState;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.neoforged.neoforge.resource.ResourcePackLoader;
import net.neoforged.neoforgespi.locating.IModFile;

/**
 * A Fabric mod's own pack whose {@code pack.mcmeta} can't be read is left out as NeoForge leaves out one it can't open:
 * with its error naming the mod file and a loading warning, while the other packs load (PACK-12). NeoForge 26.x reads a
 * mod's metadata again with its fallback section when the first read fails, and the second failure of an unreadable file
 * stopped the whole repository's reload. Native mods' packs are NeoForge's as they are.
 */
@Mixin(ResourcePackLoader.class)
abstract class ResourcePackLoaderMixin {
	@WrapOperation(method = "packFinder", at = @At(value = "INVOKE",
			target = "Lnet/neoforged/neoforge/resource/ResourcePackLoader;readWithOptionalMeta(Lnet/minecraft/server/packs/PackLocationInfo;Lnet/minecraft/server/packs/repository/Pack$ResourcesSupplier;Lnet/minecraft/server/packs/PackType;Lnet/minecraft/server/packs/PackSelectionConfig;)Lnet/minecraft/server/packs/repository/Pack;"))
	private static Pack fabricated$leaveOutUnreadableFabricPack(PackLocationInfo location, Pack.ResourcesSupplier resources, PackType type, PackSelectionConfig config,
			Operation<Pack> original, @Local IModFile modFile) throws IOException {
		try {
			return original.call(location, resources, type, config);
		} catch (JsonParseException e) {
			FabricatedState state = FabricatedState.get();
			if (state == null || modFile.getModInfos().stream().noneMatch(mod -> state.isFabricMod(mod.getModId()))) throw e;
			throw new IOException("Fabric mod pack " + location.id() + " has a pack.mcmeta that can't be read", e);
		}
	}
}
