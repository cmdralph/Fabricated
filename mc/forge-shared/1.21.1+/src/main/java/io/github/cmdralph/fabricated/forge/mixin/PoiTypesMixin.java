package io.github.cmdralph.fabricated.forge.mixin;

import java.util.Map;
import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.GameData;

/**
 * Lets Fabric mods map a point-of-interest type's block states through vanilla's {@code PoiTypes.registerBlockStates}
 * once the type is registered, as Friends and Foes and Moonlight do (POI-3, NeoForge's POI-1). Forge 52 and 61 to 66 map
 * the states as the type is registered (their registry callback) and keep vanilla's method, which takes each entry it
 * finds as a second type for the state. The callback's entries for this very type are taken out first, so vanilla puts
 * them back; a state another type has still fails as vanilla fails it. Forge 47 empties vanilla's method.
 */
@Mixin(PoiTypes.class)
abstract class PoiTypesMixin {
	@Inject(method = "registerBlockStates", at = @At("HEAD"))
	private static void fabricated$withoutCallbackEntries(Holder<PoiType> type, Set<BlockState> states, CallbackInfo ci) {
		Map<BlockState, Holder<PoiType>> byState = GameData.PoiTypeCallbacks.getStateToPoi();
		for (BlockState state : states) {
			Holder<PoiType> mapped = byState.get(state);
			if (mapped != null && mapped.value() == type.value()) byState.remove(state);
		}
	}
}
