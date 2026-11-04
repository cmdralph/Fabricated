package io.github.cmdralph.fabricated.neoforge.registry;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.core.metadata.ModIds;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;

/**
 * The command argument types Fabric initializers register on a client through another mod's {@code fabric-command-api-v2}
 * (PROVIDER-12). Forgified Fabric API's (its 1.21.1, 26.1.2 and 26.2 builds) keeps each one a Fabric mod's
 * {@code ArgumentTypeRegistry.registerArgumentType} names, and registers them only in NeoForge's registration event for
 * argument types, from a listener its mod adds as NeoForge builds it. On a dedicated server Fabricated runs Fabric
 * initializers in an earlier registration event, so that listener registers them; on a client they run in
 * {@code Minecraft}'s constructor, after the registration events, and their argument types were never registered: the
 * integrated server can't send the player a command taking one, and the client lacks an entry a server has. Once a
 * client's initializers have run, with the registries open, those the module holds and the game doesn't have are
 * registered as its listener registers them.
 *
 * <p>Where they are kept is the provider's own, reached by reflection under the names its builds give it. A provider
 * keeping none there registers them its own way, and nothing is done; where the holder is there but its maps aren't, the
 * report says a Fabric mod's argument type may be missing.
 */
public final class ProviderArgumentTypes {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	/** Where Forgified Fabric API keeps them: its 26.x builds, then its 1.21.1 one. */
	private static final List<String> HOLDERS = List.of("net.fabricmc.fabric.impl.command.FabricCommandApiV2",
			"org.sinytra.fabric.command_api.FabricCommandApiV2");

	private ProviderArgumentTypes() {
	}

	/**
	 * Registers the argument types the mod providing {@code module} holds and the game doesn't have, by class and by id.
	 * Call with the registries open.
	 *
	 * @param problems where a failure to do so goes (the report)
	 * @return the ids registered
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static List<String> registerLate(String module, Consumer<String> problems) {
		Optional<? extends ModContainer> provider = ModList.get().getModContainerById(ModIds.toNeoForgeId(module));
		if (provider.isEmpty()) return List.of();
		Class<?> holder = holder();
		if (holder == null) return List.of();
		String name = provider.get().getModInfo().getDisplayName();
		try {
			Map<Class, ArgumentTypeInfo<?, ?>> byClass = (Map<Class, ArgumentTypeInfo<?, ?>>) read(holder, "ARGUMENT_TYPE_CLASSES");
			Map<Identifier, ArgumentTypeInfo<?, ?>> byId = (Map<Identifier, ArgumentTypeInfo<?, ?>>) read(holder, "ARGUMENT_TYPES");
			// As its listener does: by class, every one (the same again for one it registered), then by id those missing.
			byClass.forEach((type, info) -> ArgumentTypeInfos.registerByClass(type, (ArgumentTypeInfo) info));
			List<String> registered = new ArrayList<>();
			byId.forEach((id, info) -> {
				if (BuiltInRegistries.COMMAND_ARGUMENT_TYPE.containsKey(id)) return;
				Registry.register(BuiltInRegistries.COMMAND_ARGUMENT_TYPE, id, info);
				registered.add(id.toString());
			});
			if (!registered.isEmpty()) {
				LOGGER.info("Registered the command argument types {}, which Fabric initializers registered through {}'s {}: it registers them in a"
						+ " registration event that passed before them on a client (PROVIDER-12)", registered, name, module);
			}
			return registered;
		} catch (ReflectiveOperationException | RuntimeException e) {
			String text = "Could not register the command argument types Fabric initializers registered through " + name + "'s " + module
					+ ": a Fabric mod's argument type may be missing on this client, and a command taking it can't reach the player (PROVIDER-12): " + e;
			LOGGER.error("{}", text, e);
			problems.accept(text);
			return List.of();
		}
	}

	private static @Nullable Class<?> holder() {
		ClassLoader loader = ProviderArgumentTypes.class.getClassLoader();
		for (String holder : HOLDERS) {
			try {
				return Class.forName(holder, false, loader);
			} catch (ClassNotFoundException e) {
				// another build's name
			}
		}
		return null;
	}

	private static Map<?, ?> read(Class<?> holder, String field) throws ReflectiveOperationException {
		Field map = holder.getDeclaredField(field);
		map.setAccessible(true);
		return (Map<?, ?>) map.get(null);
	}
}
