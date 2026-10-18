package net.fabricmc.fabric.mixin.content.registry;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import io.github.cmdralph.fabricated.neoforge.registry.FabricTableEntries;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WeatheringCopper;
import net.neoforged.neoforge.common.DataMapHooks;

/**
 * NeoForge (26.3) reads oxidation and waxing from its data maps only; Fabric's oxidizable registry writes vanilla's
 * tables. A block the data maps leave out gets what Fabric code registered for it (see {@link FabricTableEntries}).
 */
@Mixin(DataMapHooks.class)
abstract class FabricatedDataMapHooksMixin {
	@ModifyReturnValue(method = "getNextOxidizedStage", at = @At("RETURN"))
	private static @Nullable Block fabricated$nextStage(@Nullable Block next, Block block) {
		return next != null ? next : FabricTableEntries.nextOxidationStage(block);
	}

	@ModifyReturnValue(method = "getPreviousOxidizedStage", at = @At("RETURN"))
	private static @Nullable Block fabricated$previousStage(@Nullable Block previous, Block block) {
		return previous != null ? previous : FabricTableEntries.previousOxidationStage(block);
	}

	@ModifyReturnValue(method = "getBlockWaxed", at = @At("RETURN"))
	private static @Nullable Block fabricated$waxed(@Nullable Block waxed, Block block) {
		return waxed != null ? waxed : FabricTableEntries.waxed(block);
	}

	@ModifyReturnValue(method = "getBlockUnwaxed", at = @At("RETURN"))
	private static @Nullable Block fabricated$unwaxed(@Nullable Block unwaxed, Block block) {
		return unwaxed != null ? unwaxed : FabricTableEntries.unwaxed(block);
	}
}
