package io.github.cmdralph.fabricated.forge.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.LayeredDraw;

/** Vanilla's list of a HUD layer stack, which Forge 52's stacks fill only once they are resolved ({@link ForgeLayeredDrawVanillaLayersMixin}). */
@Mixin(LayeredDraw.class)
public interface LayeredDrawLayersAccessor {
	@Accessor("layers")
	List<LayeredDraw.Layer> fabricated$layers();
}
