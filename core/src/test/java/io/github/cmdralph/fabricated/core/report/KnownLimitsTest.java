package io.github.cmdralph.fabricated.core.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.cmdralph.fabricated.core.runtime.EntrypointRunner;

/** The known limits the report explains, matched on the lines of the release's runs. */
class KnownLimitsTest {
	@Test
	void aSpawnCheckHookIsSpawn1OnEveryPlatform() {
		String neoForge = "@Redirect in serverTick expects @At(\"INVOKE\", target=\"Lnet/minecraft/world/entity/Mob;checkSpawnRules("
				+ "Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/EntitySpawnReason;)Z\", ordinal=0), which does not occur in that method";
		String forge47 = "@Redirect in m_151311_ expects @At(\"INVOKE\", target=\"Lnet/minecraft/world/entity/Mob;m_6914_(Lnet/minecraft/world/level/LevelReader;)Z\","
				+ " ordinal=0), which does not occur in that method";
		String limit = KnownLimits.ofFinding("NeoForge", neoForge);
		assertEquals("Known limit: NeoForge and Forge run spawn checks through their own event, so Fabric hooks of those checks can't apply;"
				+ " use the mod's NeoForge or Forge build (SPAWN-1)", limit);
		assertEquals(limit, KnownLimits.ofFinding("Forge", forge47));
	}

	@Test
	void aScreenRenderWrapperIsScreen2OnForgeOnly() {
		String wrapper = "@WrapOperation in extractRenderState expects @At(\"INVOKE\", target=\"Lnet/minecraft/client/gui/screens/Screen;"
				+ "extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V\", ordinal=0), which does not occur in that method";
		assertTrue(KnownLimits.ofFinding("Forge", wrapper).endsWith("(SCREEN-2)"));
		assertNull(KnownLimits.ofFinding("NeoForge", wrapper), "NeoForge's hook takes the screen: a wrapper left out there is left out for another reason");
	}

	@Test
	void equalEntriesRegisteredOnForgeAreReg13() {
		IllegalArgumentException thrown = new IllegalArgumentException("The object PoiType[matchingStates=[], maxTickets=0, validRange=1]{36b858fa} has been"
				+ " registered twice, using the names wover:portal and wover:story_altar. (Other object at this id is PoiType[matchingStates=[], maxTickets=0,"
				+ " validRange=1]{4bef7d97})");
		EntrypointRunner.Failure failure = new EntrypointRunner.Failure("wover_story", "main", "org.example.Story", new RuntimeException("wrapped", thrown));
		assertTrue(failure.describe().endsWith("{4bef7d97}). Known limit: Forge's registries take two equal entries for one, so the mod's second one"
				+ " can't be registered; use NeoForge or the mod's Forge build (REG-13)"), failure.describe());
	}

	@Test
	void anythingElseIsNoKnownLimit() {
		for (String message : List.of(
				"@Redirect in serverTick expects @At(\"INVOKE\", target=\"Lnet/minecraft/world/entity/Mob;finalizeSpawn(Lnet/minecraft/world/level/ServerLevelAccessor;)V\")",
				"@WrapOperation in extractGui expects @At(\"INVOKE\", target=\"Lnet/minecraft/client/gui/screens/Screen;"
						+ "extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V\")")) {
			assertNull(KnownLimits.ofFinding("Forge", message), message);
		}
		assertNull(KnownLimits.ofFailure(new IllegalStateException("has been registered twice, using the names a and b")), "another class");
		assertNull(KnownLimits.ofFailure(new IllegalArgumentException((String) null)));
		assertEquals("m: main entrypoint E threw java.lang.IllegalArgumentException: bad",
				new EntrypointRunner.Failure("m", "main", "E", new IllegalArgumentException("bad")).describe());
	}
}
