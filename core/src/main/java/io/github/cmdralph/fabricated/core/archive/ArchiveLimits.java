package io.github.cmdralph.fabricated.core.archive;

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
			64L << 20,
			512L << 20,
			65_536,
			100,
			3);

	/** Entries smaller than this are exempt from the ratio check (tiny files compress extremely well). */
	public static final long RATIO_CHECK_THRESHOLD = 1L << 20;

	/**
	 * Limits for re-reading a mod after translation. On 1.21.11 the remapped jar is written uncompressed (byte-for-byte
	 * reproducible output), so its file size is about the mod's total uncompressed size; the other limits still apply
	 * to its contents.
	 */
	public ArchiveLimits forTranslatedJars() {
		long uncompressedJar = maxTotalUncompressedBytes + (long) maxEntries * 512L;
		return maxJarBytes >= uncompressedJar ? this
				: new ArchiveLimits(uncompressedJar, maxEntryBytes, maxTotalUncompressedBytes, maxEntries, maxCompressionRatio, maxNestingDepth);
	}

	public ArchiveLimits {
		if (maxJarBytes <= 0 || maxEntryBytes <= 0 || maxTotalUncompressedBytes <= 0 || maxEntries <= 0
				|| maxCompressionRatio <= 0 || maxNestingDepth < 0) {
			throw new IllegalArgumentException("Archive limits must be positive");
		}
	}
}
