package io.github.cmdralph.fabricated.neoforge.registry;

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.impl.registry.sync.RegistryAttributeImpl;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.registries.callback.AddCallback;

/**
 * Native {@code fabric-registry-sync-v0}. NeoForge already syncs registry ids to clients and checks that both sides
 * have the same entries, so Fabric's own sync protocol isn't needed. What remains is Fabric's API, mapped onto NeoForge:
 * <ul>
 *   <li>{@code FabricRegistryBuilder}: new registries go into the root registry while NeoForge's registration events
 *       run (when Fabric initializers run); NeoForge then freezes them with the rest. {@code SYNCED} sets NeoForge's
 *       sync flag.</li>
 *   <li>{@code RegistryEntryAddedCallback} / {@code RegistryIdRemapCallback}: NeoForge's registry callbacks
 *       ({@link RegistryEvents}).</li>
 *   <li>{@code DynamicRegistries}: NeoForge's data-pack registry lists ({@link NeoForgeRegistryInternals}).</li>
 *   <li>{@code DynamicRegistrySetupCallback}: fired once the dynamic registries are loaded ({@link DynamicRegistrySetup}).</li>
 *   <li>{@code FabricRegistry#addAlias}: NeoForge's own registry aliases (an interface added by a mixin).</li>
 * </ul>
 */
public final class RegistrySyncBridge {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	/** True while NeoForge's registration events run: the only time new registries may join the root registry. */
	private static volatile boolean registrationOpen;
	/** True from common setup on, when NeoForge's registration has ended. */
	private static volatile boolean registrationEnded;
	private static boolean rootOpened;
	/** Whether the root registry's callback is added (only read and written where registration events are posted). */
	private static boolean syncedCallbackAdded;

	private RegistrySyncBridge() {
	}

	/**
	 * Must be registered before Fabric initializers are hooked to {@code RegisterEvent}, so the registration window is
	 * known to be open when they run.
	 *
	 * @param problems receives one line per problem worth a place in the report
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void register(IEventBus modBus, Dist dist, Consumer<String> problems) {
		modBus.addListener(EventPriority.HIGHEST, RegisterEvent.class, e -> {
			if (!syncedCallbackAdded) {
				syncedCallbackAdded = true;
				// A registry Fabric code marked SYNCED before registering it (FabricRegistryBuilder does) gets NeoForge's
				// flag. Added here and not in the constructor: FML constructs mods in parallel, and the root registry's
				// callback list is a plain ArrayList. Registration events are posted one at a time, and this first one
				// comes before any Fabric initializer.
				((Registry) BuiltInRegistries.REGISTRY).addCallback((AddCallback<Registry<?>>) (root, id, key, registry) -> {
					if (RegistryAttributeImpl.markedSynced(key)) markSynced(registry);
				});
			}
			registrationOpen = true;
		});
		modBus.addListener(EventPriority.LOWEST, RegisterEvent.class, e -> closeRoot());
		modBus.addListener(FMLCommonSetupEvent.class, e -> {
			registrationOpen = false;
			registrationEnded = true;
			// A parallel event: freezing a vanilla registry belongs on the main thread.
			e.enqueueWork(RegistrySyncBridge::closeRoot);
		});
		NeoForge.EVENT_BUS.addListener(ServerAboutToStartEvent.class, e -> DynamicRegistrySetup.fire(e.getServer().registryAccess(), "server", problems));
		if (dist.isClient()) ClientRegistrySync.register(problems);
	}

	/** {@code FabricRegistryBuilder}'s root registry: opened for registration while NeoForge's registration events run. */
	@SuppressWarnings("unchecked")
	public static WritableRegistry<WritableRegistry<?>> rootForRegistration() {
		Registry<?> root = BuiltInRegistries.REGISTRY;
		if (registrationOpen && root instanceof MappedRegistry<?> mapped) {
			synchronized (RegistrySyncBridge.class) {
				mapped.unfreeze(false);
				rootOpened = true;
			}
		} else if (!registrationOpen) {
			LOGGER.warn("A Fabric mod is creating a registry outside NeoForge's registration events; registries are frozen by now, so this will fail");
		}
		return (WritableRegistry<WritableRegistry<?>>) (Object) root;
	}

	/**
	 * Opens a frozen built-in registry that vanilla's own code writes to before NeoForge's registration has ended.
	 * NeoForge freezes the root after its {@code NewRegistryEvent}, and processing its data pack registry event then loads
	 * the class Wover hooks to create its registries ({@code BuiltInRegistries.registerSimple}) and register entries in
	 * vanilla ones, which works on Fabric. NeoForge's registration events then open, run over and freeze every registry in
	 * the root (read in NeoForge's {@code CommonModLoader}), so another registry is opened only before they start; the root
	 * is closed again with the root {@link #rootForRegistration} opens.
	 */
	public static synchronized void openForEarlyWrite(MappedRegistry<?> registry) {
		if (registrationEnded) return;
		if (registry == BuiltInRegistries.REGISTRY) {
			registry.unfreeze(false);
			rootOpened = true;
		} else if (!registrationOpen && BuiltInRegistries.REGISTRY.getValue(registry.key().identifier()) == registry) {
			registry.unfreeze(false);
		}
	}

	private static synchronized void closeRoot() {
		if (rootOpened && BuiltInRegistries.REGISTRY instanceof MappedRegistry<?> mapped) {
			mapped.freeze();
			rootOpened = false;
		}
	}

	/** Called when Fabric code adds {@code SYNCED} to a registry's attributes. */
	public static void markSynced(ResourceKey<?> key) {
		Registry<?> registry = BuiltInRegistries.REGISTRY.getValue(key.identifier());
		if (registry != null) markSynced(registry);
	}

	private static void markSynced(Registry<?> registry) {
		if (!registry.doesSync() && !NeoForgeRegistryInternals.setSync(registry)) {
			LOGGER.warn("Registry {} is marked SYNCED by a Fabric mod, but NeoForge could not be told to sync it", registry.key().identifier());
		}
	}

	/** Whether NeoForge syncs the (static) registry with this key to clients. */
	public static boolean syncedByNeoForge(ResourceKey<?> key) {
		Registry<?> registry = BuiltInRegistries.REGISTRY.getValue(key.identifier());
		return registry != null && registry.doesSync();
	}
}
