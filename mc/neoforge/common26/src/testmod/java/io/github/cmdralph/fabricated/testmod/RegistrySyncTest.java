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
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.RegistryBuilder;

/** 26.x part of the CI test mod: exercises Fabricated's native fabric-registry-sync-v0. */
public final class RegistrySyncTest implements ModInitializer {
	/**
	 * Whether fabric-registry-sync-v0 is Fabricated's. Another mod's (Forgified Fabric API's) is checked for what Fabric
	 * mods need of it: the registry made, its entries and their callback, NeoForge's sync flag. It keeps no attributes in
	 * {@code RegistryAttributeHolder}, and has no aliases on 1.21.1.
	 */
	private static final boolean OWN = !TestMod.providedByAnotherMod("fabric-registry-sync-v0");
	private static final ResourceKey<Registry<String>> CUSTOM = ResourceKey.createRegistryKey(id("custom"));
	private static final ResourceKey<Registry<String>> DYNAMIC = ResourceKey.createRegistryKey(id("dynamic"));
	private static final ResourceKey<Registry<String>> TRACKED = ResourceKey.createRegistryKey(id("tracked"));

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("fabricated_testmod", path);
	}

	@Override
	public void onInitialize() {
		// A registry NeoForge tracks, as Forgified Fabric API's FabricRegistryBuilder has it do: NeoForge refuses one after its
		// NewRegistryEvent, where Fabricated runs initializers (PROVIDER-6). Never added to the root registry: only made.
		try {
			new RegistryBuilder<>(TRACKED).create();
			TestMod.LOGGER.info("FABRICATED-TEST registry sync: tracked registry OK");
		} catch (IllegalStateException e) {
			TestMod.LOGGER.error("FABRICATED-TEST registry sync: tracked registry FAILED: {}", e.getMessage());
		}

		// A new static registry, created and filled from a Fabric initializer.
		MappedRegistry<String> custom = FabricRegistryBuilder.create(CUSTOM).attribute(RegistryAttribute.SYNCED).buildAndRegister();
		AtomicInteger added = new AtomicInteger();
		RegistryEntryAddedCallback.event(custom).register((rawId, id, value) -> added.incrementAndGet());
		Registry.register(custom, id("first"), "first value");
		if (OWN) custom.addAlias(id("old_first"), id("first"));
		boolean customOk = BuiltInRegistries.REGISTRY.containsKey(CUSTOM.identifier())
				&& added.get() == 1
				&& custom.doesSync()
				&& (!OWN || custom instanceof FabricRegistry && RegistryAttributeHolder.get(CUSTOM).hasAttribute(RegistryAttribute.SYNCED));
		if (customOk) {
			TestMod.LOGGER.info("FABRICATED-TEST registry sync: custom registry OK");
			if (!OWN) TestMod.LOGGER.info("FABRICATED-TEST registry sync: attributes and aliases SKIPPED: fabric-registry-sync-v0 is another mod's");
		} else {
			TestMod.LOGGER.error("FABRICATED-TEST registry sync: custom registry FAILED (registered={}, added={}, fabricRegistry={}, synced={})",
					BuiltInRegistries.REGISTRY.containsKey(CUSTOM.identifier()), added.get(), custom instanceof FabricRegistry, custom.doesSync());
		}

		// A synced data-pack registry, loaded from data/fabricated_testmod/fabricated_testmod/dynamic/.
		DynamicRegistries.registerSynced(DYNAMIC, Codec.STRING.fieldOf("message").codec());
		AtomicInteger seen = new AtomicInteger();
		DynamicRegistrySetupCallback.EVENT.register(view -> view.registerEntryAdded(DYNAMIC, (rawId, id, value) -> seen.incrementAndGet()));

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			int entries = server.registryAccess().lookup(DYNAMIC).map(Registry::size).orElse(-1);
			boolean alias = custom.resolve(id("old_first")).equals(id("first"));
			TestMod.LOGGER.info("FABRICATED-TEST registry sync state: alias={}, dynamic={}, setupCallback={}", alias, entries, seen.get());
		});
	}
}
