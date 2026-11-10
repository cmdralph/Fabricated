package io.github.cmdralph.fabricated.forge.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import io.github.cmdralph.fabricated.forge.entity.PoiTypesByState;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Forge 47 keeps {@code PoiTypes.TYPE_BY_STATE}, its registry's map of each block state's point-of-interest type, by
 * the type, where vanilla keeps the type's holder. A Fabric mod writing it as vanilla's left a holder where Forge's
 * {@code forState} casts to the type (BCLib's {@code PoiManager.updateStates} puts each state of its workstation tags
 * with the type's holder): the first such block placed stopped the game (POI-4). The field holds a view of Forge's map
 * that stores a holder's type ({@link PoiTypesByState}), so every reader finds Forge's values. Forge's registry
 * callbacks go on filling the map itself (POI-2), and Forge's {@code registerBlockStates} writes nothing (POI-3).
 */
@Mixin(PoiTypes.class)
abstract class PoiTypesByStateMixin {
	@ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE",
			target = "Lnet/minecraftforge/registries/GameData;getBlockStatePointOfInterestTypeMap()Ljava/util/Map;", remap = false))
	private static Map<BlockState, PoiType> fabricated$storingTypes(Map<BlockState, PoiType> forge) {
		return PoiTypesByState.of(forge);
	}
}
