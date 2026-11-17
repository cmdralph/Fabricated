package io.github.cmdralph.fabricated.forge.entity;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.google.common.collect.ForwardingMap;

import net.minecraft.core.Holder;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code PoiTypes.TYPE_BY_STATE} as Forge 47's in-game mod has the field hold it (POI-4): Forge's own map, read as it
 * is, whose writes store a holder's point-of-interest type. Forge keeps the types where vanilla keeps their holders,
 * under the same erased type, and Fabric mods write it as vanilla's. Its values are typed {@code Object}, so that no
 * bridge method casts the holder a mod puts. Forge's registry callbacks write Forge's map itself, which this reads. A
 * write through an entry ({@code Map.Entry.setValue}) isn't seen.
 */
public final class PoiTypesByState extends ForwardingMap<BlockState, Object> {
	private final Map<BlockState, Object> forge;

	@SuppressWarnings("unchecked")
	private PoiTypesByState(Map<BlockState, ?> forge) {
		this.forge = (Map<BlockState, Object>) forge;
	}

	/** The view of Forge's map, typed as it. */
	@SuppressWarnings("unchecked")
	public static <V> Map<BlockState, V> of(Map<BlockState, V> forge) {
		return (Map<BlockState, V>) (Map<BlockState, ?>) new PoiTypesByState(forge);
	}

	@Override
	protected Map<BlockState, Object> delegate() {
		return forge;
	}

	/** The type a holder holds, and anything else as it is. */
	private static Object type(Object value) {
		return value instanceof Holder<?> holder ? holder.value() : value;
	}

	@Override
	public Object put(BlockState state, Object value) {
		return forge.put(state, type(value));
	}

	@Override
	public void putAll(Map<? extends BlockState, ?> map) {
		map.forEach(this::put);
	}

	@Override
	public Object putIfAbsent(BlockState state, Object value) {
		return forge.putIfAbsent(state, type(value));
	}

	@Override
	public Object replace(BlockState state, Object value) {
		return forge.replace(state, type(value));
	}

	@Override
	public boolean replace(BlockState state, Object oldValue, Object newValue) {
		return forge.replace(state, type(oldValue), type(newValue));
	}

	@Override
	public void replaceAll(BiFunction<? super BlockState, ? super Object, ?> function) {
		forge.replaceAll((state, type) -> type(function.apply(state, type)));
	}

	@Override
	public Object computeIfAbsent(BlockState state, Function<? super BlockState, ?> function) {
		return forge.computeIfAbsent(state, s -> type(function.apply(s)));
	}

	@Override
	public Object computeIfPresent(BlockState state, BiFunction<? super BlockState, ? super Object, ?> function) {
		return forge.computeIfPresent(state, (s, type) -> type(function.apply(s, type)));
	}

	@Override
	public Object compute(BlockState state, BiFunction<? super BlockState, ? super Object, ?> function) {
		return forge.compute(state, (s, type) -> type(function.apply(s, type)));
	}

	@Override
	public Object merge(BlockState state, Object value, BiFunction<? super Object, ? super Object, ?> function) {
		return forge.compute(state, (s, type) -> type(type == null ? value : function.apply(type, value)));
	}
}
