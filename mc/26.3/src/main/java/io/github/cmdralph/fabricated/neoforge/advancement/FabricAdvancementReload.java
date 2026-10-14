package io.github.cmdralph.fabricated.neoforge.advancement;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * What {@code fabric-advancement-api-v1}'s reload mixin does around loading the reloadable registries: lets its
 * advancement modifications see the registries being loaded, and fires {@code AdvancementEvents.ALL_LOADED} once the
 * advancements are loaded. The module is upstream's code loaded like any Fabric mod, reached by name; without it the
 * load runs unchanged.
 */
public final class FabricAdvancementReload {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final @Nullable Handles HANDLES = Handles.find();

	private FabricAdvancementReload() {
	}

	private record Handles(MethodHandle start, MethodHandle end, MethodHandle allLoadedEvent, MethodHandle allLoaded) {
		static @Nullable Handles find() {
			ClassLoader loader = FabricAdvancementReload.class.getClassLoader();
			try {
				Class<?> util = Class.forName("net.fabricmc.fabric.impl.advancement.AdvancementUtil", false, loader);
				Class<?> events = Class.forName("net.fabricmc.fabric.api.advancement.v1.AdvancementEvents", false, loader);
				Class<?> loaded = Class.forName("net.fabricmc.fabric.api.advancement.v1.AdvancementEvents$Loaded", false, loader);
				MethodHandles.Lookup lookup = MethodHandles.publicLookup();
				return new Handles(
						lookup.findStatic(util, "startReload", MethodType.methodType(void.class, ResourceManager.class, HolderLookup.Provider.class)),
						lookup.findStatic(util, "endReload", MethodType.methodType(void.class, ResourceManager.class)),
						lookup.findStaticGetter(events, "ALL_LOADED", Event.class),
						lookup.findVirtual(loaded, "onAdvancementsLoaded",
										MethodType.methodType(void.class, ResourceManager.class, Registry.class, HolderLookup.Provider.class))
								.asType(MethodType.methodType(void.class, Object.class, ResourceManager.class, Registry.class, HolderLookup.Provider.class)));
			} catch (ClassNotFoundException e) {
				return null; // fabric-advancement-api-v1 is not installed
			} catch (ReflectiveOperationException | LinkageError e) {
				LOGGER.error("fabric-advancement-api-v1 has an unexpected shape; its reload hooks will not run", e);
				return null;
			}
		}
	}

	public static CompletableFuture<RegistryAccess.Frozen> reload(ResourceManager manager, HolderLookup.Provider provider,
			Supplier<CompletableFuture<RegistryAccess.Frozen>> load) {
		Handles handles = HANDLES;
		if (handles == null) return load.get();
		// Block bodies: an expression lambda would make each invokeExact call site return Object instead of void.
		invoke(() -> {
			handles.start().invokeExact(manager, provider);
		});
		return load.get()
				.thenApply(registries -> {
					invoke(() -> {
						Event<?> event = (Event<?>) handles.allLoadedEvent().invokeExact();
						handles.allLoaded().invokeExact((Object) event.invoker(), manager, (Registry) registries.lookupOrThrow(Registries.ADVANCEMENT),
								(HolderLookup.Provider) registries);
					});
					return registries;
				})
				.whenComplete((registries, error) -> invoke(() -> {
					handles.end().invokeExact(manager);
				}));
	}

	@FunctionalInterface
	private interface Call {
		void run() throws Throwable;
	}

	private static void invoke(Call call) {
		try {
			call.run();
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new IllegalStateException(t);
		}
	}
}
