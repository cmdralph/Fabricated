package io.github.cmdralph.fabricated.core.remap;

import java.io.IOException;
import java.util.Objects;

/**
 * The intermediary table, loaded when something first needs it: translating a mod, pre-flight reading a Fabric mod's
 * classes, a mod asking its mapping resolver. A launch whose translations all come from the cache needs none of them
 * until a mod asks, and reading the table took such a launch 80 to 110 ms (PERF-2). Thread-safe: mods are translated
 * in parallel.
 */
public final class LazyMappingTable {
	/** Loads the table. */
	@FunctionalInterface
	public interface Loader {
		MappingTable load() throws MappingException, IOException;
	}

	private final Loader loader;
	private volatile MappingTable table;
	private Exception failure;

	public LazyMappingTable(Loader loader) {
		this.loader = Objects.requireNonNull(loader, "loader");
	}

	/** The table, loaded by the first call; a failure to load it is reported by every call, without loading it again. */
	public MappingTable table() throws MappingException {
		MappingTable loaded = table;
		if (loaded != null) return loaded;
		synchronized (this) {
			if (table == null && failure == null) {
				try {
					MappingTable made = loader.load();
					if (made == null) throw new MappingException("no mapping table was made");
					table = made;
				} catch (MappingException | IOException | RuntimeException e) {
					failure = e;
				}
			}
			if (failure != null) throw new MappingException(String.valueOf(failure.getMessage()), failure);
			return table;
		}
	}

	/** The table if it was loaded, else null. */
	public MappingTable loaded() {
		return table;
	}
}
