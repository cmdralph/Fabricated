package io.github.cmdralph.fabricated.testmod;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Registers content from a client initializer, as some Fabric mods do (Xaero's Minimap registers its effect there):
 * Fabric API freezes the registries only after the client initializers, so they must be open here too.
 */
public final class ClientRegistrationTest implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		try {
			Registry.register(BuiltInRegistries.MOB_EFFECT, Identifier.fromNamespaceAndPath("fabricated_testmod", "client_registered"),
					new MobEffect(MobEffectCategory.NEUTRAL, 0x808080) {
					});
			TestMod.LOGGER.info("FABRICATED-TEST client entrypoint registration OK");
		} catch (IllegalStateException e) {
			TestMod.LOGGER.error("FABRICATED-TEST client entrypoint registration FAILED: {}", e.getMessage());
		}
	}
}
