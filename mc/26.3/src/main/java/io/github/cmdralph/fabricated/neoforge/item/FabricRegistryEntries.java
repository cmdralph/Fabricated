package io.github.cmdralph.fabricated.neoforge.item;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.impl.item.BlockTransformerHelperImpl;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * The data pack registry entries {@code fabric-item-api-v1} lets mods modify while they load: enchantments and block
 * transformers.
 */
public final class FabricRegistryEntries {
	private FabricRegistryEntries() {
	}

	/** {@code entry} as Fabric mods modified it, or null if they left it alone. */
	@SuppressWarnings({"unchecked", "deprecation"})
	public static <T> @Nullable T modify(ResourceKey<T> key, T entry, @Nullable Resource resource, RegistryOps.RegistryInfoLookup registries) {
		ResourceKey<?> id = key;
		if (entry instanceof Enchantment enchantment) {
			return (T) FabricEnchantments.modify((ResourceKey<Enchantment>) id, enchantment, resource, registries);
		}
		if (entry instanceof BlockTransformer transformer) {
			return (T) BlockTransformerHelperImpl.modify((ResourceKey<BlockTransformer>) id, transformer,
					FabricEnchantments.source(resource).toResourceSource(), registries);
		}
		return null;
	}
}
