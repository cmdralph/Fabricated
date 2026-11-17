package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Vanilla 1.19.2's private {@code ItemProperties.registerGeneric} and {@code register} taking a clamped property
 * function, which Fabric API 0.77's object builder invokes (its {@code ModelPredicateProviderRegistryAccessor}s): Forge
 * 43 replaced both with public methods of its own taking any function, so without vanilla's the module failed its
 * pre-flight on every client, and every Fabric mod needing it with it. Each is restored as vanilla's was, calling
 * Forge's, under its runtime name in SRG's, which pre-flight takes for the game class's (core's {@code RestoredFields}).
 * Forge's methods keep vanilla's names in its jar, not SRG's: they are named as they are, not remapped. Applied before
 * the mixins of other mods, whose invokers find vanilla's then.
 */
@Mixin(value = ItemProperties.class, priority = 500)
abstract class ItemPropertiesClampedOverloadsMixin {
	@Shadow(remap = false)
	public static ItemPropertyFunction registerGeneric(ResourceLocation id, ItemPropertyFunction function) {
		throw new AssertionError();
	}

	@Shadow(remap = false)
	public static void register(Item item, ResourceLocation id, ItemPropertyFunction function) {
		throw new AssertionError();
	}

	/** Vanilla's {@code registerGeneric(ResourceLocation, ClampedItemPropertyFunction)}. */
	private static ClampedItemPropertyFunction m_174581_(ResourceLocation id, ClampedItemPropertyFunction function) {
		return (ClampedItemPropertyFunction) registerGeneric(id, function);
	}

	/** Vanilla's {@code register(Item, ResourceLocation, ClampedItemPropertyFunction)}. */
	private static void m_174570_(Item item, ResourceLocation id, ClampedItemPropertyFunction function) {
		register(item, id, function);
	}
}
