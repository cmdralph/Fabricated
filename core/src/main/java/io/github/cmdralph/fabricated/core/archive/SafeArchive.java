package io.github.cmdralph.fabricated.core.archive;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Read-only view of an untrusted jar that validates every entry before anything is read.
 *
 * <p>Checks performed when opening: archive size, entry count, entry names (no absolute paths,
 * {@code ..} or {@code .} segments, backslashes, drive letters, control characters or duplicates),
 * declared entry sizes and compression ratios. Reads additionally count the actual decompressed
 * bytes, so archives that lie about sizes in their headers are still bounded.
 *
 * <p>The underlying file is opened read-only and never modified.
 */
public final class SafeArchive implements Closeable {
	private static final Pattern DRIVE_LETTER = Pattern.compile("^[A-Za-z]:.*");

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
			Enumeration<? extends ZipEntry> en = zip.entries();
			while (en.hasMoreElements()) {
				ZipEntry entry = en.nextElement();
				if (entries.size() >= limits.maxEntries()) {
					throw new ArchiveException("Archive has more than " + limits.maxEntries() + " entries");
				}
				String name = entry.getName();
				validateName(name);
				if (entries.putIfAbsent(name, entry) != null) {
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

	private static void closeQuietly(ZipFile zip, Exception primary) {
		try {
			zip.close();
		} catch (IOException e) {
			primary.addSuppressed(e);
		}
	}

	/** Validates an archive entry name; package-private for tests. */
	static void validateName(String name) throws ArchiveException {
		if (name.isEmpty()) throw new ArchiveException("Empty entry name");
		if (name.length() > 1024) throw new ArchiveException("Entry name too long");
		if (name.startsWith("/")) throw new ArchiveException("Absolute entry name '" + name + "'");
		if (name.indexOf('\\') >= 0) throw new ArchiveException("Backslash in entry name '" + name + "'");
		if (DRIVE_LETTER.matcher(name).matches()) throw new ArchiveException("Drive letter in entry name '" + name + "'");
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (c < 0x20 || c == 0x7f) throw new ArchiveException("Control character in entry name");
		}
		String trimmed = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
		for (String segment : trimmed.split("/", -1)) {
			if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
				throw new ArchiveException("Unsafe path segment in entry name '" + name + "'");
			}
		}
	}

	/** All entry names (including directories) in archive order. */
	/** The uncompressed size the archive's entries declare (unchecked until read, but within the limits). */
	public long declaredUncompressedSize() {
		return declaredSize;
	}

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
	 * Reads an entry fully, enforcing the per-entry and per-archive limits on the bytes actually
	 * decompressed.
	 *
	 * @return the bytes, or {@code null} if the entry does not exist or is a directory
	 */
	public synchronized byte[] read(String name) throws ArchiveException {
		ZipEntry entry = entries.get(name);
		if (entry == null || entry.isDirectory()) return null;
		long budget = Math.min(limits.maxEntryBytes(), limits.maxTotalUncompressedBytes() - totalRead);
		try (InputStream in = zip.getInputStream(entry)) {
			// Reading one byte past the budget detects oversized entries whatever their headers claim; the trailing
			// read() also rejects entries too large for a Java array when the budget is above that.
			byte[] data = in.readNBytes((int) Math.min(budget + 1, Integer.MAX_VALUE - 8));
			if (data.length > budget || in.read() >= 0) {
				throw new ArchiveException("Entry '" + name + "' exceeds the decompression limit");
			}
			totalRead += data.length;
			return data;
		} catch (ArchiveException e) {
			throw e;
		} catch (IOException e) {
			throw new ArchiveException("Failed to read entry '" + name + "': " + e.getMessage(), e);
		}
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
