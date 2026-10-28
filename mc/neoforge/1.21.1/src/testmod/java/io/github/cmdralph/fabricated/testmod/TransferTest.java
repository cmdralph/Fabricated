package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.EmptyItemFluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.FullItemFluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * CI test mod part for Fabricated's native fabric-transfer-api-v1 (and the lookup API under it), Minecraft 1.21.1:
 * Fabric storages over a vanilla chest and a water cauldron, with aborted transactions leaving them unchanged and
 * committed ones applying; and the bridge to NeoForge 21.1's handler capabilities, both ways, on blocks and in items (a
 * Fabric mod's fluid cell filled by NeoForge code, a NeoForge mod's by Fabric code: each item's storage changes the item
 * through the other side).
 */
public final class TransferTest implements ModInitializer {
	private static final BlockPos CHEST = new BlockPos(8, 250, 8);
	private static final BlockPos CAULDRON = CHEST.east(2);
	private static final BlockPos FABRIC_BLOCK = CHEST.south(2);
	private static final BlockPos NEO_BLOCK = FABRIC_BLOCK.east(2);
	/** Holds whole buckets plus 40 droplets, so a millibucket-sized insert can't fill it exactly. */
	private static final long ODD_CAPACITY = FluidConstants.BUCKET + 40;

	private static Block block(String namespace, String name) {
		return Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath(namespace, name), new Block(BlockBehaviour.Properties.of()));
	}

	private static Item item(String namespace, String name) {
		return Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(namespace, name), new Item(new Item.Properties()));
	}

	@Override
	public void onInitialize() {
		// A Fabric mod's block and fluid cells, and a NeoForge mod's (their capabilities registered below).
		Block fabricBlock = block("fabricated_testmod", "fabric_storage_block");
		Block neoBlock = block(TestMod.NEOFORGE_NAMESPACE, "neo_handler_block");
		Item fabricCell = item("fabricated_testmod", "fabric_cell");
		Item fabricWaterCell = item("fabricated_testmod", "fabric_water_cell");
		Item neoCell = item(TestMod.NEOFORGE_NAMESPACE, "neo_cell");
		Item neoWaterCell = item(TestMod.NEOFORGE_NAMESPACE, "neo_water_cell");
		FluidStorage.combinedItemApiProvider(fabricCell).register(context ->
				new EmptyItemFluidStorage(context, fabricWaterCell, Fluids.WATER, FluidConstants.BUCKET));
		FluidStorage.combinedItemApiProvider(fabricWaterCell).register(context ->
				new FullItemFluidStorage(context, fabricCell, FluidVariant.of(Fluids.WATER), FluidConstants.BUCKET));
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
		ItemStackHandler neoItems = new ItemStackHandler(1);
		// A NeoForge capability, as a NeoForge mod would register it (on Fabricated's mod bus: this mod has none).
		ModList.get().getModContainerById("fabricated").map(container -> container.getEventBus()).ifPresent(bus ->
				bus.addListener(RegisterCapabilitiesEvent.class, event -> {
					event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, blockEntity, side) -> neoItems, neoBlock);
					event.registerItem(Capabilities.FluidHandler.ITEM, (stack, context) -> new NeoCell(stack, neoCell, neoWaterCell), neoCell, neoWaterCell);
				}));

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel level = server.overworld();
			List<String> failed = new ArrayList<>();
			level.setBlockAndUpdate(FABRIC_BLOCK, fabricBlock.defaultBlockState());
			level.setBlockAndUpdate(NEO_BLOCK, neoBlock.defaultBlockState());

			// NeoForge code using a Fabric storage.
			IItemHandler asNeo = level.getCapability(Capabilities.ItemHandler.BLOCK, FABRIC_BLOCK, Direction.UP);
			if (asNeo == null) {
				failed.add("neo_sees_fabric");
			} else {
				if (!asNeo.insertItem(0, new ItemStack(Items.DIAMOND, 3), true).isEmpty()) failed.add("neo_insert");
				if (fabricItems.amount != 0) failed.add("neo_simulate");
				asNeo.insertItem(0, new ItemStack(Items.DIAMOND, 3), false);
				asNeo.extractItem(0, 1, false);
				if (fabricItems.amount != 2 || !asNeo.getStackInSlot(0).is(Items.DIAMOND)) failed.add("neo_execute");
			}
			IFluidHandler fluidsAsNeo = level.getCapability(Capabilities.FluidHandler.BLOCK, FABRIC_BLOCK, Direction.UP);
			if (fluidsAsNeo == null) {
				failed.add("neo_sees_fabric_fluid");
			} else {
				// 1001 mB don't fit, and 1000 mB plus 40 droplets would: only whole millibuckets may move.
				if (fluidsAsNeo.fill(new FluidStack(Fluids.WATER, 1001), IFluidHandler.FluidAction.EXECUTE) != 1000) failed.add("neo_fluid_units");
				if (fabricFluids.amount != FluidConstants.BUCKET) failed.add("neo_fluid_execute");
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
				if (!neoItems.getStackInSlot(0).isEmpty()) failed.add("fabric_abort");
				try (Transaction tx = Transaction.openOuter()) {
					try (Transaction inner = tx.openNested()) {
						asFabric.insert(emerald, 4, inner);
						inner.commit();
					}
					tx.commit();
				}
				if (neoItems.getStackInSlot(0).getCount() != 4) failed.add("fabric_commit");
			}
			level.setBlockAndUpdate(FABRIC_BLOCK, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(NEO_BLOCK, Blocks.AIR.defaultBlockState());

			// NeoForge code filling a Fabric mod's cell: the cell's Fabric storage swaps the item, which becomes the
			// handler's container.
			IFluidHandlerItem fabricCellAsNeo = new ItemStack(fabricCell).getCapability(Capabilities.FluidHandler.ITEM);
			if (fabricCellAsNeo == null) {
				failed.add("neo_sees_fabric_item");
			} else {
				if (fabricCellAsNeo.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.SIMULATE) != 1000) failed.add("neo_item_fill");
				if (!fabricCellAsNeo.getContainer().is(fabricCell)) failed.add("neo_item_simulate");
				fabricCellAsNeo.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
				if (!fabricCellAsNeo.getContainer().is(fabricWaterCell)) failed.add("neo_item_execute");
			}

			// Fabric code filling a NeoForge mod's cell in a slot: the cell's NeoForge handler swaps the item through
			// the Fabric context, inside the Fabric transaction.
			ItemStack[] neoCellStack = {new ItemStack(neoCell)};
			SingleStackStorage neoCellSlot = new SingleStackStorage() {
				@Override
				protected ItemStack getStack() {
					return neoCellStack[0];
				}

				@Override
				protected void setStack(ItemStack stack) {
					neoCellStack[0] = stack;
				}
			};
			Storage<FluidVariant> neoCellAsFabric = ContainerItemContext.ofSingleSlot(neoCellSlot).find(FluidStorage.ITEM);
			if (neoCellAsFabric == null) {
				failed.add("fabric_sees_neo_item");
			} else {
				try (Transaction tx = Transaction.openOuter()) {
					if (neoCellAsFabric.insert(FluidVariant.of(Fluids.WATER), FluidConstants.BUCKET, tx) != FluidConstants.BUCKET) {
						failed.add("fabric_item_insert");
					}
				}
				if (!neoCellStack[0].is(neoCell)) failed.add("fabric_item_abort");
				try (Transaction tx = Transaction.openOuter()) {
					try (Transaction inner = tx.openNested()) {
						neoCellAsFabric.insert(FluidVariant.of(Fluids.WATER), FluidConstants.BUCKET, inner);
						inner.commit();
					}
					tx.commit();
				}
				if (!neoCellStack[0].is(neoWaterCell)) failed.add("fabric_item_commit");
			}

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

	/** A NeoForge mod's fluid cell: empty, or holding a bucket of water, as its item says; filling swaps the container. */
	private static final class NeoCell implements IFluidHandlerItem {
		private final Item empty;
		private final Item full;
		private ItemStack container;

		NeoCell(ItemStack container, Item empty, Item full) {
			this.container = container;
			this.empty = empty;
			this.full = full;
		}

		@Override
		public ItemStack getContainer() {
			return container;
		}

		@Override
		public int getTanks() {
			return 1;
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			return container.is(full) ? new FluidStack(Fluids.WATER, 1000) : FluidStack.EMPTY;
		}

		@Override
		public int getTankCapacity(int tank) {
			return 1000;
		}

		@Override
		public boolean isFluidValid(int tank, FluidStack stack) {
			return stack.getFluid() == Fluids.WATER;
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			if (resource.getFluid() != Fluids.WATER || resource.getAmount() < 1000 || !container.is(empty)) return 0;
			if (action.execute()) container = new ItemStack(full);
			return 1000;
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			return resource.getFluid() == Fluids.WATER ? drain(resource.getAmount(), action) : FluidStack.EMPTY;
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			if (maxDrain < 1000 || !container.is(full)) return FluidStack.EMPTY;
			if (action.execute()) container = new ItemStack(empty);
			return new FluidStack(Fluids.WATER, 1000);
		}
	}
}
