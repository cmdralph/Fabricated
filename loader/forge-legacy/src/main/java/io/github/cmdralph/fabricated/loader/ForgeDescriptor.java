package io.github.cmdralph.fabricated.loader;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.translate.ModsTomlWriter;
import io.github.cmdralph.fabricated.core.translate.PlatformDescriptor;

/**
 * Forge 47's descriptor: Forge's dialect ({@link PlatformDescriptor#FORGE}), with a {@code pack.mcmeta} in Minecraft
 * 1.20.1's format. Forge 47 builds each mod's pack with vanilla's {@code Pack.readMetaAndCreate} (its
 * {@code ServerLifecycleHooks}), which gives no pack for metadata without a {@code pack_format}, the field 1.20.1 requires
 * where Forge 61 to 66's versions read {@code min_format} and {@code max_format}.
 */
final class ForgeDescriptor implements PlatformDescriptor {
	/** Minecraft 1.20.1's resource and data pack format. */
	static final int PACK_FORMAT = 15;
	static final PlatformDescriptor INSTANCE = new ForgeDescriptor();

	private ForgeDescriptor() {
	}

	@Override
	public String loaderName() {
		return FORGE.loaderName();
	}

	@Override
	public String path() {
		return FORGE.path();
	}

	@Override
	public String write(ModsTomlWriter.Spec spec) {
		return FORGE.write(spec);
	}

	@Override
	public Map<String, String> manifestAttributes(ModsTomlWriter.Spec spec) {
		return FORGE.manifestAttributes(spec);
	}

	@Override
	public boolean enumExtensions() {
		return FORGE.enumExtensions();
	}

	@Override
	public byte[] packMetadata(ModsTomlWriter.Spec spec) {
		Map<String, Object> pack = new LinkedHashMap<>();
		pack.put("description", spec.displayName());
		pack.put("pack_format", PACK_FORMAT);
		return (Json.write(Map.of("pack", pack), true) + "\n").getBytes(StandardCharsets.UTF_8);
	}
}
