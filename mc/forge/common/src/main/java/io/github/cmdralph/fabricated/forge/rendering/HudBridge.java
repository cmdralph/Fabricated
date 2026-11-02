package io.github.cmdralph.fabricated.forge.rendering;

import java.lang.invoke.LambdaConversionException;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.GameType;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayer;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.eventbus.api.listener.Priority;

/**
 * Runs {@code fabric-rendering-v1}'s HUD elements ({@code HudElementRegistry}) on Forge 61 to 66 (HUD-2). Forge draws
 * the HUD as layers of its own ({@link ForgeLayeredDraw}): vanilla's HUD method ({@code Gui.render}, on 26.2 and 26.3
 * {@code Hud.extractRenderState}) hands the drawing to them and returns before vanilla's calls, which upstream's HUD
 * mixin wraps, and Forge's hotbar layers make one by one the calls vanilla's hotbar method made, so upstream's wrappers
 * there are passed by too. Its mixin still applies, to code that no longer runs: an element attached before or after a
 * vanilla one was never drawn, and a vanilla one replaced or removed was drawn as is. Each of those Forge layers is
 * wrapped here with the Fabric element list of the same vanilla element, where vanilla made the call: what mods attached
 * before or after it, replaced or removed. The health bars (Forge's layer calls vanilla's method, in which upstream's
 * wrappers run) and the subtitles (upstream's subtitle mixin applies) keep upstream's own hooks.
 *
 * <p>Fabric API's deprecated {@code HudRenderCallback} (1.21.11's module has it) is fired after Forge's last layer, as
 * upstream fires it at the end of vanilla's method, which Forge's layers return before (upstream's own hook still fires
 * it when the method does reach its end: while a world is loading, when Forge draws no layer).
 *
 * <p>The module is upstream's code loaded like any Fabric mod, so its classes are looked up by name, and nothing happens
 * when it is not installed. The GUI drawing type and the drawing methods' names differ between Minecraft versions
 * ({@code GuiGraphics} and {@code render} on 1.21.11, {@code GuiGraphicsExtractor} and {@code extract} or
 * {@code extractRenderState} on 26.x), so they are taken from Forge's {@link ForgeLayer} and upstream's classes.
 */
public final class HudBridge {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final String MODULE = "fabric-rendering-v1";
	private static final String REGISTRY = "net.fabricmc.fabric.impl.client.rendering.hud.HudElementRegistryImpl";
	private static final String ELEMENT = "net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement";
	private static final String VANILLA_ELEMENTS = "net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements";
	private static final String CALLBACK = "net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback";
	/** The layer firing {@code HudRenderCallback}, last of Forge's root stack. */
	private static final Identifier CALLBACK_LAYER = Identifier.fromNamespaceAndPath("fabricated", "hud_render_callback");

	/**
	 * Vanilla's conditions around a call in its hotbar method, which Forge's layer checks inside itself: upstream's
	 * element list only runs where vanilla made the call, so the wrapper checks them first.
	 */
	private static final BooleanSupplier SPECTATING = () -> Minecraft.getInstance().gameMode.getPlayerMode() == GameType.SPECTATOR;
	private static final BooleanSupplier NOT_SPECTATING = () -> !SPECTATING.getAsBoolean();
	private static final BooleanSupplier EXPERIENCE_SHOWN = () -> {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.gameMode.hasExperience() && minecraft.player.experienceLevel > 0;
	};
	private static final BooleanSupplier SPECTATOR_TOOLTIP_SHOWN = () -> SPECTATING.getAsBoolean() && Minecraft.getInstance().player.isSpectator();

	/** Forge's layers that make a call upstream wraps, each with the vanilla element it draws (Fabric's id). */
	private static final List<Wrap> LAYERS = List.of(
			new Wrap(ForgeLayeredDraw.PRE_SLEEP_STACK, ForgeLayeredDraw.CAMERA_OVERLAY, "misc_overlays", null),
			new Wrap(ForgeLayeredDraw.PRE_SLEEP_STACK, ForgeLayeredDraw.CROSSHAIR, "crosshair", null),
			new Wrap(ForgeLayeredDraw.HOTBAR_AND_DECOS, ForgeLayeredDraw.SPECTATOR_HOTBAR, "spectator_menu", SPECTATING),
			new Wrap(ForgeLayeredDraw.HOTBAR_AND_DECOS, ForgeLayeredDraw.ITEM_HOTBAR, "hotbar", NOT_SPECTATING),
			new Wrap(ForgeLayeredDraw.HOTBAR_AND_DECOS, ForgeLayeredDraw.VEHICLE_HEALTH, "mount_health", null),
			// Forge's background layer also switches the contextual bar (experience, locator, jump) before drawing it.
			new Wrap(ForgeLayeredDraw.HOTBAR_AND_DECOS, ForgeLayeredDraw.BACKGROUND, "info_bar", null),
			new Wrap(ForgeLayeredDraw.HOTBAR_AND_DECOS, ForgeLayeredDraw.EXPERIENCE_LEVEL, "experience_level", EXPERIENCE_SHOWN),
			new Wrap(ForgeLayeredDraw.HOTBAR_AND_DECOS, ForgeLayeredDraw.SELECTED_ITEM_NAME, "held_item_tooltip", NOT_SPECTATING),
			new Wrap(ForgeLayeredDraw.HOTBAR_AND_DECOS, ForgeLayeredDraw.SPECTATOR_ACTION, "spectator_tooltip", SPECTATOR_TOOLTIP_SHOWN),
			// The element is "status_effects" on 1.21.11 and "mob_effects" on 26.x; only the one that exists wraps.
			new Wrap(ForgeLayeredDraw.PRE_SLEEP_STACK, ForgeLayeredDraw.POTION_EFFECTS, "status_effects", null),
			new Wrap(ForgeLayeredDraw.PRE_SLEEP_STACK, ForgeLayeredDraw.POTION_EFFECTS, "mob_effects", null),
			new Wrap(ForgeLayeredDraw.PRE_SLEEP_STACK, ForgeLayeredDraw.BOSS_OVERLAY, "boss_bar", null),
			new Wrap(ForgeLayeredDraw.VANILLA_ROOT, ForgeLayeredDraw.SLEEP_OVERLAY, "sleep", null),
			new Wrap(ForgeLayeredDraw.POST_SLEEP_STACK, ForgeLayeredDraw.DEMO_OVERLAY, "demo_timer", null),
			new Wrap(ForgeLayeredDraw.POST_SLEEP_STACK, ForgeLayeredDraw.SCOREBOARD, "scoreboard", null),
			new Wrap(ForgeLayeredDraw.POST_SLEEP_STACK, ForgeLayeredDraw.HOTBAR_MESSAGE, "overlay_message", null),
			new Wrap(ForgeLayeredDraw.POST_SLEEP_STACK, ForgeLayeredDraw.TITLE_OVERLAY, "title_and_subtitle", null),
			new Wrap(ForgeLayeredDraw.POST_SLEEP_STACK, ForgeLayeredDraw.CHAT_OVERLAY, "chat", null),
			new Wrap(ForgeLayeredDraw.POST_SLEEP_STACK, ForgeLayeredDraw.TAB_LIST, "player_list", null));

	private HudBridge() {
	}

	/** Called once on the client, before the HUD is made; nothing is registered without Fabric API's rendering module. */
	public static void register() {
		if (!FabricLoader.getInstance().isModLoaded(MODULE)) return;
		// Last, so the Fabric elements surround whatever Forge mods made of the vanilla layers.
		AddGuiOverlayLayersEvent.BUS.addListener(Priority.LOWEST, HudBridge::wrapLayers);
	}

	private static void wrapLayers(AddGuiOverlayLayersEvent event) {
		FabricHud hud = FabricHud.find();
		if (hud == null) return;
		ForgeLayeredDraw root = event.getLayeredDraw();
		int wrapped = 0;
		for (Wrap wrap : LAYERS) {
			ForgeLayeredDraw stack = root.locateStack(wrap.stack()).orElse(null);
			ForgeLayer vanilla = stack == null ? null : stack.getLayer(wrap.layer());
			if (vanilla == null) continue;
			try {
				ForgeLayer layer = hud.wrap(Identifier.withDefaultNamespace(wrap.element()), vanilla, wrap.condition());
				if (layer == vanilla) continue;
				root.replace(wrap.stack(), wrap.layer(), layer);
				wrapped++;
			} catch (RuntimeException e) {
				// The HUD is made in Minecraft's constructor: the layer is left as Forge made it rather than the game stopped.
				LOGGER.error("Fabric HUD elements around {} will not be drawn", wrap.element(), e);
			}
		}
		ForgeLayer callback = HudCallback.find();
		if (callback != null) root.add(CALLBACK_LAYER, callback);
		LOGGER.debug("Fabric HUD elements run from {} of Forge's HUD layers{}", wrapped, callback != null ? ", HudRenderCallback after them" : "");
	}

	/** The only abstract method of a functional interface. */
	private static Method functionalMethod(Class<?> type) throws NoSuchMethodException {
		List<Method> methods = Arrays.stream(type.getMethods()).filter(m -> Modifier.isAbstract(m.getModifiers())).toList();
		if (methods.size() != 1) throw new NoSuchMethodException(type.getName() + " has " + methods.size() + " abstract methods");
		return methods.get(0);
	}

	/** The drawing type of the HUD's layers: {@code GuiGraphics} on 1.21.11, {@code GuiGraphicsExtractor} on 26.x. */
	private static Class<?> graphicsType() throws NoSuchMethodException {
		return functionalMethod(ForgeLayer.class).getParameterTypes()[0];
	}

	private static RuntimeException rethrow(Throwable t) {
		if (t instanceof RuntimeException e) return e;
		if (t instanceof Error e) throw e;
		return new IllegalStateException(t);
	}

	/**
	 * A Forge layer that draws a vanilla HUD element.
	 *
	 * @param stack     the Forge layer stack it is in
	 * @param layer     its name in Forge's stack
	 * @param element   the path of Fabric's {@code VanillaHudElements} id for it
	 * @param condition vanilla's condition around the call, which Forge's layer checks inside itself (null: none)
	 */
	private record Wrap(Identifier stack, Identifier layer, String element, @Nullable BooleanSupplier condition) {
	}

	/**
	 * Upstream's {@code HudElementRegistryImpl}, reached through method handles, and a factory for {@code HudElement}s
	 * that draw a Forge layer (a generated class, like a lambda: no reflection per frame). Upstream keeps its vanilla
	 * elements' roots by identity, keyed by the {@code VanillaHudElements} constants, so an element is looked up by the
	 * constant equal to its id ({@code vanillaIds}), as on NeoForge (HUD-1).
	 */
	private record FabricHud(MethodHandle getRoot, MethodHandle draw, MethodHandle element, MethodHandle layerDraw,
			Map<Identifier, Identifier> vanillaIds) {
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
				Method layerDraw = functionalMethod(ForgeLayer.class);
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
				MethodHandle forgeDraw = lookup.findVirtual(ForgeLayer.class, layerDraw.getName(), drawType);
				MethodHandle factory = LambdaMetafactory.metafactory(MethodHandles.lookup(), functionalMethod(element).getName(),
								MethodType.methodType(element, ForgeLayer.class), drawType, forgeDraw, drawType)
						.getTarget().asType(MethodType.methodType(Object.class, ForgeLayer.class));
				Map<Identifier, Identifier> vanillaIds = new HashMap<>();
				for (Field field : Class.forName(VANILLA_ELEMENTS, true, loader).getFields()) {
					if (Modifier.isStatic(field.getModifiers()) && field.get(null) instanceof Identifier id) vanillaIds.put(id, id);
				}
				return new FabricHud(getRoot, draw, factory,
						forgeDraw.asType(MethodType.methodType(void.class, ForgeLayer.class, Object.class, DeltaTracker.class)), vanillaIds);
			} catch (ReflectiveOperationException | LambdaConversionException | LinkageError e) {
				LOGGER.error("fabric-rendering-v1's HUD registry has an unexpected shape; Fabric HUD elements attached to vanilla ones will not be drawn", e);
				return null;
			}
		}

		/**
		 * {@code vanilla}, drawn through the Fabric element list of {@code element} when {@code condition} holds (as is
		 * otherwise, or when Fabric has no such element).
		 */
		ForgeLayer wrap(Identifier element, ForgeLayer vanilla, @Nullable BooleanSupplier condition) {
			Identifier constant = vanillaIds.get(element);
			if (constant == null) return vanilla;
			Object root;
			Object vanillaElement;
			try {
				root = (Object) getRoot.invokeExact(constant);
				if (root == null) return vanilla;
				vanillaElement = (Object) this.element.invokeExact(vanilla);
			} catch (Throwable t) {
				throw rethrow(t);
			}
			return (graphics, deltaTracker) -> {
				try {
					// Where vanilla wouldn't have made the call, Forge's layer is drawn as is (it checks the condition too,
					// unless a Forge mod made it draw something else).
					if (condition != null && !condition.getAsBoolean()) layerDraw.invokeExact(vanilla, (Object) graphics, deltaTracker);
					else draw.invokeExact(root, (Object) graphics, deltaTracker, vanillaElement);
				} catch (Throwable t) {
					throw rethrow(t);
				}
			};
		}
	}

	/** Fabric API's deprecated {@code HudRenderCallback}, fired from a Forge layer, where the module has it. */
	private static final class HudCallback {
		private HudCallback() {
		}

		static @Nullable ForgeLayer find() {
			Class<?> callback;
			try {
				callback = Class.forName(CALLBACK, false, HudBridge.class.getClassLoader());
			} catch (ClassNotFoundException e) {
				return null; // not in this version's module (26.x)
			}
			Event<?> event;
			MethodHandle onHudRender;
			try {
				event = (Event<?>) callback.getField("EVENT").get(null);
				onHudRender = MethodHandles.publicLookup()
						.findVirtual(callback, "onHudRender", MethodType.methodType(void.class, graphicsType(), DeltaTracker.class))
						.asType(MethodType.methodType(void.class, Object.class, Object.class, DeltaTracker.class));
			} catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
				LOGGER.error("fabric-rendering-v1's HudRenderCallback has an unexpected shape; it will not fire", e);
				return null;
			}
			return (graphics, deltaTracker) -> {
				try {
					onHudRender.invokeExact((Object) event.invoker(), (Object) graphics, deltaTracker);
				} catch (Throwable t) {
					throw rethrow(t);
				}
			};
		}
	}
}
