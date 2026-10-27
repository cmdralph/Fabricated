package io.github.cmdralph.fabricated.forgetest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/**
 * The forge test mod's part for the bridge between Fabric's storages and Forge's item and fluid handler capabilities
 * (with Fabric API's jar only, which provides fabric-transfer-api-v1), forge-legacy's check in Mojang's names. This mod's
 * block entity offers a Fabric item storage and a Fabric fluid storage, which Forge's code reaches through the block
 * entity's capabilities; a block entity registered under a namespace no Fabric mod has, standing in for a Forge mod's,
 * offers Forge's own {@code ItemStackHandler} and {@code FluidTank} through its capabilities, as a Forge mod does, which
 * Fabric's code reaches through {@code ItemStorage.SIDED} and {@code FluidStorage.SIDED}: an aborted transaction leaves
 * the handler as it was, two insertions in one transaction take no more than the slots hold together, and a committed
 * one fills them.
 */
public final class ForgeTransferTest {
	/** A namespace no Fabric mod has: the bridge tells Forge mods' blocks from Fabric mods' by namespace. */
	private static final String FORGE_NAMESPACE = "fabricated_forge_test";
	/** Holds a bucket and 40 droplets, so a millibucket-sized fill can't fill it exactly. */
	private static final long ODD_CAPACITY = 81000 + 40;

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

		private WithTransferApi() {
		}

		static void register() {
			Identifier fabricId = Identifier.fromNamespaceAndPath(ForgeTestMod.MOD_ID, "storage_block");
			Identifier forgeId = Identifier.fromNamespaceAndPath(FORGE_NAMESPACE, "handler_block");
			fabricBlock = block(fabricId, FabricStorageEntity::new);
			forgeBlock = block(forgeId, ForgeHandlerEntity::new);
			fabricType = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, fabricId, new BlockEntityType<>(FabricStorageEntity::new, Set.of(fabricBlock)));
			forgeType = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, forgeId, new BlockEntityType<>(ForgeHandlerEntity::new, Set.of(forgeBlock)));
			ItemStorage.SIDED.registerForBlocks((level, pos, state, blockEntity, side) ->
					blockEntity instanceof FabricStorageEntity entity ? entity.items : null, fabricBlock);
			FluidStorage.SIDED.registerForBlocks((level, pos, state, blockEntity, side) ->
					blockEntity instanceof FabricStorageEntity entity ? entity.fluids : null, fabricBlock);
		}

		private static Block block(Identifier id, BlockEntityType.BlockEntitySupplier<?> entity) {
			ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
			return Registry.register(BuiltInRegistries.BLOCK, key, new BlockWithEntity(BlockBehaviour.Properties.of().setId(key), entity));
		}

		static void check(ServerLevel level) {
			BlockPos fabricPos = ForgeTestMod.testSite(level, 16, 4);
			BlockPos forgePos = ForgeTestMod.testSite(level, 18, 4);
			List<String> failed = new ArrayList<>();
			level.setBlockAndUpdate(fabricPos, fabricBlock.defaultBlockState());
			level.setBlockAndUpdate(forgePos, forgeBlock.defaultBlockState());
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
			level.setBlockAndUpdate(fabricPos, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(forgePos, Blocks.AIR.defaultBlockState());
			if (failed.isEmpty()) {
				ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST storages OK");
			} else {
				ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST storages: {}", String.join("; ", failed));
			}
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
				if (taken.getCount() != 4 || !taken.is(Items.STONE) || entity.items.amount != 6) {
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
		public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
			if (capability == ForgeCapabilities.ITEM_HANDLER) return itemCapability.cast();
			if (capability == ForgeCapabilities.FLUID_HANDLER) return fluidCapability.cast();
			return super.getCapability(capability, side);
		}
	}
}
