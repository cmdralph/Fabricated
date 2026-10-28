/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.mixin.client.particle;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Fabricated: upstream also reads the engine's map of factories, which NeoForge 21.1 keeps by the type's id instead of
 * its raw id (that accessor failed pre-flight, so the module was off), and its map of sprite sets: both are now written
 * through the engine's own methods ({@code ParticleFactoryRegistryImpl}). The sprite set's class is package-private,
 * so its accessor names it as a string.
 */
@Mixin(ParticleEngine.class)
public interface ParticleManagerAccessor {
	@Accessor("textureAtlas")
	TextureAtlas getParticleAtlasTexture();

	@Mixin(targets = "net.minecraft.client.particle.ParticleEngine$MutableSpriteSet")
	interface SimpleSpriteProviderAccessor {
		@Accessor("sprites")
		List<TextureAtlasSprite> getSprites();
	}
}
