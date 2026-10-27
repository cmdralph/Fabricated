package io.github.cmdralph.fabricated.neoforge.resource;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

import io.github.cmdralph.fabricated.neoforge.GameCompat;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;

/**
 * {@code fabric-resource-conditions-api-v1}'s {@code fabric:load_conditions} on NeoForge. NeoForge decides whether a
 * data file or registry entry loads with its own conditions ({@code neoforge:conditions}), through
 * {@code ConditionalOps.createConditionalCodec}; the codec that returns also skips an object whose Fabric conditions
 * don't match, using upstream's own evaluation (the module is upstream's code, reached by name; without it nothing
 * changes). Upstream's mixins on the code NeoForge rewrote are left out by the Fabric API plan.
 */
public final class FabricResourceConditions {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final String KEY = "fabric:load_conditions";
	private static final Identifier UNKNOWN = Identifier.fromNamespaceAndPath("fabricated", "conditional_data");
	private static final @Nullable MethodHandle APPLY = find();

	private FabricResourceConditions() {
	}

	private static @Nullable MethodHandle find() {
		try {
			Class<?> impl = Class.forName("net.fabricmc.fabric.impl.resource.conditions.ResourceConditionsImpl", false,
					FabricResourceConditions.class.getClassLoader());
			// The registries' type depends on the version (GameCompat): the handle takes them as an Object.
			return MethodHandles.publicLookup().findStatic(impl, "applyResourceConditions",
					MethodType.methodType(boolean.class, JsonObject.class, String.class, Identifier.class, GameCompat.CONDITION_REGISTRIES))
					.asType(MethodType.methodType(boolean.class, JsonObject.class, String.class, Identifier.class, Object.class));
		} catch (ClassNotFoundException e) {
			return null; // fabric-resource-conditions-api-v1 is not installed
		} catch (ReflectiveOperationException | LinkageError e) {
			LOGGER.error("fabric-resource-conditions-api-v1 has an unexpected shape; fabric:load_conditions will be ignored", e);
			return null;
		}
	}

	/** NeoForge's conditional codec, also honouring Fabric's conditions. */
	public static <T> Codec<Optional<T>> wrap(Codec<Optional<T>> neoForge) {
		if (APPLY == null) return neoForge;
		return new Codec<>() {
			@Override
			public <I> DataResult<Pair<Optional<T>, I>> decode(DynamicOps<I> ops, I input) {
				if (input instanceof JsonObject json && json.has(KEY) && !matches(json, ops)) {
					return DataResult.success(Pair.of(Optional.empty(), input));
				}
				return neoForge.decode(ops, input);
			}

			@Override
			public <O> DataResult<O> encode(Optional<T> input, DynamicOps<O> ops, O prefix) {
				return neoForge.encode(input, ops, prefix);
			}

			@Override
			public String toString() {
				return "FabricConditions[" + neoForge + "]";
			}
		};
	}

	private static boolean matches(JsonObject json, DynamicOps<?> ops) {
		@Nullable Object registries = ops instanceof RegistryOps<?> registryOps ? GameCompat.conditionRegistries(registryOps) : null;
		try {
			return (boolean) APPLY.invokeExact(json, "data", UNKNOWN, registries);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new IllegalStateException(t);
		}
	}
}
