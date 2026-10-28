package io.github.cmdralph.fabricated.core.translate;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.cmdralph.fabricated.core.accesswidener.AccessTransformerConverter;
import io.github.cmdralph.fabricated.core.json.Json;

/**
 * How the platform's loader learns of a translated Fabric mod (docs/PORTING.md §6 item 5): where it reads a mod's
 * descriptor and in which dialect, what the overlay's manifest adds, whether it adds enum constants from a mod's
 * enum extension file, and which fields its coremods keep private. NeoForge's is {@link #NEOFORGE} and Forge's {@link #FORGE}; a branch for another loader hands the
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

		@Override
		public Map<String, String> privateFields() {
			return AccessTransformerConverter.FORGE_PRIVATE_FIELDS;
		}

		/**
		 * Mod Menu's only: Forge's in-game mod provides neither its API nor Forge Config API Port's, and Forge Config API
		 * Port's Fabric jar loads as other mods do, with its own copy of NeoForge's config classes, which Forge lacks
		 * (REPL-1). Mod Menu's stays out as before: Forge lists mods and their config screens itself, and Mod Menu's hooks
		 * into the title screen, which Forge patches, stopped the Forge 66 client once it loaded.
		 */
		@Override
		public Map<String, String> replacedMods() {
			return Map.of("modmenu", "Forge lists mods and their config screens itself; Mod Menu's Fabric build, which hooks the screens Forge"
					+ " patches, isn't loaded on Forge, and its API isn't provided");
		}

		/**
		 * None beyond the packages the loader reads from Forge's layers and mod files: Forge has none of NeoForge's,
		 * which Forge Config API Port's Fabric jar copies and needs (PLAT-5).
		 */
		@Override
		public List<String> providedPackages() {
			return List.of();
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

	/**
	 * Fabric mods Fabricated leaves out on this platform, by id, each with the report's line: their jar isn't loaded, and
	 * mods requiring them resolve at its version. NeoForge's by default, whose in-game mod provides their API (Forge
	 * Config API Port's Fabric API on NeoForge's config system, which the port copies; Mod Menu's API in NeoForge's mod
	 * list).
	 */
	default Map<String, String> replacedMods() {
		return Map.of(
				"forgeconfigapiport", "Fabricated provides Forge Config API Port's Fabric API on NeoForge's own config system, which the port copies",
				"modmenu", "Fabricated shows the config screens Fabric mods offer through Mod Menu's API in NeoForge's mod list");
	}

	/**
	 * Package prefixes (internal form) the platform's loader owns besides the libraries every platform provides
	 * ({@code PlatformLibraries.PROVIDED_PACKAGES}) and the packages the loader reads from the platform's layers: a Fabric
	 * mod's copy of a package there is left out for the platform's. NeoForge's by default, its whole namespace.
	 */
	default List<String> providedPackages() {
		return List.of("net/neoforged/");
	}

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
	 * The fields the platform's loader reads through a getter its patches add and requires to stay private, as
	 * {@code owner field} in the game's runtime names, to that getter: an access widener can't open them (PLAT-4).
	 * NeoForge's by default.
	 */
	default Map<String, String> privateFields() {
		return AccessTransformerConverter.NEOFORGE_PRIVATE_FIELDS;
	}

	/**
	 * The {@code pack.mcmeta} a mod whose jar has none is given, as Fabric API's resource loader gives every mod's
	 * resources one on Fabric; null when the platform's loader makes its own (NeoForge).
	 */
	default byte[] packMetadata(ModsTomlWriter.Spec spec) {
		return null;
	}
}
