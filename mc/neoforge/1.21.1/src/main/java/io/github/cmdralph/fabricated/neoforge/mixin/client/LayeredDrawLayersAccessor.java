package io.github.cmdralph.fabricated.neoforge.mixin.client;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.LayeredDraw;

/** The list of a vanilla HUD layer stack, which Fabric mods' hooks in {@code Gui}'s constructor add to (HUD-7). */
@Mixin(LayeredDraw.class)
public interface LayeredDrawLayersAccessor {
	@Accessor("layers")
	List<LayeredDraw.Layer> fabricated$layers();
}
