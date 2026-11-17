package io.github.cmdralph.fabricated.testmod;

import java.util.List;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;

/**
 * Whether a pack holds a resource, as 26.3 opens a pack: a stream of {@link PackResources}, each closed here
 * ({@code PackCreatorHookTest}).
 */
final class PackContents {
	private PackContents() {
	}

	static boolean holds(Pack pack, PackType type, Identifier id) {
		List<PackResources> opened = pack.open().toList();
		try {
			return opened.stream().anyMatch(resources -> resources.getResource(type, id) != null);
		} finally {
			opened.forEach(PackResources::close);
		}
	}
}
