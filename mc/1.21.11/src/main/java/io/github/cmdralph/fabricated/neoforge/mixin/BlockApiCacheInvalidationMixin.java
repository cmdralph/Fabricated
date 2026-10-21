package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.fabricmc.fabric.impl.lookup.block.ServerWorldCache;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Invalidates Fabric's {@code BlockApiCache}s where NeoForge invalidates its capability caches. Upstream does it from
 * block entity load and unload events, which Fabricated fires too; NeoForge's also come when a NeoForge mod announces
 * that what a position provides changed without a block entity changing, which Fabric's caches then see.
 */
@Mixin(ServerLevel.class)
public abstract class BlockApiCacheInvalidationMixin {
	@Inject(method = "invalidateCapabilities(Lnet/minecraft/core/BlockPos;)V", at = @At("TAIL"))
	private void fabricated$invalidateBlockApiCaches(BlockPos pos, CallbackInfo ci) {
		((ServerWorldCache) this).fabric_invalidateCache(pos);
	}
}
