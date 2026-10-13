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
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * CI test mod part for Fabricated's native fabric-transfer-api-v1 (and the lookup API under it): Fabric storages over a
 * vanilla chest and a water cauldron, with aborted transactions leaving them unchanged and committed ones applying; and
 * the bridge to NeoForge's capabilities, both ways.
 */
public final class TransferTest implements ModInitializer {
	private static final BlockPos CHEST = new BlockPos(8, 250, 8);
	private static final BlockPos CAULDRON = CHEST.east(2);
	private static final BlockPos FABRIC_BLOCK = CHEST.south(2);
	private static final BlockPos NEO_BLOCK = FABRIC_BLOCK.east(2);
	/** Holds whole buckets plus 40 droplets, so a millibucket-sized insert can't fill it exactly. */
	private static final long ODD_CAPACITY = FluidConstants.BUCKET + 40;

	private static Block block(String name) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("fabricated_testmod", name));
		return Registry.register(BuiltInRegistries.BLOCK, key, new Block(BlockBehaviour.Properties.of().setId(key)));
	}

	@Override
	public void onInitialize() {
		Block fabricBlock = block("fabric_storage_block");
		Block neoBlock = block("neo_handler_block");
		SingleVariantStorage<ItemVariant> fabricItems = new SingleVariantStorage<>() {
			@Override
			protected ItemVariant getBlankVariant() {
				return ItemVariant.blank();
			}

			@Override
			protected long getCapacity(ItemVariant variant) {
				return 64;
			}
		};
		SingleVariantStorage<FluidVariant> fabricFluids = new SingleVariantStorage<>() {
			@Override
			protected FluidVariant getBlankVariant() {
				return FluidVariant.blank();
			}

			@Override
			protected long getCapacity(FluidVariant variant) {
				return ODD_CAPACITY;
			}
		};
		ItemStorage.SIDED.registerForBlocks((level, pos, state, blockEntity, side) -> fabricItems, fabricBlock);
		FluidStorage.SIDED.registerForBlocks((level, pos, state, blockEntity, side) -> fabricFluids, fabricBlock);
		ItemStacksResourceHandler neoItems = new ItemStacksResourceHandler(1);
		// A NeoForge capability, as a NeoForge mod would register it (on Fabricated's mod bus: this mod has none).
		ModList.get().getModContainerById("fabricated").map(container -> container.getEventBus()).ifPresent(bus ->
				bus.addListener(RegisterCapabilitiesEvent.class, event -> event.registerBlock(Capabilities.Item.BLOCK,
						(level, pos, state, blockEntity, side) -> neoItems, neoBlock)));

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel level = server.overworld();
			List<String> failed = new ArrayList<>();
			level.setBlockAndUpdate(FABRIC_BLOCK, fabricBlock.defaultBlockState());
			level.setBlockAndUpdate(NEO_BLOCK, neoBlock.defaultBlockState());

			// NeoForge code using a Fabric storage.
			ResourceHandler<ItemResource> asNeo = level.getCapability(Capabilities.Item.BLOCK, FABRIC_BLOCK, Direction.UP);
			if (asNeo == null) {
				failed.add("neo_sees_fabric");
			} else {
				ItemResource diamond = ItemResource.of(Items.DIAMOND);
				try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
					if (asNeo.insert(diamond, 3, tx) != 3) failed.add("neo_insert");
				}
				if (fabricItems.amount != 0) failed.add("neo_abort");
				try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
					asNeo.insert(diamond, 3, tx);
					try (var inner = net.neoforged.neoforge.transfer.transaction.Transaction.open(tx)) {
						asNeo.extract(diamond, 1, inner);
						inner.commit();
					}
					tx.commit();
				}
				if (fabricItems.amount != 2 || !asNeo.getResource(0).is(Items.DIAMOND)) failed.add("neo_commit");
			}
			ResourceHandler<FluidResource> fluidsAsNeo = level.getCapability(Capabilities.Fluid.BLOCK, FABRIC_BLOCK, Direction.UP);
			if (fluidsAsNeo == null) {
				failed.add("neo_sees_fabric_fluid");
			} else {
				try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
					// 1001 mB don't fit, and 1000 mB plus 40 droplets would: only whole millibuckets may move.
					if (fluidsAsNeo.insert(FluidResource.of(Fluids.WATER), 1001, tx) != 1000) failed.add("neo_fluid_units");
					tx.commit();
				}
				if (fabricFluids.amount != FluidConstants.BUCKET) failed.add("neo_fluid_commit");
			}

			// Fabric code using a NeoForge handler.
			Storage<ItemVariant> asFabric = ItemStorage.SIDED.find(level, NEO_BLOCK, Direction.UP);
			if (asFabric == null) {
				failed.add("fabric_sees_neo");
			} else {
				ItemVariant emerald = ItemVariant.of(Items.EMERALD);
				try (Transaction tx = Transaction.openOuter()) {
					if (asFabric.insert(emerald, 4, tx) != 4) failed.add("fabric_insert");
				}
				if (neoItems.getAmountAsLong(0) != 0) failed.add("fabric_abort");
				try (Transaction tx = Transaction.openOuter()) {
					try (Transaction inner = tx.openNested()) {
						asFabric.insert(emerald, 4, inner);
						inner.commit();
					}
					tx.commit();
				}
				if (neoItems.getAmountAsLong(0) != 4) failed.add("fabric_commit");
			}
			level.setBlockAndUpdate(FABRIC_BLOCK, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(NEO_BLOCK, Blocks.AIR.defaultBlockState());

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
