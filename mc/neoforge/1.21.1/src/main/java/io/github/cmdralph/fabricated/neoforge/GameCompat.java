package io.github.cmdralph.fabricated.neoforge;

import java.util.Optional;
import java.util.stream.Stream;

import com.mojang.serialization.Lifecycle;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

/**
 * Game methods Minecraft names differently from one version to another, for the code NeoForge's versions share
 * (mc/neoforge/common). These are 1.21.1's names.
 */
public final class GameCompat {
	private GameCompat() {
	}

	/** A key's id within its registry. */
	public static ResourceLocation location(ResourceKey<?> key) {
		return key.location();
	}

	/** A registry's entries. */
	public static <T> Stream<Holder.Reference<T>> elements(Registry<T> registry) {
		return registry.holders();
	}

	/** A registry's entry with this id, or null. */
	public static <T> T value(Registry<T> registry, ResourceLocation id) {
		return registry.get(id);
	}

	/** Opens a frozen registry for writing again, keeping its tags. */
	public static void unfreeze(MappedRegistry<?> registry) {
		registry.unfreeze();
	}

	/**
	 * Binds a registry's tags as vanilla's bootstrap freeze does before it freezes the registry: nothing to do on 1.21.1,
	 * whose registries bind their tags only when tags load.
	 */
	public static void bindTagsForFreeze(MappedRegistry<?> registry) {
	}

	/** The type of the registries Fabric API 0.116's resource conditions are evaluated with. */
	public static final Class<?> CONDITION_REGISTRIES = HolderLookup.Provider.class;

	/**
	 * The registries a decode with these ops sees, as Fabric's resource conditions take them: those behind the ops, or,
	 * while the data pack registries load (whose ops only look registries up), a view that finds their entries and tags.
	 */
	public static Object conditionRegistries(RegistryOps<?> ops) {
		RegistryOps.RegistryInfoLookup lookup = ops.lookupProvider;
		if (lookup instanceof RegistryOps.HolderLookupAdapter adapter) return adapter.lookupProvider;
		return new HolderLookup.Provider() {
			@Override
			public Stream<ResourceKey<? extends Registry<?>>> listRegistries() {
				return Stream.empty();
			}

			@Override
			public <T> Optional<HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
				return lookup.<T>lookup(key).map(info -> new InfoRegistryLookup<>(key, info));
			}
		};
	}

	/** A registry being loaded, as far as its {@code RegistryInfo} goes: entries and tags by key, not listed. */
	private record InfoRegistryLookup<T>(ResourceKey<? extends Registry<? extends T>> key, RegistryOps.RegistryInfo<T> info)
			implements HolderLookup.RegistryLookup<T> {
		@Override
		public Lifecycle registryLifecycle() {
			return info.elementsLifecycle();
		}

		@Override
		public Stream<Holder.Reference<T>> listElements() {
			return Stream.empty();
		}

		@Override
		public Stream<HolderSet.Named<T>> listTags() {
			return Stream.empty();
		}

		@Override
		public Optional<Holder.Reference<T>> get(ResourceKey<T> id) {
			return info.getter().get(id);
		}

		@Override
		public Optional<HolderSet.Named<T>> get(TagKey<T> tag) {
			return info.getter().get(tag);
		}

		@Override
		public boolean canSerializeIn(HolderOwner<T> owner) {
			return info.owner().canSerializeIn(owner);
		}
	}
}
