package io.github.cmdralph.fabricated.neoforge.rendering;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;

/**
 * Hands the extra models Fabric mods registered with {@code fabric-model-loading-api-v1}, once baked, to the model
 * manager, where upstream's {@code getModel} reads them (1.21.11). Upstream's hook takes the baking result from a local
 * at the end of {@code ModelManager.apply}, chosen by its type, and in NeoForge's build Mixin finds no such local there
 * (every client crashed), so the hook is left out (fabric-api-plan.json); NeoForge passes the result to its own bake
 * event in the same method instead.
 *
 * <p>The module is loaded like any Fabric mod, so its members are looked up by name: upstream's {@code BakedModelsHooks}
 * on the baking result, and the field its mixin adds to {@code ModelManager}. Nothing happens when it is not installed.
 */
public final class FabricBakedExtraModels {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final String HOOKS = "net.fabricmc.fabric.impl.client.model.loading.BakedModelsHooks";
	/** The field upstream's {@code ModelManagerMixin} adds, read by its {@code getModel}. */
	private static final String FIELD = "extraModels";

	/** {@code (Object bakingResult) -> Map}. */
	private static final @Nullable MethodHandle EXTRA_MODELS;
	/** {@code (ModelManager, Map) -> void}. */
	private static final @Nullable MethodHandle STORE;

	static {
		MethodHandle extraModels = null;
		MethodHandle store = null;
		try {
			Class<?> hooks = Class.forName(HOOKS, false, FabricBakedExtraModels.class.getClassLoader());
			extraModels = MethodHandles.publicLookup().findVirtual(hooks, "fabric_getExtraModels", MethodType.methodType(Map.class))
					.asType(MethodType.methodType(Map.class, Object.class));
			Field field = ModelManager.class.getDeclaredField(FIELD);
			field.setAccessible(true);
			store = MethodHandles.lookup().unreflectSetter(field).asType(MethodType.methodType(void.class, ModelManager.class, Map.class));
		} catch (ClassNotFoundException e) {
			// fabric-model-loading-api-v1 is not installed
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			LOGGER.error("fabric-model-loading-api-v1 has an unexpected shape; Fabric mods' extra models will not be available", e);
			extraModels = null;
		}
		EXTRA_MODELS = extraModels;
		STORE = store;
	}

	private FabricBakedExtraModels() {
	}

	public static void handOver(ModelManager manager, ModelBakery.BakingResult result) {
		if (EXTRA_MODELS == null || STORE == null) return;
		try {
			STORE.invokeExact(manager, (Map<?, ?>) EXTRA_MODELS.invokeExact((Object) result));
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new IllegalStateException(t);
		}
	}
}
