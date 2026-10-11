package io.github.cmdralph.fabricated.neoforge.resource;

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
import java.util.function.Function;

import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;

/**
 * What Fabric mods register with {@code fabric-resource-loader-v1}, per pack type. Fabric's {@code ResourceLoaderImpl}
 * (whose method names differ between Minecraft versions) only forwards here; {@link ResourceLoaderBridge} reads it on
 * every reload. Registration rules (unique ids, unique listener instances, no self-ordering) and their messages match
 * upstream.
 */
public final class ResourceRegistry {
	private static final Map<PackType, ResourceRegistry> REGISTRIES = new EnumMap<>(PackType.class);
	private static final List<BuiltinPack> BUILTIN_PACKS = new ArrayList<>();

	/** A Fabric ordering: {@code first} must run before {@code second}. */
	public record Ordering(Identifier first, Identifier second) {
	}

	/** A pack inside a mod ({@code resourcepacks/<name>} by default), registered by {@code ResourceLoader.registerBuiltinPack}. */
	public record BuiltinPack(Identifier id, String modName, Path root, Component displayName, PackActivationType activationType) {
	}

	private final Map<Identifier, PreparableReloadListener> listeners = new LinkedHashMap<>();
	private final Map<Identifier, Function<HolderLookup.Provider, PreparableReloadListener>> factories = new LinkedHashMap<>();
	private final Set<Ordering> orderings = new LinkedHashSet<>();

	private ResourceRegistry() {
	}

	public static synchronized ResourceRegistry of(PackType type) {
		Objects.requireNonNull(type, "The pack type should not be null.");
		return REGISTRIES.computeIfAbsent(type, t -> new ResourceRegistry());
	}

	public synchronized void addListener(Identifier id, PreparableReloadListener listener) {
		Objects.requireNonNull(id, "The listener identifier should not be null.");
		Objects.requireNonNull(listener, "The listener should not be null.");
		checkUnique(id);
		for (Map.Entry<Identifier, PreparableReloadListener> entry : this.listeners.entrySet()) {
			if (entry.getValue() == listener) {
				throw new IllegalStateException("Resource listener with ID %s already in resource listener set with ID %s!".formatted(id, entry.getKey()));
			}
		}
		this.listeners.put(id, listener);
	}

	/** Server data only: a listener created on every reload from that reload's registries. */
	public synchronized void addFactory(Identifier id, Function<HolderLookup.Provider, PreparableReloadListener> factory) {
		Objects.requireNonNull(id, "The reloader identifier should not be null.");
		Objects.requireNonNull(factory, "The reloader factory should not be null.");
		checkUnique(id);
		for (Map.Entry<Identifier, Function<HolderLookup.Provider, PreparableReloadListener>> entry : this.factories.entrySet()) {
			if (entry.getValue() == factory) {
				throw new IllegalStateException("Resource reloader factory with ID %s already in resource reloader factory set with ID %s!".formatted(id, entry.getKey()));
			}
		}
		this.factories.put(id, factory);
	}

	public synchronized void addOrdering(Identifier first, Identifier second) {
		Objects.requireNonNull(first, "The first listener identifier should not be null.");
		Objects.requireNonNull(second, "The second listener identifier should not be null.");
		if (first.equals(second)) throw new IllegalArgumentException("Tried to add a phase that depends on itself.");
		this.orderings.add(new Ordering(first, second));
	}

	private void checkUnique(Identifier id) {
		if (this.listeners.containsKey(id) || this.factories.containsKey(id)) {
			throw new IllegalStateException("Tried to register resource listener %s twice!".formatted(id));
		}
	}

	synchronized Map<Identifier, PreparableReloadListener> listeners() {
		return new LinkedHashMap<>(this.listeners);
	}

	synchronized Map<Identifier, Function<HolderLookup.Provider, PreparableReloadListener>> factories() {
		return new LinkedHashMap<>(this.factories);
	}

	synchronized List<Ordering> orderings() {
		return List.copyOf(this.orderings);
	}

	/** @return whether the mod contains the pack directory */
	public static boolean addBuiltinPack(Identifier id, String subPath, ModContainer container, Component displayName, PackActivationType activationType) {
		Objects.requireNonNull(id, "The pack identifier should not be null.");
		Objects.requireNonNull(activationType, "The activation type should not be null.");
		Optional<Path> root = container.findPath(subPath).filter(Files::isDirectory);
		if (root.isEmpty()) return false;
		synchronized (BUILTIN_PACKS) {
			BUILTIN_PACKS.add(new BuiltinPack(id, container.getMetadata().getName(), root.get(), displayName, activationType));
		}
		return true;
	}

	static List<BuiltinPack> builtinPacks() {
		synchronized (BUILTIN_PACKS) {
			return List.copyOf(BUILTIN_PACKS);
		}
	}
}
