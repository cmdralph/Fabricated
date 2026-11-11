package io.github.cmdralph.fabricated.core.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;
import io.github.cmdralph.fabricated.core.bytecode.InstalledClasses;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding.Severity;
import io.github.cmdralph.fabricated.core.testutil.TestSources;

/**
 * SEAM-2: an {@code @Inject} capturing locals that Mixin skips at each of its points (the locals it finds there aren't the
 * handler's) is noted, and the injectors left reading what it alone stores are left out alone; where another injector
 * that applies stores it, where that can't be told, or where they can't be left out alone, nothing changes.
 */
class SkippedCapturesTest {
	private static final String LEVEL = "net/minecraft/world/level/";
	private static final String GET_STATE = "L" + LEVEL + "Level;getBlockState(L" + LEVEL + "Pos;)L" + LEVEL + "State;";
	private static final String STICK = "L" + LEVEL + "Line;stick(L" + LEVEL + "State;L" + LEVEL + "State;)Z";

	/** Vanilla's line: the position behind is declared first (slot 5), the old state after it (slot 6). */
	private static final String VANILLA = "Pos behind = pos.relative(i); State old = state;";
	/** The platform's (NeoForge 21.1 to 26.1.2 and Forge 47): the old state declared first, stored after the position. */
	private static final String PLATFORM = "State old; Pos behind = pos.relative(i); old = state;";

	/** Carpet's capture of the line's positions, at {@code addBlockLine}'s second {@code getBlockState}. */
	private static final String AT_LINE = "at = @At(value = \"INVOKE\", target = \"" + GET_STATE + "\", ordinal = 1)";
	private static final String HARD = "locals = LocalCapture.CAPTURE_FAILHARD";
	private static final String CAPTURE = "capture(L" + LEVEL + "Pos;ILorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;L" + LEVEL
			+ "State;IL" + LEVEL + "Pos;)V";
	private static final String LINE = "line(L" + LEVEL + "State;L" + LEVEL + "State;)Z";

	/** The game: {@code addBlockLine} as vanilla's resolver has it, its stores in the line's loop as {@code order} has them. */
	private static Map<String, String> game(String order, String beforePoint) {
		return Map.of(
				LEVEL + "Pos.java", "package net.minecraft.world.level; public class Pos { public Pos relative(int i) { return this; } }",
				LEVEL + "State.java", "package net.minecraft.world.level; public class State { public boolean isAir() { return false; }"
						+ " public boolean sticky() { return false; } public boolean canStickTo(State other) { return false; } }",
				LEVEL + "Level.java", "package net.minecraft.world.level; public class Level { public State getBlockState(Pos p) { return null; } }",
				LEVEL + "Line.java", "package net.minecraft.world.level; public class Line { Level level;"
						+ " static boolean stick(State a, State b) { return a.canStickTo(b); }"
						+ " boolean resolve() { return true; }"
						+ " boolean addBlockLine(Pos pos, int dir) { State state = level.getBlockState(pos); int i = 1;"
						+ "  while (state.sticky()) { " + order + beforePoint + " state = level.getBlockState(behind);"
						+ "   if (state.isAir() || !stick(old, state)) break; i++; }"
						+ "  return true; } }");
	}

	/**
	 * A mixin of Carpet's custom stickiness: a capture keeping the line's position in the mixin's field {@code linePos},
	 * a redirect of the line's check reading it, a hook sharing nothing, and {@code extra} members.
	 */
	private static Map<String, String> mixin(String capture, String implemented, String field, String extra) {
		return Map.of(
				"org/spongepowered/asm/mixin/Overwrite.java", "package org.spongepowered.asm.mixin; public @interface Overwrite { }",
				"org/spongepowered/asm/mixin/injection/At.java", "package org.spongepowered.asm.mixin.injection; public @interface At { String value();"
						+ " String target() default \"\"; int ordinal() default -1; }",
				"org/spongepowered/asm/mixin/injection/Inject.java", "package org.spongepowered.asm.mixin.injection; public @interface Inject { String[] method();"
						+ " At[] at(); org.spongepowered.asm.mixin.injection.callback.LocalCapture locals() default"
						+ " org.spongepowered.asm.mixin.injection.callback.LocalCapture.NO_CAPTURE; int require() default -1; }",
				"org/spongepowered/asm/mixin/injection/Redirect.java", "package org.spongepowered.asm.mixin.injection; public @interface Redirect { String[] method(); At at(); }",
				"org/spongepowered/asm/mixin/injection/callback/LocalCapture.java", "package org.spongepowered.asm.mixin.injection.callback;"
						+ " public enum LocalCapture { NO_CAPTURE, PRINT, CAPTURE_FAILSOFT, CAPTURE_FAILHARD, CAPTURE_FAILEXCEPTION }",
				"org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable.java", "package org.spongepowered.asm.mixin.injection.callback;"
						+ " public class CallbackInfoReturnable<R> { }",
				"com/example/Duck.java", "package com.example; public interface Duck { }",
				"com/example/mixin/Sticky.java", "package com.example.mixin; import org.spongepowered.asm.mixin.*; import org.spongepowered.asm.mixin.injection.*;"
						+ " import org.spongepowered.asm.mixin.injection.callback.*; import net.minecraft.world.level.*;"
						+ " @Mixin(Line.class) public class Sticky" + implemented + " { " + field + " Pos linePos;"
						+ " @Inject(method = \"addBlockLine\", " + capture + ")"
						+ "  private void capture(Pos pos, int dir, CallbackInfoReturnable<Boolean> cir, State state, int i, Pos behind) { linePos = behind; }"
						+ " @Redirect(method = \"addBlockLine\", at = @At(value = \"INVOKE\", target = \"" + STICK + "\"))"
						+ "  private boolean line(State a, State b) { return linePos.relative(0) != null; }"
						+ " @Inject(method = \"resolve\", at = @At(\"HEAD\")) private void start(CallbackInfoReturnable<Boolean> cir) { }"
						+ extra + " }");
	}

	private static List<PreflightFinding> check(Path dir, String order, String beforePoint, String capture, String implemented, String field,
			String extra) throws Exception {
		Map<String, byte[]> classes = new HashMap<>(TestSources.compileWithLocals(dir, TestSources.MIXIN_ANNOTATIONS, game(order, beforePoint),
				mixin(capture, implemented, field, extra)));
		MixinPreflight preflight = new MixinPreflight(new ClassIndex(classes::get), InstalledClasses.NONE, classes::get, ClassBytesSource.EMPTY, false, true,
				ClassBytesSource.EMPTY);
		MixinConfigFile config = MixinConfigFile.parse("{\"required\":true,\"package\":\"com.example.mixin\",\"mixins\":[\"Sticky\"]}");
		return preflight.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
	}

	private static List<PreflightFinding> check(Path dir, String order, String beforePoint, String capture, String implemented) throws Exception {
		return check(dir, order, beforePoint, capture, implemented, "private", "");
	}

	private static List<String> handlers(List<PreflightFinding> findings, Severity severity) {
		return findings.stream().filter(f -> f.severity() == severity).map(PreflightFinding::handler).toList();
	}

	@Test
	void theInjectorsReadingWhatOnlyASkippedCaptureStoresAreLeftOutAlone(@TempDir Path dir) throws Exception {
		List<PreflightFinding> findings = check(dir, PLATFORM, "", AT_LINE + ", " + HARD, "");
		assertEquals(List.of(LINE), handlers(findings, Severity.LEFT_OUT), "only the redirect reading the position: " + findings);
		assertEquals(List.of(CAPTURE), handlers(findings, Severity.INFO), findings.toString());
		assertEquals(2, findings.size(), "the hook sharing nothing stays: " + findings);
		assertEquals("@Inject capture captures the locals (State, int, Pos) in addBlockLine(Pos, int), where Mixin finds (State, int, State) in"
				+ " NeoForge's code at each of its points: Mixin skips it there (SEAM-2)", findings.get(0).message());
		assertEquals("The injector of " + LINE + " goes with that of " + CAPTURE + ", which Mixin skips at each of its points or which can't apply: it reads"
				+ " what only " + CAPTURE + " stores, in this mixin's field linePos (SEAM-2)", findings.get(1).message());

		// As the soften step leaves it (skipping itself, required nowhere), the same.
		List<PreflightFinding> softened = check(dir.resolve("soft"), PLATFORM, "", AT_LINE + ", locals = LocalCapture.CAPTURE_FAILSOFT, require = 0", "");
		assertEquals(List.of(LINE), handlers(softened, Severity.LEFT_OUT), softened.toString());
	}

	@Test
	void anAlternativeCaptureThatAppliesKeepsEverything(@TempDir Path dir) throws Exception {
		// Vanilla's order and the platform's, each storing the position: on either, one is skipped and the other applies.
		String platformCapture = " @Inject(method = \"addBlockLine\", " + AT_LINE + ", locals = LocalCapture.CAPTURE_FAILSOFT, require = 0)"
				+ "  private void capturePlatform(Pos pos, int dir, CallbackInfoReturnable<Boolean> cir, State state, int i, State old, Pos behind) {"
				+ " linePos = behind; }";
		String soft = AT_LINE + ", locals = LocalCapture.CAPTURE_FAILSOFT, require = 0";
		List<PreflightFinding> platform = check(dir, PLATFORM, "", soft, "", "private", platformCapture);
		assertEquals(List.of(CAPTURE), handlers(platform, Severity.INFO), platform.toString());
		assertEquals(1, platform.size(), "nothing goes: " + platform);
		List<PreflightFinding> vanilla = check(dir.resolve("vanilla"), VANILLA, "", soft, "", "private", platformCapture);
		assertEquals(1, vanilla.size(), vanilla.toString());
		assertTrue(vanilla.get(0).severity() == Severity.INFO && vanilla.get(0).handler().startsWith("capturePlatform("), vanilla.toString());
	}

	@Test
	void readersWhoseStateReachesOtherCodeStay(@TempDir Path dir) throws Exception {
		// The position kept in a field that isn't private, or read by a method the target may call: STATE-1 couldn't leave
		// the redirect out alone, so nothing goes, and never the whole mixin.
		Map<String, String[]> cases = Map.of(
				"a field that isn't private", new String[] {"public", ""},
				"a method that isn't private", new String[] {"private", " public Pos shown() { return linePos; }"},
				"an @Overwrite", new String[] {"private", " @Overwrite boolean resolve() { return linePos != null; }"});
		for (Map.Entry<String, String[]> e : cases.entrySet()) {
			List<PreflightFinding> findings = check(dir.resolve(e.getKey().replace(' ', '_').replace("@", "")), PLATFORM, "", AT_LINE + ", " + HARD, "",
					e.getValue()[0], e.getValue()[1]);
			assertTrue(findings.stream().noneMatch(f -> f.severity() == Severity.LEFT_OUT || f.severity() == Severity.FAIL), e.getKey() + ": " + findings);
			assertTrue(findings.stream().anyMatch(f -> f.message().startsWith("The injector of " + LINE + " reads what only " + CAPTURE + " stores")
					&& f.message().endsWith("it stays, as it did before, as it can't be left out alone: the state reaches code that isn't one of this mixin's"
							+ " injectors, or this mixin adds an interface or accessors the mod's code uses (SEAM-2)")), e.getKey() + ": " + findings);
		}
	}

	@Test
	void readersOfAMixinTheModsCodeReachesStay(@TempDir Path dir) throws Exception {
		// The mixin adds an interface the mod's code may cast to: none of its injectors can be left out without disabling the mod.
		List<PreflightFinding> findings = check(dir, PLATFORM, "", AT_LINE + ", " + HARD, " implements com.example.Duck");
		assertEquals(List.of(Severity.INFO, Severity.INFO), findings.stream().map(PreflightFinding::severity).toList(), findings.toString());
		assertTrue(findings.get(1).message().contains("it stays, as it did before"), findings.get(1).message());
	}

	@Test
	void aCaptureFindingTheLocalsItTakesStays(@TempDir Path dir) throws Exception {
		List<PreflightFinding> findings = check(dir, VANILLA, "", AT_LINE + ", " + HARD, "");
		assertTrue(findings.isEmpty(), findings.toString());
	}

	@Test
	void aCaptureWhoseLocalsAFrameMayChangeIsNotJudged(@TempDir Path dir) throws Exception {
		// A branch between the stores and the point: Mixin may give each slot another local at the frame there.
		List<PreflightFinding> findings = check(dir, PLATFORM, " if (i > 100) i = 100;", AT_LINE + ", " + HARD, "");
		assertTrue(findings.isEmpty(), findings.toString());
	}

	@Test
	void aCaptureAtAConstructionIsNotJudged(@TempDir Path dir) throws Exception {
		// Mixin's versions differ in where they inject at a NEW and how they count its ordinal: not judged.
		List<PreflightFinding> findings = check(dir, PLATFORM, " new Pos();", "at = @At(value = \"NEW\", target = \"L" + LEVEL + "Pos;\"), " + HARD, "");
		assertTrue(findings.stream().noneMatch(f -> f.message().contains("SEAM-2")), findings.toString());
		assertTrue(findings.stream().noneMatch(f -> f.severity() == Severity.LEFT_OUT), findings.toString());
	}

	@Test
	void foundAtReadsTheLocalsMixinWalksTo(@TempDir Path dir) throws Exception {
		Map<String, byte[]> classes = TestSources.compileWithLocals(dir, game(PLATFORM, ""));
		ClassNode line = new ClassNode();
		new ClassReader(classes.get(LEVEL + "Line")).accept(line, 0);
		MethodNode method = line.methods.stream().filter(m -> m.name.equals("addBlockLine")).findFirst().orElseThrow();
		List<Integer> calls = PreflightAsm.matching(method, "INVOKE", MemberRef.parse(GET_STATE), -1);
		assertEquals(2, calls.size());
		// At the first call, nothing past the parameters holds a local yet.
		assertEquals(List.of(), SkippedCaptures.foundAt(method, calls.get(0), 0));
		assertNull(SkippedCaptures.foundAt(method, calls.get(0), 1), "the slots past the parameters hold none: fewer than one found");
		// At the second, the stores since the loop's frame tell each slot: the state, the count, the old state, the position.
		assertEquals("(State, int, State, Pos)", SkippedCaptures.types(SkippedCaptures.foundAt(method, calls.get(1), 4)));
		// The method has no fifth: fewer found than asked isn't judged (the slots Mixin's own injections add may hold more).
		assertNull(SkippedCaptures.foundAt(method, calls.get(1), 5));
	}
}
