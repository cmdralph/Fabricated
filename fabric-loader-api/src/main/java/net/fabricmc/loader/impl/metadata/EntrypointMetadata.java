package net.fabricmc.loader.impl.metadata;

/**
 * Fabric Loader's internal description of an entrypoint, which mods adding entrypoints to Fabric Loader's own storage
 * implement (FrozenLib, from a mixin plugin). It exists so their code links: {@link
 * net.fabricmc.loader.impl.entrypoint.EntrypointStorage} has no instance mods can reach, so they take the fallback
 * they keep for a loader without one.
 */
public interface EntrypointMetadata {
	String getAdapter();

	String getValue();
}
