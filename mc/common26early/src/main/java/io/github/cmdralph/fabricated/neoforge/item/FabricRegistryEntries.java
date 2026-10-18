package io.github.cmdralph.fabricated.neoforge.item;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.item.v1.EnchantmentEvents;
import net.fabricmc.fabric.api.item.v1.EnchantmentSource;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.enchantment.Enchantment;

/** The data pack registry entries {@code fabric-item-api-v1} lets mods modify while they load: enchantments. */
public final class FabricRegistryEntries {
	private FabricRegistryEntries() {
	}

	/** {@code entry} as Fabric mods modified it, or null if they left it alone. */
	@SuppressWarnings({"unchecked", "deprecation"})
	public static <T> @Nullable T modify(ResourceKey<T> key, T entry, @Nullable Resource resource, RegistryOps.RegistryInfoLookup registries) {
		if (entry instanceof Enchantment enchantment) {
			ResourceKey<Enchantment> id = (ResourceKey<Enchantment>) (ResourceKey<?>) key;
			EnchantmentSource source = FabricEnchantments.source(resource);
			return (T) FabricEnchantments.modify(enchantment, builder -> {
				EnchantmentEvents.MODIFY.invoker().modify(id, builder, source);
				EnchantmentEvents.MODIFY_WITH_LOOKUP.invoker().modify(id, builder, source, registries);
			});
		}
		return null;
	}
}
