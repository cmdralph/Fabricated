package io.github.cmdralph.fabricated.forge;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.serialization.Lifecycle;

import io.github.cmdralph.fabricated.core.metadata.ModIds;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;
import net.minecraftforge.registries.DataPackRegistriesHooks;
import net.minecraftforge.registries.DataPackRegistryEvent;
import net.minecraftforge.registries.RegistryManager;

/**
 * The registries Fabric initializers make through another mod's {@code fabric-registry-sync-v0}: Forgified Fabric
 * API's on Forge 47 hands them to Forge only as Forge makes its own registries, which comes before Fabricated runs the
 * initializers (in Forge's registration events, or in {@code Minecraft}'s constructor on a client), as Sinytra Connector
 * runs them before mod loading. Its {@code FabricRegistryBuilder} adds a registry to the root registry from its hook
 * into Forge's {@code postNewRegistryEvent} (PROVIDER-20), and its {@code DynamicRegistries} hands a data pack registry
 * to Forge from its listener of Forge's {@code DataPackRegistryEvent.NewRegistry} (PROVIDER-21): a registry made later
 * never existed. Fabric's builder adds the registry to the root itself, so one built after Forge made its registries
 * joins the root as it is built; the data pack registries are asked for again once the initializers have run, with a new
 * event to the provider's own mod bus, and those Forge doesn't have yet are added as Forge adds its event's.
 */
public final class ProviderRegistries {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");

	private ProviderRegistries() {
	}

	/**
	 * Called as Fabric's {@code FabricRegistryBuilder.buildAndRegister} returns: adds the registry to the root registry,
	 * as upstream's builder does, where the builder left it out after Forge made its registries. A builder that adds it
	 * itself (upstream's), or one that will once Forge makes them, is left alone.
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void joinRoot(WritableRegistry<?> registry) {
		if (RegistryManager.getVanillaRegistryKeys().isEmpty() || BuiltInRegistries.REGISTRY.containsKey(registry.key().location())) return;
		((WritableRegistry) BuiltInRegistries.REGISTRY).register(registry.key(), registry, Lifecycle.stable());
		LOGGER.info("Added the registry {}, which a Fabric mod built after Forge made its registries, to the root registry: its"
				+ " FabricRegistryBuilder adds one only as Forge makes them (PROVIDER-20)", registry.key().location());
	}

	/**
	 * Asks the mod providing {@code module} for its data pack registries again and adds those Forge doesn't have.
	 *
	 * @param problems where a failure to do so goes (the report)
	 */
	public static void addLateDataPackRegistries(String module, Consumer<String> problems) {
		Optional<? extends ModContainer> provider = ModList.get().getModContainerById(ModIds.toNeoForgeId(module));
		if (provider.isEmpty() || !(provider.get() instanceof FMLModContainer container)) return;
		try {
			Field listField = DataPackRegistryEvent.NewRegistry.class.getDeclaredField("registryDataList");
			listField.setAccessible(true);
			Method process = DataPackRegistryEvent.NewRegistry.class.getDeclaredMethod("process");
			process.setAccessible(true);
			Method loaderData = Class.forName("net.minecraftforge.registries.DataPackRegistryEvent$DataPackRegistryData").getDeclaredMethod("loaderData");
			loaderData.setAccessible(true);

			DataPackRegistryEvent.NewRegistry event = new DataPackRegistryEvent.NewRegistry();
			ModContainer previous = ModLoadingContext.get().getActiveContainer();
			ModLoadingContext.get().setActiveContainer(container);
			try {
				container.getEventBus().post(event);
			} finally {
				ModLoadingContext.get().setActiveContainer(previous);
			}
			List<String> added = new ArrayList<>();
			for (Iterator<?> it = ((List<?>) listField.get(event)).iterator(); it.hasNext();) {
				RegistryDataLoader.RegistryData<?> data = (RegistryDataLoader.RegistryData<?>) loaderData.invoke(it.next());
				if (DataPackRegistriesHooks.getDataPackRegistries().stream().anyMatch(known -> known.key().equals(data.key()))) {
					it.remove();
				} else {
					added.add(data.key().location().toString());
				}
			}
			if (added.isEmpty()) return;
			process.invoke(event);
			LOGGER.info("Added the data pack registries {}, which Fabric initializers made through {}'s {}: it hands them to Forge in an event"
					+ " that fired before them (PROVIDER-21)", added, container.getModInfo().getDisplayName(), module);
		} catch (ReflectiveOperationException | RuntimeException e) {
			Throwable cause = e instanceof InvocationTargetException && e.getCause() != null ? e.getCause() : e;
			String text = "Could not ask " + container.getModInfo().getDisplayName() + "'s " + module + " for the data pack registries Fabric"
					+ " initializers made: a registry a Fabric mod made through it may not exist (PROVIDER-21): " + cause;
			LOGGER.error("{}", text, e);
			problems.accept(text);
		}
	}
}
