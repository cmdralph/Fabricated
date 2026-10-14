package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.Codec;

import io.github.cmdralph.fabricated.neoforge.tag.FabricTagRemoval;
import net.minecraft.tags.TagFile;

/** Tag files also read Fabric's {@code fabric:remove} list (see {@link FabricTagRemoval}). */
@Mixin(TagFile.class)
abstract class TagFileRemoveMixin {
	@ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE",
			target = "Lcom/mojang/serialization/codecs/RecordCodecBuilder;create(Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"))
	private static Codec<TagFile> fabricated$readFabricRemove(Codec<TagFile> neoForge) {
		return FabricTagRemoval.codec(neoForge);
	}
}
