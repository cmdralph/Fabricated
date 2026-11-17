package io.github.cmdralph.fabricated.testmod;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;

/** Whether a pack holds a resource, as 1.21.1 to 26.2 open a pack: one {@link PackResources} ({@code PackCreatorHookTest}). */
final class PackContents {
	private PackContents() {
	}

	static boolean holds(Pack pack, PackType type, Identifier id) {
		try (PackResources resources = pack.open()) {
			return resources.getResource(type, id) != null;
		}
	}
}
