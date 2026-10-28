package io.github.cmdralph.fabricated.testmod;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.CreativeModeTabRegistry;

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
			Registry.register((Registry) BuiltInRegistries.REGISTRY, TestCompat.location(key), new MappedRegistry<>(key, Lifecycle.stable()));
			return BuiltInRegistries.REGISTRY.containsKey(TestCompat.location(key));
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

	/**
	 * GSON-1: vanilla reading a player's stats, as it does when the player joins a world they played before. Its parser is
	 * in a package Gson's module doesn't export ({@code com.google.gson.internal}), which the game's class loader finds
	 * through its fallback, the loader of the launcher's libraries; a Fabric jar's copy of that package mustn't take it
	 * either (the test mods' shade jar has one).
	 */
	private static void vanillaStatsRead(net.minecraft.server.MinecraftServer server) {
		try {
			Path file = Files.createTempFile("fabricated-stats", ".json");
			Files.writeString(file, "{\"stats\":{}}");
			// (MinecraftServer, File) up to 1.21.11, (MinecraftServer, Path) on 26.x.
			for (Constructor<?> constructor : net.minecraft.stats.ServerStatsCounter.class.getConstructors()) {
				if (constructor.getParameterCount() != 2) continue;
				Class<?> type = constructor.getParameterTypes()[1];
				constructor.newInstance(server, type == File.class ? file.toFile() : file);
				LOGGER.info("FABRICATED-TEST vanilla stats read OK");
				return;
			}
			LOGGER.error("FABRICATED-TEST vanilla stats read FAILED: no ServerStatsCounter constructor");
		} catch (java.lang.reflect.InvocationTargetException e) {
			LOGGER.error("FABRICATED-TEST vanilla stats read FAILED: {}", e.getCause().toString());
		} catch (IOException | ReflectiveOperationException | LinkageError e) {
			LOGGER.error("FABRICATED-TEST vanilla stats read FAILED: {}", e.toString());
		}
	}

	@Override
	public void onInitialize() {
		LOGGER.info("FABRICATED-TEST main entrypoint OK");
		// The modules of libraries NeoForge loads without mixin support, whose fields LIB-1's bridges reach by reflection.
		LOGGER.info("FABRICATED-TEST library modules: brigadier {}, joml {}", openness(com.mojang.brigadier.CommandDispatcher.class),
				openness(org.joml.Matrix4f.class));
		// As on Fabric, a client's main initializers run once the game instance exists (CORPUS-16).
		if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
			if (FabricLoader.getInstance().getGameInstance() != null) LOGGER.info("FABRICATED-TEST main entrypoint game instance OK");
			else LOGGER.error("FABRICATED-TEST main entrypoint game instance FAILED: there is none yet");
		}
		// Environment stripping: the client-only interface is removed on a dedicated server, kept on a client.
		try {
			Class<?> sided = Class.forName("io.github.cmdralph.fabricated.testmod.SidedThing");
			boolean client = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
			if ((sided.getInterfaces().length == 1) == client) LOGGER.info("FABRICATED-TEST environment stripping OK");
			else LOGGER.error("FABRICATED-TEST environment stripping FAILED: interfaces {}", List.of(sided.getInterfaces()));
		} catch (ClassNotFoundException | LinkageError e) {
			LOGGER.error("FABRICATED-TEST environment stripping FAILED", e);
		}
		// Fabricated's public API, and detecting Fabricated through Fabric's API. With ci/neoforge/standins.py's mods or
		// Forgified Fabric API, fabric-api-base is another mod's (PROVIDER-1).
		Fabricated.get().ifPresent(api -> api.afterEntrypoints(() -> {
			FabricApiModule.Status base = providedByAnotherMod("fabric-api-base") ? FabricApiModule.Status.PROVIDED_BY_MOD
					: FabricApiModule.Status.NATIVE;
			boolean ok = FabricLoader.getInstance().isModLoaded(Fabricated.MOD_ID) && api.entrypointsComplete()
					&& api.isFabricModLoaded("fabricated-testmod")
					&& api.fabricApiModule("fabric-api-base").map(m -> m.status() == base && m.isAvailable()).orElse(false);
			if (ok) LOGGER.info("FABRICATED-TEST api OK");
			else LOGGER.error("FABRICATED-TEST api FAILED: {}, fabric-api-base {} (expected {})", api.fabricMod("fabricated-testmod"),
					api.fabricApiModule("fabric-api-base"), base);
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
		resourceBundle();

		List<Runnable> custom = loader.getEntrypoints("fabricated-test", Runnable.class);
		custom.forEach(Runnable::run);
		// The entrypoint the mod's mixin plugin added to Fabric Loader's storage, as FrozenLib adds its own (LOADER-4).
		loader.getEntrypoints(AddedEntrypoint.KEY, Runnable.class).forEach(Runnable::run);

		Item item = Registry.register(BuiltInRegistries.ITEM, ITEM_ID, new Item(TestCompat.withId(new Item.Properties(), ResourceKey.create(Registries.ITEM, ITEM_ID))));
		if (BuiltInRegistries.ITEM.getKey(item).equals(ITEM_ID)) {
			LOGGER.info("FABRICATED-TEST registry OK");
		}
		BootstrapContent.register();
		lateRegistry();
		creativeTab(item);

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
		ServerLifecycleEvents.SERVER_STARTED.register(TestMod::vanillaStatsRead);
		// Damage types in the mod's data and in its built-in pack, under conditions only a NeoForge build would read
		// (neoforge:false), which Fabric ignores (DATA-1, PACK-2).
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			var damageTypes = TestCompat.registry(server.registryAccess(), Registries.DAMAGE_TYPE);
			if (damageTypes.containsKey(Identifier.fromNamespaceAndPath("fabricated_testmod", "conditioned"))) {
				LOGGER.info("FABRICATED-TEST data without NeoForge conditions OK");
			}
			Identifier builtin = Identifier.fromNamespaceAndPath("fabricated_testmod", "conditioned_builtin");
			if (damageTypes.containsKey(builtin)) {
				LOGGER.info("FABRICATED-TEST built-in pack data without NeoForge conditions OK");
			}
			// A world whose registries hold an experimental entry asks the player to confirm before it opens (PACK-3).
			if (damageTypes.registrationInfo(ResourceKey.create(Registries.DAMAGE_TYPE, builtin))
					.filter(info -> info.lifecycle().equals(Lifecycle.stable())).isPresent()) {
				LOGGER.info("FABRICATED-TEST built-in pack data is stable OK");
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

	/** Translations loaded with a control, as LuckPerms loads its own: the JDK refuses that from a named module (LOADER-3). */
	private static void resourceBundle() {
		try {
			ResourceBundle bundle = ResourceBundle.getBundle("fabricated_testmod_messages", Locale.ENGLISH,
					ResourceBundle.Control.getControl(ResourceBundle.Control.FORMAT_PROPERTIES));
			if ("hello".equals(bundle.getString("greeting"))) LOGGER.info("FABRICATED-TEST resource bundle with a control OK");
			else LOGGER.error("FABRICATED-TEST resource bundle with a control FAILED: greeting is {}", bundle.getString("greeting"));
		} catch (RuntimeException e) {
			LOGGER.error("FABRICATED-TEST resource bundle with a control FAILED", e);
		}
	}

	/**
	 * A creative tab registered by a main initializer, which runs after NeoForge's registration events on a client
	 * (CORPUS-16): it must still be ordered after the vanilla tabs. Its name would sort it first otherwise.
	 */
	private static void creativeTab(Item item) {
		CreativeModeTab tab = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
				.title(Component.literal("Fabricated test"))
				.icon(() -> new ItemStack(item))
				.displayItems((parameters, output) -> output.accept(item))
				.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath("fabricated_testmod", "aaa_test_tab"), tab);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			List<CreativeModeTab> sorted = CreativeModeTabRegistry.getSortedCreativeModeTabs();
			int index = sorted.indexOf(tab);
			int spawnEggs = sorted.indexOf(TestCompat.value(BuiltInRegistries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("spawn_eggs")));
			if (spawnEggs >= 0 && index > spawnEggs) LOGGER.info("FABRICATED-TEST creative tab order OK");
			else LOGGER.error("FABRICATED-TEST creative tab order FAILED: the test tab is at {}, the spawn eggs at {}", index, spawnEggs);
		});
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

	/** A class's module: unnamed, or its name and whether it opens the class's package to every module. */
	/**
	 * Whether a NeoForge mod provides the Fabric API module (Forgified Fabric API's, or ci/neoforge/standins.py's), which
	 * is then its and not Fabricated's: asked of NeoForge, not of Fabricated.
	 */
	static boolean providedByAnotherMod(String module) {
		return ModList.get().isLoaded(module.replace('-', '_'));
	}

	private static String openness(Class<?> type) {
		Module module = type.getModule();
		if (!module.isNamed()) return "unnamed";
		return module.getName() + (module.isOpen(type.getPackageName()) ? " (open)" : " (not open)");
	}
}
