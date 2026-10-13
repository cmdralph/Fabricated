package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

/**
 * CI test mod part for Fabricated's native fabric-transfer-api-v1 (and the lookup API under it): Fabric storages over a
 * vanilla chest and a water cauldron, with aborted transactions leaving them unchanged and committed ones applying.
 */
public final class TransferTest implements ModInitializer {
	private static final BlockPos CHEST = new BlockPos(8, 250, 8);
	private static final BlockPos CAULDRON = CHEST.east(2);

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel level = server.overworld();
			List<String> failed = new ArrayList<>();
			level.setBlockAndUpdate(CHEST, Blocks.CHEST.defaultBlockState());
			level.setBlockAndUpdate(CAULDRON, Blocks.WATER_CAULDRON.defaultBlockState()
					.setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
			Container chest = (Container) level.getBlockEntity(CHEST);
			chest.setItem(0, new ItemStack(Items.DIAMOND, 5));

			Storage<ItemVariant> items = ItemStorage.SIDED.find(level, CHEST, Direction.UP);
			if (items == null) {
				failed.add("chest_storage");
			} else {
				ItemVariant diamond = ItemVariant.of(Items.DIAMOND);
				try (Transaction tx = Transaction.openOuter()) {
					if (items.extract(diamond, 2, tx) != 2) failed.add("extract");
				}
				if (chest.getItem(0).getCount() != 5) failed.add("abort");
				try (Transaction tx = Transaction.openOuter()) {
					items.extract(diamond, 2, tx);
					if (items.insert(ItemVariant.of(Items.EMERALD), 3, tx) != 3) failed.add("insert");
					tx.commit();
				}
				if (chest.getItem(0).getCount() != 3 || chest.countItem(Items.EMERALD) != 3) failed.add("commit");
			}

			Storage<FluidVariant> fluids = FluidStorage.SIDED.find(level, CAULDRON, Direction.UP);
			if (fluids == null) {
				failed.add("cauldron_storage");
			} else {
				try (Transaction tx = Transaction.openOuter()) {
					if (fluids.extract(FluidVariant.of(Fluids.WATER), FluidConstants.BUCKET, tx) != FluidConstants.BUCKET) failed.add("fluid_extract");
					tx.commit();
				}
				if (!level.getBlockState(CAULDRON).is(Blocks.CAULDRON)) failed.add("fluid_commit");
			}

			chest.clearContent();
			level.setBlockAndUpdate(CHEST, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(CAULDRON, Blocks.AIR.defaultBlockState());
			if (failed.isEmpty()) {
				TestMod.LOGGER.info("FABRICATED-TEST transfer OK");
			} else {
				TestMod.LOGGER.error("FABRICATED-TEST transfer FAILED: {}", failed);
			}
		});
	}
}
