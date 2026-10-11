package io.github.cmdralph.fabricated.fabricloader.version;

import java.util.Objects;

import net.fabricmc.loader.api.Version;

/**
 * A version that is not a valid semantic version. Compared lexically by its friendly string,
 * which matches Fabric Loader's behaviour for free-form versions.
 */
public final class StringVersion implements Version {
	private final String version;

	public StringVersion(String version) {
		this.version = Objects.requireNonNull(version, "version");
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
	public boolean equals(Object obj) {
		return obj instanceof StringVersion other && version.equals(other.version);
	}

	@Override
	public int hashCode() {
		return version.hashCode();
	}

	@Override
	public String toString() {
		return version;
	}
}
