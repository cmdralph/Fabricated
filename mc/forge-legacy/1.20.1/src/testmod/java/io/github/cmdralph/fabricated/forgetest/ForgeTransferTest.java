package io.github.cmdralph.fabricated.forgetest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.EmptyItemFluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.FullItemFluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantItemStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/**
 * The forge-legacy test mod's part for the bridge between Fabric's storages and Forge 47's item and fluid handler
 * capabilities (with Fabric API's jar only, which provides fabric-transfer-api-v1). This mod's block entity offers a
 * Fabric item storage and a Fabric fluid storage, which Forge's code reaches through the block entity's capabilities; a
 * block entity registered under a namespace no Fabric mod has, standing in for a Forge mod's, offers Forge's own
 * {@code ItemStackHandler} and {@code FluidTank} through its capabilities, as a Forge mod does, which Fabric's code reaches
 * through {@code ItemStorage.SIDED} and {@code FluidStorage.SIDED}: an aborted transaction leaves the handler as it was,
 * two insertions in one transaction take no more than the slots hold together, and a committed one fills them. Items
 * the same way: Forge's code fills and drains this mod's cell, which becomes another item when full, and its gauge,
 * which keeps its fluid as damage, through {@code FLUID_HANDLER_ITEM}, the gauge's own stack showing the change, and
 * Fabric's code fills and drains a Forge mod's cell through {@code FluidStorage.ITEM}, an aborted transaction leaving
 * it as it was. A Forge mod's part names Forge's classes as Forge 47 has them. Where another mod provides the module
 * (Forgified Fabric API), its answers are that mod's ({@link ForgeTestMod#report}).
 */
public final class ForgeTransferTest {
	/** A namespace no Fabric mod has: the bridge tells Forge mods' blocks from Fabric mods' by namespace. */
	private static final String FORGE_NAMESPACE = "fabricated_forge_test";
	/** The Fabric mod's own namespace, its mod id's, which the bridge takes for a Fabric mod's. */
	private static final String FABRIC_NAMESPACE = "fabricated_testmod";
	/** Holds a bucket and 40 droplets, so a millibucket-sized fill can't fill it exactly. */
	private static final long ODD_CAPACITY = 81000 + 40;

	/**
	 * What Forgified Fabric API's transfer module (0.92.6 for 1.20.1) answers differently, as its source reads: the items
	 * that then fail, each with why.
	 */
	private static final Map<String, String> PROVIDERS_ANSWERS = Map.of(
			"filling the last part of a millibucket filled 0 mB, 81040 droplets",
			"its Forge handler of a Fabric storage fills the droplets of a millibucket the storage has room for and reports 0 mB (FluidStorageFluidHandler.fill)",
			"draining 500 mB gave 500 mB, leaving 40540 droplets", "the 40 droplets that fill left stay",
			"two insertions of 100 took 100 and 100 of the 128 two slots hold",
			"its Fabric storage of a Forge item handler (ForgeItemStorage) simulates each insertion on the handler as it stands and inserts once the"
					+ " transaction commits, so a second insertion in the same transaction doesn't see the first",
			"no fluid handler capability for the Fabric mod's cell", "it gives Fabric mods' items no Forge fluid handler (onAttachItemStackCapabilities is empty)",
			"no fluid handler capability for the Fabric mod's gauge", "it gives Fabric mods' items no Forge fluid handler (onAttachItemStackCapabilities is empty)",
			"filling the Forge mod's cell took 81000 droplets and made it handler_cell",
			"its Fabric storage of a Forge item's handler (ForgeFluidStorage) fills the handler's own stack, never handing the context the item it becomes",
			"a committed fill left the cell handler_cell", "the context keeps the empty cell that fill left",
			"draining the full cell gave 0 droplets", "the context keeps the empty cell that fill left");

	/** Whether {@link #register} ran with Fabric API's transfer module, whose classes are only touched then. */
	private static boolean registered;

	private ForgeTransferTest() {
	}

	/** From the main entrypoint; only with Fabric API's transfer module. */
	static void register() {
		if (!FabricLoader.getInstance().isModLoaded("fabric-transfer-api-v1")) return;
		WithTransferApi.register();
		registered = true;
	}

	/** At the first server tick, on the overworld. */
	public static void check(ServerLevel level) {
		if (registered) WithTransferApi.check(level);
	}

	/**
	 * Everything that names Fabric API's transfer module, loaded only once it is known to be installed: the JVM's verifier
	 * loads the types a method's values are checked against (a storage a lookup's provider returns), which aren't there
	 * without it.
	 */
	private static final class WithTransferApi {
		private static Block fabricBlock;
		private static Block forgeBlock;
		static BlockEntityType<FabricStorageEntity> fabricType;
		static BlockEntityType<ForgeHandlerEntity> forgeType;
		private static Item fabricCell;
		private static Item fabricWaterCell;
		private static Item fabricGauge;
		static Item forgeCell;
		static Item forgeWaterCell;

		private WithTransferApi() {
		}

		static void register() {
			fabricBlock = block(new ResourceLocation(FABRIC_NAMESPACE, "storage_block"), FabricStorageEntity::new);
			forgeBlock = block(new ResourceLocation(FORGE_NAMESPACE, "handler_block"), ForgeHandlerEntity::new);
			fabricType = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, new ResourceLocation(FABRIC_NAMESPACE, "storage_block"),
					BlockEntityType.Builder.<FabricStorageEntity>of(FabricStorageEntity::new, fabricBlock).build(null));
			forgeType = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, new ResourceLocation(FORGE_NAMESPACE, "handler_block"),
					BlockEntityType.Builder.<ForgeHandlerEntity>of(ForgeHandlerEntity::new, forgeBlock).build(null));
			ItemStorage.SIDED.registerForBlocks((level, pos, state, blockEntity, side) ->
					blockEntity instanceof FabricStorageEntity entity ? entity.items : null, fabricBlock);
			FluidStorage.SIDED.registerForBlocks((level, pos, state, blockEntity, side) ->
					blockEntity instanceof FabricStorageEntity entity ? entity.fluids : null, fabricBlock);
			fabricCell = item(new ResourceLocation(FABRIC_NAMESPACE, "cell"), new Item(new Item.Properties()));
			fabricWaterCell = item(new ResourceLocation(FABRIC_NAMESPACE, "water_cell"), new Item(new Item.Properties()));
			fabricGauge = item(new ResourceLocation(FABRIC_NAMESPACE, "gauge"), new Item(new Item.Properties().durability(1000)));
			forgeCell = item(new ResourceLocation(FORGE_NAMESPACE, "handler_cell"), new ForgeCellItem());
			forgeWaterCell = item(new ResourceLocation(FORGE_NAMESPACE, "handler_water_cell"), new ForgeCellItem());
			FluidStorage.ITEM.registerForItems((stack, context) -> new EmptyItemFluidStorage(context, fabricWaterCell, Fluids.WATER, 81000), fabricCell);
			FluidStorage.ITEM.registerForItems((stack, context) ->
					new FullItemFluidStorage(context, fabricCell, FluidVariant.of(Fluids.WATER), 81000), fabricWaterCell);
			FluidStorage.ITEM.registerForItems((stack, context) -> new GaugeStorage(context), fabricGauge);
		}

		private static Item item(ResourceLocation id, Item item) {
			return Registry.register(BuiltInRegistries.ITEM, id, item);
		}

		private static Block block(ResourceLocation id, BlockEntityType.BlockEntitySupplier<?> entity) {
			return Registry.register(BuiltInRegistries.BLOCK, id, new BlockWithEntity(BlockBehaviour.Properties.copy(Blocks.STONE), entity));
		}

		static void check(ServerLevel level) {
			BlockPos fabricPos = ForgeTestMod.testSite(level, 16, 284, 4);
			BlockPos forgePos = ForgeTestMod.testSite(level, 18, 284, 4);
			List<String> failed = new ArrayList<>();
			level.setBlock(fabricPos, fabricBlock.defaultBlockState(), 3);
			level.setBlock(forgePos, forgeBlock.defaultBlockState(), 3);
			if (level.getBlockEntity(fabricPos) instanceof FabricStorageEntity entity) {
				forgeSeesFabric(entity, failed);
			} else {
				failed.add("the Fabric mod's block has no block entity");
			}
			if (level.getBlockEntity(forgePos) instanceof ForgeHandlerEntity entity) {
				fabricSeesForge(level, forgePos, entity, failed);
			} else {
				failed.add("the Forge mod's block has no block entity");
			}
			level.setBlock(fabricPos, Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(forgePos, Blocks.AIR.defaultBlockState(), 3);
			forgeSeesFabricItems(failed);
			fabricSeesForgeItems(failed);
			ForgeTestMod.report("transfer", failed, "fabric-transfer-api-v1", PROVIDERS_ANSWERS);
		}

		/** Forge's code, as a hopper or pipe of a Forge mod's, on the Fabric mod's storages. */
		private static void forgeSeesFabric(FabricStorageEntity entity, List<String> failed) {
			IItemHandler items = entity.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).resolve().orElse(null);
			if (items == null) {
				failed.add("no item handler capability for the Fabric mod's storage");
			} else {
				ItemStack left = items.insertItem(0, new ItemStack(Items.STONE, 10), true);
				if (!left.isEmpty() || entity.items.amount != 0) failed.add("a simulated insertion left " + left.getCount() + " and moved " + entity.items.amount);
				left = items.insertItem(0, new ItemStack(Items.STONE, 10), false);
				if (!left.isEmpty() || entity.items.amount != 10) failed.add("an insertion left " + left.getCount() + " and moved " + entity.items.amount);
				ItemStack taken = items.extractItem(0, 4, false);
				if (taken.getCount() != 4 || taken.getItem() != Items.STONE || entity.items.amount != 6) {
					failed.add("an extraction of 4 gave " + taken.getCount() + ", leaving " + entity.items.amount);
				}
				if (items.insertItem(0, new ItemStack(Items.DIRT, 1), false).getCount() != 1) failed.add("dirt went into a storage holding stone");
			}
			IFluidHandler fluids = entity.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).resolve().orElse(null);
			if (fluids == null) {
				failed.add("no fluid handler capability for the Fabric mod's storage");
			} else {
				int filled = fluids.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
				if (filled != 1000 || entity.fluids.amount != 81000) failed.add("filling 1000 mB filled " + filled + " mB, " + entity.fluids.amount + " droplets");
				// 40 droplets are left, less than a millibucket.
				filled = fluids.fill(new FluidStack(Fluids.WATER, 1), IFluidHandler.FluidAction.EXECUTE);
				if (filled != 0 || entity.fluids.amount != 81000) failed.add("filling the last part of a millibucket filled " + filled + " mB, " + entity.fluids.amount + " droplets");
				FluidStack drained = fluids.drain(500, IFluidHandler.FluidAction.EXECUTE);
				if (drained.getAmount() != 500 || drained.getFluid() != Fluids.WATER || entity.fluids.amount != 40500) {
					failed.add("draining 500 mB gave " + drained.getAmount() + " mB, leaving " + entity.fluids.amount + " droplets");
				}
			}
		}

		/** Fabric's code, as a Fabric mod's pipe, on the Forge mod's handlers. */
		private static void fabricSeesForge(ServerLevel level, BlockPos pos, ForgeHandlerEntity entity, List<String> failed) {
			Storage<ItemVariant> items = ItemStorage.SIDED.find(level, pos, Direction.UP);
			if (items == null) {
				failed.add("Fabric's item lookup found nothing for the Forge mod's handler");
			} else {
				ItemVariant stone = ItemVariant.of(Items.STONE);
				try (Transaction transaction = Transaction.openOuter()) {
					long inserted = items.insert(stone, 10, transaction);
					if (inserted != 10) failed.add("an insertion of 10 took " + inserted);
					if (!entity.items.getStackInSlot(0).isEmpty()) failed.add("the handler changed before the transaction committed");
				}
				if (!entity.items.getStackInSlot(0).isEmpty()) failed.add("an aborted insertion reached the handler");
				try (Transaction transaction = Transaction.openOuter()) {
					long first = items.insert(stone, 100, transaction);
					long second = items.insert(stone, 100, transaction);
					if (first != 100 || second != 28) failed.add("two insertions of 100 took " + first + " and " + second + " of the 128 two slots hold");
					transaction.commit();
				}
				int held = entity.items.getStackInSlot(0).getCount() + entity.items.getStackInSlot(1).getCount();
				if (held != 128) failed.add("the committed insertions left " + held + " in the handler");
				try (Transaction transaction = Transaction.openOuter()) {
					long extracted = items.extract(stone, 70, transaction);
					if (extracted != 70) failed.add("an extraction of 70 gave " + extracted);
					transaction.commit();
				}
				held = entity.items.getStackInSlot(0).getCount() + entity.items.getStackInSlot(1).getCount();
				if (held != 58) failed.add("the committed extraction left " + held + " in the handler");
			}
			Storage<FluidVariant> fluids = FluidStorage.SIDED.find(level, pos, Direction.UP);
			if (fluids == null) {
				failed.add("Fabric's fluid lookup found nothing for the Forge mod's handler");
			} else {
				FluidVariant water = FluidVariant.of(Fluids.WATER);
				try (Transaction transaction = Transaction.openOuter()) {
					// 1100 millibuckets and 40 droplets: the handler takes whole millibuckets.
					long inserted = fluids.insert(water, 81000 + 8100 + 40, transaction);
					if (inserted != 89100) failed.add("an insertion of 1100 mB and 40 droplets took " + inserted + " droplets");
					transaction.commit();
				}
				if (entity.tank.getFluidAmount() != 1100) failed.add("the committed insertion left " + entity.tank.getFluidAmount() + " mB in the tank");
				try (Transaction transaction = Transaction.openOuter()) {
					long extracted = fluids.extract(water, 81000, transaction);
					if (extracted != 81000) failed.add("an extraction of a bucket gave " + extracted + " droplets");
				}
				if (entity.tank.getFluidAmount() != 1100) failed.add("an aborted extraction left " + entity.tank.getFluidAmount() + " mB in the tank");
			}
		}

		/** Forge's code, as a Forge mod's machine filling and draining an item in its slot, on the Fabric mod's items. */
		private static void forgeSeesFabricItems(List<String> failed) {
			IFluidHandlerItem cell = new ItemStack(fabricCell).getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).resolve().orElse(null);
			if (cell == null) {
				failed.add("no fluid handler capability for the Fabric mod's cell");
			} else {
				int filled = cell.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.SIMULATE);
				if (filled != 1000 || cell.getContainer().getItem() != fabricCell) failed.add("a simulated fill of the cell filled " + filled + " mB and made it " + cell.getContainer().getItem());
				filled = cell.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
				if (filled != 1000 || cell.getContainer().getItem() != fabricWaterCell) failed.add("filling the cell filled " + filled + " mB and made it " + cell.getContainer().getItem());
				FluidStack drained = cell.drain(1000, IFluidHandler.FluidAction.EXECUTE);
				if (drained.getAmount() != 1000 || cell.getContainer().getItem() != fabricCell) failed.add("draining the full cell gave " + drained.getAmount() + " mB and made it " + cell.getContainer().getItem());
			}
			ItemStack gaugeStack = new ItemStack(fabricGauge);
			IFluidHandlerItem gauge = gaugeStack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).resolve().orElse(null);
			if (gauge == null) {
				failed.add("no fluid handler capability for the Fabric mod's gauge");
			} else {
				int filled = gauge.fill(new FluidStack(Fluids.WATER, 250), IFluidHandler.FluidAction.EXECUTE);
				if (filled != 250 || gauge.getContainer() != gaugeStack || gaugeStack.getDamageValue() != 250) {
					failed.add("filling the gauge filled " + filled + " mB, its own stack showing " + gaugeStack.getDamageValue());
				}
				FluidStack held = gauge.getFluidInTank(0);
				if (held.getAmount() != 250 || held.getFluid() != Fluids.WATER) failed.add("the gauge's tank shows " + held.getAmount() + " mB");
				filled = gauge.fill(new FluidStack(Fluids.WATER, 900), IFluidHandler.FluidAction.SIMULATE);
				if (filled != 750 || gaugeStack.getDamageValue() != 250) failed.add("a simulated fill of 900 mB filled " + filled + ", the gauge showing " + gaugeStack.getDamageValue());
				FluidStack drained = gauge.drain(new FluidStack(Fluids.WATER, 100), IFluidHandler.FluidAction.EXECUTE);
				if (drained.getAmount() != 100 || gaugeStack.getDamageValue() != 150) failed.add("draining 100 mB gave " + drained.getAmount() + ", the gauge showing " + gaugeStack.getDamageValue());
			}
			if (new ItemStack(ForgeTestMod.testItem).getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent()) {
				failed.add("the Fabric mod's item without a fluid storage has a fluid handler");
			}
		}

		/** Fabric's code, as a Fabric mod's machine with a slot, on the Forge mod's cell. */
		private static void fabricSeesForgeItems(List<String> failed) {
			ContainerItemContext context = ContainerItemContext.ofSingleSlot(new MachineSlot(new ItemStack(forgeCell)));
			Storage<FluidVariant> cell = context.find(FluidStorage.ITEM);
			if (cell == null) {
				failed.add("Fabric's item lookup found nothing for the Forge mod's cell");
				return;
			}
			FluidVariant water = FluidVariant.of(Fluids.WATER);
			try (Transaction transaction = Transaction.openOuter()) {
				long inserted = cell.insert(water, 81000, transaction);
				if (inserted != 81000 || context.getItemVariant().getItem() != forgeWaterCell) {
					failed.add("filling the Forge mod's cell took " + inserted + " droplets and made it " + context.getItemVariant().getItem());
				}
			}
			if (context.getItemVariant().getItem() != forgeCell) failed.add("an aborted fill left the cell " + context.getItemVariant().getItem());
			try (Transaction transaction = Transaction.openOuter()) {
				long inserted = cell.insert(water, 40500, transaction);
				if (inserted != 0) failed.add("half a bucket went into the cell, which takes a bucket");
				cell.insert(water, 81000, transaction);
				transaction.commit();
			}
			if (context.getItemVariant().getItem() != forgeWaterCell) failed.add("a committed fill left the cell " + context.getItemVariant().getItem());
			Storage<FluidVariant> full = context.find(FluidStorage.ITEM);
			try (Transaction transaction = Transaction.openOuter()) {
				long extracted = full == null ? 0 : full.extract(water, 81000, transaction);
				if (extracted != 81000) failed.add("draining the full cell gave " + extracted + " droplets");
				transaction.commit();
			}
			if (context.getItemVariant().getItem() != forgeCell) failed.add("a committed drain left the cell " + context.getItemVariant().getItem());
		}
	}

	/** A Forge mod's cell, as Forge mods' own fluid items do: its handler fills or drains a bucket, swapping the item. */
	static final class ForgeCellItem extends Item {
		ForgeCellItem() {
			super(new Item.Properties());
		}

		/** Forge's way for an item to give its stacks capabilities ({@code IForgeItem}'s). */
		public ICapabilityProvider initCapabilities(ItemStack stack, CompoundTag nbt) {
			return new CellHandler(stack);
		}
	}

	/** The Forge mod's cell handler, a bucket of water or none. */
	static final class CellHandler implements IFluidHandlerItem, ICapabilityProvider {
		private final LazyOptional<IFluidHandlerItem> capability = LazyOptional.of(() -> this);
		private ItemStack container;

		CellHandler(ItemStack container) {
			this.container = container;
		}

		private boolean full() {
			return container.getItem() == WithTransferApi.forgeWaterCell;
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
			return full() ? new FluidStack(Fluids.WATER, 1000) : FluidStack.EMPTY;
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
			if (full() || container.getCount() != 1 || resource.getFluid() != Fluids.WATER || resource.getAmount() < 1000) return 0;
			if (action.execute()) container = new ItemStack(WithTransferApi.forgeWaterCell);
			return 1000;
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			return resource.getFluid() == Fluids.WATER ? drain(resource.getAmount(), action) : FluidStack.EMPTY;
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			if (!full() || container.getCount() != 1 || maxDrain < 1000) return FluidStack.EMPTY;
			if (action.execute()) container = new ItemStack(WithTransferApi.forgeCell);
			return new FluidStack(Fluids.WATER, 1000);
		}

		@Override
		public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
			return capability == ForgeCapabilities.FLUID_HANDLER_ITEM ? this.capability.cast() : LazyOptional.empty();
		}
	}

	/** A Fabric mod's machine slot, holding a stack. */
	static final class MachineSlot extends SingleStackStorage {
		private ItemStack stack;

		MachineSlot(ItemStack stack) {
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

	/** This mod's gauge storage: up to a bucket of water, a millibucket for each point of the item's damage. */
	static final class GaugeStorage extends SingleVariantItemStorage<FluidVariant> {
		GaugeStorage(ContainerItemContext context) {
			super(context);
		}

		@Override
		protected FluidVariant getBlankResource() {
			return FluidVariant.blank();
		}

		@Override
		protected FluidVariant getResource(ItemVariant item) {
			return item.toStack().getDamageValue() > 0 ? FluidVariant.of(Fluids.WATER) : FluidVariant.blank();
		}

		@Override
		protected long getAmount(ItemVariant item) {
			return item.toStack().getDamageValue() * 81L;
		}

		@Override
		protected long getCapacity(FluidVariant variant) {
			return 81000;
		}

		@Override
		protected ItemVariant getUpdatedVariant(ItemVariant item, FluidVariant resource, long amount) {
			ItemStack stack = item.toStack();
			stack.setDamageValue((int) (amount / 81));
			return ItemVariant.of(stack);
		}

		@Override
		protected boolean canInsert(FluidVariant resource) {
			return resource.equals(FluidVariant.of(Fluids.WATER));
		}
	}

	/** A block with a block entity. */
	private static final class BlockWithEntity extends Block implements EntityBlock {
		private final BlockEntityType.BlockEntitySupplier<?> entity;

		BlockWithEntity(BlockBehaviour.Properties properties, BlockEntityType.BlockEntitySupplier<?> entity) {
			super(properties);
			this.entity = entity;
		}

		@Override
		public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
			return entity.create(pos, state);
		}
	}

	/** The Fabric mod's block entity, with a storage of one kind of item, 64 at most, and one of a bucket and 40 droplets. */
	static final class FabricStorageEntity extends BlockEntity {
		final SingleVariantStorage<ItemVariant> items = new SingleVariantStorage<>() {
			@Override
			protected ItemVariant getBlankVariant() {
				return ItemVariant.blank();
			}

			@Override
			protected long getCapacity(ItemVariant variant) {
				return 64;
			}
		};
		final SingleVariantStorage<FluidVariant> fluids = new SingleVariantStorage<>() {
			@Override
			protected FluidVariant getBlankVariant() {
				return FluidVariant.blank();
			}

			@Override
			protected long getCapacity(FluidVariant variant) {
				return ODD_CAPACITY;
			}
		};

		FabricStorageEntity(BlockPos pos, BlockState state) {
			super(WithTransferApi.fabricType, pos, state);
		}
	}

	/** The Forge mod's block entity, with Forge's own two-slot item handler and 2000 mB tank as its capabilities. */
	static final class ForgeHandlerEntity extends BlockEntity {
		final ItemStackHandler items = new ItemStackHandler(2);
		final FluidTank tank = new FluidTank(2000);
		private final LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> items);
		private final LazyOptional<IFluidHandler> fluidCapability = LazyOptional.of(() -> tank);

		ForgeHandlerEntity(BlockPos pos, BlockState state) {
			super(WithTransferApi.forgeType, pos, state);
		}

		@Override
		public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
			if (capability == ForgeCapabilities.ITEM_HANDLER) return itemCapability.cast();
			if (capability == ForgeCapabilities.FLUID_HANDLER) return fluidCapability.cast();
			return super.getCapability(capability, side);
		}
	}
}
