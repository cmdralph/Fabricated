package net.fabricmc.fabric.impl.resource.loader;

import net.fabricmc.fabric.api.resource.ModResourcePack;

/**
 * Fabricated's stand-in for upstream's pack of a Fabric mod's resources (1.21.1's package). NeoForge itself builds the
 * packs of mod resources (ids {@code mod/<mod ids>}), Fabric mods' among them, so no pack is one of these: a mod asking
 * {@code pack instanceof ModNioResourcePack}, as Moonlight's {@code FilteredResManagerImpl.isModResourcePack} does to
 * tell mods' packs from the player's, gets {@code false} where it would have found the class missing and stopped the
 * game as resources loaded (CORPUS-44). Such a mod then counts Fabric mods' packs as the player's: what that changes for
 * Moonlight (it fingerprints the packs its dynamic resources' cache depends on) is UNVERIFIED.
 */
public abstract class ModNioResourcePack implements ModResourcePack {
	private ModNioResourcePack() {
	}
}
