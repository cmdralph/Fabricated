package io.github.cmdralph.fabricated.forge.entity;

import java.util.Collection;
import java.util.Set;

import com.google.common.collect.ForwardingSet;

import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code PoiTypes.ALL_STATES} as Forge 43's in-game mod has the field hold it: the block states of a point-of-interest
 * type, which vanilla 1.19.2 keeps in a set of its own, and Forge 43 reads from its map of each state's type, as a view
 * that refuses writes. Fabric API 0.77's {@code PointOfInterestHelper} adds a new type's states there before it
 * registers the type, which threw. Adding a state changes nothing: it is in the set once its type is registered, as
 * Forge's registry callback maps its states then.
 */
public final class PoiTypesAllStates extends ForwardingSet<BlockState> {
	private final Set<BlockState> forge;

	private PoiTypesAllStates(Set<BlockState> forge) {
		this.forge = forge;
	}

	public static Set<BlockState> of(Set<BlockState> forge) {
		return new PoiTypesAllStates(forge);
	}

	@Override
	protected Set<BlockState> delegate() {
		return forge;
	}

	@Override
	public boolean add(BlockState state) {
		return false;
	}

	@Override
	public boolean addAll(Collection<? extends BlockState> states) {
		return false;
	}
}
