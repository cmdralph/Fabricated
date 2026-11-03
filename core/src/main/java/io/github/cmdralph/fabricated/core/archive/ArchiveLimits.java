package io.github.cmdralph.fabricated.core.archive;

import java.util.List;

/**
 * Hard limits applied to every untrusted archive.
 *
 * @param maxJarBytes               maximum size of the archive file itself
 * @param maxEntryBytes             maximum uncompressed size of a single entry
 * @param maxTotalUncompressedBytes maximum uncompressed bytes read from one archive in total
 * @param maxEntries                maximum number of entries
 * @param maxCompressionRatio       maximum uncompressed/compressed ratio for entries larger than 1 MiB
 * @param maxNestingDepth           maximum jar-in-jar nesting depth
 */
public record ArchiveLimits(
		long maxJarBytes,
		long maxEntryBytes,
		long maxTotalUncompressedBytes,
		int maxEntries,
		int maxCompressionRatio,
		int maxNestingDepth) {

	public static final ArchiveLimits DEFAULTS = new ArchiveLimits(
			256L << 20,
			// Flashback ships a 71 MB native library (LIMIT-1); the total and the compression ratio still bound a bomb.
			128L << 20,
			512L << 20,
			65_536,
			100,
			// Polymer's bundle nests its packet library four jars deep (LIMIT-2); a top-level jar's nested jars are bounded
			// in number and bytes whatever their depth.
			8);

	/**
	 * The defaults of earlier versions, which wrote them to every new {@code config/fabricated.json}: a file holding one
	 * of these sets as it was written reads as {@link #DEFAULTS}, so a raised default reaches it (LIMIT-2).
	 */
	public static final List<ArchiveLimits> EARLIER_DEFAULTS = List.of(
			new ArchiveLimits(256L << 20, 64L << 20, 512L << 20, 65_536, 100, 3),
			new ArchiveLimits(256L << 20, 128L << 20, 512L << 20, 65_536, 100, 3));

	/** Entries smaller than this are exempt from the ratio check (tiny files compress extremely well). */
	public static final long RATIO_CHECK_THRESHOLD = 1L << 20;

	/**
	 * Limits for re-reading a mod after translation. On 1.21.11 the remapped jar is written uncompressed (byte-for-byte
	 * reproducible output), so its file size is about the mod's total uncompressed size, and it has an entry for every
	 * directory, which the original may leave out; the other limits still apply to its contents.
	 */
	public ArchiveLimits forTranslatedJars() {
		long uncompressedJar = maxTotalUncompressedBytes + (long) maxEntries * 512L;
		int entries = (int) Math.min(Integer.MAX_VALUE, 2L * maxEntries);
		return new ArchiveLimits(Math.max(maxJarBytes, uncompressedJar), maxEntryBytes, maxTotalUncompressedBytes, entries,
				maxCompressionRatio, maxNestingDepth);
	}

	public ArchiveLimits {
		if (maxJarBytes <= 0 || maxEntryBytes <= 0 || maxTotalUncompressedBytes <= 0 || maxEntries <= 0
				|| maxCompressionRatio <= 0 || maxNestingDepth < 0) {
			throw new IllegalArgumentException("Archive limits must be positive");
		}
	}
}
