package io.github.cmdralph.fabricated.testmod.frapi;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;

/**
 * The renderer test mod's calls as Minecraft 1.19.2 and Fabric API 0.77 name them. Fabric API 0.77 has no model
 * loading plugins, and its render context doesn't say where an item is drawn: a Fabric mod for 1.19.2 replaced baked
 * models with a mixin into the model bakery ({@code mixin/ModelBakeryMixin}), and the item is placed from the game's
 * frames on the stack.
 */
public final class FrapiCompat {
	/** The test blocks' models are put in place as the model bakery finishes, not by a model loading plugin. */
	static final boolean MODEL_LOADING_PLUGIN = false;
	/** The innermost of the game's classes on the stack that says where an item is drawn, by class name. */
	private static final Map<String, String> WHERE = Map.of("net.minecraft.client.renderer.ItemInHandRenderer", "hand",
			"net.minecraft.client.renderer.entity.ItemEntityRenderer", "dropped", "net.minecraft.client.renderer.entity.ItemFrameRenderer", "frame",
			"net.minecraft.client.gui.Gui", "screen", "net.minecraftforge.client.gui.overlay.ForgeGui", "screen");
	private static @Nullable BiFunction<BakedModel, ResourceLocation, BakedModel> replacer;

	private FrapiCompat() {
	}

	static ServerLevel level(ServerPlayer player) {
		return player.getLevel();
	}

	static BlockBehaviour.Properties blockProperties() {
		return BlockBehaviour.Properties.of(Material.STONE);
	}

	/** Puts models in place of baked ones once the model bakery has baked the top-level models ({@link #replace}). */
	static void replaceBakedModels(BiFunction<BakedModel, ResourceLocation, BakedModel> replace) {
		replacer = replace;
	}

	/** From {@code mixin/ModelBakeryMixin}: the bakery's top-level models, which the model manager then draws from. */
	public static void replace(Map<ResourceLocation, BakedModel> baked) {
		BiFunction<BakedModel, ResourceLocation, BakedModel> replace = replacer;
		if (replace != null) baked.replaceAll((id, model) -> replace.apply(model, id));
	}

	/** Where an item is drawn ("screen", "hand", "dropped", "frame"), from the frames on the stack, or null. */
	static @Nullable String itemPlace(RenderContext context) {
		return frame().map(WHERE::get).orElse(null);
	}

	/** What said where it is, for the log. */
	static Object itemPlaceDetail(RenderContext context) {
		return frame().orElse("no frame that says");
	}

	private static Optional<String> frame() {
		return StackWalker.getInstance().walk(frames -> frames.map(StackWalker.StackFrame::getClassName).filter(WHERE::containsKey).findFirst());
	}
}
