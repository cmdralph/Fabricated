package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;

import io.github.cmdralph.fabricated.neoforge.lifecycle.BlockEntityLifecycle;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Fabric's block entity load and unload events, where upstream fires them: as a block entity enters its chunk's map
 * (when the chunk loads, or when it is placed later) and as it leaves it (broken, replaced, or found removed). The
 * calls are matched by what they do, not by local variable names, which differ between NeoForge's builds. The block
 * entities a chunk still holds when it unloads are unloaded with the chunk ({@link BlockEntityLifecycle#unloadAll}).
 */
@Mixin(LevelChunk.class)
abstract class LevelChunkBlockEntityMixin {
	@Shadow
	public abstract Level getLevel();

	@ModifyExpressionValue(method = "setBlockEntity", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
	private Object fabricated$blockEntityLoaded(Object previous, @Local(argsOnly = true) BlockEntity blockEntity) {
		if (blockEntity != previous) BlockEntityLifecycle.loaded(blockEntity, getLevel());
		return previous;
	}

	/** The block entity being removed: the one a new block entity replaces, or the one at a broken block. */
	@WrapWithCondition(method = {"setBlockEntity", "removeBlockEntity"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BlockEntity;setRemoved()V"))
	private boolean fabricated$blockEntityUnloaded(BlockEntity blockEntity) {
		BlockEntityLifecycle.unloaded(blockEntity, getLevel());
		return true; // only observes the call
	}

	/** A block entity marked removed elsewhere leaves the map when it is next looked up (pending data removed there is skipped). */
	@ModifyExpressionValue(method = "getBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/LevelChunk$EntityCreationType;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
			at = @At(value = "INVOKE", target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;"))
	private Object fabricated$removedBlockEntityDropped(Object removed) {
		if (removed instanceof BlockEntity blockEntity) BlockEntityLifecycle.unloaded(blockEntity, getLevel());
		return removed;
	}

	/** On the client, a chunk the server sends again drops all its block entities first; new ones are made from the packet. */
	@Inject(method = "replaceWithPacketData", at = @At("HEAD"))
	private void fabricated$blockEntitiesReplaced(CallbackInfo ci) {
		BlockEntityLifecycle.unloadAll((LevelChunk) (Object) this);
	}
}
