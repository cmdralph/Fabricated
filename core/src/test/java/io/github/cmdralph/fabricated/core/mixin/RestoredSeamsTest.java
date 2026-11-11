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
		Map<String, String> sources = new HashMap<>(io.github.cmdralph.fabricated.core.bytecode.VanillaCallsTest.neoForgeSources());
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
				+ " @Mixin(ServerPlayerGameMode.class) public abstract class Breaking {" + members + " }");
		classes.putAll(TestSources.compileWithLocals(dir, TestSources.MIXIN_ANNOTATIONS, sources));
		ClassBytesSource game = VanillaSeams.over(classes::get);
		MixinPreflight preflight = new MixinPreflight(new ClassIndex(game), InstalledClasses.NONE, classes::get, ClassBytesSource.EMPTY, false, true,
				ClassBytesSource.EMPTY);
		MixinConfigFile config = MixinConfigFile.parse("{\"required\":true,\"package\":\"com.example.mixin\",\"mixins\":[\"Breaking\"],"
				+ "\"injectors\":{\"defaultRequire\":1}}");
		preflight.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), findings)
				.forEach((file, data) -> classes.put(file.substring(0, file.length() - ".class".length()), data));
		findings.addAll(preflight.check("test.mixins.json", config, Refmap.EMPTY, Set.of()));
		return preflight;
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
		assertEquals(Set.of(), breaking(dir.resolve("elsewhere"), wrap.replace("method = \"destroyBlock\"", "method = \"harvest\""), elsewhere).restoredSeams());
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
