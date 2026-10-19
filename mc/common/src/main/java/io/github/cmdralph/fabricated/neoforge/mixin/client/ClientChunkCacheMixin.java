package io.github.cmdralph.fabricated.neoforge.mixin.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import io.github.cmdralph.fabricated.neoforge.lifecycle.ClientChunkLifecycle;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Unloads (for Fabric) the chunks the client drops without {@code drop()}, the only place NeoForge posts its chunk
 * unload event: chunks left outside a smaller view distance, and a stale chunk whose slot a chunk packet reuses.
 * Upstream fires Fabric's {@code CHUNK_UNLOAD} at the same two points.
 */
@Mixin(ClientChunkCache.class)
abstract class ClientChunkCacheMixin {
	@Shadow
	@Final
	private ClientLevel level;

	@ModifyExpressionValue(method = "updateViewRadius", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;inRange(II)Z"))
	private boolean fabricated$unloadOutOfRange(boolean inRange, @Local LevelChunk chunk) {
		if (!inRange) ClientChunkLifecycle.chunkUnloaded(level, chunk);
		return inRange;
	}

	@Inject(method = "replaceWithPacketData", at = @At(value = "NEW", target = "net/minecraft/world/level/chunk/LevelChunk"))
	private void fabricated$unloadReplaced(CallbackInfoReturnable<LevelChunk> cir, @Local LevelChunk stale) {
		if (stale != null) ClientChunkLifecycle.chunkUnloaded(level, stale);
	}
}
