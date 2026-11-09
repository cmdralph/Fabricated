package io.github.cmdralph.fabricated.forgetest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import fuzs.forgeconfigapiport.fabric.api.forge.v4.ForgeConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.forge.v4.ForgeModConfigEvents;
import io.github.cmdralph.fabricated.testmod.TestMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Forge 52's half of the test mod's part for Forge Config API Port's Fabric API, whose Forge half Fabricated's in-game
 * mod provides on Forge 52's own config system (CFG-4), as Forge 47's test mod checks its version for 1.20.1: a common
 * config through the port's registry, by the test mod's Fabric id (whose dashes its Forge id doesn't have), with a
 * listener for the port's loading event. The main entrypoint writes the file with values other than the defaults
 * first, which the spec must read right after registering, as with the port; by the first server tick Forge must have
 * loaded the config once, firing the port's event, and kept the file's values, under the port's file name in the
 * config directory.
 */
public final class ForgeConfigPortTest {
	private static final String FILE = TestMod.MOD_ID + "-common.toml";
	private static final AtomicInteger LOADINGS = new AtomicInteger();
	private static ForgeConfigSpec.BooleanValue enabled;
	private static ForgeConfigSpec.IntValue count;
	private static ModConfig config;
	/** The values right after registering. */
	private static String registered;

	private ForgeConfigPortTest() {
	}

	/** From the main entrypoint, as Fabric mods register their configs. */
	static void register() {
		try {
			Path directory = FabricLoader.getInstance().getConfigDir();
			Files.createDirectories(directory);
			Files.writeString(directory.resolve(FILE), "enabled = false\ncount = 7\n");
			ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
			enabled = builder.define("enabled", true);
			count = builder.defineInRange("count", 3, 0, 10);
			ForgeConfigSpec spec = builder.build();
			ForgeModConfigEvents.loading(TestMod.MOD_ID).register(loaded -> {
				if (FILE.equals(loaded.getFileName())) LOADINGS.incrementAndGet();
			});
			config = ForgeConfigRegistry.INSTANCE.register(TestMod.MOD_ID, ModConfig.Type.COMMON, spec);
			registered = values();
		} catch (IOException | RuntimeException | LinkageError e) {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST config port registration failed", e);
		}
	}

	/** At the first server tick, Forge's config loading done. */
	static void check() {
		if (config == null) return;
		String now = values();
		Path file = FabricLoader.getInstance().getConfigDir().resolve(FILE);
		boolean ok = "false/7".equals(registered) && "false/7".equals(now) && LOADINGS.get() == 1 && FILE.equals(config.getFileName())
				&& config.getConfigData() != null && Files.isRegularFile(file);
		if (ok) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST config port OK");
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST config port FAILED: after registering {}, now {}, loaded {} time(s), file {} ({}), loaded by Forge {}",
					registered, now, LOADINGS.get(), config.getFileName(), Files.isRegularFile(file) ? file : "missing", config.getConfigData() != null);
		}
	}

	private static String values() {
		return enabled.get() + "/" + count.get();
	}
}
