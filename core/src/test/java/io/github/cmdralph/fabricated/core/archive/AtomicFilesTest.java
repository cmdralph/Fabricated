package io.github.cmdralph.fabricated.core.archive;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFilesTest {
	@Test
	void writesAtomicallyAndCleansUp(@TempDir Path dir) throws Exception {
		Path target = dir.resolve("out.bin");
		AtomicFiles.writeAtomically(target, new byte[] {1, 2, 3}, dir);
		AtomicFiles.writeAtomically(target, new byte[] {4, 5}, dir);
		assertArrayEquals(new byte[] {4, 5}, Files.readAllBytes(target));
		try (var files = Files.list(dir)) {
			assertFalse(files.anyMatch(p -> p.getFileName().toString().endsWith(".tmp")), "no temporary file left behind");
		}
	}

	/** Only a file in use is worth another try: anything else fails at once, with the file system's own exception. */
	@Test
	void moveThrowsOtherFailuresUnchanged(@TempDir Path dir) throws Exception {
		assertThrows(NoSuchFileException.class, () -> AtomicFiles.move(dir.resolve("missing"), dir.resolve("target")));
		Path source = Files.writeString(dir.resolve("a"), "a");
		Path existing = Files.writeString(dir.resolve("b"), "b");
		assertThrows(FileAlreadyExistsException.class, () -> AtomicFiles.move(source, existing));
		AtomicFiles.move(source, existing, StandardCopyOption.REPLACE_EXISTING);
		assertArrayEquals("a".getBytes(), Files.readAllBytes(existing));
	}
}
