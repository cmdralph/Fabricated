package io.github.cmdralph.fabricated.neoforge.rendering;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Runs {@code fabric-rendering-v1}'s HUD elements ({@code HudElementRegistry}) on NeoForge. Upstream wraps each
 * vanilla HUD call with a mixin; NeoForge draws the HUD as layers instead, so each vanilla layer is wrapped with
 * the Fabric element list of the same vanilla element: what mods attached before or after it, replaced or removed.
 *
 * <p>The module is upstream's code loaded like any Fabric mod, so its classes are looked up by name, and nothing
 * happens when it is not installed.
 */
public final class HudBridge {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final String REGISTRY = "net.fabricmc.fabric.impl.client.rendering.hud.HudElementRegistryImpl";
	private static final String ELEMENT = "net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement";

	private static final Identifier SPECTATOR_MENU = Identifier.withDefaultNamespace("spectator_menu");
	private static final Identifier HOTBAR = Identifier.withDefaultNamespace("hotbar");
	/** NeoForge's vanilla layer for each of Fabric's {@code VanillaHudElements} (except the hotbar, see below). */
	private static final Map<Identifier, Identifier> LAYERS = Map.ofEntries(
			Map.entry(VanillaGuiLayers.CAMERA_OVERLAYS, Identifier.withDefaultNamespace("misc_overlays")),
			Map.entry(VanillaGuiLayers.CROSSHAIR, Identifier.withDefaultNamespace("crosshair")),
			Map.entry(VanillaGuiLayers.ARMOR_LEVEL, Identifier.withDefaultNamespace("armor_bar")),
			Map.entry(VanillaGuiLayers.PLAYER_HEALTH, Identifier.withDefaultNamespace("health_bar")),
			Map.entry(VanillaGuiLayers.FOOD_LEVEL, Identifier.withDefaultNamespace("food_bar")),
			Map.entry(VanillaGuiLayers.AIR_LEVEL, Identifier.withDefaultNamespace("air_bar")),
			Map.entry(VanillaGuiLayers.VEHICLE_HEALTH, Identifier.withDefaultNamespace("mount_health")),
			Map.entry(VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND, Identifier.withDefaultNamespace("info_bar")),
			Map.entry(VanillaGuiLayers.EXPERIENCE_LEVEL, Identifier.withDefaultNamespace("experience_level")),
			Map.entry(VanillaGuiLayers.SELECTED_ITEM_NAME, Identifier.withDefaultNamespace("held_item_tooltip")),
			Map.entry(VanillaGuiLayers.SPECTATOR_TOOLTIP, Identifier.withDefaultNamespace("spectator_tooltip")),
			Map.entry(VanillaGuiLayers.EFFECTS, Identifier.withDefaultNamespace("mob_effects")),
			Map.entry(VanillaGuiLayers.BOSS_OVERLAY, Identifier.withDefaultNamespace("boss_bar")),
			Map.entry(VanillaGuiLayers.SLEEP_OVERLAY, Identifier.withDefaultNamespace("sleep")),
			Map.entry(VanillaGuiLayers.DEMO_OVERLAY, Identifier.withDefaultNamespace("demo_timer")),
			Map.entry(VanillaGuiLayers.SCOREBOARD_SIDEBAR, Identifier.withDefaultNamespace("scoreboard")),
			Map.entry(VanillaGuiLayers.OVERLAY_MESSAGE, Identifier.withDefaultNamespace("overlay_message")),
			Map.entry(VanillaGuiLayers.TITLE, Identifier.withDefaultNamespace("title_and_subtitle")),
			Map.entry(VanillaGuiLayers.CHAT, Identifier.withDefaultNamespace("chat")),
			Map.entry(VanillaGuiLayers.TAB_LIST, Identifier.withDefaultNamespace("player_list")));
	// Subtitles are not here: upstream's subtitle mixin still applies on NeoForge.

	private HudBridge() {
	}

	public static void register(IEventBus modBus) {
		// Last, so the Fabric elements surround whatever NeoForge mods made of the vanilla layers.
		modBus.addListener(EventPriority.LOWEST, RegisterGuiLayersEvent.class, HudBridge::wrapLayers);
	}

	private static void wrapLayers(RegisterGuiLayersEvent event) {
		FabricHud hud = FabricHud.find();
		if (hud == null) return;
		LAYERS.forEach((layer, element) -> event.wrapLayer(layer, vanilla -> hud.wrap(element, vanilla)));
		// NeoForge's hotbar layer draws the spectator menu in spectator mode and the item hotbar otherwise; Fabric has
		// an element for each.
		event.wrapLayer(VanillaGuiLayers.HOTBAR, vanilla -> {
			GuiLayer spectator = hud.wrap(SPECTATOR_MENU, vanilla);
			GuiLayer hotbar = hud.wrap(HOTBAR, vanilla);
			return (graphics, deltaTracker) -> {
				Minecraft minecraft = Minecraft.getInstance();
				boolean spectating = minecraft.gameMode != null && minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR;
				(spectating ? spectator : hotbar).render(graphics, deltaTracker);
			};
		});
		LOGGER.debug("Fabric HUD elements run from NeoForge's vanilla GUI layers");
	}

	/**
	 * Upstream's {@code HudElementRegistryImpl}, reached through method handles, and a factory for {@code HudElement}s
	 * that draw a NeoForge layer (a generated class, like a lambda: no reflection per frame).
	 */
	private record FabricHud(MethodHandle getRoot, MethodHandle extract, MethodHandle element) {
		static @Nullable FabricHud find() {
			ClassLoader loader = HudBridge.class.getClassLoader();
			Class<?> registry;
			try {
				registry = Class.forName(REGISTRY, true, loader);
			} catch (ClassNotFoundException e) {
				return null; // fabric-rendering-v1 is not installed
			}
			try {
				Class<?> element = Class.forName(ELEMENT, false, loader);
				Class<?> root = Class.forName(REGISTRY + "$RootLayer", false, loader);
				MethodHandles.Lookup lookup = MethodHandles.publicLookup();
				MethodHandle getRoot = lookup.findStatic(registry, "getRoot", MethodType.methodType(root, Identifier.class))
						.asType(MethodType.methodType(Object.class, Identifier.class));
				MethodHandle extract = lookup.findVirtual(root, "extractRenderState",
								MethodType.methodType(void.class, GuiGraphicsExtractor.class, DeltaTracker.class, element))
						.asType(MethodType.methodType(void.class, Object.class, GuiGraphicsExtractor.class, DeltaTracker.class, Object.class));
				MethodType draw = MethodType.methodType(void.class, GuiGraphicsExtractor.class, DeltaTracker.class);
				MethodHandle factory = LambdaMetafactory.metafactory(MethodHandles.lookup(), "extractRenderState",
								MethodType.methodType(element, GuiLayer.class), draw, lookup.findVirtual(GuiLayer.class, "render", draw), draw)
						.getTarget().asType(MethodType.methodType(Object.class, GuiLayer.class));
				return new FabricHud(getRoot, extract, factory);
			} catch (ReflectiveOperationException | java.lang.invoke.LambdaConversionException | LinkageError e) {
				LOGGER.error("fabric-rendering-v1's HUD registry has an unexpected shape; Fabric HUD elements will not be drawn", e);
				return null;
			}
		}

		/** {@code vanilla}, drawn through the Fabric element list of {@code element}. */
		GuiLayer wrap(Identifier element, GuiLayer vanilla) {
			Object root;
			Object vanillaElement;
			try {
				root = (Object) getRoot.invokeExact(element);
				vanillaElement = (Object) this.element.invokeExact(vanilla);
			} catch (Throwable t) {
				throw new IllegalStateException("Could not set up Fabric HUD element " + element, t);
			}
			if (root == null) return vanilla;
			return (graphics, deltaTracker) -> {
				try {
					extract.invokeExact(root, graphics, deltaTracker, vanillaElement);
				} catch (RuntimeException | Error e) {
					throw e;
				} catch (Throwable t) {
					throw new IllegalStateException(t);
				}
			};
		}
	}
}
