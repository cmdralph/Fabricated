package io.github.cmdralph.fabricated.forge.transfer;

import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.listener.Priority;

/** {@link TransferBridge} on Forge 61 to 66's attach events (EventBus 7: an event per kind of object). */
final class TransferEvents {
	private TransferEvents() {
	}

	/** Last, so a Forge mod's own capability for a Fabric mod's block entity comes first. */
	static void attachLast() {
		AttachCapabilitiesEvent.BlockEntities.BUS.addListener(Priority.LOWEST, event -> {
			FabricStorageProvider provider = TransferBridge.provider(event.getObject());
			if (provider == null) return;
			event.addCapability(TransferBridge.PROVIDER, provider);
			event.addListener(provider::invalidate);
		});
		AttachCapabilitiesEvent.ItemStacks.BUS.addListener(Priority.LOWEST, event -> {
			if (TransferBridge.isFabric(event.getObject())) TransferBridge.attachToStack(event.getObject(), event::addCapability);
		});
	}
}
