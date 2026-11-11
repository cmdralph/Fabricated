package io.github.cmdralph.fabricated.core.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.cmdralph.fabricated.core.mixin.PreflightFinding;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding.Severity;
import io.github.cmdralph.fabricated.core.runtime.EntrypointRunner;

/** The known limits the report explains, matched on the lines of the release's runs. */
class KnownLimitsTest {
	private static final String SPAWN_HOOK = "@Redirect in serverTick expects @At(\"INVOKE\", target=\"Lnet/minecraft/world/entity/Mob;checkSpawnRules("
			+ "Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/EntitySpawnReason;)Z\", ordinal=0), which does not occur in that method";

	private static PreflightFinding finding(Severity severity, String message) {
		return new PreflightFinding(severity, "mod.mixins.json", "com.example.mixin.SpawnerMixin", message, "the mod needs an update", "handler()V");
	}

	private static String registeredTwice(String hash, String otherHash) {
		return "The object PoiType[matchingStates=[], maxTickets=0, validRange=1]{" + hash + "} has been registered twice, using the names"
				+ " wover:portal and wover:story_altar. (Other object at this id is PoiType[matchingStates=[], maxTickets=0, validRange=1]{" + otherHash + "})";
	}

	@Test
	void aSpawnCheckHookIsSpawn1OnEveryPlatform() {
		String forge47 = "@Redirect in m_151311_ expects @At(\"INVOKE\", target=\"Lnet/minecraft/world/entity/Mob;m_6914_(Lnet/minecraft/world/level/LevelReader;)Z\","
				+ " ordinal=0), which does not occur in that method";
		String limit = KnownLimits.ofFinding("NeoForge", finding(Severity.LEFT_OUT, SPAWN_HOOK));
		assertEquals("Known limit: NeoForge and Forge run spawn checks through their own event, so Fabric hooks of those checks can't apply;"
				+ " use the mod's NeoForge or Forge build (SPAWN-1)", limit);
		assertEquals(limit, KnownLimits.ofFinding("Forge", finding(Severity.LEFT_OUT, forge47)));
		assertEquals(limit, KnownLimits.ofFinding("Forge", finding(Severity.FAIL, SPAWN_HOOK)), "the hook disables its mod");
	}

	/** A @Group's member that can't apply is a warning, whether or not its group applies: only the group's failure leaves hooks out. */
	@Test
	void aWarningIsNoKnownLimit() {
		assertNull(KnownLimits.ofFinding("NeoForge", finding(Severity.WARN, SPAWN_HOOK)));
		assertNull(KnownLimits.ofFinding("NeoForge", finding(Severity.INFO, SPAWN_HOOK)));
	}

	@Test
	void aScreenRenderWrapperIsScreen2OnForgeOnly() {
		PreflightFinding wrapper = finding(Severity.LEFT_OUT, "@WrapOperation in extractRenderState expects @At(\"INVOKE\", target=\"Lnet/minecraft/client/gui/screens/Screen;"
				+ "extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V\", ordinal=0), which does not occur in that method");
		assertTrue(KnownLimits.ofFinding("Forge", wrapper).endsWith("(SCREEN-2)"));
		assertNull(KnownLimits.ofFinding("NeoForge", wrapper), "NeoForge's hook takes the screen: a wrapper left out there is left out for another reason");
	}

	@Test
	void aHookOfTheServersPlayerWillDestroyCallIsSeam3OnForgeOnly() {
		String message = "@ModifyExpressionValue in m_9280_ expects @At(\"INVOKE\", target=\"Lnet/minecraft/world/level/block/Block;"
				+ "m_5707_(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;"
				+ "Lnet/minecraft/world/entity/player/Player;)V\"), which does not occur in that method";
		// Pre-flight's hint names the server's class, where the call isn't put back.
		PreflightFinding forge47 = new PreflightFinding(Severity.LEFT_OUT, "mod.mixins.json", "com.example.mixin.BreakMixin", message,
				"Forge's ServerPlayerGameMode makes that call through the block's IForgeBlock.onDestroyedByPlayer instead, which Fabricated doesn't"
						+ " put back there (SEAM-3).", "handler()V");
		assertTrue(KnownLimits.ofFinding("Forge", forge47).endsWith("(SEAM-3)"), KnownLimits.ofFinding("Forge", forge47));
		assertNull(KnownLimits.ofFinding("NeoForge", forge47), "NeoForge keeps the call in destroyBlock: a hook left out there is left out for another reason");
		// The client's destroyBlock (Forge 52 on) left out for another reason, where the call goes back, is none.
		assertNull(KnownLimits.ofFinding("Forge", finding(Severity.LEFT_OUT, message.replace("m_9280_", "destroyBlock").replace("m_5707_", "playerWillDestroy"))));
	}

	@Test
	void twoEqualEntriesRegisteredOnForgeAreReg13() {
		IllegalArgumentException thrown = new IllegalArgumentException(registeredTwice("36b858fa", "4bef7d97"));
		EntrypointRunner.Failure failure = new EntrypointRunner.Failure("wover_story", "main", "org.example.Story", new RuntimeException("wrapped", thrown));
		assertTrue(failure.describe().endsWith("{4bef7d97}). Known limit: Forge's registries take two equal entries for one, so the mod's second one"
				+ " can't be registered; use NeoForge or the mod's Forge build (REG-13)"), failure.describe());
	}

	/** One object registered under two names has the same identity hash twice: the mod's own mistake, which the hint would hide. */
	@Test
	void oneObjectRegisteredTwiceIsNotReg13() {
		assertNull(KnownLimits.ofFailure(new IllegalArgumentException(registeredTwice("36b858fa", "36b858fa"))));
	}

	@Test
	void anythingElseIsNoKnownLimit() {
		for (String message : List.of(
				"@Redirect in serverTick expects @At(\"INVOKE\", target=\"Lnet/minecraft/world/entity/Mob;finalizeSpawn(Lnet/minecraft/world/level/ServerLevelAccessor;)V\")",
				"@WrapOperation in extractGui expects @At(\"INVOKE\", target=\"Lnet/minecraft/client/gui/screens/Screen;"
						+ "extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V\")")) {
			assertNull(KnownLimits.ofFinding("Forge", finding(Severity.LEFT_OUT, message)), message);
		}
		assertNull(KnownLimits.ofFailure(new IllegalStateException(registeredTwice("1", "2"))), "another class");
		assertNull(KnownLimits.ofFailure(new IllegalArgumentException("The name wover:portal has been registered twice, for a and b.")));
		assertNull(KnownLimits.ofFailure(new IllegalArgumentException((String) null)));
		assertEquals("m: main entrypoint E threw java.lang.IllegalArgumentException: bad",
				new EntrypointRunner.Failure("m", "main", "E", new IllegalArgumentException("bad")).describe());
	}
}
