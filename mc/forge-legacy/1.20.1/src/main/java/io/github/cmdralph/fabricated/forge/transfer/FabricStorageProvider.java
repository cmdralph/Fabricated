package io.github.cmdralph.fabricated.forge.transfer;

import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;

/**
 * Forge's item and fluid handler capabilities of a Fabric mod's block entity: what Fabric's lookups find for it on that
 * side, asked each time, as Fabric's own code asks them. The handler for a side is kept while the lookup keeps finding
 * the same storage, so a Forge mod holding the capability keeps a live one; it is invalidated when the storage changes
 * or the block entity goes, as Forge expects of a capability.
 */
final class FabricStorageProvider implements ICapabilityProvider {
	private static final int SIDES = Direction.values().length + 1;

	private final BlockEntity blockEntity;
	/** By capability (items, then fluids), then side, the unsided one last. */
	private final Found[] found = new Found[2 * SIDES];

	FabricStorageProvider(BlockEntity blockEntity) {
		this.blockEntity = blockEntity;
	}

	/** The storage a handler was made for, and the capability holding it. */
	private record Found(Object storage, LazyOptional<?> handler) {
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
		if (capability == ForgeCapabilities.ITEM_HANDLER) return find(0, side, ItemStorage.SIDED, TransferBridge::itemHandler).cast();
		if (capability == ForgeCapabilities.FLUID_HANDLER) return find(SIDES, side, FluidStorage.SIDED, TransferBridge::fluidHandler).cast();
		return LazyOptional.empty();
	}

	private <S> LazyOptional<?> find(int offset, @Nullable Direction side, BlockApiLookup<Storage<S>, Direction> lookup, Function<Storage<S>, ?> handlerOf) {
		Level level = blockEntity.getLevel();
		if (level == null || blockEntity.isRemoved()) return LazyOptional.empty();
		Storage<S> storage = lookup.find(level, blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, side);
		int index = offset + (side == null ? SIDES - 1 : side.ordinal());
		Found previous = found[index];
		if (previous != null && previous.storage() == storage && previous.handler().isPresent()) return previous.handler();
		if (previous != null) previous.handler().invalidate();
		found[index] = null;
		if (storage == null) return LazyOptional.empty();
		Object handler = handlerOf.apply(storage);
		LazyOptional<?> optional = LazyOptional.of(() -> handler);
		found[index] = new Found(storage, optional);
		return optional;
	}

	/** The block entity is gone, or its chunk unloaded: every handler handed out is invalidated. */
	void invalidate() {
		for (int i = 0; i < found.length; i++) {
			if (found[i] != null) found[i].handler().invalidate();
			found[i] = null;
		}
	}
}
