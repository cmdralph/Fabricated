package net.fabricmc.loader.impl.metadata;

/**
 * Fabric Loader's internal description of an entrypoint, which mods adding entrypoints to Fabric Loader's own storage
 * implement (FrozenLib, from a mixin plugin): see {@link net.fabricmc.loader.impl.entrypoint.EntrypointStorage}.
 */
public interface EntrypointMetadata {
	String getAdapter();

	String getValue();
}
