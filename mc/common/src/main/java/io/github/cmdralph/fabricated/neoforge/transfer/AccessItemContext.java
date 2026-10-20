package io.github.cmdralph.fabricated.neoforge.transfer;

import java.util.List;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * NeoForge's access to where an item is, as a Fabric container item context: for a Fabric mod's item storage that
 * NeoForge code uses. Inserting goes where the access puts it (its own slot first, then elsewhere, such as the
 * player's inventory), which is what a context's insertion and overflow do together; there are no further slots.
 */
final class AccessItemContext implements ContainerItemContext {
	final ItemAccess access;
	private final SingleSlotStorage<ItemVariant> mainSlot = new SingleSlotStorage<>() {
		@Override
		public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
			return AccessItemContext.this.insert(resource, maxAmount, transaction);
		}

		@Override
		public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
			return AccessItemContext.this.extract(resource, maxAmount, transaction);
		}

		@Override
		public boolean isResourceBlank() {
			return access.getResource().isEmpty();
		}

		@Override
		public ItemVariant getResource() {
			return ResourceKind.ITEMS.fabric(access.getResource());
		}

		@Override
		public long getAmount() {
			return access.getAmount();
		}

		@Override
		public long getCapacity() {
			ItemResource resource = access.getResource();
			return resource.isEmpty() ? Item.DEFAULT_MAX_STACK_SIZE : Math.max(access.getAmount(), resource.getMaxStackSize());
		}
	};

	private AccessItemContext(ItemAccess access) {
		this.access = access;
	}

	/** The access as a Fabric context: the context it stands for, when it is one of Fabric's. */
	static ContainerItemContext of(ItemAccess access) {
		return access instanceof ContextItemAccess fabricContext ? fabricContext.context() : new AccessItemContext(access);
	}

	@Override
	public long insert(ItemVariant itemVariant, long maxAmount, TransactionContext transaction) {
		StoragePreconditions.notBlankNotNegative(itemVariant, maxAmount);
		return access.insert(ResourceKind.ITEMS.neo(itemVariant), ResourceKind.ITEMS.toNeoAmount(maxAmount), NeoTransactions.of(transaction));
	}

	@Override
	public long extract(ItemVariant itemVariant, long maxAmount, TransactionContext transaction) {
		StoragePreconditions.notBlankNotNegative(itemVariant, maxAmount);
		return access.extract(ResourceKind.ITEMS.neo(itemVariant), ResourceKind.ITEMS.toNeoAmount(maxAmount), NeoTransactions.of(transaction));
	}

	@Override
	public SingleSlotStorage<ItemVariant> getMainSlot() {
		return mainSlot;
	}

	@Override
	public long insertOverflow(ItemVariant itemVariant, long maxAmount, TransactionContext transactionContext) {
		return insert(itemVariant, maxAmount, transactionContext);
	}

	@Override
	public List<SingleSlotStorage<ItemVariant>> getAdditionalSlots() {
		return List.of();
	}
}
