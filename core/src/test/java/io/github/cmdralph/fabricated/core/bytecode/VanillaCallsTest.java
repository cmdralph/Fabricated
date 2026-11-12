package io.github.cmdralph.fabricated.core.bytecode;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import io.github.cmdralph.fabricated.core.translate.DefaultBridge;

/**
 * SEAM-3: vanilla's block-breaking calls, which NeoForge and Forge make through the block state's
 * {@code onDestroyedByPlayer}, put back in {@code destroyBlock} for the blocks keeping its default; SEAM-4: vanilla's
 * harvest check, {@code hasCorrectToolForDrops}, which they make through the state's {@code canHarvestBlock} there and
 * through a hook of theirs in {@code getDestroyProgress}. Each fixture is the platform's code in its form (NeoForge 21.1's,
 * NeoForge 21.11 to 26.3's, Forge's; the server's and the client's; Forge 47's under SRG's names), against stand-ins of the
 * game whose calls log what they do, NeoForge's harvest event and Forge's among them; the extensions also name a class the
 * game lacks as it runs, as Forge 66's name client classes. The class rewritten does what the platform's did, step for
 * step, for a block keeping the default, one overriding it, one overriding it to call it, one overriding it through an
 * interface of its own, one inheriting an override and a state whose class overrides the state's; a hook of the call
 * put back runs where the block keeps the default, and only there.
 */
public class VanillaCallsTest {
	private static final String SERVER = "net/minecraft/server/level/ServerPlayerGameMode";
	private static final String CLIENT = "net/minecraft/client/multiplayer/MultiPlayerGameMode";
	private static final String BEHAVIOUR = "net/minecraft/world/level/block/state/BlockBehaviour";

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
				{"destroyBlock", "m_9280_"}, {"clientDestroyBlock", "m_105267_"}, {"level", "f_9244_"}, {"player", "f_9245_"},
				{"hasCorrectToolForDrops", "m_36298_"}, {"requiresCorrectToolForDrops", "m_60834_"}, {"getDestroyProgress", "m_5880_"}}) {
			MOJANG.put("$" + names[0] + "$", names[0].equals("clientDestroyBlock") ? "destroyBlock" : names[0]);
			SRG.put("$" + names[0] + "$", names[1]);
		}
	}

	/** The game's stand-ins and the platform's classes in the form. */
	private static Map<String, String> sources(Form form, String serverBody, String clientBody) {
		String willDestroy = form.srg ? "void" : "BlockState";
		Map<String, String> sources = new HashMap<>();
		sources.put("net/minecraft/core/BlockPos.java", "package net.minecraft.core; public record BlockPos(int x) { }");
		sources.put("net/minecraft/world/level/BlockGetter.java", "package net.minecraft.world.level; public interface BlockGetter { }");
		sources.put("net/minecraft/world/level/LevelAccessor.java", "package net.minecraft.world.level; public interface LevelAccessor extends BlockGetter { }");
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
		// Vanilla's harvest check; Forge's fires its event with vanilla's answer.
		String answer = "!state.$requiresCorrectToolForDrops$() || this.tool";
		sources.put("net/minecraft/world/entity/player/Player.java", "package net.minecraft.world.entity.player; import net.minecraft.world.level.block.state.BlockState;"
				+ " public class Player { public boolean tool; public boolean $hasCorrectToolForDrops$(BlockState state) { return "
				+ (form.forge ? "net.minecraftforge.event.ForgeEventFactory.doPlayerHarvestCheck(this, state, " + answer + ")" : answer) + "; } }");
		// A player of the platform's kind whose class answers the check itself.
		sources.put("com/example/OwnPlayer.java", "package com.example; import net.minecraft.world.level.block.state.BlockState; public class OwnPlayer extends"
				+ " net.minecraft.server.level.ServerPlayer { @Override public boolean $hasCorrectToolForDrops$(BlockState state) { Hook.LOG.add(\"own player\");"
				+ " return true; } }");
		if (form.forge) {
			sources.put("net/minecraftforge/event/ForgeEventFactory.java", "package net.minecraftforge.event; public class ForgeEventFactory {"
					+ " public static boolean doPlayerHarvestCheck(net.minecraft.world.entity.player.Player player, net.minecraft.world.level.block.state.BlockState state,"
					+ " boolean success) { com.example.Hook.LOG.add(\"harvest event \" + success); return success; } }");
		} else {
			sources.put("net/neoforged/bus/api/Event.java", "package net.neoforged.bus.api; public class Event { }");
			sources.put("net/neoforged/bus/api/IEventBus.java", "package net.neoforged.bus.api; public interface IEventBus { <T extends Event> T post(T event); }");
			sources.put("net/neoforged/neoforge/common/NeoForge.java", "package net.neoforged.neoforge.common; public class NeoForge {"
					+ " public static final net.neoforged.bus.api.IEventBus EVENT_BUS = new net.neoforged.bus.api.IEventBus() { public <T extends net.neoforged.bus.api.Event>"
					+ " T post(T event) { com.example.Hook.LOG.add(\"harvest event \" + ((net.neoforged.neoforge.event.entity.player.PlayerEvent.HarvestCheck) event)"
					+ ".canHarvest()); return event; } }; }");
			sources.put("net/neoforged/neoforge/event/entity/player/PlayerEvent.java", "package net.neoforged.neoforge.event.entity.player; public class PlayerEvent"
					+ " extends net.neoforged.bus.api.Event { public static class HarvestCheck extends PlayerEvent { final boolean success; public HarvestCheck("
					+ "net.minecraft.world.entity.player.Player player, net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.BlockGetter level,"
					+ " net.minecraft.core.BlockPos pos, boolean success) { this.success = success; } public boolean canHarvest() { return success; } } }");
			sources.put("net/neoforged/neoforge/event/EventHooks.java", "package net.neoforged.neoforge.event; import net.minecraft.world.entity.player.Player;"
					+ " import net.minecraft.world.level.block.state.BlockState; public class EventHooks { public static boolean doPlayerHarvestCheck(Player player,"
					+ " BlockState state, net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos) { boolean vanilla = player.$hasCorrectToolForDrops$(state);"
					+ " return net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.HarvestCheck(player, state,"
					+ " level, pos, vanilla)).canHarvest(); } }");
		}
		// The platform's form of getDestroyProgress, its harvest check made through the platform's hook.
		sources.put(BEHAVIOUR + ".java", "package net.minecraft.world.level.block.state; import net.minecraft.core.BlockPos;"
				+ " import net.minecraft.world.entity.player.Player; import net.minecraft.world.level.BlockGetter; public class BlockBehaviour {"
				+ " public float $getDestroyProgress$(BlockState state, Player player, BlockGetter level, BlockPos pos) { int i = "
				+ (form.forge ? "net.minecraftforge.common.ForgeHooks.isCorrectToolForDrops(state, player)" : "net.neoforged.neoforge.event.EventHooks.doPlayerHarvestCheck("
						+ "player, state, level, pos)") + " ? 30 : 100; return 1.0F / (float) i; } }");
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
				+ " public boolean $requiresCorrectToolForDrops$() { return !block.name.equals(\"loose\"); }"
				+ " public String toString() { return block.name; } }");
		String ext = form.ext().replace('.', '/') + "/";
		sources.put(ext + form.stateExt() + ".java", "package " + form.ext() + ";" + imports + " public interface " + form.stateExt() + " {"
				+ " default boolean onDestroyedByPlayer(Level level, BlockPos pos, Player player," + form.params() + ") {"
				+ " return ((BlockState) this).$getBlock$().onDestroyedByPlayer((BlockState) this, level, pos, player," + form.args() + "); }"
				+ " default boolean canHarvestBlock(BlockGetter level, BlockPos pos, Player player) { return ((BlockState) this).$getBlock$().canHarvestBlock("
				+ "(BlockState) this, level, pos, player); } }");
		String body = form.forge ? " ((Block) this).$playerWillDestroy$(level, pos, state, player); return level.$setBlock$(pos, fluid.$createLegacyBlock$(), level.isClientSide() ? 11 : 3);"
				: " if (level.isClientSide()) return level.$setBlock$(pos, fluid.$createLegacyBlock$(), 11); return level.$removeBlock$(pos, false);";
		sources.put(ext + form.blockExt() + ".java", "package " + form.ext() + ";" + imports + " public interface " + form.blockExt() + " {"
				+ " default boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player," + form.params() + ") {" + body + " }"
				+ " default boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) { return " + (form.forge
						? "net.minecraftforge.common.ForgeHooks.isCorrectToolForDrops(state, player)" : "net.neoforged.neoforge.event.EventHooks.doPlayerHarvestCheck("
						+ "player, state, level, pos)") + "; }"
				+ " default void drawn(com.example.ClientOnly client) { } }");
		sources.put("com/example/ClientOnly.java", "package com.example; public class ClientOnly { }");
		sources.put(form.forge ? "net/minecraftforge/common/ForgeHooks.java" : "net/neoforged/neoforge/common/CommonHooks.java",
				form.forge ? "package net.minecraftforge.common; import net.minecraft.world.entity.player.Player; import net.minecraft.world.level.block.state.BlockState;"
						+ " public class ForgeHooks { public static void onBlockBreakEvent() { } public static boolean isCorrectToolForDrops(BlockState state,"
						+ " Player player) { if (!state.$requiresCorrectToolForDrops$()) return net.minecraftforge.event.ForgeEventFactory.doPlayerHarvestCheck(player,"
						+ " state, true); return player.$hasCorrectToolForDrops$(state); } }"
						: "package net.neoforged.neoforge.common; public class CommonHooks { public static void fireBlockBreak() { } }");
		// A platform mod's blocks: one overriding the extension, one overriding it to call the default.
		String override = " @Override public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player," + form.params() + ") {";
		sources.put("com/example/Overriding.java", "package com.example;" + imports + " public class Overriding extends Block { public Overriding() { super(\"overriding\"); }"
				+ override + " level.log.add(\"override \" + willHarvest); return level.$setBlock$(pos, new BlockState(new Block(\"stump\")), 3); } }");
		sources.put("com/example/Hook.java", "package com.example; public class Hook { public static final java.util.List<String> LOG = new java.util.ArrayList<>();"
				+ " public static void hooked() { LOG.add(\"hooked\"); } public static boolean answer(boolean vanilla) { LOG.add(\"answered \" + vanilla);"
				+ " return true; } }");
		// A platform mod's block overriding the harvest extension, and a block needing no tool.
		sources.put("com/example/Harvesting.java", "package com.example;" + imports + " public class Harvesting extends Block { public Harvesting() { super(\"harvesting\"); }"
				+ " @Override public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) { Hook.LOG.add(\"harvest override\");"
				+ " return true; } }");
		// A state of a class of its own overriding the state's harvest extension.
		sources.put("com/example/HarvestingState.java", "package com.example;" + imports + " public class HarvestingState extends BlockState {"
				+ " public HarvestingState(Block block) { super(block); } @Override public boolean canHarvestBlock(BlockGetter level, BlockPos pos, Player player) {"
				+ " Hook.LOG.add(\"state harvest override\"); return true; } }");
		sources.put("com/example/Loose.java", "package com.example; public class Loose extends net.minecraft.world.level.block.Block { public Loose() { super(\"loose\"); } }");
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
		// A Fabric mod's interfaces bringing other defaults of the extensions' methods to the state and the block, as Porting
		// Lib's BlockStateExtensions does to Forge 47's BlockState (SEAM-7, bridged()).
		sources.put("com/example/ModStateExtension.java", "package com.example;" + imports + " public interface ModStateExtension {"
				+ " default boolean onDestroyedByPlayer(Level level, BlockPos pos, Player player," + form.params() + ") { level.log.add(\"mod default\"); return false; }"
				+ " default boolean canHarvestBlock(BlockGetter level, BlockPos pos, Player player) { Hook.LOG.add(\"mod harvest default\"); return true; } }");
		sources.put("com/example/ModBlockExtension.java", "package com.example;" + imports + " public interface ModBlockExtension {"
				+ " default boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player," + form.params() + ") {"
				+ " level.log.add(\"mod default\"); return false; }"
				+ " default boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) { Hook.LOG.add(\"mod harvest default\");"
				+ " return true; } }");
		sources.put(SERVER + ".java", "package net.minecraft.server.level;" + imports + " public class ServerPlayerGameMode {"
				+ " protected ServerLevel $level$; protected final ServerPlayer $player$; private final boolean creative;"
				+ " public ServerPlayerGameMode(ServerLevel level, ServerPlayer player, boolean creative) { this.$level$ = level; this.$player$ = player; this.creative = creative; }"
				+ serverBody + " }");
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
					+ " boolean flag1 = blockstate.canHarvestBlock(this.$level$, pos, this.$player$); boolean flag = removeBlock(pos, flag1);"
					+ " if (flag && flag1) this.$level$.log.add(\"playerDestroy\"); return true; }"
					+ " private boolean removeBlock(BlockPos pos, boolean canHarvest) { BlockState state = this.$level$.$getBlockState$(pos);"
					+ " boolean removed = state.onDestroyedByPlayer(this.$level$, pos, this.$player$, canHarvest, this.$level$.getFluidState(pos));"
					+ " if (removed) state.$getBlock$().$destroy$(this.$level$, pos, state); return removed; }";
		}
		return " public boolean $destroyBlock$(BlockPos pos) { BlockState blockstate1 = this.$level$.$getBlockState$(pos); " + form.hook()
				+ " Block block = blockstate1.$getBlock$(); BlockState blockstate = block.$playerWillDestroy$(this.$level$, pos, blockstate1, this.$player$);"
				+ " if (this.creative) { removeBlock(pos, blockstate, false" + tool + "); return true; }"
				+ " boolean flag1 = blockstate.canHarvestBlock(this.$level$, pos, this.$player$); boolean flag = removeBlock(pos, blockstate, flag1" + tool + ");"
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

	/** Forge 47's game classes, under SRG's names, as the fixtures have them. */
	public static Map<String, String> forge47Sources() {
		return sources(Form.FORGE_47, server(Form.FORGE_47), client(Form.FORGE_47, "level"));
	}

	private static Map<String, byte[]> platform(Path dir, Form form) throws Exception {
		return TestSources.compileWithLocals(dir, sources(form, server(form), client(form, "level")));
	}

	/** The classes with the game mode classes put back, as they load. */
	private static Map<String, byte[]> restored(Map<String, byte[]> platform) {
		Map<String, byte[]> restored = new HashMap<>(platform);
		for (String name : List.of(SERVER, CLIENT, BEHAVIOUR)) {
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
			assertEquals(List.of("net/minecraft/server/level/ServerLevel;" + names.get("$removeBlock$") + " SEAM-3", "net/minecraft/world/level/block/Block;"
					+ names.get("$destroy$") + " SEAM-3", "net/minecraft/server/level/ServerPlayer;" + names.get("$hasCorrectToolForDrops$") + " SEAM-4"),
					server.stream().map(r -> r.owner() + ";" + r.name() + " " + r.finding()).toList(), form.name());
			assertTrue(server.stream().allMatch(r -> r.in().equals(gameMode)), server.toString());
			List<String> client = VanillaSeams.restoredIn(node(restored.get(CLIENT))).stream().map(r -> r.name()).toList();
			assertEquals(form.forge ? List.of(names.get("$playerWillDestroy$"), names.get("$createLegacyBlock$"), names.get("$setBlock$"))
					: List.of(names.get("$createLegacyBlock$"), names.get("$setBlock$")), client, form.name());
		}
	}

	/**
	 * SEAM-7: where a Fabric mod's interface on the block state or the block brings another default of the extension's
	 * method, Fabricated declares a bridge there calling the platform's default ({@link DefaultBridge}), as for Porting
	 * Lib's {@code BlockStateExtensions} on Forge 47's {@code BlockState}. The state and the block still keep the
	 * platform's default: vanilla's calls are made, the hooks of them run, and the break and the harvest check are as
	 * before. A mod's own declaration of the method still overrides it.
	 */
	@Test
	void aDefaultBridgeKeepsThePlatformsDefault(@TempDir Path dir) throws Exception {
		for (Form form : Form.values()) {
			Map<String, byte[]> platform = bridged(platform(dir.resolve(form.name()), form), form);
			Map<String, byte[]> restored = restored(platform);
			String block = "net.minecraft.world.level.block.Block";
			for (boolean creative : List.of(false, true)) {
				List<String> expected = server(platform, block, creative, false);
				// Forge's default sets the fluid's block, where vanilla's removeBlock does.
				if (form.forge) expected.add(expected.indexOf("setBlock air 3"), "removeBlock");
				assertEquals(expected, server(restored, block, creative, false), form + " creative " + creative);
			}
			assertTrue(server(restored, block, false, true).contains("hooked"), form.name());
			assertTrue(client(restored, block, true).contains("hooked"), form.name());
			List<String> harvest = harvest(restored, block, "net.minecraft.server.level.ServerPlayer", false, "net/minecraft/server/level/ServerPlayer");
			assertTrue(harvest.stream().anyMatch(l -> l.startsWith("answered")), form + " " + harvest);
			assertTrue(harvest.stream().noneMatch(l -> l.startsWith("mod ")), form + " " + harvest);
			for (String overriding : List.of("com.example.Overriding", "com.example.OverridingState")) {
				assertTrue(!server(restored, overriding, false, true).contains("hooked"), form + " " + overriding);
			}
		}
	}

	/**
	 * The classes with a Fabric mod's interfaces added to the block state and the block, each bringing another default of
	 * both extensions' methods, and the bridges Fabricated declares for them, calling the platform's defaults.
	 */
	private static Map<String, byte[]> bridged(Map<String, byte[]> classes, Form form) {
		Map<String, byte[]> bridged = new HashMap<>(classes);
		String ext = form.ext().replace('.', '/') + "/";
		for (String[] target : new String[][] {{"net/minecraft/world/level/block/state/BlockState", ext + form.stateExt(), "com/example/ModStateExtension"},
				{"net/minecraft/world/level/block/Block", ext + form.blockExt(), "com/example/ModBlockExtension"}}) {
			ClassNode node = node(classes.get(target[0]));
			node.interfaces.add(target[2]);
			for (MethodNode method : node(classes.get(target[2])).methods) {
				node.methods.add(new DefaultBridge(target[0], target[1], method.name, method.desc).method(true));
			}
			bridged.put(target[0], write(node));
		}
		return bridged;
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
		Map<String, Boolean> breaks = Map.of("net.minecraft.world.level.block.state.BlockState", false, block, false, "com.example.Overriding", true,
				"com.example.Unreadable", true);
		assertEquals(breaks, known(gameMode, "fabricated$overriding", breaks.keySet()));
		// Each extension's answers are kept apart: the overriding block keeps canHarvestBlock's default.
		Map<String, Boolean> harvests = Map.of("net.minecraft.world.level.block.state.BlockState", false, block, false, "com.example.Overriding", false,
				"com.example.Unreadable", true);
		assertEquals(harvests, known(gameMode, "fabricated$overridingHarvest", harvests.keySet()));
		assertEquals(List.of("hooked", "hooked"), ((List<?>) loader.loadClass("com.example.Hook").getField("LOG").get(null)).stream().filter("hooked"::equals).toList());
	}

	/**
	 * The answers a rewritten class keeps in its field (a ClassValue, each class's slot holding its answer once asked), for
	 * the classes named; one never asked of has none.
	 */
	private static Map<String, Boolean> known(Class<?> owner, String name, Set<String> types) throws Exception {
		java.lang.reflect.Field field = owner.getDeclaredField(name);
		field.setAccessible(true);
		ClassValue<?> store = (ClassValue<?>) field.get(null);
		Map<String, Boolean> known = new HashMap<>();
		for (String type : types) {
			Object answer = ((Object[]) store.get(Class.forName(type, false, owner.getClassLoader())))[0];
			if (answer != null) known.put(type, (Boolean) answer);
		}
		assertNull(((Object[]) store.get(Object.class))[0], "a class never asked of has no answer");
		return known;
	}

	/**
	 * Translation reads classes in parallel: every class put back at once is the same, the helpers copied whole; for each
	 * platform's server game mode (both families' calls, and on Forge the player's check) and its getDestroyProgress.
	 */
	@Test
	void classesPutBackInParallelAreTheSame(@TempDir Path dir) throws Exception {
		Map<String, byte[]> neoForge = platform(dir.resolve("neoforge"), Form.NEOFORGE), forge = platform(dir.resolve("forge"), Form.FORGE);
		List<Callable<byte[]>> restores = new ArrayList<>();
		for (Map<String, byte[]> platform : List.of(neoForge, forge)) {
			for (String name : List.of(SERVER, BEHAVIOUR)) {
				restores.add(() -> {
					ClassNode node = node(platform.get(name));
					assertTrue(!VanillaSeams.restore(node).isEmpty());
					return write(node);
				});
			}
		}
		List<byte[]> references = new ArrayList<>();
		for (Callable<byte[]> restore : restores) references.add(restore.call());
		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			for (int round = 0; round < 100; round++) {
				CyclicBarrier start = new CyclicBarrier(8);
				List<Future<byte[]>> copies = new ArrayList<>();
				for (int i = 0; i < 8; i++) {
					Callable<byte[]> restore = restores.get(i % restores.size());
					copies.add(pool.submit(() -> {
						start.await();
						return restore.call();
					}));
				}
				for (int i = 0; i < copies.size(); i++) {
					assertArrayEquals(references.get(i % restores.size()), copies.get(i).get(60, TimeUnit.SECONDS), "round " + round + ", copy " + i);
				}
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
		String harvest = "Lnet/minecraft/world/level/BlockGetter;" + pos + "Lnet/minecraft/world/entity/player/Player;)Z";
		assertEquals(Set.of(SERVER + ";removeBlock(" + pos + state + "ZLnet/minecraft/world/item/ItemStack;)Z",
				"net/minecraft/world/level/block/state/BlockState;canHarvestBlock(" + harvest), server.movedCalls());
		assertEquals(Set.of("destroyBlock(" + pos + ")Z"), server.holders());
		assertEquals(Set.of("removeBlock(" + pos + state + "ZLnet/minecraft/world/item/ItemStack;)Z"), server.bypassed());
		assertTrue(server.added().containsAll(Set.of("net/minecraft/server/level/ServerLevel;removeBlock(" + pos + "Z)Z",
				SERVER + ";level:Lnet/minecraft/server/level/ServerLevel;", "net/minecraft/server/level/ServerPlayer;hasCorrectToolForDrops(" + state + ")Z",
				"net/neoforged/neoforge/event/entity/player/PlayerEvent$HarvestCheck;<init>(Lnet/minecraft/world/entity/player/Player;" + state
						+ "Lnet/minecraft/world/level/BlockGetter;" + pos + "Z)V")), server.added().toString());
		assertEquals(Map.of("net/neoforged/neoforge/common/extensions/IBlockExtension", Set.of("canHarvestBlock", "onDestroyedByPlayer"),
				"net/neoforged/neoforge/common/extensions/IBlockStateExtension", Set.of("canHarvestBlock", "onDestroyedByPlayer"),
				"net/neoforged/neoforge/event/EventHooks", Set.of("doPlayerHarvestCheck")), server.others());
		// getDestroyProgress: NeoForge's hook, put back in its place, no longer called.
		VanillaSeams.Rewrite progress = VanillaSeams.rewriteOf(node(platform.get(BEHAVIOUR)));
		assertEquals(Set.of("net/neoforged/neoforge/event/EventHooks;doPlayerHarvestCheck(Lnet/minecraft/world/entity/player/Player;" + state
				+ "Lnet/minecraft/world/level/BlockGetter;" + pos + ")Z"), progress.movedCalls());
		assertEquals(Set.of("getDestroyProgress(" + state + "Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/BlockGetter;" + pos + ")F"),
				progress.holders());
		assertEquals(Map.of("net/neoforged/neoforge/event/EventHooks", Set.of("doPlayerHarvestCheck")), progress.others());
		assertTrue(!progress.constants());
		// Forge's server: Level's removeBlock, which vanilla's call put back runs, where Forge's default sets the block itself;
		// Forge's harvest hook, no longer called, and the player's harvest check, called where Forge's only fires its event.
		Map<String, byte[]> forgeClasses = platform(dir.resolve("forge"), Form.FORGE);
		VanillaSeams.Rewrite forge = VanillaSeams.rewriteOf(node(forgeClasses.get(SERVER)));
		assertEquals(Set.of("removeBlock", "m_7471_"), forge.others().get("net/minecraft/world/level/Level"));
		assertEquals(Set.of("removeBlock", "m_7471_"), forge.others().get("net/minecraft/server/level/ServerLevel"));
		assertEquals(Set.of("isCorrectToolForDrops"), forge.others().get("net/minecraftforge/common/ForgeHooks"));
		assertEquals(Set.of("hasCorrectToolForDrops", "m_36298_"), forge.others().get("net/minecraft/world/entity/player/Player"));
		assertEquals(Set.of("canHarvestBlock", "onDestroyedByPlayer"), forge.others().get("net/minecraftforge/common/extensions/IForgeBlock"));
		VanillaSeams.Rewrite forgeProgress = VanillaSeams.rewriteOf(node(forgeClasses.get(BEHAVIOUR)));
		assertEquals(Map.of("net/minecraftforge/common/ForgeHooks", Set.of("isCorrectToolForDrops"), "net/minecraft/world/entity/player/Player",
				Set.of("hasCorrectToolForDrops", "m_36298_")), forgeProgress.others());
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
		assertEquals(List.of("FORGE_CLIENT"), VanillaCalls.applicable(node).stream().map(r -> r.code().name()).toList());

		// Pre-flight's view, from a game whose extension it can't find, keeps the class as it is.
		Map<String, byte[]> platform = platform(dir.resolve("neoforge"), Form.NEOFORGE);
		ClassBytesSource lacking = name -> name.endsWith("IBlockExtension") ? null : platform.get(name);
		assertArrayEquals(platform.get(SERVER), VanillaSeams.over(lacking).read(SERVER));
		assertTrue(!VanillaSeams.restoredIn(VanillaSeams.over(platform::get).readNode(SERVER, 0)).isEmpty());
		// NeoForge's event bus is a library of its own, not in the game's files: the harvest event's post counts as there where
		// EventHooks.doPlayerHarvestCheck, whose code the helper copies, makes it the same way; without that, nothing is put back.
		ClassBytesSource noBus = name -> name.startsWith("net/neoforged/bus/") ? null : platform.get(name);
		for (String name : List.of(SERVER, BEHAVIOUR)) {
			assertTrue(!VanillaSeams.restoredIn(VanillaSeams.over(noBus).readNode(name, 0)).isEmpty(), name);
		}
		ClassBytesSource noHooks = name -> name.startsWith("net/neoforged/bus/") || name.equals("net/neoforged/neoforge/event/EventHooks") ? null : platform.get(name);
		assertArrayEquals(platform.get(BEHAVIOUR), VanillaSeams.over(noHooks).read(BEHAVIOUR));
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

	/**
	 * SEAM-4: vanilla's harvest check, put back in the server's destroyBlock and in getDestroyProgress, answers as the
	 * platform's code did for every block and player, the platform's harvest event fired as it fired it; a hook of it runs
	 * where the platform's default would make vanilla's call, and only there: for the blocks keeping canHarvestBlock's
	 * default, and on Forge for the players whose class keeps Player.hasCorrectToolForDrops. A hook answering otherwise than
	 * vanilla's call is followed by NeoForge's event, as NeoForge's default posts it after the call; Forge's fires inside it.
	 */
	@Test
	void vanillasHarvestCheckIsPutBackWhereThePlatformsCodeMakesIt(@TempDir Path dir) throws Exception {
		for (Form form : Form.values()) {
			Map<String, byte[]> platform = platform(dir.resolve(form.name()), form);
			Map<String, byte[]> restored = restored(platform);
			Map<String, String> names = form.srg ? SRG : MOJANG;
			for (String player : List.of("net.minecraft.server.level.ServerPlayer", "com.example.OwnPlayer")) {
				for (String block : List.of("net.minecraft.world.level.block.Block", "com.example.Loose", "com.example.Harvesting", "com.example.HarvestingState",
						"com.example.Overriding")) {
					for (boolean tool : List.of(false, true)) {
						String what = form + " " + player + " " + block + " tool " + tool;
						List<String> before = harvest(platform, block, player, tool, null), after = harvest(restored, block, player, tool, null);
						// Forge's default sets the fluid's block, where vanilla's removeBlock does (SEAM-3).
						if (form.forge) after.remove("removeBlock");
						assertEquals(before, after, what);
						List<String> progressBefore = progress(platform, block, player, tool, null), progressAfter = progress(restored, block, player, tool, null);
						assertEquals(progressBefore, progressAfter, what);
						// The hook of the call put back, answering true.
						boolean keeps = !block.endsWith("Harvesting") && !block.endsWith("HarvestingState") && (!form.forge || !player.endsWith("OwnPlayer"));
						List<String> hooked = harvest(restored, block, player, tool, "net/minecraft/server/level/ServerPlayer");
						assertEquals(keeps, hooked.stream().anyMatch(l -> l.startsWith("answered")), what + " " + hooked);
						if (keeps) assertTrue(hooked.contains("playerDestroy"), what + " " + hooked);
						boolean progressKeeps = !form.forge || !player.endsWith("OwnPlayer");
						List<String> hookedProgress = progress(restored, block, player, tool, "net/minecraft/world/entity/player/Player");
						assertEquals(progressKeeps, hookedProgress.stream().anyMatch(l -> l.startsWith("answered")), what + " " + hookedProgress);
						if (progressKeeps) assertEquals("progress 0.033333335", hookedProgress.get(hookedProgress.size() - 1), what + " " + hookedProgress);
					}
				}
			}
			// The event follows the hook's answer on NeoForge, and vanilla's inside the call on Forge.
			List<String> answered = harvest(restored, "net.minecraft.world.level.block.Block", "net.minecraft.server.level.ServerPlayer", false,
					"net/minecraft/server/level/ServerPlayer");
			assertTrue(answered.contains(form.forge ? "harvest event false" : "harvest event true"), form + " " + answered);
			// Pre-flight reads what was put back in getDestroyProgress.
			List<VanillaSeams.Restored> progress = VanillaSeams.restoredIn(node(restored.get(BEHAVIOUR)));
			assertEquals(List.of("net/minecraft/world/entity/player/Player;" + names.get("$hasCorrectToolForDrops$") + " SEAM-4"),
					progress.stream().map(r -> r.owner() + ";" + r.name() + " " + r.finding()).toList(), form.name());
			assertEquals(form.forge ? "ForgeHooks.isCorrectToolForDrops" : "EventHooks.doPlayerHarvestCheck", progress.get(0).through());
		}
	}

	/** The server's survival break of a block of the class at 1 by a player of the class; with {@code hook}, vanilla's harvest check put back answers true. */
	private static List<String> harvest(Map<String, byte[]> classes, String block, String player, boolean tool, String hook) throws Exception {
		ClassLoader loader = loader(hook != null ? answering(classes, SERVER, hook) : classes);
		Object level = loader.loadClass("net.minecraft.server.level.ServerLevel").getConstructor().newInstance();
		place(loader, level, block);
		Object who = loader.loadClass(player).getConstructor().newInstance();
		loader.loadClass("net.minecraft.world.entity.player.Player").getField("tool").set(who, tool);
		Class<?> gameMode = loader.loadClass(SERVER.replace('/', '.'));
		destroy(loader, gameMode, gameMode.getConstructors()[0].newInstance(level, who, false));
		return log(loader, level);
	}

	/** getDestroyProgress of a block of the class for a player of the class, its answer last; with {@code hook}, as {@link #harvest}. */
	private static List<String> progress(Map<String, byte[]> classes, String block, String player, boolean tool, String hook) throws Exception {
		ClassLoader loader = loader(hook != null ? answering(classes, BEHAVIOUR, hook) : classes);
		Object level = loader.loadClass("net.minecraft.server.level.ServerLevel").getConstructor().newInstance();
		place(loader, level, block);
		@SuppressWarnings("unchecked")
		Object state = ((Map<Integer, Object>) level.getClass().getField("blocks").get(level)).get(1);
		Object who = loader.loadClass(player).getConstructor().newInstance();
		loader.loadClass("net.minecraft.world.entity.player.Player").getField("tool").set(who, tool);
		Class<?> behaviour = loader.loadClass(BEHAVIOUR.replace('/', '.'));
		Object pos = loader.loadClass("net.minecraft.core.BlockPos").getConstructor(int.class).newInstance(1);
		Object answer = null;
		for (java.lang.reflect.Method m : behaviour.getMethods()) {
			if (m.getReturnType() == float.class && m.getParameterCount() == 4) answer = m.invoke(behaviour.getConstructor().newInstance(), state, who, level, pos);
		}
		List<String> log = log(loader, level);
		log.add("progress " + answer);
		return log;
	}

	/**
	 * The classes with vanilla's harvest check put back in the class made through a hook that answers true, logging
	 * vanilla's answer, as a Fabric mod's wrapper of it would: the calls of {@code owner}'s harvest check outside the helpers.
	 */
	private static Map<String, byte[]> answering(Map<String, byte[]> classes, String name, String owner) {
		ClassNode node = node(classes.get(name));
		for (MethodNode method : node.methods) {
			if (method.name.startsWith("fabricated$")) continue;
			for (AbstractInsnNode insn : method.instructions) {
				if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.desc.equals("(Lnet/minecraft/world/level/block/state/BlockState;)Z")) {
					method.instructions.insert(call, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/example/Hook", "answer", "(Z)Z", false));
				}
			}
		}
		Map<String, byte[]> hooked = new HashMap<>(classes);
		hooked.put(name, write(node));
		return hooked;
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
