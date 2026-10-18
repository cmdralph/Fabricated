package io.github.cmdralph.fabricated.neoforge.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;

import io.github.cmdralph.fabricated.neoforge.item.FabricEnchantments;
import net.fabricmc.fabric.api.item.v1.EnchantmentEvents;
import net.fabricmc.fabric.api.item.v1.EnchantmentSource;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * {@code EnchantmentEvents.MODIFY} on 1.21.11, as each enchantment is read from its file (see {@link FabricEnchantments}).
 * Upstream wraps the registration, which NeoForge moved into a lambda for its load conditions; this changes the decoded
 * entry instead. A changed entry is no longer the known pack's own, so it is sent to clients in full.
 */
@Mixin(RegistryDataLoader.class)
abstract class RegistryDataLoaderEnchantmentMixin {
	@ModifyExpressionValue(method = "loadElementFromResource", at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/DataResult;getOrThrow()Ljava/lang/Object;"))
	@SuppressWarnings("unchecked")
	private static Object fabricated$modifyEnchantment(Object decoded, @Local(argsOnly = true) ResourceKey<?> key,
			@Local(argsOnly = true) Resource resource, @Local(argsOnly = true) LocalRef<RegistrationInfo> info) {
		if (!(decoded instanceof Optional<?> candidate) || !(candidate.orElse(null) instanceof Enchantment enchantment)) return decoded;
		ResourceKey<Enchantment> id = (ResourceKey<Enchantment>) key;
		EnchantmentSource source = FabricEnchantments.source(resource);
		Enchantment modified = FabricEnchantments.modify(enchantment, builder -> EnchantmentEvents.MODIFY.invoker().modify(id, builder, source));
		if (modified == null) return decoded;
		info.set(new RegistrationInfo(Optional.empty(), info.get().lifecycle()));
		return Optional.of(modified);
	}
}
