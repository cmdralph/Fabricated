package io.github.cmdralph.fabricated.neoforge.transfer;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import io.github.cmdralph.fabricated.core.metadata.ModIds;
import io.github.cmdralph.fabricated.loader.FabricatedState;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup.BlockApiProvider;
import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.TransferVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SidedStorageBlockEntity;
import net.fabricmc.fabric.impl.lookup.block.BlockApiLookupImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.resource.Resource;

/**
 * Lets NeoForge and Fabric code reach each other's item and fluid storages on blocks and in items: a NeoForge hopper or
 * pipe sees a Fabric mod's storage through NeoForge's capabilities, and a Fabric mod sees a NeoForge mod's handler
 * through {@code ItemStorage.SIDED}/{@code FluidStorage.SIDED}; a NeoForge machine fills a Fabric mod's tank item
 * through {@code Capabilities.Fluid.ITEM}, and a Fabric machine a NeoForge mod's through {@code FluidStorage.ITEM}, each
 * changing the item through the other side's access to where it is ({@link AccessItemContext},
 * {@link ContextItemAccess}). Transactions are mirrored both ways ({@link FabricTransactions}, {@link NeoTransactions});
 * fluid amounts convert at 81 droplets per millibucket.
 *
 * <p>Vanilla blocks and items are left to each side's own wrappers, and NeoForge's item capabilities are offered for
 * Fabric mods' items only (NeoForge code asks them of every stack it sees, often every tick). For a block, neither
 * direction asks the other side's fallbacks, so a storage is never wrapped back into itself; for an item, each asks
 * the other side's whole lookup (a Fabric mod's tank may come from a fallback, as Team Reborn's batteries do), and the
 * other side's fallback doesn't ask back about the same stack meanwhile ({@link #askOtherSide}).
 */
public final class TransferBridge {
	/** The stacks this thread is asking one side's item lookup about on behalf of the other. */
	private static final ThreadLocal<Set<ItemStack>> ASKING = ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));

	private TransferBridge() {
	}

	public static void register(IEventBus modBus) {
		// Last, so NeoForge mods' own capabilities for their blocks come first.
		modBus.addListener(EventPriority.LOWEST, RegisterCapabilitiesEvent.class, TransferBridge::registerCapabilities);
		addFabricFallback(ItemStorage.SIDED, Capabilities.Item.BLOCK, ResourceKind.ITEMS);
		addFabricFallback(FluidStorage.SIDED, Capabilities.Fluid.BLOCK, ResourceKind.FLUIDS);
		addFabricItemFallback(ItemStorage.ITEM, Capabilities.Item.ITEM, ResourceKind.ITEMS);
		addFabricItemFallback(FluidStorage.ITEM, Capabilities.Fluid.ITEM, ResourceKind.FLUIDS);
		if (energy()) EnergyBridge.register();
	}

	/** Whether a Fabric mod brought Team Reborn's energy library (see {@link EnergyBridge}). */
	private static boolean energy() {
		return ModList.get().isLoaded(ModIds.toNeoForgeId("team_reborn_energy"));
	}

	static boolean isVanilla(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft");
	}

	static boolean isVanilla(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft");
	}

	/** Whether a Fabric mod added the item (it has a Fabric mod's namespace). */
	private static boolean isFabric(Item item) {
		return FabricatedState.get().isFabricMod(ModIds.toNeoForgeId(BuiltInRegistries.ITEM.getKey(item).getNamespace()));
	}

	private static void registerCapabilities(RegisterCapabilitiesEvent event) {
		// The blocks every mod adds, and the items Fabric mods add: the ones the bridges register capabilities for.
		Block[] blocks = BuiltInRegistries.BLOCK.stream().filter(block -> !isVanilla(block)).toArray(Block[]::new);
		Item[] items = BuiltInRegistries.ITEM.stream().filter(TransferBridge::isFabric).toArray(Item[]::new);
		if (blocks.length > 0) {
			event.registerBlock(Capabilities.Item.BLOCK, (level, pos, state, blockEntity, side) ->
					neoHandler(ItemStorage.SIDED, ResourceKind.ITEMS, SidedStorageBlockEntity::getItemStorage, level, pos, state, blockEntity, side), blocks);
			event.registerBlock(Capabilities.Fluid.BLOCK, (level, pos, state, blockEntity, side) ->
					neoHandler(FluidStorage.SIDED, ResourceKind.FLUIDS, SidedStorageBlockEntity::getFluidStorage, level, pos, state, blockEntity, side), blocks);
		}
		if (items.length > 0) {
			event.registerItem(Capabilities.Item.ITEM, (stack, access) -> neoItemHandler(ItemStorage.ITEM, ResourceKind.ITEMS, stack, access), items);
			event.registerItem(Capabilities.Fluid.ITEM, (stack, access) -> neoItemHandler(FluidStorage.ITEM, ResourceKind.FLUIDS, stack, access), items);
		}
		if (energy()) EnergyBridge.registerCapabilities(event, blocks, items);
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

	/**
	 * Asks the other side's item lookup about {@code stack}, unless this thread is already asking about it on the other
	 * side's behalf: then the question comes from there, and is answered with nothing rather than asked back. Lookups for
	 * other stacks inside it (a backpack's contents, say) are asked as usual.
	 */
	static <T> @Nullable T askOtherSide(ItemStack stack, Supplier<@Nullable T> question) {
		Set<ItemStack> asking = ASKING.get();
		if (!asking.add(stack)) return null;
		try {
			return question.get();
		} finally {
			asking.remove(stack);
		}
	}

	/** The item's Fabric storage, as a NeoForge handler changing the item through {@code access}. */
	private static <F extends TransferVariant<?>, N extends Resource> @Nullable ResourceHandler<N> neoItemHandler(
			ItemApiLookup<Storage<F>, ContainerItemContext> lookup, ResourceKind<F, N> kind, ItemStack stack, ItemAccess access) {
		ContainerItemContext context = AccessItemContext.of(access);
		Storage<F> storage = askOtherSide(stack, () -> lookup.find(stack, context));
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

	/**
	 * Fabric's fallback for items without a Fabric storage: NeoForge's capability, changing the item through the
	 * context. It comes after upstream's fallback, which gives every bucket item Fabric's own bucket storage, and before
	 * the fallbacks of Fabric mods' initializers.
	 */
	private static <F extends TransferVariant<?>, N extends Resource> void addFabricItemFallback(ItemApiLookup<Storage<F>, ContainerItemContext> lookup,
			ItemCapability<ResourceHandler<N>, ItemAccess> capability, ResourceKind<F, N> kind) {
		lookup.registerFallback((stack, context) -> {
			if (isVanilla(stack.getItem())) return null;
			ItemAccess access = ContextItemAccess.of(context);
			ResourceHandler<N> handler = askOtherSide(stack, () -> stack.getCapability(capability, access));
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
