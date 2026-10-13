package io.github.cmdralph.fabricated.testmod;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** A Mod Menu config screen entrypoint, which Fabricated puts behind NeoForge's mod list config button. */
public final class ModMenuEntry implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> parent;
	}

	/** Client only: whether NeoForge's mod list has this mod's config screen. */
	static boolean registered() {
		return net.neoforged.fml.ModList.get().getModContainerById("fabricated_testmod")
				.flatMap(c -> c.getCustomExtension(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class)).isPresent();
	}
}
