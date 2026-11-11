package io.github.cmdralph.fabricated.neoforge.resource;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

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

	/**
	 * Decides a JSON data loader's files by their Fabric conditions before another mod's hook reads them (COND-1): drops
	 * each whose conditions fail, as upstream's hook at the start of applying them does, and gives each that loads in
	 * their place a copy without them, which upstream's hook then finds none on and keeps. A mod reading
	 * {@code fabric:load_conditions} again its own way finds none either: Moonlight's NeoForge build for 1.21.1 reads them
	 * as NeoForge's conditions, its {@code all_mods_loaded} asking NeoForge's mod list, which has no Fabric id with a dash
	 * (the NeoForge mod Fabricated makes of a Fabric mod has the id with underscores) nor the Fabric API modules Fabricated
	 * provides, so it dropped files that load on Fabric. A file whose conditions upstream can't parse is left exactly as it
	 * is: upstream keeps it, having decided nothing, and a later reader that can parse them may still drop it. The
	 * objects themselves aren't changed, for a loader whose prepared files outlive a reload. Without
	 * fabric-resource-conditions-api-v1, or where its codec can't be found, nothing changes.
	 *
	 * @param data       the loader's files, by id: one is removed, or replaced by a copy
	 * @param dataType   the loader's folder, for upstream's messages
	 * @param registries the registries upstream gives the conditions (the recipe and advancement loaders'), or null
	 */
	public static void decideFirst(Map<?, ?> data, String dataType, @Nullable Object registries) {
		if (APPLY == null) return;
		@Nullable Codec<?> conditions = Parsed.CODEC;
		if (conditions == null) return;
		for (Iterator<? extends Map.Entry<?, ?>> entries = data.entrySet().iterator(); entries.hasNext(); ) {
			Map.Entry<?, ?> entry = entries.next();
			if (!(entry.getKey() instanceof Identifier id) || !(entry.getValue() instanceof JsonObject json) || !json.has(KEY)) continue;
			// Upstream parses them with this codec and, where it fails, keeps the file as it is.
			if (!conditions.parse(JsonOps.INSTANCE, json.get(KEY)).isSuccess()) continue;
			if (!apply(json, dataType, id, registries)) {
				entries.remove();
				continue;
			}
			JsonObject loads = new JsonObject();
			for (Map.Entry<String, JsonElement> member : json.entrySet()) {
				if (!member.getKey().equals(KEY)) loads.add(member.getKey(), member.getValue());
			}
			@SuppressWarnings("unchecked")
			Map.Entry<Object, Object> file = (Map.Entry<Object, Object>) entry;
			file.setValue(loads);
		}
	}

	/** Upstream's codec of a file's conditions ({@code ResourceCondition.CONDITION_CODEC}), found once asked (1.21.1). */
	private static final class Parsed {
		static final @Nullable Codec<?> CODEC = conditionCodec();

		private static @Nullable Codec<?> conditionCodec() {
			try {
				Class<?> condition = Class.forName("net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition", false,
						FabricResourceConditions.class.getClassLoader());
				return (Codec<?>) MethodHandles.publicLookup().findStaticGetter(condition, "CONDITION_CODEC", Codec.class).invoke();
			} catch (VirtualMachineError e) {
				throw e;
			} catch (Throwable t) {
				LOGGER.error("fabric-resource-conditions-api-v1 has an unexpected shape; its own hook alone decides fabric:load_conditions", t);
				return null;
			}
		}
	}

	private static boolean matches(JsonObject json, DynamicOps<?> ops) {
		@Nullable Object registries = ops instanceof RegistryOps<?> registryOps ? GameCompat.conditionRegistries(registryOps) : null;
		return apply(json, "data", UNKNOWN, registries);
	}

	/** Upstream's evaluation ({@code ResourceConditionsImpl.applyResourceConditions}): whether the object loads. */
	private static boolean apply(JsonObject json, String dataType, Identifier id, @Nullable Object registries) {
		try {
			return (boolean) APPLY.invokeExact(json, dataType, id, registries);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new IllegalStateException(t);
		}
	}
}
