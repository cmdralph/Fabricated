package io.github.cmdralph.fabricated.testmod;

import java.util.List;
import java.util.function.Consumer;

import com.mojang.brigadier.CommandDispatcher;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.fabricmc.fabric.api.event.registry.RegistryAttributeHolder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.CreativeModeTabRegistry;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModList;

/**
 * What the test mod's shared checks (mc/shared) ask of the platform they run on, Forge 47 here. Every platform's test mod
 * has this class with these methods, so the checks compile against each platform's.
 */
final class TestPlatform {
	private TestPlatform() {
	}

	/**
	 * Whether the platform loaded a mod of its own with this id (ci/forge-legacy/standins.py's, Forgified Fabric API's):
	 * Forge's mod list has the Fabric mods Fabricated's language loader loads too, Fabric API's modules among them, which
	 * aren't.
	 */
	static boolean isModLoaded(String modId) {
		return ModList.get().getModContainerById(modId)
				.filter(c -> c.getModInfo().getOwningFile().requiredLanguageLoaders().stream().noneMatch(l -> l.languageName().equals("fabricated")))
				.isPresent();
	}

	/** The creative tabs in the order the platform shows them. */
	static List<CreativeModeTab> sortedCreativeTabs() {
		return CreativeModeTabRegistry.getSortedCreativeModeTabs();
	}

	/** Client only: whether the platform's mod list has a config screen for the mod. */
	static boolean hasConfigScreen(String modId) {
		return ModList.get().getModContainerById(modId).flatMap(c -> c.getCustomExtension(ConfigScreenHandler.ConfigScreenFactory.class)).isPresent();
	}

	/**
	 * Forge makes a mod's registries in its NewRegistryEvent only, which it posts before Fabric's initializers run: no
	 * Forge mod makes one later.
	 */
	static <T> void createTrackedRegistry(ResourceKey<Registry<T>> key) {
		throw new IllegalStateException("Forge makes a mod's registries in NewRegistryEvent only, before Fabric's initializers run: " + key);
	}

	/** Whether a left click starting to break a block, as the platform's hook fires it, was cancelled. */
	static boolean leftClickBlockCancelled(Player player, BlockPos pos) {
		return ForgeHooks.onLeftClickBlock(player, pos, Direction.UP, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK).isCanceled();
	}

	/** Registers server commands each time the platform builds the command tree. */
	static void registerCommands(Consumer<CommandDispatcher<CommandSourceStack>> commands) {
		MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> commands.accept(event.getDispatcher()));
	}

	/** Whether the platform takes the player for a fake one: Forge's own fake players, and Fabric's. */
	static boolean isFakePlayer(Player player) {
		return player instanceof FakePlayer || player instanceof net.minecraftforge.common.util.FakePlayer;
	}

	/**
	 * Whether the registry's entries are synced to clients: Forge syncs its own registries only, and Fabric API's registry
	 * sync module those with Fabric's synced attribute.
	 */
	static boolean syncs(Registry<?> registry) {
		return RegistryAttributeHolder.get(registry.key()).hasAttribute(RegistryAttribute.SYNCED);
	}

	/** The id an id names in the registry, an alias resolved; null for neither an entry nor an alias. */
	static <T> ResourceLocation resolve(Registry<T> registry, ResourceLocation id) {
		return registry.getOptional(id).map(registry::getKey).orElse(null);
	}
}
