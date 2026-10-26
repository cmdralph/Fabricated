package io.github.cmdralph.fabricated.forge.advancement;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.advancements.Advancement;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Fabric API's advancement events (fabric-advancement-api-v1, passed through) for each advancement Forge 66 decodes.
 * Forge's patch decodes the element as an {@link Optional} (its conditional data), which upstream's hook casts to an
 * advancement, stopping the data pack load: the plan leaves that hook out, and Fabricated's
 * ({@code ResourceManagerRegistryLoadTaskMixin}) runs upstream's code on the advancement inside instead. Upstream's
 * classes are there only with Fabric API's jar, so they are looked up, once, when the first advancement loads.
 */
public final class AdvancementCompat {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final String UTIL = "net.fabricmc.fabric.impl.advancement.AdvancementUtil";
	private static final String HOLDER_PROVIDER = "net.fabricmc.fabric.impl.advancement.AdvancementHolderProvider";
	private static final String SOURCE = "net.fabricmc.fabric.api.advancement.v1.AdvancementSource";

	/** Upstream's methods this calls, as its own hook calls them. */
	private record Upstream(Method activeReloadProvider, Method determineSource, Method modifyAdvancement, Constructor<?> holderProvider) {
	}

	/** Without Fabric API's advancement module, or with one whose methods differ. */
	private static final Upstream ABSENT = new Upstream(null, null, null, null);
	private static volatile Upstream upstream;

	private AdvancementCompat() {
	}

	/** A decoded element of the advancement registry, as Forge 66 decodes it or as vanilla does, with Fabric's events run. */
	public static Object modify(Object value, Identifier id, Resource resource, ResourceManager resourceManager, RegistryOps.RegistryInfoLookup lookup) {
		Upstream api = upstream();
		if (api == ABSENT) return value;
		// As upstream: nothing to do outside a reload its other hook has seen start.
		Object reloadRegistries = call(api.activeReloadProvider(), resourceManager);
		if (reloadRegistries == null) return value;
		Object registries = create(api.holderProvider(), lookup, reloadRegistries);
		Object source = call(api.determineSource(), resource);
		if (value instanceof Advancement advancement) return call(api.modifyAdvancement(), id, advancement, source, registries);
		if (value instanceof Optional<?> optional) {
			return optional.map(element -> element instanceof Advancement advancement ? call(api.modifyAdvancement(), id, advancement, source, registries) : element);
		}
		return value;
	}

	private static Upstream upstream() {
		Upstream api = upstream;
		if (api == null) upstream = api = find();
		return api;
	}

	private static Upstream find() {
		ClassLoader loader = AdvancementCompat.class.getClassLoader();
		try {
			Class<?> util = Class.forName(UTIL, false, loader);
			Class<?> source = Class.forName(SOURCE, false, loader);
			return new Upstream(util.getMethod("getActiveReloadProvider", ResourceManager.class),
					util.getMethod("determineSource", Resource.class),
					util.getMethod("modifyAdvancement", Identifier.class, Advancement.class, source, HolderLookup.Provider.class),
					Class.forName(HOLDER_PROVIDER, false, loader).getConstructor(RegistryOps.RegistryInfoLookup.class, HolderLookup.Provider.class));
		} catch (ClassNotFoundException e) {
			return ABSENT;
		} catch (NoSuchMethodException e) {
			LOGGER.warn("Fabric API's advancement events won't run on Forge: its advancement module has no {}", e.getMessage());
			return ABSENT;
		}
	}

	private static Object call(Method method, Object... arguments) {
		try {
			return method.invoke(null, arguments);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Fabric API's advancement events can't run", e);
		} catch (InvocationTargetException e) {
			throw rethrown(e);
		}
	}

	private static Object create(Constructor<?> constructor, Object... arguments) {
		try {
			return constructor.newInstance(arguments);
		} catch (InstantiationException | IllegalAccessException e) {
			throw new IllegalStateException("Fabric API's advancement events can't run", e);
		} catch (InvocationTargetException e) {
			throw rethrown(e);
		}
	}

	/** A listener's failure, which upstream's hook lets through to the data pack load. */
	private static RuntimeException rethrown(InvocationTargetException e) {
		if (e.getCause() instanceof RuntimeException failure) return failure;
		if (e.getCause() instanceof Error failure) throw failure;
		return new IllegalStateException("Fabric API's advancement events failed", e.getCause());
	}
}
