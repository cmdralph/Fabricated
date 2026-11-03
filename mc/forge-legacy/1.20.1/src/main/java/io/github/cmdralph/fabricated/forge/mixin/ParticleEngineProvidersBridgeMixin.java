package io.github.cmdralph.fabricated.forge.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import io.github.cmdralph.fabricated.forge.particle.ParticleProvidersById;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * The particle engine's providers as vanilla keeps them, by their type's raw id, for Fabric mods' accessors of them
 * (CORPUS-37): Forge 47 keeps them by the type's id ({@code providers}), where Porting Lib's accessor reads vanilla's
 * {@code Int2ObjectMap}. Pre-flight points the accessor at this bridge ({@code MixinRepairs.retypedFieldBridge} in core),
 * named after the field's runtime name, which the field is named by here too. The map it gives is a view of Forge's,
 * which reads and writes go to (Fabric API's particle registry is the in-game mod's own here, PART-1).
 */
@Mixin(ParticleEngine.class)
abstract class ParticleEngineProvidersBridgeMixin {
	@Shadow(remap = false)
	@Final
	private Map<ResourceLocation, ParticleProvider<?>> f_107293_;

	@Unique
	private Int2ObjectMap<ParticleProvider<?>> fabricated$providersById;

	/** Vanilla's {@code providers}: a view by raw id of Forge's map by id. */
	public Int2ObjectMap<ParticleProvider<?>> fabricated$get$f_107293_() {
		if (fabricated$providersById == null) fabricated$providersById = new ParticleProvidersById(f_107293_);
		return fabricated$providersById;
	}
}
