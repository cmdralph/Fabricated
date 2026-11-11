package io.github.cmdralph.fabricated.testmod.neoforge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.google.gson.JsonObject;

import io.github.cmdralph.fabricated.testmod.TestMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * What deciding Fabric's conditions first leaves of the test mod's conditioned files (COND-1), as the data loader the
 * test mod's {@code fabricated_conditions} folder has applies them, at each reload: the file whose conditions upstream
 * can't parse ({@code unparsed}, a condition type no mod registers) keeps them, as upstream, having decided nothing,
 * keeps it as it is, for a later reader that can parse them; and the objects as prepared, which a loader may keep from
 * one reload to the next, are as they were, the conditions of the file that loads ({@code kept}) on it still.
 * Priority 100: its hook at the start of applying them runs before Fabricated's (500), to see the objects as prepared.
 */
@Mixin(value = SimplePreparableReloadListener.class, priority = 100)
public abstract class ConditionsAsPreparedMixin {
	@Unique
	private static final String CONDITIONS = "fabric:load_conditions";
	/** The test mod's kept file as prepared, while its loader applies it. */
	@Unique
	private static JsonObject fabricatedTest$keptAsPrepared;

	@Inject(method = "lambda$reload$1", at = @At("HEAD"))
	private void fabricatedTest$asPrepared(ResourceManager manager, ProfilerFiller profiler, Object prepared, CallbackInfo ci) {
		if (prepared instanceof Map<?, ?> data && data.get(fabricatedTest$id("unparsed")) instanceof JsonObject
				&& data.get(fabricatedTest$id("kept")) instanceof JsonObject kept) {
			fabricatedTest$keptAsPrepared = kept;
		}
	}

	@Inject(method = "lambda$reload$1", at = @At("TAIL"))
	private void fabricatedTest$leftAsTheyAre(ResourceManager manager, ProfilerFiller profiler, Object prepared, CallbackInfo ci) {
		JsonObject kept = fabricatedTest$keptAsPrepared;
		if (kept == null || !(prepared instanceof Map<?, ?> data)) return;
		fabricatedTest$keptAsPrepared = null;
		if (!FabricLoader.getInstance().isModLoaded("fabric-resource-conditions-api-v1")) {
			TestMod.LOGGER.info("FABRICATED-TEST json loader conditions left as they are SKIPPED: fabric-resource-conditions-api-v1 isn't installed");
			return;
		}
		List<String> failed = new ArrayList<>();
		Object unparsed = data.get(fabricatedTest$id("unparsed"));
		if (unparsed == null) {
			failed.add("the file whose conditions upstream can't parse wasn't loaded");
		} else if (!(unparsed instanceof JsonObject object) || !object.has(CONDITIONS)) {
			failed.add("the file whose conditions upstream can't parse lost them");
		}
		if (!kept.has(CONDITIONS)) failed.add("the kept file as prepared lost its conditions");
		if (failed.isEmpty()) {
			TestMod.LOGGER.info("FABRICATED-TEST json loader conditions left as they are OK");
		} else {
			TestMod.LOGGER.error("FABRICATED-TEST json loader conditions left as they are FAILED: {}", failed);
		}
	}

	@Unique
	private static ResourceLocation fabricatedTest$id(String path) {
		return ResourceLocation.fromNamespaceAndPath("fabricated_testmod", path);
	}
}
