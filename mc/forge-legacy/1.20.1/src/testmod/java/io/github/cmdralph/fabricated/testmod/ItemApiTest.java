package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.item.v1.ModifyItemAttributeModifiersCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1.20.1 part of the CI test mod: exercises Fabric API's fabric-item-api-v1 through Forge 47's own item hooks (equipment
 * slot, crafting remainder, tool check, attribute modifiers and their callback, custom damage).
 */
public final class ItemApiTest implements ModInitializer {
	private static final UUID SPEED = UUID.fromString("5d3c2b1a-0000-4000-8000-00000000f001");
	private static final UUID LUCK = UUID.fromString("5d3c2b1a-0000-4000-8000-00000000f002");
	private static final List<LivingEntity> DAMAGED_BY = new ArrayList<>();

	/** A Fabric item overriding FabricItem's methods, which Fabric's item interface gives every item. */
	private static final class TestItem extends Item implements FabricItem {
		TestItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public ItemStack getRecipeRemainder(ItemStack stack) {
			return new ItemStack(Items.BUCKET);
		}

		@Override
		public boolean isSuitableFor(ItemStack stack, BlockState state) {
			return state.is(Blocks.OBSIDIAN);
		}

		@Override
		public Multimap<Attribute, AttributeModifier> getAttributeModifiers(ItemStack stack, EquipmentSlot slot) {
			if (slot != EquipmentSlot.MAINHAND) return ImmutableMultimap.of();
			return ImmutableMultimap.of(Attributes.MOVEMENT_SPEED, new AttributeModifier(SPEED, "fabricated test speed", 0.1, AttributeModifier.Operation.ADDITION));
		}
	}

	@Override
	public void onInitialize() {
		FabricItemSettings settings = new FabricItemSettings().equipmentSlot(stack -> EquipmentSlot.HEAD).customDamage((stack, amount, entity, onBroken) -> {
			DAMAGED_BY.add(entity);
			return 0;
		});
		Item item = Registry.register(BuiltInRegistries.ITEM, TestCompat.id("fabricated_testmod", "item_api_item"), new TestItem(settings.durability(100)));
		ModifyItemAttributeModifiersCallback.EVENT.register((stack, slot, modifiers) -> {
			if (stack.is(item) && slot == EquipmentSlot.MAINHAND) {
				modifiers.put(Attributes.LUCK, new AttributeModifier(LUCK, "fabricated test luck", 1, AttributeModifier.Operation.ADDITION));
			}
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			List<String> failed = new ArrayList<>();
			ItemStack stack = new ItemStack(item);
			if (LivingEntity.getEquipmentSlotForItem(stack) != EquipmentSlot.HEAD || stack.getEquipmentSlot() != EquipmentSlot.HEAD) failed.add("equipment_slot");
			// Forge's crafting, furnaces and brewing stands ask the stack, as vanilla's remainder still answers.
			if (!stack.hasCraftingRemainingItem() || !ItemStack.matches(stack.getCraftingRemainingItem(), new ItemStack(Items.BUCKET))
					|| !ItemStack.matches(new ItemStack(Items.MILK_BUCKET).getCraftingRemainingItem(), new ItemStack(Items.BUCKET))) failed.add("crafting_remainder");
			// Forge's tools sort tiers in their own answer, which stands where Fabric's method isn't overridden.
			if (!stack.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()) || stack.isCorrectToolForDrops(Blocks.STONE.defaultBlockState())
					|| !new ItemStack(Items.DIAMOND_PICKAXE).isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState())) failed.add("suitable_for");
			Multimap<Attribute, AttributeModifier> modifiers = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
			if (!has(modifiers, Attributes.MOVEMENT_SPEED, SPEED) || !has(modifiers, Attributes.LUCK, LUCK)) failed.add("attribute_modifiers");
			// Vanilla hands the entity to the damage hook only for players.
			Player player = FakePlayer.get(server.overworld());
			stack.hurtAndBreak(5, player, broken -> {
			});
			if (!DAMAGED_BY.equals(List.of(player)) || stack.getDamageValue() != 0) failed.add("custom_damage");
			TestMod.report("item api", failed, "fabric-item-api-v1", Map.of());
		});
	}

	private static boolean has(Multimap<Attribute, AttributeModifier> modifiers, Attribute attribute, UUID id) {
		return modifiers.get(attribute).stream().anyMatch(modifier -> modifier.getId().equals(id));
	}
}
