package io.github.cmdralph.fabricated.neoforge.resource;

import java.util.List;

import net.minecraft.server.packs.CompositePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;

/**
 * A built-in pack's files with those translation changed over them (PACK-2), as vanilla layers a pack's overlays: the
 * pack's metadata is the mod's own.
 */
final class LayeredPackResources {
	private LayeredPackResources() {
	}

	static Pack.ResourcesSupplier of(Pack.ResourcesSupplier mod, Pack.ResourcesSupplier translated) {
		return new Pack.ResourcesSupplier() {
			@Override
			public PackResources openPrimary(PackLocationInfo location) {
				return new CompositePackResources(mod.openPrimary(location), List.of(translated.openPrimary(location)));
			}

			@Override
			public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
				return new CompositePackResources(mod.openFull(location, metadata), List.of(translated.openFull(location, metadata)));
			}
		};
	}
}
