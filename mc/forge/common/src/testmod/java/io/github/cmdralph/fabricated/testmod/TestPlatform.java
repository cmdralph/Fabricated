package io.github.cmdralph.fabricated.testmod;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Optional;
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
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.CreativeModeTabRegistry;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;

/**
 * What the test mod's shared checks (mc/shared) ask of the platform they run on, Forge here. Every platform's test mod
 * has this class with these methods, so the checks compile against each platform's.
 */
final class TestPlatform {
	private TestPlatform() {
	}

	/**
	 * Whether the platform loaded a mod of its own with this id (ci/forge/standins.py's): Forge's mod list has the Fabric
	 * mods Fabricated's language loader loads too, Fabric API's modules among them, which aren't.
	 */
	static boolean isModLoaded(String modId) {
		return ((Optional<?>) modList("getModContainerById", modId)).map(ModContainer.class::cast)
				.filter(c -> c.getModInfo().getOwningFile().requiredLanguageLoaders().stream().noneMatch(l -> l.languageName().equals("fabricated")))
				.isPresent();
	}

	/** The creative tabs in the order the platform shows them. */
	static List<CreativeModeTab> sortedCreativeTabs() {
		return CreativeModeTabRegistry.getSortedCreativeModeTabs();
	}

	/** Client only: whether the platform's mod list has a config screen for the mod. */
	static boolean hasConfigScreen(String modId) {
		return ((Optional<?>) modList("getModContainerById", modId)).map(ModContainer.class::cast)
				.flatMap(c -> c.getCustomExtension(ConfigScreenHandler.ConfigScreenFactory.class)).isPresent();
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
		return ForgeEventFactory.isLeftClickBlockCancelled(player, pos, Direction.UP, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
	}

	/** Registers server commands each time the platform builds the command tree. */
	static void registerCommands(Consumer<CommandDispatcher<CommandSourceStack>> commands) {
		RegisterCommandsEvent.BUS.addListener(event -> commands.accept(event.getDispatcher()));
	}

	/** Whether the platform takes the player for a fake one: Forge has no fake players of its own, so Fabric's are the only ones. */
	static boolean isFakePlayer(Player player) {
		return player instanceof FakePlayer;
	}

	/**
	 * Whether the registry's entries are synced to clients: Forge syncs its own registries only, and Fabric API's registry
	 * sync module those with Fabric's synced attribute.
	 */
	static boolean syncs(Registry<?> registry) {
		return RegistryAttributeHolder.get(registry.key()).hasAttribute(RegistryAttribute.SYNCED);
	}

	/** The id an id names in the registry, an alias resolved; null for neither an entry nor an alias. */
	static <T> Identifier resolve(Registry<T> registry, Identifier id) {
		return registry.getOptional(id).map(registry::getKey).orElse(null);
	}

	/**
	 * A method of Forge's mod list: Forge 64 and later (Minecraft 26.x) made them static, where Forge 61 reaches them
	 * through {@code ModList.get()}.
	 */
	private static Object modList(String name, String modId) {
		try {
			Method method = ModList.class.getMethod(name, String.class);
			return method.invoke(Modifier.isStatic(method.getModifiers()) ? null : ModList.class.getMethod("get").invoke(null), modId);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
