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
	private static final Map<String, String> PRIVATE_FIELDS = Map.of(
			"net/minecraft/world/level/biome/Biome f_47437_", "getModifiedClimateSettings",
			"net/minecraft/world/level/biome/Biome f_47443_", "getModifiedSpecialEffects",
			"net/minecraft/world/level/levelgen/structure/Structure f_226555_", "getModifiedStructureSettings",
			"net/minecraft/world/effect/MobEffectInstance f_19502_", "m_19544_",
			"net/minecraft/world/level/block/LiquidBlock f_54689_", "getFluid",
			"net/minecraft/world/item/BucketItem f_40687_", "getFluid",
			"net/minecraft/world/level/block/StairBlock f_56858_", "getModelBlock",
			"net/minecraft/world/level/block/StairBlock f_56859_", "getModelState",
			"net/minecraft/world/level/block/FlowerPotBlock f_53525_", "m_53560_",
			"net/minecraft/world/item/ItemStack f_41589_", "m_41720_");

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

	/**
	 * The fields Forge 47's {@code field_to_method.js} coremod redirects to a getter, in SRG's names as Forge 47 runs the
	 * game (read in Forge 47.4's sources): its {@code redirectFieldToMethod} needs them private, as Forge 61 to 66's
	 * transformer does (PLAT-4).
	 */
	@Override
	public Map<String, String> privateFields() {
		return PRIVATE_FIELDS;
	}

	@Override
	public byte[] packMetadata(ModsTomlWriter.Spec spec) {
		Map<String, Object> pack = new LinkedHashMap<>();
		pack.put("description", spec.displayName());
		pack.put("pack_format", PACK_FORMAT);
		return (Json.write(Map.of("pack", pack), true) + "\n").getBytes(StandardCharsets.UTF_8);
	}
}
