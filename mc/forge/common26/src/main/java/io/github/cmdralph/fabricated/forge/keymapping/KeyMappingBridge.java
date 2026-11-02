package io.github.cmdralph.fabricated.forge.keymapping;

import net.fabricmc.fabric.impl.client.keymapping.KeyMappingRegistryImpl;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;

/**
 * {@code fabric-key-mapping-api-v1} on Forge 64 to 66's client: Fabric mods' key mappings are registered with Forge's
 * key mapping event, where Forge mods register theirs (KEYMAP-3, see {@link KeyMappingRegistryImpl}). Client only: the
 * class names client classes.
 */
public final class KeyMappingBridge {
	/** The Fabric API module this bridge serves on this version. */
	public static final String MODULE = "fabric-key-mapping-api-v1";

	private KeyMappingBridge() {
	}

	public static void register() {
		RegisterKeyMappingsEvent.BUS.addListener(KeyMappingRegistryImpl::register);
	}
}
