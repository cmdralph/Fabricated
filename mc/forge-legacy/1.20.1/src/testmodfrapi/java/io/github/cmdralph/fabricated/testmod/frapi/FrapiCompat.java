package io.github.cmdralph.fabricated.testmod.frapi;

import java.util.Map;
import java.util.function.BiFunction;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** The renderer test mod's calls as Minecraft 1.20.1 and Fabric API 0.92 name them. */
final class FrapiCompat {
	/** The test blocks' models are put in place by a model loading plugin (fabric-model-loading-api-v1). */
	static final boolean MODEL_LOADING_PLUGIN = true;
	private static final Map<ItemDisplayContext, String> WHERE = Map.of(ItemDisplayContext.GUI, "screen", ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, "hand",
			ItemDisplayContext.FIRST_PERSON_LEFT_HAND, "hand", ItemDisplayContext.GROUND, "dropped", ItemDisplayContext.FIXED, "frame");

	private FrapiCompat() {
	}

	static ServerLevel level(ServerPlayer player) {
		return player.serverLevel();
	}

	static BlockBehaviour.Properties blockProperties() {
		return BlockBehaviour.Properties.of();
	}

	/** Puts models in place of baked ones, as a Fabric mod for 1.20.1 does: a model loading plugin's after-bake modifier. */
	static void replaceBakedModels(BiFunction<BakedModel, ResourceLocation, BakedModel> replace) {
		ModelLoadingPlugin.register(context -> context.modifyModelAfterBake().register((model, bake) -> replace.apply(model, bake.id())));
	}

	/** Where an item is drawn ("screen", "hand", "dropped", "frame"), as Fabric API's render context says, or null. */
	static @Nullable String itemPlace(RenderContext context) {
		return WHERE.get(context.itemTransformationMode());
	}

	/** What said where it is, for the log. */
	static Object itemPlaceDetail(RenderContext context) {
		return context.itemTransformationMode();
	}
}
