package net.fabricmc.fabric.impl.object.builder;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Registry;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Fabric's entity data serializer registry, backed by NeoForge's own {@code ENTITY_DATA_SERIALIZERS} registry. NeoForge
 * keeps its numeric ids in step between server and client, which is what upstream's reordering of Minecraft's table
 * does on Fabric. Registration must happen while registries are open, as in a mod's initializer.
 */
public final class FabricEntityDataRegistryImpl {
	private FabricEntityDataRegistryImpl() {
	}

	public static void register(Identifier id, EntityDataSerializer<?> handler) {
		Objects.requireNonNull(id, "Entity data serializer ID cannot be null!");
		Objects.requireNonNull(handler, "Entity data serializer cannot be null!");
		Registry.register(NeoForgeRegistries.ENTITY_DATA_SERIALIZERS, id, handler);
	}

	public static @Nullable EntityDataSerializer<?> get(Identifier id) {
		Objects.requireNonNull(id, "Entity data serializer ID cannot be null!");
		return NeoForgeRegistries.ENTITY_DATA_SERIALIZERS.getValue(id);
	}

	public static @Nullable Identifier getId(EntityDataSerializer<?> handler) {
		Objects.requireNonNull(handler, "Entity data serializer cannot be null!");
		return NeoForgeRegistries.ENTITY_DATA_SERIALIZERS.getKey(handler);
	}
}
