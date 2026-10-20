package io.github.cmdralph.fabricated.neoforge.transfer;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A Fabric container item context as NeoForge's access to where an item is: for a NeoForge mod's item handler that
 * Fabric code uses. Inserting goes through the context's main slot first, then its overflow, as NeoForge's access
 * may put an item elsewhere when its own slot can't take it.
 */
record ContextItemAccess(ContainerItemContext context) implements ItemAccess {
	@Override
	public ItemResource getResource() {
		return ResourceKind.ITEMS.neo(context.getItemVariant());
	}

	@Override
	public int getAmount() {
		ItemVariant variant = context.getItemVariant();
		return variant.isBlank() ? 0 : ResourceKind.ITEMS.toNeoAmount(context.getAmount());
	}

	@Override
	public int insert(ItemResource resource, int amount, TransactionContext transaction) {
		return (int) context.insert(ResourceKind.ITEMS.fabric(resource), amount, FabricTransactions.of(transaction));
	}

	@Override
	public int extract(ItemResource resource, int amount, TransactionContext transaction) {
		return (int) context.extract(ResourceKind.ITEMS.fabric(resource), amount, FabricTransactions.of(transaction));
	}
}
