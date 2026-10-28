package io.github.cmdralph.fabricated.neoforge.transfer;

import org.jspecify.annotations.Nullable;

import io.github.cmdralph.fabricated.core.metadata.ModIds;
import io.github.cmdralph.fabricated.loader.FabricatedState;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.impl.lookup.block.BlockApiLookupImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Lets NeoForge 21.1 and Fabric code reach each other's item and fluid storages on blocks, and fluid storages in items,
 * as later versions' bridge does over NeoForge's newer transfer API: a NeoForge hopper or pipe sees a Fabric mod's
 * storage through the {@code ItemHandler} and {@code FluidHandler} block capabilities, and a Fabric mod sees a NeoForge
 * mod's handler through {@code ItemStorage.SIDED} and {@code FluidStorage.SIDED}; a NeoForge machine fills a Fabric mod's
 * tank item through {@code FluidHandler.ITEM}, and a Fabric machine a NeoForge mod's through {@code FluidStorage.ITEM}.
 * Fluid amounts convert at 81 droplets per millibucket, whole millibuckets only. Items in items (Fabric's
 * {@code ItemStorage.ITEM}) aren't bridged on this version, as on Forge.
 *
 * <p>Each side answers for what its own mods add, told apart by namespace: NeoForge's capabilities for Fabric mods'
 * blocks and items come from Fabric's whole lookup, fallbacks included, and Fabric's lookups fall back to NeoForge's
 * capabilities for NeoForge mods' blocks and items. So neither side asks the other about the same block or item, and
 * nothing is wrapped back into itself. Vanilla's are left to each side's own wrappers.
 *
 * <p>NeoForge 21.1's handlers move resources at once, with a flag to only simulate; Fabric's storages move them in
 * transactions that may be undone. A NeoForge handler seen as a Fabric storage ({@link NeoItemStorage},
 * {@link NeoFluidStorage}) only simulates while the transaction is open and moves the result once the outermost one
 * commits; a Fabric storage seen as a NeoForge handler ({@link FabricItemHandler}, {@link FabricFluidHandler}) opens a
 * transaction for each call, committed unless the call simulates. An item's handler and storage
 * ({@link FabricItemFluidHandler}, {@link NeoItemFluidStorage}) change the item by replacing it, as both sides' own item
 * storages do.
 */
public final class TransferBridge {
	private TransferBridge() {
	}

	public static void register(IEventBus modBus) {
		// Last, so a NeoForge mod's own capability for a Fabric mod's block or item comes first.
		modBus.addListener(EventPriority.LOWEST, RegisterCapabilitiesEvent.class, TransferBridge::registerCapabilities);
		addFabricFallback(ItemStorage.SIDED, Capabilities.ItemHandler.BLOCK, TransferBridge::itemStorage);
		addFabricFallback(FluidStorage.SIDED, Capabilities.FluidHandler.BLOCK, TransferBridge::fluidStorage);
		// After upstream's fallback, which gives every bucket item Fabric's own bucket storage.
		FluidStorage.ITEM.registerFallback((stack, context) -> isNeoForge(stack.getItem())
				&& stack.getCapability(Capabilities.FluidHandler.ITEM) != null ? new NeoItemFluidStorage(context) : null);
		if (energy()) EnergyBridge.register();
	}

	/** Whether a Fabric mod brought Team Reborn's energy library (see {@link EnergyBridge}). */
	private static boolean energy() {
		return ModList.get().isLoaded(ModIds.toNeoForgeId("team_reborn_energy"));
	}

	/** Whether a NeoForge mod added the block: neither vanilla nor a Fabric mod (by namespace). */
	static boolean isNeoForge(Block block) {
		return isNeoForge(BuiltInRegistries.BLOCK.getKey(block).getNamespace());
	}

	static boolean isNeoForge(Item item) {
		return isNeoForge(BuiltInRegistries.ITEM.getKey(item).getNamespace());
	}

	private static boolean isNeoForge(String namespace) {
		return !namespace.equals("minecraft") && !isFabric(namespace);
	}

	private static boolean isFabric(String namespace) {
		return FabricatedState.get().isFabricMod(ModIds.toNeoForgeId(namespace));
	}

	private static void registerCapabilities(RegisterCapabilitiesEvent event) {
		Block[] blocks = BuiltInRegistries.BLOCK.stream().filter(block -> isFabric(BuiltInRegistries.BLOCK.getKey(block).getNamespace())).toArray(Block[]::new);
		Item[] items = BuiltInRegistries.ITEM.stream().filter(item -> isFabric(BuiltInRegistries.ITEM.getKey(item).getNamespace())).toArray(Item[]::new);
		if (blocks.length > 0) {
			event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, blockEntity, side) ->
					itemHandler(ItemStorage.SIDED.find(level, pos, state, blockEntity, side)), blocks);
			event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, blockEntity, side) ->
					fluidHandler(FluidStorage.SIDED.find(level, pos, state, blockEntity, side)), blocks);
		}
		if (items.length > 0) event.registerItem(Capabilities.FluidHandler.ITEM, (stack, context) -> FabricItemFluidHandler.of(stack), items);
		if (energy()) EnergyBridge.registerCapabilities(event, blocks, items);
	}

	/** A Fabric mod's item storage as a NeoForge handler (the handler itself, when the storage stands for one). */
	private static @Nullable IItemHandler itemHandler(@Nullable Storage<ItemVariant> storage) {
		if (storage == null) return null;
		return storage instanceof NeoItemStorage bridged ? bridged.handler : new FabricItemHandler(storage);
	}

	private static @Nullable IFluidHandler fluidHandler(@Nullable Storage<FluidVariant> storage) {
		if (storage == null) return null;
		return storage instanceof NeoFluidStorage bridged ? bridged.handler : new FabricFluidHandler(storage);
	}

	/** A NeoForge mod's item handler as a Fabric storage (the storage itself, when the handler stands for one). */
	private static Storage<ItemVariant> itemStorage(IItemHandler handler, Level level, BlockPos pos) {
		return handler instanceof FabricItemHandler bridged ? bridged.storage : NeoItemStorage.of(handler, level, pos);
	}

	private static Storage<FluidVariant> fluidStorage(IFluidHandler handler, Level level, BlockPos pos) {
		return handler instanceof FabricFluidHandler bridged ? bridged.storage : NeoFluidStorage.of(handler);
	}

	@FunctionalInterface
	private interface StorageOf<H, S> {
		Storage<S> wrap(H handler, Level level, BlockPos pos);
	}

	/**
	 * Fabric's fallback for NeoForge mods' blocks: NeoForge's capability. It goes before upstream's fallback that wraps
	 * any {@code Container}, so a NeoForge mod's block keeps the access rules of its own handler.
	 */
	private static <H, S> void addFabricFallback(BlockApiLookup<Storage<S>, @Nullable Direction> lookup, BlockCapability<H, @Nullable Direction> capability,
			StorageOf<H, S> wrap) {
		((BlockApiLookupImpl<Storage<S>, @Nullable Direction>) lookup).getFallbackProviders().add(0, (level, pos, state, blockEntity, side) -> {
			if (!isNeoForge(state.getBlock())) return null;
			H handler = level.getCapability(capability, pos, state, blockEntity, side);
			return handler == null ? null : wrap.wrap(handler, level, pos);
		});
	}
}
