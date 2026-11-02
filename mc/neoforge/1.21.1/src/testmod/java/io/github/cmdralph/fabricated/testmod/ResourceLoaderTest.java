package io.github.cmdralph.fabricated.testmod;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleResourceReloadListener;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

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
					// An item tag read through the reload's registries before the reload binds its tags, as Moonlight's fuel
					// data map reads Supplementaries' (CORPUS-50): upstream's lookup has it, bound as the reload ends.
					DataResult<HolderSet<Item>> tag = RegistryCodecs.homogeneousList(Registries.ITEM)
							.parse(RegistryOps.create(JsonOps.INSTANCE, registries), new JsonPrimitive("#fabricated_testmod:removal"));
					if (tag.error().isPresent()) {
						TestMod.LOGGER.error("FABRICATED-TEST resource reload listener FAILED: the reload's registries have no item tag as it runs: {}",
								tag.error().get().message());
						return;
					}
					TestMod.LOGGER.info("FABRICATED-TEST resource reload listener OK ({} data file(s))", data.size());
				}, executor);
			}
		});

		// A data loader of vanilla's JSON kind, which NeoForge 21.1 leaves as vanilla's: with Fabric API's conditions module,
		// a file whose fabric:load_conditions fail isn't among its data, as on Fabric (CORPUS-49: Moonlight's villager
		// trades read Supplementaries' trade for a mod that wasn't installed and stopped the server).
		loader.registerReloadListener(new ConditionedData());

		FabricLoader.getInstance().getModContainer("fabricated-testmod").ifPresent(container -> {
			if (ResourceManagerHelper.registerBuiltinResourcePack(id("extra"), container, ResourcePackActivationType.DEFAULT_ENABLED)) {
				TestMod.LOGGER.info("FABRICATED-TEST builtin pack registered");
			}
			// A resource pack enabled by default: the client switches it on the first time (DefaultResourcePackTest).
			if (!ResourceManagerHelper.registerBuiltinResourcePack(id("extra_assets"), container, ResourcePackActivationType.DEFAULT_ENABLED)) {
				TestMod.LOGGER.error("FABRICATED-TEST builtin resource pack registration FAILED");
			}
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			boolean packEnabled = server.getPackRepository().getSelectedIds().contains(id("extra").toString());
			TestMod.LOGGER.info("FABRICATED-TEST resource loader state: order={}, store={}, builtinPackEnabled={}", orderOk, files, packEnabled);
			customIngredient();
		});
	}

	/**
	 * A Fabric custom ingredient, as a recipe gives one, read by the codec recipes use. NeoForge 21.1 builds
	 * {@code Ingredient}'s codecs without calling the method upstream's hook adds Fabric's custom ingredients at, so
	 * every recipe with one failed to parse (DEPRECATED-2: 225 of the 1.21.1 corpus server's recipes).
	 */
	private static void customIngredient() {
		if (!FabricLoader.getInstance().isModLoaded("fabric-recipe-api-v1")) {
			TestMod.LOGGER.info("FABRICATED-TEST recipe custom ingredient SKIPPED: fabric-recipe-api-v1 isn't installed");
			return;
		}
		JsonElement json = JsonParser.parseString("{\"fabric:type\": \"fabric:any\", \"ingredients\": "
				+ "[{\"item\": \"minecraft:diamond\"}, {\"item\": \"minecraft:emerald\"}]}");
		DataResult<Ingredient> result = Ingredient.CODEC_NONEMPTY.parse(JsonOps.INSTANCE, json);
		Ingredient ingredient = result.result().orElse(null);
		if (ingredient != null && ingredient.test(new ItemStack(Items.DIAMOND)) && ingredient.test(new ItemStack(Items.EMERALD))
				&& !ingredient.test(new ItemStack(Items.STONE))) {
			TestMod.LOGGER.info("FABRICATED-TEST recipe custom ingredient OK");
		} else {
			TestMod.LOGGER.error("FABRICATED-TEST recipe custom ingredient FAILED: {}",
					result.error().map(DataResult.Error::message).orElse("the ingredient doesn't match its items"));
		}
	}

	/** Reads {@code data/fabricated_testmod/fabricated_conditions}: {@code kept} has conditions that hold, {@code dropped} not. */
	private static final class ConditionedData extends SimpleJsonResourceReloadListener implements IdentifiableResourceReloadListener {
		ConditionedData() {
			super(new Gson(), "fabricated_conditions");
		}

		@Override
		public ResourceLocation getFabricId() {
			return id("conditioned_data");
		}

		@Override
		protected void apply(Map<ResourceLocation, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
			if (!FabricLoader.getInstance().isModLoaded("fabric-resource-conditions-api-v1")) {
				TestMod.LOGGER.info("FABRICATED-TEST json loader conditions SKIPPED: fabric-resource-conditions-api-v1 isn't installed");
			} else if (data.containsKey(id("kept")) && !data.containsKey(id("dropped"))) {
				TestMod.LOGGER.info("FABRICATED-TEST json loader conditions OK");
			} else {
				TestMod.LOGGER.error("FABRICATED-TEST json loader conditions FAILED: the loader read {}", data.keySet());
			}
		}
	}
}
