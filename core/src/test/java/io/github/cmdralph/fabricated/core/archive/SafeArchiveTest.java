package io.github.cmdralph.fabricated.core.archive;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
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

	/**
	 * The allocation-free check accepts and refuses the names the check with a regular expression and a split did
	 * (PERF-2), over short names of the characters that matter; it also refuses a drive letter followed by a line
	 * separator, which the expression's {@code .*} let through.
	 */
	@Test
	void validatesNamesAsTheFormerCheckDid() {
		char[] alphabet = {'a', 'Z', '.', '/', '\\', ':', '\u0001', '\u007f', ' ', '\u00e9', '\u2028', '1'};
		Random random = new Random(42);
		for (int n = 0; n < 300_000; n++) {
			char[] name = new char[random.nextInt(9)];
			for (int i = 0; i < name.length; i++) name[i] = alphabet[random.nextInt(alphabet.length)];
			String s = new String(name);
			boolean driveLetter = s.length() > 1 && s.charAt(1) == ':' && Character.isLetter(s.charAt(0)) && s.charAt(0) < 0x80;
			boolean accepted = accepts(s);
			if (driveLetter) {
				assertFalse(accepted, s);
			} else {
				assertEquals(formerlyAccepted(s), accepted, () -> "name " + s.chars().mapToObj(Integer::toHexString).toList());
			}
		}
		for (String good : new String[] {"a/b/c.class", "META-INF/", "a/b/", "a/.b/..c", "x", "..a"}) assertTrue(accepts(good), good);
		for (String bad : new String[] {"a//", "..", ".", "a/./b", "a/..", "a/../", "C:", "z:x"}) assertFalse(accepts(bad), bad);
	}

	private static boolean accepts(String name) {
		try {
			SafeArchive.validateName(name);
			return true;
		} catch (ArchiveException e) {
			return false;
		}
	}

	/** The former check, as it read before PERF-2. */
	private static boolean formerlyAccepted(String name) {
		if (name.isEmpty() || name.length() > 1024 || name.startsWith("/") || name.indexOf('\\') >= 0) return false;
		if (java.util.regex.Pattern.compile("^[A-Za-z]:.*").matcher(name).matches()) return false;
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (c < 0x20 || c == 0x7f) return false;
		}
		String trimmed = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
		for (String segment : trimmed.split("/", -1)) {
			if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) return false;
		}
		return true;
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

	/**
	 * ZipFile inflates past an entry's declared size, which the compression ratio check and a translation's memory
	 * reservation trust: an entry declaring 1000 bytes that holds 200,000 is refused when read.
	 */
	@Test
	void refusesAnEntryHoldingMoreThanItsHeaderDeclares() throws Exception {
		byte[] bytes = TestJars.create().bytes("big.bin", new byte[200_000]).text("small.txt", "hello").toBytes();
		ByteBuffer zip = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		for (int i = 0; i + 46 < bytes.length; i++) {
			if (zip.getInt(i) != 0x02014b50) continue; // a central directory header
			String name = new String(bytes, i + 46, zip.getShort(i + 28), StandardCharsets.UTF_8);
			if (name.equals("big.bin")) zip.putInt(i + 24, 1000); // its uncompressed size
		}
		Path jar = Files.write(tmp.resolve("lying.jar"), bytes);
		try (SafeArchive archive = SafeArchive.open(jar, ArchiveLimits.DEFAULTS)) {
			assertEquals(1005, archive.declaredUncompressedSize());
			ArchiveException e = assertThrows(ArchiveException.class, () -> archive.read("big.bin"));
			assertTrue(e.getMessage().contains("more bytes than its header declares"), e.getMessage());
			assertEquals("hello", archive.readText("small.txt"));
		}
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
