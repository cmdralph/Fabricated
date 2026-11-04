package io.github.cmdralph.fabricated.forge.resource;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;

/**
 * Fabric mods' built-in resource packs that are enabled by default, on Forge 47 (CORPUS-104). Upstream's
 * {@code fabric-resource-loader-v0} (Fabric API 0.92) enables such a pack the first time the game sees it, and keeps a
 * file of the packs it enabled, {@code data/fabricDefaultResourcePacks.dat}, so that the player's choice holds after.
 * It also always enables the Fabric mods' own pack ("fabric"). It does so in its hook at the return of the options'
 * load ({@code GameOptionsMixin.onLoad}). On Fabric the options load after the Fabric mods' entrypoints, which register
 * those packs. Forge loads them before it loads mods, so the hook found no built-in pack, enabled none, and dropped
 * every pack it had kept from its file.
 *
 * <p>The plan leaves that hook out, and this does the hook's work once the entrypoints have run, as upstream's code
 * does it, with the same file. That is still in Forge's mod loading in {@code Minecraft}'s constructor, before the game
 * selects its packs from the options, so the game selects what it would on Fabric. Fabric API 0.92 has the work only
 * inside the hook (the later {@code fabric-resource-loader-v1} has it in a method of its own, which the {@code forge}
 * branch calls), so this follows it in Forge 47's names. It reads upstream's pack provider, its source of the Fabric
 * mods' own pack and its built-in packs' activation type by reflection, as the in-game mod doesn't compile against
 * Fabric API.
 */
public final class FabricDefaultPacks {
	/** The Fabric API module whose hook this takes over. */
	public static final String MODULE = "fabric-resource-loader-v0";
	private static final String CREATOR = "net.fabricmc.fabric.impl.resource.loader.ModResourcePackCreator";
	private static final String BUILTIN_PACK = "net.fabricmc.fabric.impl.resource.loader.ModNioResourcePack";
	private static final String ACTIVATION_TYPE = "net.fabricmc.fabric.api.resource.ResourcePackActivationType";
	private static final String TRACKER = "fabricDefaultResourcePacks.dat";
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");

	private FabricDefaultPacks() {
	}

	/** Enables the built-in packs enabled by default that the game sees for the first time, where the module is installed. */
	public static void apply() {
		if (!FabricLoader.getInstance().isModLoaded(MODULE)) return;
		RepositorySource provider;
		PackSource fabricMods;
		Class<?> builtinPack;
		Method activationType;
		Method enabledByDefault;
		try {
			ClassLoader loader = FabricDefaultPacks.class.getClassLoader();
			Class<?> creator = Class.forName(CREATOR, true, loader);
			provider = (RepositorySource) creator.getField("CLIENT_RESOURCE_PACK_PROVIDER").get(null);
			fabricMods = (PackSource) creator.getField("RESOURCE_PACK_SOURCE").get(null);
			builtinPack = Class.forName(BUILTIN_PACK, true, loader);
			activationType = builtinPack.getMethod("getActivationType");
			enabledByDefault = Class.forName(ACTIVATION_TYPE, true, loader).getMethod("isEnabledByDefault");
		} catch (ReflectiveOperationException | ClassCastException e) {
			LOGGER.error("{} has an unexpected shape; Fabric mods' built-in packs that are enabled by default are not enabled", MODULE, e);
			return;
		}
		Options options = Minecraft.getInstance().options;
		File dataDir = FabricLoader.getInstance().getGameDir().resolve("data").toFile();
		if (!dataDir.isDirectory() && !dataDir.mkdirs()) LOGGER.warn("{} couldn't create {}", MODULE, dataDir);
		File tracker = new File(dataDir, TRACKER);
		// The packs offered before (upstream's file), those of them not seen this time, and the options' packs.
		Set<String> tracked = read(tracker);
		Set<String> gone = new HashSet<>(tracked);
		Set<String> selected = new LinkedHashSet<>(options.resourcePacks);
		List<Pack> packs = new ArrayList<>();
		provider.loadPacks(packs::add);
		try {
			for (Pack pack : packs) {
				if (pack.getPackSource() == fabricMods) {
					selected.add(pack.getId());
					continue;
				}
				try (PackResources resources = pack.open()) {
					if (builtinPack.isInstance(resources) && (Boolean) enabledByDefault.invoke(activationType.invoke(resources))) {
						if (tracked.add(resources.packId())) {
							selected.add(pack.getId());
						} else {
							gone.remove(resources.packId());
						}
					}
				}
			}
		} catch (InvocationTargetException e) {
			LOGGER.error("{} failed to read a built-in pack's activation type", MODULE, e.getCause());
			return;
		} catch (ReflectiveOperationException | ClassCastException e) {
			LOGGER.error("{} has an unexpected shape; Fabric mods' built-in packs that are enabled by default are not enabled", MODULE, e);
			return;
		}
		tracked.removeAll(gone);
		write(tracker, tracked);
		options.resourcePacks = new ArrayList<>(selected);
	}

	private static Set<String> read(File tracker) {
		Set<String> tracked = new HashSet<>();
		if (!tracker.exists()) return tracked;
		try {
			ListTag values = NbtIo.readCompressed(tracker).getList("values", Tag.TAG_STRING);
			for (int i = 0; i < values.size(); i++) tracked.add(values.getString(i));
		} catch (IOException e) {
			LOGGER.warn("{} couldn't read {}", MODULE, tracker, e);
		}
		return tracked;
	}

	private static void write(File tracker, Set<String> tracked) {
		ListTag values = new ListTag();
		for (String id : tracked) values.add(StringTag.valueOf(id));
		CompoundTag data = new CompoundTag();
		data.put("values", values);
		try {
			NbtIo.writeCompressed(data, tracker);
		} catch (IOException e) {
			LOGGER.warn("{} couldn't write {}", MODULE, tracker, e);
		}
	}
}
