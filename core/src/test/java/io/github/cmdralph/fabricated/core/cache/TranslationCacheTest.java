package io.github.cmdralph.fabricated.core.cache;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TranslationCacheTest {
	@Test
	void storesAndVerifiesEntries(@TempDir Path game) throws Exception {
		TranslationCache cache = new TranslationCache(game, true, true);
		assertTrue(cache.root().startsWith(game.toRealPath()));
		String name = TranslationCache.entryName("my_mod", "ab".repeat(32));
		cache.store(name, Map.of("overlay.jar", new byte[] {1, 2, 3}), Map.of("modId", "my_mod"));
		TranslationCache.Entry entry = cache.lookup(name).orElseThrow();
		assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(entry.file("overlay.jar")));
		assertEquals("my_mod", entry.metadata().get("modId"));
	}

	@Test
	void detectsTamperingAndRebuilds(@TempDir Path game) throws Exception {
		TranslationCache cache = new TranslationCache(game, true, true);
		String name = TranslationCache.entryName("my_mod", "cd".repeat(32));
		TranslationCache.Entry entry = cache.store(name, Map.of("overlay.jar", new byte[] {1}), Map.of());
		Files.write(entry.file("overlay.jar"), new byte[] {9});
		assertTrue(cache.lookup(name).isEmpty(), "tampered entry is rejected");
		assertFalse(Files.exists(entry.dir()), "and deleted");
		assertTrue(cache.notes().stream().anyMatch(n -> n.contains("failed verification")));
	}

	@Test
	void disabledReuseNeverReturnsEntries(@TempDir Path game) throws Exception {
		TranslationCache cache = new TranslationCache(game, false, true);
		String name = TranslationCache.entryName("my_mod", "ef".repeat(32));
		cache.store(name, Map.of("overlay.jar", new byte[] {1}), Map.of());
		assertTrue(cache.lookup(name).isEmpty());
	}

	@Test
	void refusesSymlinkedCacheDirectories(@TempDir Path game, @TempDir Path elsewhere) throws Exception {
		Files.createSymbolicLink(game.resolve(".fabricated"), elsewhere);
		assertThrows(IOException.class, () -> new TranslationCache(game, true, true));
	}

	@Test
	void refusesSymlinkedCacheSubdirectories(@TempDir Path game, @TempDir Path elsewhere) throws Exception {
		Path root = new TranslationCache(game, true, true).root();
		Files.delete(root.resolve("tmp"));
		Files.createSymbolicLink(root.resolve("tmp"), elsewhere);
		assertThrows(IOException.class, () -> new TranslationCache(game, true, true));
	}

	@Test
	void verifiesBlobs(@TempDir Path game) throws Exception {
		TranslationCache cache = new TranslationCache(game, true, true);
		Path blob = cache.storeBlob("table.bin", new byte[] {1, 2, 3});
		assertArrayEquals(new byte[] {1, 2, 3}, cache.readBlob("table.bin").orElseThrow());
		byte[] damaged = Files.readAllBytes(blob);
		damaged[damaged.length - 1] = 9;
		Files.write(blob, damaged);
		assertTrue(cache.readBlob("table.bin").isEmpty(), "damaged blob is rejected");
		assertFalse(Files.exists(blob), "and deleted");
	}

	@Test
	void requiresExpectedFiles(@TempDir Path game) throws Exception {
		TranslationCache cache = new TranslationCache(game, true, true);
		String name = TranslationCache.entryName("my_mod", "12".repeat(32));
		cache.store(name, Map.of("overlay.jar", new byte[] {1}), Map.of());
		assertTrue(cache.lookup(name, "overlay.jar", "remapped.jar").isEmpty());
	}

	@Test
	void rejectsUnsafeNames(@TempDir Path game) throws Exception {
		TranslationCache cache = new TranslationCache(game, true, true);
		assertThrows(IllegalArgumentException.class, () -> TranslationCache.entryName("../x", "00".repeat(32)));
		assertThrows(IllegalArgumentException.class, () -> cache.store("ok", Map.of("../evil", new byte[0]), Map.of()));
		assertThrows(IllegalArgumentException.class, () -> cache.storeBlob("a/b", new byte[0]));
	}

	@Test
	void contentAddressesNestedJarsAndMemoisesHashes(@TempDir Path game) throws Exception {
		TranslationCache cache = new TranslationCache(game, true, true);
		Path a = cache.storeNested(new byte[] {5, 6});
		Path b = cache.storeNested(new byte[] {5, 6});
		assertEquals(a, b);
		Path file = Files.write(game.resolve("mod.jar"), new byte[] {1, 2});
		assertEquals(cache.sha256(file), cache.sha256(file));
	}

	@Test
	void garbageCollectsUnusedEntries(@TempDir Path game) throws Exception {
		String name = TranslationCache.entryName("old_mod", "11".repeat(32));
		TranslationCache first = new TranslationCache(game, true, true);
		first.store(name, Map.of("overlay.jar", new byte[] {1}), Map.of());
		first.finish(1);
		for (int i = 0; i < 3; i++) {
			TranslationCache next = new TranslationCache(game, true, true);
			next.finish(1);
		}
		assertFalse(Files.exists(new TranslationCache(game, true, true).root().resolve("entries").resolve(name)));
	}

	@Test
	void keepsNestedJarsAndEntriesUsedEveryLaunch(@TempDir Path game) throws Exception {
		// As in a real launch: a bootstrap cache extracts Fabricated's game mod, the pipeline's cache uses a mod entry
		// and a nested library, and only the pipeline's cache finishes (owns the index).
		String name = TranslationCache.entryName("my_mod", "ef".repeat(32));
		for (int launch = 0; launch < 12; launch++) {
			TranslationCache bootstrap = new TranslationCache(game, true, true);
			Path gameMod = bootstrap.storeNested(new byte[] {7, 7, 7});
			TranslationCache cache = new TranslationCache(game, true, true);
			if (cache.lookup(name).isEmpty()) {
				assertEquals(0, launch, "entry rebuilt on launch " + launch);
				cache.store(name, Map.of("overlay.jar", new byte[] {1}), Map.of());
			}
			Path library = cache.storeNested(new byte[] {4, 2});
			cache.markNestedUsed(gameMod);
			cache.finish(2);
			assertTrue(Files.isRegularFile(gameMod), "game mod kept on launch " + launch);
			assertTrue(Files.isRegularFile(library), "nested library kept on launch " + launch);
		}
	}
}
