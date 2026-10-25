package io.github.cmdralph.fabricated.core.translate;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * How the platform's loader learns of a translated Fabric mod (docs/PORTING.md §6 item 5): where it reads a mod's
 * descriptor and in which dialect, what the overlay's manifest adds, and whether it adds enum constants from a mod's
 * enum extension file. NeoForge's is {@link #NEOFORGE} and Forge's {@link #FORGE}; a branch for another loader hands the
 * pipeline its own.
 */
public interface PlatformDescriptor {
	/** NeoForge's: {@code META-INF/neoforge.mods.toml} as {@link ModsTomlWriter} writes it. */
	PlatformDescriptor NEOFORGE = new PlatformDescriptor() {
		@Override
		public String path() {
			return ModsTomlWriter.PATH;
		}

		@Override
		public String write(ModsTomlWriter.Spec spec) {
			return ModsTomlWriter.write(spec);
		}
	};

	/**
	 * Forge's, 47 to 66: {@code META-INF/mods.toml} in Forge's dialect ({@link ModsTomlWriter#writeForge}), the mixin
	 * configs in the manifest's {@code MixinConfigs}, where Mixin's default platform agent reads them (comma-separated),
	 * and no enum extension files.
	 */
	PlatformDescriptor FORGE = new PlatformDescriptor() {
		@Override
		public String path() {
			return ModsTomlWriter.FORGE_PATH;
		}

		@Override
		public String write(ModsTomlWriter.Spec spec) {
			return ModsTomlWriter.writeForge(spec);
		}

		@Override
		public Map<String, String> manifestAttributes(ModsTomlWriter.Spec spec) {
			if (spec.mixins().isEmpty()) return Map.of();
			return Map.of("MixinConfigs", spec.mixins().stream().map(ModsTomlWriter.MixinConfigSpec::config).collect(Collectors.joining(",")));
		}

		@Override
		public boolean enumExtensions() {
			return false;
		}
	};

	/** Where the platform's loader reads a mod's descriptor in its jar. */
	String path();

	/** The translated mod's descriptor. */
	String write(ModsTomlWriter.Spec spec);

	/** Attributes the overlay's manifest needs besides Fabricated's own; none by default. */
	default Map<String, String> manifestAttributes(ModsTomlWriter.Spec spec) {
		return Map.of();
	}

	/** Whether the platform's loader adds enum constants from the file a descriptor names (ENUM-1). */
	default boolean enumExtensions() {
		return true;
	}
}
