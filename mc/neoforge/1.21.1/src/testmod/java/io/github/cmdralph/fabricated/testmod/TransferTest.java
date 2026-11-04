package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantItemStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
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
 * through the other side; a Fabric mod's bag filled and emptied by NeoForge code, its own stack showing the change, and a
 * NeoForge mod's pouch by Fabric code, while a Fabric mod's sack, which would take a stone by becoming another item, is
 * refused one by NeoForge code holding it).
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
		return item(namespace, name, new Item.Properties());
	}

	private static Item item(String namespace, String name, Item.Properties properties) {
		return Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(namespace, name), new Item(properties));
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
		// A Fabric mod's bag and sack, and a NeoForge mod's pouch: items holding items.
		Item fabricBag = item("fabricated_testmod", "fabric_bag", new Item.Properties().durability(100));
		Item fabricSack = item("fabricated_testmod", "fabric_sack");
		Item neoPouch = item(TestMod.NEOFORGE_NAMESPACE, "neo_pouch", new Item.Properties().durability(100));
		ItemStorage.ITEM.registerForItems((stack, context) -> new BagStorage(context), fabricBag);
		ItemStorage.ITEM.registerForItems((stack, context) -> new SackStorage(context, fabricCell), fabricSack);
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
					event.registerItem(Capabilities.ItemHandler.ITEM, (stack, context) -> new NeoPouch(stack), neoPouch);
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
			StackSlot neoCellSlot = new StackSlot(new ItemStack(neoCell));
			Storage<FluidVariant> neoCellAsFabric = ContainerItemContext.ofSingleSlot(neoCellSlot).find(FluidStorage.ITEM);
			if (neoCellAsFabric == null) {
				failed.add("fabric_sees_neo_item");
			} else {
				try (Transaction tx = Transaction.openOuter()) {
					if (neoCellAsFabric.insert(FluidVariant.of(Fluids.WATER), FluidConstants.BUCKET, tx) != FluidConstants.BUCKET) {
						failed.add("fabric_item_insert");
					}
				}
				if (!neoCellSlot.stack.is(neoCell)) failed.add("fabric_item_abort");
				try (Transaction tx = Transaction.openOuter()) {
					try (Transaction inner = tx.openNested()) {
						neoCellAsFabric.insert(FluidVariant.of(Fluids.WATER), FluidConstants.BUCKET, inner);
						inner.commit();
					}
					tx.commit();
				}
				if (!neoCellSlot.stack.is(neoWaterCell)) failed.add("fabric_item_commit");
			}

			// NeoForge code filling and emptying a Fabric mod's bag, which keeps its stone as damage: the bag's own stack
			// shows each change.
			ItemStack bagStack = new ItemStack(fabricBag);
			IItemHandler bagAsNeo = bagStack.getCapability(Capabilities.ItemHandler.ITEM);
			if (bagAsNeo == null) {
				failed.add("neo_sees_fabric_bag");
			} else {
				if (!bagAsNeo.insertItem(0, new ItemStack(Items.STONE, 10), true).isEmpty() || bagStack.getDamageValue() != 0) failed.add("neo_bag_simulate");
				if (!bagAsNeo.insertItem(0, new ItemStack(Items.STONE, 60), false).isEmpty() || bagStack.getDamageValue() != 60) failed.add("neo_bag_insert");
				if (bagAsNeo.insertItem(0, new ItemStack(Items.STONE, 10), false).getCount() != 6 || bagStack.getDamageValue() != 64) failed.add("neo_bag_capacity");
				if (bagAsNeo.insertItem(0, new ItemStack(Items.DIRT), false).getCount() != 1) failed.add("neo_bag_wrong_item");
				ItemStack taken = bagAsNeo.extractItem(0, 20, false);
				if (!taken.is(Items.STONE) || taken.getCount() != 20 || bagStack.getDamageValue() != 44) failed.add("neo_bag_extract");
			}
			// A Fabric mod's sack takes a stone by becoming another item, which NeoForge's caller, holding the sack, would
			// never see: an item handler can't hand it another stack.
			ItemStack sackStack = new ItemStack(fabricSack);
			IItemHandler sackAsNeo = sackStack.getCapability(Capabilities.ItemHandler.ITEM);
			if (sackAsNeo == null) {
				failed.add("neo_sees_fabric_sack");
			} else if (sackAsNeo.insertItem(0, new ItemStack(Items.STONE), false).getCount() != 1 || !sackStack.is(fabricSack)) {
				failed.add("neo_sack_in_place");
			}

			// Fabric code filling and emptying a NeoForge mod's pouch in a slot, in the Fabric transaction.
			StackSlot pouchSlot = new StackSlot(new ItemStack(neoPouch));
			Storage<ItemVariant> pouchAsFabric = ContainerItemContext.ofSingleSlot(pouchSlot).find(ItemStorage.ITEM);
			if (pouchAsFabric == null) {
				failed.add("fabric_sees_neo_pouch");
			} else {
				ItemVariant stone = ItemVariant.of(Items.STONE);
				try (Transaction tx = Transaction.openOuter()) {
					if (pouchAsFabric.insert(stone, 10, tx) != 10 || pouchSlot.stack.getDamageValue() != 10) failed.add("fabric_pouch_insert");
				}
				if (pouchSlot.stack.getDamageValue() != 0) failed.add("fabric_pouch_abort");
				try (Transaction tx = Transaction.openOuter()) {
					if (pouchAsFabric.insert(stone, 100, tx) != 64) failed.add("fabric_pouch_capacity");
					if (pouchAsFabric.insert(ItemVariant.of(Items.DIRT), 1, tx) != 0) failed.add("fabric_pouch_wrong_item");
					tx.commit();
				}
				if (!pouchSlot.stack.is(neoPouch) || pouchSlot.stack.getDamageValue() != 64) failed.add("fabric_pouch_commit");
				long viewed = 0;
				for (StorageView<ItemVariant> view : pouchAsFabric) {
					if (view.getResource().equals(stone)) viewed += view.getAmount();
				}
				if (viewed != 64) failed.add("fabric_pouch_views");
				try (Transaction tx = Transaction.openOuter()) {
					if (pouchAsFabric.extract(stone, 20, tx) != 20) failed.add("fabric_pouch_extract");
					tx.commit();
				}
				if (pouchSlot.stack.getDamageValue() != 44) failed.add("fabric_pouch_extract_commit");
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
			TestMod.report("transfer", failed, "fabric-transfer-api-v1", Map.of(
					"neo_sees_fabric_fluid", "Forgified Fabric API gives a Fabric fluid storage a NeoForge fluid handler on block entities only",
					"fabric_sees_neo", "Forgified Fabric API's Fabric view of a NeoForge item handler needs a block entity",
					"neo_sees_fabric_item", "Forgified Fabric API gives Fabric items no NeoForge fluid handler (left out in its source as broken)",
					"fabric_item_commit", "Forgified Fabric API's Fabric view of a NeoForge item's fluid handler fills the handler's own item, not the slot's",
					"neo_sees_fabric_bag", "Forgified Fabric API gives a Fabric item storage in an item (ItemStorage.ITEM) no NeoForge item handler",
					"neo_sees_fabric_sack", "Forgified Fabric API gives a Fabric item storage in an item (ItemStorage.ITEM) no NeoForge item handler",
					"fabric_sees_neo_pouch", "Forgified Fabric API gives a NeoForge item handler of a stack no Fabric storage (ItemStorage.ITEM)"));
		});
	}

	/** A Fabric mod's machine slot, holding a stack. */
	private static final class StackSlot extends SingleStackStorage {
		ItemStack stack;

		StackSlot(ItemStack stack) {
			this.stack = stack;
		}

		@Override
		protected ItemStack getStack() {
			return stack;
		}

		@Override
		protected void setStack(ItemStack stack) {
			this.stack = stack;
		}
	}

	/** A Fabric mod's bag storage: up to 64 stone, one for each point of the item's damage. */
	private static final class BagStorage extends SingleVariantItemStorage<ItemVariant> {
		BagStorage(ContainerItemContext context) {
			super(context);
		}

		@Override
		protected ItemVariant getBlankResource() {
			return ItemVariant.blank();
		}

		@Override
		protected ItemVariant getResource(ItemVariant item) {
			return item.toStack().getDamageValue() > 0 ? ItemVariant.of(Items.STONE) : ItemVariant.blank();
		}

		@Override
		protected long getAmount(ItemVariant item) {
			return item.toStack().getDamageValue();
		}

		@Override
		protected long getCapacity(ItemVariant variant) {
			return 64;
		}

		@Override
		protected ItemVariant getUpdatedVariant(ItemVariant item, ItemVariant resource, long amount) {
			ItemStack stack = item.toStack();
			stack.setDamageValue((int) amount);
			return ItemVariant.of(stack);
		}

		@Override
		protected boolean canInsert(ItemVariant resource) {
			return resource.isOf(Items.STONE) && !resource.hasComponents();
		}
	}

	/** A Fabric mod's sack storage, which takes a stone by becoming another item: one no NeoForge item handler can be. */
	private static final class SackStorage implements Storage<ItemVariant> {
		private final ContainerItemContext context;
		private final Item filled;

		SackStorage(ContainerItemContext context, Item filled) {
			this.context = context;
			this.filled = filled;
		}

		@Override
		public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
			if (maxAmount < 1 || !resource.equals(ItemVariant.of(Items.STONE))) return 0;
			return context.exchange(ItemVariant.of(filled), 1, transaction);
		}

		@Override
		public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
			return 0;
		}

		@Override
		public Iterator<StorageView<ItemVariant>> iterator() {
			return Collections.emptyIterator();
		}
	}

	/** A NeoForge mod's pouch handler: up to 64 stone in one slot, one for each point of the item's damage. */
	private static final class NeoPouch implements IItemHandler {
		private final ItemStack container;

		NeoPouch(ItemStack container) {
			this.container = container;
		}

		@Override
		public int getSlots() {
			return 1;
		}

		@Override
		public ItemStack getStackInSlot(int slot) {
			int held = container.getDamageValue();
			return slot == 0 && held > 0 ? new ItemStack(Items.STONE, held) : ItemStack.EMPTY;
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			if (slot != 0 || !stack.is(Items.STONE)) return stack;
			int moved = Math.min(stack.getCount(), 64 - container.getDamageValue());
			if (moved <= 0) return stack;
			if (!simulate) container.setDamageValue(container.getDamageValue() + moved);
			return moved == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - moved);
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			int moved = slot == 0 ? Math.min(amount, container.getDamageValue()) : 0;
			if (moved <= 0) return ItemStack.EMPTY;
			if (!simulate) container.setDamageValue(container.getDamageValue() - moved);
			return new ItemStack(Items.STONE, moved);
		}

		@Override
		public int getSlotLimit(int slot) {
			return 64;
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return slot == 0 && stack.is(Items.STONE);
		}
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
