package io.github.cmdralph.fabricated.neoforge.item;

import java.util.Objects;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.item.v1.CustomDamageHandler;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.fabricmc.fabric.impl.item.ComponentTooltipAppenderRegistryImpl;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TooltipProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Native {@code fabric-item-api-v1} (1.21.11): the events, and helpers for Fabricated's item mixins. Fabric's default
 * component changes become NeoForge component patches; Fabric's tooltip callback and tooltip appenders run from
 * NeoForge's {@link ItemTooltipEvent}.
 */
public final class ItemApiBridge {
	private static volatile Supplier<@Nullable Player> clientPlayer = () -> null;

	private static final ClassValue<Boolean> OVERRIDES_CREATOR_NAMESPACE = new ClassValue<>() {
		@Override
		protected Boolean computeValue(Class<?> type) {
			try {
				return type.getMethod("getCreatorNamespace", ItemStack.class).getDeclaringClass() != FabricItem.class;
			} catch (NoSuchMethodException e) {
				return false;
			}
		}
	};

	private ItemApiBridge() {
	}

	public static void register(IEventBus modBus, Dist dist) {
		modBus.addListener(ModifyDefaultComponentsEvent.class, ItemApiBridge::modifyDefaultComponents);
		if (dist.isClient()) {
			clientPlayer = ClientItemApi::player;
			NeoForge.EVENT_BUS.addListener(ItemTooltipEvent.class, ItemApiBridge::onTooltip);
		}
	}

	/** Fabric edits a full component map; NeoForge 1.21.11 takes a patch, so the difference is handed over. */
	private static void modifyDefaultComponents(ModifyDefaultComponentsEvent event) {
		DefaultItemComponentEvents.MODIFY.invoker().modify((predicate, consumer) -> event.getAllItems().filter(predicate).forEach(item -> {
			DataComponentMap before = item.components();
			DataComponentMap.Builder builder = DataComponentMap.builder().addAll(before);
			consumer.accept(builder, item);
			DataComponentMap after = builder.build();
			event.modify(item, patch -> {
				for (TypedDataComponent<?> component : after) {
					if (!Objects.equals(before.get(component.type()), component.value())) set(patch, component);
				}
				for (TypedDataComponent<?> component : before) {
					if (!after.has(component.type())) patch.remove(component.type());
				}
			});
		}));
	}

	private static <T> void set(DataComponentPatch.Builder patch, TypedDataComponent<T> component) {
		patch.set(component.type(), component.value());
	}

	private static void onTooltip(ItemTooltipEvent event) {
		ItemStack stack = event.getItemStack();
		TooltipDisplay display = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
		for (DataComponentType<? extends TooltipProvider> type : ComponentTooltipAppenderRegistryImpl.inOrder()) {
			TooltipProvider provider = stack.get(type);
			if (provider != null && display.shows(type)) {
				provider.addToTooltip(event.getContext(), event.getToolTip()::add, event.getFlags(), stack);
			}
		}
		ItemTooltipCallback.EVENT.invoker().getTooltip(stack, event.getContext(), event.getFlags(), event.getToolTip());
	}

	public static @Nullable Player clientPlayer() {
		return clientPlayer.get();
	}

	public static boolean overridesCreatorNamespace(Class<?> itemClass) {
		return OVERRIDES_CREATOR_NAMESPACE.get(itemClass);
	}

	/** Fabric's custom damage handler, as upstream: not asked for creative players (or without an entity). */
	public static int customDamage(CustomDamageHandler handler, ItemStack stack, int amount, @Nullable LivingEntity entity, Runnable onBroken) {
		if (entity == null || entity.hasInfiniteMaterials()) return amount;
		boolean[] broken = {false};
		int result = handler.damage(stack, amount, entity, slotOf(entity, stack), () -> {
			broken[0] = true;
			stack.shrink(1);
			onBroken.run();
		});
		return broken[0] ? 0 : result;
	}

	private static EquipmentSlot slotOf(LivingEntity entity, ItemStack stack) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (entity.getItemBySlot(slot) == stack) return slot;
		}
		return EquipmentSlot.MAINHAND;
	}
}
