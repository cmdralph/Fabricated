package io.github.cmdralph.fabricated.neoforge.registry;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.core.metadata.ModIds;
import io.github.cmdralph.fabricated.neoforge.GameCompat;
import net.minecraft.resources.RegistryDataLoader;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoadingContext;

/**
 * The data-pack registries Fabric initializers make through another mod's {@code fabric-registry-sync-v0}
 * (PROVIDER-11). Forgified Fabric API's on NeoForge 21.1 keeps each one a Fabric mod's {@code DynamicRegistries} call
 * names, and hands them to NeoForge only in {@code DataPackRegistryEvent.NewRegistry}, which fires before Fabricated
 * runs Fabric initializers (in NeoForge's registration events, or in {@code Minecraft}'s constructor on a client), as
 * Sinytra Connector runs them before mod loading: a registry a Fabric mod made in its initializer was never loaded,
 * nor synced. Once the initializers have run, the module is asked again, with a new event to its own mod bus, and the
 * registries it names that NeoForge doesn't have yet are added as NeoForge adds them from its event. Its 26.x builds
 * hand a late registry to NeoForge themselves, so NeoForge has every one they name.
 *
 * <p>The event, its list and its processing are NeoForge's, reached by reflection: NeoForge 26.3 has no
 * {@code DataPackRegistryEvent} in that package (nor Forgified Fabric API a build for it), and nothing is asked there. Where the event is there but its list or processing
 * isn't, the report says the registries weren't added.
 */
public final class ProviderDataPackRegistries {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final String EVENT_CLASS = "net.neoforged.neoforge.registries.DataPackRegistryEvent$NewRegistry";
	private static final String DATA_CLASS = "net.neoforged.neoforge.registries.DataPackRegistryEvent$DataPackRegistryData";

	private ProviderDataPackRegistries() {
	}

	/**
	 * Asks the mod providing {@code module} for its data-pack registries again and adds those NeoForge doesn't have.
	 *
	 * @param problems where a failure to do so goes (the report)
	 * @return the registries added
	 */
	public static List<String> addLate(String module, Consumer<String> problems) {
		Optional<? extends ModContainer> provider = ModList.get().getModContainerById(ModIds.toNeoForgeId(module));
		if (provider.isEmpty() || provider.get().getEventBus() == null) return List.of();
		ClassLoader loader = ProviderDataPackRegistries.class.getClassLoader();
		Class<?> eventClass;
		try {
			eventClass = Class.forName(EVENT_CLASS, false, loader);
		} catch (ClassNotFoundException e) {
			return List.of(); // not on this NeoForge (26.3)
		}
		try {
			Field listField = eventClass.getDeclaredField("registryDataList");
			listField.setAccessible(true);
			Method process = eventClass.getDeclaredMethod("process");
			process.setAccessible(true);
			Method loaderData = Class.forName(DATA_CLASS, false, loader).getDeclaredMethod("loaderData");
			loaderData.setAccessible(true);
			Constructor<?> constructor = eventClass.getDeclaredConstructor();
			constructor.setAccessible(true);

			Event event = (Event) constructor.newInstance();
			ModContainer previous = ModLoadingContext.get().getActiveContainer();
			ModLoadingContext.get().setActiveContainer(provider.get());
			try {
				provider.get().getEventBus().post(event);
			} finally {
				ModLoadingContext.get().setActiveContainer(previous);
			}
			List<?> named = (List<?>) listField.get(event);
			List<String> added = new ArrayList<>();
			for (var it = named.iterator(); it.hasNext();) {
				RegistryDataLoader.RegistryData<?> data = (RegistryDataLoader.RegistryData<?>) loaderData.invoke(it.next());
				if (NeoForgeRegistryInternals.isDataPackRegistry(data.key())) {
					it.remove();
				} else {
					added.add(GameCompat.location(data.key()).toString());
				}
			}
			if (added.isEmpty()) return added;
			process.invoke(event);
			NeoForgeRegistryInternals.syncLoaderLists();
			LOGGER.info("Added the data-pack registries {}, which Fabric initializers made through {}'s {}: it hands them to NeoForge in an event"
					+ " that fired before them (PROVIDER-11)", added, provider.get().getModInfo().getDisplayName(), module);
			return added;
		} catch (ReflectiveOperationException | RuntimeException e) {
			String text = "Could not ask " + provider.get().getModInfo().getDisplayName() + "'s " + module + " for the data-pack registries Fabric"
					+ " initializers made: a registry a Fabric mod made through it may not exist (PROVIDER-11): " + describe(e);
			LOGGER.error("{}", text, e);
			problems.accept(text);
			return List.of();
		}
	}

	private static String describe(@Nullable Throwable e) {
		Throwable cause = e instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : e;
		return String.valueOf(cause);
	}
}
