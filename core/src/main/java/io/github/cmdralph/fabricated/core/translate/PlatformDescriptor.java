package io.github.cmdralph.fabricated.core.translate;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.cmdralph.fabricated.core.json.Json;

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
		public String loaderName() {
			return "Forge";
		}

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

		/**
		 * Forge reads no mod's pack without metadata ("Missing metadata in pack mod:...", then "Missing data pack", in the
		 * forge branch's server smoke on Forge 64 to 66), where NeoForge makes one: the formats are those Fabricated's own
		 * in-game mod on Forge declares, whose pack loads on Forge 61 to 66.
		 */
		@Override
		public byte[] packMetadata(ModsTomlWriter.Spec spec) {
			Map<String, Object> pack = new LinkedHashMap<>();
			pack.put("description", spec.displayName());
			pack.put("min_format", 82);
			pack.put("max_format", 2048);
			return (Json.write(Map.of("pack", pack), true) + "\n").getBytes(StandardCharsets.UTF_8);
		}
	};

	/** The loader's name as players know it, for Fabricated's report: {@code NeoForge} by default. */
	default String loaderName() {
		return "NeoForge";
	}

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

	/**
	 * The {@code pack.mcmeta} a mod whose jar has none is given, as Fabric API's resource loader gives every mod's
	 * resources one on Fabric; null when the platform's loader makes its own (NeoForge).
	 */
	default byte[] packMetadata(ModsTomlWriter.Spec spec) {
		return null;
	}
}
