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
 * handler's) is left out, so the injectors keeping state with it go with it; where that can't be told, or leaving it out
 * would disable the mod, nothing changes.
 */
class SkippedCapturesTest {
	private static final String LEVEL = "net/minecraft/world/level/";
	private static final String GET_STATE = "L" + LEVEL + "Level;getBlockState(L" + LEVEL + "Pos;)L" + LEVEL + "State;";
	private static final String STICK = "L" + LEVEL + "Line;stick(L" + LEVEL + "State;L" + LEVEL + "State;)Z";

	/** Vanilla's line: the position behind is declared first (slot 5), the old state after it (slot 6). */
	private static final String VANILLA = "Pos behind = pos.relative(i); State old = state;";
	/** The platform's (NeoForge 21.1 to 26.1.2 and Forge 47): the old state declared first, stored after the position. */
	private static final String PLATFORM = "State old; Pos behind = pos.relative(i); old = state;";

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
	 * Carpet's custom stickiness: a capture keeping the line's position in the mixin's field, a redirect of the line's check
	 * reading it, and a hook sharing nothing.
	 */
	private static Map<String, String> mixin(String capture, String implemented) {
		return Map.of(
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
				"com/example/mixin/Sticky.java", "package com.example.mixin; import org.spongepowered.asm.mixin.Mixin; import org.spongepowered.asm.mixin.injection.*;"
						+ " import org.spongepowered.asm.mixin.injection.callback.*; import net.minecraft.world.level.*;"
						+ " @Mixin(Line.class) public class Sticky" + implemented + " { private Pos linePos;"
						+ " @Inject(method = \"addBlockLine\", at = @At(value = \"INVOKE\", target = \"" + GET_STATE + "\", ordinal = 1), " + capture + ")"
						+ "  private void capture(Pos pos, int dir, CallbackInfoReturnable<Boolean> cir, State state, int i, Pos behind) { linePos = behind; }"
						+ " @Redirect(method = \"addBlockLine\", at = @At(value = \"INVOKE\", target = \"" + STICK + "\"))"
						+ "  private boolean line(State a, State b) { return linePos.relative(0) != null; }"
						+ " @Inject(method = \"resolve\", at = @At(\"HEAD\")) private void start(CallbackInfoReturnable<Boolean> cir) { } }");
	}

	private static List<PreflightFinding> check(Path dir, String order, String beforePoint, String capture, String implemented) throws Exception {
		Map<String, byte[]> classes = new HashMap<>(TestSources.compileWithLocals(dir, TestSources.MIXIN_ANNOTATIONS, game(order, beforePoint),
				mixin(capture, implemented)));
		MixinPreflight preflight = new MixinPreflight(new ClassIndex(classes::get), InstalledClasses.NONE, classes::get, ClassBytesSource.EMPTY, false, true,
				ClassBytesSource.EMPTY);
		MixinConfigFile config = MixinConfigFile.parse("{\"required\":true,\"package\":\"com.example.mixin\",\"mixins\":[\"Sticky\"]}");
		return preflight.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
	}

	private static final String HARD = "locals = LocalCapture.CAPTURE_FAILHARD";
	private static final String CAPTURE = "capture(L" + LEVEL + "Pos;ILorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;L" + LEVEL
			+ "State;IL" + LEVEL + "Pos;)V";
	private static final String LINE = "line(L" + LEVEL + "State;L" + LEVEL + "State;)Z";

	@Test
	void aCaptureMixinSkipsEverywhereGoesWithTheInjectorsKeepingStateWithIt(@TempDir Path dir) throws Exception {
		List<PreflightFinding> findings = check(dir, PLATFORM, "", HARD, "");
		assertTrue(findings.stream().allMatch(f -> f.severity() == Severity.LEFT_OUT), findings.toString());
		assertEquals(List.of(CAPTURE, LINE), findings.stream().map(PreflightFinding::handler).toList(), "the hook sharing nothing stays: " + findings);
		assertEquals("@Inject capture captures the locals (State, int, Pos) in addBlockLine(Pos, int), where Mixin finds (State, int, State) in"
				+ " NeoForge's code at each of its points: Mixin skips it there, so it is left out (SEAM-2)", findings.get(0).message());
		assertEquals("The injector of " + LINE + " goes with that of " + CAPTURE + ", which can't apply: they keep state together in this mixin's field"
				+ " linePos", findings.get(1).message());

		// As the soften step leaves it (skipping itself, required nowhere), the same.
		List<PreflightFinding> softened = check(dir.resolve("soft"), PLATFORM, "", "locals = LocalCapture.CAPTURE_FAILSOFT, require = 0", "");
		assertEquals(List.of(CAPTURE, LINE), softened.stream().map(PreflightFinding::handler).toList(), softened.toString());
	}

	@Test
	void aCaptureFindingTheLocalsItTakesStays(@TempDir Path dir) throws Exception {
		List<PreflightFinding> findings = check(dir, VANILLA, "", HARD, "");
		assertTrue(findings.isEmpty(), findings.toString());
	}

	@Test
	void aCaptureWhoseLocalsAFrameMayChangeIsNotJudged(@TempDir Path dir) throws Exception {
		// A branch between the stores and the point: Mixin may give each slot another local at the frame there.
		List<PreflightFinding> findings = check(dir, PLATFORM, " if (i > 100) i = 100;", HARD, "");
		assertTrue(findings.isEmpty(), findings.toString());
	}

	@Test
	void aSkippedCaptureStaysWhereLeavingItsStateOutWouldDisableTheMod(@TempDir Path dir) throws Exception {
		// The mixin adds an interface the mod's code may cast to: its injectors can't be left out without disabling the mod.
		List<PreflightFinding> findings = check(dir, PLATFORM, "", HARD, " implements com.example.Duck");
		assertEquals(List.of(Severity.INFO), findings.stream().map(PreflightFinding::severity).toList(), findings.toString());
		assertTrue(findings.get(0).message().endsWith(": Mixin skips it there; it stays, and the injectors keeping state with it too, as leaving them out"
				+ " would disable the mod (this mixin adds an interface or accessors the mod's code uses; SEAM-2)"), findings.get(0).message());
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
