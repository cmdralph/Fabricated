package io.github.cmdralph.fabricated.core.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;
import io.github.cmdralph.fabricated.core.bytecode.InstalledClasses;
import io.github.cmdralph.fabricated.core.bytecode.VanillaSeams;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding.Severity;
import io.github.cmdralph.fabricated.core.testutil.TestSources;

/**
 * SEAM-1: a game class is rewritten as it loads, its vanilla methods put back, only where a Fabric mod's mixin member hooks
 * one of them (an injector naming it, a shadow of it). One that targets the class but hooks none, as Lithium's mixin of
 * the resolver, leaves it as the platform has it, so a native mod's mixin of the platform's calls there
 * still finds them.
 */
class RestoredSeamsTest {
	private static final String RESOLVER = "net/minecraft/world/level/block/piston/PistonStructureResolver";
	private static final String STATE = "Lnet/minecraft/world/level/block/state/BlockState;";

	private static Set<String> recorded(Path dir, String members) throws Exception {
		Map<String, byte[]> classes = new HashMap<>(TestSources.compile(dir, TestSources.MIXIN_ANNOTATIONS, Map.of(
				"net/minecraft/world/level/block/state/BlockState.java", "package net.minecraft.world.level.block.state; public class BlockState {"
						+ " public boolean isAir() { return false; } public boolean isStickyBlock() { return false; }"
						+ " public boolean canStickTo(BlockState other) { return false; } }",
				RESOLVER + ".java", "package net.minecraft.world.level.block.piston; import net.minecraft.world.level.block.state.BlockState;"
						+ " public class PistonStructureResolver { boolean blocked(BlockState s) { return false; }"
						+ " public boolean resolve(BlockState s) { return s.isStickyBlock(); }"
						+ " boolean addBranchingBlocks(BlockState n, BlockState f) { if (n.canStickTo(f) && f.canStickTo(n) && !blocked(n)) return false; return true; } }",
				"org/spongepowered/asm/mixin/injection/At.java", "package org.spongepowered.asm.mixin.injection; public @interface At { String value(); String target() default \"\"; }",
				"org/spongepowered/asm/mixin/injection/Inject.java", "package org.spongepowered.asm.mixin.injection; public @interface Inject { String[] method(); At[] at(); boolean cancellable() default false; }",
				"org/spongepowered/asm/mixin/injection/Redirect.java", "package org.spongepowered.asm.mixin.injection; public @interface Redirect { String[] method(); At at(); }",
				"org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable.java", "package org.spongepowered.asm.mixin.injection.callback; public class CallbackInfoReturnable<R> { }",
				"com/example/mixin/Resolver.java", "package com.example.mixin; import org.spongepowered.asm.mixin.*; import org.spongepowered.asm.mixin.injection.*;"
						+ " import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;"
						+ " import net.minecraft.world.level.block.state.BlockState; import net.minecraft.world.level.block.piston.PistonStructureResolver;"
						+ " @Mixin(PistonStructureResolver.class) public abstract class Resolver {" + members + " }")));
		ClassBytesSource game = VanillaSeams.over(classes::get);
		MixinPreflight preflight = new MixinPreflight(new ClassIndex(game), InstalledClasses.NONE, classes::get, ClassBytesSource.EMPTY, false, true,
				ClassBytesSource.EMPTY);
		List<PreflightFinding> findings = preflight.check("test.mixins.json",
				MixinConfigFile.parse("{\"required\":true,\"package\":\"com.example.mixin\",\"mixins\":[\"Resolver\"]}"), Refmap.EMPTY, Set.of());
		assertTrue(findings.stream().noneMatch(f -> f.severity() == Severity.LEFT_OUT || f.severity() == Severity.FAIL), findings.toString());
		return preflight.restoredSeams();
	}

	/**
	 * Pre-flight's findings and the classes recorded for a Fabric mixin of NeoForge's server game mode (SEAM-3), checked
	 * once repaired; {@code classes}, if given, gets the mod's classes as repaired.
	 */
	private static MixinPreflight breaking(Path dir, String members, List<PreflightFinding> findings) throws Exception {
		return breaking(dir, members, findings, new HashMap<>());
	}

	private static MixinPreflight breaking(Path dir, String members, List<PreflightFinding> findings, Map<String, byte[]> classes) throws Exception {
		return breaking(dir, io.github.cmdralph.fabricated.core.bytecode.VanillaCallsTest.neoForgeSources(), members, findings, classes);
	}

	private static MixinPreflight breaking(Path dir, Map<String, String> gameSources, String members, List<PreflightFinding> findings, Map<String, byte[]> classes)
			throws Exception {
		return breaking(dir, gameSources, members, null, true, Set.of(), findings, classes);
	}

	/**
	 * As {@link #breaking(Path, String, List)}, with a second mixin of the class, {@code Reader} ({@code reader} its members,
	 * null for none), a config that is or isn't required, and the mixins {@code skip} leaves out.
	 */
	private static MixinPreflight breaking(Path dir, Map<String, String> gameSources, String members, String reader, boolean required, Set<String> skip,
			List<PreflightFinding> findings, Map<String, byte[]> classes) throws Exception {
		return checked(dir, gameSources, "ServerPlayerGameMode", members, reader, required, skip, findings, classes);
	}

	/** As {@link #breaking(Path, Map, String, String, boolean, Set, List, Map)}, the mixins targeting {@code target}. */
	private static MixinPreflight checked(Path dir, Map<String, String> gameSources, String target, String members, String reader, boolean required,
			Set<String> skip, List<PreflightFinding> findings, Map<String, byte[]> classes) throws Exception {
		Map<String, String> sources = new HashMap<>(gameSources);
		sources.put("org/spongepowered/asm/mixin/injection/At.java", "package org.spongepowered.asm.mixin.injection; public @interface At { String value(); String target() default \"\";"
				+ " int ordinal() default -1; }");
		sources.put("com/llamalad7/mixinextras/injector/wrapoperation/WrapOperation.java", "package com.llamalad7.mixinextras.injector.wrapoperation;"
				+ " public @interface WrapOperation { String[] method(); org.spongepowered.asm.mixin.injection.At[] at(); int allow() default -1; }");
		sources.put("com/llamalad7/mixinextras/injector/wrapoperation/Operation.java", "package com.llamalad7.mixinextras.injector.wrapoperation;"
				+ " public interface Operation<R> { R call(Object... args); }");
		sources.put("org/spongepowered/asm/mixin/injection/Inject.java", "package org.spongepowered.asm.mixin.injection; public @interface Inject { String[] method(); At[] at(); }");
		sources.put("org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable.java", "package org.spongepowered.asm.mixin.injection.callback;"
				+ " public class CallbackInfoReturnable<R> { }");
		sources.put("com/llamalad7/mixinextras/sugar/Share.java", "package com.llamalad7.mixinextras.sugar; public @interface Share { String value();"
				+ " String namespace() default \"\"; }");
		sources.put("com/llamalad7/mixinextras/sugar/ref/LocalRef.java", "package com.llamalad7.mixinextras.sugar.ref; public interface LocalRef<T> { T get();"
				+ " void set(T value); }");
		sources.put("com/example/mixin/Breaking.java", "package com.example.mixin; import org.spongepowered.asm.mixin.*; import org.spongepowered.asm.mixin.injection.*;"
				+ " import org.spongepowered.asm.mixin.injection.callback.*; import com.llamalad7.mixinextras.sugar.*; import com.llamalad7.mixinextras.sugar.ref.*;"
				+ " import com.llamalad7.mixinextras.injector.wrapoperation.*; import net.minecraft.core.BlockPos; import net.minecraft.server.level.*;"
				+ " @Mixin(" + target + ".class) public abstract class Breaking {" + members + " }");
		if (reader != null) {
			sources.put("com/example/mixin/Reader.java", sources.get("com/example/mixin/Breaking.java").replace("class Breaking {" + members, "class Reader {" + reader));
		}
		classes.putAll(TestSources.compileWithLocals(dir, TestSources.MIXIN_ANNOTATIONS, sources));
		ClassBytesSource game = VanillaSeams.over(classes::get);
		MixinPreflight preflight = new MixinPreflight(new ClassIndex(game), InstalledClasses.NONE, classes::get, ClassBytesSource.EMPTY, false, true,
				ClassBytesSource.EMPTY);
		MixinConfigFile config = MixinConfigFile.parse("{\"required\":" + required + ",\"package\":\"com.example.mixin\",\"mixins\":[\"Breaking\""
				+ (reader != null ? ",\"Reader\"" : "") + "],\"injectors\":{\"defaultRequire\":1}}");
		preflight.repairs("test.mixins.json", config, Refmap.EMPTY, skip, findings)
				.forEach((file, data) -> classes.put(file.substring(0, file.length() - ".class".length()), data));
		findings.addAll(preflight.check("test.mixins.json", config, Refmap.EMPTY, skip));
		return preflight;
	}

	/**
	 * A mixin out whole in other ways than the check's own leaving out is out for the seams too: no class is recorded for
	 * its hook, its note goes, and the injectors reading a local it sets in another mixin go with it (SHARE-2). Skipped
	 * before the check (as translation does a mixin using an unsupported module's class), rejected by Mixin as it applies
	 * (a problem of the whole mixin in a config that isn't required: here, a wrapper missing an argument of the call), or
	 * left out by translation after the check (a mixin using an interface only one left out adds).
	 */
	@Test
	void aMixinOutWholeTakesItsSeamsAndItsSharesReadersWithIt(@TempDir Path dir) throws Exception {
		String share = "@Share(value = \"removed\", namespace = \"test\") LocalRef<Object> removed";
		String wrap = " @WrapOperation(method = \"destroyBlock\", at = @At(value = \"INVOKE\", target = \"Lnet/minecraft/server/level/ServerLevel;"
				+ "removeBlock(Lnet/minecraft/core/BlockPos;Z)Z\")) private boolean removed(ServerLevel level, BlockPos pos, boolean moving,"
				+ " Operation<Boolean> original, " + share + ") { removed.set(pos); return original.call(level, pos, moving); }";
		String reader = " @Inject(method = \"destroyBlock\", at = @At(\"HEAD\")) private void read(BlockPos pos, CallbackInfoReturnable<Boolean> cir, "
				+ share + ") { removed.get(); }";
		Map<String, String> game = io.github.cmdralph.fabricated.core.bytecode.VanillaCallsTest.neoForgeSources();
		String readHandler = "read(Lnet/minecraft/core/BlockPos;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;"
				+ "Lcom/llamalad7/mixinextras/sugar/ref/LocalRef;)V";

		// Both apply: the class is recorded for the wrapper.
		List<PreflightFinding> applying = new java.util.ArrayList<>();
		MixinPreflight both = breaking(dir.resolve("both"), game, wrap, reader, true, Set.of(), applying, new HashMap<>());
		assertEquals(Set.of("net/minecraft/server/level/ServerPlayerGameMode"), both.restoredSeams(), applying.toString());
		assertTrue(applying.stream().noneMatch(f -> f.severity() == Severity.LEFT_OUT), applying.toString());

		// Skipped before the check.
		List<PreflightFinding> skipped = new java.util.ArrayList<>();
		assertEquals(Set.of(), breaking(dir.resolve("skipped"), game, wrap, reader, true, Set.of("Breaking"), skipped, new HashMap<>()).restoredSeams());
		assertTrue(skipped.stream().anyMatch(f -> f.severity() == Severity.LEFT_OUT && readHandler.equals(f.handler())
				&& f.message().contains("goes with that of com.example.mixin.Breaking.removed(") && f.message().contains(", which is left out")), skipped.toString());
		assertTrue(skipped.stream().noneMatch(f -> f.message().endsWith("(SEAM-3)")), skipped.toString());

		// Rejected by Mixin: a wrapper missing the call's boolean, in a config that isn't required.
		List<PreflightFinding> rejected = new java.util.ArrayList<>();
		MixinPreflight rejecting = breaking(dir.resolve("rejected"), game, wrap.replace("BlockPos pos, boolean moving,", "BlockPos pos,")
				.replace("original.call(level, pos, moving)", "original.call(level, pos)"), reader, false, Set.of(), rejected, new HashMap<>());
		assertTrue(rejected.stream().anyMatch(f -> f.severity() == Severity.WARN && "com.example.mixin.Breaking".equals(f.mixin()) && f.handler() == null),
				rejected.toString());
		assertEquals(Set.of(), rejecting.restoredSeams(), rejected.toString());
		assertTrue(rejected.stream().noneMatch(f -> f.message().endsWith("(SEAM-3)")), rejected.toString());
		assertTrue(rejected.stream().anyMatch(f -> f.severity() == Severity.LEFT_OUT && readHandler.equals(f.handler())), rejected.toString());

		// Left out by translation after the check: its reader goes, returned to be left out in turn, and its note.
		List<PreflightFinding> after = new java.util.ArrayList<>();
		MixinPreflight later = breaking(dir.resolve("after"), game, wrap, reader, true, Set.of(), after, new HashMap<>());
		List<PreflightFinding> more = later.leftOutAfterCheck(List.of("com.example.mixin.Breaking"));
		assertEquals(List.of(readHandler), more.stream().filter(f -> f.severity() == Severity.LEFT_OUT).map(PreflightFinding::handler).toList(), more.toString());
		assertEquals(List.of(), later.leftOutAfterCheck(List.of("com.example.mixin.Breaking")), "told once, it adds nothing again");
		assertEquals(Set.of(), later.restoredSeams());
		after.addAll(more);
		later.withoutSeamNotesOfLeftOut(after);
		assertTrue(after.stream().noneMatch(f -> f.message().endsWith("(SEAM-3)")), after.toString());
	}

	@Test
	void aWrapperOfVanillasCallPutBackHasTheClassRewritten(@TempDir Path dir) throws Exception {
		// As Wilder Wild's and FrozenLib's: a wrapper of the server's removeBlock call in destroyBlock.
		String wrap = " @WrapOperation(method = \"destroyBlock\", at = @At(value = \"INVOKE\", target = \"Lnet/minecraft/server/level/ServerLevel;"
				+ "removeBlock(Lnet/minecraft/core/BlockPos;Z)Z\")) private boolean removed(ServerLevel level, BlockPos pos, boolean moving,"
				+ " Operation<Boolean> original) { return original.call(level, pos, moving); }";
		List<PreflightFinding> findings = new java.util.ArrayList<>();
		MixinPreflight preflight = breaking(dir.resolve("wrap"), wrap, findings);
		assertTrue(findings.stream().noneMatch(f -> f.severity() == Severity.LEFT_OUT || f.severity() == Severity.FAIL), findings.toString());
		assertEquals(Set.of("net/minecraft/server/level/ServerPlayerGameMode"), preflight.restoredSeams());
		assertTrue(findings.stream().anyMatch(f -> f.message().equals("@WrapOperation removed hooks ServerLevel.removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"
				+ " in destroyBlock, vanilla's call NeoForge's ServerPlayerGameMode makes through the block's IBlockExtension.onDestroyedByPlayer instead;"
				+ " Fabricated puts it back there for the blocks keeping that method's default, so the hook applies to their breaking, and a block"
				+ " overriding it breaks as NeoForge has it, without the hook (SEAM-3)")), findings.toString());

		// Left out with another injector of the mixin, whose state it reads (SHARE-1), it applies to nothing put back: the class
		// isn't rewritten for it, nor is its hook noted.
		String reading = wrap.replace("Operation<Boolean> original) {", "Operation<Boolean> original, @Share(\"x\") LocalRef<Object> x) { x.get();")
				+ " @Inject(method = \"destroyBlock\", at = @At(value = \"INVOKE\", target = \"Lnet/minecraft/world/level/block/Block;noSuchCall()V\"))"
				+ " private void keep(BlockPos pos, CallbackInfoReturnable<Boolean> cir, @Share(\"x\") LocalRef<Object> x) { x.set(pos); }";
		List<PreflightFinding> shared = new java.util.ArrayList<>();
		assertEquals(Set.of(), breaking(dir.resolve("shared"), reading, shared).restoredSeams());
		assertTrue(shared.stream().anyMatch(f -> f.severity() == Severity.LEFT_OUT && f.handler() != null && f.handler().startsWith("removed(")), shared.toString());
		assertTrue(shared.stream().noneMatch(f -> f.message().endsWith("(SEAM-3)")), shared.toString());

		// The same call wrapped in a method it isn't put back in finds nothing there, and the class isn't rewritten for it.
		List<PreflightFinding> elsewhere = new java.util.ArrayList<>();
		assertEquals(Set.of(), breaking(dir.resolve("elsewhere"), wrap.replace("method = \"destroyBlock\"", "method = \"removeBlock\""), elsewhere).restoredSeams());
		assertTrue(elsewhere.stream().anyMatch(f -> f.message().contains("which does not occur in that method")), elsewhere.toString());
	}

	/**
	 * Vanilla's one removal call, which the platforms' server makes in a creative branch and a survival one: put back in
	 * each, a wrapper picking it by ordinal 0 would hook creative breaks alone. The ordinal is dropped; where the wrapper
	 * allows one injection alone, it stays.
	 */
	@Test
	void anOrdinalOfVanillasOneCallPutBackInEachBranchIsDropped(@TempDir Path dir) throws Exception {
		String wrap = " @WrapOperation(method = \"destroyBlock\", at = @At(value = \"INVOKE\", target = \"Lnet/minecraft/server/level/ServerLevel;"
				+ "removeBlock(Lnet/minecraft/core/BlockPos;Z)Z\", ordinal = 0)) private boolean removed(ServerLevel level, BlockPos pos, boolean moving,"
				+ " Operation<Boolean> original) { return original.call(level, pos, moving); }";
		List<PreflightFinding> findings = new java.util.ArrayList<>();
		Map<String, byte[]> classes = new HashMap<>();
		assertEquals(Set.of("net/minecraft/server/level/ServerPlayerGameMode"), breaking(dir.resolve("ordinal"), wrap, findings, classes).restoredSeams());
		assertTrue(findings.stream().anyMatch(f -> f.severity() == Severity.INFO && f.message().equals("@WrapOperation removed picks the call"
				+ " Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z by ordinal 0, vanilla's one call there, which"
				+ " Fabricated puts back in each of NeoForge's places making it through the block's extension (SEAM-3); the ordinal is dropped, so it"
				+ " picks the call in each")), findings.toString());
		assertEquals(null, ordinal(classes.get("com/example/mixin/Breaking")));

		List<PreflightFinding> once = new java.util.ArrayList<>();
		Map<String, byte[]> allowing = new HashMap<>();
		breaking(dir.resolve("allow"), wrap.replace("ordinal = 0))", "ordinal = 0), allow = 1)"), once, allowing);
		assertEquals(0, ordinal(allowing.get("com/example/mixin/Breaking")));

		// An @Inject after the call, at INVOKE_ASSIGN, the same.
		List<PreflightFinding> assigned = new java.util.ArrayList<>();
		Map<String, byte[]> after = new HashMap<>();
		breaking(dir.resolve("assign"), " @Inject(method = \"destroyBlock\", at = @At(value = \"INVOKE_ASSIGN\", target = \"Lnet/minecraft/server/level/ServerLevel;"
				+ "removeBlock(Lnet/minecraft/core/BlockPos;Z)Z\", ordinal = 0)) private void removed(BlockPos pos, CallbackInfoReturnable<Boolean> cir) { }", assigned, after);
		assertTrue(assigned.stream().anyMatch(f -> f.severity() == Severity.INFO && f.message().startsWith("@Inject removed picks the call")), assigned.toString());
		assertEquals(null, ordinal(after.get("com/example/mixin/Breaking")));
	}

	/** The ordinal of the first {@code @At} of the class's first method annotated with one, or null. */
	private static Object ordinal(byte[] mixin) {
		org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
		new org.objectweb.asm.ClassReader(mixin).accept(node, 0);
		for (org.objectweb.asm.tree.MethodNode method : node.methods) {
			for (org.objectweb.asm.tree.AnnotationNode ann : io.github.cmdralph.fabricated.core.bytecode.Annotations.all(method)) {
				for (org.objectweb.asm.tree.AnnotationNode at : io.github.cmdralph.fabricated.core.bytecode.Annotations.list(
						io.github.cmdralph.fabricated.core.bytecode.Annotations.value(ann, "at"))) {
					return io.github.cmdralph.fabricated.core.bytecode.Annotations.value(at, "ordinal");
				}
			}
		}
		return null;
	}

	/**
	 * On Forge's server a wrapper of vanilla's playerWillDestroy call, which Forge makes from its extension once the tool is
	 * damaged, stays out, its hint naming the class, which the known limit reads (SEAM-3).
	 */
	@Test
	void aWrapperOfForgesServersPlayerWillDestroyCallStaysOutSayingWhy(@TempDir Path dir) throws Exception {
		String state = "net.minecraft.world.level.block.state.BlockState";
		String wrap = " @WrapOperation(method = \"destroyBlock\", at = @At(value = \"INVOKE\", target = \"Lnet/minecraft/world/level/block/Block;playerWillDestroy("
				+ "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;"
				+ "Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/level/block/state/BlockState;\")) private " + state + " willDestroy("
				+ "net.minecraft.world.level.block.Block block, net.minecraft.world.level.Level level, BlockPos pos, " + state + " s,"
				+ " net.minecraft.world.entity.player.Player player, Operation<" + state + "> original) { return original.call(block, level, pos, s, player); }";
		List<PreflightFinding> findings = new java.util.ArrayList<>();
		breaking(dir, io.github.cmdralph.fabricated.core.bytecode.VanillaCallsTest.forgeSources(), wrap, findings, new HashMap<>());
		PreflightFinding out = findings.stream().filter(f -> f.severity() == Severity.LEFT_OUT).findFirst().orElseThrow();
		assertEquals("Forge's ServerPlayerGameMode makes that call through the block's IForgeBlock.onDestroyedByPlayer instead, which Fabricated doesn't"
				+ " put back there (SEAM-3).", out.suggestion(), findings.toString());
		assertTrue(io.github.cmdralph.fabricated.core.report.KnownLimits.ofFinding("Forge", out).endsWith("(SEAM-3)"));
	}

	/**
	 * SEAM-4: a wrapper of vanilla's harvest check in the server's destroyBlock, as Apoli's and Porting Lib's (Apoli's with a
	 * namespaced share of the block it keeps at the method's start), applies, and the class is rewritten for it, on each
	 * platform; one in getDestroyProgress, as Apoli's other, the same for BlockBehaviour.
	 */
	@Test
	void aWrapperOfVanillasHarvestCheckPutBackHasTheClassRewritten(@TempDir Path dir) throws Exception {
		String state = "net.minecraft.world.level.block.state.BlockState";
		String harvest = " @WrapOperation(method = \"destroyBlock\", at = @At(value = \"INVOKE\", target = \"Lnet/minecraft/server/level/ServerPlayer;"
				+ "hasCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z\")) private boolean harvest(ServerPlayer player, " + state + " s,"
				+ " Operation<Boolean> original, @Share(value = \"block\", namespace = \"test\") LocalRef<Object> block) { block.get(); return original.call(player, s); }"
				+ " @Inject(method = \"destroyBlock\", at = @At(\"HEAD\")) private void keep(BlockPos pos, CallbackInfoReturnable<Boolean> cir,"
				+ " @Share(value = \"block\", namespace = \"test\") LocalRef<Object> block) { block.set(pos); }";
		String progress = " @WrapOperation(method = \"getDestroyProgress\", at = @At(value = \"INVOKE\", target = \"Lnet/minecraft/world/entity/player/Player;"
				+ "hasCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z\")) private boolean delta(net.minecraft.world.entity.player.Player player,"
				+ " " + state + " s, Operation<Boolean> original) { return original.call(player, s); }";
		for (boolean forge : List.of(false, true)) {
			Map<String, String> game = forge ? io.github.cmdralph.fabricated.core.bytecode.VanillaCallsTest.forgeSources()
					: io.github.cmdralph.fabricated.core.bytecode.VanillaCallsTest.neoForgeSources();
			List<PreflightFinding> findings = new java.util.ArrayList<>();
			MixinPreflight server = breaking(dir.resolve("server-" + forge), game, harvest, findings, new HashMap<>());
			assertTrue(findings.stream().noneMatch(f -> f.severity() == Severity.LEFT_OUT || f.severity() == Severity.FAIL), findings.toString());
			assertEquals(Set.of("net/minecraft/server/level/ServerPlayerGameMode"), server.restoredSeams());
			String through = forge ? "IForgeBlock" : "IBlockExtension";
			assertTrue(findings.stream().anyMatch(f -> f.message().startsWith("@WrapOperation harvest hooks ServerPlayer.hasCorrectToolForDrops("
					+ "Lnet/minecraft/world/level/block/state/BlockState;)Z in destroyBlock, vanilla's call ") && f.message().contains(" makes through the block's "
					+ through + ".canHarvestBlock instead; Fabricated puts it back there for the blocks keeping that method's default") && f.message().endsWith("(SEAM-4)")),
					findings.toString());
			List<PreflightFinding> mining = new java.util.ArrayList<>();
			MixinPreflight behaviour = checked(dir.resolve("progress-" + forge), game, "net.minecraft.world.level.block.state.BlockBehaviour", progress, null, true,
					Set.of(), mining, new HashMap<>());
			assertTrue(mining.stream().noneMatch(f -> f.severity() == Severity.LEFT_OUT || f.severity() == Severity.FAIL), mining.toString());
			assertEquals(Set.of("net/minecraft/world/level/block/state/BlockBehaviour"), behaviour.restoredSeams());
			assertTrue(mining.stream().anyMatch(f -> f.message().contains("@WrapOperation delta hooks Player.hasCorrectToolForDrops(") && f.message().contains(
					" makes through " + (forge ? "ForgeHooks.isCorrectToolForDrops" : "EventHooks.doPlayerHarvestCheck") + " instead; Fabricated puts it back there")
					&& f.message().endsWith("(SEAM-4)")), mining.toString());
		}
		assertTrue(breakingNote(dir.resolve("note"), harvest).equals("@WrapOperation harvest hooks ServerPlayer.hasCorrectToolForDrops("
				+ "Lnet/minecraft/world/level/block/state/BlockState;)Z in destroyBlock, vanilla's call NeoForge's ServerPlayerGameMode makes through the block's"
				+ " IBlockExtension.canHarvestBlock instead; Fabricated puts it back there for the blocks keeping that method's default, NeoForge's harvest check"
				+ " event following it as that default posts it, so the hook applies to their harvest check, and a block overriding it is checked as NeoForge has"
				+ " it, without the hook (SEAM-4)"));
	}

	/** The SEAM-4 note of a NeoForge server's mixin. */
	private static String breakingNote(Path dir, String members) throws Exception {
		List<PreflightFinding> findings = new java.util.ArrayList<>();
		breaking(dir, members, findings);
		return findings.stream().map(PreflightFinding::message).filter(m -> m.endsWith("(SEAM-4)")).findFirst().orElse("");
	}

	/**
	 * SEAM-4: a hook of vanilla's check of the held item in the server's destroyBlock, which both platforms ask inside their
	 * block break event's hook, stays out, its hint saying why, which the known limit reads.
	 */
	@Test
	void aHookOfTheHeldItemsCheckStaysOutSayingWhy(@TempDir Path dir) throws Exception {
		String hook = " @ModifyExpressionValue(method = \"destroyBlock\", at = @At(value = \"INVOKE\", target = \"Lnet/minecraft/world/item/Item;canAttackBlock("
				+ "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
				+ "Lnet/minecraft/world/entity/player/Player;)Z\")) private boolean attack(boolean original) { return original; }";
		for (boolean forge : List.of(false, true)) {
			Map<String, String> game = new HashMap<>(forge ? io.github.cmdralph.fabricated.core.bytecode.VanillaCallsTest.forgeSources()
					: io.github.cmdralph.fabricated.core.bytecode.VanillaCallsTest.neoForgeSources());
			game.put("com/llamalad7/mixinextras/injector/ModifyExpressionValue.java", "package com.llamalad7.mixinextras.injector;"
					+ " public @interface ModifyExpressionValue { String[] method(); org.spongepowered.asm.mixin.injection.At[] at(); }");
			List<PreflightFinding> findings = new java.util.ArrayList<>();
			breaking(dir.resolve("attack-" + forge), game, hook.replace("@ModifyExpressionValue", "@com.llamalad7.mixinextras.injector.ModifyExpressionValue"),
					findings, new HashMap<>());
			PreflightFinding out = findings.stream().filter(f -> f.severity() == Severity.LEFT_OUT).findFirst().orElseThrow();
			String loader = forge ? "Forge" : "NeoForge";
			assertEquals(loader + "'s ServerPlayerGameMode asks the held item's canAttackBlock inside its block break event ("
					+ (forge ? "ForgeHooks.onBlockBreakEvent" : "CommonHooks.fireBlockBreak") + "), which can overrule the answer, so Fabricated doesn't put that"
					+ " check back there (SEAM-4).", out.suggestion(), findings.toString());
			assertTrue(io.github.cmdralph.fabricated.core.report.KnownLimits.ofFinding(loader, out).endsWith("(SEAM-4)"));
		}
	}

	/** The item checks' game classes (SEAM-6) in a form, with what the mixins compiled by {@link #checked} import. */
	private static Map<String, String> itemGame(Map<String, String> form) {
		Map<String, String> game = new HashMap<>(form);
		game.put("net/minecraft/core/BlockPos.java", "package net.minecraft.core; public record BlockPos(int x) { }");
		game.put("net/minecraft/server/level/ServerLevel.java", "package net.minecraft.server.level; public class ServerLevel { }");
		game.put("com/llamalad7/mixinextras/injector/ModifyExpressionValue.java", "package com.llamalad7.mixinextras.injector;"
				+ " public @interface ModifyExpressionValue { String[] method(); org.spongepowered.asm.mixin.injection.At[] at(); }");
		return game;
	}

	/**
	 * SEAM-6: a wrapper of vanilla's shears check in the tripwire's playerWillDestroy, as BCLib's and Porting Lib's, applies,
	 * and the class is rewritten for it, on NeoForge and on Forge 47 under SRG's names, its note saying how.
	 */
	@Test
	void aWrapperOfVanillasItemCheckPutBackHasTheClassRewritten(@TempDir Path dir) throws Exception {
		for (boolean forge47 : List.of(false, true)) {
			Map<String, String> game = itemGame(forge47 ? io.github.cmdralph.fabricated.core.bytecode.ItemChecksTest.forge47Sources()
					: io.github.cmdralph.fabricated.core.bytecode.ItemChecksTest.neoForgeSources());
			String method = forge47 ? "m_5707_" : "playerWillDestroy";
			String is = (forge47 ? "m_150930_" : "is") + "(Lnet/minecraft/world/item/Item;)Z";
			String wrap = " @WrapOperation(method = \"" + method + "\", at = @At(value = \"INVOKE\", target = \"Lnet/minecraft/world/item/ItemStack;" + is + "\"))"
					+ " private boolean shears(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.Item item, Operation<Boolean> original) {"
					+ " return original.call(stack, item); }";
			List<PreflightFinding> findings = new java.util.ArrayList<>();
			MixinPreflight preflight = checked(dir.resolve("tripwire-" + forge47), game, "net.minecraft.world.level.block.TripWireBlock", wrap, null, true, Set.of(),
					findings, new HashMap<>());
			assertTrue(findings.stream().noneMatch(f -> f.severity() == Severity.LEFT_OUT || f.severity() == Severity.FAIL), findings.toString());
			assertEquals(Set.of("net/minecraft/world/level/block/TripWireBlock"), preflight.restoredSeams());
			// The class is named by the pre-flight's platform (here always NeoForge's), the note by the row's.
			String loader = forge47 ? "Forge" : "NeoForge";
			assertTrue(findings.stream().anyMatch(f -> f.message().equals("@WrapOperation shears hooks ItemStack." + is + " in " + method + ", vanilla's call"
					+ " NeoForge's TripWireBlock makes through the item's " + (forge47 ? "IForgeItem" : "IItemExtension") + ".canPerformAction instead; Fabricated"
					+ " puts it back there for the items keeping that method's default, so the hook applies to their check, and an item overriding it (vanilla's"
					+ " shears among them) is checked as " + loader + " has it, without the hook (SEAM-6)")), findings.toString());
		}
	}

	/**
	 * SEAM-8: a wrapper of vanilla's friction in a living entity's travel, as FrozenLib's, Artifacts' and Spell Engine's,
	 * applies, and the class is rewritten for it, on NeoForge and on Forge 47 under SRG's names, its note saying how: the
	 * block state's call, the same on both platforms, as the note names it.
	 */
	@Test
	void aWrapperOfVanillasFrictionPutBackHasTheClassRewritten(@TempDir Path dir) throws Exception {
		for (boolean forge47 : List.of(false, true)) {
			Map<String, String> game = new HashMap<>(forge47 ? io.github.cmdralph.fabricated.core.bytecode.ExtensionCallsTest.forge47Sources()
					: io.github.cmdralph.fabricated.core.bytecode.ExtensionCallsTest.neoForgeSources());
			game.put("net/minecraft/server/level/ServerLevel.java", "package net.minecraft.server.level; public class ServerLevel { }");
			String method = forge47 ? "m_7023_" : "travel", friction = (forge47 ? "m_49958_" : "getFriction") + "()F";
			String wrap = " @WrapOperation(method = \"" + method + "\", at = @At(value = \"INVOKE\", target = \"Lnet/minecraft/world/level/block/Block;" + friction + "\"))"
					+ " private float friction(net.minecraft.world.level.block.Block block, Operation<Float> original) { return original.call(block); }";
			List<PreflightFinding> findings = new java.util.ArrayList<>();
			MixinPreflight preflight = checked(dir.resolve("friction-" + forge47), game, "net.minecraft.world.entity.LivingEntity", wrap, null, true, Set.of(), findings,
					new HashMap<>());
			assertTrue(findings.stream().noneMatch(f -> f.severity() == Severity.LEFT_OUT || f.severity() == Severity.FAIL), findings.toString());
			assertEquals(Set.of("net/minecraft/world/entity/LivingEntity"), preflight.restoredSeams());
			assertTrue(findings.stream().anyMatch(f -> f.message().equals("@WrapOperation friction hooks Block." + friction + " in " + method + ", vanilla's call"
					+ " NeoForge's LivingEntity makes through the block state's getFriction(level, pos, entity) instead; Fabricated puts it back there for the blocks"
					+ " keeping that method's default, so the hook applies to them, and a block overriding it is asked as the platform has it, without the hook"
					+ " (SEAM-8)")), findings.toString());
		}
	}

	/**
	 * SEAM-6: a hook of vanilla's shears check in a mob's mobInteract, where the platform shears it from the shears item, and
	 * one of vanilla's map check in the held item renderer, where the platform asks whether the item is a MapItem, stay out,
	 * their hints saying why, which the known limits read.
	 */
	@Test
	void aHookOfAnItemCheckThePlatformDoesntMakeStaysOutSayingWhy(@TempDir Path dir) throws Exception {
		Map<String, String> game = itemGame(io.github.cmdralph.fabricated.core.bytecode.ItemChecksTest.neoForgeSources());
		game.put("net/minecraft/world/entity/animal/sheep/Sheep.java", "package net.minecraft.world.entity.animal.sheep; public class Sheep {"
				+ " public boolean mobInteract(net.minecraft.world.entity.player.Player player) { return player.getMainHandItem().isEmpty(); } }");
		game.put("net/minecraft/world/item/MapItem.java", "package net.minecraft.world.item; public class MapItem extends Item { public MapItem() { super(\"map\"); } }");
		game.put("net/neoforged/neoforge/client/ClientHooks.java", "package net.neoforged.neoforge.client; public class ClientHooks { public static void hand() { } }");
		game.put("net/minecraft/client/renderer/ItemInHandRenderer.java", "package net.minecraft.client.renderer; public class ItemInHandRenderer {"
				+ " public boolean renderArmWithItem(net.minecraft.world.item.ItemStack stack) { net.neoforged.neoforge.client.ClientHooks.hand();"
				+ " return stack.getItem() instanceof net.minecraft.world.item.MapItem; } }");
		String hook = " @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = \"%s\", at = @At(value = \"INVOKE\", target ="
				+ " \"Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z\")) private boolean check(boolean original) { return original; }";
		Map<String, String> cases = Map.of("net.minecraft.world.entity.animal.sheep.Sheep", "mobInteract", "net.minecraft.client.renderer.ItemInHandRenderer",
				"renderArmWithItem");
		for (Map.Entry<String, String> e : cases.entrySet()) {
			List<PreflightFinding> findings = new java.util.ArrayList<>();
			checked(dir.resolve(e.getValue()), game, e.getKey(), String.format(hook, e.getValue()), null, true, Set.of(), findings, new HashMap<>());
			PreflightFinding out = findings.stream().filter(f -> f.severity() == Severity.LEFT_OUT).findFirst().orElseThrow();
			assertEquals(e.getValue().equals("mobInteract") ? "Sheep's mobInteract asks for no shears here: the platform shears it from the shears item,"
					+ " through its shearable extension (NeoForge's IShearable, Forge's IForgeShearable), which posts its events and drops what its loot says, so"
					+ " Fabricated doesn't put vanilla's check back there (SEAM-6)." : "NeoForge's ItemInHandRenderer asks whether the held item is a MapItem or a"
					+ " CrossbowItem instead, which other mods' map and crossbow items are too, so Fabricated doesn't put vanilla's check back there (SEAM-6).",
					out.suggestion(), findings.toString());
			assertTrue(io.github.cmdralph.fabricated.core.report.KnownLimits.ofFinding("NeoForge", out).endsWith("(SEAM-6)"),
					String.valueOf(io.github.cmdralph.fabricated.core.report.KnownLimits.ofFinding("NeoForge", out)));
		}
	}

	@Test
	void aMixinHookingNoSeamLeavesTheClassAsThePlatformHasIt(@TempDir Path dir) throws Exception {
		// As Lithium's: a redirect of another call of the resolver's.
		assertEquals(Set.of(), recorded(dir, " @Redirect(method = \"addBranchingBlocks\", at = @At(value = \"INVOKE\", target = \"L" + RESOLVER
				+ ";blocked(" + STATE + ")Z\")) private boolean free(PistonStructureResolver self, BlockState s) { return false; }"));
	}

	@Test
	void aMixinHookingASeamHasTheClassRewritten(@TempDir Path dir) throws Exception {
		assertEquals(Set.of(RESOLVER), recorded(dir.resolve("inject"), " @Inject(method = \"isSticky\", at = @At(\"HEAD\"), cancellable = true)"
				+ " private static void sticky(BlockState s, CallbackInfoReturnable<Boolean> cir) { }"));
		// A shadow of one alone, which Mixin can't apply where the class lacks it.
		assertEquals(Set.of(RESOLVER), recorded(dir.resolve("shadow"), " @Shadow private static boolean canStickToEachOther(BlockState a, BlockState b) {"
				+ " throw new AssertionError(); }"));
	}
}
