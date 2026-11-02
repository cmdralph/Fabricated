package io.github.cmdralph.fabricated.forge.resource;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;

import io.github.cmdralph.fabricated.forge.resource.mixin.RegistryOpsAccessor;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;

/**
 * {@code fabric-resource-conditions-api-v1}'s {@code fabric:load_conditions} for the data pack registries' entries on
 * Forge 61, as NeoForge's in-game mod honours them in NeoForge's conditional codec. Forge 61 decodes each entry through
 * its own conditional decoder ({@code ConditionCodec.wrap}, which gives no entry where Forge's conditions fail), where
 * upstream's hook looks for vanilla's decoding of the entry, so the Fabric API plan leaves that hook out: the decoder
 * Forge wraps also gives no entry where the Fabric conditions fail, by upstream's own evaluation (the module is
 * upstream's code, reached by name; without it nothing changes).
 */
public final class FabricResourceConditions {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final String KEY = "fabric:load_conditions";
	private static final Identifier UNKNOWN = Identifier.fromNamespaceAndPath("fabricated", "conditional_data");
	private static final MethodHandle APPLY = find();

	private FabricResourceConditions() {
	}

	private static MethodHandle find() {
		try {
			Class<?> impl = Class.forName("net.fabricmc.fabric.impl.resource.conditions.ResourceConditionsImpl", false,
					FabricResourceConditions.class.getClassLoader());
			return MethodHandles.publicLookup().findStatic(impl, "applyResourceConditions",
					MethodType.methodType(boolean.class, JsonObject.class, String.class, Identifier.class, RegistryOps.RegistryInfoLookup.class));
		} catch (ClassNotFoundException e) {
			return null; // fabric-resource-conditions-api-v1 is not installed
		} catch (ReflectiveOperationException | LinkageError e) {
			LOGGER.error("fabric-resource-conditions-api-v1 has an unexpected shape; fabric:load_conditions in data pack registries will be ignored", e);
			return null;
		}
	}

	/** Forge's conditional decoder, also honouring Fabric's conditions. */
	public static <T> Decoder<Optional<T>> wrap(Decoder<Optional<T>> forge) {
		if (APPLY == null) return forge;
		return new Decoder<>() {
			@Override
			public <I> DataResult<Pair<Optional<T>, I>> decode(DynamicOps<I> ops, I input) {
				if (input instanceof JsonObject json && json.has(KEY) && !matches(json, ops)) {
					return DataResult.success(Pair.of(Optional.empty(), input));
				}
				return forge.decode(ops, input);
			}

			@Override
			public String toString() {
				return "FabricConditions[" + forge + "]";
			}
		};
	}

	private static boolean matches(JsonObject json, DynamicOps<?> ops) {
		RegistryOps.RegistryInfoLookup registries = ops instanceof RegistryOpsAccessor accessor ? accessor.fabricated$lookupProvider() : null;
		try {
			return (boolean) APPLY.invokeExact(json, "data", UNKNOWN, registries);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new IllegalStateException(t);
		}
	}
}
