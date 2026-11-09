package io.github.cmdralph.fabricated.testmod;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** A Mod Menu config screen entrypoint, which Fabricated puts behind the platform's mod list config button. */
public final class ModMenuEntry implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> parent;
	}

	/** Client only: whether the platform's mod list has this mod's config screen. */
	static boolean registered() {
		return TestPlatform.hasConfigScreen("fabricated_testmod");
	}
}
