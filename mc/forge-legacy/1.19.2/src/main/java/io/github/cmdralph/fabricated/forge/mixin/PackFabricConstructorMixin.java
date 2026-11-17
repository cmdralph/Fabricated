package io.github.cmdralph.fabricated.forge.mixin;

import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.forge.resource.FabricPackConstructors;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;

/**
 * Forge 43's {@code Pack.create} calls a pack constructor's method taking whether the pack is hidden, which a Fabric
 * mod's constructor doesn't implement: it gets vanilla's ({@link FabricPackConstructors}). The method is Forge's, by
 * its own name, not remapped.
 */
@Mixin(Pack.class)
abstract class PackFabricConstructorMixin {
	@WrapOperation(method = "create(Ljava/lang/String;ZLjava/util/function/Supplier;Lnet/minecraft/server/packs/repository/Pack$PackConstructor;"
			+ "Lnet/minecraft/server/packs/repository/Pack$Position;Lnet/minecraft/server/packs/repository/PackSource;)Lnet/minecraft/server/packs/repository/Pack;",
			at = @At(value = "INVOKE", remap = false, target = "Lnet/minecraft/server/packs/repository/Pack$PackConstructor;create(Ljava/lang/String;"
					+ "Lnet/minecraft/network/chat/Component;ZLjava/util/function/Supplier;Lnet/minecraft/server/packs/metadata/pack/PackMetadataSection;"
					+ "Lnet/minecraft/server/packs/repository/Pack$Position;Lnet/minecraft/server/packs/repository/PackSource;Z)Lnet/minecraft/server/packs/repository/Pack;"))
	private static Pack fabricated$fabricConstructor(Pack.PackConstructor constructor, String id, Component title, boolean required,
			Supplier<PackResources> resources, PackMetadataSection metadata, Pack.Position position, PackSource source, boolean hidden,
			Operation<Pack> create) {
		if (FabricPackConstructors.vanillaOnly(constructor)) {
			return FabricPackConstructors.create(constructor, id, title, required, resources, metadata, position, source);
		}
		return create.call(constructor, id, title, required, resources, metadata, position, source, hidden);
	}
}
