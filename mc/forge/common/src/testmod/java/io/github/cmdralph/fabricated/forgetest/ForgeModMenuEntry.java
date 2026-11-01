package io.github.cmdralph.fabricated.forgetest;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * A Mod Menu config screen entrypoint, which Fabricated puts behind the config button of Forge's mod list
 * ({@code ModMenuBridge}); the client smoke checks it is there ({@link ForgeTestClient}).
 */
public final class ForgeModMenuEntry implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> parent;
	}

	/** Client only: whether Forge's mod list has this mod's config screen. */
	static boolean registered() {
		return net.minecraftforge.fml.ModList.get().getModContainerById("fabricated_forge_testmod")
				.flatMap(c -> c.getCustomExtension(net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class)).isPresent();
	}
}
