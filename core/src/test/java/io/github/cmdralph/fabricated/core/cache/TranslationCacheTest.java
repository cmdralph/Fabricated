package io.github.cmdralph.fabricated.core.cache;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
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
	void remembersNestedJarsAcrossLaunches(@TempDir Path game) throws Exception {
		TranslationCache first = new TranslationCache(game, true, true);
		Path stored = first.storeNested(new byte[] {7, 8, 9}, "parent", "META-INF/jars/lib.jar");
		first.finish(5);
		TranslationCache next = new TranslationCache(game, true, true);
		assertEquals(stored, next.nested("parent", "META-INF/jars/lib.jar").orElseThrow());
		assertTrue(next.nested("other", "META-INF/jars/lib.jar").isEmpty());
		Files.write(stored, new byte[] {1});
		assertTrue(next.nested("parent", "META-INF/jars/lib.jar").isEmpty(), "a damaged copy is not reused");
	}

	@Test
	void findsANestedJarByItsHash(@TempDir Path game) throws Exception {
		TranslationCache first = new TranslationCache(game, true, true);
		Path stored = first.storeNested(new byte[] {4, 5, 6});
		first.finish(5);
		TranslationCache next = new TranslationCache(game, true, true);
		assertEquals(stored, next.storedNested(TranslationCache.nestedSha(stored)).orElseThrow());
		assertTrue(next.storedNested("ab".repeat(32)).isEmpty());
		assertTrue(next.storedNested("../" + TranslationCache.nestedSha(stored)).isEmpty(), "only a hash names a stored jar");
		assertTrue(new TranslationCache(game, false, true).storedNested(TranslationCache.nestedSha(stored)).isEmpty(), "nothing is reused when reuse is off");
		Files.write(stored, new byte[] {1});
		assertTrue(next.storedNested(TranslationCache.nestedSha(stored)).isEmpty(), "a damaged copy is not reused");
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

	/** A blob a launch keeps without reading it (the mapping table, PERF-2) outlives cleanup; one nothing keeps doesn't. */
	@Test
	void keepsABlobALaunchKeepsUnread(@TempDir Path game) throws Exception {
		TranslationCache first = new TranslationCache(game, true, true);
		Path kept = first.storeBlob("kept.bin", new byte[] {1});
		Path dropped = first.storeBlob("dropped.bin", new byte[] {2});
		first.finish(1);
		for (int i = 0; i < 3; i++) {
			TranslationCache next = new TranslationCache(game, true, true);
			next.keepBlob("kept.bin");
			next.finish(1);
		}
		assertTrue(Files.isRegularFile(kept));
		assertFalse(Files.exists(dropped));
		assertThrows(IllegalArgumentException.class, () -> first.keepBlob("../x"));
	}

	/** CACHE-3: a name looked up but never stored (its translation failed) leaves the index too, though it has no file. */
	@Test
	void forgetsNamesNeverStored(@TempDir Path game) throws Exception {
		String name = TranslationCache.entryName("failed_mod", "22".repeat(32));
		TranslationCache first = new TranslationCache(game, true, true);
		assertTrue(first.lookup(name, "overlay.jar").isEmpty());
		first.finish(1);
		assertTrue(Files.readString(first.root().resolve("index.json")).contains(name));
		for (int i = 0; i < 3; i++) {
			new TranslationCache(game, true, true).finish(1);
		}
		assertFalse(Files.readString(first.root().resolve("index.json")).contains(name));
	}

	@Test
	void keepsNestedJarsAndEntriesUsedEveryLaunch(@TempDir Path game) throws Exception {
		// As in a real launch: Fabricated's game mod is extracted, then a mod entry and a nested library are used.
		String name = TranslationCache.entryName("my_mod", "ef".repeat(32));
		for (int launch = 0; launch < 12; launch++) {
			TranslationCache cache = new TranslationCache(game, true, true);
			Path gameMod = cache.storeNested(new byte[] {7, 7, 7});
			if (cache.lookup(name).isEmpty()) {
				assertEquals(0, launch, "entry rebuilt on launch " + launch);
				cache.store(name, Map.of("overlay.jar", new byte[] {1}), Map.of());
			}
			Path library = cache.storeNested(new byte[] {4, 2});
			cache.finish(2);
			assertTrue(Files.isRegularFile(gameMod), "game mod kept on launch " + launch);
			assertTrue(Files.isRegularFile(library), "nested library kept on launch " + launch);
		}
	}

	/** FC-6: a launch sharing the game directory that stores an entry another stored first keeps that one's files. */
	@Test
	void keepsTheSameEntryAnotherLaunchStored(@TempDir Path game) throws Exception {
		String name = TranslationCache.entryName("shared_mod", "33".repeat(32));
		TranslationCache first = new TranslationCache(game, true, true);
		TranslationCache second = new TranslationCache(game, true, true);
		// Both missed it, then translated it: the first stores, and its game may be using the files.
		assertTrue(first.lookup(name).isEmpty());
		assertTrue(second.lookup(name).isEmpty());
		TranslationCache.Entry stored = first.store(name, Map.of("overlay.jar", new byte[] {1, 2}), Map.of("by", "first"));
		Object fileKey = Files.readAttributes(stored.file("overlay.jar"), BasicFileAttributes.class).fileKey();
		TranslationCache.Entry again = second.store(name, Map.of("overlay.jar", new byte[] {1, 2}), Map.of("by", "second"));
		assertEquals(stored.dir(), again.dir());
		assertEquals("first", again.metadata().get("by"), "the entry already there is kept");
		assertEquals(fileKey, Files.readAttributes(again.file("overlay.jar"), BasicFileAttributes.class).fileKey(), "its file wasn't replaced");
	}

	/** FC-6: an entry already there with other files, or damaged ones, is replaced. */
	@Test
	void replacesAnEntryWithOtherOrDamagedFiles(@TempDir Path game) throws Exception {
		String name = TranslationCache.entryName("shared_mod", "44".repeat(32));
		TranslationCache cache = new TranslationCache(game, true, true);
		cache.store(name, Map.of("overlay.jar", new byte[] {1}), Map.of());
		TranslationCache.Entry other = cache.store(name, Map.of("overlay.jar", new byte[] {2}), Map.of());
		assertArrayEquals(new byte[] {2}, Files.readAllBytes(other.file("overlay.jar")));
		Files.write(other.file("overlay.jar"), new byte[] {9});
		TranslationCache.Entry repaired = new TranslationCache(game, true, true).store(name, Map.of("overlay.jar", new byte[] {2}), Map.of());
		assertArrayEquals(new byte[] {2}, Files.readAllBytes(repaired.file("overlay.jar")));
		try (var left = Files.list(cache.root().resolve("tmp"))) {
			assertEquals(0, left.count(), "what was set aside is deleted");
		}
	}

	/** FC-6: two launches that ran side by side both keep what they used, whichever saves the index last. */
	@Test
	void mergesTheIndexOfLaunchesSharingTheDirectory(@TempDir Path game) throws Exception {
		String a = TranslationCache.entryName("client_mod", "55".repeat(32));
		String b = TranslationCache.entryName("server_mod", "66".repeat(32));
		TranslationCache client = new TranslationCache(game, true, true);
		TranslationCache server = new TranslationCache(game, true, true);
		client.store(a, Map.of("overlay.jar", new byte[] {1}), Map.of());
		server.store(b, Map.of("overlay.jar", new byte[] {2}), Map.of());
		server.finish(5);
		client.finish(5);
		String index = Files.readString(client.root().resolve("index.json"));
		assertTrue(index.contains(a) && index.contains(b), index);
		// Each later launch counts from both: the entries stay until five launches have used neither.
		for (int i = 0; i < 5; i++) new TranslationCache(game, true, true).finish(5);
		TranslationCache later = new TranslationCache(game, true, true);
		assertTrue(later.lookup(a).isPresent() && later.lookup(b).isPresent());
	}

	/** FC-7: cleanup deletes a link inside the cache, never what it leads to. */
	@Test
	void deletesLinksNotWhatTheyLeadTo(@TempDir Path game, @TempDir Path elsewhere) throws Exception {
		Path outside = Files.write(elsewhere.resolve("keep.txt"), new byte[] {1});
		TranslationCache cache = new TranslationCache(game, true, true);
		Path dir = Files.createDirectories(cache.root().resolve("tmp").resolve("leftover"));
		Files.createSymbolicLink(dir.resolve("link"), elsewhere);
		TranslationCache.deleteQuietly(dir);
		assertFalse(Files.exists(dir));
		assertTrue(Files.isRegularFile(outside));
	}
}
