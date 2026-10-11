package io.github.cmdralph.fabricated.neoforge.resource;

import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;

/** Version-specific shared reload state: none on 26.3, where recipes and advancements are no longer reload listeners. */
final class ServerReloadExtras {
	private ServerReloadExtras() {
	}

	static void prepare(ReloadableServerResources resources, PreparableReloadListener.SharedState state) {
	}
}
