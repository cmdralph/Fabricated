package io.github.cmdralph.fabricated.forge.item;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.item.v1.EnchantingContext;
import net.fabricmc.fabric.api.item.v1.EquipmentSlotProvider;
import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.fabricmc.fabric.api.item.v1.FabricItemStack;
import net.fabricmc.fabric.impl.item.ItemExtensions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Fabric API's item hooks (fabric-item-api-v1, passed through) where Forge 61 to 66 ask their own: an item's
 * equipment slot, whether the enchanting table may offer it an enchantment, the mod that made it, and on Forge 61 its
 * stack's crafting remainder. Forge asks its {@code IForgeItem} hooks for those where upstream's hooks look for
 * vanilla's code (its enchanting table check calls Forge's hook, not the enchantment's), and Forge mods ask the hooks
 * themselves, so Fabricated's item mixins answer them from a Fabric item's {@link FabricItem} settings and overrides.
 * An item overriding Forge's hooks (a Forge mod's) keeps its own answers. The mixins name no type of Fabric API's, which
 * a game may not have.
 */
public final class ItemHooks {
	/** Fabric API's item module is installed. Its classes are only touched then. */
	public static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("fabric-item-api-v1");

	private ItemHooks() {
	}

	/**
	 * Forge's {@code getEquipmentSlot}: the slot a Fabric item's equipment slot provider gives, or null (Forge's default,
	 * which leaves the slot to the item's components). Forge's hook names no entity, so the provider gets null for it;
	 * Forge's own {@code LivingEntity.getEquipmentSlotForItem} asks upstream's hook first, with the entity.
	 */
	public static @Nullable EquipmentSlot equipmentSlot(Item item, ItemStack stack) {
		return ENABLED ? Upstream.equipmentSlot(item, stack) : null;
	}

	/**
	 * Forge's {@code canApplyAtEnchantingTable}: Fabric's {@code ALLOW_ENCHANTING} event, then the item's
	 * {@code canBeEnchantedWith} for the table ({@code PRIMARY}), whose default is Forge's and vanilla's check. Only
	 * called once {@link #ENABLED} is known to be true.
	 */
	public static boolean canApplyAtEnchantingTable(ItemStack stack, Holder<Enchantment> enchantment) {
		return Upstream.canApplyAtEnchantingTable(stack, enchantment);
	}

	/**
	 * The creator mod Forge shows for a stack: Fabric's {@code getCreatorNamespace} where the item overrides it, or null
	 * to keep Forge's answer (which also knows a spawn egg's mob).
	 */
	public static @Nullable String creatorNamespace(Item item, ItemStack stack) {
		return ENABLED ? Upstream.creatorNamespace(item, stack) : null;
	}

	/** Everything that names Fabric's item module: loaded only once {@link #ENABLED} is known to be true. */
	private static final class Upstream {
		private static final ClassValue<Boolean> OVERRIDES_CREATOR_NAMESPACE = new ClassValue<>() {
			@Override
			protected Boolean computeValue(Class<?> type) {
				try {
					return type.getMethod("getCreatorNamespace", ItemStack.class).getDeclaringClass() != FabricItem.class;
				} catch (NoSuchMethodException | LinkageError e) {
					return false; // a type its methods name is missing: Forge's answer
				}
			}
		};

		private Upstream() {
		}

		static @Nullable EquipmentSlot equipmentSlot(Item item, ItemStack stack) {
			EquipmentSlotProvider provider = ((ItemExtensions) item).fabric_getEquipmentSlotProvider();
			return provider == null ? null : ItemCompat.equipmentSlot(provider, stack);
		}

		static boolean canApplyAtEnchantingTable(ItemStack stack, Holder<Enchantment> enchantment) {
			return ((FabricItemStack) (Object) stack).canBeEnchantedWith(enchantment, EnchantingContext.PRIMARY);
		}

		static @Nullable String creatorNamespace(Item item, ItemStack stack) {
			return OVERRIDES_CREATOR_NAMESPACE.get(item.getClass()) ? ((FabricItem) item).getCreatorNamespace(stack) : null;
		}
	}
}
