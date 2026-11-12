package io.github.cmdralph.fabricated.core.archive;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
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

	/** The jar {@code builder} writes, with every name {@code from} renamed {@code to} (of the same length) in both headers. */
	private Path renamed(TestJars builder, String from, String to, String file) throws Exception {
		String s = new String(builder.toBytes(), StandardCharsets.ISO_8859_1).replace(from, to);
		return Files.write(tmp.resolve(file), s.getBytes(StandardCharsets.ISO_8859_1));
	}

	/**
	 * SEC-11: ReplayMod 1.20.1's jar has the directory entry {@code assets/} twice, both empty, and nothing else twice; it
	 * was refused. A directory's entry holds nothing two readers could see differently, nothing is extracted under an
	 * entry's name, and a directory is never read, so a repeated empty one is accepted, once. Its copies still count
	 * against the entry limit.
	 */
	@Test
	void acceptsADirectorysEmptyEntryRepeated() throws Exception {
		Path jar = renamed(TestJars.create().bytes("assets/", new byte[0]).text("assets/x/a.txt", "1").bytes("assetz/", new byte[0]),
				"assetz/", "assets/", "dir-twice.jar");
		try (SafeArchive archive = SafeArchive.open(jar, ArchiveLimits.DEFAULTS)) {
			assertEquals(java.util.List.of("assets/", "assets/x/a.txt"), archive.entryNames());
			assertTrue(archive.isDirectory("assets/"));
			assertNull(archive.read("assets/"));
			assertEquals("1", archive.readText("assets/x/a.txt"));
		}
		ArchiveLimits twoEntries = new ArchiveLimits(1 << 20, 1 << 20, 1 << 20, 2, 100, 1);
		ArchiveException e = assertThrows(ArchiveException.class, () -> SafeArchive.open(jar, twoEntries));
		assertTrue(e.getMessage().contains("more than 2 entries"), e.getMessage());
	}

	/**
	 * SEC-11's limit: a repeated file stays refused, whether or not it is empty, as two readers may take different copies;
	 * so does a repeated directory's entry when one copy declares content, which no directory has.
	 */
	@Test
	void stillRejectsARepeatedFileOrADirectoryEntryWithContent() throws Exception {
		Path emptyFiles = renamed(TestJars.create().bytes("a.txt", new byte[0]).bytes("b.txt", new byte[0]), "b.txt", "a.txt", "empty-twice.jar");
		ArchiveException e = assertThrows(ArchiveException.class, () -> SafeArchive.open(emptyFiles, ArchiveLimits.DEFAULTS));
		assertTrue(e.getMessage().contains("Duplicate entry 'a.txt'"), e.getMessage());

		Path withContent = renamed(TestJars.create().bytes("assets/", new byte[0]).text("assetz/", "hidden"), "assetz/", "assets/", "dir-content.jar");
		e = assertThrows(ArchiveException.class, () -> SafeArchive.open(withContent, ArchiveLimits.DEFAULTS));
		assertTrue(e.getMessage().contains("Duplicate entry 'assets/'"), e.getMessage());

		Path contentFirst = renamed(TestJars.create().text("assets/", "hidden").bytes("assetz/", new byte[0]), "assetz/", "assets/", "content-dir.jar");
		e = assertThrows(ArchiveException.class, () -> SafeArchive.open(contentFirst, ArchiveLimits.DEFAULTS));
		assertTrue(e.getMessage().contains("Duplicate entry 'assets/'"), e.getMessage());
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

	/** An entry is read into one array of its size, not into buffers then copied: about its size is allocated, not twice it. */
	@Test
	void readsAnEntryIntoOneArray() throws Exception {
		byte[] data = new byte[1 << 20];
		new Random(3).nextBytes(data);
		Path jar = TestJars.create().bytes("data.bin", data).write(tmp.resolve("data.jar"));
		com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
		try (SafeArchive archive = SafeArchive.open(jar, ArchiveLimits.DEFAULTS)) {
			assertArrayEquals(data, archive.read("data.bin"));
			long before = threads.getCurrentThreadAllocatedBytes();
			byte[] again = archive.read("data.bin");
			long allocated = threads.getCurrentThreadAllocatedBytes() - before;
			assertArrayEquals(data, again);
			assertTrue(allocated < data.length * 3L / 2, allocated + " bytes allocated reading " + data.length);
		}
	}

	/**
	 * A header declaring far more than an entry holds costs no more to read than what it holds: the compressed size beside
	 * it is the archive author's too, so it bounds nothing. Here a 64 MiB entry, at a ratio the archive's check takes,
	 * holds 16 bytes.
	 */
	@Test
	void aLyingHeaderDoesntDecideWhatAReadAllocates() throws Exception {
		byte[] bytes = TestJars.create().bytes("lie.bin", "sixteen bytes!!!".getBytes(StandardCharsets.UTF_8)).toBytes();
		ByteBuffer zip = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		for (int i = 0; i + 46 < bytes.length; i++) {
			if (zip.getInt(i) != 0x02014b50) continue;
			zip.putInt(i + 20, (64 << 20) / 100 + 1); // the central directory's compressed size
			zip.putInt(i + 24, 64 << 20); // and its uncompressed size
		}
		Path lying = Files.write(tmp.resolve("lie.jar"), bytes);
		com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
		try (SafeArchive archive = SafeArchive.open(lying, ArchiveLimits.DEFAULTS)) {
			long before = threads.getCurrentThreadAllocatedBytes();
			byte[] read = archive.read("lie.bin");
			long allocated = threads.getCurrentThreadAllocatedBytes() - before;
			assertEquals("sixteen bytes!!!", new String(read, StandardCharsets.UTF_8));
			assertTrue(allocated < 1 << 20, allocated + " bytes allocated reading 16 bytes under a 64 MiB header");
		}
	}

	/** Copying an entry gives the bytes reading it does, with the same limits. */
	@Test
	void copiesAnEntryAsItReadsIt() throws Exception {
		byte[] data = new byte[300_000];
		new Random(5).nextBytes(data);
		Path jar = TestJars.create().bytes("a/data.bin", data).text("b.txt", "hello").text("dir/", "").write(tmp.resolve("copy.jar"));
		try (SafeArchive archive = SafeArchive.open(jar, ArchiveLimits.DEFAULTS)) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			assertTrue(archive.copy("a/data.bin", out));
			assertArrayEquals(archive.read("a/data.bin"), out.toByteArray());
			out.reset();
			assertTrue(archive.copy("b.txt", out));
			assertEquals("hello", out.toString(StandardCharsets.UTF_8));
			out.reset();
			assertFalse(archive.copy("missing", out));
			assertFalse(archive.copy("dir/", out));
			assertEquals(0, out.size());
		}

		byte[] bytes = TestJars.create().bytes("big.bin", new byte[200_000]).toBytes();
		ByteBuffer zip = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		for (int i = 0; i + 46 < bytes.length; i++) {
			if (zip.getInt(i) == 0x02014b50) zip.putInt(i + 24, 1000); // the central directory's uncompressed size
		}
		Path lying = Files.write(tmp.resolve("lying.jar"), bytes);
		try (SafeArchive archive = SafeArchive.open(lying, ArchiveLimits.DEFAULTS)) {
			ArchiveException e = assertThrows(ArchiveException.class, () -> archive.copy("big.bin", OutputStream.nullOutputStream()));
			assertTrue(e.getMessage().contains("more bytes than its header declares"), e.getMessage());
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
