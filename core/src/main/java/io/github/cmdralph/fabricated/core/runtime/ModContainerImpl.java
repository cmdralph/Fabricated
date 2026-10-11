package io.github.cmdralph.fabricated.core.runtime;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;

/**
 * {@link ModContainer} for Fabric mods and, with builtin metadata, for NeoForge mods, Minecraft and Java.
 * Root paths are read-only zip file systems opened on first use.
 */
public final class ModContainerImpl implements ModContainer {
	private final ModMetadata metadata;
	private final List<Path> sources;
	private final ModOrigin origin;
	private final String parentId;
	private final List<String> childIds;
	private final FabricLoaderRuntime runtime;
	private volatile List<Path> roots;

	/**
	 * @param sources  jar files or directories holding the mod's files (first = primary)
	 * @param parentId id of the containing mod for nested jars, else {@code null}
	 */
	public ModContainerImpl(ModMetadata metadata, List<Path> sources, ModOrigin origin, String parentId, List<String> childIds,
			FabricLoaderRuntime runtime) {
		this.metadata = metadata;
		this.sources = List.copyOf(sources);
		this.origin = origin;
		this.parentId = parentId;
		this.childIds = List.copyOf(childIds);
		this.runtime = runtime;
	}

	@Override
	public ModMetadata getMetadata() {
		return metadata;
	}

	@Override
	public List<Path> getRootPaths() {
		List<Path> r = roots;
		if (r == null) {
			synchronized (this) {
				r = roots;
				if (r == null) {
					List<Path> opened = new ArrayList<>();
					for (Path source : sources) {
						if (Files.isDirectory(source)) {
							opened.add(source);
						} else if (Files.isRegularFile(source)) {
							try {
								FileSystem fs = FileSystems.newFileSystem(source, (ClassLoader) null);
								opened.add(fs.getRootDirectories().iterator().next());
							} catch (IOException | RuntimeException e) {
								// A mod whose jar cannot be opened simply has no browsable roots.
							}
						}
					}
					r = List.copyOf(opened);
					roots = r;
				}
			}
		}
		return r;
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
		return r.getFirst();
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
