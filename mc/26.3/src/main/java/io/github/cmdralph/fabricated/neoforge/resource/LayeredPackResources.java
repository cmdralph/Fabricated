package io.github.cmdralph.fabricated.neoforge.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import net.minecraft.server.packs.OverlayedPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;

/**
 * A built-in pack's files with those translation changed over them (PACK-2), as vanilla layers a pack's overlays. The
 * pack's metadata is the mod's own, or translation's when it changed it ({@code translatedMetadata}: its NeoForge
 * build's sections left out, PACK-4).
 */
final class LayeredPackResources {
	private LayeredPackResources() {
	}

	static Pack.ResourcesSupplier of(Pack.ResourcesSupplier mod, Pack.ResourcesSupplier translated, boolean translatedMetadata) {
		return new Pack.ResourcesSupplier() {
			@Override
			public PackMetadataResources openMetadata(PackLocationInfo location) {
				return (translatedMetadata ? translated : mod).openMetadata(location);
			}

			@Override
			public Stream<PackResources> openResources(PackLocationInfo location, Pack.Metadata metadata) {
				List<PackResources> packs = new ArrayList<>(mod.openResources(location, metadata).toList());
				translated.openResources(location, metadata).forEach(packs::add);
				return Stream.of(new OverlayedPackResources(packs.getFirst(), packs.subList(1, packs.size())));
			}
		};
	}
}
