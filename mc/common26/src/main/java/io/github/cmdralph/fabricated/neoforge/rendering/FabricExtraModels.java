package io.github.cmdralph.fabricated.neoforge.rendering;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.resources.model.ModelDiscovery;
import net.minecraft.client.resources.model.ResolvableModel;

/**
 * Adds the extra models Fabric mods registered with {@code fabric-model-loading-api-v1} to the models the game
 * resolves. Upstream's mixin does this in the method NeoForge kept only as a delegate (NeoForge's own overload, which
 * also takes its standalone models, does the work), so it would never run.
 *
 * <p>The module is loaded like any Fabric mod, so its classes are looked up by name, and nothing happens when it is
 * not installed.
 */
public final class FabricExtraModels {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final String DISPATCHER = "net.fabricmc.fabric.impl.client.model.loading.ModelLoadingEventDispatcher";

	/** The dispatcher of the reload running on this thread, or null: {@code () -> Object}. */
	private static final @Nullable MethodHandle CURRENT;
	/** {@code (Object dispatcher) -> Map}. */
	private static final @Nullable MethodHandle EXTRA_MODELS;

	static {
		MethodHandle current = null;
		MethodHandle extraModels = null;
		try {
			Class<?> dispatcher = Class.forName(DISPATCHER, false, FabricExtraModels.class.getClassLoader());
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			current = MethodHandles.filterReturnValue(lookup.findStaticGetter(dispatcher, "CURRENT", ThreadLocal.class),
					lookup.findVirtual(ThreadLocal.class, "get", MethodType.methodType(Object.class)));
			extraModels = lookup.findVirtual(dispatcher, "getExtraModels", MethodType.methodType(Map.class))
					.asType(MethodType.methodType(Map.class, Object.class));
		} catch (ClassNotFoundException e) {
			// fabric-model-loading-api-v1 is not installed
		} catch (ReflectiveOperationException | LinkageError e) {
			LOGGER.error("fabric-model-loading-api-v1 has an unexpected shape; its extra models will not be loaded", e);
			current = null;
		}
		CURRENT = current;
		EXTRA_MODELS = extraModels;
	}

	private FabricExtraModels() {
	}

	public static void addRoots(ModelDiscovery discovery) {
		if (CURRENT == null || EXTRA_MODELS == null) return;
		try {
			Object dispatcher = (Object) CURRENT.invokeExact();
			if (dispatcher == null) return;
			Map<?, ?> models = (Map<?, ?>) EXTRA_MODELS.invokeExact(dispatcher);
			for (Object model : models.values()) discovery.addRoot((ResolvableModel) model);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new IllegalStateException(t);
		}
	}
}
