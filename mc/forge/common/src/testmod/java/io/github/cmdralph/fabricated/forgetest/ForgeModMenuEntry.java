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

	/**
	 * Client only: whether Forge's mod list has this mod's config screen. Forge 66 made {@code ModList}'s methods static,
	 * where Forge 61 to 65 reach them through {@code ModList.get()}, so the container is looked up by reflection.
	 */
	static boolean registered() {
		try {
			Class<?> modList = net.minecraftforge.fml.ModList.class;
			java.lang.reflect.Method byId = modList.getMethod("getModContainerById", String.class);
			Object list = java.lang.reflect.Modifier.isStatic(byId.getModifiers()) ? null : modList.getMethod("get").invoke(null);
			java.util.Optional<?> container = (java.util.Optional<?>) byId.invoke(list, "fabricated_forge_testmod");
			return container.map(c -> (net.minecraftforge.fml.ModContainer) c)
					.flatMap(c -> c.getCustomExtension(net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class)).isPresent();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
