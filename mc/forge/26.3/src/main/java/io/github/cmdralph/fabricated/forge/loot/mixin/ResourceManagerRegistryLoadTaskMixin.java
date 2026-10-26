package io.github.cmdralph.fabricated.forge.loot.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Decoder;

import io.github.cmdralph.fabricated.forge.loot.LootBridge;
import io.github.cmdralph.fabricated.forge.loot.LootCompat;
import net.fabricmc.fabric.api.loot.v3.LootTableSource;
import net.fabricmc.fabric.impl.loot.LootTableHolderProvider;
import net.fabricmc.fabric.impl.loot.LootUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceManagerRegistryLoadTask;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Fabric's {@code REPLACE} and {@code MODIFY} for each loot table Forge 66 decodes, where upstream hooks in (its
 * {@code ResourceManagerRegistryLoadTaskMixin}): Forge's patch decodes the element as an {@code Optional} (its conditional
 * data), which upstream's hook casts to a table, stopping the data pack load, and fires Forge's loot table event without
 * using the table its listeners leave. The lambda is found by its name's pattern, as Forge compiles the patched class
 * itself.
 */
@Mixin(ResourceManagerRegistryLoadTask.class)
abstract class ResourceManagerRegistryLoadTaskMixin {
	@SuppressWarnings("unchecked")
	@WrapOperation(method = "/^lambda\\$load\\$\\d+$/", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/resources/RegistryLoadTask$PendingRegistration;loadFromResource(Lcom/mojang/serialization/Decoder;Lnet/minecraft/resources/RegistryOps;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/server/packs/resources/Resource;)Lcom/mojang/datafixers/util/Either;"))
	private <T> Either<T, Exception> fabricated$modifyLootTable(Decoder<T> decoder, RegistryOps<JsonElement> ops, ResourceKey<T> key, Resource resource, Operation<Either<T, Exception>> original) {
		Either<T, Exception> result = original.call(decoder, ops, key, resource);
		if (!key.isFor(Registries.LOOT_TABLE)) return result;
		ResourceKey<LootTable> tableKey = ResourceKey.create(Registries.LOOT_TABLE, key.identifier());
		// Reloadable registries (loot tables) through the loading ops, listing through the reload's registries, as upstream.
		HolderLookup.Provider registries = new LootTableHolderProvider(ops::getter, LootBridge.loadingRegistriesOrEmpty());
		LootTableSource source = LootUtil.determineSource(resource);
		return result.mapLeft(value -> (T) LootCompat.modify(value, tableKey, source, registries));
	}
}
