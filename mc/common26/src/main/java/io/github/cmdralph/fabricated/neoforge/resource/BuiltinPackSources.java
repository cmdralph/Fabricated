package io.github.cmdralph.fabricated.neoforge.resource;

import net.fabricmc.fabric.impl.resource.pack.BuiltinModPackSource;
import net.minecraft.server.packs.repository.PackSource;

/** The pack source of Fabric built-in packs, under the class name this version's Fabric API modules test for. */
final class BuiltinPackSources {
	private BuiltinPackSources() {
	}

	static PackSource create(String modName, boolean addAutomatically) {
		return new BuiltinModPackSource(modName, addAutomatically);
	}
}
