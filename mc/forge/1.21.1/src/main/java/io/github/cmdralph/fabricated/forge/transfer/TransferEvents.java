package io.github.cmdralph.fabricated.forge.transfer;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** {@link TransferBridge} on Forge 52's attach events (EventBus 6: one generic event, by the kind of object). */
final class TransferEvents {
	private TransferEvents() {
	}

	/** Last, so a Forge mod's own capability for a Fabric mod's block entity comes first. */
	static void attachLast() {
		MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, EventPriority.LOWEST, false, AttachCapabilitiesEvent.class, TransferEvents::attach);
		MinecraftForge.EVENT_BUS.addGenericListener(ItemStack.class, EventPriority.LOWEST, false, AttachCapabilitiesEvent.class, TransferEvents::attachToStack);
	}

	private static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
		FabricStorageProvider provider = TransferBridge.provider(event.getObject());
		if (provider == null) return;
		event.addCapability(TransferBridge.PROVIDER, provider);
		event.addListener(provider::invalidate);
	}

	private static void attachToStack(AttachCapabilitiesEvent<ItemStack> event) {
		if (TransferBridge.isFabric(event.getObject())) TransferBridge.attachToStack(event.getObject(), event::addCapability);
	}
}
