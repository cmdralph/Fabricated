package io.github.cmdralph.fabricated.neoforge.rendering;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.cmdralph.fabricated.neoforge.mixin.client.GuiLayerManagerLayersAccessor;
import io.github.cmdralph.fabricated.neoforge.mixin.client.LayeredDrawLayersAccessor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.gui.GuiLayerManager;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Vanilla's two HUD layer stacks as Fabric mods' hooks in {@code Gui}'s constructor see them on NeoForge 21.1 (HUD-7):
 * vanilla's {@code LayeredDraw}s, holding a placeholder for each of vanilla's layers at its index, for the hooks to add
 * their layers to (Apoli adds its resource bars at index 3 of the first, after the hotbar).
 *
 * <p>NeoForge builds each stack as a layer manager of its own, where some of vanilla's layers are several of NeoForge's:
 * vanilla's hotbar layer is NeoForge's ten from {@code HOTBAR} to {@code SPECTATOR_TOOLTIP}, and NeoForge's second stack
 * ends with the saving indicator, which vanilla draws after its HUD. {@link #addToNeoForge} puts each layer a hook added
 * into NeoForge's manager, in the stack's order, before NeoForge's first layer of the vanilla layer after it, or where
 * vanilla's stack ends; NeoForge then copies its stacks into its root under each stack's condition, as vanilla draws its
 * stacks. NeoForge's own layers stay as they are, in their order, so its mods' layers order against them as before: a
 * vanilla layer a hook takes out of its list, moves or replaces is still NeoForge's.
 */
public final class VanillaHudStacks {
	/** The NeoForge layer each layer of vanilla's first stack starts at. */
	private static final List<ResourceLocation> FIRST = List.of(VanillaGuiLayers.CAMERA_OVERLAYS, VanillaGuiLayers.CROSSHAIR, VanillaGuiLayers.HOTBAR,
			VanillaGuiLayers.EXPERIENCE_LEVEL, VanillaGuiLayers.EFFECTS, VanillaGuiLayers.BOSS_OVERLAY);
	/** The same for vanilla's second stack. */
	private static final List<ResourceLocation> SECOND = List.of(VanillaGuiLayers.DEMO_OVERLAY, VanillaGuiLayers.DEBUG_OVERLAY,
			VanillaGuiLayers.SCOREBOARD_SIDEBAR, VanillaGuiLayers.OVERLAY_MESSAGE, VanillaGuiLayers.TITLE, VanillaGuiLayers.CHAT, VanillaGuiLayers.TAB_LIST,
			VanillaGuiLayers.SUBTITLE_OVERLAY);
	/** How many layers hooks added, for each a name of its own in NeoForge's manager; on the render thread only. */
	private static int added;

	private final GuiLayerManager neoForgeFirst;
	private final GuiLayerManager neoForgeSecond;
	private final LayeredDraw first = vanilla(FIRST);
	private final LayeredDraw second = vanilla(SECOND);

	/** @param first NeoForge's manager of vanilla's first stack; {@code second}, of its second */
	public VanillaHudStacks(GuiLayerManager first, GuiLayerManager second) {
		this.neoForgeFirst = first;
		this.neoForgeSecond = second;
	}

	public LayeredDraw first() {
		return first;
	}

	public LayeredDraw second() {
		return second;
	}

	/** Puts the layers hooks added to vanilla's stacks into NeoForge's, before NeoForge copies those into its root. */
	public void addToNeoForge() {
		addTo(neoForgeFirst, first, null);
		addTo(neoForgeSecond, second, VanillaGuiLayers.SAVING_INDICATOR);
	}

	/** A placeholder for one of vanilla's layers, which NeoForge's layers from {@code start} on draw. */
	private record VanillaLayer(ResourceLocation start) implements LayeredDraw.Layer {
		@Override
		public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		}
	}

	private static LayeredDraw vanilla(List<ResourceLocation> starts) {
		LayeredDraw draw = new LayeredDraw();
		for (ResourceLocation start : starts) draw.add(new VanillaLayer(start));
		return draw;
	}

	/**
	 * Puts each layer of {@code vanilla} that isn't one of vanilla's into {@code neoForge}, before NeoForge's layer the
	 * vanilla layer after it starts at, or before {@code end} (NeoForge's end where null) when none follows.
	 */
	private static void addTo(GuiLayerManager neoForge, LayeredDraw vanilla, @Nullable ResourceLocation end) {
		List<GuiLayerManager.NamedLayer> layers = ((GuiLayerManagerLayersAccessor) neoForge).fabricated$layers();
		List<LayeredDraw.Layer> pending = new ArrayList<>();
		for (LayeredDraw.Layer layer : ((LayeredDrawLayersAccessor) vanilla).fabricated$layers()) {
			if (layer instanceof VanillaLayer next) {
				insert(layers, pending, next.start());
			} else {
				pending.add(layer);
			}
		}
		insert(layers, pending, end);
	}

	private static void insert(List<GuiLayerManager.NamedLayer> layers, List<LayeredDraw.Layer> pending, @Nullable ResourceLocation before) {
		if (pending.isEmpty()) return;
		int at = layers.size();
		for (int i = 0; i < layers.size(); i++) {
			if (layers.get(i).name().equals(before)) {
				at = i;
				break;
			}
		}
		for (LayeredDraw.Layer layer : pending) {
			layers.add(at++, new GuiLayerManager.NamedLayer(ResourceLocation.fromNamespaceAndPath("fabricated", "vanilla_stack_layer_" + added++), layer));
		}
		pending.clear();
	}
}
