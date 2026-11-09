package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.serialization.Codec;

import io.github.cmdralph.fabricated.forge.tag.FabricTagRemoval;
import net.minecraft.tags.TagFile;

/**
 * Tag files also read Fabric's {@code fabric:remove} list (see {@link FabricTagRemoval}). Plain Mixin, as the in-game mod
 * doesn't compile against MixinExtras here: the codec Forge's patch builds is wrapped once the class has made it.
 */
@Mixin(TagFile.class)
abstract class TagFileRemoveMixin {
	@Shadow
	@Final
	@Mutable
	public static Codec<TagFile> CODEC;

	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void fabricated$readFabricRemove(CallbackInfo ci) {
		CODEC = FabricTagRemoval.codec(CODEC);
	}
}
