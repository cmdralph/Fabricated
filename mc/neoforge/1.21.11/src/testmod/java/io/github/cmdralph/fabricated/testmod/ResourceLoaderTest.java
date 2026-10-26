package io.github.cmdralph.fabricated.testmod;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.v1.DataResourceLoader;
import net.fabricmc.fabric.api.resource.v1.DataResourceStore;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys;
import net.fabricmc.fabric.api.resource.v1.FabricResource;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleResourceReloader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** 1.21.11 part of the CI test mod: exercises Fabricated's native fabric-resource-loader-v1. */
public final class ResourceLoaderTest implements ModInitializer {
	private static final DataResourceStore.Key<Integer> FILES = new DataResourceStore.Key<>();
	private static final AtomicBoolean FIRST_RAN = new AtomicBoolean();
	private static volatile boolean orderOk;

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("fabricated_testmod", path);
	}

	@Override
	public void onInitialize() {
		DataResourceLoader loader = DataResourceLoader.get();

		// Ordering: "second" is registered first but must run after "first".
		loader.registerReloader(id("second"), (ResourceManagerReloadListener) manager -> orderOk = FIRST_RAN.get());
		loader.registerReloader(id("first"), (ResourceManagerReloadListener) manager -> FIRST_RAN.set(true));
		loader.addReloaderOrdering(id("first"), id("second"));
		loader.addReloaderOrdering(ResourceReloaderKeys.Server.FUNCTIONS, id("first"));

		// Shared state: registries, feature flags and the data store, plus this mod's data files.
		loader.registerReloader(id("data_files"), new SimpleResourceReloader<Map<Identifier, Resource>>() {
			@Override
			protected Map<Identifier, Resource> prepare(SharedState state) {
				HolderLookup.Provider registries = state.get(ResourceLoader.RELOADER_REGISTRY_LOOKUP_KEY);
				registries.lookupOrThrow(Registries.ITEM).getOrThrow(ResourceKey.create(Registries.ITEM, Identifier.withDefaultNamespace("diamond")));
				state.get(ResourceLoader.RELOADER_FEATURE_SET_KEY);
				return state.resourceManager().listResources("fabricated_test", path -> path.getPath().endsWith(".json"));
			}

			@Override
			protected void apply(Map<Identifier, Resource> files, SharedState state) {
				state.get(DataResourceLoader.DATA_RESOURCE_STORE_KEY).put(FILES, files.size());
				TestMod.LOGGER.info("FABRICATED-TEST resource reload listener OK ({} data file(s), pack source {})",
						files.size(), files.values().stream().findFirst().map(r -> ((FabricResource) r).getFabricPackSource().getClass().getName()).orElse("none"));
			}
		});

		// A listener built from each reload's registries.
		loader.registerReloader(id("factory"), registries -> new PreparableReloadListener() {
			@Override
			public CompletableFuture<Void> reload(SharedState state, Executor prepareExecutor, PreparationBarrier barrier, Executor applyExecutor) {
				registries.lookupOrThrow(Registries.ITEM);
				return barrier.wait(null);
			}
		});

		FabricLoader.getInstance().getModContainer("fabricated-testmod").ifPresent(container -> {
			if (ResourceLoader.registerBuiltinPack(id("extra"), container, PackActivationType.DEFAULT_ENABLED)) {
				TestMod.LOGGER.info("FABRICATED-TEST builtin pack registered");
			}
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			int files = ((DataResourceStore) server).getOrThrow(FILES);
			boolean packEnabled = server.getPackRepository().getSelectedIds().contains(id("extra").toString());
			TestMod.LOGGER.info("FABRICATED-TEST resource loader state: order={}, store={}, builtinPackEnabled={}", orderOk, files, packEnabled);
		});
	}
}
