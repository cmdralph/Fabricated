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

/** 1.20.1 and 1.21.1 part of the CI test mod: exercises fabric-registry-sync-v0 (Fabricated's native module on NeoForge 21.1). */
public final class RegistrySyncTest implements ModInitializer {
	/**
	 * Whether fabric-registry-sync-v0 is Fabricated's. Another mod's (Forgified Fabric API's) is checked for what Fabric
	 * mods need of it: the registry made, its entries and their callback, NeoForge's sync flag. It keeps no attributes in
	 * {@code RegistryAttributeHolder}, and has no aliases on 1.20.1 and 1.21.1.
	 */
	private static final boolean OWN = !TestMod.providedByAnotherMod("fabric-registry-sync-v0");
	private static final ResourceKey<Registry<String>> CUSTOM = ResourceKey.createRegistryKey(id("custom"));
	private static final ResourceKey<Registry<String>> DYNAMIC = ResourceKey.createRegistryKey(id("dynamic"));
	private static final ResourceKey<Registry<String>> TRACKED = ResourceKey.createRegistryKey(id("tracked"));

	private static ResourceLocation id(String path) {
		return TestCompat.id("fabricated_testmod", path);
	}

	@Override
	public void onInitialize() {
		// A registry NeoForge tracks, as Forgified Fabric API's FabricRegistryBuilder has it do: NeoForge refuses one after its
		// NewRegistryEvent, where Fabricated runs initializers (PROVIDER-6). A platform that makes no mod's registry that
		// late says so, and there is nothing to check. Never added to the root registry: only made.
		try {
			TestPlatform.createTrackedRegistry(TRACKED);
			TestMod.LOGGER.info("FABRICATED-TEST registry sync: tracked registry OK");
		} catch (UnsupportedOperationException e) {
			TestMod.LOGGER.info("FABRICATED-TEST registry sync: tracked registry SKIPPED: {}", e.getMessage());
		} catch (IllegalStateException e) {
			TestMod.LOGGER.error("FABRICATED-TEST registry sync: tracked registry FAILED: {}", e.getMessage());
		}

		// A new static registry, created and filled from a Fabric initializer.
		MappedRegistry<String> custom = FabricRegistryBuilder.createSimple(CUSTOM).attribute(RegistryAttribute.SYNCED).buildAndRegister();
		AtomicInteger added = new AtomicInteger();
		RegistryEntryAddedCallback.event(custom).register((rawId, id, value) -> added.incrementAndGet());
		Registry.register(custom, id("first"), "first value");
		if (OWN) ((FabricRegistry) custom).addAlias(id("old_first"), id("first"));
		boolean customOk = BuiltInRegistries.REGISTRY.containsKey(CUSTOM.location())
				&& added.get() == 1
				&& TestPlatform.syncs(custom)
				&& (!OWN || custom instanceof FabricRegistry && RegistryAttributeHolder.get(CUSTOM).hasAttribute(RegistryAttribute.SYNCED));
		if (customOk) {
			TestMod.LOGGER.info("FABRICATED-TEST registry sync: custom registry OK");
			if (!OWN) TestMod.LOGGER.info("FABRICATED-TEST registry sync: attributes and aliases SKIPPED: fabric-registry-sync-v0 is another mod's");
		} else {
			TestMod.LOGGER.error("FABRICATED-TEST registry sync: custom registry FAILED (registered={}, added={}, fabricRegistry={}, synced={})",
					BuiltInRegistries.REGISTRY.containsKey(CUSTOM.location()), added.get(), OWN && custom instanceof FabricRegistry, TestPlatform.syncs(custom));
		}

		// A synced data-pack registry, loaded from data/fabricated_testmod/fabricated_testmod/dynamic/.
		DynamicRegistries.registerSynced(DYNAMIC, Codec.STRING.fieldOf("message").codec());
		AtomicInteger seen = new AtomicInteger();
		DynamicRegistrySetupCallback.EVENT.register(view -> view.registerEntryAdded(DYNAMIC, (rawId, id, value) -> seen.incrementAndGet()));

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			int entries = server.registryAccess().registry(DYNAMIC).map(Registry::size).orElse(-1);
			boolean alias = id("first").equals(TestPlatform.resolve(custom, id("old_first")));
			TestMod.LOGGER.info("FABRICATED-TEST registry sync state: alias={}, dynamic={}, setupCallback={}", alias, entries, seen.get());
		});
	}
}
