package io.github.cmdralph.fabricated.forgetest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.advancement.v1.AdvancementEvents;
import net.fabricmc.fabric.api.advancement.v1.AdvancementSource;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.resources.Identifier;

/**
 * Forge's half of the CI test mod for fabric-advancement-api-v1 on Forge 65 and 66, where Fabricated passes the module
 * through (26.1.2's Fabric API has none): a vanilla advancement's reward, set through {@code AdvancementEvents.MODIFY},
 * must be what the server loaded, with the advancement seen as vanilla's. On Forge 66 the event runs from the in-game
 * mod's hook on Forge's conditional decoding, as upstream's casts what Forge decodes.
 */
public final class ForgeAdvancementTest implements ModInitializer {
	private static final Identifier MINE_STONE = Identifier.withDefaultNamespace("story/mine_stone");
	private static final int EXPERIENCE = 7;
	private static AdvancementSource source;

	@Override
	public void onInitialize() {
		if (!FabricLoader.getInstance().isModLoaded("fabric-advancement-api-v1")) return;
		AdvancementEvents.MODIFY.register((id, builder, source, registries) -> {
			if (!MINE_STONE.equals(id)) return;
			ForgeAdvancementTest.source = source;
			builder.rewards(AdvancementRewards.Builder.experience(EXPERIENCE).build());
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			AdvancementHolder holder = server.getAdvancements().get(MINE_STONE);
			int experience = holder == null ? -1 : holder.value().rewards().experience();
			if (experience == EXPERIENCE && source == AdvancementSource.VANILLA) {
				ForgeTestMod.LOGGER.info("FABRICATED-TEST advancement modify OK");
			} else {
				ForgeTestMod.LOGGER.error("FABRICATED-TEST advancement modify FAILED: {} gives {} experience (MODIFY set {}), its source seen as {}",
						MINE_STONE, holder == null ? "no advancement, so no" : experience, EXPERIENCE, source);
			}
		});
	}
}
