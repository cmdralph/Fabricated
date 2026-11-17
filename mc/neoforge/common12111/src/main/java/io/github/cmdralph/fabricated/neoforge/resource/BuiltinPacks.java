package io.github.cmdralph.fabricated.neoforge.resource;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.fabric.impl.resource.pack.ModResourcePackCreator;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.repository.Pack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.resource.ResourcePackLoader;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.locating.IModFile;

/**
 * Offers the built-in packs Fabric mods register ({@code ResourceLoader.registerBuiltinPack}) to every pack
 * repository NeoForge builds, through the {@link ModResourcePackCreator} each gets, as on Fabric. The pack list is read
 * when the repository scans, so packs registered by initializers that run later are still found.
 *
 * <p>Activation: {@code ALWAYS_ENABLED} packs are required (cannot be turned off); {@code DEFAULT_ENABLED} data
 * packs are added to new worlds automatically, and {@code DEFAULT_ENABLED} resource packs are switched on the first
 * time they are offered ({@link DefaultResourcePacks}).
 *
 * <p>A creator a mod makes itself offers the mods' packs first ({@link #offerModPacks}), as upstream's does.
 */
public final class BuiltinPacks {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	/** The namespace upstream's mod packs take in their known-pack identity. */
	private static final String KNOWN_PACK_NAMESPACE = "vanilla";

	private BuiltinPacks() {
	}

	/** Gives the pack repository upstream's pack finder, whose instance mods' mixins hook (PROVIDER-22). */
	static void onAddPackFinders(AddPackFindersEvent event) {
		event.addRepositorySource(new ModResourcePackCreator(event.getPackType(), event.isTrusted()));
	}

	/**
	 * Offers the mods' packs for {@code type}, each on its own, to a {@link ModResourcePackCreator} a mod made itself:
	 * upstream's offers Fabric's mod packs so (PROVIDER-23). They are NeoForge's, as its own pack finder builds them for
	 * the pack repositories it makes, the packs it nests in its mod data or resources pack included.
	 */
	public static void offerModPacks(PackType type, Consumer<Pack> consumer) {
		Map<IModFile, Pack.ResourcesSupplier> packs = new LinkedHashMap<>();
		for (IModFileInfo file : ModList.get().getModFiles()) packs.put(file.getFile(), ResourcePackLoader.createPackForMod(file));
		ResourcePackLoader.buildPackFinder(packs, type).loadPacks(pack -> pack.streamSelfAndChildren().forEach(consumer));
	}

	/** Offers the built-in packs for {@code type} to a repository scanning its {@link ModResourcePackCreator}. */
	public static void offer(PackType type, Consumer<Pack> consumer) {
		String directory = type == PackType.SERVER_DATA ? "data" : "assets";
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
			if (pack.overlayRoot() != null) {
				// Translation changes a built-in pack's pack.mcmeta only to leave out its NeoForge build's sections (PACK-4).
				files = LayeredPackResources.of(files, new PathPackResources.PathResourcesSupplier(pack.overlayRoot()),
						Files.isRegularFile(pack.overlayRoot().resolve("pack.mcmeta")));
			}
			Pack created = Pack.readMetaAndCreate(info, files, type, new PackSelectionConfig(required, Pack.Position.TOP, false));
			if (created == null) {
				LOGGER.warn("Built-in pack {} has no valid pack.mcmeta for {}, skipped", pack.id(), type);
			} else {
				consumer.accept(created);
			}
		}
	}
}
