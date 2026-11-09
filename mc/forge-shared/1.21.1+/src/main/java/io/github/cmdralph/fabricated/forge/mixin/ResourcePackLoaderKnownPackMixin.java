package io.github.cmdralph.fabricated.forge.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import io.github.cmdralph.fabricated.forge.resource.FabricPackIdentity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.resource.ResourcePackLoader;

/**
 * A Fabric mod's own pack takes Fabric API's known-pack identity, so that its registry entries are stable, as on Fabric,
 * and a world holding them opens without asking to confirm experimental settings (PACK-5, PACK-10, see
 * {@link FabricPackIdentity}). The first pack location {@code findPacks} makes is each mod file's (the later ones are
 * for the merged pack of the mods hidden from the resource pack list, which holds no registry data).
 */
@Mixin(ResourcePackLoader.class)
abstract class ResourcePackLoaderKnownPackMixin {
	@ModifyArg(method = "findPacks", at = @At(value = "INVOKE", ordinal = 0,
			target = "Lnet/minecraft/server/packs/PackLocationInfo;<init>(Ljava/lang/String;Lnet/minecraft/network/chat/Component;Lnet/minecraft/server/packs/repository/PackSource;Ljava/util/Optional;)V"),
			index = 3)
	private static Optional<KnownPack> fabricated$knownPack(String name, Component title, PackSource source, Optional<KnownPack> knownPack) {
		return FabricPackIdentity.of(name, knownPack);
	}
}
