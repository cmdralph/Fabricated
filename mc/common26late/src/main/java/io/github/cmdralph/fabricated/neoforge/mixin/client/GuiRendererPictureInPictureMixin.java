package io.github.cmdralph.fabricated.neoforge.mixin.client;

import java.util.HashMap;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.neoforged.neoforge.client.gui.PictureInPictureRendererPool;

/**
 * Gives the GUI renderer back vanilla's map of picture-in-picture renderers by render state class, which NeoForge
 * replaced with renderer pools filled from its own event: Fabric mods read that map and replace it with one holding
 * their renderers too (malilib, PIP-1). A state NeoForge has no pool for is prepared with the map's renderer, as
 * vanilla prepares every state, and the map's renderers close with the GUI renderer. Applied before other mods' mixins,
 * which shadow the field.
 */
@Mixin(value = GuiRenderer.class, priority = 100)
abstract class GuiRendererPictureInPictureMixin {
	@Shadow
	@Final
	private GuiRenderState renderState;
	@Shadow
	@Final
	private FeatureRenderDispatcher featureRenderDispatcher;
	@Shadow
	@Final
	private Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRendererPool<?>> pictureInPictureRendererPools;
	private final Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> pictureInPictureRenderers = new HashMap<>();

	@Inject(method = "preparePictureInPictureState", at = @At("HEAD"), cancellable = true)
	private <T extends PictureInPictureRenderState> void fabricated$prepareModRenderer(T state, int guiScale, boolean firstPass,
			CallbackInfoReturnable<Boolean> cir) {
		if (pictureInPictureRendererPools.containsKey(state.getClass())) return;
		@SuppressWarnings("unchecked")
		PictureInPictureRenderer<T> renderer = (PictureInPictureRenderer<T>) pictureInPictureRenderers.get(state.getClass());
		if (renderer == null) return;
		renderer.prepare(state, renderState, featureRenderDispatcher, guiScale);
		cir.setReturnValue(true);
	}

	@Inject(method = "close", at = @At("TAIL"))
	private void fabricated$closeModRenderers(CallbackInfo ci) {
		pictureInPictureRenderers.values().forEach(PictureInPictureRenderer::close);
	}
}
