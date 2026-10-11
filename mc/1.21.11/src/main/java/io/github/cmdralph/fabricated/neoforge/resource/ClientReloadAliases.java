package io.github.cmdralph.fabricated.neoforge.resource;

import java.util.Map;

import net.minecraft.resources.Identifier;

/**
 * Fabric's vanilla client listener ids (Minecraft 1.21.11 API) to NeoForge's (snake_case of the listener's class).
 * Orderings against listeners NeoForge doesn't have are skipped.
 */
final class ClientReloadAliases {
	static final Map<Identifier, Identifier> ALIASES = Map.ofEntries(
			alias("atlas", "atlas_manager"),
			alias("block_entity_renderers", "block_entity_render_dispatcher"),
			alias("block_render_manager", "block_render_dispatcher"),
			alias("cloud_cells", "cloud_renderer"),
			alias("dry_foliage_colormap", "dry_foliage_color_reload_listener"),
			alias("entity_renderers", "entity_render_dispatcher"),
			alias("equipment_models", "equipment_asset_manager"),
			alias("foliage_colormap", "foliage_color_reload_listener"),
			alias("fonts", "font_manager"),
			alias("grass_colormap", "grass_color_reload_listener"),
			alias("languages", "language_manager"),
			alias("models", "model_manager"),
			alias("particles", "particle_resources"),
			alias("shaders", "shader_manager"),
			alias("sounds", "sound_manager"),
			alias("splash_texts", "splash_manager"),
			alias("textures", "texture_manager"),
			alias("waypoint_style_assets", "waypoint_style_manager"));

	private ClientReloadAliases() {
	}

	private static Map.Entry<Identifier, Identifier> alias(String fabric, String neoForge) {
		return Map.entry(ReloadListeners.vanilla(fabric), ReloadListeners.vanilla(neoForge));
	}
}
