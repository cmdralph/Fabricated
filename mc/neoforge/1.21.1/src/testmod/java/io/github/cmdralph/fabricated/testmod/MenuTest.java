package io.github.cmdralph.fabricated.testmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;

/**
 * 1.21.1 part of the CI test mod: an extended menu whose opening data reaches the client. The client asks for it
 * once it is in the world (see {@link ClientTestMod}); the server opens it with data 42 and closes it again.
 */
public final class MenuTest implements ModInitializer {
	/** Ping value the client sends to have the menu opened. */
	public static final int OPEN_REQUEST = 7;
	public static final ExtendedScreenHandlerType<ChestMenu, Integer> TYPE = new ExtendedScreenHandlerType<>(MenuTest::clientMenu, ByteBufCodecs.VAR_INT);

	private static ChestMenu clientMenu(int containerId, Inventory inventory, Integer data) {
		if (data == 42) TestMod.LOGGER.info("FABRICATED-TEST extended menu opened with its data OK");
		return menu(containerId, inventory);
	}

	private static ChestMenu menu(int containerId, Inventory inventory) {
		return new ChestMenu(MenuTest.TYPE, containerId, inventory, new SimpleContainer(9), 1);
	}

	static void open(ServerPlayer player) {
		player.openMenu(new ExtendedScreenHandlerFactory<Integer>() {
			@Override
			public Integer getScreenOpeningData(ServerPlayer opener) {
				return 42;
			}

			@Override
			public Component getDisplayName() {
				return Component.literal("Fabricated test menu");
			}

			@Override
			public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player opener) {
				return menu(containerId, inventory);
			}
		});
		player.closeContainer();
	}

	@Override
	public void onInitialize() {
		Registry.register(BuiltInRegistries.MENU, ResourceLocation.fromNamespaceAndPath("fabricated_testmod", "test_menu"), TYPE);
	}
}
