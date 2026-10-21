package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.item.v1.EnchantmentEvents;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * 26.x part of the CI test mod: exercises Fabricated's native fabric-item-api-v1 through NeoForge's own item hooks
 * (equipment slot, crafting remainder, enchanting check, custom damage, default components, item id, creator mod), and
 * its modify event for enchantments as the data pack loads them.
 */
public final class ItemApiTest implements ModInitializer {
	private static final List<EquipmentSlot> DAMAGED_IN = new ArrayList<>();
	private static volatile @Nullable String sharpnessSource;

	/** A Fabric item overriding FabricItem's methods. */
	private static final class TestItem extends Item {
		TestItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public @Nullable ItemStackTemplate getCraftingRemainder(ItemStack stack) {
			return Items.MILK_BUCKET.getCraftingRemainder();
		}

		@Override
		public String getCreatorNamespace(ItemStack stack) {
			return "fabricated_creator";
		}
	}

	@Override
	public void onInitialize() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("fabricated_testmod", "item_api_item"));
		Item.Properties properties = new Item.Properties().setId(key).durability(100)
				.equipmentSlot((entity, stack) -> EquipmentSlot.HEAD)
				.customDamage((stack, amount, entity, slot, onBroken) -> {
					DAMAGED_IN.add(slot);
					return 0;
				});
		boolean idOk = key.equals(properties.itemId());
		Item item = Registry.register(BuiltInRegistries.ITEM, key, new TestItem(properties));
		// Its predicate reads every item's components, as Wover's does: upstream tests it once they are bound (ITEMCOMP-1).
		DefaultItemComponentEvents.MODIFY.register(context -> context.modify(candidate -> candidate.components().has(DataComponents.MAX_DAMAGE) && candidate == item,
				(builder, modified) -> builder.set(DataComponents.RARITY, Rarity.EPIC)));
		EnchantmentEvents.ALLOW_ENCHANTING.register((enchantment, stack, context) -> stack.is(item) ? TriState.TRUE : TriState.DEFAULT);
		EnchantmentEvents.MODIFY_WITH_LOOKUP.register((enchantment, builder, source, registries) -> {
			if (!enchantment.equals(Enchantments.SHARPNESS)) return;
			sharpnessSource = source.name();
			builder.withEffect(EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE);
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel level = server.overworld();
			List<String> failed = new ArrayList<>();
			ItemStack stack = new ItemStack(item);
			if (!idOk) failed.add("item_id");
			if (stack.getEquipmentSlot() != EquipmentSlot.HEAD) failed.add("equipment_slot");
			if (!Objects.equals(item.getCraftingRemainder((ItemInstance) stack), Items.MILK_BUCKET.getCraftingRemainder())) failed.add("crafting_remainder");
			// The stack's own, which both Fabric's FabricItemStack and NeoForge's ItemInstanceExtension declare.
			if (!Objects.equals(stack.getCraftingRemainder(), Items.MILK_BUCKET.getCraftingRemainder())
					|| !Objects.equals(((net.fabricmc.fabric.api.item.v1.FabricItemStack) stack).getCraftingRemainder(), Items.MILK_BUCKET.getCraftingRemainder())) {
				failed.add("stack_crafting_remainder");
			}
			Holder<Enchantment> sharpness = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
			if (!stack.supportsEnchantment(sharpness) || new ItemStack(Items.STICK).supportsEnchantment(sharpness)) failed.add("allow_enchanting");
			if (stack.get(DataComponents.RARITY) != Rarity.EPIC) failed.add("default_components");
			if (!"VANILLA".equals(sharpnessSource) || !sharpness.value().effects().has(EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) failed.add("enchantment_modify");
			if (!"fabricated_creator".equals(item.getCreatorModId(server.registryAccess(), stack))) failed.add("creator_namespace");
			// Vanilla hands the entity to the damage hook only for players.
			Player player = FakePlayerFactory.getMinecraft(level);
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
