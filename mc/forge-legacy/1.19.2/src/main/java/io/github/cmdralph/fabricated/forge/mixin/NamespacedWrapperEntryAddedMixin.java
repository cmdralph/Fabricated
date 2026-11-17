package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.serialization.Lifecycle;

import io.github.cmdralph.fabricated.forge.RegistryEntryAddedBridge;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.registries.ForgeRegistry;

/**
 * Fabric API's entry added event for an entry registered through vanilla's API into a registry Forge manages, as a
 * Fabric mod registers ({@link RegistryEntryAddedBridge}, CORPUS-108). Forge's wrappers (Forge 43 has one for defaulted
 * registries, such as the items', and one for the rest) replace vanilla's {@code registerMapping}, where upstream fires
 * the event, and their {@code register} calls their own; the event fires once Forge's registry has the entry, for a name
 * it didn't have, as upstream's does for a name new to vanilla's registry (an override of an entry by a Forge mod isn't
 * one).
 *
 * <p>Forge's jar is in SRG's names at run time, so the target is the SRG name of {@code registerMapping},
 * {@code m_203704_}, read from MCPConfig's and Fabric's intermediary mappings for 1.19.2, whose {@code registerMapping}
 * returns a {@code Holder}, and nothing in this mixin is remapped. Forge's wrappers are package-private: named as
 * strings.
 */
@Mixin(targets = {"net.minecraftforge.registries.NamespacedWrapper", "net.minecraftforge.registries.NamespacedDefaultedWrapper"}, remap = false)
abstract class NamespacedWrapperEntryAddedMixin<T> {
	// Mixin refuses a remapped @Shadow in a mixin of several targets, and reads each @Shadow's own remap.
	@Shadow(remap = false)
	@Final
	private ForgeRegistry<T> delegate;

	@WrapMethod(method = "m_203704_(ILnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lcom/mojang/serialization/Lifecycle;)Lnet/minecraft/core/Holder;")
	@SuppressWarnings("unchecked")
	private Holder<T> fabricated$entryAdded(int id, ResourceKey<T> key, T value, Lifecycle lifecycle,
			Operation<Holder<T>> registerMapping) {
		boolean added = !delegate.containsKey(key.location());
		Holder<T> holder = registerMapping.call(id, key, value, lifecycle);
		if (added) RegistryEntryAddedBridge.added((Registry<T>) (Object) this, delegate.getID(value), key.location(), value);
		return holder;
	}
}
