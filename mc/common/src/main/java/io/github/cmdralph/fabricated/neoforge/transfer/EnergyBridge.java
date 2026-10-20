package io.github.cmdralph.fabricated.neoforge.transfer;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup.BlockApiProvider;
import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.impl.lookup.block.BlockApiLookupImpl;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import team.reborn.energy.api.EnergyStorage;

/**
 * Lets NeoForge and Fabric code reach each other's energy storages when a Fabric mod brings Team Reborn's energy API,
 * the one Fabric tech mods share: on blocks through {@code Capabilities.Energy.BLOCK} and its {@code SIDED} lookup, in
 * items through {@code Capabilities.Energy.ITEM} and its {@code ITEM} lookup, as {@link TransferBridge} does for items
 * and fluids. One unit is one unit: neither API fixes an exchange rate. Loaded only when the library is present.
 */
final class EnergyBridge {
	/** The library's lookups, found by id: the same instances it creates, whichever is asked for first. */
	private static final BlockApiLookup<EnergyStorage, @Nullable Direction> SIDED =
			BlockApiLookup.get(Identifier.fromNamespaceAndPath("teamreborn", "sided_energy"), EnergyStorage.class, Direction.class);
	private static final ItemApiLookup<EnergyStorage, ContainerItemContext> ITEM =
			ItemApiLookup.get(Identifier.fromNamespaceAndPath("teamreborn", "energy"), EnergyStorage.class, ContainerItemContext.class);

	private EnergyBridge() {
	}

	static void register(IEventBus modBus) {
		modBus.addListener(EventPriority.LOWEST, RegisterCapabilitiesEvent.class, EnergyBridge::registerCapabilities);
		((BlockApiLookupImpl<EnergyStorage, @Nullable Direction>) SIDED).getFallbackProviders().add(0, (level, pos, state, blockEntity, side) -> {
			if (TransferBridge.isVanilla(state.getBlock())) return null;
			EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, pos, state, blockEntity, side);
			return handler == null ? null : storage(handler);
		});
		ITEM.registerFallback((stack, context) -> {
			if (TransferBridge.isVanilla(stack.getItem())) return null;
			ItemAccess access = context instanceof AccessItemContext neoAccess ? neoAccess.access : new ContextItemAccess(context);
			EnergyHandler handler = TransferBridge.askOtherSide(() -> stack.getCapability(Capabilities.Energy.ITEM, access));
			return handler == null ? null : storage(handler);
		});
	}

	private static void registerCapabilities(RegisterCapabilitiesEvent event) {
		Block[] blocks = TransferBridge.moddedBlocks();
		if (blocks.length > 0) {
			event.registerBlock(Capabilities.Energy.BLOCK, (level, pos, state, blockEntity, side) -> {
				BlockApiProvider<EnergyStorage, @Nullable Direction> provider = SIDED.getProvider(state.getBlock());
				EnergyStorage storage = provider == null ? null : provider.find(level, pos, state, blockEntity, side);
				return storage == null ? null : handler(storage);
			}, blocks);
		}
		Item[] items = TransferBridge.moddedItems();
		if (items.length > 0) {
			event.registerItem(Capabilities.Energy.ITEM, (stack, access) -> {
				ContainerItemContext context = access instanceof ContextItemAccess fabricContext ? fabricContext.context() : new AccessItemContext(access);
				EnergyStorage storage = TransferBridge.askOtherSide(() -> ITEM.find(stack, context));
				return storage == null ? null : handler(storage);
			}, items);
		}
	}

	private static EnergyHandler handler(EnergyStorage storage) {
		return storage instanceof NeoEnergyStorage bridged ? bridged.handler() : new FabricEnergyHandler(storage);
	}

	private static EnergyStorage storage(EnergyHandler handler) {
		return handler instanceof FabricEnergyHandler bridged ? bridged.storage() : new NeoEnergyStorage(handler);
	}

	/** A Fabric mod's energy storage seen as a NeoForge energy handler. */
	private record FabricEnergyHandler(EnergyStorage storage) implements EnergyHandler {
		@Override
		public long getAmountAsLong() {
			return storage.getAmount();
		}

		@Override
		public long getCapacityAsLong() {
			return storage.getCapacity();
		}

		@Override
		public int insert(int amount, TransactionContext transaction) {
			return (int) storage.insert(amount, FabricTransactions.of(transaction));
		}

		@Override
		public int extract(int amount, TransactionContext transaction) {
			return (int) storage.extract(amount, FabricTransactions.of(transaction));
		}
	}

	/** A NeoForge mod's energy handler seen as a Fabric energy storage. */
	private record NeoEnergyStorage(EnergyHandler handler) implements EnergyStorage {
		@Override
		public long insert(long maxAmount, net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
			return handler.insert((int) Math.min(Integer.MAX_VALUE, maxAmount), NeoTransactions.of(transaction));
		}

		@Override
		public long extract(long maxAmount, net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
			return handler.extract((int) Math.min(Integer.MAX_VALUE, maxAmount), NeoTransactions.of(transaction));
		}

		@Override
		public long getAmount() {
			return handler.getAmountAsLong();
		}

		@Override
		public long getCapacity() {
			return handler.getCapacityAsLong();
		}
	}
}
