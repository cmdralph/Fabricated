package io.github.cmdralph.fabricated.testmod;

import java.util.List;
import java.util.function.Consumer;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.CreativeModeTabRegistry;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

/**
 * What the test mod's shared checks (mc/shared) ask of the platform they run on, NeoForge here. Every platform's test mod
 * has this class with these methods, so the checks compile against each platform's.
 */
final class TestPlatform {
	private TestPlatform() {
	}

	/** Whether the platform loaded a mod of its own with this id (Forgified Fabric API's modules, ci/neoforge/standins.py's). */
	static boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}

	/** The creative tabs in the order the platform shows them. */
	static List<CreativeModeTab> sortedCreativeTabs() {
		return CreativeModeTabRegistry.getSortedCreativeModeTabs();
	}

	/** Client only: whether the platform's mod list has a config screen for the mod. */
	static boolean hasConfigScreen(String modId) {
		return ModList.get().getModContainerById(modId).flatMap(c -> c.getCustomExtension(IConfigScreenFactory.class)).isPresent();
	}

	/** Makes a registry the platform tracks, as a mod of the platform's makes one, without adding it to the root registry. */
	static <T> void createTrackedRegistry(ResourceKey<Registry<T>> key) {
		new RegistryBuilder<>(key).create();
	}

	/** Whether a left click starting to break a block, as the platform's hook fires it, was cancelled. */
	static boolean leftClickBlockCancelled(Player player, BlockPos pos) {
		return CommonHooks.onLeftClickBlock(player, pos, Direction.UP, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK).isCanceled();
	}

	/** Registers server commands each time the platform builds the command tree. */
	static void registerCommands(Consumer<CommandDispatcher<CommandSourceStack>> commands) {
		NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> commands.accept(event.getDispatcher()));
	}

	/** Whether the platform takes the player for a fake one. */
	static boolean isFakePlayer(Player player) {
		return player.isFakePlayer();
	}

	/** Whether the registry's entries are synced to clients. */
	static boolean syncs(Registry<?> registry) {
		return registry.doesSync();
	}

	/** The id an id names in the registry, an alias resolved. */
	static <T> Identifier resolve(Registry<T> registry, Identifier id) {
		return registry.resolve(id);
	}
}
