package io.github.cmdralph.fabricated.neoforge.transfer;

import org.jspecify.annotations.Nullable;

import io.github.cmdralph.fabricated.core.metadata.ModIds;
import io.github.cmdralph.fabricated.loader.FabricatedState;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.TransferVariant;
import net.fabricmc.fabric.impl.lookup.block.BlockApiLookupImpl;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
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
 * <p>Each side answers for what its own mods add, told apart by namespace: NeoForge's capabilities for Fabric mods'
 * blocks and items come from Fabric's whole lookup, fallbacks included (Tech Reborn offers its machines' tanks through
 * one), and Fabric's lookups fall back to NeoForge's capabilities for NeoForge mods' blocks and items. So neither side
 * ever asks the other about the same thing, and nothing is wrapped back into itself. Vanilla's are left to each side's
 * own wrappers.
 */
public final class TransferBridge {
	private TransferBridge() {
	}

	public static void register(IEventBus modBus) {
		// Last, so a NeoForge mod's own capability for a Fabric mod's block or item comes first.
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
			event.registerBlock(Capabilities.Item.BLOCK, (level, pos, state, blockEntity, side) ->
					neoHandler(ItemStorage.SIDED.find(level, pos, state, blockEntity, side), ResourceKind.ITEMS), blocks);
			event.registerBlock(Capabilities.Fluid.BLOCK, (level, pos, state, blockEntity, side) ->
					neoHandler(FluidStorage.SIDED.find(level, pos, state, blockEntity, side), ResourceKind.FLUIDS), blocks);
		}
		if (items.length > 0) {
			event.registerItem(Capabilities.Item.ITEM, (stack, access) ->
					neoHandler(ItemStorage.ITEM.find(stack, AccessItemContext.of(access)), ResourceKind.ITEMS), items);
			event.registerItem(Capabilities.Fluid.ITEM, (stack, access) ->
					neoHandler(FluidStorage.ITEM.find(stack, AccessItemContext.of(access)), ResourceKind.FLUIDS), items);
		}
		if (energy()) EnergyBridge.registerCapabilities(event, blocks, items);
	}

	/** A Fabric mod's storage as a NeoForge handler (the handler itself, when the storage stands for one). */
	private static <F extends TransferVariant<?>, N extends Resource> @Nullable ResourceHandler<N> neoHandler(@Nullable Storage<F> storage, ResourceKind<F, N> kind) {
		if (storage == null) return null;
		if (storage instanceof NeoHandlerStorage<?, ?> bridged) return unwrap(bridged);
		return new FabricStorageHandler<>(storage, kind);
	}

	@SuppressWarnings("unchecked") // Only ever built with matching kinds.
	private static <N extends Resource> ResourceHandler<N> unwrap(NeoHandlerStorage<?, ?> storage) {
		return (ResourceHandler<N>) storage.handler;
	}

	/**
	 * Fabric's fallback for NeoForge mods' blocks: NeoForge's capability. It goes before upstream's fallback that wraps
	 * any {@code Container}, so a NeoForge mod's block keeps the access rules of its own handler.
	 */
	private static <F extends TransferVariant<?>, N extends Resource> void addFabricFallback(BlockApiLookup<Storage<F>, @Nullable Direction> lookup,
			BlockCapability<ResourceHandler<N>, @Nullable Direction> capability, ResourceKind<F, N> kind) {
		((BlockApiLookupImpl<Storage<F>, @Nullable Direction>) lookup).getFallbackProviders().add(0, (level, pos, state, blockEntity, side) ->
				isNeoForge(state.getBlock()) ? storage(level.getCapability(capability, pos, state, blockEntity, side), kind) : null);
	}

	/**
	 * Fabric's fallback for NeoForge mods' items: NeoForge's capability, changing the item through the context. It
	 * comes after upstream's fallback, which gives every bucket item Fabric's own bucket storage.
	 */
	private static <F extends TransferVariant<?>, N extends Resource> void addFabricItemFallback(ItemApiLookup<Storage<F>, ContainerItemContext> lookup,
			ItemCapability<ResourceHandler<N>, ItemAccess> capability, ResourceKind<F, N> kind) {
		lookup.registerFallback((stack, context) ->
				isNeoForge(stack.getItem()) ? storage(stack.getCapability(capability, ContextItemAccess.of(context)), kind) : null);
	}

	/** A NeoForge mod's handler as a Fabric storage (the storage itself, when the handler stands for one). */
	private static <F extends TransferVariant<?>, N extends Resource> @Nullable Storage<F> storage(@Nullable ResourceHandler<N> handler, ResourceKind<F, N> kind) {
		if (handler == null) return null;
		if (handler instanceof FabricStorageHandler<?, ?> bridged) return unwrapStorage(bridged);
		return new NeoHandlerStorage<>(handler, kind);
	}

	@SuppressWarnings("unchecked") // Only ever built with matching kinds.
	private static <F extends TransferVariant<?>> Storage<F> unwrapStorage(FabricStorageHandler<?, ?> handler) {
		return (Storage<F>) handler.storage;
	}
}
