package io.github.cmdralph.fabricated.fabricloader.version;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

/**
 * Entry point used by {@link Version#parse(String)} and {@link SemanticVersion#parse(String)}.
 */
public final class VersionParser {
	private VersionParser() {
	}

	/**
	 * Parses any version: semantic when possible, otherwise a {@link StringVersion}.
	 *
	 * @param storeX whether {@code x}/{@code X}/{@code *} components are accepted as wildcards
	 */
	public static Version parse(String s, boolean storeX) throws VersionParsingException {
		if (s == null || s.isEmpty()) {
			throw new VersionParsingException("Version must be a non-empty string!");
		}
		try {
			return new SemanticVersionImpl(s, storeX);
		} catch (VersionParsingException e) {
			return new StringVersion(s);
		}
	}

	public static SemanticVersion parseSemantic(String s) throws VersionParsingException {
		if (s == null || s.isEmpty()) {
			throw new VersionParsingException("Version must be a non-empty string!");
		}
		return new SemanticVersionImpl(s, false);
	}
}
