package net.fabricmc.fabric.impl.resource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.ModContainer;

/**
 * Fabricated implementation of Fabric's resource loader. It only records what Fabric mods register; on every
 * reload Fabricated hands the listeners and their orderings to NeoForge's sorted reload-listener events
 * ({@code ResourceLoaderBridge}), and the built-in packs to {@code AddPackFindersEvent}.
 *
 * <p>Registration rules (unique ids, unique listener instances, no self-ordering) match upstream.
 */
public class ResourceLoaderImpl implements ResourceLoader {
	private static final Map<PackType, ResourceLoaderImpl> LOADERS = new EnumMap<>(PackType.class);
	private static final List<BuiltinPack> BUILTIN_PACKS = new ArrayList<>();

	/** A pack inside a mod ({@code resourcepacks/<name>} by default), registered by {@code ResourceLoader.registerBuiltinPack}. */
	public record BuiltinPack(Identifier id, String modName, Path root, Component displayName, PackActivationType activationType) {
	}

	/** A Fabric ordering: {@code first} must run before {@code second}. */
	public record Ordering(Identifier first, Identifier second) {
	}

	private final PackType type;
	private final Map<Identifier, PreparableReloadListener> listeners = new LinkedHashMap<>();
	private final Set<Ordering> orderings = new LinkedHashSet<>();

	protected ResourceLoaderImpl(PackType type) {
		this.type = type;
	}

	public static synchronized ResourceLoaderImpl get(PackType type) {
		Objects.requireNonNull(type, "The pack type should not be null.");
		if (type == PackType.SERVER_DATA) return DataResourceLoaderImpl.INSTANCE;
		return LOADERS.computeIfAbsent(type, ResourceLoaderImpl::new);
	}

	protected synchronized boolean hasResourceReloader(Identifier id) {
		return this.listeners.containsKey(id);
	}

	protected final void checkUniqueResourceReloader(Identifier id) {
		if (this.hasResourceReloader(id)) {
			throw new IllegalStateException("Tried to register resource listener %s twice!".formatted(id));
		}
	}

	@Override
	public synchronized void registerReloadListener(Identifier id, PreparableReloadListener listener) {
		Objects.requireNonNull(id, "The listener identifier should not be null.");
		Objects.requireNonNull(listener, "The listener should not be null.");
		this.checkUniqueResourceReloader(id);
		for (Map.Entry<Identifier, PreparableReloadListener> entry : this.listeners.entrySet()) {
			if (entry.getValue() == listener) {
				throw new IllegalStateException("Resource listener with ID %s already in resource listener set with ID %s!".formatted(id, entry.getKey()));
			}
		}
		this.listeners.put(id, listener);
	}

	@Override
	public synchronized void addListenerOrdering(Identifier firstListener, Identifier secondListener) {
		Objects.requireNonNull(firstListener, "The first listener identifier should not be null.");
		Objects.requireNonNull(secondListener, "The second listener identifier should not be null.");
		if (firstListener.equals(secondListener)) {
			throw new IllegalArgumentException("Tried to add a phase that depends on itself.");
		}
		this.orderings.add(new Ordering(firstListener, secondListener));
	}

	/** The listeners to add on a reload, in registration order. */
	public synchronized Map<Identifier, PreparableReloadListener> listeners() {
		return new LinkedHashMap<>(this.listeners);
	}

	public synchronized List<Ordering> orderings() {
		return List.copyOf(this.orderings);
	}

	public PackType type() {
		return this.type;
	}

	public static boolean registerBuiltinPack(Identifier id, String subPath, ModContainer container, PackActivationType activationType) {
		return registerBuiltinPack(id, subPath, container, Component.literal(id.getNamespace() + '/' + id.getPath()), activationType);
	}

	/**
	 * Records a built-in pack. Same signature as upstream (Fabric API's deprecated resource-loader-v0 calls it).
	 *
	 * @return whether the mod contains the pack directory
	 */
	public static boolean registerBuiltinPack(Identifier id, String subPath, ModContainer container, Component displayName, PackActivationType activationType) {
		Objects.requireNonNull(id, "The pack identifier should not be null.");
		Objects.requireNonNull(activationType, "The activation type should not be null.");
		Optional<Path> root = container.findPath(subPath).filter(Files::isDirectory);
		if (root.isEmpty()) return false;
		synchronized (BUILTIN_PACKS) {
			BUILTIN_PACKS.add(new BuiltinPack(id, container.getMetadata().getName(), root.get(), displayName, activationType));
		}
		return true;
	}

	public static List<BuiltinPack> builtinPacks() {
		synchronized (BUILTIN_PACKS) {
			return List.copyOf(BUILTIN_PACKS);
		}
	}
}
