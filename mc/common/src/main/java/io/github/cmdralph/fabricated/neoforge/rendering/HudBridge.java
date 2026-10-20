package io.github.cmdralph.fabricated.neoforge.rendering;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
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
 * happens when it is not installed. The GUI drawing type and the drawing methods' names differ between Minecraft
 * versions ({@code GuiGraphics.render} on 1.21.11, {@code GuiGraphicsExtractor.extractRenderState} on 26.x), so they
 * are taken from NeoForge's {@link GuiLayer} and upstream's classes.
 */
public final class HudBridge {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final String REGISTRY = "net.fabricmc.fabric.impl.client.rendering.hud.HudElementRegistryImpl";
	private static final String ELEMENT = "net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement";

	private static final Identifier SPECTATOR_MENU = Identifier.withDefaultNamespace("spectator_menu");
	private static final Identifier HOTBAR = Identifier.withDefaultNamespace("hotbar");
	/** NeoForge's vanilla layer and the ids of Fabric's {@code VanillaHudElements} it draws (except the hotbar, see below). */
	private static final List<Map.Entry<Identifier, Identifier>> LAYERS = List.of(
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
			// The element is "status_effects" on 1.21.11 and "mob_effects" on 26.x; only the one that exists wraps.
			Map.entry(VanillaGuiLayers.EFFECTS, Identifier.withDefaultNamespace("status_effects")),
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
		for (Map.Entry<Identifier, Identifier> layer : LAYERS) {
			event.wrapLayer(layer.getKey(), vanilla -> hud.wrap(layer.getValue(), vanilla));
		}
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

	/** The only abstract method of a functional interface. */
	private static Method functionalMethod(Class<?> type) throws NoSuchMethodException {
		List<Method> methods = Arrays.stream(type.getMethods()).filter(m -> Modifier.isAbstract(m.getModifiers())).toList();
		if (methods.size() != 1) throw new NoSuchMethodException(type.getName() + " has " + methods.size() + " abstract methods");
		return methods.getFirst();
	}

	/**
	 * Upstream's {@code HudElementRegistryImpl}, reached through method handles, and a factory for {@code HudElement}s
	 * that draw a NeoForge layer (a generated class, like a lambda: no reflection per frame).
	 */
	private record FabricHud(MethodHandle getRoot, MethodHandle draw, MethodHandle element) {
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
				Method layerDraw = functionalMethod(GuiLayer.class);
				Class<?> graphics = layerDraw.getParameterTypes()[0];
				MethodType drawType = MethodType.methodType(void.class, graphics, DeltaTracker.class);
				MethodHandles.Lookup lookup = MethodHandles.publicLookup();
				MethodHandle getRoot = lookup.findStatic(registry, "getRoot", MethodType.methodType(root, Identifier.class))
						.asType(MethodType.methodType(Object.class, Identifier.class));
				// RootLayer's method drawing its element list around the vanilla element, by its parameters.
				Method rootDraw = Arrays.stream(root.getMethods())
						.filter(m -> m.getReturnType() == void.class
								&& Arrays.equals(m.getParameterTypes(), new Class<?>[] {graphics, DeltaTracker.class, element}))
						.findFirst().orElseThrow(() -> new NoSuchMethodException(root.getName() + " has no drawing method"));
				MethodHandle draw = lookup.unreflect(rootDraw)
						.asType(MethodType.methodType(void.class, Object.class, Object.class, DeltaTracker.class, Object.class));
				MethodHandle factory = LambdaMetafactory.metafactory(MethodHandles.lookup(), functionalMethod(element).getName(),
								MethodType.methodType(element, GuiLayer.class), drawType,
								lookup.findVirtual(GuiLayer.class, layerDraw.getName(), drawType), drawType)
						.getTarget().asType(MethodType.methodType(Object.class, GuiLayer.class));
				return new FabricHud(getRoot, draw, factory);
			} catch (ReflectiveOperationException | java.lang.invoke.LambdaConversionException | LinkageError e) {
				LOGGER.error("fabric-rendering-v1's HUD registry has an unexpected shape; Fabric HUD elements will not be drawn", e);
				return null;
			}
		}

		/** {@code vanilla}, drawn through the Fabric element list of {@code element}, or as is when Fabric has no such element. */
		GuiLayer wrap(Identifier element, GuiLayer vanilla) {
			Object root;
			Object vanillaElement;
			try {
				root = (Object) getRoot.invokeExact(element);
				if (root == null) return vanilla;
				vanillaElement = (Object) this.element.invokeExact(vanilla);
			} catch (RuntimeException | Error e) {
				throw e;
			} catch (Throwable t) {
				throw new IllegalStateException("Could not set up Fabric HUD element " + element, t);
			}
			return (graphics, deltaTracker) -> {
				try {
					draw.invokeExact(root, (Object) graphics, deltaTracker, vanillaElement);
				} catch (RuntimeException | Error e) {
					throw e;
				} catch (Throwable t) {
					throw new IllegalStateException(t);
				}
			};
		}
	}
}
