package io.github.cmdralph.fabricated.neoforge.item;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.item.v1.CustomDamageHandler;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Native {@code fabric-item-api-v1} (26.x): the events, and helpers for Fabricated's item mixins. Fabric's default
 * component changes go through NeoForge's {@link ModifyDefaultComponentsEvent}; tooltip providers and item click
 * behaviour through {@link ItemApiCompat} (they differ per version); Fabric's client tooltip callback is fired from
 * NeoForge's {@link ItemTooltipEvent}.
 */
public final class ItemApiBridge {
	private static volatile Supplier<@Nullable Player> clientPlayer = () -> null;

	/** Whether an item class overrides Fabric's {@code getCreatorNamespace} (otherwise NeoForge's answer is kept). */
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
		modBus.addListener(ModifyDefaultComponentsEvent.class, event -> DefaultItemComponentEvents.MODIFY.invoker().modify(
				(predicate, consumer) -> event.modifyMatching((item, components) -> predicate.test(item), consumer::modify)));
		ItemApiCompat.register(modBus);
		if (dist.isClient()) {
			clientPlayer = ClientItemApi::player;
			NeoForge.EVENT_BUS.addListener(ItemTooltipEvent.class, event -> ItemTooltipCallback.EVENT.invoker()
					.getTooltip(event.getItemStack(), event.getContext(), event.getFlags(), event.getToolTip()));
		}
	}

	/** The client's player, for Fabric's client-side item hooks; null on a server. */
	public static @Nullable Player clientPlayer() {
		return clientPlayer.get();
	}

	public static boolean overridesCreatorNamespace(Class<?> itemClass) {
		return OVERRIDES_CREATOR_NAMESPACE.get(itemClass);
	}

	/**
	 * Fabric's custom damage handler, as upstream: not asked for creative players (or without an entity); breaking the
	 * stack through the callback ends the damage there.
	 */
	public static int customDamage(CustomDamageHandler handler, ItemStack stack, int amount, @Nullable LivingEntity entity, Runnable onBroken) {
		if (entity == null || entity.hasInfiniteMaterials()) return amount;
		boolean[] broken = {false};
		int result = handler.hurtAndBreak(stack, amount, entity, slotOf(entity, stack), () -> {
			broken[0] = true;
			stack.shrink(1);
			onBroken.run();
		});
		return broken[0] ? 0 : result;
	}

	/** NeoForge's damage hook doesn't name the slot; it's the one holding this very stack (the main hand otherwise). */
	private static EquipmentSlot slotOf(LivingEntity entity, ItemStack stack) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (entity.getItemBySlot(slot) == stack) return slot;
		}
		return EquipmentSlot.MAINHAND;
	}
}
