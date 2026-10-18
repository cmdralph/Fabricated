package io.github.cmdralph.fabricated.neoforge.item;

import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.item.v1.EnchantmentSource;
import net.fabricmc.fabric.impl.loot.LootUtil;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * {@code EnchantmentEvents.MODIFY} (and {@code MODIFY_WITH_LOOKUP} on 26.x), as upstream runs them: each enchantment a
 * data pack defines is turned back into a builder while it loads, handed to the listeners, and rebuilt if one of them
 * used it.
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

	/** {@code original} as {@code listeners} left its builder, or null if none of them changed it. */
	@SuppressWarnings("unchecked")
	public static @Nullable Enchantment modify(Enchantment original, Consumer<Enchantment.Builder> listeners) {
		Enchantment.Builder builder = Enchantment.enchantment(original.definition());
		Builder state = (Builder) builder;
		builder.exclusiveWith(original.exclusiveSet());
		state.fabricated$effectMap().addAll(original.effects());
		for (TypedDataComponent<?> component : original.effects()) {
			// The component's type matches its value, a list.
			if (component.value() instanceof List<?> values) state.fabricated$effectsList((DataComponentType<List<Object>>) component.type()).addAll(values);
		}
		state.fabricated$resetModified();
		listeners.accept(builder);
		if (!state.fabricated$modified()) return null;
		return new Enchantment(original.description(), state.fabricated$definition(), state.fabricated$exclusiveSet(), state.fabricated$effectMap().build());
	}

	/** Where a data pack file came from, as upstream decides it (the same way as for loot tables). */
	@SuppressWarnings("deprecation")
	public static EnchantmentSource source(@Nullable Resource resource) {
		return switch (LootUtil.determineSource(resource)) {
			case VANILLA -> EnchantmentSource.VANILLA;
			case MOD -> EnchantmentSource.MOD;
			default -> EnchantmentSource.DATA_PACK;
		};
	}
}
