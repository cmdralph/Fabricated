package io.github.cmdralph.fabricated.forge.item;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

import net.fabricmc.fabric.api.item.v1.EquipmentSlotProvider;
import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.fabricmc.fabric.api.item.v1.ModifyItemAttributeModifiersCallback;
import net.fabricmc.fabric.impl.item.ItemExtensions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric API's item hooks (fabric-item-api-v1, passed through) where Forge 47 asks its own. Forge's {@code ItemStack}
 * asks its {@code IForgeItem} hooks where upstream's hooks look for vanilla's calls, which Forge replaced: a stack's
 * crafting remainder, which Forge's crafting, furnaces and brewing stands ask ({@code getCraftingRemainingItem}), its
 * attribute modifiers and whether it is the tool for a block. Forge mods ask an item's equipment slot through Forge's
 * {@code getEquipmentSlot}, which vanilla's code doesn't reach. Fabricated's item mixins answer them from a Fabric item's
 * {@link FabricItem} overrides and settings; an item overriding Forge's hooks (a Forge mod's) keeps its own, and where a
 * Fabric item doesn't override Fabric's method, Forge's answer stands (Forge's tools sort tiers there). The mixins name
 * no type of Fabric API's, which a game may not have.
 */
public final class ItemHooks {
	/** Fabric API's item module is installed. Its classes are only touched then. */
	public static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("fabric-item-api-v1");

	private ItemHooks() {
	}

	/**
	 * Forge's {@code getEquipmentSlot}: the slot a Fabric item's equipment slot provider gives, or null (Forge's default,
	 * which leaves the slot to vanilla's rules). Upstream's hook gives the same slot to vanilla's
	 * {@code LivingEntity.getEquipmentSlotForItem}, which asks it before Forge's hook.
	 */
	public static @Nullable EquipmentSlot equipmentSlot(Item item, ItemStack stack) {
		return ENABLED ? Upstream.equipmentSlot(item, stack) : null;
	}

	/**
	 * Forge's {@code getCraftingRemainingItem}: Fabric's {@code getRecipeRemainder}, whose default is vanilla's remainder,
	 * as Forge's is. Only called once {@link #ENABLED} is known to be true.
	 */
	public static ItemStack recipeRemainder(Item item, ItemStack stack) {
		return ((FabricItem) item).getRecipeRemainder(stack);
	}

	/** Whether the item's class overrides Fabric's {@code isSuitableFor}, which then answers for it. */
	public static boolean overridesSuitableFor(Item item) {
		return ENABLED && Upstream.OVERRIDES_SUITABLE_FOR.get(item.getClass());
	}

	/** Fabric's {@code isSuitableFor}; only called where {@link #overridesSuitableFor} is true. */
	public static boolean isSuitableFor(Item item, ItemStack stack, BlockState state) {
		return ((FabricItem) item).isSuitableFor(stack, state);
	}

	/** Whether the item's class overrides Fabric's {@code getAttributeModifiers}, which then answers for it. */
	public static boolean overridesAttributeModifiers(Item item) {
		return ENABLED && Upstream.OVERRIDES_ATTRIBUTE_MODIFIERS.get(item.getClass());
	}

	/** Fabric's {@code getAttributeModifiers}; only called where {@link #overridesAttributeModifiers} is true. */
	public static Multimap<Attribute, AttributeModifier> attributeModifiers(Item item, ItemStack stack, EquipmentSlot slot) {
		return ((FabricItem) item).getAttributeModifiers(stack, slot);
	}

	/**
	 * The item's attribute modifiers as Fabric's {@code ModifyItemAttributeModifiersCallback} leaves them, in a copy the
	 * callback may change, as upstream's hook makes it, before Forge's own event sees them. Only called once
	 * {@link #ENABLED} is known to be true.
	 */
	public static Multimap<Attribute, AttributeModifier> modifyAttributeModifiers(ItemStack stack, EquipmentSlot slot, Multimap<Attribute, AttributeModifier> modifiers) {
		Multimap<Attribute, AttributeModifier> modifiable = LinkedHashMultimap.create(modifiers);
		ModifyItemAttributeModifiersCallback.EVENT.invoker().modifyAttributeModifiers(stack, slot, modifiable);
		return modifiable;
	}

	/** Everything that names Fabric's item module: loaded only once {@link #ENABLED} is known to be true. */
	private static final class Upstream {
		static final ClassValue<Boolean> OVERRIDES_SUITABLE_FOR = overrides("isSuitableFor", ItemStack.class, BlockState.class);
		static final ClassValue<Boolean> OVERRIDES_ATTRIBUTE_MODIFIERS = overrides("getAttributeModifiers", ItemStack.class, EquipmentSlot.class);

		private Upstream() {
		}

		static @Nullable EquipmentSlot equipmentSlot(Item item, ItemStack stack) {
			EquipmentSlotProvider provider = ((ItemExtensions) item).fabric_getEquipmentSlotProvider();
			return provider == null ? null : provider.getPreferredEquipmentSlot(stack);
		}

		private static ClassValue<Boolean> overrides(String name, Class<?>... parameters) {
			return new ClassValue<>() {
				@Override
				protected Boolean computeValue(Class<?> type) {
					try {
						return type.getMethod(name, parameters).getDeclaringClass() != FabricItem.class;
					} catch (NoSuchMethodException | LinkageError e) {
						return false; // a type its methods name is missing: Forge's answer
					}
				}
			};
		}
	}
}
