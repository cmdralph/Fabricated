package io.github.cmdralph.fabricated.neoforge.rendering.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * The quad transforms Indigo's emitters hold (1.21.11): where a wrapped model threw, the transforms it left are popped
 * before its wrapper's call returns ({@link WrapperBlockStateModelMixin}). Indigo is a mod ({@code @Pseudo}).
 */
@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableQuadViewImpl")
public interface MutableQuadViewAccessor {
	@Accessor("transformStack")
	ObjectArrayList<?> fabricated$transformStack();
}
