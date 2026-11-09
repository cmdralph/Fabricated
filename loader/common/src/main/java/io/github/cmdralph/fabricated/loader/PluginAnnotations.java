package io.github.cmdralph.fabricated.loader;

import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.core.translate.Interop;

/**
 * Lets the platform builds of recipe viewers, tooltip mods and Simple Voice Chat find a Fabric mod's plugins: they look
 * for annotations in the platform's scan data, which the platform hands the mod's language loader or container after
 * scanning and before any mod is constructed (the same object they read later). The annotations are core's
 * {@link Interop}, from the mod's entrypoints ({@link FabricatedState#pluginAnnotations}).
 */
public final class PluginAnnotations {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");

	private PluginAnnotations() {
	}

	/**
	 * Adds the plugins' annotations to a mod's scan data, each as the platform's annotation data of a type annotation. A
	 * class that already carries the annotation (a multi-loader mod) was found by the scan, and adding it again would load
	 * the plugin twice.
	 *
	 * @param annotations the scan data's annotations
	 * @param type        an annotation's type, in the platform's data
	 * @param clazz       the class an annotation is on, in the platform's data
	 * @param create      the platform's data of an annotation type on a class
	 */
	public static <A> void add(String modId, List<Interop.Annotation> plugins, Set<A> annotations, Function<A, Type> type, Function<A, Type> clazz,
			BiFunction<Type, Type, A> create) {
		for (Interop.Annotation plugin : plugins) {
			Type annotation = Type.getType(plugin.descriptor());
			Type owner = Type.getObjectType(plugin.className().replace('.', '/'));
			if (annotations.stream().anyMatch(a -> type.apply(a).equals(annotation) && clazz.apply(a).equals(owner))) continue;
			try {
				annotations.add(create.apply(annotation, owner));
			} catch (UnsupportedOperationException e) {
				LOGGER.warn("The platform's scan data of {} can't be extended; its plugins for other mods won't be found", modId);
				return;
			}
		}
	}
}
