package io.github.cmdralph.fabricated.forgetest;

import net.fabricmc.fabric.api.advancement.v1.AdvancementEvents;
import net.fabricmc.fabric.api.advancement.v1.AdvancementSource;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

/**
 * The forge test mod's part for fabric-advancement-api-v1 on Forge 65 and 66, where Fabricated passes the module through
 * (with Fabric API's jar only; 26.1.2's has none): a vanilla advancement's reward, set through
 * {@code AdvancementEvents.MODIFY}, must be what the server loaded, with the advancement seen as vanilla's. On Forge 66
 * the event runs from the in-game mod's hook on Forge's conditional decoding, as upstream's casts what Forge decodes.
 */
public final class ForgeAdvancementTest {
	private static final Identifier MINE_STONE = Identifier.withDefaultNamespace("story/mine_stone");
	private static final int EXPERIENCE = 7;
	private static boolean registered;
	private static AdvancementSource source;

	private ForgeAdvancementTest() {
	}

	/** From the main entrypoint, as Fabric mods register their listeners; only with Fabric API's advancement module. */
	public static void register() {
		AdvancementEvents.MODIFY.register((id, builder, source, registries) -> {
			if (!MINE_STONE.equals(id)) return;
			ForgeAdvancementTest.source = source;
			builder.rewards(AdvancementRewards.Builder.experience(EXPERIENCE).build());
		});
		registered = true;
	}

	public static void check(MinecraftServer server) {
		if (!registered) return;
		AdvancementHolder holder = server.getAdvancements().get(MINE_STONE);
		int experience = holder == null ? -1 : holder.value().rewards().experience();
		if (experience == EXPERIENCE && source == AdvancementSource.VANILLA) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST advancement modify OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST advancement modify: {} gives {} experience (MODIFY set {}), its source seen as {}",
					MINE_STONE, holder == null ? "no advancement, so no" : experience, EXPERIENCE, source);
		}
	}
}
