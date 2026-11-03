package io.github.cmdralph.fabricated.forgetest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.class_702;

/**
 * The particle engine's providers by their type's raw id, as Porting Lib's accessor reads them: Forge 47 keeps them by
 * the type's id, so this invokes the in-game mod's bridge (CORPUS-37, {@code ForgeParticleClient}).
 */
@Mixin(class_702.class)
public interface ParticleEngineProvidersAccessor {
	@Accessor("field_3835")
	Int2ObjectMap<Object> fabricatedtest$providers();
}
