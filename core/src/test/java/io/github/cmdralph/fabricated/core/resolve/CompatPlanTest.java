package io.github.cmdralph.fabricated.core.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.cmdralph.fabricated.core.json.JsonException;

class CompatPlanTest {
	@Test
	void parsesWhatIsKnownAboutAMod() throws Exception {
		CompatPlan plan = CompatPlan.parse("{\"schema\":1,\"mods\":{\"shaders\":{\"neoForgeBuildOnly\":\"replaces the pipeline\","
				+ "\"notWithNeoForge\":{\"renderer\":\"hooks the Fabric build's classes\"}},"
				+ "\"loader-stub\":{\"neoForgeBuildOnly\":\"downloads the mod at launch\",\"suggestion\":\"Remove it.\",\"runsOn\":[\"NeoForge\"]}}}");

		assertEquals(new CompatPlan.Entry("replaces the pipeline", null, Map.of("renderer", "hooks the Fabric build's classes"), Set.of()), plan.entry("shaders"));
		assertEquals(new CompatPlan.Entry("downloads the mod at launch", "Remove it.", Map.of(), Set.of("NeoForge")), plan.entry("loader-stub"));
		assertNull(plan.entry("other").neoForgeBuildOnly());
		assertEquals(Map.of(), plan.entry("other").notWithNeoForge());
		// A refusal lifted on one loader stands on the others.
		assertNull(plan.entry("loader-stub").refusal("NeoForge"));
		assertEquals("downloads the mod at launch", plan.entry("loader-stub").refusal("Forge"));
		assertEquals("replaces the pipeline", plan.entry("shaders").refusal("NeoForge"));
		assertNull(plan.entry("other").refusal("Forge"));
	}

	/** The plan ships in the jar: a mistake in it is a build error, caught here rather than at a player's launch. */
	@Test
	void theShippedPlanIsValid() {
		CompatPlan plan = CompatPlan.builtin();
		assertEquals(List.of("sodium"), List.copyOf(plan.entry("iris").notWithNeoForge().keySet()));
		// Gated on both loaders, so refused on neither (decision 3 of the v0.6.0-beta pass).
		for (String id : List.of("sodium", "iris", "essential-container", "essential-loader", "malilib", "flashback")) {
			assertNull(plan.entry(id).refusal("NeoForge"), id);
			assertNull(plan.entry(id).refusal("Forge"), id);
		}
		assertNotNull(plan.entry("wthit").refusal("NeoForge"), "WTHIT's own entrypoint refuses NeoForge");
	}

	@Test
	void rejectsWhatItDoesNotUnderstand() {
		for (String bad : List.of("{\"mods\":{}}", "{\"schema\":2,\"mods\":{}}", "{\"schema\":1}",
				"{\"schema\":1,\"mods\":{\"Bad Id\":{}}}",
				"{\"schema\":1,\"mods\":{\"a-mod\":{\"skipMixins\":[]}}}",
				"{\"schema\":1,\"mods\":{\"a-mod\":{\"neoForgeBuildOnly\":\"\"}}}",
				"{\"schema\":1,\"mods\":{\"a-mod\":{\"suggestion\":\"Remove it.\"}}}",
				"{\"schema\":1,\"mods\":{\"a-mod\":{\"notWithNeoForge\":{\"other\":true}}}}",
				"{\"schema\":1,\"mods\":{\"a-mod\":{\"runsOn\":[\"NeoForge\"]}}}",
				"{\"schema\":1,\"mods\":{\"a-mod\":{\"neoForgeBuildOnly\":\"why\",\"runsOn\":[]}}}",
				"{\"schema\":1,\"mods\":{\"a-mod\":{\"neoForgeBuildOnly\":\"why\",\"runsOn\":[\"Fabric\"]}}}",
				"{\"schema\":1,\"mods\":{\"a-mod\":{\"neoForgeBuildOnly\":\"why\",\"runsOn\":\"NeoForge\"}}}")) {
			assertThrows(JsonException.class, () -> CompatPlan.parse(bad), bad);
		}
	}
}
