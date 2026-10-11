package io.github.cmdralph.fabricated.testmod;

import java.lang.reflect.Modifier;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * Fabric test mod used by Fabricated's CI smoke test. Every check logs a {@code FABRICATED-TEST} marker
 * that the CI script looks for in the server log.
 */
public final class TestMod implements ModInitializer, DedicatedServerModInitializer {
	public static final String MOD_ID = "fabricated-testmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final Identifier ITEM_ID = Identifier.fromNamespaceAndPath("fabricated_testmod", "test_item");

	@Override
	public void onInitialize() {
		LOGGER.info("FABRICATED-TEST main entrypoint OK");

		FabricLoader loader = FabricLoader.getInstance();
		ModContainer self = loader.getModContainer(MOD_ID).orElseThrow();
		if (loader.isModLoaded("minecraft") && self.getMetadata().getName().equals("Fabricated Test Mod") && loader.getConfigDir() != null) {
			LOGGER.info("FABRICATED-TEST loader api OK ({} mods visible)", loader.getAllMods().size());
		}

		List<Runnable> custom = loader.getEntrypoints("fabricated-test", Runnable.class);
		custom.forEach(Runnable::run);

		Item item = Registry.register(BuiltInRegistries.ITEM, ITEM_ID, new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, ITEM_ID))));
		if (BuiltInRegistries.ITEM.getKey(item).equals(ITEM_ID)) {
			LOGGER.info("FABRICATED-TEST registry OK");
		}

		Event<Runnable> event = EventFactory.createArrayBacked(Runnable.class, listeners -> () -> {
			for (Runnable listener : listeners) listener.run();
		});
		event.register(() -> LOGGER.info("FABRICATED-TEST fabric api base event OK"));
		event.invoker().run();

		// Native fabric-lifecycle-events-v1: these fire from NeoForge's server events.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> LOGGER.info("FABRICATED-TEST lifecycle server started OK"));
		ServerTickEvents.END_SERVER_TICK.register(new ServerTickEvents.EndTick() {
			private boolean logged;

			@Override
			public void onEndTick(net.minecraft.server.MinecraftServer server) {
				if (!logged) {
					logged = true;
					LOGGER.info("FABRICATED-TEST lifecycle server tick OK");
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LOGGER.info("FABRICATED-TEST lifecycle server stopping OK"));

		try {
			Class<?> widened = Class.forName("net.minecraft.server.MinecraftServer$ReloadableResources");
			if (Modifier.isPublic(widened.getModifiers())) LOGGER.info("FABRICATED-TEST access widener OK");
			else LOGGER.error("FABRICATED-TEST access widener NOT applied");
		} catch (ClassNotFoundException e) {
			LOGGER.error("FABRICATED-TEST access widener target missing", e);
		}
	}

	@Override
	public void onInitializeServer() {
		LOGGER.info("FABRICATED-TEST server entrypoint OK");
	}

	/** Custom entrypoint, only run when the mod asks the loader for it. */
	public static final class CustomEntrypoint implements Runnable {
		@Override
		public void run() {
			LOGGER.info("FABRICATED-TEST custom entrypoint OK");
		}
	}
}
