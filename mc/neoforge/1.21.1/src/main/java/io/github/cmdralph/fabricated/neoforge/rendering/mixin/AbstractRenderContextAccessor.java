package io.github.cmdralph.fabricated.neoforge.rendering.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * The quad transforms Indigo's render contexts hold (1.21.1): where a wrapped model threw, the transforms it left are
 * popped before its wrapper's call returns ({@link ForwardingBakedModelMixin}). Indigo's class is package-private, and
 * a mod ({@code @Pseudo}).
 */
@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AbstractRenderContext")
public interface AbstractRenderContextAccessor {
	@Accessor("transformStack")
	ObjectArrayList<?> fabricated$transformStack();
}
