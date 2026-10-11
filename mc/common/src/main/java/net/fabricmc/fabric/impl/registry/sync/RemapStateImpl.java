package net.fabricmc.fabric.impl.registry.sync;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import net.fabricmc.fabric.api.event.registry.RegistryIdRemapCallback;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

/** The raw ids that changed when NeoForge applied a registry snapshot (for example the server's, on joining it). */
public final class RemapStateImpl<T> implements RegistryIdRemapCallback.RemapState<T> {
	private final Int2IntMap rawIdChangeMap;
	private final Int2ObjectMap<Identifier> oldIdMap;
	private final Int2ObjectMap<Identifier> newIdMap = new Int2ObjectOpenHashMap<>();

	public RemapStateImpl(Registry<T> registry, Int2ObjectMap<Identifier> oldIdMap, Int2IntMap rawIdChangeMap) {
		this.rawIdChangeMap = rawIdChangeMap;
		this.oldIdMap = oldIdMap;
		for (Int2IntMap.Entry entry : rawIdChangeMap.int2IntEntrySet()) {
			this.newIdMap.put(entry.getIntValue(), registry.getKey(registry.byId(entry.getIntValue())));
		}
	}

	@Override
	public Int2IntMap getRawIdChangeMap() {
		return this.rawIdChangeMap;
	}

	@Override
	public Identifier getIdFromOld(int oldRawId) {
		return this.oldIdMap.get(oldRawId);
	}

	@Override
	public Identifier getIdFromNew(int newRawId) {
		return this.newIdMap.get(newRawId);
	}
}
