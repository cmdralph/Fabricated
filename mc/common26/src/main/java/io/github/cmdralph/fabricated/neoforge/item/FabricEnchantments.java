package io.github.cmdralph.fabricated.neoforge.item;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.item.v1.EnchantmentEvents;
import net.fabricmc.fabric.api.item.v1.EnchantmentSource;
import net.fabricmc.fabric.impl.resource.pack.BuiltinModPackSource;
import net.fabricmc.fabric.impl.resource.pack.ModResourcePackCreator;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * {@code EnchantmentEvents.MODIFY} and {@code MODIFY_WITH_LOOKUP}, as upstream runs them: each enchantment a data pack
 * defines is turned back into a builder while it loads, handed to the listeners, and rebuilt if one of them used it.
 */
public final class FabricEnchantments {
	/** An enchantment builder's state, opened up (and its use tracked) by Fabricated's mixin. */
	public interface Builder {
		Enchantment.EnchantmentDefinition fabricated$definition();

		HolderSet<Enchantment> fabricated$exclusiveSet();

		DataComponentMap.Builder fabricated$effectMap();

		<E> List<E> fabricated$effectsList(DataComponentType<List<E>> type);

		void fabricated$resetModified();

		boolean fabricated$modified();
	}

	private FabricEnchantments() {
	}

	/** {@code original} as the listeners left it, or null if none of them changed it. */
	@SuppressWarnings({"unchecked", "deprecation"})
	public static @Nullable Enchantment modify(ResourceKey<Enchantment> key, Enchantment original, @Nullable Resource resource,
			RegistryOps.RegistryInfoLookup registries) {
		Enchantment.Builder builder = Enchantment.enchantment(original.definition());
		Builder state = (Builder) builder;
		builder.exclusiveWith(original.exclusiveSet());
		state.fabricated$effectMap().addAll(original.effects());
		for (TypedDataComponent<?> component : original.effects()) {
			// The component's type matches its value, a list.
			if (component.value() instanceof List<?> values) state.fabricated$effectsList((DataComponentType<List<Object>>) component.type()).addAll(values);
		}
		state.fabricated$resetModified();

		EnchantmentSource source = source(resource);
		EnchantmentEvents.MODIFY.invoker().modify(key, builder, source);
		EnchantmentEvents.MODIFY_WITH_LOOKUP.invoker().modify(key, builder, source, registries);
		if (!state.fabricated$modified()) return null;
		return new Enchantment(original.description(), state.fabricated$definition(), state.fabricated$exclusiveSet(), state.fabricated$effectMap().build());
	}

	/** Where a data pack file came from, as upstream decides it. */
	@SuppressWarnings("deprecation")
	public static EnchantmentSource source(@Nullable Resource resource) {
		PackSource pack = resource != null ? resource.getFabricPackSource() : null;
		if (pack == PackSource.BUILT_IN) return EnchantmentSource.VANILLA;
		if (pack == ModResourcePackCreator.RESOURCE_PACK_SOURCE || pack instanceof BuiltinModPackSource) return EnchantmentSource.MOD;
		return EnchantmentSource.DATA_PACK;
	}
}
