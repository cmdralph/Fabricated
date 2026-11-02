package io.github.cmdralph.fabricated.neoforge.resource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.core.runtime.DefaultPackTracker;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Switches on the built-in resource packs Fabric mods enable by default ({@code DEFAULT_ENABLED}, and
 * {@code ALWAYS_ENABLED}, which are required anyway) the first time each is offered, as Fabric API does on Fabric;
 * NeoForge has no such default for resource packs. Which packs were offered before is kept in upstream's own file, so a
 * pack the player turned off stays off (see {@link DefaultPackTracker}). Runs once on the client, before the options'
 * pack selection is applied, when the client's pack repository has found every pack (Fabric's initializers, which
 * register them, have run).
 */
public final class DefaultResourcePacks {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");

	private DefaultResourcePacks() {
	}

	/** The player's resource packs, with the default-enabled built-in packs not offered before added last (on top). */
	public static List<String> withNew(List<String> resourcePacks, PackRepository repository) {
		List<String> offered = new ArrayList<>();
		for (Pack pack : repository.getAvailablePacks()) {
			if (pack.getPackSource() instanceof FabricBuiltinPackSource source && source.shouldAddAutomatically()) offered.add(pack.getId());
		}
		Path file = FMLPaths.GAMEDIR.get().resolve(DefaultPackTracker.FILE);
		Set<String> tracked;
		try {
			tracked = DefaultPackTracker.read(file);
		} catch (IOException e) {
			// As upstream: the packs are then offered as new, and switched on again.
			LOGGER.warn("Could not read {}, which records the built-in resource packs already switched on once", file, e);
			tracked = Set.of();
		}
		DefaultPackTracker.Selection selection = DefaultPackTracker.select(offered, tracked);
		if (!selection.tracked().equals(tracked)) {
			try {
				DefaultPackTracker.write(file, selection.tracked());
			} catch (IOException e) {
				LOGGER.warn("Could not write {}: the built-in resource packs switched on now will be again at the next start", file, e);
			}
		}
		if (selection.added().isEmpty()) return resourcePacks;
		List<String> packs = new ArrayList<>(resourcePacks);
		for (String id : selection.added()) {
			if (!packs.contains(id)) packs.add(id);
		}
		LOGGER.info("Switched on the built-in resource packs Fabric mods enable by default: {}", selection.added());
		return packs;
	}
}
