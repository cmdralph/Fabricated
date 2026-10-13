package io.github.cmdralph.fabricated.neoforge.transfer;

import java.util.function.BiFunction;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiProvider;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.TransferVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SidedStorageBlockEntity;
import net.fabricmc.fabric.impl.lookup.block.BlockApiLookupImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;

/**
 * Lets NeoForge and Fabric code reach each other's item and fluid storages on blocks: a NeoForge hopper or pipe sees a
 * Fabric mod's storage through NeoForge's capabilities, and a Fabric mod sees a NeoForge mod's handler through
 * {@code ItemStorage.SIDED}/{@code FluidStorage.SIDED}. Transactions are mirrored both ways ({@link FabricTransactions},
 * {@link NeoTransactions}); fluid amounts convert at 81 droplets per millibucket.
 *
 * <p>Vanilla blocks are left to each side's own wrappers. Neither direction asks the other side's fallbacks, so a
 * storage is never wrapped back into itself.
 */
public final class TransferBridge {
	private TransferBridge() {
	}

	public static void register(IEventBus modBus) {
		// Last, so NeoForge mods' own capabilities for their blocks come first.
		modBus.addListener(EventPriority.LOWEST, RegisterCapabilitiesEvent.class, TransferBridge::registerCapabilities);
		addFabricFallback(ItemStorage.SIDED, Capabilities.Item.BLOCK, ResourceKind.ITEMS);
		addFabricFallback(FluidStorage.SIDED, Capabilities.Fluid.BLOCK, ResourceKind.FLUIDS);
	}

	private static boolean isVanilla(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft");
	}

	private static void registerCapabilities(RegisterCapabilitiesEvent event) {
		Block[] blocks = BuiltInRegistries.BLOCK.stream().filter(block -> !isVanilla(block)).toArray(Block[]::new);
		if (blocks.length == 0) return;
		event.registerBlock(Capabilities.Item.BLOCK, (level, pos, state, blockEntity, side) ->
				neoHandler(ItemStorage.SIDED, ResourceKind.ITEMS, SidedStorageBlockEntity::getItemStorage, level, pos, state, blockEntity, side), blocks);
		event.registerBlock(Capabilities.Fluid.BLOCK, (level, pos, state, blockEntity, side) ->
				neoHandler(FluidStorage.SIDED, ResourceKind.FLUIDS, SidedStorageBlockEntity::getFluidStorage, level, pos, state, blockEntity, side), blocks);
	}

	/** A Fabric storage registered for the block (or offered by a Fabric block entity), as a NeoForge handler. */
	private static <F extends TransferVariant<?>, N extends Resource> @Nullable ResourceHandler<N> neoHandler(
			BlockApiLookup<Storage<F>, @Nullable Direction> lookup, ResourceKind<F, N> kind,
			BiFunction<SidedStorageBlockEntity, @Nullable Direction, @Nullable Storage<F>> sided,
			Level level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, @Nullable Direction side) {
		BlockApiProvider<Storage<F>, @Nullable Direction> provider = lookup.getProvider(state.getBlock());
		Storage<F> storage = provider != null ? provider.find(level, pos, state, blockEntity, side)
				: blockEntity instanceof SidedStorageBlockEntity sidedEntity ? sided.apply(sidedEntity, side) : null;
		if (storage == null) return null;
		if (storage instanceof NeoHandlerStorage<?, ?> bridged) return unwrap(bridged);
		return new FabricStorageHandler<>(storage, kind);
	}

	@SuppressWarnings("unchecked") // Only ever built with matching kinds.
	private static <N extends Resource> ResourceHandler<N> unwrap(NeoHandlerStorage<?, ?> storage) {
		return (ResourceHandler<N>) storage.handler;
	}

	/**
	 * Fabric's fallback for blocks without a Fabric storage: NeoForge's capability. It goes before upstream's fallback
	 * that wraps any {@code Container}, so a NeoForge mod's block keeps the access rules of its own handler.
	 */
	private static <F extends TransferVariant<?>, N extends Resource> void addFabricFallback(BlockApiLookup<Storage<F>, @Nullable Direction> lookup,
			BlockCapability<ResourceHandler<N>, @Nullable Direction> capability, ResourceKind<F, N> kind) {
		((BlockApiLookupImpl<Storage<F>, @Nullable Direction>) lookup).getFallbackProviders().add(0, (level, pos, state, blockEntity, side) -> {
			if (isVanilla(state.getBlock()) || blockEntity instanceof SidedStorageBlockEntity) return null;
			ResourceHandler<N> handler = level.getCapability(capability, pos, state, blockEntity, side);
			if (handler == null) return null;
			if (handler instanceof FabricStorageHandler<?, ?> bridged) return unwrapStorage(bridged);
			return new NeoHandlerStorage<>(handler, kind);
		});
	}

	@SuppressWarnings("unchecked") // Only ever built with matching kinds.
	private static <F extends TransferVariant<?>> Storage<F> unwrapStorage(FabricStorageHandler<?, ?> handler) {
		return (Storage<F>) handler.storage;
	}
}
