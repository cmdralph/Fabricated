package io.github.cmdralph.fabricated.core.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.cmdralph.fabricated.core.report.DiagnosticsBlock.Entry;
import io.github.cmdralph.fabricated.core.report.DiagnosticsBlock.Launch;
import io.github.cmdralph.fabricated.core.report.DiagnosticsBlock.Mod;
import io.github.cmdralph.fabricated.core.report.DiagnosticsBlock.Owner;

/** The diagnostics block: what it names of a failure, only where that is certain, and its bounds. */
class DiagnosticsBlockTest {
	private static final Launch LAUNCH = new Launch("NeoForge", "21.1.200", "1.21.1", "0.6.0-beta.3");
	private static final Mod LEDGER = new Mod("ledger", "Ledger", "1.3.5", "ledger-1.3.5+1.21.1.jar");
	private static final Mod LIB = new Mod("examplelib", "Example Lib", "2.0", "examplelib-2.0.jar");
	private static final List<Owner> OWNERS = List.of(
			new Owner(LEDGER, "ledger", Set.of("com/github/quiltservertools/ledger", "com/github/quiltservertools/ledger/database",
					"com/github/quiltservertools/ledger/mixin"), Set.of("ledger.mixins.json")),
			new Owner(LIB, "examplelib", Set.of("com/example/lib", "com/example/shared"), Set.of("examplelib.mixins.json", "common.mixins.json")),
			new Owner(new Mod("other", "Other", "1.0", "other.jar"), "other", Set.of("com/example/shared"), Set.of("common.mixins.json")));

	@Test
	void aMixinFailureNamesItsModItsMixinAndItsTarget() {
		String block = DiagnosticsBlock.render(List.of(new Entry(LEDGER, "ledger.mixins.json:AnvilScreenHandlerMixin", "net.minecraft.world.inventory.AnvilMenu",
				null)), LAUNCH, DiagnosticsBlock.LEFT_OUT);
		assertEquals("""
				---- Fabricated: a Fabric mod was involved ----
				Fabric mod:  Ledger (ledger) 1.3.5, ledger-1.3.5+1.21.1.jar
				Mixin:       ledger.mixins.json:AnvilScreenHandlerMixin, into net.minecraft.world.inventory.AnvilMenu
				Loader:      NeoForge 21.1.200, Minecraft 1.21.1, Fabricated 0.6.0-beta.3
				Fabricated:  leaves this mixin out from the next launch (logs/fabric-bridge-report.txt says why)""", block);
	}

	@Test
	void aMixinWhoseTargetIsUnknownIsNamedAlone() {
		String block = DiagnosticsBlock.render(List.of(new Entry(LEDGER, "ledger.mixins.json:A", "", null)), LAUNCH, DiagnosticsBlock.NOT_RECORDED);
		assertTrue(block.contains("\nMixin:       ledger.mixins.json:A\n"), block);
	}

	@Test
	void withoutAMixinTheFirstFrameInAFabricModsClassesNamesIt() {
		DiagnosticsBlock diagnostics = new DiagnosticsBlock(OWNERS, false);
		Throwable thrown = thrown("boom", frame("net.minecraft.server.MinecraftServer", "tickServer"),
				frame("com.github.quiltservertools.ledger.database.DatabaseManager", "logAction"), frame("net.minecraft.server.Main", "main"));
		List<Entry> entries = diagnostics.entries(thrown, List.of(), null);
		assertEquals(1, entries.size(), entries.toString());
		assertSame(LEDGER, entries.get(0).mod());
		assertEquals("com.github.quiltservertools.ledger.database.DatabaseManager.logAction(DatabaseManager.java:12)", entries.get(0).frame());
		String block = DiagnosticsBlock.render(entries, LAUNCH, DiagnosticsBlock.UNCHANGED);
		assertTrue(block.contains("\nClass:       com.github.quiltservertools.ledger.database.DatabaseManager.logAction(DatabaseManager.java:12)\n"), block);
		assertTrue(block.endsWith("Fabricated:  " + DiagnosticsBlock.UNCHANGED), block);
	}

	@Test
	void nothingAttributableWritesNoBlock() {
		DiagnosticsBlock diagnostics = new DiagnosticsBlock(OWNERS, true);
		Throwable thrown = thrown("vanilla", frame("net.minecraft.world.level.Level", "tick"), frame("java.lang.Thread", "run"));
		List<Entry> entries = diagnostics.entries(thrown, List.of(), null);
		assertTrue(entries.isEmpty(), entries.toString());
		assertNull(DiagnosticsBlock.render(entries, LAUNCH, DiagnosticsBlock.UNCHANGED));
		assertNull(DiagnosticsBlock.render(List.of(), LAUNCH, DiagnosticsBlock.UNCHANGED));
		assertTrue(diagnostics.entries(null, null, null).isEmpty());
	}

	@Test
	void aPackageOrAConfigTwoModsHaveNamesNeither() {
		DiagnosticsBlock diagnostics = new DiagnosticsBlock(OWNERS, false);
		assertTrue(diagnostics.entries(thrown("shared", frame("com.example.shared.Util", "run")), List.of(), null).isEmpty());
		assertNull(diagnostics.modOf("common.mixins.json", null));
		assertSame(LIB, diagnostics.modOf("examplelib.mixins.json", null));
		assertSame(LIB, diagnostics.entries(thrown("lib", frame("com.example.lib.Util", "run")), List.of(), null).get(0).mod());
		// A class of a subpackage is no class of its parent's: packages are not prefixes.
		assertTrue(diagnostics.entries(thrown("sub", frame("com.example.lib.inner.Util", "run")), List.of(), null).isEmpty());
	}

	@Test
	void theModMixinNamesIsTheOneWithItsPlatformIdElseNone() {
		DiagnosticsBlock diagnostics = new DiagnosticsBlock(OWNERS, true);
		assertSame(LEDGER, diagnostics.modOf("ledger.mixins.json", "ledger"));
		// Another mod's mixin (a NeoForge mod's) is not a Fabric mod's, whatever its config is called.
		assertNull(diagnostics.modOf("ledger.mixins.json", "sodium"));
		assertNull(diagnostics.modOf("unknown.mixins.json", null));
		assertNull(diagnostics.modOf(null, null));
	}

	@Test
	void aMergedHandlerNamesItsModOnlyWhereTheMixinNamesMods() {
		Throwable thrown = thrown("handler", frame("net.minecraft.world.level.Level", "handler$zza000$ledger$onExplode"));
		List<Entry> named = new DiagnosticsBlock(OWNERS, true).entries(thrown, List.of(), null);
		assertEquals(1, named.size());
		assertSame(LEDGER, named.get(0).mod());
		assertEquals("net.minecraft.world.level.Level.handler$zza000$ledger$onExplode(Level.java:12)", named.get(0).frame());
		// Forge's Mixin names no mod there: the same name could be a handler called ledger$onExplode.
		assertTrue(new DiagnosticsBlock(OWNERS, false).entries(thrown, List.of(), null).isEmpty());
		// A name in the pattern whose part is no loaded Fabric mod's id names none.
		assertTrue(new DiagnosticsBlock(OWNERS, true).entries(thrown("other", frame("net.minecraft.world.level.Level", "handler$zza000$sodium$onTick")),
				List.of(), null).isEmpty());
	}

	@Test
	void theMixinComesFirstThenTheDeepestCausesFramesEachModOnce() {
		DiagnosticsBlock diagnostics = new DiagnosticsBlock(OWNERS, false);
		Throwable cause = thrown("cause", frame("com.example.lib.Thing", "deep"), frame("com.github.quiltservertools.ledger.Ledger", "call"));
		Throwable outer = thrown("outer", frame("com.github.quiltservertools.ledger.Ledger", "wrap"));
		outer.initCause(cause);
		List<Entry> entries = diagnostics.entries(outer, List.of(), null);
		assertEquals(List.of(LIB, LEDGER), entries.stream().map(Entry::mod).toList());
		assertEquals("com.example.lib.Thing.deep(Thing.java:12)", entries.get(0).frame());

		Entry mixin = new Entry(LEDGER, "ledger.mixins.json:A", "", null);
		entries = diagnostics.entries(outer, List.of(), mixin);
		assertEquals(List.of(LEDGER, LIB), entries.stream().map(Entry::mod).toList());
		assertSame(mixin, entries.get(0));
	}

	@Test
	void throwablesThePlatformHoldsBesideItAreReadToo() {
		DiagnosticsBlock diagnostics = new DiagnosticsBlock(OWNERS, false);
		Throwable placeholder = thrown("Mod Loading has failed", frame("net.minecraftforge.logging.CrashReportExtender", "dump"));
		Throwable error = thrown("in a constructor", frame("com.example.lib.Init", "init"));
		assertSame(LIB, diagnostics.entries(placeholder, List.of(error), null).get(0).mod());
	}

	@Test
	void aCycleOfCausesOrSuppressedOnesIsReadOnce() {
		Exception a = new Exception("a");
		Exception b = new Exception("b");
		a.initCause(b);
		b.initCause(a);
		a.addSuppressed(b);
		b.addSuppressed(a);
		assertEquals(2, DiagnosticsBlock.throwables(a, List.of(b)).size());
	}

	@Test
	void itIsBoundedInModsAndLineLength() {
		List<Owner> many = new ArrayList<>();
		Throwable thrown = new Throwable("many");
		List<StackTraceElement> frames = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			many.add(new Owner(new Mod("mod" + i, "Mod\n" + "x".repeat(300), "1." + i, "mod" + i + ".jar"), "mod" + i, Set.of("com/mod" + i), Set.of()));
			frames.add(frame("com.mod" + i + ".Main", "run"));
		}
		thrown.setStackTrace(frames.toArray(StackTraceElement[]::new));
		DiagnosticsBlock diagnostics = new DiagnosticsBlock(many, false);
		List<Entry> entries = diagnostics.entries(thrown, List.of(), null);
		assertEquals(DiagnosticsBlock.MAX_MODS, entries.size());
		String block = DiagnosticsBlock.render(entries, LAUNCH, DiagnosticsBlock.UNCHANGED);
		String[] lines = block.split("\n");
		assertEquals(1 + 2 * DiagnosticsBlock.MAX_MODS + 2, lines.length, block);
		for (String line : lines) assertTrue(line.length() <= 13 + 4 * DiagnosticsBlock.MAX_VALUE, line);
		assertTrue(lines[1].startsWith("Fabric mod:  Mod xxx"), "control characters are flattened: " + lines[1]);
		assertTrue(lines[1].contains("...") && lines[1].endsWith("mod0.jar"), lines[1]);
		assertEquals(DiagnosticsBlock.MAX_VALUE, DiagnosticsBlock.value("y".repeat(1000)).length());
	}

	@Test
	void onlyABoundedPartOfAStackIsRead() {
		Throwable thrown = new Throwable("deep");
		StackTraceElement[] frames = new StackTraceElement[DiagnosticsBlock.MAX_FRAMES + 1];
		for (int i = 0; i < DiagnosticsBlock.MAX_FRAMES; i++) frames[i] = frame("net.minecraft.Recursive", "call");
		frames[DiagnosticsBlock.MAX_FRAMES] = frame("com.example.lib.Thing", "past");
		thrown.setStackTrace(frames);
		assertTrue(new DiagnosticsBlock(OWNERS, false).entries(thrown, List.of(), null).isEmpty());
	}

	@Test
	void framesAreWrittenAsJavaPrintsThemWithoutLoaderOrModule() {
		assertEquals("a.B.c(Native Method)", DiagnosticsBlock.frame(new StackTraceElement("a.B", "c", null, -2)));
		assertEquals("a.B.c(Unknown Source)", DiagnosticsBlock.frame(new StackTraceElement("a.B", "c", null, -1)));
		assertEquals("a.B.c(B.java)", DiagnosticsBlock.frame(new StackTraceElement("a.B", "c", "B.java", -1)));
		assertEquals("a.B.c(B.java:3)", DiagnosticsBlock.frame(new StackTraceElement("TRANSFORMER", "mod", "1.0", "a.B", "c", "B.java", 3)));
		assertFalse(DiagnosticsBlock.frame(new StackTraceElement("TRANSFORMER", "mod", "1.0", "a.B", "c", "B.java", 3)).contains("TRANSFORMER"));
	}

	private static StackTraceElement frame(String className, String method) {
		return new StackTraceElement(className, method, className.substring(className.lastIndexOf('.') + 1) + ".java", 12);
	}

	private static Throwable thrown(String message, StackTraceElement... frames) {
		Throwable t = new Throwable(message);
		t.setStackTrace(frames);
		return t;
	}
}
