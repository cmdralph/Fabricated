package io.github.cmdralph.fabricated.forge.transfer;

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
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.listener.Priority;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/**
 * Lets Forge and Fabric code reach each other's item and fluid storages on blocks, as {@code main}'s bridge does on
 * NeoForge (forge-legacy's, ported): a Forge hopper or pipe sees a Fabric mod's storage through Forge's
 * {@code ITEM_HANDLER} and {@code FLUID_HANDLER} capabilities, and a Fabric mod sees a Forge mod's handler through
 * {@code ItemStorage.SIDED} and {@code FluidStorage.SIDED}. Fluid amounts convert at 81 droplets per millibucket, whole
 * millibuckets only; a fluid carrying data (Fabric's components, a Forge stack's tag) doesn't cross, as neither side
 * reads the other's.
 *
 * <p>Each side answers for what its own mods add, told apart by the block's namespace: the capabilities of a Fabric mod's
 * block entity come from Fabric's whole lookup, fallbacks included, and Fabric's lookups fall back to the capabilities
 * of a Forge mod's block entity. So neither side asks the other about the same block, and nothing is wrapped back into
 * itself. Vanilla's blocks are left to each side's own wrappers. Forge has capabilities only on block entities, so a
 * Fabric mod's storage on a block without one stays Fabric's.
 *
 * <p>Forge's handlers move items at once, with a flag to only simulate; Fabric's storages move them in transactions that
 * may be undone. A Forge handler seen as a Fabric storage ({@link ForgeItemStorage}, {@link ForgeFluidStorage}) only
 * simulates while the transaction is open and moves the result once the outermost one commits; a Fabric storage seen as
 * a Forge handler ({@link FabricItemHandler}, {@link FabricFluidHandler}) opens a transaction for each call, committed
 * unless the call simulates.
 */
public final class TransferBridge {
	/** The capability provider Fabricated attaches to Fabric mods' block entities. */
	private static final Identifier PROVIDER = Identifier.fromNamespaceAndPath("fabricated", "fabric_storages");

	private TransferBridge() {
	}

	/** Called once, with Fabric API's transfer module loaded. */
	public static void register() {
		// Last, so a Forge mod's own capability for a Fabric mod's block entity comes first.
		AttachCapabilitiesEvent.BlockEntities.BUS.addListener(Priority.LOWEST, TransferBridge::attach);
		addFallback(ItemStorage.SIDED, ForgeCapabilities.ITEM_HANDLER, TransferBridge::itemStorage);
		addFallback(FluidStorage.SIDED, ForgeCapabilities.FLUID_HANDLER, TransferBridge::fluidStorage);
	}

	private static void attach(AttachCapabilitiesEvent.BlockEntities event) {
		BlockEntity blockEntity = event.getObject();
		if (!isFabric(blockEntity.getBlockState().getBlock())) return;
		FabricStorageProvider provider = new FabricStorageProvider(blockEntity);
		event.addCapability(PROVIDER, provider);
		event.addListener(provider::invalidate);
	}

	/** Whether a Forge mod added the block: neither vanilla nor a Fabric mod (by namespace). */
	private static boolean isForge(Block block) {
		String namespace = BuiltInRegistries.BLOCK.getKey(block).getNamespace();
		return !namespace.equals("minecraft") && !isFabric(namespace);
	}

	private static boolean isFabric(Block block) {
		return isFabric(BuiltInRegistries.BLOCK.getKey(block).getNamespace());
	}

	private static boolean isFabric(String namespace) {
		return FabricatedState.get().isFabricMod(ModIds.toNeoForgeId(namespace));
	}

	/** A Fabric mod's item storage as a Forge handler (the handler itself, when the storage stands for one). */
	static IItemHandler itemHandler(Storage<ItemVariant> storage) {
		return storage instanceof ForgeItemStorage bridged ? bridged.handler : new FabricItemHandler(storage);
	}

	static IFluidHandler fluidHandler(Storage<FluidVariant> storage) {
		return storage instanceof ForgeFluidStorage bridged ? bridged.handler : new FabricFluidHandler(storage);
	}

	/** A Forge mod's item handler as a Fabric storage (the storage itself, when the handler stands for one). */
	private static Storage<ItemVariant> itemStorage(IItemHandler handler, Level level, BlockPos pos) {
		return handler instanceof FabricItemHandler bridged ? bridged.storage : ForgeItemStorage.of(handler, level, pos);
	}

	private static Storage<FluidVariant> fluidStorage(IFluidHandler handler, Level level, BlockPos pos) {
		return handler instanceof FabricFluidHandler bridged ? bridged.storage : ForgeFluidStorage.of(handler);
	}

	@FunctionalInterface
	private interface StorageOf<H, S> {
		Storage<S> wrap(H handler, Level level, BlockPos pos);
	}

	/**
	 * Fabric's fallback for Forge mods' block entities: Forge's capability. It goes before upstream's fallback that wraps
	 * any {@code Container}, so a Forge mod's block entity keeps the access rules of its own handler.
	 */
	private static <H, S> void addFallback(BlockApiLookup<Storage<S>, Direction> lookup, Capability<H> capability, StorageOf<H, S> wrap) {
		((BlockApiLookupImpl<Storage<S>, Direction>) lookup).getFallbackProviders().add(0, (level, pos, state, blockEntity, side) -> {
			if (blockEntity == null || !isForge(state.getBlock())) return null;
			H handler = blockEntity.getCapability(capability, side).orElse(null);
			return handler == null ? null : wrap.wrap(handler, level, pos);
		});
	}
}
