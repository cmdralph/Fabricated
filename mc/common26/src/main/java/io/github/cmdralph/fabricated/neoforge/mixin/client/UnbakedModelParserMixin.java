package io.github.cmdralph.fabricated.neoforge.mixin.client;

import java.lang.reflect.Type;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;

import io.github.cmdralph.fabricated.neoforge.rendering.FabricModelFormats;
import net.minecraft.client.resources.model.UnbakedModel;
import net.neoforged.neoforge.client.model.UnbakedModelParser;

/**
 * Model files of a Fabric type ({@code "fabric:type"}) go to the deserializer a Fabric mod registered for it, before
 * NeoForge's own model types ({@code "loader"}) are looked at (see {@link FabricModelFormats}).
 */
@Mixin(UnbakedModelParser.Deserializer.class)
abstract class UnbakedModelParserMixin {
	// With the descriptor: the class also has the compiler's bridge method of the same name.
	@Inject(method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lnet/minecraft/client/resources/model/UnbakedModel;", at = @At("HEAD"), cancellable = true)
	private void fabricated$fabricTypes(JsonElement json, Type type, JsonDeserializationContext context, CallbackInfoReturnable<UnbakedModel> cir) {
		UnbakedModel model = FabricModelFormats.deserialize(json, context);
		if (model != null) cir.setReturnValue(model);
	}
}
