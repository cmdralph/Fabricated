package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.item.v1.EnchantmentEvents;
import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Forge 52's part of the CI test mod: exercises Fabric API's fabric-item-api-v1 through Forge's own item hooks
 * (equipment slot, crafting remainder, enchanting check, custom damage, default components, creator mod), in 1.21.1's
 * names (Forge 61 to 66's is mc/forge's).
 */
public final class ItemApiTest implements ModInitializer {
	private static final List<EquipmentSlot> DAMAGED_IN = new ArrayList<>();
	private static volatile String sharpnessSource;

	/** A Fabric item overriding FabricItem's methods, which Fabric's item interface gives every item. */
	private static final class TestItem extends Item implements FabricItem {
		TestItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public ItemStack getRecipeRemainder(ItemStack stack) {
			return new ItemStack(Items.MILK_BUCKET.getCraftingRemainingItem());
		}

		@Override
		public String getCreatorNamespace(ItemStack stack) {
			return "fabricated_creator";
		}
	}

	@Override
	public void onInitialize() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("fabricated_testmod", "item_api_item"));
		Item.Properties properties = new Item.Properties().durability(100);
		((FabricItem.Settings) properties).equipmentSlot((entity, stack) -> EquipmentSlot.HEAD);
		((FabricItem.Settings) properties).customDamage((stack, amount, entity, slot, onBroken) -> {
			DAMAGED_IN.add(slot);
			return 0;
		});
		Item item = Registry.register(BuiltInRegistries.ITEM, key, new TestItem(properties));
		DefaultItemComponentEvents.MODIFY.register(context -> context.modify(item, builder -> builder.set(DataComponents.RARITY, Rarity.EPIC)));
		EnchantmentEvents.ALLOW_ENCHANTING.register((enchantment, stack, context) -> stack.is(item) ? TriState.TRUE : TriState.DEFAULT);
		EnchantmentEvents.MODIFY.register((enchantment, builder, source) -> {
			if (!enchantment.equals(Enchantments.SHARPNESS)) return;
			sharpnessSource = source.name();
			builder.withEffect(EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE);
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			List<String> failed = new ArrayList<>();
			ItemStack stack = new ItemStack(item);
			if (stack.getEquipmentSlot() != EquipmentSlot.HEAD) failed.add("equipment_slot");
			if (!ItemStack.matches(item.getCraftingRemainingItem(stack), new ItemStack(Items.MILK_BUCKET.getCraftingRemainingItem()))) failed.add("crafting_remainder");
			Holder<Enchantment> sharpness = server.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SHARPNESS);
			if (!stack.canApplyAtEnchantingTable(sharpness.value()) || new ItemStack(Items.STICK).canApplyAtEnchantingTable(sharpness.value())) {
				failed.add("allow_enchanting");
			}
			if (stack.get(DataComponents.RARITY) != Rarity.EPIC) failed.add("default_components");
			if (!"VANILLA".equals(sharpnessSource) || !sharpness.value().effects().has(EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) failed.add("enchantment_modify");
			if (!"fabricated_creator".equals(item.getCreatorModId(stack))) failed.add("creator_namespace");
			// Vanilla hands the entity to the damage hook only for players.
			Player player = FakePlayer.get(server.overworld());
			player.setItemSlot(EquipmentSlot.MAINHAND, stack);
			stack.hurtAndBreak(5, player, EquipmentSlot.MAINHAND);
			if (!DAMAGED_IN.equals(List.of(EquipmentSlot.MAINHAND)) || stack.getDamageValue() != 0) failed.add("custom_damage");
			if (failed.isEmpty()) {
				TestMod.LOGGER.info("FABRICATED-TEST item api OK");
			} else {
				TestMod.LOGGER.error("FABRICATED-TEST item api FAILED: {}", failed);
			}
		});
	}
}
