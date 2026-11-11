package io.github.cmdralph.fabricated.neoforge.mixin.client;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.neoforged.neoforge.client.gui.GuiLayerManager;

/**
 * The named layers of one of NeoForge's HUD layer managers, which the layers Fabric mods add to vanilla's stacks in
 * {@code Gui}'s constructor go into (HUD-7). NeoForge adds to it only at the end.
 */
@Mixin(GuiLayerManager.class)
public interface GuiLayerManagerLayersAccessor {
	@Accessor("layers")
	List<GuiLayerManager.NamedLayer> fabricated$layers();
}
