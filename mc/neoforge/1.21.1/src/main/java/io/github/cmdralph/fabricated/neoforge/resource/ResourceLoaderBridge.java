package io.github.cmdralph.fabricated.neoforge.resource;

import java.nio.file.Files;
import java.util.Optional;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.fabric.impl.resource.loader.BuiltinModResourcePackSource;
import net.fabricmc.fabric.impl.resource.loader.ResourceManagerHelperImpl;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.repository.Pack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Native {@code fabric-resource-loader-v0} on NeoForge 21.1: Fabric reload listeners join NeoForge's
 * {@code AddReloadListenerEvent} (every data reload, with that reload's registries) and
 * {@code RegisterClientReloadListenersEvent} (once, as the client starts), after vanilla's listeners and each after the
 * ones it depends on ({@link ResourceManagerHelperImpl#listeners}); built-in packs are offered through
 * {@code AddPackFindersEvent}. Mod resources themselves need nothing: NeoForge already loads the {@code assets/} and
 * {@code data/} of every mod, Fabric mods included.
 *
 * <p>Runs at the lowest priority, after NeoForge mods' listeners, as upstream adds Fabric's after every other.
 */
public final class ResourceLoaderBridge {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	/** The namespace upstream's mod packs take in their known-pack identity. */
	private static final String KNOWN_PACK_NAMESPACE = "vanilla";

	private ResourceLoaderBridge() {
	}

	/** @param problems receives one line per problem worth a place in the report */
	public static void register(IEventBus modBus, Dist dist, Consumer<String> problems) {
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, AddReloadListenerEvent.class, event -> {
			for (IdentifiableResourceReloadListener listener : ResourceManagerHelperImpl.get(PackType.SERVER_DATA).listeners(event.getRegistryAccess(), problems)) {
				event.addListener(listener);
			}
		});
		modBus.addListener(AddPackFindersEvent.class, ResourceLoaderBridge::onAddPackFinders);
		if (dist.isClient()) {
			modBus.addListener(EventPriority.LOWEST, RegisterClientReloadListenersEvent.class, event -> {
				for (IdentifiableResourceReloadListener listener : ResourceManagerHelperImpl.get(PackType.CLIENT_RESOURCES).listeners(null, problems)) {
					event.registerReloadListener(listener);
				}
			});
		}
	}

	/**
	 * Offers the built-in packs Fabric mods register to every pack repository NeoForge builds; the list is read when the
	 * repository scans, so packs registered by initializers that run later are still found. {@code ALWAYS_ENABLED} packs are
	 * required; {@code DEFAULT_ENABLED} data packs are added to new worlds automatically. Default-enabled client resource
	 * packs are not switched on automatically (upstream patches the options screen for that).
	 */
	private static void onAddPackFinders(AddPackFindersEvent event) {
		PackType type = event.getPackType();
		String directory = type == PackType.SERVER_DATA ? "data" : "assets";
		event.addRepositorySource(consumer -> {
			for (ResourceManagerHelperImpl.BuiltinPack pack : ResourceManagerHelperImpl.builtinPacks()) {
				// Like upstream: only offer the pack for a type it has content for.
				if (!Files.isDirectory(pack.root().resolve(directory))) continue;
				boolean required = pack.activationType() == ResourcePackActivationType.ALWAYS_ENABLED;
				PackLocationInfo info = new PackLocationInfo(
						pack.id().toString(),
						pack.displayName(),
						new BuiltinModResourcePackSource(pack.modName(), pack.activationType().isEnabledByDefault()),
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
		});
	}
}
