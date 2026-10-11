package io.github.cmdralph.fabricated.core.archive;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
	private long totalRead;

	private SafeArchive(Path path, ArchiveLimits limits, ZipFile zip, Map<String, ZipEntry> entries) {
		this.path = path;
		this.limits = limits;
		this.zip = zip;
		this.entries = entries;
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
			Set<String> seen = new HashSet<>();
			long declaredTotal = 0;
			Enumeration<? extends ZipEntry> en = zip.entries();
			while (en.hasMoreElements()) {
				ZipEntry entry = en.nextElement();
				if (entries.size() >= limits.maxEntries()) {
					throw new ArchiveException("Archive has more than " + limits.maxEntries() + " entries");
				}
				String name = entry.getName();
				validateName(name);
				if (!seen.add(name)) {
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
				entries.put(name, entry);
			}
			return new SafeArchive(path, limits, zip, entries);
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

	public Path path() {
		return path;
	}

	/** All entry names (including directories) in archive order. */
	public List<String> entryNames() {
		return Collections.unmodifiableList(new ArrayList<>(entries.keySet()));
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
			ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(Math.max(entry.getSize(), 32), 1 << 20));
			byte[] buffer = new byte[16 * 1024];
			long count = 0;
			int n;
			while ((n = in.read(buffer)) > 0) {
				count += n;
				if (count > budget) {
					throw new ArchiveException("Entry '" + name + "' exceeds the decompression limit");
				}
				out.write(buffer, 0, n);
			}
			totalRead += count;
			return out.toByteArray();
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
