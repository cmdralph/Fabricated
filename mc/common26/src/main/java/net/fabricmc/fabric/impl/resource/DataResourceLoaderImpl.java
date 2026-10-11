package net.fabricmc.fabric.impl.resource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import net.fabricmc.fabric.api.resource.v1.DataResourceLoader;

/** Server data loader: also accepts listeners created on every reload from that reload's registries. */
public final class DataResourceLoaderImpl extends ResourceLoaderImpl implements DataResourceLoader {
	public static final DataResourceLoaderImpl INSTANCE = new DataResourceLoaderImpl();

	private final Map<Identifier, Function<HolderLookup.Provider, PreparableReloadListener>> factories = new LinkedHashMap<>();

	private DataResourceLoaderImpl() {
		super(PackType.SERVER_DATA);
	}

	@Override
	protected synchronized boolean hasResourceReloader(Identifier id) {
		return super.hasResourceReloader(id) || this.factories.containsKey(id);
	}

	@Override
	public synchronized void registerReloadListener(Identifier id, Function<HolderLookup.Provider, PreparableReloadListener> factory) {
		Objects.requireNonNull(id, "The reloader identifier should not be null.");
		Objects.requireNonNull(factory, "The reloader factory should not be null.");
		this.checkUniqueResourceReloader(id);
		for (Map.Entry<Identifier, Function<HolderLookup.Provider, PreparableReloadListener>> entry : this.factories.entrySet()) {
			if (entry.getValue() == factory) {
				throw new IllegalStateException("Resource reloader factory with ID %s already in resource reloader factory set with ID %s!".formatted(id, entry.getKey()));
			}
		}
		this.factories.put(id, factory);
	}

	/** The listener factories, applied to the registries of each reload. */
	public synchronized Map<Identifier, Function<HolderLookup.Provider, PreparableReloadListener>> factories() {
		return new LinkedHashMap<>(this.factories);
	}
}
