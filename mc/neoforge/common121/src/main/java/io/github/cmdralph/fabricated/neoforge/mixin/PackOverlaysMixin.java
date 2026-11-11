package io.github.cmdralph.fabricated.neoforge.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.llamalad7.mixinextras.sugar.Local;

import io.github.cmdralph.fabricated.neoforge.resource.FabricOverlayConditions;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;

/**
 * Fabric's conditional overlays join those the game reads from a pack's metadata, NeoForge's included, once they are all
 * read ({@link FabricOverlayConditions}, PACK-12).
 */
@Mixin(Pack.class)
abstract class PackOverlaysMixin {
	@ModifyArg(method = "readPackMetadata", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/packs/repository/Pack$Metadata;<init>(Lnet/minecraft/network/chat/Component;Lnet/minecraft/server/packs/repository/PackCompatibility;Lnet/minecraft/world/flag/FeatureFlagSet;Ljava/util/List;Z)V"),
			index = 3)
	private static List<String> fabricated$fabricOverlays(List<String> overlays, @Local(argsOnly = true) PackLocationInfo location, @Local PackResources pack) {
		return FabricOverlayConditions.overlays(location.id(), overlays, pack.getRootResource("pack.mcmeta"));
	}
}
