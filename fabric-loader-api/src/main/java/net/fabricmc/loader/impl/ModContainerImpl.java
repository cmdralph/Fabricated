package net.fabricmc.loader.impl;

import net.fabricmc.loader.api.ModContainer;

/**
 * Fabric Loader's internal mod container class, which mods test containers against before using its internals
 * (FrozenLib). Fabricated's containers are its own, never this one, so such a test fails and the mod skips the
 * container; the class exists so the mod's code links.
 */
public abstract class ModContainerImpl implements ModContainer {
	private ModContainerImpl() {
	}
}
