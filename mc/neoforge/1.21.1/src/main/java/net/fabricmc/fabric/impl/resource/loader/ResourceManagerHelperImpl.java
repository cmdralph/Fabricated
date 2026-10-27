package net.fabricmc.fabric.impl.resource.loader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.core.runtime.ModContainerImpl;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;

/**
 * Fabricated implementation of {@code fabric-resource-loader-v0}'s registry (Minecraft 1.21.1), under upstream's name,
 * which its API calls. Registration follows upstream (an id once per pack type; registry-aware listeners for server data
 * only); the listeners join NeoForge's reload events ({@code ResourceLoaderBridge}) instead of upstream's mixins, after
 * vanilla's own, in an order that puts each after those it depends on, as upstream sorts them. Built-in packs are offered
 * through NeoForge's pack finders.
 */
public final class ResourceManagerHelperImpl implements ResourceManagerHelper {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final Map<PackType, ResourceManagerHelperImpl> HELPERS = new EnumMap<>(PackType.class);
	private static final List<BuiltinPack> BUILTIN_PACKS = new ArrayList<>();
	/** Vanilla's server listeners, which NeoForge runs before every listener its event adds. */
	private static final Set<ResourceLocation> VANILLA_SERVER = Set.of(ResourceReloadListenerKeys.RECIPES, ResourceReloadListenerKeys.TAGS,
			ResourceReloadListenerKeys.FUNCTIONS, ResourceReloadListenerKeys.ADVANCEMENTS);
	/** Vanilla's client listeners, all registered before NeoForge's client event. */
	private static final Set<ResourceLocation> VANILLA_CLIENT = Set.of(ResourceReloadListenerKeys.SOUNDS, ResourceReloadListenerKeys.FONTS,
			ResourceReloadListenerKeys.MODELS, ResourceReloadListenerKeys.LANGUAGES, ResourceReloadListenerKeys.TEXTURES);

	/**
	 * A pack inside a mod, registered by {@code registerBuiltinResourcePack}.
	 *
	 * @param modVersion  the mod's version, part of the pack's known-pack identity
	 * @param overlayRoot the pack's directory in the mod's translation overlay, whose files take priority, or {@code null}
	 */
	public record BuiltinPack(ResourceLocation id, String modName, String modVersion, Path root, @Nullable Path overlayRoot, Component displayName,
			ResourcePackActivationType activationType) {
	}

	private final PackType type;
	private final Map<ResourceLocation, Function<HolderLookup.@Nullable Provider, IdentifiableResourceReloadListener>> factories = new LinkedHashMap<>();

	private ResourceManagerHelperImpl(PackType type) {
		this.type = type;
	}

	public static synchronized ResourceManagerHelperImpl get(PackType type) {
		return HELPERS.computeIfAbsent(type, ResourceManagerHelperImpl::new);
	}

	@Override
	public void registerReloadListener(IdentifiableResourceReloadListener listener) {
		Objects.requireNonNull(listener, "listener");
		add(listener.getFabricId(), registries -> listener);
	}

	@Override
	public void registerReloadListener(ResourceLocation identifier, Function<HolderLookup.Provider, IdentifiableResourceReloadListener> listenerFactory) {
		if (this.type == PackType.CLIENT_RESOURCES) {
			throw new IllegalArgumentException("Cannot register a registry listener for the client resource type!");
		}
		Objects.requireNonNull(listenerFactory, "listenerFactory");
		add(identifier, registries -> {
			IdentifiableResourceReloadListener listener = listenerFactory.apply(registries);
			if (!identifier.equals(listener.getFabricId())) {
				throw new IllegalStateException("Listener factory for " + identifier + " created a listener with ID " + listener.getFabricId());
			}
			return listener;
		});
	}

	private synchronized void add(ResourceLocation id, Function<HolderLookup.@Nullable Provider, IdentifiableResourceReloadListener> factory) {
		// As upstream: a second registration of an id is ignored with a warning, not an error.
		if (this.factories.putIfAbsent(id, factory) != null) {
			LOGGER.warn("Tried to register resource reload listener {} twice!", id);
		}
	}

	/**
	 * This reload's Fabric listeners, each after the listeners it depends on; one whose dependencies are never met is left
	 * out and reported, as upstream logs it.
	 *
	 * @param registries the reload's registries (server data), or {@code null} (client resources)
	 */
	public List<IdentifiableResourceReloadListener> listeners(HolderLookup.@Nullable Provider registries, Consumer<String> problems) {
		List<IdentifiableResourceReloadListener> pending = new ArrayList<>();
		synchronized (this) {
			for (Map.Entry<ResourceLocation, Function<HolderLookup.@Nullable Provider, IdentifiableResourceReloadListener>> entry : this.factories.entrySet()) {
				// A factory is mod code: if it throws, this reload fails, as it would on Fabric. Say whose it was.
				try {
					pending.add(entry.getValue().apply(registries));
				} catch (RuntimeException e) {
					throw new IllegalStateException("Fabric reload listener factory " + entry.getKey() + " failed", e);
				}
			}
		}
		Set<ResourceLocation> resolved = new HashSet<>(this.type == PackType.SERVER_DATA ? VANILLA_SERVER : VANILLA_CLIENT);
		List<IdentifiableResourceReloadListener> sorted = new ArrayList<>(pending.size());
		int lastSize = -1;
		while (sorted.size() != lastSize) {
			lastSize = sorted.size();
			for (Iterator<IdentifiableResourceReloadListener> it = pending.iterator(); it.hasNext(); ) {
				IdentifiableResourceReloadListener listener = it.next();
				if (resolved.containsAll(listener.getFabricDependencies())) {
					resolved.add(listener.getFabricId());
					sorted.add(listener);
					it.remove();
				}
			}
		}
		for (IdentifiableResourceReloadListener listener : pending) {
			problems.accept("Reload listener " + listener.getFabricId() + " was left out: it depends on " + listener.getFabricDependencies()
					+ ", not all of which are registered");
		}
		return sorted;
	}

	public static boolean registerBuiltinResourcePack(ResourceLocation id, String subPath, ModContainer container, ResourcePackActivationType activationType) {
		return registerBuiltinResourcePack(id, subPath, container, Component.literal(id.getNamespace() + "/" + id.getPath()), activationType);
	}

	/** @return whether the mod contains the pack directory */
	public static boolean registerBuiltinResourcePack(ResourceLocation id, String subPath, ModContainer container, Component displayName,
			ResourcePackActivationType activationType) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(activationType, "activationType");
		Optional<Path> root = container.findPath(subPath).filter(Files::isDirectory);
		if (root.isEmpty()) return false;
		// Translation changed some of the pack's files (PACK-2): its data without the NeoForge build's conditions.
		Path overlayRoot = container instanceof ModContainerImpl fabric ? fabric.overlayDirectory(subPath).orElse(null) : null;
		synchronized (BUILTIN_PACKS) {
			BUILTIN_PACKS.add(new BuiltinPack(id, container.getMetadata().getName(), container.getMetadata().getVersion().getFriendlyString(), root.get(),
					overlayRoot, displayName, activationType));
		}
		return true;
	}

	public static List<BuiltinPack> builtinPacks() {
		synchronized (BUILTIN_PACKS) {
			return List.copyOf(BUILTIN_PACKS);
		}
	}
}
