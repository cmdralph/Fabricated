package io.github.cmdralph.fabricated.core.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;

class FabricOverlaysTest {
	private static final FabricOverlays NEOFORGE = new FabricOverlays(FabricOverlays.NEOFORGE_SECTION);
	private static final Callable<List<String>> NOT_READ = () -> {
		throw new AssertionError("the section was read");
	};

	@Test
	void appliedOverlaysFollowThePacksOwn() {
		List<String> warnings = new ArrayList<>();
		List<String> overlays = NEOFORGE.overlays("mod/a", List.of("vanilla"), Set.of("pack", "overlays", FabricOverlays.SECTION),
				() -> List.of("fabric_one", "fabric_two"), warnings::add);
		assertEquals(List.of("vanilla", "fabric_one", "fabric_two"), overlays);
		assertThrows(UnsupportedOperationException.class, () -> overlays.add("more"), "the list is the game's, as upstream's is");
		assertEquals(List.of(), warnings);
	}

	@Test
	void aPackWithoutFabricsSectionKeepsItsOverlays() {
		List<String> own = List.of("vanilla");
		assertSame(own, NEOFORGE.overlays("mod/a", own, Set.of("pack", "overlays"), NOT_READ, warning -> {
			throw new AssertionError(warning);
		}));
	}

	@Test
	void noConditionHoldingKeepsThePacksOverlays() {
		List<String> own = List.of("vanilla");
		assertSame(own, NEOFORGE.overlays("mod/a", own, Set.of("pack", FabricOverlays.SECTION), List::of, warning -> {
			throw new AssertionError(warning);
		}));
	}

	@Test
	void aPackDeclaringThePlatformsOverlaysIsThePlatforms() {
		// Terralith's NeoForge jar: NeoForge's overlays, gated by its NeoForge build's condition type, and Fabric's, gated by
		// one only its Fabric build registers. Fabric's isn't read, so its unknown condition never comes up.
		List<String> neoForgeOwn = List.of("disable.skylands");
		List<String> warnings = new ArrayList<>();
		assertSame(neoForgeOwn, NEOFORGE.overlays("mod/terralith", neoForgeOwn,
				Set.of("pack", "overlays", FabricOverlays.NEOFORGE_SECTION, FabricOverlays.SECTION), NOT_READ, warnings::add));
		assertEquals(List.of(), warnings);
	}

	@Test
	void withoutOverlaysOfItsOwnThePlatformReadsFabrics() {
		// Forge reads no neoforge:overlays: a pack declaring them has nothing of Forge's, and Fabric's apply.
		FabricOverlays forge = new FabricOverlays(null);
		assertEquals(List.of("fabric"), forge.overlays("mod:a", List.of(), Set.of("pack", FabricOverlays.NEOFORGE_SECTION, FabricOverlays.SECTION),
				() -> List.of("fabric"), warning -> {
					throw new AssertionError(warning);
				}));
	}

	@Test
	void anUnreadableSectionIsLeftOutWithOneWarning() {
		FabricOverlays overlays = new FabricOverlays(FabricOverlays.NEOFORGE_SECTION);
		List<String> warnings = new ArrayList<>();
		Callable<List<String>> unknown = () -> {
			throw new IllegalStateException("Unknown resource condition key: tectonic:config");
		};
		List<String> own = List.of("overlay.26_1");
		assertSame(own, overlays.overlays("mod/tectonic", own, Set.of("pack", FabricOverlays.SECTION), unknown, warnings::add));
		// The game reads a pack's metadata again on every reload.
		assertSame(own, overlays.overlays("mod/tectonic", own, Set.of("pack", FabricOverlays.SECTION), unknown, warnings::add));
		assertEquals(1, warnings.size(), warnings.toString());
		assertTrue(warnings.get(0).contains("mod/tectonic") && warnings.get(0).contains("Unknown resource condition key: tectonic:config"), warnings.get(0));
		// Another pack with the same section, or the same pack failing otherwise, is warned of too.
		overlays.overlays("file/tectonic.zip", own, Set.of(FabricOverlays.SECTION), unknown, warnings::add);
		overlays.overlays("mod/tectonic", own, Set.of(FabricOverlays.SECTION), () -> {
			throw new IllegalArgumentException("Directory name is invalid");
		}, warnings::add);
		assertEquals(3, warnings.size(), warnings.toString());
	}

	@Test
	void anExceptionWithoutAMessageIsNamedByItsType() {
		List<String> warnings = new ArrayList<>();
		new FabricOverlays(null).overlays("mod:a", List.of(), Set.of(FabricOverlays.SECTION), () -> {
			throw new NullPointerException();
		}, warnings::add);
		assertEquals(1, warnings.size());
		assertTrue(warnings.get(0).contains("java.lang.NullPointerException"), warnings.get(0));
	}

	@Test
	void noOverlaysFromTheReaderIsUnreadable() {
		List<String> warnings = new ArrayList<>();
		List<String> own = List.of();
		assertSame(own, new FabricOverlays(null).overlays("mod:a", own, Set.of(FabricOverlays.SECTION), () -> null, warnings::add));
		assertEquals(1, warnings.size());
	}
}
