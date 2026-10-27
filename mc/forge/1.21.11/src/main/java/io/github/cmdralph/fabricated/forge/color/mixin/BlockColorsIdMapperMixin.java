package io.github.cmdralph.fabricated.forge.color.mixin;

import java.util.HashMap;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.core.IdMapper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.IdMappingEvent;

/**
 * Gives the block colours back vanilla's map of colour providers by block id, beside the map by registry holder Forge
 * put in its place under the same name: Fabric API's colour provider registry reads a block's provider from vanilla's
 * map, and pre-flight disabled fabric-rendering-v1, with the renderer modules needing it, on Forge 61 (COLOR-1). Every
 * provider registered, Forge's colour event's too, goes in both. Forge changes block ids as it syncs registries with a
 * server, so vanilla's map is built again from the providers by block each time it remaps ids. Applied before other
 * mods' mixins, which shadow the field; Forge's Mixin wouldn't find it for them, so Fabricated's transformation service
 * declares it first, and this mixin's initialiser sets it.
 */
@Mixin(value = BlockColors.class, priority = 100)
abstract class BlockColorsIdMapperMixin {
	private volatile IdMapper<BlockColor> blockColors = new IdMapper<>(32);
	@Unique
	private final Map<Block, BlockColor> fabricated$providers = new HashMap<>();

	@Inject(method = "<init>", at = @At("RETURN"))
	private void fabricated$followIdChanges(CallbackInfo ci) {
		IdMappingEvent.BUS.addListener(event -> fabricated$mapByIds());
	}

	@Inject(method = "register", at = @At("TAIL"))
	private void fabricated$mapById(BlockColor provider, Block[] blocks, CallbackInfo ci) {
		for (Block block : blocks) {
			fabricated$providers.put(block, provider);
			blockColors.addMapping(provider, BuiltInRegistries.BLOCK.getId(block));
		}
	}

	@Unique
	private void fabricated$mapByIds() {
		IdMapper<BlockColor> ids = new IdMapper<>(32);
		fabricated$providers.forEach((block, provider) -> ids.addMapping(provider, BuiltInRegistries.BLOCK.getId(block)));
		blockColors = ids;
	}
}
