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

package net.fabricmc.fabric.api.resource;

import net.minecraft.resources.ResourceLocation;

/**
 * This class contains default keys for various Minecraft resource reload listeners.
 *
 * @see IdentifiableResourceReloadListener
 */
public final class ResourceReloadListenerKeys {
	// client
	public static final ResourceLocation SOUNDS = ResourceLocation.withDefaultNamespace("sounds");
	public static final ResourceLocation FONTS = ResourceLocation.withDefaultNamespace("fonts");
	public static final ResourceLocation MODELS = ResourceLocation.withDefaultNamespace("models");
	public static final ResourceLocation LANGUAGES = ResourceLocation.withDefaultNamespace("languages");
	public static final ResourceLocation TEXTURES = ResourceLocation.withDefaultNamespace("textures");

	// server
	public static final ResourceLocation TAGS = ResourceLocation.withDefaultNamespace("tags");
	public static final ResourceLocation RECIPES = ResourceLocation.withDefaultNamespace("recipes");
	public static final ResourceLocation ADVANCEMENTS = ResourceLocation.withDefaultNamespace("advancements");
	public static final ResourceLocation FUNCTIONS = ResourceLocation.withDefaultNamespace("functions");

	private ResourceReloadListenerKeys() { }
}
