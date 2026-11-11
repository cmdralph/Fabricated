package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;

/** A JSON data loader's folder, which upstream's conditions name in their messages ({@link JsonReloadConditionsMixin}). */
@Mixin(SimpleJsonResourceReloadListener.class)
public interface SimpleJsonResourceReloadListenerAccessor {
	@Accessor("directory")
	String fabricated$directory();
}
