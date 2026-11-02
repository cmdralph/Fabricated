package io.github.cmdralph.fabricated.forgetest;

import com.mojang.serialization.Lifecycle;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageType;

/**
 * A Fabric mod's registry entries are stable, as on Fabric (PACK-5): the test mod's damage type whose load conditions
 * hold (loaded with Fabric API's jar or without it), from the pack Forge makes of the test mod's file, must have a stable
 * lifecycle. Vanilla marks the entries of a pack without a known-pack identity experimental, and a world holding one asks
 * the player to confirm experimental settings before it opens, which stopped every client smoke test at world creation.
 */
public final class ForgePackTest {
	private static final ResourceKey<DamageType> ENTRY = ResourceKey.create(Registries.DAMAGE_TYPE,
			Identifier.fromNamespaceAndPath(ForgeTestMod.MOD_ID, "conditions_kept"));

	private ForgePackTest() {
	}

	public static void check(MinecraftServer server) {
		var damageTypes = server.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
		var lifecycle = damageTypes.registrationInfo(ENTRY).map(info -> info.lifecycle());
		if (lifecycle.isPresent() && lifecycle.get().equals(Lifecycle.stable())) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST mod pack data is stable OK");
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST mod pack data is stable FAILED: {} has {}", ENTRY.identifier(),
					lifecycle.map(value -> "the lifecycle " + value).orElse("no registration info"));
		}
	}
}
