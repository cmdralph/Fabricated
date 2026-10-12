package io.github.cmdralph.fabricated.core.runtime;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.github.cmdralph.fabricated.core.metadata.FabricModMetadata.EntrypointSpec;
import io.github.cmdralph.fabricated.fabricloader.DefaultLanguageAdapter;
import io.github.cmdralph.fabricated.fabricloader.FabricLoaderImpl;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.EntrypointException;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.ObjectShare;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

/**
 * Fabricated's implementation of {@link FabricLoader}: mod lookup (Fabric mods, NeoForge mods and
 * built-ins), entrypoints, language adapters, the object share and the mapping resolver.
 */
public final class FabricLoaderRuntime implements FabricLoader {
	/** An entrypoint declaration bound to the mod that declared it. */
	public record Declared(ModContainerImpl mod, String key, EntrypointSpec spec) {
	}

	private final EnvType envType;
	private final Path gameDir;
	private final Path configDir;
	private final String rawGameVersion;
	private final boolean development;
	private final MappingResolver mappingResolver;
	private final Supplier<String[]> launchArguments;
	private final ObjectShare objectShare = new ObjectShareImpl();

	private final Map<String, ModContainerImpl> modsById = new LinkedHashMap<>();
	private final Map<ModContainerImpl, ClassLoader> classLoaders = new ConcurrentHashMap<>();
	private final Map<String, List<Declared>> entrypoints = new LinkedHashMap<>();
	private final Map<String, String> adapterClasses = new LinkedHashMap<>();
	private final Map<String, ModContainerImpl> adapterOwners = new LinkedHashMap<>();
	private final Map<String, LanguageAdapter> adapters = new ConcurrentHashMap<>();
	private final Map<String, Object> instances = new ConcurrentHashMap<>();
	private volatile Object gameInstance;

	public FabricLoaderRuntime(EnvType envType, Path gameDir, Path configDir, String rawGameVersion, boolean development,
			MappingResolver mappingResolver, Supplier<String[]> launchArguments) {
		this.envType = envType;
		this.gameDir = gameDir;
		this.configDir = configDir;
		this.rawGameVersion = rawGameVersion;
		this.development = development;
		this.mappingResolver = mappingResolver;
		this.launchArguments = launchArguments;
		adapters.put("default", DefaultLanguageAdapter.INSTANCE);
	}

	/** Makes this the instance returned by {@link FabricLoader#getInstance()}. */
	public void install() {
		FabricLoaderImpl.install(this, mod -> {
			ClassLoader cl = mod instanceof ModContainerImpl impl ? classLoaders.get(impl) : null;
			if (cl != null) return cl;
			ClassLoader context = Thread.currentThread().getContextClassLoader();
			return context != null ? context : FabricLoaderRuntime.class.getClassLoader();
		});
	}

	/**
	 * Registers a mod under its id and any additional ids (provides, aliases). Ids already taken are not
	 * replaced, so real mods win over aliases registered later.
	 */
	public synchronized void addMod(ModContainerImpl container, Collection<String> extraIds) {
		modsById.putIfAbsent(container.getMetadata().getId(), container);
		for (String id : extraIds) modsById.putIfAbsent(id, container);
	}

	/** Records which class loader can load a mod's classes (set once the game layer exists). */
	public void setClassLoader(ModContainerImpl container, ClassLoader loader) {
		classLoaders.put(container, loader);
	}

	/** Registers a mod's entrypoints, minus keys disabled in the config. */
	public synchronized void addEntrypoints(ModContainerImpl mod, Map<String, List<EntrypointSpec>> declared, Set<String> skippedKeys) {
		for (Map.Entry<String, List<EntrypointSpec>> e : declared.entrySet()) {
			if (skippedKeys.contains(e.getKey())) continue;
			for (EntrypointSpec spec : e.getValue()) {
				entrypoints.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(new Declared(mod, e.getKey(), spec));
			}
		}
	}

	/** Registers language adapters declared by a mod ({@code languageAdapters} in fabric.mod.json). */
	public synchronized void addLanguageAdapters(ModContainerImpl mod, Map<String, String> declared) {
		for (Map.Entry<String, String> e : declared.entrySet()) {
			adapterClasses.putIfAbsent(e.getKey(), e.getValue());
			adapterOwners.putIfAbsent(e.getKey(), mod);
		}
	}

	public void setGameInstance(Object instance) {
		this.gameInstance = instance;
	}

	/** Entrypoint declarations for {@code key}, in mod load order. */
	public synchronized List<Declared> declared(String key) {
		return List.copyOf(entrypoints.getOrDefault(key, List.of()));
	}

	/**
	 * Creates (or returns the cached) instance of one entrypoint. The same declaration requested with the
	 * same type yields the same object, as in Fabric Loader.
	 */
	public <T> T instantiate(Declared declared, Class<T> type) throws LanguageAdapterException {
		String cacheKey = declared.mod().getMetadata().getId() + "|" + declared.spec().adapter() + "|" + declared.spec().value() + "|" + type.getName();
		Object existing = instances.get(cacheKey);
		if (existing != null) return type.cast(existing);
		LanguageAdapter adapter = adapter(declared.spec().adapter());
		T created = adapter.create(declared.mod(), declared.spec().value(), type);
		Object raced = instances.putIfAbsent(cacheKey, created);
		return type.cast(raced != null ? raced : created);
	}

	private LanguageAdapter adapter(String id) throws LanguageAdapterException {
		LanguageAdapter existing = adapters.get(id);
		if (existing != null) return existing;
		String className;
		ModContainerImpl owner;
		synchronized (this) {
			className = adapterClasses.get(id);
			owner = adapterOwners.get(id);
		}
		if (className == null) throw new LanguageAdapterException("Unknown language adapter '" + id + "'");
		try {
			Class<?> cls = Class.forName(className, true, FabricLoaderImpl.classLoaderFor(owner));
			LanguageAdapter created = (LanguageAdapter) cls.getDeclaredConstructor().newInstance();
			LanguageAdapter raced = adapters.putIfAbsent(id, created);
			return raced != null ? raced : created;
		} catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
			throw new LanguageAdapterException("Could not create language adapter " + className + " for '" + id + "'", e);
		}
	}

	@Override
	public <T> List<T> getEntrypoints(String key, Class<T> type) {
		List<T> ret = new ArrayList<>();
		for (EntrypointContainer<T> c : getEntrypointContainers(key, type)) ret.add(c.getEntrypoint());
		return ret;
	}

	@Override
	@SuppressWarnings("deprecation") // EntrypointException's constructors are reserved for loader implementations
	public <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type) {
		List<EntrypointContainer<T>> ret = new ArrayList<>();
		EntrypointException failure = null;
		for (Declared d : declared(key)) {
			try {
				T instance = instantiate(d, type);
				String definition = d.spec().value();
				ModContainer provider = d.mod();
				ret.add(new EntrypointContainer<>() {
					@Override
					public T getEntrypoint() {
						return instance;
					}

					@Override
					public ModContainer getProvider() {
						return provider;
					}

					@Override
					public String getDefinition() {
						return definition;
					}
				});
			} catch (Throwable t) {
				EntrypointException e = new EntrypointException(key, d.mod().getMetadata().getId(), t);
				if (failure == null) failure = e;
				else failure.addSuppressed(e);
			}
		}
		if (failure != null) throw failure;
		return ret;
	}

	@Override
	@SuppressWarnings("deprecation") // EntrypointException's constructors are reserved for loader implementations
	public <T> void invokeEntrypoints(String key, Class<T> type, Consumer<? super T> invoker) {
		RuntimeException failure = null;
		for (EntrypointContainer<T> c : getEntrypointContainers(key, type)) {
			try {
				invoker.accept(c.getEntrypoint());
			} catch (Throwable t) {
				RuntimeException e = new EntrypointException(key, c.getProvider().getMetadata().getId(), t);
				if (failure == null) failure = e;
				else failure.addSuppressed(e);
			}
		}
		if (failure != null) throw failure;
	}

	@Override
	public ObjectShare getObjectShare() {
		return objectShare;
	}

	@Override
	public MappingResolver getMappingResolver() {
		return mappingResolver;
	}

	@Override
	public synchronized Optional<ModContainer> getModContainer(String id) {
		return Optional.ofNullable(lookup(Objects.requireNonNull(id, "id")));
	}

	/**
	 * A mod by Fabric id. NeoForge mod ids can't contain '-', so a NeoForge build of a mod that is {@code cloth-config}
	 * on Fabric is {@code cloth_config}; dependency resolution already accepts it for the Fabric id, and so does this.
	 */
	private ModContainer lookup(String id) {
		ModContainer mod = modsById.get(id);
		if (mod == null && id.indexOf('-') >= 0) mod = modsById.get(id.replace('-', '_'));
		return mod;
	}

	@Override
	public synchronized Collection<ModContainer> getAllMods() {
		return Collections.unmodifiableCollection(new ArrayList<>(new java.util.LinkedHashSet<>(modsById.values())));
	}

	@Override
	public synchronized boolean isModLoaded(String id) {
		return lookup(Objects.requireNonNull(id, "id")) != null;
	}

	@Override
	public boolean isDevelopmentEnvironment() {
		return development;
	}

	@Override
	public EnvType getEnvironmentType() {
		return envType;
	}

	@Override
	public String getRawGameVersion() {
		return rawGameVersion;
	}

	@Override
	@Deprecated
	public Object getGameInstance() {
		return gameInstance;
	}

	@Override
	public Path getGameDir() {
		return gameDir;
	}

	@Override
	@Deprecated
	public File getGameDirectory() {
		return gameDir.toFile();
	}

	@Override
	public Path getConfigDir() {
		return configDir;
	}

	@Override
	@Deprecated
	public File getConfigDirectory() {
		return configDir.toFile();
	}

	@Override
	public String[] getLaunchArguments(boolean sanitize) {
		String[] args = launchArguments.get().clone();
		if (!sanitize) return args;
		List<String> ret = new ArrayList<>();
		for (int i = 0; i < args.length; i++) {
			String a = args[i];
			if (a.startsWith("--")) {
				int eq = a.indexOf('=');
				String name = (eq < 0 ? a.substring(2) : a.substring(2, eq)).toLowerCase(Locale.ROOT);
				if (SENSITIVE_ARGUMENTS.contains(name)) {
					if (eq < 0) i++; // skip the value too
					continue;
				}
			}
			ret.add(a);
		}
		return ret.toArray(String[]::new);
	}

	/** Arguments Fabric Loader removes when asked for sanitized launch arguments (compared ignoring case). */
	private static final java.util.Set<String> SENSITIVE_ARGUMENTS = java.util.Set.of(
			"accesstoken", "clientid", "profileproperties", "proxypass", "proxyuser", "username", "userproperties", "uuid", "xuid");
}
