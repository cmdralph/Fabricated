package io.github.cmdralph.fabricated.neoforge.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.neoforge.item.ItemApiBridge;
import net.fabricmc.fabric.api.item.v1.CustomDamageHandler;
import net.fabricmc.fabric.api.item.v1.EnchantingContext;
import net.fabricmc.fabric.api.item.v1.EquipmentSlotProvider;
import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.fabricmc.fabric.impl.item.FabricItemInternals;
import net.fabricmc.fabric.impl.item.ItemExtensions;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.common.extensions.IItemExtension;

/**
 * Makes every item Fabric's {@link FabricItem} (26.x) and answers NeoForge's item hooks from it, so a Fabric item's
 * overrides take effect wherever NeoForge asks: crafting remainders (furnaces, brewing, crafting), enchanting checks
 * (with Fabric's {@code ALLOW_ENCHANTING} event), equipment slots, the re-equip animation and block-breaking reset, and
 * the creator mod shown in tooltips. Items that override NeoForge's hooks themselves (NeoForge mods' items) keep their
 * own. Adds methods and fields, plus one hook at the end of the constructor that copies Fabric's per-item settings.
 */
@Mixin(Item.class)
abstract class ItemMixin implements FabricItem, ItemExtensions, IItemExtension {
	@Unique
	private @Nullable EquipmentSlotProvider fabricated$equipmentSlotProvider;
	@Unique
	private @Nullable CustomDamageHandler fabricated$customDamageHandler;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void fabricated$onConstruct(Item.Properties properties, CallbackInfo ci) {
		FabricItemInternals.onBuild(properties, (Item) (Object) this);
	}

	@Override
	public @Nullable EquipmentSlotProvider fabric_getEquipmentSlotProvider() {
		return fabricated$equipmentSlotProvider;
	}

	@Override
	public void fabric_setEquipmentSlotProvider(@Nullable EquipmentSlotProvider equipmentSlotProvider) {
		this.fabricated$equipmentSlotProvider = equipmentSlotProvider;
	}

	@Override
	public @Nullable CustomDamageHandler fabric_getCustomDamageHandler() {
		return fabricated$customDamageHandler;
	}

	@Override
	public void fabric_setCustomDamageHandler(@Nullable CustomDamageHandler handler) {
		this.fabricated$customDamageHandler = handler;
	}

	/** NeoForge's crafting remainder hook, answered from Fabric's stack-aware {@code getCraftingRemainder}. */
	@Override
	public @Nullable ItemStackTemplate getCraftingRemainder(ItemInstance instance) {
		if (instance instanceof ItemStack stack) return this.getCraftingRemainder(stack);
		return IItemExtension.super.getCraftingRemainder(instance);
	}

	/** Anvils, the enchant command and random-enchant loot: Fabric's {@code ACCEPTABLE} check (enchanted books stay accepted). */
	@Override
	public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
		return stack.is(Items.ENCHANTED_BOOK) || stack.canBeEnchantedWith(enchantment, EnchantingContext.ACCEPTABLE);
	}

	/** The enchanting table: Fabric's {@code PRIMARY} check (books stay accepted). */
	@Override
	public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
		return stack.is(Items.BOOK) || stack.canBeEnchantedWith(enchantment, EnchantingContext.PRIMARY);
	}

	/**
	 * Fabric's equipment slot provider. NeoForge's hook doesn't say which entity asks, so the provider gets
	 * {@code null} for it.
	 */
	@Override
	@SuppressWarnings("DataFlowIssue")
	public @Nullable EquipmentSlot getEquipmentSlot(ItemStack stack) {
		EquipmentSlotProvider provider = fabricated$equipmentSlotProvider;
		return provider != null ? provider.getEquipmentSlotForItem(null, stack) : IItemExtension.super.getEquipmentSlot(stack);
	}

	/** Fabric's {@code allowComponentsUpdateAnimation} (client): no re-equip animation when only the components changed. */
	@Override
	public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
		Player player = ItemApiBridge.clientPlayer();
		if (player != null && !slotChanged && oldStack.getItem() == newStack.getItem()) {
			InteractionHand hand = newStack == player.getOffhandItem() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
			if (!this.allowComponentsUpdateAnimation(player, hand, oldStack, newStack)) return false;
		}
		return IItemExtension.super.shouldCauseReequipAnimation(oldStack, newStack, slotChanged);
	}

	/** Fabric's {@code allowContinuingBlockBreaking} (client): keep mining when only the components changed. */
	@Override
	public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
		Player player = ItemApiBridge.clientPlayer();
		if (player != null && oldStack.is(newStack.getItem()) && this.allowContinuingBlockBreaking(player, oldStack, newStack)) return false;
		return IItemExtension.super.shouldCauseBlockBreakReset(oldStack, newStack);
	}

	/** The creator mod NeoForge shows: Fabric's {@code getCreatorNamespace} when the item overrides it. */
	@Override
	public String getCreatorModId(HolderLookup.Provider registries, ItemStack stack) {
		if (ItemApiBridge.overridesCreatorNamespace(this.getClass())) return this.getCreatorNamespace(stack);
		return IItemExtension.super.getCreatorModId(registries, stack);
	}
}
