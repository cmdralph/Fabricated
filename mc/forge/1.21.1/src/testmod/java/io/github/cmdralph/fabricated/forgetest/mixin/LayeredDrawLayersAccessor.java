package io.github.cmdralph.fabricated.forgetest.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.LayeredDraw;

/** Vanilla's list of a HUD layer stack, as Fabric mods reach it ({@link HudLayersMixin}). */
@Mixin(LayeredDraw.class)
public interface LayeredDrawLayersAccessor {
	@Accessor("layers")
	List<LayeredDraw.Layer> fabricatedForgeTest$layers();
}
