package io.github.cmdralph.fabricated.forge.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import io.github.cmdralph.fabricated.forge.entity.PoiTypesAllStates;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.state.BlockState;

/** {@code PoiTypes.ALL_STATES} takes a Fabric mod's additions without throwing ({@link PoiTypesAllStates}). */
@Mixin(PoiTypes.class)
abstract class PoiTypesAllStatesMixin {
	@ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE", target = "Ljava/util/Map;keySet()Ljava/util/Set;", remap = false))
	private static Set<BlockState> fabricated$takingAdditions(Set<BlockState> forge) {
		return PoiTypesAllStates.of(forge);
	}
}
