package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * CI test mod part for the energy bridge on NeoForge 21.1, when a run has Team Reborn's energy library (CI fetches it for
 * the server run with Fabric API): NeoForge code filling a Fabric mod's energy block, and Fabric code a NeoForge mod's,
 * each through the other's API: a NeoForge simulation and an aborted Fabric transaction leave them as they were.
 */
public final class EnergyTest implements ModInitializer {
	@Override
	public void onInitialize() {
		if (FabricLoader.getInstance().isModLoaded("team_reborn_energy")) WithLibrary.init();
	}

	/** Apart, so the test mod loads without the library. */
	private static final class WithLibrary {
		private static final BlockPos FABRIC_BLOCK = new BlockPos(8, 250, 14);
		private static final BlockPos NEO_BLOCK = FABRIC_BLOCK.east(2);

		private static Block block(String namespace, String name) {
			return Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath(namespace, name), new Block(BlockBehaviour.Properties.of()));
		}

		static void init() {
			BlockApiLookup<team.reborn.energy.api.EnergyStorage, @Nullable Direction> sided = BlockApiLookup.get(
					ResourceLocation.fromNamespaceAndPath("teamreborn", "sided_energy"), team.reborn.energy.api.EnergyStorage.class, Direction.class);
			Block fabricBlock = block("fabricated_testmod", "fabric_energy_block");
			Block neoBlock = block(TestMod.NEOFORGE_NAMESPACE, "neo_energy_block");
			FabricEnergy fabricEnergy = new FabricEnergy();
			// Through a fallback, as Team Reborn offers its machines' energy.
			sided.registerFallback((level, pos, state, blockEntity, side) -> state.is(fabricBlock) ? fabricEnergy : null);
			EnergyStorage neoEnergy = new EnergyStorage(1000);
			// A NeoForge capability, as a NeoForge mod would register it (on Fabricated's mod bus: this mod has none).
			ModList.get().getModContainerById("fabricated").map(container -> container.getEventBus()).ifPresent(bus ->
					bus.addListener(RegisterCapabilitiesEvent.class, event ->
							event.registerBlock(Capabilities.EnergyStorage.BLOCK, (level, pos, state, blockEntity, side) -> neoEnergy, neoBlock)));

			ServerLifecycleEvents.SERVER_STARTED.register(server -> {
				ServerLevel level = server.overworld();
				List<String> failed = new ArrayList<>();
				level.setBlockAndUpdate(FABRIC_BLOCK, fabricBlock.defaultBlockState());
				level.setBlockAndUpdate(NEO_BLOCK, neoBlock.defaultBlockState());

				IEnergyStorage asNeo = level.getCapability(Capabilities.EnergyStorage.BLOCK, FABRIC_BLOCK, Direction.UP);
				if (asNeo == null) {
					failed.add("neo_sees_fabric_energy");
				} else {
					if (asNeo.receiveEnergy(100, true) != 100) failed.add("neo_energy_insert");
					if (fabricEnergy.amount != 0) failed.add("neo_energy_simulate");
					asNeo.receiveEnergy(100, false);
					if (fabricEnergy.amount != 100 || asNeo.getEnergyStored() != 100) failed.add("neo_energy_execute");
				}

				team.reborn.energy.api.EnergyStorage asFabric = sided.find(level, NEO_BLOCK, Direction.UP);
				if (asFabric == null) {
					failed.add("fabric_sees_neo_energy");
				} else {
					try (Transaction tx = Transaction.openOuter()) {
						if (asFabric.insert(50, tx) != 50) failed.add("fabric_energy_insert");
					}
					if (neoEnergy.getEnergyStored() != 0) failed.add("fabric_energy_abort");
					try (Transaction tx = Transaction.openOuter()) {
						asFabric.insert(50, tx);
						tx.commit();
					}
					if (neoEnergy.getEnergyStored() != 50 || asFabric.getAmount() != 50) failed.add("fabric_energy_commit");
				}

				level.setBlockAndUpdate(FABRIC_BLOCK, Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(NEO_BLOCK, Blocks.AIR.defaultBlockState());
				if (failed.isEmpty()) {
					TestMod.LOGGER.info("FABRICATED-TEST energy OK");
				} else {
					TestMod.LOGGER.error("FABRICATED-TEST energy FAILED: {}", failed);
				}
			});
		}
	}

	/** A Fabric mod's energy storage, kept in a field, transaction-aware as Fabric's transfer API requires. */
	private static final class FabricEnergy extends SnapshotParticipant<Long> implements team.reborn.energy.api.EnergyStorage {
		private static final long CAPACITY = 1000;
		long amount;

		@Override
		public long insert(long maxAmount, TransactionContext transaction) {
			long inserted = Math.min(maxAmount, CAPACITY - amount);
			if (inserted > 0) {
				updateSnapshots(transaction);
				amount += inserted;
			}
			return inserted;
		}

		@Override
		public long extract(long maxAmount, TransactionContext transaction) {
			long extracted = Math.min(maxAmount, amount);
			if (extracted > 0) {
				updateSnapshots(transaction);
				amount -= extracted;
			}
			return extracted;
		}

		@Override
		public long getAmount() {
			return amount;
		}

		@Override
		public long getCapacity() {
			return CAPACITY;
		}

		@Override
		protected Long createSnapshot() {
			return amount;
		}

		@Override
		protected void readSnapshot(Long snapshot) {
			amount = snapshot;
		}
	}
}
