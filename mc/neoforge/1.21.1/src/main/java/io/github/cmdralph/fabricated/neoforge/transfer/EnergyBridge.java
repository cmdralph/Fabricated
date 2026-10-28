package io.github.cmdralph.fabricated.neoforge.transfer;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.collect.MapMaker;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.fabricmc.fabric.impl.lookup.block.BlockApiLookupImpl;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import team.reborn.energy.api.EnergyStorage;

/**
 * Lets NeoForge 21.1 and Fabric code reach each other's energy storages when a Fabric mod brings Team Reborn's energy
 * API, the one Fabric tech mods share: on blocks through the {@code EnergyStorage.BLOCK} capability and the library's
 * {@code SIDED} lookup, in items through {@code EnergyStorage.ITEM} and its {@code ITEM} lookup, as
 * {@link TransferBridge} does for items and fluids, with the same rules: NeoForge's handlers simulate while Fabric's
 * transaction is open and move once it commits ({@link NeoEnergyStorage}), Fabric's storages run each NeoForge call in
 * a transaction of its own ({@link FabricEnergyHandler}), and an item's energy changes the item by replacing it. One
 * unit is one unit: neither API fixes an exchange rate. Loaded only when the library is present.
 */
final class EnergyBridge {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	/** The library's lookups, found by id: the same instances it creates, whichever is asked for first. */
	private static final BlockApiLookup<EnergyStorage, @Nullable Direction> SIDED =
			BlockApiLookup.get(ResourceLocation.fromNamespaceAndPath("teamreborn", "sided_energy"), EnergyStorage.class, Direction.class);
	private static final ItemApiLookup<EnergyStorage, ContainerItemContext> ITEM =
			ItemApiLookup.get(ResourceLocation.fromNamespaceAndPath("teamreborn", "energy"), EnergyStorage.class, ContainerItemContext.class);
	/** The storage of each block's handler, for as long as either is in use: a transaction holds the storages it changed. */
	private static final Map<IEnergyStorage, NeoEnergyStorage> STORAGES = new MapMaker().weakKeys().weakValues().makeMap();
	/** The handler classes already logged as moving less than they promised. */
	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

	private EnergyBridge() {
	}

	static void register() {
		// Fabric's fallbacks for NeoForge mods' blocks and items: NeoForge's capabilities.
		((BlockApiLookupImpl<EnergyStorage, @Nullable Direction>) SIDED).getFallbackProviders().add(0, (level, pos, state, blockEntity, side) -> {
			if (!TransferBridge.isNeoForge(state.getBlock())) return null;
			IEnergyStorage handler = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, state, blockEntity, side);
			if (handler == null) return null;
			return handler instanceof FabricEnergyHandler bridged ? bridged.storage() : STORAGES.computeIfAbsent(handler, NeoEnergyStorage::new);
		});
		ITEM.registerFallback((stack, context) -> TransferBridge.isNeoForge(stack.getItem())
				&& stack.getCapability(Capabilities.EnergyStorage.ITEM) != null ? new NeoItemEnergyStorage(context) : null);
	}

	/** NeoForge's energy capabilities for Fabric mods' blocks and items, from the library's whole lookups. */
	static void registerCapabilities(RegisterCapabilitiesEvent event, Block[] blocks, Item[] items) {
		if (blocks.length > 0) {
			event.registerBlock(Capabilities.EnergyStorage.BLOCK, (level, pos, state, blockEntity, side) -> {
				EnergyStorage storage = SIDED.find(level, pos, state, blockEntity, side);
				if (storage == null) return null;
				return storage instanceof NeoEnergyStorage bridged ? bridged.handler : new FabricEnergyHandler(storage);
			}, blocks);
		}
		if (items.length > 0) event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, context) -> FabricItemEnergyHandler.of(stack), items);
	}

	/** A Fabric mod's energy storage seen as a NeoForge energy handler: each call in a transaction of its own. */
	private record FabricEnergyHandler(EnergyStorage storage) implements IEnergyStorage {
		@Override
		public int receiveEnergy(int toReceive, boolean simulate) {
			if (toReceive <= 0) return 0;
			return (int) FabricTransactions.run(simulate, transaction -> storage.insert(toReceive, transaction));
		}

		@Override
		public int extractEnergy(int toExtract, boolean simulate) {
			if (toExtract <= 0) return 0;
			return (int) FabricTransactions.run(simulate, transaction -> storage.extract(toExtract, transaction));
		}

		@Override
		public int getEnergyStored() {
			return (int) Math.min(storage.getAmount(), Integer.MAX_VALUE);
		}

		@Override
		public int getMaxEnergyStored() {
			return (int) Math.min(storage.getCapacity(), Integer.MAX_VALUE);
		}

		@Override
		public boolean canExtract() {
			return storage.supportsExtraction();
		}

		@Override
		public boolean canReceive() {
			return storage.supportsInsertion();
		}
	}

	/**
	 * A Fabric mod's energy storage in an item, seen as NeoForge's energy handler of the item: the storage of a slot
	 * holding the container ({@link ContainerSlot}), found anew for each call, as the item may have changed since.
	 */
	private static final class FabricItemEnergyHandler implements IEnergyStorage {
		private final ContainerItemContext context;

		private FabricItemEnergyHandler(ItemStack container) {
			context = ContainerItemContext.ofSingleSlot(new ContainerSlot(container));
		}

		static @Nullable FabricItemEnergyHandler of(ItemStack container) {
			FabricItemEnergyHandler handler = new FabricItemEnergyHandler(container);
			return handler.storage() == null ? null : handler;
		}

		private @Nullable EnergyStorage storage() {
			return context.find(ITEM);
		}

		private IEnergyStorage handler() {
			EnergyStorage storage = storage();
			return storage == null ? EmptyEnergy.INSTANCE : new FabricEnergyHandler(storage);
		}

		@Override
		public int receiveEnergy(int toReceive, boolean simulate) {
			return handler().receiveEnergy(toReceive, simulate);
		}

		@Override
		public int extractEnergy(int toExtract, boolean simulate) {
			return handler().extractEnergy(toExtract, simulate);
		}

		@Override
		public int getEnergyStored() {
			return handler().getEnergyStored();
		}

		@Override
		public int getMaxEnergyStored() {
			return handler().getMaxEnergyStored();
		}

		@Override
		public boolean canExtract() {
			return handler().canExtract();
		}

		@Override
		public boolean canReceive() {
			return handler().canReceive();
		}
	}

	/** No energy: what an item's handler is once its item has no storage any more. */
	private enum EmptyEnergy implements IEnergyStorage {
		INSTANCE;

		@Override
		public int receiveEnergy(int toReceive, boolean simulate) {
			return 0;
		}

		@Override
		public int extractEnergy(int toExtract, boolean simulate) {
			return 0;
		}

		@Override
		public int getEnergyStored() {
			return 0;
		}

		@Override
		public int getMaxEnergyStored() {
			return 0;
		}

		@Override
		public boolean canExtract() {
			return false;
		}

		@Override
		public boolean canReceive() {
			return false;
		}
	}

	/**
	 * A NeoForge mod's block energy handler seen as a Fabric energy storage. As {@link NeoItemStorage}'s slots, it only
	 * asks the handler, simulating, how much of everything moved so far it would take or give while a transaction is
	 * open, and moves that once the outermost one commits: one way per transaction.
	 */
	private static final class NeoEnergyStorage extends SnapshotParticipant<Long> implements EnergyStorage {
		final IEnergyStorage handler;
		/** Energy pending: received (positive) or extracted (negative). */
		private long delta;

		NeoEnergyStorage(IEnergyStorage handler) {
			this.handler = handler;
		}

		@Override
		public boolean supportsInsertion() {
			return handler.canReceive();
		}

		@Override
		public boolean supportsExtraction() {
			return handler.canExtract();
		}

		@Override
		public long insert(long maxAmount, TransactionContext transaction) {
			if (maxAmount <= 0 || delta < 0) return 0;
			int asked = (int) Math.min(delta + maxAmount, Integer.MAX_VALUE);
			if (asked <= delta) return 0;
			long accepted = Math.min(handler.receiveEnergy(asked, true) - delta, maxAmount);
			if (accepted <= 0) return 0;
			updateSnapshots(transaction);
			delta += accepted;
			return accepted;
		}

		@Override
		public long extract(long maxAmount, TransactionContext transaction) {
			if (maxAmount <= 0 || delta > 0) return 0;
			long pending = -delta;
			int asked = (int) Math.min(pending + maxAmount, Integer.MAX_VALUE);
			if (asked <= pending) return 0;
			long extracted = Math.min(handler.extractEnergy(asked, true) - pending, maxAmount);
			if (extracted <= 0) return 0;
			updateSnapshots(transaction);
			delta -= extracted;
			return extracted;
		}

		@Override
		public long getAmount() {
			return Math.max(handler.getEnergyStored() + delta, 0);
		}

		@Override
		public long getCapacity() {
			return handler.getMaxEnergyStored();
		}

		@Override
		protected Long createSnapshot() {
			return delta;
		}

		@Override
		protected void readSnapshot(Long snapshot) {
			delta = snapshot;
		}

		/** Moves what the committed transactions left pending, now that none is open. */
		@Override
		protected void onFinalCommit() {
			long amount = delta;
			delta = 0;
			if (amount > 0) {
				if (handler.receiveEnergy((int) amount, false) < amount) warn("took less energy");
			} else if (amount < 0) {
				if (handler.extractEnergy((int) -amount, false) < -amount) warn("gave less energy");
			}
		}

		private void warn(String what) {
			if (WARNED.add(handler.getClass().getName())) {
				LOGGER.warn("NeoForge energy handler {} {} than it said it would when simulating; Fabric's transaction had already committed",
						handler.getClass().getName(), what);
			}
		}
	}

	/**
	 * A NeoForge mod's energy handler item seen as a Fabric energy storage in a context, as {@link NeoItemFluidStorage}
	 * for fluids: the handler acts on a copy of one of the context's items, and what it makes of the copy replaces that
	 * item in the context, in the transaction. A change that doesn't show in the item is refused.
	 */
	private record NeoItemEnergyStorage(ContainerItemContext context) implements EnergyStorage {
		/** The handler's stack, a copy of one of the context's items as it is now, and the handler acting on it. */
		private record Copy(ItemStack stack, IEnergyStorage handler) {
		}

		private @Nullable Copy copy() {
			ItemVariant item = context.getItemVariant();
			if (item.isBlank() || context.getAmount() < 1) return null;
			ItemStack stack = item.toStack(1);
			IEnergyStorage handler = stack.getCapability(Capabilities.EnergyStorage.ITEM);
			return handler == null ? null : new Copy(stack, handler);
		}

		@Override
		public boolean supportsInsertion() {
			Copy copy = copy();
			return copy != null && copy.handler().canReceive();
		}

		@Override
		public boolean supportsExtraction() {
			Copy copy = copy();
			return copy != null && copy.handler().canExtract();
		}

		@Override
		public long insert(long maxAmount, TransactionContext transaction) {
			Copy copy = copy();
			if (copy == null || maxAmount <= 0) return 0;
			int received = copy.handler().receiveEnergy((int) Math.min(maxAmount, Integer.MAX_VALUE), false);
			return received > 0 && replace(copy.stack(), transaction) ? received : 0;
		}

		@Override
		public long extract(long maxAmount, TransactionContext transaction) {
			Copy copy = copy();
			if (copy == null || maxAmount <= 0) return 0;
			int extracted = copy.handler().extractEnergy((int) Math.min(maxAmount, Integer.MAX_VALUE), false);
			return extracted > 0 && replace(copy.stack(), transaction) ? extracted : 0;
		}

		/** Puts what the handler made of its copy in place of one of the context's items. */
		private boolean replace(ItemStack result, TransactionContext transaction) {
			ItemVariant after = ItemVariant.of(result);
			if (result.getCount() != 1 || after.equals(context.getItemVariant())) return false;
			return context.exchange(after, 1, transaction) == 1;
		}

		@Override
		public long getAmount() {
			Copy copy = copy();
			return copy == null ? 0 : copy.handler().getEnergyStored();
		}

		@Override
		public long getCapacity() {
			Copy copy = copy();
			return copy == null ? 0 : copy.handler().getMaxEnergyStored();
		}
	}
}
