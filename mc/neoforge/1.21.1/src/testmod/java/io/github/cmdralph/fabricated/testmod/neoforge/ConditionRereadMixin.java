package io.github.cmdralph.fabricated.testmod.neoforge;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import io.github.cmdralph.fabricated.api.FabricApiModule;
import io.github.cmdralph.fabricated.api.Fabricated;
import io.github.cmdralph.fabricated.api.FabricatedApi;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.fml.ModList;

/**
 * A NeoForge mod reading Fabric's conditions again its own way at the start of applying a JSON data loader's files, as
 * Moonlight's NeoForge build for 1.21.1 does (its {@code ConditionHackMixin}, COND-1): a file still carrying
 * {@code fabric:load_conditions} whose {@code fabric:all_mods_loaded} names a mod NeoForge's mod list hasn't is dropped
 * (Moonlight reads it as NeoForge's own {@code all_mods_loaded}). The test mod's kept file names the test mod's Fabric
 * id, with a dash, which NeoForge's list hasn't: the json loader conditions check passes only where Fabric's conditions
 * were decided first. Only while the module is Fabric API's own, the copy Fabricated decides them first for: another
 * mod's (Forgified Fabric API's) reads them its own way.
 */
@Mixin(SimplePreparableReloadListener.class)
public abstract class ConditionRereadMixin {
	@Inject(method = "lambda$reload$1", at = @At("HEAD"))
	private void fabricatedTest$readConditionsAgain(ResourceManager manager, ProfilerFiller profiler, Object prepared, CallbackInfo ci) {
		if (!((Object) this instanceof SimpleJsonResourceReloadListener) || !(prepared instanceof Map<?, ?> data)) return;
		Optional<FabricatedApi> api = Fabricated.get();
		Optional<FabricApiModule> module = api.isPresent() ? api.get().fabricApiModule("fabric-resource-conditions-api-v1") : Optional.empty();
		if (module.isEmpty() || module.get().status() != FabricApiModule.Status.LOADED) return;
		for (Iterator<?> files = data.values().iterator(); files.hasNext(); ) {
			if (fabricatedTest$namesModNeoForgeHasNot(files.next())) files.remove();
		}
	}

	/** Whether a file's {@code fabric:all_mods_loaded} conditions name a mod NeoForge's mod list hasn't. */
	@Unique
	private static boolean fabricatedTest$namesModNeoForgeHasNot(Object file) {
		if (!(file instanceof JsonObject json)) return false;
		JsonElement conditions = json.get("fabric:load_conditions");
		List<JsonElement> each = conditions instanceof JsonArray array ? array.asList() : conditions == null ? List.of() : List.of(conditions);
		for (JsonElement condition : each) {
			if (!(condition instanceof JsonObject object) || !(object.get("condition") instanceof JsonPrimitive type)
					|| !"fabric:all_mods_loaded".equals(type.getAsString()) || !(object.get("values") instanceof JsonArray values)) continue;
			for (JsonElement id : values) {
				if (id instanceof JsonPrimitive name && !ModList.get().isLoaded(name.getAsString())) return true;
			}
		}
		return false;
	}
}
