package io.github.cmdralph.fabricated.testmod;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.jar.JarFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.serialization.Lifecycle;

import io.github.cmdralph.fabricated.api.FabricApiModule;
import io.github.cmdralph.fabricated.api.Fabricated;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Fabric test mod used by Fabricated's CI smoke test. Every check logs a {@code FABRICATED-TEST} marker
 * that the CI script looks for in the server log.
 */
public final class TestMod implements ModInitializer, DedicatedServerModInitializer {
	public static final String MOD_ID = "fabricated-testmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final Identifier ITEM_ID = Identifier.fromNamespaceAndPath("fabricated_testmod", "test_item");
	/**
	 * A namespace no Fabric mod has: content registered under it stands for a NeoForge mod's, since Fabricated's bridges
	 * tell the two apart by namespace.
	 */
	public static final String NEOFORGE_NAMESPACE = "fabricated_neoforge_test";
	/** Set when a pack repository's sources were made immutable (PackRepositoryMixin), as FrozenLib leaves them (PACK-1). */
	public static volatile boolean immutablePackSources;

	/** A registry vanilla's own code adds to the root while mods start, which works on Fabric (REG-3: Wover's). */
	private static void lateRegistry() {
		if (addRegistry("late")) LOGGER.info("FABRICATED-TEST late registry OK");
	}

	/**
	 * Called from {@code RegistryDataLoader}'s class initializer, where Wover creates its registries and registers entries
	 * in vanilla ones (REG-3): NeoForge has frozen them by then, and its registration events haven't opened them yet.
	 */
	public static void earlyRegistryWrites() {
		Identifier stat = Identifier.fromNamespaceAndPath("fabricated_testmod", "early_stat");
		try {
			Registry.register(BuiltInRegistries.CUSTOM_STAT, stat, stat);
			if (addRegistry("early")) LOGGER.info("FABRICATED-TEST early registry writes OK");
		} catch (RuntimeException e) {
			LOGGER.error("FABRICATED-TEST early registry writes FAILED", e);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static boolean addRegistry(String name) {
		ResourceKey<Registry<String>> key = ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("fabricated_testmod", name));
		try {
			Registry.register((Registry) BuiltInRegistries.REGISTRY, key.identifier(), new MappedRegistry<>(key, Lifecycle.stable()));
			return BuiltInRegistries.REGISTRY.containsKey(key.identifier());
		} catch (RuntimeException e) {
			LOGGER.error("FABRICATED-TEST registry {} FAILED", name, e);
			return false;
		}
	}

	/**
	 * RES-1: a file of another jar read through one of the mod's own classes, as Lattice reads Minecraft's version
	 * information: on Fabric the class path has it, on NeoForge only the mod's module, unless Fabricated adapts the read.
	 */
	private static void classResources() {
		try (InputStream in = TestMod.class.getResourceAsStream("/version.json")) {
			if (in != null) LOGGER.info("FABRICATED-TEST class resources OK");
			else LOGGER.error("FABRICATED-TEST class resources FAILED: /version.json not found through the mod's class");
		} catch (IOException e) {
			LOGGER.error("FABRICATED-TEST class resources FAILED", e);
		}
	}

	/**
	 * A classpath scan as Reflections starts one (YUNG's API finds what it registers that way): the directory of a package
	 * from the context class loader, then the classes under it in the jar the URL names (SCAN-1).
	 */
	private static void classpathScan() {
		String pkg = "io/github/cmdralph/fabricated/testmod/mixin";
		URL dir = Thread.currentThread().getContextClassLoader().getResource(pkg);
		String form = dir == null ? "" : dir.toExternalForm();
		int at = form.lastIndexOf(pkg);
		try {
			if (at <= 0) throw new IOException("no directory found: " + dir);
			JarURLConnection connection = (JarURLConnection) URI.create(form.substring(0, at)).toURL().openConnection();
			connection.setUseCaches(false);
			try (JarFile jar = connection.getJarFile()) {
				long classes = jar.stream().filter(e -> e.getName().startsWith(pkg + "/") && e.getName().endsWith(".class")).count();
				if (classes > 0) LOGGER.info("FABRICATED-TEST classpath scan OK ({} classes)", classes);
				else LOGGER.error("FABRICATED-TEST classpath scan FAILED: no classes under {}", form);
			}
		} catch (IOException | RuntimeException e) {
			LOGGER.error("FABRICATED-TEST classpath scan FAILED", e);
		}
	}

	@Override
	public void onInitialize() {
		LOGGER.info("FABRICATED-TEST main entrypoint OK");
		// Environment stripping: the client-only interface is removed on a dedicated server, kept on a client.
		try {
			Class<?> sided = Class.forName("io.github.cmdralph.fabricated.testmod.SidedThing");
			boolean client = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
			if ((sided.getInterfaces().length == 1) == client) LOGGER.info("FABRICATED-TEST environment stripping OK");
			else LOGGER.error("FABRICATED-TEST environment stripping FAILED: interfaces {}", List.of(sided.getInterfaces()));
		} catch (ClassNotFoundException | LinkageError e) {
			LOGGER.error("FABRICATED-TEST environment stripping FAILED", e);
		}
		// Fabricated's public API, and detecting Fabricated through Fabric's API.
		Fabricated.get().ifPresent(api -> api.afterEntrypoints(() -> {
			boolean ok = FabricLoader.getInstance().isModLoaded(Fabricated.MOD_ID) && api.entrypointsComplete()
					&& api.isFabricModLoaded("fabricated-testmod")
					&& api.fabricApiModule("fabric-api-base").map(m -> m.status() == FabricApiModule.Status.NATIVE).orElse(false);
			if (ok) LOGGER.info("FABRICATED-TEST api OK");
			else LOGGER.error("FABRICATED-TEST api FAILED: {}", api.fabricMod("fabricated-testmod"));
			if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT && ModMenuEntry.registered()) {
				LOGGER.info("FABRICATED-TEST modmenu config screen OK");
			}
		}));

		FabricLoader loader = FabricLoader.getInstance();
		ModContainer self = loader.getModContainer(MOD_ID).orElseThrow();
		if (loader.isModLoaded("minecraft") && self.getMetadata().getName().equals("Fabricated Test Mod") && loader.getConfigDir() != null) {
			LOGGER.info("FABRICATED-TEST loader api OK ({} mods visible)", loader.getAllMods().size());
		}

		classpathScan();
		classResources();

		List<Runnable> custom = loader.getEntrypoints("fabricated-test", Runnable.class);
		custom.forEach(Runnable::run);

		Item item = Registry.register(BuiltInRegistries.ITEM, ITEM_ID, new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, ITEM_ID))));
		if (BuiltInRegistries.ITEM.getKey(item).equals(ITEM_ID)) {
			LOGGER.info("FABRICATED-TEST registry OK");
		}
		lateRegistry();

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
		// Damage types in the mod's data and in its built-in pack, under conditions only a NeoForge build would read
		// (neoforge:false), which Fabric ignores (DATA-1, PACK-2).
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			var damageTypes = server.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
			if (damageTypes.containsKey(Identifier.fromNamespaceAndPath("fabricated_testmod", "conditioned"))) {
				LOGGER.info("FABRICATED-TEST data without NeoForge conditions OK");
			}
			if (damageTypes.containsKey(Identifier.fromNamespaceAndPath("fabricated_testmod", "conditioned_builtin"))) {
				LOGGER.info("FABRICATED-TEST built-in pack data without NeoForge conditions OK");
			}
		});
		// NeoForge adds its pack finders to the server's pack repository after it was made: it couldn't start had that
		// failed on the immutable sources the test mod left there (PACK-1).
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (immutablePackSources) LOGGER.info("FABRICATED-TEST immutable pack sources OK");
		});
		// Upstream fires TAGS_LOADED once a data load bound the tags and the items' default components: mods build item
		// stacks in it (Jade, CORPUS-9), which throws while the components aren't bound.
		CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
			if (!client && !Items.SHEARS.getDefaultInstance().isEmpty()) LOGGER.info("FABRICATED-TEST lifecycle tags loaded OK");
		});

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
