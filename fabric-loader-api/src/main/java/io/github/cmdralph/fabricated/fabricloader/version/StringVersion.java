package io.github.cmdralph.fabricated.fabricloader.version;

import java.util.Objects;

import net.fabricmc.loader.api.Version;

/**
 * A version that is not a valid semantic version. Compared lexically by its friendly string,
 * which matches Fabric Loader's behaviour for free-form versions.
 */
public record StringVersion(String version) implements Version {
	public StringVersion {
		Objects.requireNonNull(version, "version");
	}

	@Override
	public String getFriendlyString() {
		return version;
	}

	@Override
	public int compareTo(Version other) {
		return getFriendlyString().compareTo(other.getFriendlyString());
	}

	@Override
	public String toString() {
		return version;
	}
}
