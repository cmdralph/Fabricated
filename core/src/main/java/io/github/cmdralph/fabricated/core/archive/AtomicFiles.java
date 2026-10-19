package io.github.cmdralph.fabricated.core.archive;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.CopyOption;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Atomic file replacement. */
public final class AtomicFiles {
	/** Pauses between attempts of a move a file in use refused (150 ms in all). */
	private static final long[] RETRY_DELAYS_MS = {10, 20, 40, 80};

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
			move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} finally {
			Files.deleteIfExists(tmp);
		}
	}

	/**
	 * {@link Files#move}, tried again briefly when the file system refuses because a file is in use. On Windows an
	 * antivirus, the search indexer or a sync client often holds a file just written, which makes renaming it (or the
	 * directory holding it) fail with {@link AccessDeniedException} or a plain {@link FileSystemException} for a moment.
	 * Other failures are thrown at once.
	 */
	public static void move(Path source, Path target, CopyOption... options) throws IOException {
		for (int attempt = 0; ; attempt++) {
			try {
				Files.move(source, target, options);
				return;
			} catch (FileSystemException e) {
				boolean inUse = e instanceof AccessDeniedException || e.getClass() == FileSystemException.class;
				if (!inUse || attempt == RETRY_DELAYS_MS.length) throw e;
				try {
					Thread.sleep(RETRY_DELAYS_MS[attempt]);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw e;
				}
			}
		}
	}
}
