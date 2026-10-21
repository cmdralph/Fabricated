package io.github.cmdralph.fabricated.core.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.cmdralph.fabricated.core.json.JsonException;

class CompatPlanTest {
	@Test
	void parsesWhatIsKnownAboutAMod() throws Exception {
		CompatPlan plan = CompatPlan.parse("{\"schema\":1,\"mods\":{\"shaders\":{\"neoForgeBuildOnly\":\"replaces the pipeline\","
				+ "\"notWithNeoForge\":{\"renderer\":\"hooks the Fabric build's classes\"}},"
				+ "\"loader-stub\":{\"neoForgeBuildOnly\":\"downloads the mod at launch\",\"suggestion\":\"Remove it.\"}}}");

		assertEquals(new CompatPlan.Entry("replaces the pipeline", null, Map.of("renderer", "hooks the Fabric build's classes")), plan.entry("shaders"));
		assertEquals(new CompatPlan.Entry("downloads the mod at launch", "Remove it.", Map.of()), plan.entry("loader-stub"));
		assertNull(plan.entry("other").neoForgeBuildOnly());
		assertEquals(Map.of(), plan.entry("other").notWithNeoForge());
	}

	/** The plan ships in the jar: a mistake in it is a build error, caught here rather than at a player's launch. */
	@Test
	void theShippedPlanIsValid() {
		CompatPlan plan = CompatPlan.builtin();
		assertNotNull(plan.entry("sodium").neoForgeBuildOnly());
		assertEquals(List.of("sodium"), List.copyOf(plan.entry("iris").notWithNeoForge().keySet()));
		for (String id : List.of("essential-container", "essential-loader", "malilib", "wthit", "flashback")) assertNotNull(plan.entry(id).neoForgeBuildOnly(), id);
		assertNotNull(plan.entry("essential-container").suggestion(), "Essential has no NeoForge build for every version");
	}

	@Test
	void rejectsWhatItDoesNotUnderstand() {
		for (String bad : List.of("{\"mods\":{}}", "{\"schema\":2,\"mods\":{}}", "{\"schema\":1}",
				"{\"schema\":1,\"mods\":{\"Bad Id\":{}}}",
				"{\"schema\":1,\"mods\":{\"a-mod\":{\"skipMixins\":[]}}}",
				"{\"schema\":1,\"mods\":{\"a-mod\":{\"neoForgeBuildOnly\":\"\"}}}",
				"{\"schema\":1,\"mods\":{\"a-mod\":{\"suggestion\":\"Remove it.\"}}}",
				"{\"schema\":1,\"mods\":{\"a-mod\":{\"notWithNeoForge\":{\"other\":true}}}}")) {
			assertThrows(JsonException.class, () -> CompatPlan.parse(bad), bad);
		}
	}
}
