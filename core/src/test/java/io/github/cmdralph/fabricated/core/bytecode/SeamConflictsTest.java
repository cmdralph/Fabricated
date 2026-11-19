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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

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
		return stripped(SeamConflicts.find(game.readNode(RESOLVER, 0), nativeJar(classes, List.of("com/moon/NativeSticky"))));
	}

	/** The conflict without what the mixin hooks, which the report names and the tests of that check apart. */
	static SeamConflicts.Conflict stripped(SeamConflicts.Conflict conflict) {
		return conflict == null ? null : new SeamConflicts.Conflict(conflict.owner(), conflict.mixin(), conflict.readable());
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
	 * A native mixin naming the class whose annotations nest too deep to read (ASM reads them recursively) keeps the class,
	 * as one that doesn't parse does, instead of failing the discovery.
	 */
	@Test
	void aNativeMixinNestedTooDeeplyToReadKeepsTheClass(@TempDir Path dir) throws Exception {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/moon/Deep", null, "java/lang/Object", null);
		AnnotationVisitor root = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor targets = root.visitArray("targets");
		targets.visit(null, "net.minecraft.world.level.block.piston.PistonStructureResolver");
		targets.visitEnd();
		Deque<AnnotationVisitor> open = new ArrayDeque<>();
		AnnotationVisitor array = root.visitArray("value");
		for (int i = 0; i < 200_000; i++) {
			open.push(array);
			array = array.visitArray(null);
		}
		array.visitEnd();
		while (!open.isEmpty()) open.pop().visitEnd();
		root.visitEnd();
		cw.visitEnd();
		Map<String, byte[]> classes = new HashMap<>(classes(dir, "", "net.minecraft.world.level.block.piston.PistonStructureResolver"));
		classes.put("com/moon/Deep", cw.toByteArray());
		ClassBytesSource game = classes::get;
		assertEquals(new SeamConflicts.Conflict("moonlight", "com/moon/Deep", false),
				SeamConflicts.find(game.readNode(RESOLVER, 0), nativeJar(classes, List.of("com/moon/Deep"))));
	}

	/**
	 * SEAM-3: the conflict, if any, of a native mod's mixin with putting vanilla's calls back in NeoForge's server game
	 * mode, the mixin targeting {@code nativeTarget}.
	 */
	private static SeamConflicts.Conflict breaking(Path dir, String nativeMembers, String nativeTarget) throws Exception {
		return breaking(dir, VanillaCallsTest.neoForgeSources(), nativeMembers, nativeTarget);
	}

	private static SeamConflicts.Conflict breaking(Path dir, Map<String, String> gameSources, String nativeMembers, String nativeTarget) throws Exception {
		return conflict(dir, gameSources, nativeMembers, nativeTarget, "net/minecraft/server/level/ServerPlayerGameMode");
	}

	/** As {@link #breaking(Path, Map, String, String)}, for the game's class {@code gameClass}. */
	private static SeamConflicts.Conflict conflict(Path dir, Map<String, String> gameSources, String nativeMembers, String nativeTarget, String gameClass)
			throws Exception {
		return conflict(dir, gameSources, nativeMembers, nativeTarget, gameClass, null);
	}

	/** As {@link #conflict(Path, Map, String, String, String)}, the mixin's config naming {@code refmap} (null for none). */
	private static SeamConflicts.Conflict conflict(Path dir, Map<String, String> gameSources, String nativeMembers, String nativeTarget, String gameClass,
			InstalledClasses.RefmapFile refmap) throws Exception {
		return stripped(found(dir, gameSources, nativeMembers, nativeTarget, gameClass, refmap));
	}

	/** As {@link #conflict(Path, Map, String, String, String, InstalledClasses.RefmapFile)}, with what the mixin hooks. */
	private static SeamConflicts.Conflict found(Path dir, Map<String, String> gameSources, String nativeMembers, String nativeTarget, String gameClass,
			InstalledClasses.RefmapFile refmap) throws Exception {
		Map<String, String> sources = new HashMap<>(gameSources);
		sources.put("org/spongepowered/asm/mixin/injection/At.java", "package org.spongepowered.asm.mixin.injection; public @interface At { String value();"
				+ " String target() default \"\"; }");
		sources.put("org/spongepowered/asm/mixin/injection/Inject.java", "package org.spongepowered.asm.mixin.injection; public @interface Inject { String[] method()"
				+ " default { }; At[] at(); Desc[] target() default { }; }");
		sources.put("org/spongepowered/asm/mixin/injection/Desc.java", "package org.spongepowered.asm.mixin.injection; public @interface Desc { String value(); }");
		sources.put("org/spongepowered/asm/mixin/injection/callback/CallbackInfo.java", "package org.spongepowered.asm.mixin.injection.callback; public class CallbackInfo { }");
		sources.put("com/moon/NativeBreak.java", "package com.moon; import org.spongepowered.asm.mixin.*; import org.spongepowered.asm.mixin.injection.*;"
				+ " import org.spongepowered.asm.mixin.injection.callback.CallbackInfo; @Mixin("
				+ (nativeTarget.startsWith("targets") ? nativeTarget : nativeTarget + ".class") + ") public abstract class NativeBreak {"
				+ nativeMembers + " }");
		// Mixin's own annotation, with the string targets of a class a mixin can't name in code.
		sources.put("org/spongepowered/asm/mixin/Mixin.java", "package org.spongepowered.asm.mixin; public @interface Mixin { Class<?>[] value() default { };"
				+ " String[] targets() default { }; boolean remap() default true; }");
		Map<String, byte[]> classes = new HashMap<>(TestSources.compile(dir, TestSources.MIXIN_ANNOTATIONS, sources));
		// A 26.x game's class, read as Java 25's, as the game's are: the rows take 26.x's forms then.
		if (gameSources.values().stream().anyMatch(text -> text.contains(JAVA_25))) classes.get(gameClass)[7] = 69;
		ClassBytesSource game = classes::get;
		InstalledClasses installed = new InstalledClasses(List.of(new InstalledClasses.Jar("breaker", classes::containsKey, classes::get,
				() -> new InstalledClasses.Listing(List.of("com/moon/NativeBreak"), List.of(), refmap == null ? Map.of() : Map.of("com/moon/NativeBreak", refmap)),
				false, null)));
		return SeamConflicts.find(game.readNode(gameClass, 0), installed);
	}

	/**
	 * SEAM-4: a native mod's mixin of what putting vanilla's harvest check back moves or bypasses keeps the class as the
	 * platform has it: the platform's calls there (the state's canHarvestBlock, its hook in getDestroyProgress), the
	 * extension's default and NeoForge's EventHooks.doPlayerHarvestCheck, which those blocks no longer reach, and on Forge
	 * Player.hasCorrectToolForDrops, which vanilla's call put back makes where Forge's default only fires its event.
	 */
	@Test
	void aNativeMixinOfWhatPuttingTheHarvestCheckBackChangesKeepsTheClass(@TempDir Path dir) throws Exception {
		String gameMode = "net.minecraft.server.level.ServerPlayerGameMode";
		String behaviour = "net/minecraft/world/level/block/state/BlockBehaviour";
		SeamConflicts.Conflict expected = new SeamConflicts.Conflict("breaker", "com/moon/NativeBreak", true);
		String head = " @Inject(method = \"%s\", at = @At(\"HEAD\")) private void head(CallbackInfo ci) { }";
		assertEquals(expected, breaking(dir.resolve("call"), " @Inject(method = \"destroyBlock\", at = @At(value = \"INVOKE\", target ="
				+ " \"Lnet/minecraft/world/level/block/state/BlockState;canHarvestBlock(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;"
				+ "Lnet/minecraft/world/entity/player/Player;)Z\")) private void call(CallbackInfo ci) { }", gameMode));
		assertEquals(expected, breaking(dir.resolve("default"), head.formatted("canHarvestBlock"), "net.neoforged.neoforge.common.extensions.IBlockExtension"));
		assertEquals(expected, breaking(dir.resolve("hook"), head.formatted("doPlayerHarvestCheck"), "net.neoforged.neoforge.event.EventHooks"));
		// NeoForge's default makes vanilla's call too: a mixin into it changes nothing there.
		assertNull(breaking(dir.resolve("player"), head.formatted("hasCorrectToolForDrops"), "net.minecraft.world.entity.player.Player"));
		assertEquals(expected, breaking(dir.resolve("forge-player"), VanillaCallsTest.forgeSources(), head.formatted("hasCorrectToolForDrops"),
				"net.minecraft.world.entity.player.Player"));
		assertEquals(expected, breaking(dir.resolve("forge-player-srg"), VanillaCallsTest.forgeSources(), head.formatted("m_36298_"),
				"net.minecraft.world.entity.player.Player"));
		assertEquals(expected, breaking(dir.resolve("forge-hook"), VanillaCallsTest.forgeSources(), head.formatted("isCorrectToolForDrops"),
				"net.minecraftforge.common.ForgeHooks"));
		assertNull(breaking(dir.resolve("forge-player-other"), VanillaCallsTest.forgeSources(), head.formatted("tick"), "net.minecraft.world.entity.player.Player"));
		// getDestroyProgress: the hook's call there moves; its start stays.
		assertEquals(expected, conflict(dir.resolve("progress-call"), VanillaCallsTest.neoForgeSources(), " @Inject(method = \"getDestroyProgress\", at = @At("
				+ "value = \"INVOKE\", target = \"Lnet/neoforged/neoforge/event/EventHooks;doPlayerHarvestCheck(Lnet/minecraft/world/entity/player/Player;"
				+ "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z\"))"
				+ " private void call(CallbackInfo ci) { }", behaviour.replace('/', '.'), behaviour));
		assertNull(conflict(dir.resolve("progress-head"), VanillaCallsTest.neoForgeSources(), head.formatted("getDestroyProgress"), behaviour.replace('/', '.'),
				behaviour));
		assertEquals(expected, conflict(dir.resolve("progress-forge-player"), VanillaCallsTest.forgeSources(), head.formatted("hasCorrectToolForDrops"),
				"net.minecraft.world.entity.player.Player", behaviour));
	}

	/** Marks a test's game sources as a 26.x game's, whose class the conflict helper reads as Java 25's. */
	private static final String JAVA_25 = "a 26.x game, its classes Java 25's";

	/**
	 * SEAM-12: a native mod's mixin of what putting vanilla's checks back in a crop's growth speed bypasses on Forge keeps
	 * the class as the platform has it: the crop's {@code mayPlaceOn}, its plant ({@code BushBlock}'s, {@code IPlantable}'s)
	 * and its plant type, which Block's {@code canSustainPlant} asks and the crops keeping them no longer reach, as the
	 * extensions' defaults. One of another of Block's methods doesn't. And what the rows name besides: vanilla's checks the defaults
	 * don't make, {@code BlockStateBase.is} and 26.x's {@code StateHolder.getValueOrElse}.
	 */
	@Test
	void aNativeMixinOfWhatPuttingTheGrowthChecksBackBypassesKeepsTheClass(@TempDir Path dir) throws Exception {
		String crop = "net/minecraft/world/level/block/CropBlock";
		SeamConflicts.Conflict expected = new SeamConflicts.Conflict("breaker", "com/moon/NativeBreak", true);
		String head = " @Inject(method = \"%s\", at = @At(\"HEAD\")) private void head(CallbackInfo ci) { }";
		Map<String, String> forge = CropGrowthTest.sources(CropGrowthTest.Form.FORGE_52);
		Map<String, String> forge64 = new HashMap<>(CropGrowthTest.sources(CropGrowthTest.Form.FORGE_64));
		forge64.put("java25/Marker.java", "package java25; /** " + JAVA_25 + " */ class Marker { }");
		Map<String, String> neoForge26 = new HashMap<>(CropGrowthTest.sources(CropGrowthTest.Form.NEOFORGE_26));
		neoForge26.put("java25/Marker.java", "package java25; /** " + JAVA_25 + " */ class Marker { }");
		assertEquals(expected, conflict(dir.resolve("may-place-on"), forge, head.formatted("mayPlaceOn"), crop.replace('/', '.'), crop));
		assertEquals(expected, conflict(dir.resolve("plant"), forge, head.formatted("getPlant"), "net.minecraft.world.level.block.BushBlock", crop));
		assertEquals(expected, conflict(dir.resolve("plant-type"), forge, head.formatted("getPlantType"), "net.minecraftforge.common.IPlantable", crop));
		assertEquals(expected, conflict(dir.resolve("sustain"), forge, head.formatted("canSustainPlant"), "net.minecraft.world.level.block.Block", crop));
		assertNull(conflict(dir.resolve("block-other"), forge, head.formatted("defaultBlockState"), "net.minecraft.world.level.block.Block", crop));
		// The report's line names the native mod, its mixin and the method it hooks.
		SeamConflicts.Conflict placing = found(dir.resolve("may-place-on-named"), forge, head.formatted("mayPlaceOn"), crop.replace('/', '.'), crop, null);
		assertEquals("CropBlock.mayPlaceOn", placing.hooked());
		assertEquals("Forge mod breaker's mixin NativeBreak hooks the platform's code that putting vanilla's calls back would move, CropBlock.mayPlaceOn",
				placing.describe("Forge", "vanilla's calls"));
		assertEquals("IPlantable.getPlantType", found(dir.resolve("plant-type-named"), forge, head.formatted("getPlantType"), "net.minecraftforge.common.IPlantable",
				crop, null).hooked());
		Map<String, Set<String>> others = VanillaSeams.mayChange(crop);
		assertTrue(others.getOrDefault("net/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase", Set.of()).containsAll(Set.of("is", "m_60713_")), others.toString());
		assertTrue(others.getOrDefault("net/minecraft/world/level/block/state/StateHolder", Set.of()).contains("getValueOrElse"), others.toString());
		assertTrue(others.getOrDefault("net/minecraft/world/level/block/VegetationBlock", Set.of()).contains("getPlant"), others.toString());
		// 26.x: Forge 64 to 66's tag check, TypedInstance.is, and the moisture's getValue, which the defaults read and the
		// put-back's getValueOrElse stands in for.
		assertEquals(expected, conflict(dir.resolve("typed-instance"), forge64, head.formatted("is"),
				"targets = \"net.minecraft.core.TypedInstance\"", crop));
		assertEquals(expected, conflict(dir.resolve("get-value"), neoForge26, head.formatted("getValue"),
				"targets = \"net.minecraft.world.level.block.state.StateHolder\"", crop));
		// Forge 43 to 52's moisture check alone: its default's state.is(Blocks.FARMLAND), which the put-back's helper tells by the block.
		Map<String, String> moistureOnly = new HashMap<>(CropGrowthTest.sources(CropGrowthTest.Form.FORGE_47));
		moistureOnly.replaceAll((file, text) -> file.equals(crop + ".java") ? text.replaceFirst("soil\\.canSustainPlant\\([^;{]*?\\(net\\.minecraftforge\\.common\\.IPlantable\\) type\\)",
				"true") : text);
		ClassBytesSource moistureGame = TestSources.compileWithLocals(dir.resolve("moisture-only"), moistureOnly)::get;
		org.objectweb.asm.tree.ClassNode moisture = moistureGame.readNode(crop, 0);
		VanillaSeams.Rewrite alone = VanillaSeams.rewriteOf(moisture);
		assertTrue(alone.others().getOrDefault("net/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase", Set.of()).contains("m_60713_"),
				alone.others().toString());
		assertTrue(alone.movedCalls().stream().noneMatch(c -> c.contains("canSustainPlant")), alone.movedCalls().toString());
	}

	/**
	 * SEAM-9: a native mod's mixin of what putting vanilla's eye check back makes or bypasses keeps the class as the platform
	 * has it: Entity's {@code isEyeInFluid}, which vanilla's call makes (26.1.2's of a tag calling the fluid type's), and the
	 * fluid interaction's {@code isEyeInFluid}, {@code isEyeInFluidMatching} and {@code getFluidTypeByTag}. One of another
	 * of Entity's methods doesn't.
	 */
	@Test
	void aNativeMixinOfWhatPuttingTheEyeCheckBackChangesKeepsTheClass(@TempDir Path dir) throws Exception {
		String player = "net/minecraft/world/entity/player/Player";
		SeamConflicts.Conflict expected = new SeamConflicts.Conflict("breaker", "com/moon/NativeBreak", true);
		String head = " @Inject(method = \"%s\", at = @At(\"HEAD\")) private void head(CallbackInfo ci) { }";
		Map<String, String> neoForge = EyesInWaterTest.sources(EyesInWaterTest.Form.BY_TYPE);
		String interaction = "net.minecraft.world.entity.EntityFluidInteraction";
		assertEquals(expected, conflict(dir.resolve("entity"), neoForge, head.formatted("isEyeInFluid"), "net.minecraft.world.entity.Entity", player));
		for (String method : List.of("isEyeInFluid", "isEyeInFluidMatching", "getFluidTypeByTag")) {
			assertEquals(expected, conflict(dir.resolve(method), neoForge, head.formatted(method), interaction, player), method);
		}
		assertNull(conflict(dir.resolve("entity-other"), neoForge, head.formatted("getFluidInteraction"), "net.minecraft.world.entity.Entity", player));
		assertEquals("EntityFluidInteraction.isEyeInFluidMatching", found(dir.resolve("matching-named"), neoForge, head.formatted("isEyeInFluidMatching"),
				interaction, player, null).hooked());
		// The class's own method, where a native mixin hooks the platform's call vanilla's goes back in place of.
		assertEquals("Player.getDestroySpeed", found(dir.resolve("own-named"), neoForge, " @Inject(method = \"getDestroySpeed\", at = @At(value = \"INVOKE\","
				+ " target = \"Lnet/minecraft/world/entity/EntityFluidInteraction;isEyeInFluidMatching(Lnet/minecraft/world/entity/Entity;"
				+ "Lnet/neoforged/neoforge/fluids/InFluidPredicate;)Z\")) private void call(CallbackInfo ci) { }", "net.minecraft.world.entity.player.Player",
				player, null).hooked());
	}

	/**
	 * The report's line names the method of the class a native mixin hooks: of an injector's selectors, those picking a
	 * method holding the calls the put-back changes, never MixinExtras' {@code @Definition} (whose {@code method} names
	 * the call it matches) or {@code @Expression}; and the class's own method before another class's it hooks too.
	 */
	@Test
	void theConflictsLineNamesTheMethodHoldingTheCallsThatChange(@TempDir Path dir) throws Exception {
		Map<String, String> sources = new HashMap<>(VanillaCallsTest.neoForgeSources());
		sources.put("com/llamalad7/mixinextras/expression/Definition.java", "package com.llamalad7.mixinextras.expression; public @interface Definition {"
				+ " String id(); String[] method() default { }; }");
		sources.put("com/llamalad7/mixinextras/expression/Expression.java", "package com.llamalad7.mixinextras.expression; public @interface Expression { String[] value(); }");
		sources.put("com/llamalad7/mixinextras/injector/ModifyExpressionValue.java", "package com.llamalad7.mixinextras.injector; public @interface"
				+ " ModifyExpressionValue { String[] method(); org.spongepowered.asm.mixin.injection.At[] at(); }");
		String harvest = "Lnet/minecraft/world/level/block/state/BlockState;canHarvestBlock(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;"
				+ "Lnet/minecraft/world/entity/player/Player;)Z";
		String gameMode = "net.minecraft.server.level.ServerPlayerGameMode";
		String expression = " @com.llamalad7.mixinextras.expression.Definition(id = \"harvest\", method = \"" + harvest + "\")"
				+ " @com.llamalad7.mixinextras.expression.Expression(\"?.harvest(?, ?, ?)\")"
				+ " @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = \"destroyBlock\", at = @At(\"MIXINEXTRAS:EXPRESSION\"))"
				+ " private boolean harvested(boolean original) { return original; }";
		assertEquals("ServerPlayerGameMode.destroyBlock", found(dir.resolve("expression"), sources, expression, gameMode, "net/minecraft/server/level/ServerPlayerGameMode",
				null).hooked());
		String both = " @Inject(method = {\"<init>\", \"destroyBlock\"}, at = @At(value = \"INVOKE\", target = \"" + harvest + "\"))"
				+ " private void call(CallbackInfo ci) { }";
		assertEquals("ServerPlayerGameMode.destroyBlock", found(dir.resolve("two"), sources, both, gameMode, "net/minecraft/server/level/ServerPlayerGameMode",
				null).hooked());
		// An injector naming a call that moves in a method holding none of those calls: its selector, as it is.
		String elsewhere = " @Inject(method = \"<init>\", at = @At(value = \"INVOKE\", target = \"" + harvest + "\")) private void call(CallbackInfo ci) { }";
		assertEquals("ServerPlayerGameMode.<init>", found(dir.resolve("elsewhere"), sources, elsewhere, gameMode, "net/minecraft/server/level/ServerPlayerGameMode",
				null).hooked());
		// A mixin of CropBlock hooking a jump in its growth speed, which the put-back shifts, and the crop's mayPlaceOn,
		// which it bypasses: the class's own method.
		String crop = "net/minecraft/world/level/block/CropBlock";
		String growthAndPlacing = " @Inject(method = \"getGrowthSpeed\", at = @At(\"JUMP\")) private static void jump(CallbackInfo ci) { }"
				+ " @Inject(method = \"mayPlaceOn\", at = @At(\"HEAD\")) private void head(CallbackInfo ci) { }";
		assertEquals("CropBlock.getGrowthSpeed", found(dir.resolve("own-first"), CropGrowthTest.sources(CropGrowthTest.Form.FORGE_52), growthAndPlacing,
				crop.replace('/', '.'), crop, null).hooked());
	}

	/**
	 * A native mixin's selectors are read as Mixin reads them: a name with a quantifier, an owner or a descriptor
	 * annotation selects that name's methods; a quantifier alone, a regex or an unknown name, what can't be told. A Forge
	 * mod's mixin of another of Player's methods leaves the server's game mode and BlockBehaviour to be put back.
	 */
	@Test
	void selectorsAreReadAsMixinReadsThem(@TempDir Path dir) throws Exception {
		SeamConflicts.Conflict expected = new SeamConflicts.Conflict("breaker", "com/moon/NativeBreak", true);
		String head = " @Inject(method = \"%s\", at = @At(\"HEAD\")) private void head(CallbackInfo ci) { }";
		String described = " @Inject(target = @Desc(\"%s\"), at = @At(\"HEAD\")) private void head(CallbackInfo ci) { }";
		String player = "net.minecraft.world.entity.player.Player";
		String behaviour = "net/minecraft/world/level/block/state/BlockBehaviour";
		Map<String, String> forge = VanillaCallsTest.forgeSources();
		int n = 0;
		for (String other : List.of("tick*", "tick+", "tick{2}", "<init>*", "Lnet/minecraft/world/entity/player/Player;tick*()V", "net.minecraft.world.entity.player.Player.tick")) {
			assertNull(breaking(dir.resolve("other" + n++), forge, head.formatted(other), player), other);
		}
		assertNull(breaking(dir.resolve("other-desc"), forge, described.formatted("tick"), player));
		assertNull(conflict(dir.resolve("other-behaviour"), forge, head.formatted("tick*"), player, behaviour));
		for (String any : List.of("*", "/has.*/", "name=/has.*/", "hasCorrectToolForDrops*", "m_36298_{1}", "hasCorrectToolForDrops(*)Z",
				"Lnet/minecraft/world/entity/player/Player;hasCorrectToolForDrops", "net.minecraft.world.entity.player.Player.hasCorrectToolForDrops")) {
			assertEquals(expected, breaking(dir.resolve("any" + n++), forge, head.formatted(any), player), any);
		}
		assertEquals(expected, breaking(dir.resolve("any-desc"), forge, described.formatted("hasCorrectToolForDrops"), player));
		assertEquals(expected, conflict(dir.resolve("any-behaviour"), forge, head.formatted("*"), player, behaviour));
		// In the class holding the calls: a quantified name or a descriptor annotation selects the name's methods.
		String gameMode = "net.minecraft.server.level.ServerPlayerGameMode";
		assertNull(breaking(dir.resolve("start"), head.formatted("destroyBlock*"), gameMode));
		assertNull(breaking(dir.resolve("start-desc"), described.formatted("destroyBlock"), gameMode));
		assertEquals(expected, breaking(dir.resolve("bypassed"), head.formatted("removeBlock*"), gameMode));
		assertEquals(expected, breaking(dir.resolve("bypassed-desc"), described.formatted("removeBlock"), gameMode));
		assertEquals(expected, breaking(dir.resolve("unknown"), head.formatted("m_9280_*"), gameMode));
	}

	/**
	 * Forge 47's mods name the game's methods by Mojang's names, which their refmaps map to the SRG names the game runs
	 * with: a native mixin's strings are read as its config's refmap maps them. One selecting the server's destroyBlock
	 * at its start lets the calls be put back; one at vanilla's call the rewrite adds there, by the name its refmap maps,
	 * keeps the class; one whose refmap fails to read too, unread.
	 */
	@Test
	void aNativeMixinsStringsAreReadThroughItsRefmap(@TempDir Path dir) throws Exception {
		Map<String, String> forge47 = VanillaCallsTest.forge47Sources();
		String gameMode = "net.minecraft.server.level.ServerPlayerGameMode";
		String internal = "net/minecraft/server/level/ServerPlayerGameMode";
		SeamConflicts.Conflict expected = new SeamConflicts.Conflict("breaker", "com/moon/NativeBreak", true);
		String head = " @Inject(method = \"destroyBlock\", at = @At(\"HEAD\")) private void head(CallbackInfo ci) { }";
		// As written, Forge 47's class has no destroyBlock: what the mixin hooks can't be told.
		assertEquals(expected, conflict(dir.resolve("unmapped"), forge47, head, gameMode, internal));
		assertNull(conflict(dir.resolve("mapped"), forge47, head, gameMode, internal, refmap(Map.of("destroyBlock", "m_9280_(Lnet/minecraft/core/BlockPos;)Z"))));
		String target = "Lnet/minecraft/server/level/ServerPlayer;hasCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z";
		String call = " @Inject(method = \"destroyBlock\", at = @At(value = \"INVOKE\", target = \"" + target + "\")) private void call(CallbackInfo ci) { }";
		assertEquals(expected, conflict(dir.resolve("call"), forge47, call, gameMode, internal, refmap(Map.of("destroyBlock", "m_9280_(Lnet/minecraft/core/BlockPos;)Z",
				target, target.replace("hasCorrectToolForDrops", "m_36298_")))));
		// Forge's Player: another of its methods, by the name its refmap maps, leaves the class to be put back; its harvest check doesn't.
		String player = "net.minecraft.world.entity.player.Player";
		String tick = " @Inject(method = \"tick\", at = @At(\"HEAD\")) private void tick(CallbackInfo ci) { }";
		assertNull(conflict(dir.resolve("player-tick"), forge47, tick, player, internal, refmap(Map.of("tick", "m_8119_()V"))));
		String check = " @Inject(method = \"hasCorrectToolForDrops\", at = @At(\"HEAD\")) private void check(CallbackInfo ci) { }";
		assertEquals(expected, conflict(dir.resolve("player-check"), forge47, check, player, internal,
				refmap(Map.of("hasCorrectToolForDrops", "m_36298_(Lnet/minecraft/world/level/block/state/BlockState;)Z"))));
		InstalledClasses.RefmapFile failing = new InstalledClasses.RefmapFile("breaker.refmap.json", path -> {
			throw new IOException("EIO");
		});
		assertEquals(new SeamConflicts.Conflict("breaker", "com/moon/NativeBreak", false), conflict(dir.resolve("failing"), forge47, head, gameMode, internal, failing));
		// A refmap the jar hasn't, or that doesn't parse, maps nothing, as Mixin then reads the strings as they are.
		assertEquals(expected, conflict(dir.resolve("absent"), forge47, head, gameMode, internal, new InstalledClasses.RefmapFile("breaker.refmap.json", path -> null)));
		assertEquals(expected, conflict(dir.resolve("broken"), forge47, head, gameMode, internal, new InstalledClasses.RefmapFile("breaker.refmap.json",
				path -> "{\"mappings\": {".getBytes(StandardCharsets.UTF_8))));
	}

	/**
	 * PRE-31: Mixin strips a string's whitespace before it looks it up in the refmap, whose keys are written without it, so
	 * a native mixin's selector or {@code @At} target written with a space is read as its refmap maps it.
	 */
	@Test
	void aNativeMixinsStringsWithWhitespaceAreLookedUpWithoutIt(@TempDir Path dir) throws Exception {
		Map<String, String> forge47 = VanillaCallsTest.forge47Sources();
		String gameMode = "net.minecraft.server.level.ServerPlayerGameMode";
		String internal = "net/minecraft/server/level/ServerPlayerGameMode";
		String destroy = "m_9280_(Lnet/minecraft/core/BlockPos;)Z";
		String head = " @Inject(method = \"destroyBlock (Lnet/minecraft/core/BlockPos;)Z\", at = @At(\"HEAD\")) private void head(CallbackInfo ci) { }";
		assertNull(conflict(dir.resolve("head"), forge47, head, gameMode, internal, refmap(Map.of("destroyBlock(Lnet/minecraft/core/BlockPos;)Z", destroy))));
		String target = "Lnet/minecraft/server/level/ServerPlayer;hasCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z";
		String call = " @Inject(method = \"destroyBlock\", at = @At(value = \"INVOKE\", target = \"" + target.replace("(", " (")
				+ "\")) private void call(CallbackInfo ci) { }";
		assertEquals(new SeamConflicts.Conflict("breaker", "com/moon/NativeBreak", true), conflict(dir.resolve("call"), forge47, call, gameMode, internal,
				refmap(Map.of("destroyBlock", destroy, target, target.replace("hasCorrectToolForDrops", "m_36298_")))));
	}

	/** A refmap mapping NativeBreak's strings as {@code mappings} says. */
	private static InstalledClasses.RefmapFile refmap(Map<String, String> mappings) {
		StringBuilder json = new StringBuilder("{\"mappings\": {\"com.moon.NativeBreak\": {");
		mappings.forEach((from, to) -> json.append(json.charAt(json.length() - 1) == '{' ? "" : ", ").append('"').append(from).append("\": \"").append(to).append('"'));
		String text = json.append("}}, \"data\": {}}").toString();
		return new InstalledClasses.RefmapFile("breaker.refmap.json", path -> path.equals("breaker.refmap.json") ? text.getBytes(StandardCharsets.UTF_8) : null);
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
		// A mixin naming the class only in its string targets, the name followed in the class file by the next constant's tag.
		String constant = " @Inject(method = \"destroyBlock\", at = @At(value = \"CONSTANT\")) private void constant(CallbackInfo ci) { }";
		assertEquals(expected, breaking(dir.resolve("targets-dotted"), constant, "targets = \"" + gameMode + "\""));
		assertEquals(expected, breaking(dir.resolve("targets-internal"), constant, "targets = \"" + gameMode.replace('.', '/') + "\""));
		assertEquals(expected, breaking(dir.resolve("targets-level"), VanillaCallsTest.forgeSources(), level, "targets = \"net.minecraft.world.level.Level\""));
		// One naming only a class whose name runs on from it isn't parsed: it can't target the class.
		assertNull(breaking(dir.resolve("targets-longer"), constant, "targets = \"" + gameMode + "Extra\""));
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
		Map<String, SeamConflicts.Conflict> decided = Map.of(RESOLVER, new SeamConflicts.Conflict("moonlight", "com/moon/NativeSticky", true,
				"PistonStructureResolver.resolve"));

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
		Map<String, SeamConflicts.Conflict> decided = Map.of(RESOLVER, new SeamConflicts.Conflict("moonlight", "com/moon/NativeSticky", true,
				"PistonStructureResolver.resolve"));

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
