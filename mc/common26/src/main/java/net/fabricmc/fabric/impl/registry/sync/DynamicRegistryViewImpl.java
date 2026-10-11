package net.fabricmc.fabric.impl.registry.sync;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.github.cmdralph.fabricated.neoforge.registry.DynamicRegistrySetup;
import net.fabricmc.fabric.api.event.registry.DynamicRegistryView;
import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;

/**
 * The registries passed to {@code DynamicRegistrySetupCallback}. Entry-added callbacks registered here are handed to
 * {@link DynamicRegistrySetup}, which calls them for the entries already loaded (see there for why).
 */
public final class DynamicRegistryViewImpl implements DynamicRegistryView {
	private final Map<ResourceKey<? extends Registry<?>>, Registry<?>> registries;
	private final Consumer<DynamicRegistrySetup.Recorded<?>> recorder;

	public DynamicRegistryViewImpl(Map<ResourceKey<? extends Registry<?>>, Registry<?>> registries, Consumer<DynamicRegistrySetup.Recorded<?>> recorder) {
		this.registries = registries;
		this.recorder = recorder;
	}

	@Override
	public RegistryAccess asRegistryAccess() {
		// As upstream: a frozen registry access over just these registries.
		return new RegistryAccess.Frozen() {
			@Override
			@SuppressWarnings("unchecked")
			public <T> Optional<Registry<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
				return Optional.ofNullable((Registry<T>) DynamicRegistryViewImpl.this.registries.get(key));
			}

			@Override
			public Stream<RegistryEntry<?>> registries() {
				return DynamicRegistryViewImpl.this.stream().map(this::entry);
			}

			private <T> RegistryEntry<T> entry(Registry<T> registry) {
				return new RegistryEntry<>(registry.key(), registry);
			}

			@Override
			public Frozen freeze() {
				return this;
			}
		};
	}

	@Override
	public Stream<Registry<?>> stream() {
		return this.registries.values().stream();
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Optional<Registry<T>> getOptional(ResourceKey<? extends Registry<? extends T>> registryRef) {
		return Optional.ofNullable((Registry<T>) this.registries.get(registryRef));
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> void registerEntryAdded(ResourceKey<? extends Registry<? extends T>> registryRef, RegistryEntryAddedCallback<T> callback) {
		Registry<T> registry = (Registry<T>) this.registries.get(registryRef);
		if (registry != null) this.recorder.accept(new DynamicRegistrySetup.Recorded<>(registry, callback));
	}
}
