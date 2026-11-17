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

package net.fabricmc.fabric.mixin.resource;

import java.util.Set;
import java.util.function.Predicate;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.server.packs.repository.Pack;

import net.fabricmc.fabric.impl.resource.pack.FabricPack;

// Fabricated: upstream's pack source tracking at the end of open() is left out, as Fabricated tells a resource's pack
// source from NeoForge's packs (ModResourcePackCreator.RESOURCE_PACK_SOURCE). Upstream's default predicate, which every
// set of parents passes, is no predicate here: with upstream's field initializing it, every pack read as hidden on
// NeoForge 26.1.2, and a reload dropped the vanilla data pack. A pack Fabric hides is hidden as NeoForge hides its own
// (fabricated$hiddenByFabric), where upstream's mixins of the pack screens and the datapack command hide it on Fabric
// (PROVIDER-23).
@Mixin(Pack.class)
abstract class PackMixin implements FabricPack {
	@Unique
	@Nullable
	private Predicate<Set<String>> parentsPredicate;

	@Override
	public boolean fabric$isHidden() {
		return this.parentsPredicate != null;
	}

	@Override
	public boolean fabric$parentsEnabled(Set<String> enabled) {
		return this.parentsPredicate == null || this.parentsPredicate.test(enabled);
	}

	@Override
	public void fabric$setParentsPredicate(Predicate<Set<String>> predicate) {
		this.parentsPredicate = predicate;
	}

	// Fabricated: NeoForge's pack screens, the datapack command's list and the pack ids a repository saves leave out a pack
	// whose isHidden() is true; a pack a Fabric mod hides through its parents predicate is left out there too.
	@ModifyReturnValue(method = "isHidden", at = @At("RETURN"))
	private boolean fabricated$hiddenByFabric(boolean hidden) {
		return hidden || this.fabric$isHidden();
	}
}
