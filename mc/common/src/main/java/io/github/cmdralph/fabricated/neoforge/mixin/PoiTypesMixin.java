package io.github.cmdralph.fabricated.neoforge.mixin;

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
import net.neoforged.neoforge.registries.GameData;

/**
 * Lets Fabric mods register a point-of-interest type through vanilla's {@code PoiTypes.register}, as Fabric API's
 * helper and WorldWeaver (BetterEnd, BetterNether) do (POI-1). NeoForge maps the type's block states as the type is
 * registered (its registry callback), and vanilla's registration then maps them again into the same table and takes
 * each entry it finds as a second type for the state. The callback's entries for this very type are taken out first,
 * so vanilla puts them back; a state another type has still fails as vanilla fails it.
 */
@Mixin(PoiTypes.class)
abstract class PoiTypesMixin {
	@Inject(method = "registerBlockStates", at = @At("HEAD"))
	private static void fabricated$withoutCallbackEntries(Holder<PoiType> type, Set<BlockState> states, CallbackInfo ci) {
		Map<BlockState, Holder<PoiType>> byState = GameData.getBlockStatePointOfInterestTypeMap();
		for (BlockState state : states) {
			if (byState.get(state) == type) byState.remove(state);
		}
	}
}
