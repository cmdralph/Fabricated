package io.github.cmdralph.fabricated.core.bytecode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.cmdralph.fabricated.core.cache.TranslationCache;
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
		return breaking(dir, VanillaCallsTest.neoForgeSources(), nativeMembers, nativeTarget);
	}

	private static SeamConflicts.Conflict breaking(Path dir, Map<String, String> gameSources, String nativeMembers, String nativeTarget) throws Exception {
		Map<String, String> sources = new HashMap<>(gameSources);
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
		// Level's removeBlock, which NeoForge's default calls too, but Forge's doesn't: there, vanilla's call put back runs it
		// for player breaks it never saw, so the class stays as Forge has it.
		String level = " @Inject(method = \"removeBlock\", at = @At(\"HEAD\")) private void head(CallbackInfo ci) { }";
		assertNull(breaking(dir.resolve("level"), level, "net.minecraft.world.level.Level"));
		assertEquals(expected, breaking(dir.resolve("forge-level"), VanillaCallsTest.forgeSources(), level, "net.minecraft.world.level.Level"));
		assertEquals(expected, breaking(dir.resolve("forge-level-srg"), VanillaCallsTest.forgeSources(), level.replace("removeBlock", "m_7471_"),
				"net.minecraft.server.level.ServerLevel"));
		assertNull(breaking(dir.resolve("forge-level-other"), VanillaCallsTest.forgeSources(), level.replace("removeBlock", "setBlock"),
				"net.minecraft.world.level.Level"));
		// A constant there, which vanilla's calls bring more of (their false, their 11).
		assertEquals(expected, breaking(dir.resolve("constant"), " @Inject(method = \"destroyBlock\", at = @At(value = \"CONSTANT\")) private void constant(CallbackInfo ci) { }",
				gameMode));
		// What stays: destroyBlock's start, and a call NeoForge makes there.
		assertNull(breaking(dir.resolve("head"), " @Inject(method = \"destroyBlock\", at = @At(\"HEAD\")) private void head(CallbackInfo ci) { }", gameMode));
		assertNull(breaking(dir.resolve("kept-call"), " @Inject(method = \"destroyBlock\", at = @At(value = \"INVOKE\", target ="
				+ " \"Lnet/minecraft/world/level/block/Block;playerWillDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
				+ "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/level/block/state/BlockState;\"))"
				+ " private void call(CallbackInfo ci) { }", gameMode));
	}

	/**
	 * The decision is kept from one launch to the next, keyed by the native jars' fingerprints (path, size, modification
	 * time): reused while they hold, made again when one changes or the cached file is damaged, never cached for a jar
	 * listing mixins without one.
	 */
	@Test
	void theDecisionIsCachedByTheNativeJarsFingerprints(@TempDir Path dir, @TempDir Path game, @TempDir Path mods) throws Exception {
		Map<String, byte[]> classes = new HashMap<>(classes(dir, wrap("\"resolve\"", STATE.substring(0, STATE.length() - 1) + ";isStickyBlock()Z"),
				"net.minecraft.world.level.block.piston.PistonStructureResolver"));
		ClassBytesSource gameClasses = classes::get;
		int[] reads = {0};
		ClassBytesSource counted = name -> {
			if (name.startsWith("com/moon/")) reads[0]++;
			return classes.get(name);
		};
		// The mod's file, whose path, size and modification time the decision is cached by.
		Path file = mods.resolve("moonlight.jar");
		Files.write(file, new byte[] {1});
		Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000));
		Function<String, InstalledClasses> installed = fingerprint -> new InstalledClasses(List.of(new InstalledClasses.Jar("moonlight",
				classes::containsKey, counted, () -> new InstalledClasses.Listing(List.of("com/moon/NativeSticky"), List.of()), false, fingerprint)));
		Supplier<InstalledClasses> current = () -> installed.apply(InstalledClasses.fingerprint(file, ""));
		TranslationCache cache = new TranslationCache(game, true, true);
		Map<String, SeamConflicts.Conflict> decided = Map.of(RESOLVER, new SeamConflicts.Conflict("moonlight", "com/moon/NativeSticky", true));

		// A miss: decided, and stored.
		assertEquals(decided, SeamConflicts.of(gameClasses, current.get(), cache, "env"));
		assertEquals(1, reads[0]);
		// A hit: the same jars, the mixins not read again.
		assertEquals(decided, SeamConflicts.of(gameClasses, current.get(), cache, "env"));
		assertEquals(1, reads[0], "a hit reads no mixin");
		// The jar changed: decided again (and so in another environment).
		Files.write(file, new byte[] {1, 2});
		Files.setLastModifiedTime(file, FileTime.fromMillis(2_000_000));
		assertEquals(decided, SeamConflicts.of(gameClasses, current.get(), cache, "env"));
		assertEquals(2, reads[0], "a changed jar is read again");
		assertEquals(decided, SeamConflicts.of(gameClasses, current.get(), cache, "env"));
		assertEquals(2, reads[0]);
		assertEquals(decided, SeamConflicts.of(gameClasses, current.get(), cache, "another build"));
		assertEquals(3, reads[0]);

		// A damaged or unreadable file: decided again and rewritten, never an error.
		for (byte[] damaged : List.of("not a decision".getBytes(StandardCharsets.UTF_8), new byte[0])) {
			try (Stream<Path> blobs = Files.list(cache.root().resolve("blobs"))) {
				for (Path blob : blobs.filter(p -> p.getFileName().toString().startsWith("seams-")).toList()) Files.write(blob, damaged);
			}
			int before = reads[0];
			assertEquals(decided, SeamConflicts.of(gameClasses, current.get(), cache, "env"));
			assertEquals(before + 1, reads[0], "a damaged file is decided again");
			assertEquals(decided, SeamConflicts.of(gameClasses, current.get(), cache, "env"));
			assertEquals(before + 1, reads[0], "and rewritten");
		}
		// A blob whose hash holds but which isn't a decision (another version's): decided again.
		try (Stream<Path> blobs = Files.list(cache.root().resolve("blobs"))) {
			for (Path blob : blobs.filter(p -> p.getFileName().toString().startsWith("seams-")).toList()) {
				cache.storeBlob(blob.getFileName().toString(), "{\"kept\": {\"a/B\": 1}}".getBytes(StandardCharsets.UTF_8));
			}
		}
		int before = reads[0];
		assertEquals(decided, SeamConflicts.of(gameClasses, current.get(), cache, "env"));
		assertEquals(before + 1, reads[0]);

		// A jar listing mixins without a fingerprint (a mod in development is a folder): never cached.
		assertNull(InstalledClasses.fingerprint(mods, ""));
		before = reads[0];
		assertEquals(decided, SeamConflicts.of(gameClasses, installed.apply(null), cache, "env"));
		assertEquals(decided, SeamConflicts.of(gameClasses, installed.apply(null), cache, "env"));
		assertEquals(before + 2, reads[0]);
		// A jar listing none needs none.
		assertEquals(Map.of(), SeamConflicts.of(gameClasses, new InstalledClasses(List.of(new InstalledClasses.Jar("api", classes::containsKey, counted))),
				cache, "env"));
	}

	/**
	 * A read error on one launch (EIO on a network mount, a sync client holding the file) keeps the class for that launch,
	 * and isn't cached: the next launch, reading cleanly, decides again, and caches that.
	 */
	@Test
	void aDecisionAReadErrorMadeIsntCached(@TempDir Path dir, @TempDir Path game, @TempDir Path mods) throws Exception {
		Map<String, byte[]> classes = new HashMap<>(classes(dir, wrap("\"resolve\"", STATE.substring(0, STATE.length() - 1) + ";isStickyBlock()Z"),
				"net.minecraft.world.level.block.piston.PistonStructureResolver"));
		ClassBytesSource gameClasses = classes::get;
		boolean[] listFails = {false};
		boolean[] mixinFails = {false};
		int[] reads = {0};
		ClassBytesSource flaky = name -> {
			if (name.startsWith("com/moon/")) {
				reads[0]++;
				if (mixinFails[0]) throw new IOException("EIO");
			}
			return classes.get(name);
		};
		Path file = mods.resolve("moonlight.jar");
		Files.write(file, new byte[] {1});
		Supplier<InstalledClasses> installed = () -> new InstalledClasses(List.of(new InstalledClasses.Jar("moonlight", classes::containsKey, flaky,
				() -> listFails[0] ? new InstalledClasses.Listing(List.of(), List.of("moonlight.mixins.json"))
						: new InstalledClasses.Listing(List.of("com/moon/NativeSticky"), List.of()), false, InstalledClasses.fingerprint(file, ""))));
		TranslationCache cache = new TranslationCache(game, true, true);
		Map<String, SeamConflicts.Conflict> decided = Map.of(RESOLVER, new SeamConflicts.Conflict("moonlight", "com/moon/NativeSticky", true));

		// The jar's mixin config fails to read: it lists none that read, so without it the seams would be put back, and
		// cached so, Moonlight's mixin then failing at every launch. The class is kept, by the list that failed.
		listFails[0] = true;
		SeamConflicts.Decision unclean = SeamConflicts.decide(gameClasses, installed.get());
		assertFalse(unclean.clean());
		assertEquals(Map.of(RESOLVER, new SeamConflicts.Conflict("moonlight", "moonlight.mixins.json", false)), unclean.kept());
		assertEquals(unclean.kept(), SeamConflicts.of(gameClasses, installed.get(), cache, "env"));
		assertTrue(unclean.kept().get(RESOLVER).describe("NeoForge").contains("moonlight.mixins.json, which lists its mixins, failed to read"));
		// Read cleanly next launch: decided again, and cached.
		listFails[0] = false;
		assertEquals(decided, SeamConflicts.of(gameClasses, installed.get(), cache, "env"));
		int before = reads[0];
		assertEquals(decided, SeamConflicts.of(gameClasses, installed.get(), cache, "env"));
		assertEquals(before, reads[0], "the clean decision was cached");

		// The mixin fails to read: kept for the launch as unreadable, not cached; read cleanly, a mixin hooking only what
		// stays lets the seams be put back.
		Map<String, byte[]> staying = new HashMap<>(classes(dir.resolve("staying"), " @Inject(method = \"resolve\", at = @At(\"HEAD\"))"
				+ " private void start(BlockState s, CallbackInfo ci) { }", "net.minecraft.world.level.block.piston.PistonStructureResolver"));
		classes.putAll(staying);
		Files.write(file, new byte[] {1, 2});
		Files.setLastModifiedTime(file, FileTime.fromMillis(3_000_000));
		mixinFails[0] = true;
		assertEquals(Map.of(RESOLVER, new SeamConflicts.Conflict("moonlight", "com/moon/NativeSticky", false)),
				SeamConflicts.of(gameClasses, installed.get(), cache, "env"));
		mixinFails[0] = false;
		assertEquals(Map.of(), SeamConflicts.of(gameClasses, installed.get(), cache, "env"), "decided again: the seams are put back");
		before = reads[0];
		assertEquals(Map.of(), SeamConflicts.of(gameClasses, installed.get(), cache, "env"));
		assertEquals(before, reads[0], "and cached");
	}

	/**
	 * A seam class that fails to read as the platform has it (an error from its transformers) keeps the class, no error
	 * reaching the caller, and isn't cached.
	 */
	@Test
	void aSeamClassThatFailsToReadIsKept(@TempDir Path dir, @TempDir Path game, @TempDir Path mods) throws Exception {
		Map<String, byte[]> classes = new HashMap<>(classes(dir, " @Inject(method = \"resolve\", at = @At(\"HEAD\"))"
				+ " private void start(BlockState s, CallbackInfo ci) { }", "net.minecraft.world.level.block.piston.PistonStructureResolver"));
		boolean[] fails = {true};
		ClassBytesSource gameClasses = name -> {
			if (fails[0] && name.equals(RESOLVER)) throw new java.io.UncheckedIOException(new IOException("a transformer failed"));
			return classes.get(name);
		};
		Path file = mods.resolve("moonlight.jar");
		Files.write(file, new byte[] {1});
		InstalledClasses installed = new InstalledClasses(List.of(new InstalledClasses.Jar("moonlight", classes::containsKey, classes::get,
				() -> new InstalledClasses.Listing(List.of("com/moon/NativeSticky"), List.of()), false, InstalledClasses.fingerprint(file, ""))));
		TranslationCache cache = new TranslationCache(game, true, true);

		Map<String, SeamConflicts.Conflict> kept = SeamConflicts.of(gameClasses, installed, cache, "env");
		assertEquals(Set.of(RESOLVER), kept.keySet());
		assertEquals("", kept.get(RESOLVER).owner());
		assertTrue(kept.get(RESOLVER).describe("NeoForge").startsWith("the class failed to read as NeoForge has it"));
		assertFalse(SeamConflicts.decide(gameClasses, installed).clean());
		fails[0] = false;
		assertEquals(Map.of(), SeamConflicts.of(gameClasses, installed, cache, "env"), "read next launch: decided again, the seams put back");
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
