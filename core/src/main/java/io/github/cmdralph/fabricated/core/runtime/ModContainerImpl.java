package io.github.cmdralph.fabricated.core.runtime;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;

/**
 * {@link ModContainer} for Fabric mods and, with builtin metadata, for NeoForge mods, Minecraft and Java.
 * Root paths are read-only zip file systems opened on first use. It is Fabric Loader's internal container class too, as
 * mods test containers against it before adding entrypoints to Fabric Loader's storage (LOADER-4).
 */
public final class ModContainerImpl extends net.fabricmc.loader.impl.ModContainerImpl {
	private final ModMetadata metadata;
	private final List<Path> sources;
	private final ModOrigin origin;
	private final String parentId;
	private final List<String> childIds;
	private final FabricLoaderRuntime runtime;
	private final Path overlay;
	private List<Path> roots;
	private Path overlayRoot;

	/**
	 * @param sources  jar files or directories holding the mod's files (first = primary)
	 * @param parentId id of the containing mod for nested jars, else {@code null}
	 * @param overlay  the jar of files translation generated or changed for the mod, which take priority over its own, or
	 *                 {@code null}
	 */
	public ModContainerImpl(ModMetadata metadata, List<Path> sources, ModOrigin origin, String parentId, List<String> childIds,
			FabricLoaderRuntime runtime, Path overlay) {
		this.metadata = metadata;
		this.sources = List.copyOf(sources);
		this.origin = origin;
		this.parentId = parentId;
		this.childIds = List.copyOf(childIds);
		this.runtime = runtime;
		this.overlay = overlay;
	}

	/**
	 * A directory of the mod's overlay, when it has one there: its files take priority over the mod's own, as NeoForge
	 * reads the mod's (a built-in pack's data without its NeoForge build's conditions, PACK-2). The root paths stay the
	 * mod's own files, which mods walk.
	 */
	public synchronized Optional<Path> overlayDirectory(String path) {
		if (overlay == null) return Optional.empty();
		if (overlayRoot == null) {
			try {
				overlayRoot = openZip(overlay).getRootDirectories().iterator().next();
			} catch (IOException | RuntimeException e) {
				return Optional.empty(); // a missing overlay changes nothing
			}
		}
		Path directory = overlayRoot.resolve(path);
		return Files.isDirectory(directory) ? Optional.of(directory) : Optional.empty();
	}

	@Override
	public ModMetadata getMetadata() {
		return metadata;
	}

	@Override
	public synchronized List<Path> getRootPaths() {
		if (roots == null) {
			List<Path> opened = new ArrayList<>();
			for (Path source : sources) {
				if (Files.isDirectory(source)) {
					opened.add(source);
				} else if (Files.isRegularFile(source)) {
					try {
						FileSystem fs = openZip(source);
						opened.add(fs.getRootDirectories().iterator().next());
					} catch (IOException | RuntimeException e) {
						// A mod whose jar cannot be opened simply has no browsable roots.
					}
				}
			}
			roots = List.copyOf(opened);
		}
		return roots;
	}

	/**
	 * The jar as the JDK's zip file system, whose paths give the {@code jar:file:} URLs classpath scanners read (SCAN-1).
	 * Asking the installed providers in turn would hand it to the first that takes any file, which on NeoForge 21.1 is
	 * its jar handler's union file system, with URLs nothing outside that loader opens.
	 */
	private static FileSystem openZip(Path jar) throws IOException {
		for (FileSystemProvider provider : FileSystemProvider.installedProviders()) {
			if ("jar".equalsIgnoreCase(provider.getScheme())) return provider.newFileSystem(jar, Map.of());
		}
		return FileSystems.newFileSystem(jar, (ClassLoader) null);
	}

	@Override
	public ModOrigin getOrigin() {
		return origin;
	}

	@Override
	public Optional<ModContainer> getContainingMod() {
		return parentId == null ? Optional.empty() : runtime.getModContainer(parentId);
	}

	@Override
	public Collection<ModContainer> getContainedMods() {
		List<ModContainer> ret = new ArrayList<>();
		for (String id : childIds) runtime.getModContainer(id).ifPresent(ret::add);
		return ret;
	}

	@Override
	@Deprecated
	public Path getRootPath() {
		List<Path> r = getRootPaths();
		if (r.isEmpty()) throw new IllegalStateException("Mod " + metadata.getId() + " has no root path");
		return r.get(0);
	}

	@Override
	@Deprecated
	public Path getPath(String file) {
		Optional<Path> found = findPath(file);
		return found.orElseGet(() -> getRootPath().resolve(file.replace("/", getRootPath().getFileSystem().getSeparator())));
	}

	@Override
	public String toString() {
		return metadata.getId() + " " + metadata.getVersion().getFriendlyString();
	}

	/** Origin information for {@link ModContainer#getOrigin()}. */
	public record Origin(Kind kind, List<Path> paths, String parentModId, String parentSubLocation) implements ModOrigin {
		@Override
		public Kind getKind() {
			return kind;
		}

		@Override
		public List<Path> getPaths() {
			if (kind != Kind.PATH) throw new UnsupportedOperationException("kind " + kind + " doesn't have paths");
			return paths;
		}

		@Override
		public String getParentModId() {
			if (kind != Kind.NESTED) throw new UnsupportedOperationException("kind " + kind + " doesn't have a parent mod");
			return parentModId;
		}

		@Override
		public String getParentSubLocation() {
			if (kind != Kind.NESTED) throw new UnsupportedOperationException("kind " + kind + " doesn't have a parent sub-location");
			return parentSubLocation;
		}

		@Override
		public String toString() {
			return kind == Kind.NESTED ? parentModId + "!/" + parentSubLocation : String.valueOf(paths);
		}
	}
}
