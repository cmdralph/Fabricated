package io.github.cmdralph.fabricated.testmod;

import java.util.concurrent.atomic.AtomicInteger;

import com.mojang.serialization.Codec;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.fabricmc.fabric.api.event.registry.DynamicRegistrySetupCallback;
import net.fabricmc.fabric.api.event.registry.FabricRegistry;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.fabricmc.fabric.api.event.registry.RegistryAttributeHolder;
import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;

/** 1.21.1 part of the CI test mod: exercises Fabricated's native fabric-registry-sync-v0. */
public final class RegistrySyncTest implements ModInitializer {
	private static final ResourceKey<Registry<String>> CUSTOM = ResourceKey.createRegistryKey(id("custom"));
	private static final ResourceKey<Registry<String>> DYNAMIC = ResourceKey.createRegistryKey(id("dynamic"));

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("fabricated_testmod", path);
	}

	@Override
	public void onInitialize() {
		// A new static registry, created and filled from a Fabric initializer.
		MappedRegistry<String> custom = FabricRegistryBuilder.createSimple(CUSTOM).attribute(RegistryAttribute.SYNCED).buildAndRegister();
		AtomicInteger added = new AtomicInteger();
		RegistryEntryAddedCallback.event(custom).register((rawId, id, value) -> added.incrementAndGet());
		Registry.register(custom, id("first"), "first value");
		custom.addAlias(id("old_first"), id("first"));
		boolean customOk = BuiltInRegistries.REGISTRY.containsKey(CUSTOM.location())
				&& added.get() == 1
				&& custom instanceof FabricRegistry
				&& RegistryAttributeHolder.get(CUSTOM).hasAttribute(RegistryAttribute.SYNCED)
				&& custom.doesSync();
		if (customOk) {
			TestMod.LOGGER.info("FABRICATED-TEST registry sync: custom registry OK");
		} else {
			TestMod.LOGGER.error("FABRICATED-TEST registry sync: custom registry FAILED (registered={}, added={}, fabricRegistry={}, synced={})",
					BuiltInRegistries.REGISTRY.containsKey(CUSTOM.location()), added.get(), custom instanceof FabricRegistry, custom.doesSync());
		}

		// A synced data-pack registry, loaded from data/fabricated_testmod/fabricated_testmod/dynamic/.
		DynamicRegistries.registerSynced(DYNAMIC, Codec.STRING.fieldOf("message").codec());
		AtomicInteger seen = new AtomicInteger();
		DynamicRegistrySetupCallback.EVENT.register(view -> view.registerEntryAdded(DYNAMIC, (rawId, id, value) -> seen.incrementAndGet()));

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			int entries = server.registryAccess().registry(DYNAMIC).map(Registry::size).orElse(-1);
			boolean alias = custom.resolve(id("old_first")).equals(id("first"));
			TestMod.LOGGER.info("FABRICATED-TEST registry sync state: alias={}, dynamic={}, setupCallback={}", alias, entries, seen.get());
		});
	}
}
