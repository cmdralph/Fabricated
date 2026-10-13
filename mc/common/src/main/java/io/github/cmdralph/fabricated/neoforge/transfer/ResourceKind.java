package io.github.cmdralph.fabricated.neoforge.transfer;

import java.util.function.Function;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.TransferVariant;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;

/**
 * A resource both transfer APIs move: how its Fabric variant and NeoForge resource convert, and how many Fabric units
 * make one NeoForge unit (fluids: 81 droplets per millibucket; items: 1).
 */
record ResourceKind<F extends TransferVariant<?>, N extends Resource>(Function<N, F> toFabric, Function<F, N> toNeo, N neoEmpty,
		long unit) {
	static final ResourceKind<ItemVariant, ItemResource> ITEMS = new ResourceKind<>(
			r -> r.isEmpty() ? ItemVariant.blank() : ItemVariant.of(r.getItem(), r.getComponentsPatch()),
			v -> v.isBlank() ? ItemResource.EMPTY : ItemResource.of(v.getItem(), TransferCompat.patch(v)),
			ItemResource.EMPTY, 1);
	static final ResourceKind<FluidVariant, FluidResource> FLUIDS = new ResourceKind<>(
			r -> r.isEmpty() ? FluidVariant.blank() : FluidVariant.of(r.getFluid(), r.getComponentsPatch()),
			v -> v.isBlank() ? FluidResource.EMPTY : FluidResource.of(v.getFluid(), TransferCompat.patch(v)),
			FluidResource.EMPTY, FluidConstants.BUCKET / 1000);

	N neo(F variant) {
		return toNeo.apply(variant);
	}

	F fabric(N resource) {
		return toFabric.apply(resource);
	}

	/** Fabric units to NeoForge's (int) units, rounding down. */
	int toNeoAmount(long fabricAmount) {
		return (int) Math.min(Integer.MAX_VALUE, fabricAmount / unit);
	}
}
