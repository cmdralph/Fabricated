package io.github.cmdralph.fabricated.testmod;

import java.util.concurrent.atomic.AtomicBoolean;

import net.fabricmc.fabric.api.client.model.loading.v1.UnbakedModelDeserializer;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;

/**
 * Fabric's model loading API on 1.21.11, which loads there without the renderer API: a model file of a Fabric type
 * ({@code models/block/deserializer_check.json}) is read by the deserializer registered here. Only with Fabric API's
 * model loading module installed (the client run with Fabric API); this class names it.
 */
final class ModelLoadingTest {
	private ModelLoadingTest() {
	}

	static void register() {
		// {"fabric:type": "fabricated_testmod:delegate", "model": <a model>}: reads the inner model.
		AtomicBoolean deserialized = new AtomicBoolean();
		UnbakedModelDeserializer.register(Identifier.fromNamespaceAndPath("fabricated_testmod", "delegate"), (json, context) -> {
			if (!deserialized.getAndSet(true)) TestMod.LOGGER.info("FABRICATED-TEST model deserializer OK");
			return context.deserialize(GsonHelper.getAsJsonObject(json, "model"), UnbakedModel.class);
		});
	}
}
