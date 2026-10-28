package io.github.cmdralph.fabricated.testmod;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleResourceReloadListener;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * 1.21.1 part of the CI test mod: exercises Fabricated's native fabric-resource-loader-v0 (1.21.1 has no v1). The
 * same checks and log lines as later versions' v1 test, but v0 has no data store: the count of data files the
 * listener saw is kept here.
 */
public final class ResourceLoaderTest implements ModInitializer {
	private static final AtomicBoolean FIRST_RAN = new AtomicBoolean();
	private static volatile boolean orderOk;
	private static volatile int files = -1;

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("fabricated_testmod", path);
	}

	/** A synchronous listener with an id and the listeners it runs after. */
	private static IdentifiableResourceReloadListener listener(String path, List<ResourceLocation> after, Runnable action) {
		return new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return id(path);
			}

			@Override
			public Collection<ResourceLocation> getFabricDependencies() {
				return after;
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				action.run();
			}
		};
	}

	@Override
	public void onInitialize() {
		ResourceManagerHelper loader = ResourceManagerHelper.get(PackType.SERVER_DATA);

		// Ordering: "second" is registered first but must run after "first".
		loader.registerReloadListener(listener("second", List.of(id("first")), () -> orderOk = FIRST_RAN.get()));
		loader.registerReloadListener(listener("first", List.of(ResourceReloadListenerKeys.FUNCTIONS), () -> FIRST_RAN.set(true)));

		// A listener built from each reload's registries, reading this mod's data files.
		loader.registerReloadListener(id("data_files"), registries -> new SimpleResourceReloadListener<Map<ResourceLocation, Resource>>() {
			@Override
			public ResourceLocation getFabricId() {
				return id("data_files");
			}

			@Override
			public CompletableFuture<Map<ResourceLocation, Resource>> load(ResourceManager manager, ProfilerFiller profiler, Executor executor) {
				return CompletableFuture.supplyAsync(() -> {
					registries.lookupOrThrow(Registries.ITEM).getOrThrow(ResourceKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace("diamond")));
					return manager.listResources("fabricated_test", path -> path.getPath().endsWith(".json"));
				}, executor);
			}

			@Override
			public CompletableFuture<Void> apply(Map<ResourceLocation, Resource> data, ResourceManager manager, ProfilerFiller profiler, Executor executor) {
				return CompletableFuture.runAsync(() -> {
					files = data.size();
					TestMod.LOGGER.info("FABRICATED-TEST resource reload listener OK ({} data file(s))", data.size());
				}, executor);
			}
		});

		FabricLoader.getInstance().getModContainer("fabricated-testmod").ifPresent(container -> {
			if (ResourceManagerHelper.registerBuiltinResourcePack(id("extra"), container, ResourcePackActivationType.DEFAULT_ENABLED)) {
				TestMod.LOGGER.info("FABRICATED-TEST builtin pack registered");
			}
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			boolean packEnabled = server.getPackRepository().getSelectedIds().contains(id("extra").toString());
			TestMod.LOGGER.info("FABRICATED-TEST resource loader state: order={}, store={}, builtinPackEnabled={}", orderOk, files, packEnabled);
		});
	}
}
