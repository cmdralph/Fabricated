package io.github.cmdralph.fabricated.testmod.plugin;

import io.github.cmdralph.fabricated.testmod.TestMod;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import mcp.mobius.waila.api.ICommonRegistrar;
import mcp.mobius.waila.api.IWailaCommonPlugin;
import mezz.jei.api.IModPlugin;
import me.shedaniel.rei.api.common.plugins.REICommonPlugin;
import net.minecraft.resources.Identifier;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;

/**
 * Plugins for the NeoForge builds of other mods (recipe viewers, tooltip mods, Simple Voice Chat), declared the Fabric
 * way: JEI, REI, Jade and Simple Voice Chat find them through the annotations Fabricated adds for their entrypoints,
 * WTHIT through the jar's {@code waila_plugins.json}. Only loaded when that mod is installed (CI's plugin run). Each
 * logs when it is constructed and when its mod calls it.
 */
public final class OtherModPlugins {
	private OtherModPlugins() {
	}

	private static void log(String host, String what) {
		TestMod.LOGGER.info("FABRICATED-TEST {} plugin {} OK", host, what);
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

	/** {@code voicechat} entrypoint. */
	public static final class VoiceChat implements VoicechatPlugin {
		public VoiceChat() {
			log("simple-voice-chat", "constructed");
		}

		@Override
		public String getPluginId() {
			log("simple-voice-chat", "called");
			return "fabricated_testmod";
		}
	}
}
