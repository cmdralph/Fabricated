package io.github.cmdralph.fabricated.neoforge.objectbuilder;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Registry;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Registers a mod's entity data serializer in NeoForge's {@code ENTITY_DATA_SERIALIZERS} registry when it calls
 * vanilla's {@code EntityDataSerializers.registerSerializer} (1.21.1, CORPUS-41). Fabric API 0.116 has no serializer
 * registry, so Fabric mods call vanilla's method, which NeoForge 21.1 refuses for any caller but vanilla's own class.
 * NeoForge's registry is synced by name, so each mod's serializers are named in the order that mod registers them,
 * which is the same on server and client. Vanilla's own calls are left to vanilla, as NeoForge leaves them.
 *
 * <p>Its own class, not the mixin's: vanilla's static initializer registers through the hook before a mixin's static
 * fields would be set.
 */
public final class EntityDataSerializerBridge {
	private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
	private static final Map<String, Integer> COUNTS = new HashMap<>();

	private EntityDataSerializerBridge() {
	}

	/** Called first in {@code registerSerializer}: whether the serializer went to NeoForge's registry instead. */
	public static boolean register(EntityDataSerializer<?> serializer) {
		Class<?> caller = caller();
		if (caller == EntityDataSerializers.class) return false;
		synchronized (COUNTS) {
			if (NeoForgeRegistries.ENTITY_DATA_SERIALIZERS.getKey(serializer) != null) return true;
			String namespace = namespace(caller);
			int index = COUNTS.merge(namespace, 1, Integer::sum) - 1;
			Registry.register(NeoForgeRegistries.ENTITY_DATA_SERIALIZERS,
					ResourceLocation.fromNamespaceAndPath(namespace, "fabricated_serializer_" + index), serializer);
		}
		return true;
	}

	/** The class that called {@code registerSerializer}, as NeoForge's own check sees it. */
	private static @Nullable Class<?> caller() {
		return WALKER.walk(frames -> frames
				.dropWhile(frame -> frame.getDeclaringClass() != EntityDataSerializers.class || !frame.getMethodName().equals("registerSerializer"))
				.skip(1)
				.findFirst()
				.map(StackWalker.StackFrame::getDeclaringClass)
				.orElse(null));
	}

	/** The caller's module name (a Fabric mod's id) as a resource location namespace. */
	private static String namespace(@Nullable Class<?> caller) {
		String module = caller == null ? null : caller.getModule().getName();
		if (module == null) return "fabricated";
		String namespace = module.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
		return ResourceLocation.isValidNamespace(namespace) ? namespace : "fabricated";
	}
}
