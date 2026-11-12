package io.github.cmdralph.fabricated.core.bytecode;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.core.testutil.TestSources;

/**
 * SEAM-3: vanilla's block-breaking calls, which NeoForge and Forge make through the block state's
 * {@code onDestroyedByPlayer}, put back in {@code destroyBlock} for the blocks keeping its default. Each fixture is the
 * platform's code in its form (NeoForge 21.1's, NeoForge 21.11 to 26.3's, Forge's; the server's and the client's; Forge 47's
 * under SRG's names), against stand-ins of the game whose calls log what they do; the extensions also name a class the
 * game lacks as it runs, as Forge 66's name client classes. The class rewritten does what the platform's did, step for
 * step, for a block keeping the default, one overriding it, one overriding it to call it, one overriding it through an
 * interface of its own, one inheriting an override and a state whose class overrides the state's; a hook of the call
 * put back runs where the block keeps the default, and only there.
 */
public class VanillaCallsTest {
	private static final String SERVER = "net/minecraft/server/level/ServerPlayerGameMode";
	private static final String CLIENT = "net/minecraft/client/multiplayer/MultiPlayerGameMode";

	/** A platform's form: its extensions, its game mode classes' code, and the names its game runs with. */
	private enum Form {
		NEOFORGE_21_1(false, false, false), NEOFORGE(false, true, false), FORGE(true, false, false), FORGE_47(true, false, true);

		final boolean forge, tool, srg;

		Form(boolean forge, boolean tool, boolean srg) {
			this.forge = forge;
			this.tool = tool;
			this.srg = srg;
		}

		String ext() {
			return forge ? "net.minecraftforge.common.extensions" : "net.neoforged.neoforge.common.extensions";
		}

		String stateExt() {
			return forge ? "IForgeBlockState" : "IBlockStateExtension";
		}

		String blockExt() {
			return forge ? "IForgeBlock" : "IBlockExtension";
		}

		/** The extension's parameters after the player, and the arguments passed for them. */
		String params() {
			return (tool ? " ItemStack tool," : "") + " boolean willHarvest, FluidState fluid";
		}

		String args() {
			return (tool ? " tool," : "") + " willHarvest, fluid";
		}

		String hook() {
			return forge ? "net.minecraftforge.common.ForgeHooks.onBlockBreakEvent();" : "net.neoforged.neoforge.common.CommonHooks.fireBlockBreak();";
		}
	}

	private static final Map<String, String> MOJANG = new LinkedHashMap<>();
	private static final Map<String, String> SRG = new LinkedHashMap<>();
	static {
		for (String[] names : new String[][] {{"getBlockState", "m_8055_"}, {"setBlock", "m_7731_"}, {"removeBlock", "m_7471_"},
				{"createLegacyBlock", "m_76188_"}, {"getBlock", "m_60734_"}, {"destroy", "m_6786_"}, {"playerWillDestroy", "m_5707_"},
				{"destroyBlock", "m_9280_"}, {"clientDestroyBlock", "m_105267_"}, {"level", "f_9244_"}, {"player", "f_9245_"}}) {
			MOJANG.put("$" + names[0] + "$", names[0].equals("clientDestroyBlock") ? "destroyBlock" : names[0]);
			SRG.put("$" + names[0] + "$", names[1]);
		}
	}

	/** The game's stand-ins and the platform's classes in the form. */
	private static Map<String, String> sources(Form form, String serverBody, String clientBody) {
		String willDestroy = form.srg ? "void" : "BlockState";
		Map<String, String> sources = new HashMap<>();
		sources.put("net/minecraft/core/BlockPos.java", "package net.minecraft.core; public record BlockPos(int x) { }");
		sources.put("net/minecraft/world/level/LevelAccessor.java", "package net.minecraft.world.level; public interface LevelAccessor { }");
		sources.put("net/minecraft/world/level/Level.java", "package net.minecraft.world.level; import java.util.*; import net.minecraft.core.BlockPos;"
				+ " import net.minecraft.world.level.block.state.BlockState; import net.minecraft.world.level.material.FluidState;"
				+ " public class Level implements LevelAccessor { public final List<String> log = new ArrayList<>(); public final Map<Integer, BlockState> blocks = new HashMap<>();"
				+ " final boolean client; public Level(boolean client) { this.client = client; } public boolean isClientSide() { return client; }"
				+ " public BlockState $getBlockState$(BlockPos pos) { return blocks.getOrDefault(pos.x(), BlockState.AIR); }"
				+ " public FluidState getFluidState(BlockPos pos) { return new FluidState(); }"
				+ " public boolean $setBlock$(BlockPos pos, BlockState state, int flags) { log.add(\"setBlock \" + state + \" \" + flags); return blocks.put(pos.x(), state) != state; }"
				+ " public boolean $removeBlock$(BlockPos pos, boolean moving) { log.add(\"removeBlock\"); return $setBlock$(pos, getFluidState(pos).$createLegacyBlock$(), 3 | (moving ? 64 : 0)); } }");
		sources.put("net/minecraft/server/level/ServerLevel.java", "package net.minecraft.server.level; public class ServerLevel extends net.minecraft.world.level.Level {"
				+ " public ServerLevel() { super(false); } }");
		sources.put("net/minecraft/world/level/material/FluidState.java", "package net.minecraft.world.level.material; import net.minecraft.world.level.block.state.BlockState;"
				+ " public class FluidState { public BlockState $createLegacyBlock$() { return BlockState.AIR; } }");
		sources.put("net/minecraft/world/entity/player/Player.java", "package net.minecraft.world.entity.player; public class Player { }");
		sources.put("net/minecraft/server/level/ServerPlayer.java", "package net.minecraft.server.level; public class ServerPlayer extends net.minecraft.world.entity.player.Player { }");
		sources.put("net/minecraft/world/item/ItemStack.java", "package net.minecraft.world.item; public class ItemStack { public ItemStack copy() { return new ItemStack(); } }");
		String imports = " import net.minecraft.core.BlockPos; import net.minecraft.world.level.*; import net.minecraft.world.level.block.Block;"
				+ " import net.minecraft.world.level.block.state.BlockState; import net.minecraft.world.level.material.FluidState;"
				+ " import net.minecraft.world.entity.player.Player; import net.minecraft.world.item.ItemStack;";
		sources.put("net/minecraft/world/level/block/Block.java", "package net.minecraft.world.level.block;" + imports
				+ " public class Block implements " + form.ext() + "." + form.blockExt() + " { public final String name; public Block(String name) { this.name = name; }"
				+ " public " + willDestroy + " $playerWillDestroy$(Level level, BlockPos pos, BlockState state, Player player) { level.log.add(\"playerWillDestroy \" + name);"
				+ (form.srg ? "" : " return state;") + " }"
				+ " public void $destroy$(LevelAccessor level, BlockPos pos, BlockState state) { ((Level) level).log.add(\"destroy \" + state); } }");
		sources.put("net/minecraft/world/level/block/state/BlockState.java", "package net.minecraft.world.level.block.state; import net.minecraft.world.level.block.Block;"
				+ " public class BlockState implements " + form.ext() + "." + form.stateExt() + " { public static final BlockState AIR = new BlockState(new Block(\"air\"));"
				+ " final Block block; public BlockState(Block block) { this.block = block; } public Block $getBlock$() { return block; }"
				+ " public String toString() { return block.name; } }");
		String ext = form.ext().replace('.', '/') + "/";
		sources.put(ext + form.stateExt() + ".java", "package " + form.ext() + ";" + imports + " public interface " + form.stateExt() + " {"
				+ " default boolean onDestroyedByPlayer(Level level, BlockPos pos, Player player," + form.params() + ") {"
				+ " return ((BlockState) this).$getBlock$().onDestroyedByPlayer((BlockState) this, level, pos, player," + form.args() + "); } }");
		String body = form.forge ? " ((Block) this).$playerWillDestroy$(level, pos, state, player); return level.$setBlock$(pos, fluid.$createLegacyBlock$(), level.isClientSide() ? 11 : 3);"
				: " if (level.isClientSide()) return level.$setBlock$(pos, fluid.$createLegacyBlock$(), 11); return level.$removeBlock$(pos, false);";
		sources.put(ext + form.blockExt() + ".java", "package " + form.ext() + ";" + imports + " public interface " + form.blockExt() + " {"
				+ " default boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player," + form.params() + ") {" + body + " }"
				+ " default void drawn(com.example.ClientOnly client) { } }");
		sources.put("com/example/ClientOnly.java", "package com.example; public class ClientOnly { }");
		sources.put(form.forge ? "net/minecraftforge/common/ForgeHooks.java" : "net/neoforged/neoforge/common/CommonHooks.java",
				form.forge ? "package net.minecraftforge.common; public class ForgeHooks { public static void onBlockBreakEvent() { } }"
						: "package net.neoforged.neoforge.common; public class CommonHooks { public static void fireBlockBreak() { } }");
		// A platform mod's blocks: one overriding the extension, one overriding it to call the default.
		String override = " @Override public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player," + form.params() + ") {";
		sources.put("com/example/Overriding.java", "package com.example;" + imports + " public class Overriding extends Block { public Overriding() { super(\"overriding\"); }"
				+ override + " level.log.add(\"override \" + willHarvest); return level.$setBlock$(pos, new BlockState(new Block(\"stump\")), 3); } }");
		sources.put("com/example/Hook.java", "package com.example; public class Hook { public static final java.util.List<String> LOG = new java.util.ArrayList<>();"
				+ " public static void hooked() { LOG.add(\"hooked\"); } }");
		// One overriding it through an interface of its own extending the platform's.
		sources.put("com/example/OverridingExtension.java", "package com.example;" + imports + " public interface OverridingExtension extends "
				+ form.ext() + "." + form.blockExt() + " {" + override.replace(" @Override public", " @Override default") + " level.log.add(\"interface\");"
				+ " return level.$setBlock$(pos, new BlockState(new Block(\"stump\")), 3); } }");
		sources.put("com/example/ViaInterface.java", "package com.example;" + imports + " public class ViaInterface extends Block implements OverridingExtension {"
				+ " public ViaInterface() { super(\"via\"); } }");
		// One inheriting another's override; and a state of a class of its own overriding the state's extension.
		sources.put("com/example/Inheriting.java", "package com.example; public class Inheriting extends Overriding { }");
		sources.put("com/example/OverridingState.java", "package com.example;" + imports + " public class OverridingState extends BlockState {"
				+ " public OverridingState(Block block) { super(block); } @Override public boolean onDestroyedByPlayer(Level level, BlockPos pos, Player player,"
				+ form.params() + ") { level.log.add(\"state override\"); return level.$setBlock$(pos, new BlockState(new Block(\"stump\")), 3); } }");
		// One whose own methods name a class the game lacks as it runs: whether it overrides the default can't be told.
		sources.put("com/example/Unreadable.java", "package com.example;" + imports + " public class Unreadable extends Block { public Unreadable() {"
				+ " super(\"unreadable\"); } public void drawn(ClientOnly client) { } }");
		sources.put("com/example/Calling.java", "package com.example;" + imports + " public class Calling extends Block { public Calling() { super(\"calling\"); }"
				+ override + " level.log.add(\"calling\"); return super.onDestroyedByPlayer(state, level, pos, player," + form.args() + "); } }");
		sources.put(SERVER + ".java", "package net.minecraft.server.level;" + imports + " public class ServerPlayerGameMode {"
				+ " protected ServerLevel $level$; protected final ServerPlayer $player$; private final boolean creative;"
				+ " public ServerPlayerGameMode(ServerLevel level, ServerPlayer player, boolean creative) { this.$level$ = level; this.$player$ = player; this.creative = creative; }"
				+ " boolean harvest(BlockState state) { return true; }" + serverBody + " }");
		sources.put(CLIENT + ".java", "package net.minecraft.client.multiplayer;" + imports + " public class MultiPlayerGameMode {"
				+ " final Level level; final Player player; public MultiPlayerGameMode(Level level, Player player) { this.level = level; this.player = player; }"
				+ clientBody + " }");
		Map<String, String> names = form.srg ? SRG : MOJANG;
		sources.replaceAll((file, text) -> {
			for (Map.Entry<String, String> e : names.entrySet()) text = text.replace(e.getKey(), e.getValue());
			return text;
		});
		return sources;
	}

	/** The server's {@code destroyBlock} and the platform's {@code removeBlock}, in the form's code. */
	private static String server(Form form) {
		String tool = form.tool ? ", new ItemStack().copy()" : "";
		if (form.forge) {
			return " public boolean $destroyBlock$(BlockPos pos) { BlockState blockstate = this.$level$.$getBlockState$(pos); " + form.hook()
					+ " Block block = blockstate.$getBlock$(); if (this.creative) { removeBlock(pos, false); return true; }"
					+ " boolean flag1 = harvest(blockstate); boolean flag = removeBlock(pos, flag1); if (flag && flag1) this.$level$.log.add(\"playerDestroy\"); return true; }"
					+ " private boolean removeBlock(BlockPos pos, boolean canHarvest) { BlockState state = this.$level$.$getBlockState$(pos);"
					+ " boolean removed = state.onDestroyedByPlayer(this.$level$, pos, this.$player$, canHarvest, this.$level$.getFluidState(pos));"
					+ " if (removed) state.$getBlock$().$destroy$(this.$level$, pos, state); return removed; }";
		}
		return " public boolean $destroyBlock$(BlockPos pos) { BlockState blockstate1 = this.$level$.$getBlockState$(pos); " + form.hook()
				+ " Block block = blockstate1.$getBlock$(); BlockState blockstate = block.$playerWillDestroy$(this.$level$, pos, blockstate1, this.$player$);"
				+ " if (this.creative) { removeBlock(pos, blockstate, false" + tool + "); return true; }"
				+ " boolean flag1 = harvest(blockstate); boolean flag = removeBlock(pos, blockstate, flag1" + tool + ");"
				+ " if (flag1 && flag) this.$level$.log.add(\"playerDestroy\"); return true; }"
				+ " private boolean removeBlock(BlockPos pos, BlockState state, boolean canHarvest" + (form.tool ? ", ItemStack tool" : "") + ") {"
				+ " boolean removed = state.onDestroyedByPlayer(this.$level$, pos, this.$player$" + (form.tool ? ", tool" : "") + ", canHarvest, this.$level$.getFluidState(pos));"
				+ " if (removed) state.$getBlock$().$destroy$(this.$level$, pos, state); return removed; }";
	}

	/** The client's {@code destroyBlock}, in the form's code; {@code level} is what the extension's call is given as the level. */
	private static String client(Form form, String level) {
		String willDestroy = form.forge ? "" : " BlockState removed = block.$playerWillDestroy$(level, pos, blockstate, this.player);";
		return " public boolean $clientDestroyBlock$(BlockPos pos) { " + form.hook() + " Level level = this.level; BlockState blockstate = level.$getBlockState$(pos);"
				+ " Block block = blockstate.$getBlock$();" + willDestroy + " FluidState fluidstate = level.getFluidState(pos);"
				+ " boolean flag = blockstate.onDestroyedByPlayer(" + level + ", pos, this.player," + (form.tool ? " new ItemStack().copy()," : "") + " false, fluidstate);"
				+ " if (flag) block.$destroy$(level, pos, " + (form.forge ? "blockstate" : "removed") + "); return flag; }";
	}

	/** NeoForge 21.11 to 26.3's game classes, as the fixtures have them, for pre-flight's tests. */
	public static Map<String, String> neoForgeSources() {
		return sources(Form.NEOFORGE, server(Form.NEOFORGE), client(Form.NEOFORGE, "level"));
	}

	/** Forge 52 to 66's game classes, as the fixtures have them. */
	public static Map<String, String> forgeSources() {
		return sources(Form.FORGE, server(Form.FORGE), client(Form.FORGE, "level"));
	}

	private static Map<String, byte[]> platform(Path dir, Form form) throws Exception {
		return TestSources.compileWithLocals(dir, sources(form, server(form), client(form, "level")));
	}

	/** The classes with the game mode classes put back, as they load. */
	private static Map<String, byte[]> restored(Map<String, byte[]> platform) {
		Map<String, byte[]> restored = new HashMap<>(platform);
		for (String name : List.of(SERVER, CLIENT)) {
			ClassNode node = node(platform.get(name));
			VanillaSeams.Restoring done = VanillaSeams.restoreWhole(node);
			assertEquals(null, done.failure());
			assertTrue(!done.restored().isEmpty(), name);
			restored.put(name, write(node));
		}
		return restored;
	}

	@Test
	void vanillasCallsArePutBackWhereTheBlockKeepsThePlatformsDefault(@TempDir Path dir) throws Exception {
		for (Form form : Form.values()) {
			Map<String, byte[]> platform = platform(dir.resolve(form.name()), form);
			Map<String, byte[]> restored = restored(platform);
			Map<String, String> names = form.srg ? SRG : MOJANG;
			// Every block, on either path, does what the platform's code made it do; the default's steps are vanilla's calls.
			for (String block : List.of("net.minecraft.world.level.block.Block", "com.example.Overriding", "com.example.Calling", "com.example.ViaInterface",
					"com.example.Inheriting", "com.example.OverridingState")) {
				for (boolean creative : List.of(false, true)) {
					List<String> before = server(platform, block, creative, false), after = server(restored, block, creative, false);
					List<String> expected = new ArrayList<>(before);
					// Forge's default sets the fluid's block, where vanilla's removeBlock does.
					if (form.forge && block.endsWith(".Block")) expected.add(expected.indexOf("setBlock air 3"), "removeBlock");
					assertEquals(expected, after, form + " " + block + " creative " + creative);
				}
				assertEquals(client(platform, block, false), client(restored, block, false), form + " client " + block);
			}
			// A hook of vanilla's call put back runs for the block keeping the default, and only for it.
			assertTrue(server(restored, "net.minecraft.world.level.block.Block", false, true).contains("hooked"), form.name());
			assertTrue(client(restored, "net.minecraft.world.level.block.Block", true).contains("hooked"), form.name());
			for (String block : List.of("com.example.Overriding", "com.example.Calling", "com.example.ViaInterface", "com.example.Inheriting",
					"com.example.OverridingState")) {
				assertTrue(!server(restored, block, false, true).contains("hooked"), form + " " + block);
				assertTrue(!client(restored, block, true).contains("hooked"), form + " " + block);
			}
			// Pre-flight reads what was put back.
			String gameMode = names.get("$destroyBlock$") + "(Lnet/minecraft/core/BlockPos;)Z";
			List<VanillaSeams.Restored> server = VanillaSeams.restoredIn(node(restored.get(SERVER)));
			assertEquals(List.of("net/minecraft/server/level/ServerLevel;" + names.get("$removeBlock$"), "net/minecraft/world/level/block/Block;" + names.get("$destroy$")),
					server.stream().map(r -> r.owner() + ";" + r.name()).toList(), form.name());
			assertTrue(server.stream().allMatch(r -> r.in().equals(gameMode)), server.toString());
			List<String> client = VanillaSeams.restoredIn(node(restored.get(CLIENT))).stream().map(r -> r.name()).toList();
			assertEquals(form.forge ? List.of(names.get("$playerWillDestroy$"), names.get("$createLegacyBlock$"), names.get("$setBlock$"))
					: List.of(names.get("$createLegacyBlock$"), names.get("$setBlock$")), client, form.name());
		}
	}

	/**
	 * Whether a block keeps the default is asked once for each class, the answers kept in the class's field: a block
	 * whose classes can't be read is taken to override it, once, and breaks as the platform has it.
	 */
	@Test
	void theAnswerIsKeptForEachClass(@TempDir Path dir) throws Exception {
		ClassLoader loader = loader(hooked(restored(platform(dir, Form.NEOFORGE)), SERVER, "net/minecraft/server/level/ServerLevel"));
		Class<?> gameMode = loader.loadClass(SERVER.replace('/', '.'));
		String block = "net.minecraft.world.level.block.Block";
		for (String broken : List.of(block, "com.example.Overriding", "com.example.Unreadable", block, "com.example.Unreadable")) {
			Object level = loader.loadClass("net.minecraft.server.level.ServerLevel").getConstructor().newInstance();
			place(loader, level, broken);
			Object player = loader.loadClass("net.minecraft.server.level.ServerPlayer").getConstructor().newInstance();
			destroy(loader, gameMode, gameMode.getConstructors()[0].newInstance(level, player, false));
		}
		java.lang.reflect.Field field = gameMode.getDeclaredField("fabricated$overriding");
		field.setAccessible(true);
		Map<String, Boolean> known = new HashMap<>();
		((Map<?, ?>) field.get(null)).forEach((type, overrides) -> known.put(((Class<?>) type).getName(), (Boolean) overrides));
		assertEquals(Map.of("net.minecraft.world.level.block.state.BlockState", false, block, false, "com.example.Overriding", true,
				"com.example.Unreadable", true), known);
		assertEquals(List.of("hooked", "hooked"), loader.loadClass("com.example.Hook").getField("LOG").get(null));
	}

	/** Translation reads classes in parallel: every class put back at once is the same, the helpers copied whole. */
	@Test
	void classesPutBackInParallelAreTheSame(@TempDir Path dir) throws Exception {
		Map<String, byte[]> platform = platform(dir, Form.NEOFORGE);
		Callable<byte[]> restore = () -> {
			ClassNode node = node(platform.get(SERVER));
			assertTrue(!VanillaSeams.restore(node).isEmpty());
			return write(node);
		};
		byte[] reference = restore.call();
		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			for (int round = 0; round < 100; round++) {
				CyclicBarrier start = new CyclicBarrier(8);
				List<Future<byte[]>> copies = new ArrayList<>();
				for (int i = 0; i < 8; i++) copies.add(pool.submit(() -> {
					start.await();
					return restore.call();
				}));
				for (Future<byte[]> copy : copies) assertArrayEquals(reference, copy.get(60, TimeUnit.SECONDS), "round " + round);
			}
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void whatPuttingTheCallsBackChangesIsToldForNativeMixins(@TempDir Path dir) throws Exception {
		Map<String, byte[]> platform = platform(dir, Form.NEOFORGE);
		VanillaSeams.Rewrite server = VanillaSeams.rewriteOf(node(platform.get(SERVER)));
		String pos = "Lnet/minecraft/core/BlockPos;", state = "Lnet/minecraft/world/level/block/state/BlockState;";
		assertEquals(Set.of(SERVER + ";removeBlock(" + pos + state + "ZLnet/minecraft/world/item/ItemStack;)Z"), server.movedCalls());
		assertEquals(Set.of("destroyBlock(" + pos + ")Z"), server.holders());
		assertEquals(Set.of("removeBlock(" + pos + state + "ZLnet/minecraft/world/item/ItemStack;)Z"), server.bypassed());
		assertTrue(server.added().containsAll(Set.of("net/minecraft/server/level/ServerLevel;removeBlock(" + pos + "Z)Z",
				SERVER + ";level:Lnet/minecraft/server/level/ServerLevel;")), server.added().toString());
		assertEquals(Map.of("net/neoforged/neoforge/common/extensions/IBlockExtension", Set.of("onDestroyedByPlayer"),
				"net/neoforged/neoforge/common/extensions/IBlockStateExtension", Set.of("onDestroyedByPlayer")), server.others());
		// Forge's server: Level's removeBlock, which vanilla's call put back runs, where Forge's default sets the block itself.
		VanillaSeams.Rewrite forge = VanillaSeams.rewriteOf(node(platform(dir.resolve("forge"), Form.FORGE).get(SERVER)));
		assertEquals(Set.of("removeBlock", "m_7471_"), forge.others().get("net/minecraft/world/level/Level"));
		assertEquals(Set.of("removeBlock", "m_7471_"), forge.others().get("net/minecraft/server/level/ServerLevel"));
		// Vanilla's false and 11 are constants destroyBlock didn't have as many of.
		assertTrue(server.constants());
		VanillaSeams.Rewrite client = VanillaSeams.rewriteOf(node(platform.get(CLIENT)));
		assertTrue(client.constants());
		assertEquals(Set.of(), client.bypassed());
		assertTrue(client.added().contains("net/minecraft/world/level/Level;setBlock(" + pos + state + "I)Z"), client.added().toString());
	}

	@Test
	void whereThePlatformsCodeIsntInTheFormReadOrTheGameLacksWhatItNamesTheClassIsLeftAlone(@TempDir Path dir) throws Exception {
		// The extension's level is the result of a call: vanilla's branch can't load it again.
		Map<String, byte[]> computed = TestSources.compileWithLocals(dir.resolve("computed"), sources(Form.NEOFORGE, server(Form.NEOFORGE),
				client(Form.NEOFORGE, "levelOf(level)") + " static Level levelOf(Level level) { return level; }"));
		assertTrue(VanillaSeams.restore(node(computed.get(CLIENT))).isEmpty());
		assertArrayEquals(computed.get(CLIENT), VanillaSeams.over(computed::get).read(CLIENT));

		// NeoForge's form isn't put back in Forge's classes, nor Forge's in NeoForge's.
		Map<String, byte[]> forge = platform(dir.resolve("forge"), Form.FORGE);
		ClassNode node = node(forge.get(CLIENT));
		assertEquals(List.of("FORGE_CLIENT"), VanillaCalls.applicable(node).stream().map(r -> r.removal().name()).toList());

		// Pre-flight's view, from a game whose extension it can't find, keeps the class as it is.
		Map<String, byte[]> platform = platform(dir.resolve("neoforge"), Form.NEOFORGE);
		ClassBytesSource lacking = name -> name.endsWith("IBlockExtension") ? null : platform.get(name);
		assertArrayEquals(platform.get(SERVER), VanillaSeams.over(lacking).read(SERVER));
		assertTrue(!VanillaSeams.restoredIn(VanillaSeams.over(platform::get).readNode(SERVER, 0)).isEmpty());
		// Pre-flight's view rewrites a class once, at its first read.
		int[] reads = {0};
		ClassBytesSource counting = name -> {
			if (name.equals(SERVER)) reads[0]++;
			return platform.get(name);
		};
		ClassBytesSource view = VanillaSeams.over(counting);
		byte[] first = view.read(SERVER);
		int rewriting = reads[0];
		assertArrayEquals(first, view.read(SERVER));
		assertEquals(rewriting, reads[0], "read again, the class is neither read nor rewritten again");
		// One the rewrite fails on (here, one that doesn't parse) is read as it is, as it then loads, the failure told.
		byte[] broken = {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, 61, 0, 99};
		List<String> failed = new ArrayList<>();
		assertArrayEquals(broken, VanillaSeams.over(name -> name.equals(SERVER) ? broken : null, Set.of(), (name, e) -> failed.add(name)).read(SERVER));
		assertEquals(List.of(SERVER), failed);
	}

	/** The server's break of a block of the class at 1, in creative or not; with {@code hook}, vanilla's call put back is hooked. */
	private static List<String> server(Map<String, byte[]> classes, String block, boolean creative, boolean hook) throws Exception {
		Map<String, byte[]> loaded = hook ? hooked(classes, SERVER, "net/minecraft/server/level/ServerLevel") : classes;
		ClassLoader loader = loader(loaded);
		Object level = loader.loadClass("net.minecraft.server.level.ServerLevel").getConstructor().newInstance();
		place(loader, level, block);
		Object player = loader.loadClass("net.minecraft.server.level.ServerPlayer").getConstructor().newInstance();
		Class<?> gameMode = loader.loadClass(SERVER.replace('/', '.'));
		Object mode = gameMode.getConstructors()[0].newInstance(level, player, creative);
		destroy(loader, gameMode, mode);
		return log(loader, level);
	}

	private static List<String> client(Map<String, byte[]> classes, String block, boolean hook) throws Exception {
		Map<String, byte[]> loaded = hook ? hooked(classes, CLIENT, "net/minecraft/world/level/Level") : classes;
		ClassLoader loader = loader(loaded);
		Object level = loader.loadClass("net.minecraft.world.level.Level").getConstructor(boolean.class).newInstance(true);
		place(loader, level, block);
		Object player = loader.loadClass("net.minecraft.world.entity.player.Player").getConstructor().newInstance();
		Class<?> gameMode = loader.loadClass(CLIENT.replace('/', '.'));
		Object mode = gameMode.getConstructors()[0].newInstance(level, player);
		destroy(loader, gameMode, mode);
		return log(loader, level);
	}

	/** Places a block of the class at 1, or a plain block in a state of the class ({@code OverridingState}). */
	private static void place(ClassLoader loader, Object level, String block) throws Exception {
		Class<?> blockClass = loader.loadClass("net.minecraft.world.level.block.Block");
		boolean ownState = block.endsWith("State");
		Object instance = block.endsWith(".Block") || ownState ? blockClass.getConstructor(String.class).newInstance("stone")
				: loader.loadClass(block).getConstructor().newInstance();
		Constructor<?> state = loader.loadClass(ownState ? block : "net.minecraft.world.level.block.state.BlockState").getConstructor(blockClass);
		@SuppressWarnings("unchecked")
		Map<Integer, Object> blocks = (Map<Integer, Object>) level.getClass().getField("blocks").get(level);
		blocks.put(1, state.newInstance(instance));
	}

	private static void destroy(ClassLoader loader, Class<?> gameMode, Object mode) throws Exception {
		Object pos = loader.loadClass("net.minecraft.core.BlockPos").getConstructor(int.class).newInstance(1);
		for (java.lang.reflect.Method m : gameMode.getMethods()) {
			if (m.getReturnType() == boolean.class && m.getParameterCount() == 1 && m.getParameterTypes()[0] == pos.getClass()) m.invoke(mode, pos);
		}
	}

	/** What the break did: the level's log, and the hook's where it ran. */
	@SuppressWarnings("unchecked")
	private static List<String> log(ClassLoader loader, Object level) throws Exception {
		List<String> log = new ArrayList<>((List<String>) level.getClass().getField("log").get(level));
		log.addAll((List<String>) loader.loadClass("com.example.Hook").getField("LOG").get(null));
		return log;
	}

	/**
	 * The classes with vanilla's call put back in the game mode's method made through a hook that logs, as a Fabric mod's
	 * wrapper of it would: the removal on the server, the fluid's block on the client.
	 */
	private static Map<String, byte[]> hooked(Map<String, byte[]> classes, String gameMode, String owner) {
		ClassNode node = node(classes.get(gameMode));
		for (MethodNode method : node.methods) {
			for (AbstractInsnNode insn : method.instructions) {
				if (!(insn instanceof MethodInsnNode call) || !call.owner.equals(owner) || method.name.startsWith("fabricated$") || !call.desc.endsWith(")Z")) continue;
				method.instructions.insertBefore(call, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/example/Hook", "hooked", "()V", false));
			}
		}
		Map<String, byte[]> hooked = new HashMap<>(classes);
		ClassWriter writer = new ClassWriter(0);
		node.accept(writer);
		hooked.put(gameMode, writer.toByteArray());
		return hooked;
	}

	private static ClassLoader loader(Map<String, byte[]> classes) {
		return new ClassLoader(VanillaCallsTest.class.getClassLoader()) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] data = name.equals("com.example.ClientOnly") ? null : classes.get(name.replace('.', '/'));
				if (data == null) throw new ClassNotFoundException(name);
				return defineClass(name, data, 0, data.length);
			}
		};
	}

	private static ClassNode node(byte[] data) {
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, 0);
		return node;
	}

	private static byte[] write(ClassNode node) {
		ClassWriter writer = new ClassWriter(0);
		node.accept(writer);
		return writer.toByteArray();
	}
}
