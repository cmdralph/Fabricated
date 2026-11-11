package io.github.cmdralph.fabricated.core.bytecode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.cmdralph.fabricated.core.testutil.TestSources;

/**
 * SEAM-1: a class's seams aren't put back where a native mod's mixin hooks the platform's code that would move (Moonlight's
 * NeoForge build wraps the resolver's {@code isStickyBlock} and {@code canStickTo} calls), or where what it hooks can't
 * be told; they are where its mixins hook only what stays.
 */
class SeamConflictsTest {
	private static final String RESOLVER = "net/minecraft/world/level/block/piston/PistonStructureResolver";
	private static final String STATE = "Lnet/minecraft/world/level/block/state/BlockState;";

	/** The platform's resolver (NeoForge's: the extensions asked where vanilla called its methods) and a native mod's mixin. */
	private static Map<String, byte[]> classes(Path dir, String nativeMembers, String nativeTarget) throws Exception {
		return TestSources.compile(dir, TestSources.MIXIN_ANNOTATIONS, Map.of(
				"net/minecraft/world/level/block/state/BlockState.java", "package net.minecraft.world.level.block.state; public class BlockState {"
						+ " public boolean isAir() { return false; } public boolean isStickyBlock() { return false; }"
						+ " public boolean canStickTo(BlockState other) { return false; } }",
				RESOLVER + ".java", "package net.minecraft.world.level.block.piston; import net.minecraft.world.level.block.state.BlockState;"
						+ " public class PistonStructureResolver { boolean blocked(BlockState s) { return false; } void other() { }"
						+ " public boolean resolve(BlockState s) { return s.isStickyBlock(); }"
						+ " boolean addBranchingBlocks(BlockState n, BlockState f) { if (n.canStickTo(f) && f.canStickTo(n) && !blocked(n)) return false; return true; } }",
				"net/minecraft/world/level/Other.java", "package net.minecraft.world.level; public class Other { }",
				"org/spongepowered/asm/mixin/injection/At.java", "package org.spongepowered.asm.mixin.injection; public @interface At { String value();"
						+ " String target() default \"\"; int ordinal() default -1; }",
				"org/spongepowered/asm/mixin/injection/Inject.java", "package org.spongepowered.asm.mixin.injection; public @interface Inject { String[] method(); At[] at(); }",
				"org/spongepowered/asm/mixin/injection/callback/CallbackInfo.java", "package org.spongepowered.asm.mixin.injection.callback; public class CallbackInfo { }",
				"com/llamalad7/mixinextras/injector/wrapoperation/WrapOperation.java", "package com.llamalad7.mixinextras.injector.wrapoperation;"
						+ " public @interface WrapOperation { String[] method(); org.spongepowered.asm.mixin.injection.At[] at(); }",
				"com/llamalad7/mixinextras/injector/wrapoperation/Operation.java", "package com.llamalad7.mixinextras.injector.wrapoperation; public interface Operation<R> { R call(Object... args); }",
				"com/moon/NativeSticky.java", "package com.moon; import org.spongepowered.asm.mixin.*; import org.spongepowered.asm.mixin.injection.*;"
						+ " import org.spongepowered.asm.mixin.injection.callback.CallbackInfo; import com.llamalad7.mixinextras.injector.wrapoperation.*;"
						+ " import net.minecraft.world.level.block.state.BlockState;"
						+ " @Mixin(" + nativeTarget + ".class) public abstract class NativeSticky {" + nativeMembers + " }"));
	}

	private static InstalledClasses nativeJar(Map<String, byte[]> classes, List<String> mixins) {
		return new InstalledClasses(List.of(new InstalledClasses.Jar("moonlight", classes::containsKey, classes::get, () -> mixins)));
	}

	private static SeamConflicts.Conflict conflict(Path dir, String nativeMembers) throws Exception {
		return conflict(dir, nativeMembers, "net.minecraft.world.level.block.piston.PistonStructureResolver");
	}

	private static SeamConflicts.Conflict conflict(Path dir, String nativeMembers, String nativeTarget) throws Exception {
		Map<String, byte[]> classes = new HashMap<>(classes(dir, nativeMembers, nativeTarget));
		ClassBytesSource game = classes::get;
		return SeamConflicts.find(game.readNode(RESOLVER, 0), nativeJar(classes, List.of("com/moon/NativeSticky")));
	}

	private static String wrap(String methods, String target) {
		return " @WrapOperation(method = {" + methods + "}, at = @At(value = \"INVOKE\", target = \"" + target + "\"))"
				+ " private boolean wrapped(BlockState s, Operation<Boolean> original) { return original.call(s); }";
	}

	@Test
	void aNativeMixinHookingTheCallsThatWouldMoveKeepsTheClass(@TempDir Path dir) throws Exception {
		// As Moonlight's NeoForge build: a wrap of the isStickyBlock call in resolve.
		SeamConflicts.Conflict found = conflict(dir, wrap("\"resolve\"", STATE.substring(0, STATE.length() - 1) + ";isStickyBlock()Z"));
		assertEquals(new SeamConflicts.Conflict("moonlight", "com/moon/NativeSticky", true), found);
		assertEquals("NeoForge mod moonlight's mixin NativeSticky hooks the platform's code that putting vanilla's method back would move",
				found.describe("NeoForge"));
	}

	@Test
	void aNativeMixinHookingWhatStaysLetsTheSeamsBePutBack(@TempDir Path dir) throws Exception {
		// At the start of a method holding moved calls, at another call there, and in a method holding none.
		assertNull(conflict(dir.resolve("head"), " @Inject(method = \"resolve\", at = @At(\"HEAD\")) private void start(BlockState s, CallbackInfo ci) { }"));
		assertNull(conflict(dir.resolve("call"), " @Inject(method = \"addBranchingBlocks\", at = @At(value = \"INVOKE\", target = \"L" + RESOLVER + ";blocked("
				+ STATE + ")Z\")) private void call(BlockState n, BlockState f, CallbackInfo ci) { }"));
		assertNull(conflict(dir.resolve("jump-elsewhere"), " @Inject(method = \"other\", at = @At(value = \"JUMP\", ordinal = 0)) private void jump(CallbackInfo ci) { }"));
		// A mixin of another class, though it hooks the extension there.
		assertNull(conflict(dir.resolve("elsewhere"), wrap("\"*\"", STATE.substring(0, STATE.length() - 1) + ";isStickyBlock()Z"),
				"net.minecraft.world.level.Other"));
	}

	@Test
	void whatCantBeToldKeepsTheClass(@TempDir Path dir) throws Exception {
		// A wildcard selector, a name the class doesn't have (a development name a refmap maps), a point the move shifts.
		Map<String, String> cases = Map.of(
				"wildcard", " @Inject(method = \"*\", at = @At(\"HEAD\")) private void any(CallbackInfo ci) { }",
				"unknown", " @Inject(method = \"m_60422_\", at = @At(\"HEAD\")) private void unknown(BlockState s, CallbackInfo ci) { }",
				"jump", " @Inject(method = \"addBranchingBlocks\", at = @At(value = \"JUMP\", ordinal = 1)) private void jump(BlockState n, BlockState f, CallbackInfo ci) { }");
		for (Map.Entry<String, String> e : cases.entrySet()) {
			assertEquals(new SeamConflicts.Conflict("moonlight", "com/moon/NativeSticky", true), conflict(dir.resolve(e.getKey()), e.getValue()), e.getKey());
		}
		// A mixin the jar lists but doesn't have: what it hooks can't be read.
		Map<String, byte[]> classes = new HashMap<>(classes(dir.resolve("missing"), "", "net.minecraft.world.level.block.piston.PistonStructureResolver"));
		ClassBytesSource game = classes::get;
		assertEquals(new SeamConflicts.Conflict("moonlight", "com/moon/Gone", false),
				SeamConflicts.find(game.readNode(RESOLVER, 0), nativeJar(classes, List.of("com/moon/Gone"))));
	}

	/**
	 * SEAM-3: the conflict, if any, of a native mod's mixin with putting vanilla's calls back in NeoForge's server game
	 * mode, the mixin targeting {@code nativeTarget}.
	 */
	private static SeamConflicts.Conflict breaking(Path dir, String nativeMembers, String nativeTarget) throws Exception {
		Map<String, String> sources = new HashMap<>(VanillaCallsTest.neoForgeSources());
		sources.put("org/spongepowered/asm/mixin/injection/At.java", "package org.spongepowered.asm.mixin.injection; public @interface At { String value();"
				+ " String target() default \"\"; }");
		sources.put("org/spongepowered/asm/mixin/injection/Inject.java", "package org.spongepowered.asm.mixin.injection; public @interface Inject { String[] method(); At[] at(); }");
		sources.put("org/spongepowered/asm/mixin/injection/callback/CallbackInfo.java", "package org.spongepowered.asm.mixin.injection.callback; public class CallbackInfo { }");
		sources.put("com/moon/NativeBreak.java", "package com.moon; import org.spongepowered.asm.mixin.*; import org.spongepowered.asm.mixin.injection.*;"
				+ " import org.spongepowered.asm.mixin.injection.callback.CallbackInfo; @Mixin(" + nativeTarget + ".class) public abstract class NativeBreak {"
				+ nativeMembers + " }");
		Map<String, byte[]> classes = new HashMap<>(TestSources.compile(dir, TestSources.MIXIN_ANNOTATIONS, sources));
		ClassBytesSource game = classes::get;
		InstalledClasses installed = new InstalledClasses(List.of(new InstalledClasses.Jar("breaker", classes::containsKey, classes::get,
				() -> List.of("com/moon/NativeBreak"))));
		return SeamConflicts.find(game.readNode("net/minecraft/server/level/ServerPlayerGameMode", 0), installed);
	}

	@Test
	void aNativeMixinOfWhatPuttingVanillasCallsBackBypassesKeepsTheClass(@TempDir Path dir) throws Exception {
		String gameMode = "net.minecraft.server.level.ServerPlayerGameMode";
		SeamConflicts.Conflict expected = new SeamConflicts.Conflict("breaker", "com/moon/NativeBreak", true);
		// The platform's removeBlock, which the blocks keeping the default no longer reach.
		assertEquals(expected, breaking(dir.resolve("helper"), " @Inject(method = \"removeBlock\", at = @At(\"HEAD\")) private void head(CallbackInfo ci) { }", gameMode));
		// The extension's default, which they no longer call.
		assertEquals(expected, breaking(dir.resolve("default"), " @Inject(method = \"onDestroyedByPlayer\", at = @At(\"HEAD\")) private void head(CallbackInfo ci) { }",
				"net.neoforged.neoforge.common.extensions.IBlockExtension"));
		// Vanilla's call the rewrite adds to destroyBlock, which NeoForge's code doesn't make there.
		assertEquals(expected, breaking(dir.resolve("added"), " @Inject(method = \"destroyBlock\", at = @At(value = \"INVOKE\", target ="
				+ " \"Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z\")) private void call(CallbackInfo ci) { }", gameMode));
		assertEquals("NeoForge mod breaker's mixin NativeBreak hooks the platform's code that putting vanilla's calls back would move",
				expected.describe("NeoForge", "vanilla's calls"));
		// What stays: destroyBlock's start, and a call NeoForge makes there.
		assertNull(breaking(dir.resolve("head"), " @Inject(method = \"destroyBlock\", at = @At(\"HEAD\")) private void head(CallbackInfo ci) { }", gameMode));
		assertNull(breaking(dir.resolve("kept-call"), " @Inject(method = \"destroyBlock\", at = @At(value = \"INVOKE\", target ="
				+ " \"Lnet/minecraft/world/level/block/Block;playerWillDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
				+ "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/level/block/state/BlockState;\"))"
				+ " private void call(CallbackInfo ci) { }", gameMode));
	}

	@Test
	void theDecisionIsPartOfTheCacheKeyAndTheGamesView(@TempDir Path dir) throws Exception {
		Map<String, byte[]> classes = new HashMap<>(classes(dir, wrap("\"resolve\"", STATE.substring(0, STATE.length() - 1) + ";isStickyBlock()Z"),
				"net.minecraft.world.level.block.piston.PistonStructureResolver"));
		ClassBytesSource game = classes::get;
		Map<String, SeamConflicts.Conflict> kept = SeamConflicts.of(game, nativeJar(classes, List.of("com/moon/NativeSticky")));
		assertEquals(Set.of(RESOLVER), kept.keySet());
		assertEquals("seams kept: " + RESOLVER + " kept by moonlight com/moon/NativeSticky", SeamConflicts.key(kept));
		assertEquals("seams put back", SeamConflicts.key(SeamConflicts.of(game, InstalledClasses.NONE)));
		// The game's view keeps the class as the platform has it.
		assertEquals(List.of(), VanillaSeams.restoredIn(VanillaSeams.over(game, kept.keySet()).readNode(RESOLVER, 0)));
		assertEquals(2, VanillaSeams.restoredIn(VanillaSeams.over(game).readNode(RESOLVER, 0)).size());
	}
}
