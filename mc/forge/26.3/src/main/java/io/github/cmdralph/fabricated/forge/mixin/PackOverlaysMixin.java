package io.github.cmdralph.fabricated.forge.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.llamalad7.mixinextras.sugar.Local;

import io.github.cmdralph.fabricated.forge.resource.FabricOverlayConditions;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.repository.Pack;

/**
 * Fabric's conditional overlays join those the game reads from a pack's metadata, once they are all read
 * ({@link FabricOverlayConditions}, PACK-12): Forge 66's, where 26.3 reads the metadata through
 * {@code PackMetadataResources} and Forge no longer adds whether a pack is hidden.
 */
@Mixin(Pack.class)
abstract class PackOverlaysMixin {
	@ModifyArg(method = "readPackMetadata", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/packs/repository/Pack$Metadata;<init>(Lnet/minecraft/network/chat/Component;Lnet/minecraft/server/packs/repository/PackCompatibility;Lnet/minecraft/world/flag/FeatureFlagSet;Ljava/util/List;)V"),
			index = 3)
	private static List<String> fabricated$fabricOverlays(List<String> overlays, @Local(argsOnly = true) PackLocationInfo location, @Local PackMetadataResources pack) {
		return FabricOverlayConditions.overlays(location.id(), overlays, pack.getRootResource("pack.mcmeta"));
	}
}
