package io.github.cmdralph.fabricated.testmod.plugin;

import io.github.cmdralph.fabricated.testmod.TestMod;
import mcp.mobius.waila.api.ICommonRegistrar;
import mcp.mobius.waila.api.IWailaCommonPlugin;
import mezz.jei.api.IModPlugin;
import me.shedaniel.rei.api.common.plugins.REICommonPlugin;
import net.minecraft.resources.Identifier;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;

/**
 * Plugins for the NeoForge builds of recipe viewers and tooltip mods, declared the Fabric way: JEI, REI and Jade find
 * them through the annotations Fabricated adds for their entrypoints, WTHIT through the jar's {@code waila_plugins.json}.
 * Only loaded when the viewer is installed (CI's viewer run). Each logs when it is constructed and when its viewer
 * calls it.
 */
public final class ViewerPlugins {
	private ViewerPlugins() {
	}

	private static void log(String viewer, String what) {
		TestMod.LOGGER.info("FABRICATED-TEST {} plugin {} OK", viewer, what);
	}

	/** {@code jei_mod_plugin} entrypoint. */
	public static final class Jei implements IModPlugin {
		public Jei() {
			log("jei", "constructed");
		}

		@Override
		public Identifier getPluginUid() {
			log("jei", "called");
			return Identifier.fromNamespaceAndPath("fabricated_testmod", "jei");
		}
	}

	/** {@code rei_common} entrypoint. */
	public static final class Rei implements REICommonPlugin {
		public Rei() {
			log("rei", "constructed");
		}

		/** Overrides REI's default, by name and descriptor. */
		public double getPriority() {
			log("rei", "called");
			return 0;
		}
	}

	/** {@code jade} entrypoint. */
	public static final class Jade implements IWailaPlugin {
		public Jade() {
			log("jade", "constructed");
		}

		@Override
		public void register(IWailaCommonRegistration registration) {
			log("jade", "called");
		}
	}

	/** In {@code waila_plugins.json}. */
	public static final class Wthit implements IWailaCommonPlugin {
		public Wthit() {
			log("wthit", "constructed");
		}

		@Override
		public void register(ICommonRegistrar registrar) {
			log("wthit", "called");
		}
	}
}
