package io.github.cmdralph.fabricated.neoforge.resource;

import java.nio.file.Files;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.repository.Pack;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Offers the built-in packs Fabric mods register ({@code ResourceLoader.registerBuiltinPack}) to every pack
 * repository NeoForge builds. The pack list is read when the repository scans, so packs registered by
 * initializers that run later are still found.
 *
 * <p>Activation: {@code ALWAYS_ENABLED} packs are required (cannot be turned off); {@code DEFAULT_ENABLED} data
 * packs are added to new worlds automatically. Default-enabled client resource packs are not switched on
 * automatically (upstream patches the options screen for that); players enable them in the pack screen.
 */
final class BuiltinPacks {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	/** The namespace upstream's mod packs take in their known-pack identity. */
	private static final String KNOWN_PACK_NAMESPACE = "vanilla";

	private BuiltinPacks() {
	}

	static void onAddPackFinders(AddPackFindersEvent event) {
		PackType type = event.getPackType();
		String directory = type == PackType.SERVER_DATA ? "data" : "assets";
		event.addRepositorySource(consumer -> {
			for (ResourceRegistry.BuiltinPack pack : ResourceRegistry.builtinPacks()) {
				// Like upstream: only offer the pack for a type it has content for.
				if (!Files.isDirectory(pack.root().resolve(directory))) continue;
				boolean required = pack.activationType() == PackActivationType.ALWAYS_ENABLED;
				PackLocationInfo info = new PackLocationInfo(
						pack.id().toString(),
						pack.displayName(),
						BuiltinPackSources.create(pack.modName(), pack.activationType().isEnabledByDefault()),
						// Upstream's known-pack identity (PACK-3): vanilla marks the registry entries of a pack without one
						// experimental, and asks to confirm creating or loading a world with them.
						Optional.of(new KnownPack(KNOWN_PACK_NAMESPACE, pack.id().toString(), pack.modVersion())));
				Pack.ResourcesSupplier files = new PathPackResources.PathResourcesSupplier(pack.root());
				if (pack.overlayRoot() != null) files = LayeredPackResources.of(files, new PathPackResources.PathResourcesSupplier(pack.overlayRoot()));
				Pack created = Pack.readMetaAndCreate(info, files, type, new PackSelectionConfig(required, Pack.Position.TOP, false));
				if (created == null) {
					LOGGER.warn("Built-in pack {} has no valid pack.mcmeta for {}, skipped", pack.id(), type);
				} else {
					consumer.accept(created);
				}
			}
		});
	}
}
