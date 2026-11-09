package io.github.cmdralph.fabricated.forgetest.mixin;

import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

/**
 * The particle engine's providers by their type's raw id, as Porting Lib's accessor reads them: Forge 47 keeps them by
 * the type's id, so this invokes the in-game mod's bridge (CORPUS-37, {@code ForgeParticleClient}). The field is vanilla's,
 * whose type Forge's game this compiles against changed: the accessor names it in Intermediary's names, as the mod's jar
 * does once Loom has remapped it, and stays out of the refmap.
 */
@Mixin(ParticleEngine.class)
public interface ParticleEngineProvidersAccessor {
	@Accessor(value = "field_3835", remap = false)
	Int2ObjectMap<Object> fabricatedtest$providers();
}
