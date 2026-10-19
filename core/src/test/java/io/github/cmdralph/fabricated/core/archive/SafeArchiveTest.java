package io.github.cmdralph.fabricated.core.archive;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.cmdralph.fabricated.core.testutil.TestJars;

class SafeArchiveTest {
	@TempDir
	Path tmp;

	@Test
	void readsValidArchive() throws Exception {
		Path jar = TestJars.create().text("a/b.txt", "hello").write(tmp.resolve("ok.jar"));
		try (SafeArchive archive = SafeArchive.open(jar, ArchiveLimits.DEFAULTS)) {
			assertEquals("hello", archive.readText("a/b.txt"));
			assertNull(archive.read("missing"));
			assertTrue(archive.contains("a/b.txt"));
		}
	}

	/** What translating a jar may hold in memory is sized from this, not from the compressed file. */
	@Test
	void reportsTheDeclaredUncompressedSize(@TempDir Path tmp) throws Exception {
		Path jar = TestJars.create().bytes("zeros.bin", new byte[300_000]).text("a.txt", "hello").write(tmp.resolve("big.jar"));
		try (SafeArchive archive = SafeArchive.open(jar, ArchiveLimits.DEFAULTS)) {
			assertEquals(300_005, archive.declaredUncompressedSize());
			assertTrue(Files.size(jar) < 300_000 / 10, "zeros compress well: the file is a small fraction of the data");
		}
	}

	@Test
	void rejectsUnsafeEntryNames() {
		for (String bad : new String[] {"../evil.txt", "/abs.txt", "a\\b.txt", "C:/x.txt", "a/../../b", "a//b", "./x", "a\u0000b"}) {
			assertThrows(ArchiveException.class, () -> SafeArchive.validateName(bad), bad);
		}
	}

	@Test
	void rejectsZipSlipArchive() throws Exception {
		Path jar = TestJars.create().text("../../outside.txt", "pwned").write(tmp.resolve("slip.jar"));
		ArchiveException e = assertThrows(ArchiveException.class, () -> SafeArchive.open(jar, ArchiveLimits.DEFAULTS));
		assertTrue(e.getMessage().contains("Unsafe"), e.getMessage());
	}

	@Test
	void rejectsDuplicateEntries() throws Exception {
		// Write "a.txt" and "b.txt", then rename the second to "a.txt" in both headers (same length).
		byte[] bytes = TestJars.create().text("a.txt", "1").text("b.txt", "2").toBytes();
		String s = new String(bytes, StandardCharsets.ISO_8859_1).replace("b.txt", "a.txt");
		Path jar = Files.write(tmp.resolve("dup.jar"), s.getBytes(StandardCharsets.ISO_8859_1));
		ArchiveException e = assertThrows(ArchiveException.class, () -> SafeArchive.open(jar, ArchiveLimits.DEFAULTS));
		assertTrue(e.getMessage().contains("Duplicate") || e.getMessage().contains("zip"), e.getMessage());
	}

	@Test
	void rejectsCompressionBombs() throws Exception {
		Path jar = TestJars.create().bytes("bomb.bin", new byte[8 << 20]).write(tmp.resolve("bomb.jar"));
		ArchiveException e = assertThrows(ArchiveException.class, () -> SafeArchive.open(jar, ArchiveLimits.DEFAULTS));
		assertTrue(e.getMessage().contains("compression ratio"), e.getMessage());
	}

	@Test
	void enforcesLimitsOnActualBytes() throws Exception {
		ArchiveLimits tight = new ArchiveLimits(1 << 20, 1024, 1 << 20, 100, 100, 1);
		Path jar = TestJars.create().bytes("big.bin", new byte[4096]).write(tmp.resolve("big.jar"));
		assertThrows(ArchiveException.class, () -> SafeArchive.open(jar, tight));

		ArchiveLimits fewEntries = new ArchiveLimits(1 << 20, 1 << 20, 1 << 20, 2, 100, 1);
		Path many = TestJars.create().text("1", "a").text("2", "b").text("3", "c").write(tmp.resolve("many.jar"));
		assertThrows(ArchiveException.class, () -> SafeArchive.open(many, fewEntries));
	}

	@Test
	void rejectsNonZipFiles() throws Exception {
		Path notZip = Files.writeString(tmp.resolve("not.jar"), "this is not a zip file");
		assertThrows(ArchiveException.class, () -> SafeArchive.open(notZip, ArchiveLimits.DEFAULTS));
	}

	@Test
	void neverModifiesTheInputFile() throws Exception {
		Path jar = TestJars.create().text("x.txt", "content").write(tmp.resolve("orig.jar"));
		String before = Hashing.sha256(jar);
		try (SafeArchive archive = SafeArchive.open(jar, ArchiveLimits.DEFAULTS)) {
			archive.read("x.txt");
		}
		assertEquals(before, Hashing.sha256(jar));
	}

	@Test
	void deterministicWriterIsReproducibleAndOrderIndependent() throws Exception {
		Map<String, byte[]> a = new LinkedHashMap<>();
		a.put("z/last.txt", "z".getBytes(StandardCharsets.UTF_8));
		a.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
		a.put("a/first.class", new byte[] {1, 2, 3});
		Map<String, byte[]> b = new TreeMap<>(a);

		ByteArrayOutputStream outA = new ByteArrayOutputStream();
		ByteArrayOutputStream outB = new ByteArrayOutputStream();
		DeterministicJarWriter.write(outA, a);
		DeterministicJarWriter.write(outB, b);
		assertArrayEquals(outA.toByteArray(), outB.toByteArray());

		Path written = tmp.resolve("det.jar");
		DeterministicJarWriter.write(written, a);
		try (SafeArchive archive = SafeArchive.open(written, ArchiveLimits.DEFAULTS)) {
			assertEquals("META-INF/", archive.entryNames().get(0));
			assertEquals("META-INF/MANIFEST.MF", archive.entryNames().get(1));
			assertArrayEquals(new byte[] {1, 2, 3}, archive.read("a/first.class"));
		}
		try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(written.toFile())) {
			ZipEntry e = zf.getEntry("a/first.class");
			assertEquals(ZipEntry.STORED, e.getMethod());
			assertNull(e.getExtra());
		}
	}
}
