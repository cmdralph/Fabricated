package io.github.cmdralph.fabricated.core.archive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Atomic file replacement. */
public final class AtomicFiles {
	private AtomicFiles() {
	}

	/**
	 * Writes {@code data} to a temporary file in {@code tmpDir} (which must be on the same file system as
	 * {@code target}) and atomically moves it over {@code target}. The temporary file is removed on failure.
	 */
	public static void writeAtomically(Path target, byte[] data, Path tmpDir) throws IOException {
		Path tmp = Files.createTempFile(tmpDir, "." + target.getFileName(), ".tmp");
		try {
			Files.write(tmp, data);
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} finally {
			Files.deleteIfExists(tmp);
		}
	}
}
