package net.fabricmc.fabric.impl.resource.pack;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackSource;

/**
 * Fabricated's stand-in for upstream's mod resource pack creator. NeoForge itself builds the packs of mod
 * resources (ids {@code mod/<mod ids>}), so only the pack source other Fabric API modules compare against is
 * provided: {@code Resource#getFabricPackSource} reports it for resources from those packs.
 */
public final class ModResourcePackCreator {
	public static final PackSource RESOURCE_PACK_SOURCE = new PackSource() {
		@Override
		public Component decorate(Component packName) {
			return Component.translatable("pack.nameAndSource", packName, Component.translatable("pack.source.fabricmod"));
		}

		@Override
		public boolean shouldAddAutomatically() {
			return true;
		}
	};

	private ModResourcePackCreator() {
	}
}
