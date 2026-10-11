package io.github.cmdralph.fabricated.core.archive;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes jars whose bytes depend only on their contents.
 *
 * <p>Entries are written in a fixed order ({@code META-INF/MANIFEST.MF} first, as the jar format
 * expects, then all others sorted by name), with parent directory entries, a constant DOS
 * timestamp, no extra fields, and STORED (uncompressed) data. STORED avoids depending on the zlib
 * build, so the same input produces the same bytes on every machine.
 */
public final class DeterministicJarWriter {
	/** Earliest timestamp representable in DOS format, used for every entry. */
	public static final LocalDateTime FIXED_TIME = LocalDateTime.of(1980, 2, 1, 0, 0, 0);
	private static final String MANIFEST = "META-INF/MANIFEST.MF";

	private DeterministicJarWriter() {
	}

	public static void write(Path out, Map<String, byte[]> files) throws IOException {
		Path parent = out.toAbsolutePath().getParent();
		if (parent != null) Files.createDirectories(parent);
		try (OutputStream os = Files.newOutputStream(out)) {
			write(os, files);
		}
	}

	public static void write(OutputStream os, Map<String, byte[]> files) throws IOException {
		TreeSet<String> directories = new TreeSet<>();
		for (String name : files.keySet()) {
			if (name.endsWith("/")) throw new IllegalArgumentException("Directory entries are generated automatically: " + name);
			int idx = name.indexOf('/');
			while (idx > 0) {
				directories.add(name.substring(0, idx + 1));
				idx = name.indexOf('/', idx + 1);
			}
		}

		List<String> order = new ArrayList<>();
		if (files.containsKey(MANIFEST)) {
			order.add("META-INF/");
			order.add(MANIFEST);
		}
		List<String> rest = new ArrayList<>();
		for (String dir : directories) {
			if (!dir.equals("META-INF/") || !files.containsKey(MANIFEST)) rest.add(dir);
		}
		for (String name : files.keySet()) {
			if (!name.equals(MANIFEST)) rest.add(name);
		}
		rest.sort(Comparator.naturalOrder());
		order.addAll(rest);

		ZipOutputStream zos = new ZipOutputStream(os);
		zos.setMethod(ZipOutputStream.STORED);
		for (String name : order) {
			byte[] data = name.endsWith("/") ? new byte[0] : files.get(name);
			ZipEntry entry = new ZipEntry(name);
			entry.setTimeLocal(FIXED_TIME);
			entry.setMethod(ZipEntry.STORED);
			entry.setSize(data.length);
			entry.setCompressedSize(data.length);
			CRC32 crc = new CRC32();
			crc.update(data);
			entry.setCrc(crc.getValue());
			zos.putNextEntry(entry);
			zos.write(data);
			zos.closeEntry();
		}
		zos.finish();
	}
}
