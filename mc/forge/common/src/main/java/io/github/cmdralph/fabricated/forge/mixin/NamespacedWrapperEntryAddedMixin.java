package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import io.github.cmdralph.fabricated.forge.RegistryEntryAddedBridge;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.registries.ForgeRegistry;

/**
 * Fabric API's entry added event for an entry registered through vanilla's API into a registry Forge manages, as a
 * Fabric mod registers ({@link RegistryEntryAddedBridge}, CORPUS-108). Forge's wrapper replaces vanilla's {@code
 * register}, where upstream fires the event; the event fires once Forge's registry has the entry, for a name it didn't
 * have, as upstream's does for a name new to vanilla's registry (an override of an entry by a Forge mod isn't one).
 */
@Mixin(targets = "net.minecraftforge.registries.NamespacedWrapper")
abstract class NamespacedWrapperEntryAddedMixin<T> {
	@Shadow
	@Final
	private ForgeRegistry<T> delegate;

	@WrapMethod(method = "register(Lnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lnet/minecraft/core/RegistrationInfo;)Lnet/minecraft/core/Holder$Reference;")
	@SuppressWarnings("unchecked")
	private Holder.Reference<T> fabricated$entryAdded(ResourceKey<T> key, T value, RegistrationInfo info, Operation<Holder.Reference<T>> register) {
		boolean added = !delegate.containsKey(key.identifier());
		Holder.Reference<T> holder = register.call(key, value, info);
		if (added) RegistryEntryAddedBridge.added((Registry<T>) (Object) this, delegate.getID(value), key.identifier(), value);
		return holder;
	}
}
