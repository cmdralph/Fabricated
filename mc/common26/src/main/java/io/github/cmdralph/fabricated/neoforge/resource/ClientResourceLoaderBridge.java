package io.github.cmdralph.fabricated.neoforge.resource;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import net.fabricmc.fabric.impl.resource.ResourceLoaderImpl;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;

/** Client half of {@link ResourceLoaderBridge}: Fabric client resource listeners join NeoForge's client event. */
final class ClientResourceLoaderBridge {
	/**
	 * Fabric's vanilla client listener ids to NeoForge's (snake_case of the listener's class). Listeners that don't
	 * exist on this Minecraft version are simply absent from NeoForge's registry, and orderings against them are skipped.
	 */
	private static final Map<Identifier, Identifier> ALIASES = new HashMap<>();

	static {
		alias("atlas", "atlas_manager");
		alias("block_entity_render_dispatcher", "block_entity_render_dispatcher");
		alias("cloud_renderer", "cloud_renderer");
		alias("dry_foliage_color", "dry_foliage_color_reload_listener");
		alias("entity_render_dispatcher", "entity_render_dispatcher");
		alias("equipment_assets", "equipment_asset_manager");
		alias("foliage_color", "foliage_color_reload_listener");
		alias("fonts", "font_manager");
		alias("game_renderer", "game_renderer");
		alias("grass_color", "grass_color_reload_listener");
		alias("languages", "language_manager");
		alias("models", "model_manager");
		alias("particles", "particle_resources");
		alias("shaders", "shader_manager");
		alias("sounds", "sound_manager");
		alias("splash_texts", "splash_manager");
		alias("textures", "texture_manager");
		alias("waypoint_style", "waypoint_style_manager");
	}

	private ClientResourceLoaderBridge() {
	}

	private static void alias(String fabric, String neoForge) {
		ALIASES.put(ReloadListeners.vanilla(fabric), ReloadListeners.vanilla(neoForge));
	}

	static void register(IEventBus modBus, Consumer<String> problems) {
		modBus.addListener(EventPriority.LOWEST, AddClientReloadListenersEvent.class, event -> {
			ResourceLoaderImpl loader = ResourceLoaderImpl.get(PackType.CLIENT_RESOURCES);
			ReloadListeners.apply(event, null, loader.listeners(), loader.orderings(), ALIASES, problems);
		});
	}
}
