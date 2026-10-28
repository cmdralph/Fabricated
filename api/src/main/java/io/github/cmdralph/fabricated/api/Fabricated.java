package io.github.cmdralph.fabricated.api;

import java.util.Optional;

import io.github.cmdralph.fabricated.api.internal.ApiHolder;

/**
 * Entry point of Fabricated's API.
 *
 * <pre>{@code
 * Fabricated.get().ifPresent(api -> {
 *     if (api.isFabricModLoaded("some-fabric-mod")) { ... }
 * });
 * }</pre>
 *
 * @since API 1.0
 */
public final class Fabricated {
	/** Fabricated's mod id, on NeoForge and in Fabric's {@code FabricLoader}. */
	public static final String MOD_ID = "fabricated";
	/** The API version this code was compiled against; compare with {@link FabricatedApi#apiVersion()} at runtime. */
	public static final String API_VERSION = "1.1.0";

	private Fabricated() {
	}

	/**
	 * The running API, or empty if Fabricated did not set up Fabric mod support this launch (it is not installed, failed
	 * to start, or is the build for another Minecraft version). Available before any mod's code runs; cheap to call.
	 */
	public static Optional<FabricatedApi> get() {
		return Optional.ofNullable(ApiHolder.get());
	}
}
