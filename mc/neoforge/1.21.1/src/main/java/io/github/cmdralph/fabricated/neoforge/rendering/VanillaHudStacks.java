package io.github.cmdralph.fabricated.neoforge.rendering;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.loader.FabricatedState;
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
 * their layers to (Apoli adds its resource bars at index 3 of the first, after the hotbar), or take vanilla's out.
 *
 * <p>NeoForge builds each stack as a layer manager of its own, where some of vanilla's layers are several of NeoForge's:
 * vanilla's hotbar layer is NeoForge's ten from {@code HOTBAR} to {@code SPECTATOR_TOOLTIP}, and NeoForge's second stack
 * ends with the saving indicator, which vanilla draws after its HUD. A stack of NeoForge's is told by the layers it holds,
 * not by where the constructor holds it. {@link #placeInto} puts what the hooks did to vanilla's stack into NeoForge's,
 * before NeoForge copies it into its root under the stack's condition, as vanilla draws its stacks:
 * <ul>
 * <li>each layer a hook added, in the stack's order, as a layer of its own, before NeoForge's first layer of the vanilla
 * layer after it, or where vanilla's stack ends;</li>
 * <li>for each vanilla layer a hook took out of the stack, or replaced, NeoForge's layers of it drawing nothing, under
 * their names, as NeoForge's own {@code replaceLayer} keeps them: NeoForge's mods' layers order against them as before,
 * and a replacement is drawn where the vanilla layer was.</li>
 * </ul>
 * A vanilla layer a hook moves stays where NeoForge draws it. What a hook added to a stack NeoForge's constructor never
 * adds to its root, as Fabricated knows it, isn't drawn, and the report says so ({@link #finish}).
 */
public final class VanillaHudStacks {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	/** What NeoForge's layers of a vanilla layer a hook took out of its stack draw. */
	private static final LayeredDraw.Layer NOTHING = (graphics, deltaTracker) -> {
	};
	/** Vanilla's first stack: the NeoForge layer each of its layers starts at, and its end, NeoForge's stack's own. */
	private static final Shape FIRST = new Shape("first", List.of(VanillaGuiLayers.CAMERA_OVERLAYS, VanillaGuiLayers.CROSSHAIR,
			VanillaGuiLayers.HOTBAR, VanillaGuiLayers.EXPERIENCE_LEVEL, VanillaGuiLayers.EFFECTS, VanillaGuiLayers.BOSS_OVERLAY), null);
	/** Vanilla's second stack, which ends before the saving indicator NeoForge adds to it. */
	private static final Shape SECOND = new Shape("second", List.of(VanillaGuiLayers.DEMO_OVERLAY, VanillaGuiLayers.DEBUG_OVERLAY,
			VanillaGuiLayers.SCOREBOARD_SIDEBAR, VanillaGuiLayers.OVERLAY_MESSAGE, VanillaGuiLayers.TITLE, VanillaGuiLayers.CHAT,
			VanillaGuiLayers.TAB_LIST, VanillaGuiLayers.SUBTITLE_OVERLAY), VanillaGuiLayers.SAVING_INDICATOR);
	/** How many layers hooks added, for each a name of its own in NeoForge's manager; on the render thread only. */
	private static int added;

	private final LayeredDraw first = FIRST.vanilla();
	private final LayeredDraw second = SECOND.vanilla();
	private boolean firstPlaced;
	private boolean secondPlaced;

	public LayeredDraw first() {
		return first;
	}

	public LayeredDraw second() {
		return second;
	}

	/** Whether a stack NeoForge's constructor adds to its root is vanilla's first, which vanilla's read comes before. */
	public static boolean isFirst(GuiLayerManager stack) {
		return FIRST.isIn(layers(stack));
	}

	/** Puts what the hooks did to the vanilla stack {@code stack} is into it, before NeoForge adds it to its root. */
	public void placeInto(GuiLayerManager stack) {
		List<GuiLayerManager.NamedLayer> layers = layers(stack);
		if (!firstPlaced && FIRST.isIn(layers)) {
			firstPlaced = true;
			FIRST.place(first, layers);
		} else if (!secondPlaced && SECOND.isIn(layers)) {
			secondPlaced = true;
			SECOND.place(second, layers);
		}
	}

	/** Once the constructor has built the HUD: reports the layers hooks added to a stack never placed. */
	public void finish() {
		if (!firstPlaced) lost(FIRST, first);
		if (!secondPlaced) lost(SECOND, second);
	}

	/** A placeholder for one of vanilla's layers, which NeoForge's layers from {@code start} on draw. */
	private record VanillaLayer(ResourceLocation start) implements LayeredDraw.Layer {
		@Override
		public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		}
	}

	/** One of vanilla's stacks: the NeoForge layer each of its layers starts at, and the one it ends before (null: none). */
	private record Shape(String name, List<ResourceLocation> starts, @Nullable ResourceLocation end) {
		LayeredDraw vanilla() {
			LayeredDraw draw = new LayeredDraw();
			for (ResourceLocation start : starts) draw.add(new VanillaLayer(start));
			return draw;
		}

		/** Whether NeoForge's {@code layers} are this stack's: each of its layers' starts there, in vanilla's order. */
		boolean isIn(List<GuiLayerManager.NamedLayer> layers) {
			int last = -1;
			for (ResourceLocation start : starts) {
				int at = indexOf(layers, start);
				if (at <= last) return false;
				last = at;
			}
			return true;
		}

		/** Where NeoForge's layers of vanilla's layer {@code index} end in {@code layers}. */
		int endOf(List<GuiLayerManager.NamedLayer> layers, int index) {
			if (index + 1 < starts.size()) return indexOf(layers, starts.get(index + 1));
			int at = indexOf(layers, end);
			return at < 0 ? layers.size() : at;
		}

		void place(LayeredDraw vanilla, List<GuiLayerManager.NamedLayer> layers) {
			List<LayeredDraw.Layer> list = ((LayeredDrawLayersAccessor) vanilla).fabricated$layers();
			Set<ResourceLocation> kept = new HashSet<>();
			for (LayeredDraw.Layer layer : list) {
				if (layer instanceof VanillaLayer placeholder) kept.add(placeholder.start());
			}
			for (int i = 0; i < starts.size(); i++) {
				if (kept.contains(starts.get(i))) continue;
				for (int at = indexOf(layers, starts.get(i)), to = endOf(layers, i); at < to; at++) {
					layers.set(at, new GuiLayerManager.NamedLayer(layers.get(at).name(), NOTHING));
				}
			}
			List<LayeredDraw.Layer> pending = new ArrayList<>();
			for (LayeredDraw.Layer layer : list) {
				if (layer instanceof VanillaLayer next) {
					insert(layers, pending, indexOf(layers, next.start()));
				} else {
					pending.add(layer);
				}
			}
			insert(layers, pending, endOf(layers, starts.size() - 1));
		}
	}

	private static List<GuiLayerManager.NamedLayer> layers(GuiLayerManager manager) {
		return ((GuiLayerManagerLayersAccessor) manager).fabricated$layers();
	}

	private static int indexOf(List<GuiLayerManager.NamedLayer> layers, @Nullable ResourceLocation name) {
		for (int i = 0; i < layers.size(); i++) {
			if (layers.get(i).name().equals(name)) return i;
		}
		return -1;
	}

	private static void insert(List<GuiLayerManager.NamedLayer> layers, List<LayeredDraw.Layer> pending, int at) {
		for (LayeredDraw.Layer layer : pending) {
			layers.add(at++, new GuiLayerManager.NamedLayer(ResourceLocation.fromNamespaceAndPath("fabricated", "vanilla_stack_layer_" + added++), layer));
		}
		pending.clear();
	}

	/**
	 * Reports the layers hooks added to vanilla's stack {@code shape}, which NeoForge's constructor never placed. A layer a
	 * hook makes is mostly a lambda of {@code Gui}'s, where Mixin merges the hook, so the report's own lines name the
	 * mods: those whose hooks it points at the seam.
	 */
	private static void lost(Shape shape, LayeredDraw vanilla) {
		List<String> layers = new ArrayList<>();
		for (LayeredDraw.Layer layer : ((LayeredDrawLayersAccessor) vanilla).fabricated$layers()) {
			if (!(layer instanceof VanillaLayer)) layers.add(layer.getClass().getName());
		}
		if (layers.isEmpty()) return;
		String text = layers.size() + " layer(s) Fabric mods' hooks added to vanilla's " + shape.name() + " HUD layer stack in Gui's constructor"
				+ " aren't drawn: NeoForge's constructor didn't add that stack to its HUD where Fabricated looks for it (" + String.join(", ", layers)
				+ "). The hooks are those this report points at Fabricated's seam fabricated$read$layers.";
		LOGGER.warn("{}", text);
		FabricatedState state = FabricatedState.get();
		if (state != null) {
			state.report().line("Rendering", text);
			state.writeReport();
		}
	}
}
