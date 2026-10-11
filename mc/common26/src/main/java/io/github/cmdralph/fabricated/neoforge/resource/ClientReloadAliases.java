package io.github.cmdralph.fabricated.neoforge.resource;

import java.util.Map;

import net.minecraft.resources.Identifier;

/**
 * Fabric's vanilla client listener ids (26.x API) to NeoForge's (snake_case of the listener's class). Listeners that
 * don't exist on a given Minecraft version are absent from NeoForge's registry, and orderings against them are skipped.
 */
final class ClientReloadAliases {
	static final Map<Identifier, Identifier> ALIASES = Map.ofEntries(
			alias("atlas", "atlas_manager"),
			alias("block_entity_render_dispatcher", "block_entity_render_dispatcher"),
			alias("cloud_renderer", "cloud_renderer"),
			alias("dry_foliage_color", "dry_foliage_color_reload_listener"),
			alias("entity_render_dispatcher", "entity_render_dispatcher"),
			alias("equipment_assets", "equipment_asset_manager"),
			alias("foliage_color", "foliage_color_reload_listener"),
			alias("fonts", "font_manager"),
			alias("game_renderer", "game_renderer"),
			alias("grass_color", "grass_color_reload_listener"),
			alias("languages", "language_manager"),
			alias("models", "model_manager"),
			alias("particles", "particle_resources"),
			alias("shaders", "shader_manager"),
			alias("sounds", "sound_manager"),
			alias("splash_texts", "splash_manager"),
			alias("textures", "texture_manager"),
			alias("waypoint_style", "waypoint_style_manager"));

	private ClientReloadAliases() {
	}

	private static Map.Entry<Identifier, Identifier> alias(String fabric, String neoForge) {
		return Map.entry(ReloadListeners.vanilla(fabric), ReloadListeners.vanilla(neoForge));
	}
}
