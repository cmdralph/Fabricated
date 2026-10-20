package io.github.cmdralph.fabricated.testmod;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import net.fabricmc.fabric.api.client.model.loading.v1.UnbakedModelDeserializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;

/**
 * Fabric's model loading API on 1.21.11, which loads there without the renderer API. Only with Fabric API's model
 * loading module installed (the client run with Fabric API); this class names it.
 * <ul>
 * <li>A model file of a Fabric type ({@code models/block/deserializer_check.json}) is read by the deserializer
 * registered here.</li>
 * <li>Once the models are baked, the model manager has Fabric's extra models, where upstream's {@code getModel} reads
 * them: Fabricated hands them over in place of upstream's hook, which can't apply on NeoForge 1.21.11.</li>
 * </ul>
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
		// The models are baked before the player can join a world.
		AtomicBoolean checked = new AtomicBoolean();
		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
			if (checked.getAndSet(true)) return;
			Object extraModels = extraModels(client.getModelManager());
			if (extraModels instanceof Map) TestMod.LOGGER.info("FABRICATED-TEST extra models handed to the model manager OK");
			else TestMod.LOGGER.error("FABRICATED-TEST extra models handed to the model manager FAILED ({})", extraModels);
		});
	}

	/** The extra models upstream's {@code getModel} reads: the field its mixin adds to the model manager. */
	private static Object extraModels(ModelManager manager) {
		try {
			Field field = ModelManager.class.getDeclaredField("extraModels");
			field.setAccessible(true);
			return field.get(manager);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return e;
		}
	}
}
