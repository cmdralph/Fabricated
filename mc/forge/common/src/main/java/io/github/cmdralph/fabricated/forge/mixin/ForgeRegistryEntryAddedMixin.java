package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import io.github.cmdralph.fabricated.forge.RegistryEntryAddedBridge;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraftforge.registries.ForgeRegistry;

/**
 * Fabric API's entry added event for an entry a Forge mod registers through Forge's API ({@code ForgeRegistry.register},
 * where {@code DeferredRegister} and {@code RegisterEvent} put it) into a registry with a vanilla one ({@link
 * RegistryEntryAddedBridge}, CORPUS-108): on Fabric, the event fires for every entry new to a registry, whoever adds it.
 * It fires on the vanilla registry mods hold, the one the root registry has under this registry's name, once that one
 * has the entry. Forge's loading of a registry snapshot (a world's ids, or a server's on joining it) adds its entries
 * itself, not through these methods, as on Fabric no entry is new then.
 */
@Mixin(ForgeRegistry.class)
abstract class ForgeRegistryEntryAddedMixin<V> {
	@WrapMethod(method = "register(Lnet/minecraft/resources/Identifier;Ljava/lang/Object;)V")
	private void fabricated$entryAdded(Identifier key, V value, Operation<Void> register) {
		boolean added = !fabricated$self().containsKey(key);
		register.call(key, value);
		if (added) fabricated$fire(key, value);
	}

	@WrapMethod(method = "register(ILnet/minecraft/resources/Identifier;Ljava/lang/Object;)V")
	private void fabricated$entryAddedWithId(int id, Identifier key, V value, Operation<Void> register) {
		boolean added = !fabricated$self().containsKey(key);
		register.call(id, key, value);
		if (added) fabricated$fire(key, value);
	}

	@Unique
	@SuppressWarnings("unchecked")
	private ForgeRegistry<V> fabricated$self() {
		return (ForgeRegistry<V>) (Object) this;
	}

	@Unique
	@SuppressWarnings("unchecked")
	private void fabricated$fire(Identifier key, V value) {
		ForgeRegistry<V> self = fabricated$self();
		Registry<V> vanilla = (Registry<V>) BuiltInRegistries.REGISTRY.getValue(self.getRegistryKey().identifier());
		if (vanilla != null && vanilla.getValue(key) == value) RegistryEntryAddedBridge.added(vanilla, self.getID(value), key, value);
	}
}
