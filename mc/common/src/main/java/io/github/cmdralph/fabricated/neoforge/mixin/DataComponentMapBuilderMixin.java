package io.github.cmdralph.fabricated.neoforge.mixin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import net.fabricmc.fabric.api.item.v1.FabricComponentMapBuilder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;

/**
 * Makes component map builders Fabric's {@link FabricComponentMapBuilder}. Fabric's {@code get} isn't added: NeoForge
 * already gives the builder the same method. Only adds methods.
 */
@Mixin(DataComponentMap.Builder.class)
abstract class DataComponentMapBuilderMixin implements FabricComponentMapBuilder {
	@Shadow
	@Final
	private Reference2ObjectMap<DataComponentType<?>, Object> map;

	@Shadow
	public abstract <T> DataComponentMap.Builder set(DataComponentType<T> type, @Nullable T value);

	@Override
	@SuppressWarnings("unchecked")
	public <T> T getOrCreate(DataComponentType<T> type, Supplier<T> fallback) {
		if (!this.map.containsKey(type)) {
			this.set(type, Objects.requireNonNull(fallback.get(), "Cannot insert null values to component map builder"));
		}
		return (T) this.map.get(type);
	}

	@Override
	public <T> List<T> getOrEmpty(DataComponentType<List<T>> type) {
		List<T> existing = new ArrayList<>(this.getOrCreate(type, Collections::emptyList));
		this.set(type, existing);
		return existing;
	}

	@Override
	public boolean contains(DataComponentType<?> type) {
		return this.map.containsKey(type);
	}
}
