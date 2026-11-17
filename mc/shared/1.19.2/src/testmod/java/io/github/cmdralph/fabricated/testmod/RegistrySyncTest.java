package io.github.cmdralph.fabricated.testmod;

import java.util.concurrent.atomic.AtomicInteger;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.fabricmc.fabric.api.event.registry.RegistryAttributeHolder;
import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;

/**
 * 1.19.2 part of the CI test mod: exercises fabric-registry-sync-v0. Fabric API 0.77 has neither aliases nor mods' data
 * pack registries, which the later versions' part checks too.
 */
public final class RegistrySyncTest implements ModInitializer {
	private static final ResourceKey<Registry<String>> CUSTOM = ResourceKey.createRegistryKey(id("custom"));
	private static final ResourceKey<Registry<String>> TRACKED = ResourceKey.createRegistryKey(id("tracked"));

	private static ResourceLocation id(String path) {
		return TestCompat.id("fabricated_testmod", path);
	}

	@Override
	public void onInitialize() {
		// A registry the platform tracks, as Forgified Fabric API's FabricRegistryBuilder has NeoForge do: NeoForge refuses
		// one after its NewRegistryEvent, where Fabricated runs initializers (PROVIDER-6). A platform that makes no mod's
		// registry that late says so, and there is nothing to check. Never added to the root registry: only made.
		try {
			TestPlatform.createTrackedRegistry(TRACKED);
			TestMod.LOGGER.info("FABRICATED-TEST registry sync: tracked registry OK");
		} catch (UnsupportedOperationException e) {
			TestMod.LOGGER.info("FABRICATED-TEST registry sync: tracked registry SKIPPED: {}", e.getMessage());
		} catch (IllegalStateException e) {
			TestMod.LOGGER.error("FABRICATED-TEST registry sync: tracked registry FAILED: {}", e.getMessage());
		}

		// A new static registry, created and filled from a Fabric initializer.
		MappedRegistry<String> custom = FabricRegistryBuilder.createSimple(String.class, CUSTOM.location()).attribute(RegistryAttribute.SYNCED).buildAndRegister();
		AtomicInteger added = new AtomicInteger();
		RegistryEntryAddedCallback.event(custom).register((rawId, id, value) -> added.incrementAndGet());
		Registry.register(custom, id("first"), "first value");
		boolean customOk = BuiltInRegistries.REGISTRY.containsKey(CUSTOM.location())
				&& added.get() == 1
				&& TestPlatform.syncs(custom)
				&& RegistryAttributeHolder.get(custom).hasAttribute(RegistryAttribute.SYNCED);
		if (customOk) {
			TestMod.LOGGER.info("FABRICATED-TEST registry sync: custom registry OK");
		} else {
			TestMod.LOGGER.error("FABRICATED-TEST registry sync: custom registry FAILED (registered={}, added={}, synced={})",
					BuiltInRegistries.REGISTRY.containsKey(CUSTOM.location()), added.get(), TestPlatform.syncs(custom));
		}
	}
}
