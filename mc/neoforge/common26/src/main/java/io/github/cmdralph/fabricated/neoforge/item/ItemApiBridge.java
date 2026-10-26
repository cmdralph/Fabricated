package io.github.cmdralph.fabricated.neoforge.item;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.item.v1.CustomDamageHandler;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.fabricmc.fabric.impl.item.ItemComponentTooltipProviderRegistryImpl;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.tooltip.TooltipAppender;
import net.neoforged.neoforge.common.tooltip.TooltipLocation;
import net.neoforged.neoforge.event.DefaultDataComponentsBoundEvent;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Native {@code fabric-item-api-v1} (26.x): the events, and helpers for Fabricated's item mixins. Fabric's default
 * component changes are made as the items' default components are bound ({@link DefaultDataComponentsBoundEvent}) and
 * tooltip providers go through NeoForge's {@link RegisterTooltipAppendersEvent}; item click behaviour (26.2+) through {@link ItemApiCompat}; Fabric's client
 * tooltip callback is fired from NeoForge's {@link ItemTooltipEvent}.
 */
public final class ItemApiBridge {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static volatile Supplier<@Nullable Player> clientPlayer = () -> null;
	/** The registries the items' default component initializers last ran with: what Fabric's changes are given. */
	private static volatile HolderLookup.@Nullable Provider componentRegistries;

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
		// Only to learn the registries the initializers run with; Fabric's changes wait for the binding (ITEMCOMP-1).
		modBus.addListener(ModifyDefaultComponentsEvent.class, event -> event.modify(Items.STONE, (components, registries, item) -> componentRegistries = registries));
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, DefaultDataComponentsBoundEvent.class, ItemApiBridge::modifyDefaultComponents);
		modBus.addListener(RegisterTooltipAppendersEvent.class, ItemApiBridge::registerTooltipProviders);
		ItemApiCompat.register();
		if (dist.isClient()) {
			clientPlayer = ClientItemApi::player;
			NeoForge.EVENT_BUS.addListener(ItemTooltipEvent.class, event -> ItemTooltipCallback.EVENT.invoker()
					.getTooltip(event.getItemStack(), event.getContext(), event.getFlags(), event.getToolTip()));
		}
	}

	/**
	 * Fabric's default component changes, as upstream makes them once an item's default components are bound (read in
	 * its {@code DefaultItemComponentImpl} and {@code DataComponentInitializersPendingComponentsMixin}): a listener's
	 * predicate may read the item's components, as Wover's reads them; NeoForge's own event runs before the binding,
	 * where that throws ("Components not bound yet", which stopped the 26.3 advanced corpus server). Not on a client of
	 * its own integrated server, whose items the server already bound.
	 */
	private static void modifyDefaultComponents(DefaultDataComponentsBoundEvent event) {
		if (!event.shouldUpdateStaticData()) return;
		HolderLookup.Provider registries = componentRegistries;
		DefaultItemComponentEvents.MODIFY.invoker().modify((predicate, consumer) -> {
			for (Item item : BuiltInRegistries.ITEM) {
				if (!predicate.test(item)) continue;
				DataComponentMap.Builder builder = DataComponentMap.builder().addAll(item.components());
				consumer.modify(builder, registries, item);
				item.builtInRegistryHolder().bindComponents(builder.build());
			}
		});
	}

	/** Fabric's {@code ItemComponentTooltipProviderRegistry}, placed where NeoForge places component tooltips. */
	private static void registerTooltipProviders(RegisterTooltipAppendersEvent event) {
		var entries = ItemComponentTooltipProviderRegistryImpl.entries();
		// Providers registered after this event (on a client, main initializers run in Minecraft's constructor,
		// CORPUS-16) go before or after all component tooltips: the nearest places NeoForge still takes.
		event.registerAppender(TooltipLocation.POST_CUSTOM, lateProviders(entries.size(), true));
		event.registerAppender(TooltipLocation.PRE_ITEM_INFO, lateProviders(entries.size(), false));
		for (var entry : entries) {
			TooltipAppender appender = entry.appender();
			try {
				switch (entry.position()) {
					case FIRST -> event.registerComponentAppenderBeforeAll(entry.type(), appender);
					case LAST -> event.registerComponentAppenderAfterAll(entry.type(), appender);
					case BEFORE -> event.registerComponentAppenderBefore(entry.type(), entry.anchor(), appender);
					case AFTER -> event.registerComponentAppenderAfter(entry.type(), entry.anchor(), appender);
				}
			} catch (IllegalStateException e) {
				LOGGER.warn("Tooltip provider for component {} is registered more than once; the first registration is kept", entry.type());
			}
		}
	}

	/** The providers registered after the first {@code skip}: those placed first or before another, or the others. */
	private static TooltipAppender lateProviders(int skip, boolean before) {
		return (stack, context, display, player, flag, builder) -> {
			var entries = ItemComponentTooltipProviderRegistryImpl.entries();
			for (int i = skip; i < entries.size(); i++) {
				var entry = entries.get(i);
				ItemComponentTooltipProviderRegistryImpl.Position position = entry.position();
				boolean first = position == ItemComponentTooltipProviderRegistryImpl.Position.FIRST
						|| position == ItemComponentTooltipProviderRegistryImpl.Position.BEFORE;
				if (first == before) entry.appender().append(stack, context, display, player, flag, builder);
			}
		};
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
