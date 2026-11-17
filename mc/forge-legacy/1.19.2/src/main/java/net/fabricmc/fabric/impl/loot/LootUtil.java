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

package net.fabricmc.fabric.impl.loot;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.fml.ModList;

import net.fabricmc.fabric.api.loot.v2.LootTableSource;
import net.fabricmc.fabric.impl.resource.loader.BuiltinModResourcePackSource;
import net.fabricmc.fabric.impl.resource.loader.FabricResource;
import net.fabricmc.fabric.impl.resource.loader.ModResourcePackCreator;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabricated: where a loot table came from, decided on Forge 43. Upstream asks fabric-resource-loader-v0 for the pack's
 * source, which tells mods' packs apart; on Forge each mod's data is a pack of its own, named after its mod file
 * ({@code ResourcePackLoader.createPackForMod}), while a world's data packs aren't, and a resource names the pack it is
 * from by its resources' name, vanilla's {@code Default}. With the resource loader installed, its own pack of the
 * Fabric mods' data (enabled in a world it makes), and their built-in packs, are mods' too, as upstream reads them. The
 * module doesn't depend on fabric-resource-loader-v0 here, which Fabricated passes through and may be absent.
 */
public final class LootUtil {
	/** The name of vanilla's pack resources ({@code VanillaPackResources.getName}). */
	private static final String VANILLA = "Default";

	private LootUtil() {
	}

	public static LootTableSource determineSource(ResourceLocation lootTableId, ResourceManager resourceManager) {
		ResourceLocation resourceId = new ResourceLocation(lootTableId.getNamespace(), "loot_tables/%s.json".formatted(lootTableId.getPath()));
		Optional<Resource> resource = resourceManager.getResource(resourceId);

		if (resource.isPresent()) {
			if (VANILLA.equals(resource.get().sourcePackId())) {
				return LootTableSource.VANILLA;
			} else if (ModPacks.NAMES.contains(resource.get().sourcePackId()) || FabricPacks.fromMods(resource.get())) {
				return LootTableSource.MOD;
			}
		}

		// If not builtin or mod, assume external data pack.
		// It might also be a virtual loot table injected via mixin instead of being loaded
		// from a resource, but we can't determine that here.
		return LootTableSource.DATA_PACK;
	}

	/** The resource loader's packs of the Fabric mods' data; its classes are only touched with it installed. */
	private static final class FabricPacks {
		private static final boolean INSTALLED = FabricLoader.getInstance().isModLoaded("fabric-resource-loader-v0");

		static boolean fromMods(Resource resource) {
			return INSTALLED && Upstream.fromMods(resource);
		}

		private static final class Upstream {
			static boolean fromMods(Resource resource) {
				PackSource source = ((FabricResource) resource).getFabricPackSource();
				return source == ModResourcePackCreator.RESOURCE_PACK_SOURCE || source instanceof BuiltinModResourcePackSource;
			}
		}
	}

	/** The names of the mods' packs: their mod files' names, fixed once the mods have loaded. */
	private static final class ModPacks {
		static final Set<String> NAMES = ModList.get().getModFiles().stream().map(file -> file.getFile().getFileName()).collect(Collectors.toUnmodifiableSet());
	}
}
