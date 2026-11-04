package io.github.cmdralph.fabricated.forge.resource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * Fabric mods' built-in resource packs that are enabled by default, on Forge 61 to 66 (CORPUS-104). Upstream's
 * {@code fabric-resource-loader-v1} enables such a pack the first time the game sees it, and remembers the packs it
 * enabled so that the player's choice holds after. It does so from its hook at the return of the options' load
 * ({@code OptionsMixin.onLoad}, through {@code DefaultResourcePackStorage.process}). On Fabric the options load after the
 * Fabric mods' entrypoints, which register those packs. Forge loads them before it loads mods, so the hook found no
 * built-in pack, enabled none, and forgot every pack it had remembered. It also read the Fabric mods' own packs before
 * their resource condition types were registered, and logged each pack with conditional overlays as unreadable
 * (Artifacts' and Terralith's).
 *
 * <p>The plans leave that hook out. This runs upstream's processing once the entrypoints have run. That is still in
 * Forge's mod loading in {@code Minecraft}'s constructor, before the game selects its packs, so the game selects what it
 * would on Fabric.
 */
public final class FabricDefaultPacks {
	/** The Fabric API module whose hook this takes over. */
	public static final String MODULE = "fabric-resource-loader-v1";
	private static final String STORAGE = "net.fabricmc.fabric.impl.resource.client.DefaultResourcePackStorage";
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");

	private FabricDefaultPacks() {
	}

	/** Runs upstream's processing of the options' packs, where the module is installed. */
	public static void apply() {
		if (!FabricLoader.getInstance().isModLoaded(MODULE)) return;
		Options options = Minecraft.getInstance().options;
		try {
			Method process = Class.forName(STORAGE, true, FabricDefaultPacks.class.getClassLoader()).getMethod("process", Collection.class);
			@SuppressWarnings("unchecked")
			List<String> packs = (List<String>) process.invoke(null, options.resourcePacks);
			options.resourcePacks = packs;
		} catch (InvocationTargetException e) {
			LOGGER.error("{} failed to process the game's resource packs", MODULE, e.getCause());
		} catch (ReflectiveOperationException e) {
			LOGGER.error("{} has an unexpected shape; Fabric mods' built-in packs that are enabled by default are not enabled", MODULE, e);
		}
	}
}
