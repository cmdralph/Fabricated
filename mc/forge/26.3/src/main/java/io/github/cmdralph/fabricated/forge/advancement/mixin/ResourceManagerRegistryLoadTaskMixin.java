package io.github.cmdralph.fabricated.forge.advancement.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Decoder;

import io.github.cmdralph.fabricated.forge.advancement.AdvancementCompat;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceManagerRegistryLoadTask;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Fabric's advancement {@code REPLACE} and {@code MODIFY} for each advancement Forge 66 decodes, where upstream hooks in
 * (fabric-advancement-api-v1's {@code ResourceManagerRegistryLoadTaskMixin}, which the plan leaves out): Forge's patch
 * decodes the element as an {@code Optional}, which upstream's hook casts to an advancement ({@link AdvancementCompat}).
 * The lambda is found by its name's pattern, as Forge compiles the patched class itself.
 */
@Mixin(ResourceManagerRegistryLoadTask.class)
abstract class ResourceManagerRegistryLoadTaskMixin {
	@Shadow
	@Final
	private ResourceManager resourceManager;

	@SuppressWarnings("unchecked")
	@WrapOperation(method = "/^lambda\\$load\\$\\d+$/", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/resources/RegistryLoadTask$PendingRegistration;loadFromResource(Lcom/mojang/serialization/Decoder;Lnet/minecraft/resources/RegistryOps;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/server/packs/resources/Resource;)Lcom/mojang/datafixers/util/Either;"))
	private <T> Either<T, Exception> fabricated$modifyAdvancement(Decoder<T> decoder, RegistryOps<JsonElement> ops, ResourceKey<T> key, Resource resource, Operation<Either<T, Exception>> original) {
		Either<T, Exception> result = original.call(decoder, ops, key, resource);
		if (!key.isFor(Registries.ADVANCEMENT)) return result;
		RegistryOps.RegistryInfoLookup lookup = ops::getter;
		return result.mapLeft(value -> (T) AdvancementCompat.modify(value, key.identifier(), resource, resourceManager, lookup));
	}
}
