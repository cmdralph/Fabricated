package io.github.cmdralph.fabricated.core.translate;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.archive.SafeArchive;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;

/**
 * Class source over translated jars. The class index is built on first use only, so a warm launch (linkage results
 * cached) opens no jar; opened jars are closed by {@link #close()}.
 */
public final class JarClassSource implements ClassBytesSource, AutoCloseable {
	private final List<TranslatedMod> mods;
	private final ArchiveLimits limits;
	private final Map<Path, SafeArchive> open = new HashMap<>();
	private Map<String, Path> owners;

	public JarClassSource(List<TranslatedMod> mods, ArchiveLimits limits) {
		this.mods = mods;
		this.limits = limits.forTranslatedJars();
	}

	@Override
	public synchronized byte[] read(String internalName) throws IOException {
		if (owners == null) {
			owners = new HashMap<>();
			for (TranslatedMod t : mods) {
				try (SafeArchive archive = SafeArchive.open(t.jar(), limits)) {
					for (String name : archive.classEntryNames()) owners.putIfAbsent(name, t.jar());
				} catch (IOException e) {
					// Mods that cannot be opened were already reported during translation.
				}
			}
		}
		Path jar = owners.get(internalName + ".class");
		if (jar == null) return null;
		try {
			SafeArchive archive = open.get(jar);
			if (archive == null) {
				archive = SafeArchive.open(jar, limits);
				open.put(jar, archive);
			}
			return archive.read(internalName + ".class");
		} catch (IOException e) {
			throw new IOException("Could not read " + internalName + " from " + jar + ": " + e.getMessage(), e);
		}
	}

	@Override
	public synchronized void close() {
		for (SafeArchive archive : open.values()) {
			try {
				archive.close();
			} catch (IOException ignored) {
				// Closing a read-only archive cannot lose data.
			}
		}
		open.clear();
	}
}
