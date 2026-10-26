package net.fabricmc.loader.impl;

import net.fabricmc.loader.api.ModContainer;

/**
 * Fabric Loader's internal mod container class, which mods test containers against before using its internals
 * (FrozenLib, before adding entrypoints to the loader's storage). Fabricated's containers extend it, so such a test
 * passes as it does on Fabric (LOADER-4). Fabric Loader's own internal methods (its loader metadata, code source paths)
 * are not here: a mod calling one fails to link, which the report lists among its linkage warnings.
 */
public abstract class ModContainerImpl implements ModContainer {
	protected ModContainerImpl() {
	}
}
