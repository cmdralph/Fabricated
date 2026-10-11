package net.fabricmc.fabric.impl.resource;

import java.util.function.Function;

import net.fabricmc.fabric.api.resource.v1.DataResourceLoader;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;

/** Server data loader: also accepts listeners created on every reload from that reload's registries. */
public final class DataResourceLoaderImpl extends ResourceLoaderImpl implements DataResourceLoader {
	public static final DataResourceLoaderImpl INSTANCE = new DataResourceLoaderImpl();

	private DataResourceLoaderImpl() {
		super(PackType.SERVER_DATA);
	}

	@Override
	public void registerReloadListener(Identifier id, Function<HolderLookup.Provider, PreparableReloadListener> factory) {
		this.registry.addFactory(id, factory);
	}
}
