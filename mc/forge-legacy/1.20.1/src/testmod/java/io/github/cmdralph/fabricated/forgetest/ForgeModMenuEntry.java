package io.github.cmdralph.fabricated.forgetest;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.class_437;

/**
 * A Mod Menu config screen entrypoint, which Fabricated puts behind the config button of Forge's mod list
 * ({@code ModMenuBridge}); the client smoke checks it is there ({@link ForgeTestClient}).
 */
public final class ForgeModMenuEntry implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		// Screen is class_437 in Intermediary's names, which a Fabric mod for 1.20.1 is compiled with.
		return (class_437 parent) -> parent;
	}

	/** Client only: whether Forge's mod list has this mod's config screen. */
	static boolean registered() {
		return net.minecraftforge.fml.ModList.get().getModContainerById("fabricated_forge_testmod")
				.flatMap(c -> c.getCustomExtension(net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class)).isPresent();
	}
}
