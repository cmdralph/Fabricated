package io.github.cmdralph.fabricated.core.archive;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Read-only view of an untrusted jar that validates every entry before anything is read.
 *
 * <p>Checks performed when opening: archive size, entry count, entry names (no absolute paths,
 * {@code ..} or {@code .} segments, backslashes, drive letters, control characters or duplicates, but for a
 * directory's empty entry repeated), declared entry sizes and compression ratios. Reads additionally count the actual decompressed
 * bytes, so archives that lie about sizes in their headers are still bounded.
 *
 * <p>The underlying file is opened read-only and never modified.
 */
public final class SafeArchive implements Closeable {
	/** What {@link #copy} reads at a time. */
	private static final int COPY_BUFFER = 64 << 10;

	private final Path path;
	private final ArchiveLimits limits;
	private final ZipFile zip;
	private final Map<String, ZipEntry> entries;
	private final long declaredSize;
	private long totalRead;

	private SafeArchive(Path path, ArchiveLimits limits, ZipFile zip, Map<String, ZipEntry> entries, long declaredSize) {
		this.path = path;
		this.limits = limits;
		this.zip = zip;
		this.entries = entries;
		this.declaredSize = declaredSize;
	}

	public static SafeArchive open(Path path, ArchiveLimits limits) throws ArchiveException {
		long size;
		try {
			if (!Files.isRegularFile(path)) {
				throw new ArchiveException("Not a regular file: " + path);
			}
			size = Files.size(path);
		} catch (IOException e) {
			throw new ArchiveException("Cannot read " + path + ": " + e.getMessage(), e);
		}
		if (size > limits.maxJarBytes()) {
			throw new ArchiveException("Archive is " + size + " bytes, larger than the limit of " + limits.maxJarBytes());
		}

		ZipFile zip;
		try {
			zip = new ZipFile(path.toFile(), ZipFile.OPEN_READ, StandardCharsets.UTF_8);
		} catch (ZipException e) {
			throw new ArchiveException("Not a valid zip/jar file: " + e.getMessage(), e);
		} catch (IOException e) {
			throw new ArchiveException("Cannot open archive: " + e.getMessage(), e);
		}

		try {
			Map<String, ZipEntry> entries = new LinkedHashMap<>();
			long declaredTotal = 0;
			// Every entry counts, a repeated directory's too.
			int count = 0;
			Enumeration<? extends ZipEntry> en = zip.entries();
			while (en.hasMoreElements()) {
				ZipEntry entry = en.nextElement();
				if (count++ >= limits.maxEntries()) {
					throw new ArchiveException("Archive has more than " + limits.maxEntries() + " entries");
				}
				String name = entry.getName();
				validateName(name);
				ZipEntry first = entries.putIfAbsent(name, entry);
				if (first != null) {
					// A directory's empty entry repeated (ReplayMod 1.20.1's assets/) holds nothing two readers could see
					// differently: no entry is extracted by name, and a directory is never read (SEC-11).
					if (isEmptyDirectory(first) && isEmptyDirectory(entry)) continue;
					throw new ArchiveException("Duplicate entry '" + name + "'");
				}
				long declared = entry.getSize();
				if (declared > limits.maxEntryBytes()) {
					throw new ArchiveException("Entry '" + name + "' is " + declared + " bytes uncompressed, above the limit of " + limits.maxEntryBytes());
				}
				if (declared > 0) {
					declaredTotal += declared;
					if (declaredTotal > limits.maxTotalUncompressedBytes()) {
						throw new ArchiveException("Archive declares more than " + limits.maxTotalUncompressedBytes() + " uncompressed bytes");
					}
				}
				long compressed = entry.getCompressedSize();
				if (declared > ArchiveLimits.RATIO_CHECK_THRESHOLD && compressed > 0
						&& declared / compressed > limits.maxCompressionRatio()) {
					throw new ArchiveException("Entry '" + name + "' has a suspicious compression ratio of " + declared / compressed + ":1");
				}
			}
			return new SafeArchive(path, limits, zip, entries, declaredTotal);
		} catch (ArchiveException e) {
			closeQuietly(zip, e);
			throw e;
		} catch (RuntimeException e) {
			// ZipFile throws unchecked exceptions for some malformed central directories.
			ArchiveException ex = new ArchiveException("Malformed archive: " + e.getMessage(), e);
			closeQuietly(zip, ex);
			throw ex;
		}
	}

	/** A directory's entry (its name ends in {@code /}) declaring no content. */
	private static boolean isEmptyDirectory(ZipEntry entry) {
		return entry.isDirectory() && entry.getSize() == 0;
	}

	private static void closeQuietly(ZipFile zip, Exception primary) {
		try {
			zip.close();
		} catch (IOException e) {
			primary.addSuppressed(e);
		}
	}

	/**
	 * Validates an archive entry name; package-private for tests. Every archive opened checks each of its entries' names,
	 * so this allocates nothing: the checks with a regular expression and a split took most of opening a game jar
	 * (PERF-2).
	 */
	static void validateName(String name) throws ArchiveException {
		int length = name.length();
		if (length == 0) throw new ArchiveException("Empty entry name");
		if (length > 1024) throw new ArchiveException("Entry name too long");
		char first = name.charAt(0);
		if (first == '/') throw new ArchiveException("Absolute entry name '" + name + "'");
		if (name.indexOf('\\') >= 0) throw new ArchiveException("Backslash in entry name '" + name + "'");
		if (length > 1 && name.charAt(1) == ':' && (first >= 'A' && first <= 'Z' || first >= 'a' && first <= 'z')) {
			throw new ArchiveException("Drive letter in entry name '" + name + "'");
		}
		for (int i = 0; i < length; i++) {
			char c = name.charAt(i);
			if (c < 0x20 || c == 0x7f) throw new ArchiveException("Control character in entry name");
		}
		// No empty, "." or ".." segment, a directory's trailing slash aside.
		int end = name.charAt(length - 1) == '/' ? length - 1 : length;
		int start = 0;
		for (int i = 0; i <= end; i++) {
			if (i < end && name.charAt(i) != '/') continue;
			int segment = i - start;
			if (segment == 0 || name.charAt(start) == '.' && (segment == 1 || segment == 2 && name.charAt(start + 1) == '.')) {
				throw new ArchiveException("Unsafe path segment in entry name '" + name + "'");
			}
			start = i + 1;
		}
	}

	/** The uncompressed size the archive's entries declare (unchecked until read, but within the limits). */
	public long declaredUncompressedSize() {
		return declaredSize;
	}

	/** All entry names (including directories) in archive order. */
	public List<String> entryNames() {
		return List.copyOf(entries.keySet());
	}

	/** Names of the {@code .class} entries outside {@code META-INF/}, excluding module descriptors, in archive order. */
	public List<String> classEntryNames() {
		return entries.keySet().stream()
				.filter(n -> n.endsWith(".class") && !n.startsWith("META-INF/") && !n.endsWith("module-info.class"))
				.toList();
	}

	public boolean contains(String name) {
		return entries.containsKey(name);
	}

	public boolean isDirectory(String name) {
		ZipEntry e = entries.get(name);
		return e != null && e.isDirectory();
	}

	/**
	 * Reads an entry fully, enforcing the per-entry and per-archive limits on the bytes actually decompressed, and the
	 * entry's declared size: {@code ZipFile} inflates past it, and the checks made when the archive was opened (the
	 * compression ratio, the declared total) and the memory a translation reserves trust it.
	 *
	 * @return the bytes, or {@code null} if the entry does not exist or is a directory
	 */
	public synchronized byte[] read(String name) throws ArchiveException {
		ZipEntry entry = entries.get(name);
		if (entry == null || entry.isDirectory()) return null;
		long limit = Math.min(limits.maxEntryBytes(), limits.maxTotalUncompressedBytes() - totalRead);
		long budget = entry.getSize() >= 0 ? Math.min(entry.getSize(), limit) : limit;
		try (InputStream in = zip.getInputStream(entry)) {
			byte[] data;
			if (budget == entry.getSize() && presized(entry)) {
				// Into one array of the size it declares: readNBytes reads into buffers of its own, then copies them.
				data = new byte[(int) budget];
				int read = in.readNBytes(data, 0, data.length);
				if (read < data.length) data = Arrays.copyOf(data, read);
			} else {
				// Reading one byte past the budget detects oversized entries whatever their headers claim; the trailing
				// read() also rejects entries too large for a Java array when the budget is above that.
				data = in.readNBytes((int) Math.min(budget + 1, Integer.MAX_VALUE - 8));
			}
			if (data.length > budget || in.read() >= 0) throw oversized(name, budget, limit);
			totalRead += data.length;
			return data;
		} catch (ArchiveException e) {
			throw e;
		} catch (IOException e) {
			throw failed(name, e);
		}
	}

	/**
	 * Copies an entry to {@code out} with the limits {@link #read} enforces, without holding it in memory: for an entry
	 * only written elsewhere (a server bundler's game jar, extracted to the cache). On a failure, what was copied before
	 * it is the caller's to discard.
	 *
	 * @return whether the entry exists (a directory doesn't count)
	 * @throws ArchiveException where reading the entry fails or exceeds the limits
	 * @throws IOException      where writing to {@code out} fails, as {@code out} threw it
	 */
	public synchronized boolean copy(String name, OutputStream out) throws IOException {
		ZipEntry entry = entries.get(name);
		if (entry == null || entry.isDirectory()) return false;
		long limit = Math.min(limits.maxEntryBytes(), limits.maxTotalUncompressedBytes() - totalRead);
		long budget = entry.getSize() >= 0 ? Math.min(entry.getSize(), limit) : limit;
		long copied = 0;
		byte[] buffer = new byte[COPY_BUFFER];
		InputStream opened;
		try {
			opened = zip.getInputStream(entry);
		} catch (IOException e) {
			throw failed(name, e);
		}
		try (InputStream in = opened) {
			for (int n; (n = readSome(in, buffer, name)) > 0; copied += n) {
				if (n > budget - copied) throw oversized(name, budget, limit);
				out.write(buffer, 0, n);
			}
		}
		totalRead += copied;
		return true;
	}

	private static int readSome(InputStream in, byte[] buffer, String name) throws ArchiveException {
		try {
			return in.read(buffer);
		} catch (IOException e) {
			throw failed(name, e);
		}
	}

	private static ArchiveException failed(String name, IOException e) {
		return new ArchiveException("Failed to read entry '" + name + "': " + e.getMessage(), e);
	}

	/**
	 * Whether {@link #read} may make the array for an entry from the size it declares, before reading it: only a small
	 * one ({@link ArchiveLimits#RATIO_CHECK_THRESHOLD}), as a header declaring more than the entry holds then costs at
	 * most that. Both sizes in an archive's headers are its author's to write: a compressed size bounds nothing the
	 * archive's own length doesn't, so a larger entry is read into buffers as it arrives, costing what it holds.
	 */
	private static boolean presized(ZipEntry entry) {
		return entry.getSize() <= ArchiveLimits.RATIO_CHECK_THRESHOLD;
	}

	private static ArchiveException oversized(String name, long budget, long limit) {
		return new ArchiveException("Entry '" + name + "' " + (budget < limit ? "holds more bytes than its header declares"
				: "exceeds the decompression limit"));
	}

	/** Reads an entry as UTF-8 text, or returns {@code null} if absent. */
	public String readText(String name) throws ArchiveException {
		byte[] data = read(name);
		return data == null ? null : new String(data, StandardCharsets.UTF_8);
	}

	@Override
	public void close() throws IOException {
		zip.close();
	}

	@Override
	public String toString() {
		return "SafeArchive[" + path + "]";
	}
}
