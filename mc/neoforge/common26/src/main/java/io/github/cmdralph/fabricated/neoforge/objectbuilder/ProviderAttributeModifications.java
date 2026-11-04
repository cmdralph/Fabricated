package io.github.cmdralph.fabricated.neoforge.objectbuilder;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.core.metadata.ModIds;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.neoforged.fml.ModList;

/**
 * Fabric's default attribute modification event ({@code FabricDefaultAttributeRegistry.MODIFY}) where another mod
 * provides {@code fabric-object-builder-api-v1} (Forgified Fabric API's 26.1.2 and 26.2 builds, PROVIDER-18). The
 * provider runs the event once, in NeoForge's {@code EntityAttributeModificationEvent} (upstream runs it as the
 * built-in registries freeze); on a client that is before Fabric initializers run in {@code Minecraft}'s constructor
 * (CORPUS-16), so the listeners they register never ran there, where a dedicated server's run them (its initializers
 * run in a registration event before the provider's run).
 *
 * <p>A listener of Fabricated's, registered as it loads, notes which listeners the provider's run has. Once the
 * initializers have run, those registered since run, in the event's order, with the provider's own context, as they
 * would have in its run; where the provider hasn't run the event yet, they are left to its run. The listeners are read
 * from the provider's event and its context made by reflection, under the names its builds give them; where either
 * isn't there, the report says a Fabric mod's attribute modification may be missing.
 */
public final class ProviderAttributeModifications {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	/** The provider's context, which applies a modification to the game's default attributes. */
	private static final String CONTEXT = "net.fabricmc.fabric.impl.object.builder.FabricDefaultAttributeRegistryImpl$ModifyContextImpl";
	/** The listeners the provider's run had (Fabricated's among them), or null before it ran. */
	private static volatile @Nullable List<Object> ran;

	private ProviderAttributeModifications() {
	}

	/**
	 * Registers the listener noting the provider's run. Call as Fabricated loads, before the provider runs the event.
	 *
	 * @param problems where a failure goes (the report)
	 */
	public static void watch(String module, Consumer<String> problems) {
		Event<FabricDefaultAttributeRegistry.ModifyDefaultAttribute> event = FabricDefaultAttributeRegistry.MODIFY;
		try {
			Field handlers = handlers(event);
			event.register(context -> {
				try {
					ran = List.of((Object[]) handlers.get(event));
				} catch (ReflectiveOperationException e) {
					problem(module, problems, e);
				}
			});
		} catch (ReflectiveOperationException | RuntimeException e) {
			problem(module, problems, e);
		}
	}

	/**
	 * Runs the listeners registered after the provider ran the event, if it has. Call once Fabric's initializers have
	 * run.
	 *
	 * @param problems where a failure goes (the report)
	 */
	public static void runLate(String module, Consumer<String> problems) {
		List<Object> before = ran;
		if (before == null) return;
		Event<FabricDefaultAttributeRegistry.ModifyDefaultAttribute> event = FabricDefaultAttributeRegistry.MODIFY;
		List<FabricDefaultAttributeRegistry.ModifyDefaultAttribute> late;
		FabricDefaultAttributeRegistry.ModifyContext context;
		try {
			late = since(before, (Object[]) handlers(event).get(event));
			if (late.isEmpty()) return;
			Constructor<?> constructor = Class.forName(CONTEXT, false, ProviderAttributeModifications.class.getClassLoader()).getDeclaredConstructor();
			constructor.setAccessible(true);
			context = (FabricDefaultAttributeRegistry.ModifyContext) constructor.newInstance();
		} catch (ReflectiveOperationException | RuntimeException e) {
			problem(module, problems, e);
			return;
		}
		// In the event's order, as the provider's run calls them; one that throws is reported with the actions waiting for
		// the initializers, as the provider's run would have stopped there.
		for (FabricDefaultAttributeRegistry.ModifyDefaultAttribute listener : late) listener.modify(context);
		String provider = ModList.get().getModContainerById(ModIds.toNeoForgeId(module)).map(mod -> mod.getModInfo().getDisplayName()).orElse("another mod");
		LOGGER.info("Ran {} default attribute modification(s) that Fabric initializers registered through {}'s {}: it ran its event before"
				+ " them on a client (PROVIDER-18)", late.size(), provider, module);
	}

	/** The listeners in {@code now} that {@code before} doesn't have, each as many times as it was registered again. */
	@SuppressWarnings("unchecked")
	static <T> List<T> since(List<Object> before, Object[] now) {
		Map<Object, Integer> left = new IdentityHashMap<>();
		for (Object listener : before) left.merge(listener, 1, Integer::sum);
		List<T> added = new ArrayList<>();
		for (Object listener : now) {
			Integer count = left.get(listener);
			if (count == null) added.add((T) listener);
			else if (count == 1) left.remove(listener);
			else left.put(listener, count - 1);
		}
		return added;
	}

	/** The event's listeners, in the order it calls them (Fabric API's {@code ArrayBackedEvent.handlers}). */
	private static Field handlers(Event<?> event) throws NoSuchFieldException {
		for (Class<?> type = event.getClass(); type != null && type != Event.class; type = type.getSuperclass()) {
			try {
				Field field = type.getDeclaredField("handlers");
				if (!field.getType().isArray()) break;
				field.setAccessible(true);
				return field;
			} catch (NoSuchFieldException e) {
				// a subclass of the event's implementation
			}
		}
		throw new NoSuchFieldException("no listener array in " + event.getClass().getName() + " (" + Arrays.toString(event.getClass().getDeclaredFields()) + ")");
	}

	private static void problem(String module, Consumer<String> problems, Exception e) {
		String text = "Could not run the default attribute modifications Fabric initializers registered through another mod's " + module
				+ " after its event ran: a Fabric mod's attribute change may be missing on this client (PROVIDER-18): " + e;
		LOGGER.error("{}", text, e);
		problems.accept(text);
	}
}
