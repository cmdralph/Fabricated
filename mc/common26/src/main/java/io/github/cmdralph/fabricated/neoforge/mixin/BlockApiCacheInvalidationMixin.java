package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.fabricmc.fabric.impl.lookup.block.ServerLevelCache;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Invalidates Fabric's {@code BlockApiCache}s where NeoForge invalidates its capability caches: when a block entity
 * is placed or removed. Upstream does it from block entity load and unload events, which Fabricated only fires for
 * whole chunks, so without this a cache keeps returning a removed block entity's storage.
 */
@Mixin(ServerLevel.class)
public abstract class BlockApiCacheInvalidationMixin {
	@Inject(method = "invalidateCapabilities(Lnet/minecraft/core/BlockPos;)V", at = @At("TAIL"))
	private void fabricated$invalidateBlockApiCaches(BlockPos pos, CallbackInfo ci) {
		((ServerLevelCache) this).fabric_invalidateCache(pos);
	}
}
