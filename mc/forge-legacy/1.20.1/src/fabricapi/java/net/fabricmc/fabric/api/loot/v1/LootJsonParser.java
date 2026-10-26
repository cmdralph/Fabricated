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

package net.fabricmc.fabric.api.loot.v1;

import java.io.Reader;

import com.google.gson.Gson;

import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.storage.loot.Deserializers;

/**
 * @deprecated Use {@link Deserializers#createLootTableSerializer()} from vanilla instead.
 */
@Deprecated
public final class LootJsonParser {
	private static final Gson GSON = Deserializers.createLootTableSerializer().create();

	private LootJsonParser() { }

	public static <T> T read(Reader json, Class<T> c) {
		return GsonHelper.fromJson(GSON, json, c);
	}

	public static <T> T read(String json, Class<T> c) {
		return GsonHelper.fromJson(GSON, json, c);
	}
}
