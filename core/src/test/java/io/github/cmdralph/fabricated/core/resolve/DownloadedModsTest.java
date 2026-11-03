package io.github.cmdralph.fabricated.core.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Where Fabricated looks for what a loader downloaded in an earlier launch (DOWNLOAD-1). */
class DownloadedModsTest {
	@Test
	void numbersFollowEssentialLoadersNames() {
		String base = DownloadedMods.essentialBaseName("1.21.1");
		assertEquals("Essential (fabric_1.21.1)", base);
		assertEquals(0, DownloadedMods.suffixNumber(base + ".jar", base));
		assertEquals(3, DownloadedMods.suffixNumber(base + ".3.jar", base));
		assertEquals(-1, DownloadedMods.suffixNumber(base + ".meta", base));
		assertEquals(-1, DownloadedMods.suffixNumber(base + ".jar.meta", base));
		assertEquals(-1, DownloadedMods.suffixNumber(base + ".x.jar", base));
		assertEquals(-1, DownloadedMods.suffixNumber(base + "..jar", base));
		assertEquals(-1, DownloadedMods.suffixNumber(base + "1.jar", base));
		assertEquals(-1, DownloadedMods.suffixNumber(base + ".12345678901.jar", base));
		assertEquals(-1, DownloadedMods.suffixNumber("Essential (fabric_1.21.11).jar", base));
	}

	@Test
	void theNewestRegularJarOfTheGamesVersionIsFoundOnlyWithItsLoader(@TempDir Path game) throws Exception {
		Path dir = Files.createDirectories(game.resolve("essential"));
		Files.writeString(dir.resolve("Essential (fabric_1.21.1).jar"), "a");
		Files.writeString(dir.resolve("Essential (fabric_1.21.1).2.jar"), "b");
		Files.writeString(dir.resolve("Essential (fabric_1.21.1).2.jar.meta"), "{}");
		Files.writeString(dir.resolve("Essential (fabric_1.21.11).7.jar"), "c");
		Files.createDirectories(dir.resolve("Essential (fabric_1.21.1).9.jar"));

		assertTrue(DownloadedMods.of(Set.of("some-mod"), game, "1.21.1").isEmpty());
		List<DownloadedMods.Download> found = DownloadedMods.of(Set.of("some-mod", DownloadedMods.ESSENTIAL_LOADER), game, "1.21.1");
		assertEquals(1, found.size());
		assertEquals(dir.resolve("Essential (fabric_1.21.1).2.jar"), found.getFirst().jar());
		assertEquals(DownloadedMods.ESSENTIAL_LOADER, found.getFirst().loader());
		assertTrue(DownloadedMods.of(Set.of(DownloadedMods.ESSENTIAL_LOADER), game, "26.1.2").isEmpty());
		assertNull(DownloadedMods.latest(game.resolve("missing"), "Essential (fabric_1.21.1)"));
	}

	@Test
	void aJarAddedAsTheGameRunsIsKnownWhereALoaderDownloads(@TempDir Path game) {
		assertTrue(DownloadedMods.isLookedFor(game.resolve("essential").resolve("Essential (fabric_26.1.2).jar"), game));
		assertTrue(DownloadedMods.isLookedFor(game.resolve("essential/../essential").resolve("Essential (fabric_26.1.2).4.jar"), game));
		assertFalse(DownloadedMods.isLookedFor(game.resolve("essential").resolve("libraries").resolve("kotlin.jar"), game));
		assertFalse(DownloadedMods.isLookedFor(game.resolve("essential").resolve("libraries").resolve("Essential (fabric_26.1.2).jar"), game));
		assertFalse(DownloadedMods.isLookedFor(game.resolve("mods").resolve("Essential (fabric_26.1.2).jar"), game));
		assertFalse(DownloadedMods.isLookedFor(game.resolve("essential").resolve("Essential (forge_1.20.1).jar"), game));
	}
}
