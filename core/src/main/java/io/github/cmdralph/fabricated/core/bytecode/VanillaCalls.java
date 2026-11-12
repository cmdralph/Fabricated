package io.github.cmdralph.fabricated.core.bytecode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * The second shape of {@link VanillaSeams}' table: vanilla's calls in a game method that a platform makes through its
 * extensions or hooks instead, put back as their own steps there, where the platform's code then does what vanilla's
 * does. Where the platform calls it, the rewrite asks whether the objects concerned keep the platform's default
 * ({@code fabricated$keepsDefault}: neither the state's class nor the block's overrides either interface's method,
 * {@link KeepsDefault}; on Forge, for the harvest checks, also that the player's class keeps
 * {@code Player.hasCorrectToolForDrops}), and then makes vanilla's calls, which hooks find; otherwise the platform's
 * call, as before. Each operand of the platform's call is evaluated as before, once, in the branch the call is in;
 * vanilla's branch loads again the plain ones it needs. What vanilla doesn't call there is called through private
 * helpers, out of hooks' sight, and a value kept goes in a local of type {@code Object}, which a hook capturing a local
 * of its type doesn't count. The method gets the frames its branches need.
 *
 * <p>Block breaking (SEAM-3): vanilla's {@code destroyBlock} (the server's and the client's) removes the block with
 * {@code level.removeBlock(pos, false)} (the server's, then {@code block.destroy}) or
 * {@code level.setBlock(pos, fluid.createLegacyBlock(), 11)} (the client's); NeoForge 21.1 to 26.3 and Forge 47 to 66 call
 * the state's {@code onDestroyedByPlayer} there (the server through a private {@code removeBlock} of theirs), whose
 * default makes those calls (NeoForge's) or {@code playerWillDestroy} and the {@code setBlock} (Forge's). What vanilla
 * doesn't call there (the block's {@code getBlock}, Forge's server reading the state and its {@code playerWillDestroy},
 * which vanilla makes before the tool is damaged) goes through the helpers.
 *
 * <p>Harvest checks (SEAM-4): vanilla's server {@code destroyBlock} asks {@code player.hasCorrectToolForDrops(state)},
 * and {@code BlockBehaviour.getDestroyProgress} asks it of its player; both platforms ask the state's
 * {@code canHarvestBlock(level, pos, player)} in the first and a hook of theirs in the second. NeoForge's defaults make
 * vanilla's call, then post its {@code PlayerEvent.HarvestCheck} with the answer ({@code EventHooks.doPlayerHarvestCheck}):
 * vanilla's call goes back with the event posted after it by a helper, as that method posts it, in the first for the
 * blocks keeping {@code canHarvestBlock}'s default, in the second always. Forge's default ({@code
 * ForgeHooks.isCorrectToolForDrops}) asks {@code player.hasCorrectToolForDrops(state)}, which Forge makes fire its event,
 * where the state needs the correct tool, and fires the event with {@code true} where it doesn't, which Forge's
 * {@code Player.hasCorrectToolForDrops} also does there: vanilla's call alone goes back, for the players whose class keeps
 * that method (and in {@code destroyBlock} the blocks keeping the default).
 */
final class VanillaCalls {
	private static final String STATE = "net/minecraft/world/level/block/state/BlockState";
	private static final String BLOCK = "net/minecraft/world/level/block/Block";
	private static final String LEVEL = "net/minecraft/world/level/Level";
	private static final String SERVER_LEVEL = "net/minecraft/server/level/ServerLevel";
	private static final String POS = "Lnet/minecraft/core/BlockPos;";
	private static final String PLAYER_CLASS = "net/minecraft/world/entity/player/Player";
	private static final String SERVER_PLAYER = "net/minecraft/server/level/ServerPlayer";
	private static final String PLAYER = "L" + PLAYER_CLASS + ";";
	private static final String GETTER = "Lnet/minecraft/world/level/BlockGetter;";
	private static final String FLUID = "Lnet/minecraft/world/level/material/FluidState;";
	private static final String ITEM = "Lnet/minecraft/world/item/ItemStack;";
	private static final String GAME_MODE = "net/minecraft/server/level/ServerPlayerGameMode";
	private static final String CLIENT_GAME_MODE = "net/minecraft/client/multiplayer/MultiPlayerGameMode";
	private static final String BEHAVIOUR = "net/minecraft/world/level/block/state/BlockBehaviour";
	private static final String DESTROY_BLOCK = "(" + POS + ")Z";
	private static final String DESTROY_PROGRESS = "(L" + STATE + ";" + PLAYER + GETTER + POS + ")F";
	private static final String NEOFORGE = "net/neoforged/";
	private static final String FORGE = "net/minecraftforge/";

	/**
	 * A member of the game the rewrite names: by Mojang's names, and SRG's where the game runs with them (Forge 47, which
	 * is 1.20.1: {@code srgDesc} where 1.20.1's descriptor differs, else null).
	 */
	record Member(String owner, String name, String srgName, String desc, String srgDesc) {
		Member(String owner, String name, String srgName, String desc) {
			this(owner, name, srgName, desc, null);
		}

		String name(boolean srg) {
			return srg && srgName != null ? srgName : name;
		}

		String desc(boolean srg) {
			return srg && srgDesc != null ? srgDesc : desc;
		}

		/** As {@link VanillaSeams.Rewrite} and the marker name it: {@code owner;name(desc)}, or {@code owner;name:desc} for a field. */
		String key(boolean srg) {
			String desc = desc(srg);
			return owner + ";" + name(srg) + (desc.startsWith("(") ? "" : ":") + desc;
		}
	}

	/**
	 * The platform's extension a put-back stands in for: the block state's method and the block's, each an interface's
	 * default; the helper asking whether a state's block keeps them, and the field it keeps its answers in.
	 */
	record Extension(String stateInterface, String blockInterface, String name, String desc, String entry, String field) {
		String blockDesc() {
			return "(L" + STATE + ";" + desc.substring(1);
		}
	}

	/** Which of vanilla's code a put-back makes in the platform's call's place, and why it can. */
	enum Code {
		/** NeoForge's server: its {@code removeBlock(pos, state, ...)}'s place gets {@code level.removeBlock(pos, false)} and, where it removed the block, {@code block.destroy(level, pos, state)}. */
		SERVER,
		/** Forge's server: as {@link #SERVER}, at its {@code removeBlock(pos, canHarvest)}, the state read at the position first and the default's {@code playerWillDestroy} made out of hooks' sight. */
		FORGE_SERVER,
		/** NeoForge's client: the extension call's place gets {@code level.setBlock(pos, fluid.createLegacyBlock(), 11)}. */
		CLIENT,
		/** Forge's client: as {@link #CLIENT}, after {@code block.playerWillDestroy(level, pos, state, player)}, which Forge's default makes there. */
		FORGE_CLIENT,
		/** NeoForge's server: the state's {@code canHarvestBlock(level, pos, player)}'s place gets {@code player.hasCorrectToolForDrops(state)}, then NeoForge's harvest check event with its answer. */
		HARVEST,
		/** Forge's server: the same place gets {@code player.hasCorrectToolForDrops(state)}, which fires Forge's event itself. */
		FORGE_HARVEST,
		/** NeoForge's {@code getDestroyProgress}: {@code EventHooks.doPlayerHarvestCheck(player, state, level, pos)}'s place gets its own code, vanilla's call then the event, always. */
		PROGRESS,
		/** Forge's {@code getDestroyProgress}: {@code ForgeHooks.isCorrectToolForDrops(state, player)}'s place gets {@code player.hasCorrectToolForDrops(state)}. */
		FORGE_PROGRESS;

		boolean forge() {
			return this == FORGE_SERVER || this == FORGE_CLIENT || this == FORGE_HARVEST || this == FORGE_PROGRESS;
		}

		/** The finding a put-back of this kind answers: block breaking, SEAM-3; harvest checks, SEAM-4. */
		String finding() {
			return ordinal() < HARVEST.ordinal() ? "SEAM-3" : "SEAM-4";
		}

		boolean harvest() {
			return ordinal() >= HARVEST.ordinal();
		}

		/** Whether the platform's call is a static hook's ({@code getDestroyProgress}'s). */
		boolean hookCall() {
			return this == PROGRESS || this == FORGE_PROGRESS;
		}

		/** The operand that is the player whose class must keep {@code Player.hasCorrectToolForDrops} (Forge's harvest checks), else -1. */
		int keepingPlayer() {
			return this == FORGE_HARVEST ? 3 : this == FORGE_PROGRESS ? 1 : -1;
		}
	}

	/**
	 * A row: the game method, the platform's call there whose place gets vanilla's calls, the extension whose default those
	 * calls stand for (null where no block's class is asked), and which calls.
	 */
	record PutBack(String owner, String method, String srgMethod, String desc, Member platform, Extension extension, Code code) {
		String method(boolean srg) {
			return srg ? srgMethod : method;
		}

		/** The package of the platform's own classes, which only its game's classes reference. */
		String platformPackage() {
			return code.forge() ? FORGE : NEOFORGE;
		}

		String loader() {
			return code.forge() ? "Forge" : "NeoForge";
		}
	}

	private static final String NEOFORGE_EXT = "net/neoforged/neoforge/common/extensions/";
	private static final String FORGE_EXT = "net/minecraftforge/common/extensions/";
	private static final String DESTROYED = "(L" + LEVEL + ";" + POS + PLAYER;
	private static final String KEEPS_DEFAULT = "fabricated$keepsDefault";
	private static final String KNOWN = "fabricated$overriding";
	private static final Extension NEOFORGE_21_1 = new Extension(NEOFORGE_EXT + "IBlockStateExtension", NEOFORGE_EXT + "IBlockExtension",
			"onDestroyedByPlayer", DESTROYED + "Z" + FLUID + ")Z", KEEPS_DEFAULT, KNOWN);
	private static final Extension NEOFORGE_BREAK = new Extension(NEOFORGE_EXT + "IBlockStateExtension", NEOFORGE_EXT + "IBlockExtension",
			"onDestroyedByPlayer", DESTROYED + ITEM + "Z" + FLUID + ")Z", KEEPS_DEFAULT, KNOWN);
	private static final Extension FORGE_BREAK = new Extension(FORGE_EXT + "IForgeBlockState", FORGE_EXT + "IForgeBlock", "onDestroyedByPlayer",
			DESTROYED + "Z" + FLUID + ")Z", KEEPS_DEFAULT, KNOWN);
	private static final String HARVEST_DESC = "(" + GETTER + POS + PLAYER + ")Z";
	private static final Extension NEOFORGE_HARVEST = new Extension(NEOFORGE_EXT + "IBlockStateExtension", NEOFORGE_EXT + "IBlockExtension",
			"canHarvestBlock", HARVEST_DESC, "fabricated$keepsHarvestDefault", "fabricated$overridingHarvest");
	private static final Extension FORGE_HARVEST = new Extension(FORGE_EXT + "IForgeBlockState", FORGE_EXT + "IForgeBlock", "canHarvestBlock", HARVEST_DESC,
			"fabricated$keepsHarvestDefault", "fabricated$overridingHarvest");
	/** The state's {@code canHarvestBlock}, which both platforms' {@code destroyBlock} asks. */
	private static final Member CAN_HARVEST = new Member(STATE, "canHarvestBlock", null, HARVEST_DESC);
	private static final String EVENT_HOOKS = "net/neoforged/neoforge/event/EventHooks";
	private static final String FORGE_HOOKS = "net/minecraftforge/common/ForgeHooks";
	private static final Member HARVEST_HOOK = new Member(EVENT_HOOKS, "doPlayerHarvestCheck", null, "(" + PLAYER + "L" + STATE + ";" + GETTER + POS + ")Z");
	private static final Member FORGE_HARVEST_HOOK = new Member(FORGE_HOOKS, "isCorrectToolForDrops", null, "(L" + STATE + ";" + PLAYER + ")Z");

	/** The table: the platforms' forms of the server's and the client's {@code destroyBlock} and of {@code getDestroyProgress}, read in their classes. */
	static final List<PutBack> PUT_BACKS = List.of(
			new PutBack(GAME_MODE, "destroyBlock", "m_9280_", DESTROY_BLOCK, new Member(GAME_MODE, "removeBlock", null, "(" + POS + "L" + STATE + ";Z)Z"),
					NEOFORGE_21_1, Code.SERVER),
			new PutBack(GAME_MODE, "destroyBlock", "m_9280_", DESTROY_BLOCK, new Member(GAME_MODE, "removeBlock", null, "(" + POS + "L" + STATE + ";Z" + ITEM + ")Z"),
					NEOFORGE_BREAK, Code.SERVER),
			new PutBack(GAME_MODE, "destroyBlock", "m_9280_", DESTROY_BLOCK, new Member(GAME_MODE, "removeBlock", null, "(" + POS + "Z)Z"), FORGE_BREAK,
					Code.FORGE_SERVER),
			new PutBack(CLIENT_GAME_MODE, "destroyBlock", "m_105267_", DESTROY_BLOCK, new Member(STATE, NEOFORGE_21_1.name(), null, NEOFORGE_21_1.desc()),
					NEOFORGE_21_1, Code.CLIENT),
			new PutBack(CLIENT_GAME_MODE, "destroyBlock", "m_105267_", DESTROY_BLOCK, new Member(STATE, NEOFORGE_BREAK.name(), null, NEOFORGE_BREAK.desc()),
					NEOFORGE_BREAK, Code.CLIENT),
			new PutBack(CLIENT_GAME_MODE, "destroyBlock", "m_105267_", DESTROY_BLOCK, new Member(STATE, FORGE_BREAK.name(), null, FORGE_BREAK.desc()), FORGE_BREAK,
					Code.FORGE_CLIENT),
			new PutBack(GAME_MODE, "destroyBlock", "m_9280_", DESTROY_BLOCK, CAN_HARVEST, NEOFORGE_HARVEST, Code.HARVEST),
			new PutBack(GAME_MODE, "destroyBlock", "m_9280_", DESTROY_BLOCK, CAN_HARVEST, FORGE_HARVEST, Code.FORGE_HARVEST),
			new PutBack(BEHAVIOUR, "getDestroyProgress", "m_5880_", DESTROY_PROGRESS, HARVEST_HOOK, null, Code.PROGRESS),
			new PutBack(BEHAVIOUR, "getDestroyProgress", "m_5880_", DESTROY_PROGRESS, FORGE_HARVEST_HOOK, null, Code.FORGE_PROGRESS));

	static final Set<String> CLASSES = PUT_BACKS.stream().map(PutBack::owner).collect(Collectors.toUnmodifiableSet());

	// Vanilla's calls and the fields they read, and what the helpers call.
	private static final Member REMOVE_BLOCK = new Member(SERVER_LEVEL, "removeBlock", "m_7471_", "(" + POS + "Z)Z");
	private static final Member DESTROY = new Member(BLOCK, "destroy", "m_6786_", "(Lnet/minecraft/world/level/LevelAccessor;" + POS + "L" + STATE + ";)V");
	private static final Member SET_BLOCK = new Member(LEVEL, "setBlock", "m_7731_", "(" + POS + "L" + STATE + ";I)Z");
	private static final Member LEGACY_BLOCK = new Member("net/minecraft/world/level/material/FluidState", "createLegacyBlock", "m_76188_", "()L" + STATE + ";");
	private static final Member WILL_DESTROY = new Member(BLOCK, "playerWillDestroy", "m_5707_", "(L" + LEVEL + ";" + POS + "L" + STATE + ";" + PLAYER + ")L"
			+ STATE + ";", "(L" + LEVEL + ";" + POS + "L" + STATE + ";" + PLAYER + ")V");
	private static final Member GET_BLOCK = new Member(STATE, "getBlock", "m_60734_", "()L" + BLOCK + ";");
	private static final Member GET_BLOCK_STATE = new Member(SERVER_LEVEL, "getBlockState", "m_8055_", "(" + POS + ")L" + STATE + ";");
	private static final Member LEVEL_FIELD = new Member(GAME_MODE, "level", "f_9244_", "L" + SERVER_LEVEL + ";");
	private static final Member PLAYER_FIELD = new Member(GAME_MODE, "player", "f_9245_", "L" + SERVER_PLAYER + ";");
	/** Vanilla's harvest check, as the server's {@code destroyBlock} makes it (of its {@code ServerPlayer}) and {@code getDestroyProgress} (of its {@code Player}). */
	private static final Member SERVER_HAS_CORRECT_TOOL = new Member(SERVER_PLAYER, "hasCorrectToolForDrops", "m_36298_", "(L" + STATE + ";)Z");
	private static final Member HAS_CORRECT_TOOL = new Member(PLAYER_CLASS, "hasCorrectToolForDrops", "m_36298_", "(L" + STATE + ";)Z");
	/** The helper posting NeoForge's harvest check event with vanilla's answer, as {@code EventHooks.doPlayerHarvestCheck} does. */
	private static final String HARVEST_EVENT = "fabricated$harvestCheck";
	private static final String HARVEST_EVENT_DESC = "(Z" + PLAYER + "L" + STATE + ";" + GETTER + POS + ")Z";
	private static final String HARVEST_CHECK = "net/neoforged/neoforge/event/entity/player/PlayerEvent$HarvestCheck";
	private static final String EVENT_BUS = "net/neoforged/bus/api/IEventBus";
	private static final String EVENT = "net/neoforged/bus/api/Event";
	/** The helper asking whether the player's class keeps {@code Player.hasCorrectToolForDrops} (Forge's harvest checks), and its answers' field. */
	private static final String KEEPS_PLAYER = "fabricated$keepsPlayerHarvest";
	private static final String KNOWN_PLAYER = "fabricated$overridingPlayer";

	private VanillaCalls() {
	}

	/** The rows that apply to the class as it is: its method makes the platform's call, and the class is that platform's. */
	static List<PutBack> applicable(ClassNode node) {
		List<PutBack> rows = new ArrayList<>();
		if (!CLASSES.contains(node.name)) return rows;
		boolean srg = VanillaSeams.srg(node);
		for (PutBack row : PUT_BACKS) {
			MethodNode method = holder(node, row, srg);
			if (row.owner().equals(node.name) && method != null && !calls(method, row).isEmpty() && references(node, row.platformPackage())
					&& (row.code() != Code.FORGE_CLIENT || calls(method, WILL_DESTROY.owner(), WILL_DESTROY.name(srg)).isEmpty())) {
				rows.add(row);
			}
		}
		return rows;
	}

	/** The findings the class's rows answer, as reports name them ({@code SEAM-3, SEAM-4}); empty for a class the table doesn't name. */
	static String findings(String owner) {
		return PUT_BACKS.stream().filter(r -> r.owner().equals(owner)).map(r -> r.code().finding()).distinct().sorted().collect(Collectors.joining(", "));
	}

	/**
	 * Puts back the class's calls ({@link #applicable}), all of a method's places or none; returns what it put back, as
	 * {@code method: owner;name(desc), ...}. The method gets a marker for each row, listing them, which pre-flight reads
	 * ({@link #restoredIn}).
	 */
	static List<String> restore(ClassNode node) {
		List<PutBack> rows = applicable(node);
		List<String> restored = new ArrayList<>();
		if (rows.isEmpty()) return restored;
		if (rows.stream().anyMatch(VanillaCalls::checks)) {
			try {
				keepsHelpers(node.name);
			} catch (RuntimeException e) {
				return restored; // the check can't be copied: nothing is put back, in pre-flight's view as at load
			}
		}
		boolean srg = VanillaSeams.srg(node);
		// Frames are worked out on a copy of the methods put back in with them expanded, the class's others left out (a large
		// class's, BlockBehaviour's, would cost far more than its one method); the rewritten methods are read back with them
		// compressed again.
		ClassNode holders = new ClassNode();
		holders.version = node.version;
		holders.access = node.access;
		holders.name = node.name;
		holders.superName = node.superName;
		holders.interfaces = new ArrayList<>(node.interfaces);
		for (PutBack row : rows) {
			MethodNode method = holder(node, row, srg);
			if (!holders.methods.contains(method)) holders.methods.add(method);
		}
		ClassNode expanded = read(write(holders), ClassReader.EXPAND_FRAMES);
		List<String> rewritten = new ArrayList<>();
		for (PutBack row : rows) {
			MethodNode method = holder(expanded, row, srg);
			Rewritten done = rewrite(expanded, method, row, srg);
			if (done == null) continue;
			addHelpers(expanded, row, srg);
			AnnotationNode marker = new AnnotationNode(VanillaSeams.MARKER);
			marker.values = new ArrayList<>(List.of("calls", done.calls(), "keeps", keeps(row), "places", done.places(), "platform", row.platform().key(false),
					"finding", row.code().finding(), "through", through(row), "note", note(row)));
			if (method.invisibleAnnotations == null) method.invisibleAnnotations = new ArrayList<>();
			method.invisibleAnnotations.add(marker);
			if (!rewritten.contains(method.name + method.desc)) rewritten.add(method.name + method.desc);
			restored.add(method.name + method.desc + ": " + String.join(", ", done.calls()));
		}
		if (rewritten.isEmpty()) return restored;
		byte[] written = write(expanded);
		// The helpers name only the JDK's classes and the class's own: the game can't load KeepsDefault.
		if (new String(written, StandardCharsets.ISO_8859_1).contains(KEEPS_OWNER)) throw new IllegalStateException("a copy of " + KEEPS_OWNER + " names it");
		ClassNode compressed = read(written, 0);
		for (MethodNode method : compressed.methods) {
			int at = indexOf(node, method.name, method.desc);
			if (at < 0) node.methods.add(method);
			else if (rewritten.contains(method.name + method.desc)) node.methods.set(at, method);
		}
		for (FieldNode field : compressed.fields) {
			if (field.name.startsWith("fabricated$") && node.fields.stream().noneMatch(f -> f.name.equals(field.name))) node.fields.add(field);
		}
		return restored;
	}

	/** Whether the row's put-back asks of an object's class (the block's, the player's) first. */
	private static boolean checks(PutBack row) {
		return row.extension() != null || row.code().keepingPlayer() >= 0;
	}

	/** What a row's put-back stands for, as its marker names it: the extension's default, or the platform's hook. */
	private static String keeps(PutBack row) {
		return row.extension() != null ? row.extension().blockInterface() + "." + row.extension().name() : row.platform().owner() + "." + row.platform().name();
	}

	/** How the platform makes vanilla's call, as pre-flight's note says it: {@code the block's IBlockExtension.onDestroyedByPlayer}. */
	private static String through(PutBack row) {
		String keeps = keeps(row);
		return (row.extension() != null ? "the block's " : "") + keeps.substring(keeps.lastIndexOf('/') + 1);
	}

	/** What putting the call back does, as pre-flight's note of a hook of it says. */
	private static String note(PutBack row) {
		String loader = row.loader();
		return switch (row.code()) {
			case SERVER, FORGE_SERVER, CLIENT, FORGE_CLIENT -> "Fabricated puts it back there for the blocks keeping that method's default, so the hook applies to"
					+ " their breaking, and a block overriding it breaks as " + loader + " has it, without the hook";
			case HARVEST -> "Fabricated puts it back there for the blocks keeping that method's default, NeoForge's harvest check event following it as that"
					+ " default posts it, so the hook applies to their harvest check, and a block overriding it is checked as NeoForge has it, without the hook";
			case FORGE_HARVEST -> "Fabricated puts it back there for the blocks keeping that method's default broken by a player whose class keeps"
					+ " Player.hasCorrectToolForDrops, which fires Forge's harvest check event, so the hook applies to their harvest check; other breaks are"
					+ " checked as Forge has it, without the hook";
			case PROGRESS -> "Fabricated puts it back there, NeoForge's harvest check event following it as that method posts it, so the hook applies";
			case FORGE_PROGRESS -> "Fabricated puts it back there for a player whose class keeps Player.hasCorrectToolForDrops, which fires Forge's harvest"
					+ " check event, so the hook applies to their mining; other players' is checked as Forge has it, without the hook";
		};
	}

	/** What {@link #rewrite} put back: the calls, and in how many places. */
	private record Rewritten(List<String> calls, int places) {
	}

	/**
	 * Rewrites each place of the method that makes the platform's call; the calls put back, or null where a place isn't in
	 * the shape the row reads (the method is then left as it was).
	 */
	private static Rewritten rewrite(ClassNode owner, MethodNode method, PutBack row, boolean srg) {
		List<MethodInsnNode> places = calls(method, row);
		Map<AbstractInsnNode, State> frames = frames(owner, method);
		if (frames == null) return null;
		List<Site> sites = new ArrayList<>();
		for (MethodInsnNode place : places) {
			Site site = site(method, place, frames);
			if (site == null || !site.plain(needed(row)) || !site.receives(row.code())) return null;
			sites.add(site);
		}
		int temp = method.maxLocals;
		Set<String> putBack = new LinkedHashSet<>();
		for (Site site : sites) {
			Emitter out = new Emitter(owner.name, row, srg, site, temp);
			if (row.code() == Code.PROGRESS) {
				// No branch: vanilla's call goes in front of the hook's operands, and a helper posting the event takes the hook's place.
				out.harvestCall(putBack);
				method.instructions.insertBefore(site.start(), out.code);
				method.instructions.set(site.call(), new MethodInsnNode(Opcodes.INVOKESTATIC, owner.name, HARVEST_EVENT, HARVEST_EVENT_DESC, false));
				continue;
			}
			LabelNode platform = new LabelNode(), end = new LabelNode();
			out.vanilla(platform, end, putBack);
			method.instructions.insertBefore(site.start(), out.code);
			method.instructions.insertBefore(site.start(), platform);
			method.instructions.insertBefore(site.start(), frame(site.locals(), row.code() == Code.FORGE_SERVER ? temp : -1, site.under(), false));
			method.instructions.insert(site.call(), frame(site.locals(), -1, site.under(), true));
			method.instructions.insert(site.call(), end);
		}
		if (row.code() == Code.FORGE_SERVER) method.maxLocals = temp + 1;
		method.maxStack = Math.max(method.maxStack, sites.stream().mapToInt(s -> slots(s.under())).max().orElse(0) + 7);
		return new Rewritten(List.copyOf(putBack), sites.size());
	}

	/**
	 * The platform's call at a place, its operands' instructions, their types as the call takes them, and the frame before
	 * them.
	 */
	private record Site(MethodInsnNode call, List<List<AbstractInsnNode>> operands, List<Object> types, List<Object> locals, List<Object> under) {
		AbstractInsnNode start() {
			return operands.get(0).get(0);
		}

		/** Whether each operand vanilla's code loads again only loads a local or a constant, or reads a field. */
		boolean plain(int[] needed) {
			for (int i : needed) {
				int operand = i < 0 ? operands.size() + i : i;
				for (AbstractInsnNode insn : operands.get(operand)) {
					int op = insn.getOpcode();
					if (op >= 0 && !(op >= Opcodes.ACONST_NULL && op <= Opcodes.ALOAD) && op != Opcodes.GETFIELD && op != Opcodes.GETSTATIC) return false;
				}
			}
			return true;
		}

		/** Whether the operand vanilla's harvest check is made of is of the type vanilla makes it of ({@code ServerPlayer}, {@code Player}). */
		boolean receives(Code code) {
			if (!code.harvest()) return true;
			Member call = vanillaHarvest(code);
			return call.owner().equals(types.get(harvestPlayer(code)));
		}
	}

	/** The operands vanilla's code at the place loads again (negative from the end). */
	private static int[] needed(PutBack row) {
		return switch (row.code()) {
			case SERVER -> new int[] {0, 1, 2};
			case FORGE_SERVER -> new int[] {0, 1};
			case CLIENT -> new int[] {0, 1, 2, -1};
			case FORGE_CLIENT -> new int[] {0, 1, 2, 3, -1};
			case HARVEST -> new int[] {0, 1, 2, 3};
			case FORGE_HARVEST, PROGRESS, FORGE_PROGRESS -> harvestOperands(row.code());
		};
	}

	/** The operands vanilla's harvest check is made of, and the event's where it follows: the player and the state. */
	private static int[] harvestOperands(Code code) {
		return code == Code.FORGE_HARVEST ? new int[] {0, 3} : new int[] {0, 1};
	}

	/** Which operand of the platform's call is the player vanilla asks ({@code canHarvestBlock}'s last, the hooks' first or second). */
	private static int harvestPlayer(Code code) {
		return code == Code.HARVEST || code == Code.FORGE_HARVEST ? 3 : code == Code.PROGRESS ? 0 : 1;
	}

	/** Which operand is the state vanilla asks of. */
	private static int harvestState(Code code) {
		return code == Code.PROGRESS ? 1 : 0;
	}

	/** Vanilla's harvest check, as the method makes it: of the server's {@code ServerPlayer}, or {@code getDestroyProgress}'s {@code Player}. */
	private static Member vanillaHarvest(Code code) {
		return code == Code.HARVEST || code == Code.FORGE_HARVEST ? SERVER_HAS_CORRECT_TOOL : HAS_CORRECT_TOOL;
	}

	/**
	 * The place's operands, each the instructions that push it, found by the stack's depth before each instruction; null
	 * where the code there isn't straight (a jump, a label a jump or handler reaches, a store) or leaves something else
	 * on the stack. A static call has no receiver.
	 */
	private static Site site(MethodNode method, MethodInsnNode call, Map<AbstractInsnNode, State> frames) {
		State atCall = frames.get(call);
		if (atCall == null) return null;
		boolean receiver = call.getOpcode() != Opcodes.INVOKESTATIC;
		Type[] args = Type.getArgumentTypes(call.desc);
		int count = args.length + (receiver ? 1 : 0);
		int[] sizes = new int[count];
		if (receiver) sizes[0] = 1;
		for (int i = 0; i < args.length; i++) sizes[i + (receiver ? 1 : 0)] = args[i].getSize();
		int total = 0;
		for (int size : sizes) total += size;
		int base = atCall.stack().size() - total;
		if (base < 0) return null;
		// The operands' types as the stack has them at the call.
		List<Object> types = new ArrayList<>();
		for (int i = 0, slot = base; i < count; slot += sizes[i], i++) types.add(atCall.stack().get(slot));
		Set<LabelNode> targets = targets(method);
		List<List<AbstractInsnNode>> operands = new ArrayList<>();
		for (int i = 0; i < count; i++) operands.add(new ArrayList<>());
		int depth = atCall.stack().size();
		AbstractInsnNode insn = call.getPrevious();
		for (int operand = count - 1; operand >= 0; operand--) {
			depth -= sizes[operand];
			// The operand begins at the nearest instruction before which the stack is that deep.
			while (true) {
				if (insn == null || insn instanceof FrameNode || insn instanceof LabelNode label && targets.contains(label)) return null;
				int op = insn.getOpcode();
				if (op >= Opcodes.ISTORE && op <= Opcodes.SASTORE || op == Opcodes.IINC || insn instanceof JumpInsnNode || op >= Opcodes.IRETURN && op <= Opcodes.RETURN
						|| op == Opcodes.ATHROW || op == Opcodes.TABLESWITCH || op == Opcodes.LOOKUPSWITCH) return null;
				State before = frames.get(insn);
				if (op >= 0 && before == null) return null;
				operands.get(operand).add(0, insn);
				if (op >= 0 && before.stack().size() < base) return null;
				if (op >= 0 && before.stack().size() == depth) break;
				insn = insn.getPrevious();
			}
			insn = insn.getPrevious();
		}
		// The frame after the call goes at the next instruction, which must have none of its own.
		for (AbstractInsnNode next = call.getNext(); next != null && next.getOpcode() < 0; next = next.getNext()) {
			if (next instanceof FrameNode) return null;
		}
		State atStart = frames.get(operands.get(0).get(0));
		List<Object> under = atStart.stack();
		if (under.stream().anyMatch(t -> !(t instanceof String) && !(t instanceof Integer))) return null;
		if (atStart.locals().stream().anyMatch(t -> !(t instanceof String) && !(t instanceof Integer))) return null;
		return new Site(call, operands, types, atStart.locals(), under);
	}

	/** Writes vanilla's branch of a place: what goes before the platform's operands. */
	private static final class Emitter {
		final InsnList code = new InsnList();
		private final String owner;
		private final PutBack row;
		private final boolean srg;
		private final Site site;
		private final int temp;

		Emitter(String owner, PutBack row, boolean srg, Site site, int temp) {
			this.owner = owner;
			this.row = row;
			this.srg = srg;
			this.site = site;
			this.temp = temp;
		}

		void vanilla(LabelNode platform, LabelNode end, Set<String> putBack) {
			Code removal = row.code();
			if (removal == Code.FORGE_SERVER) {
				load(0);
				load(1);
				code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, "fabricated$stateAt", "(L" + owner + ";" + POS + ")Ljava/lang/Object;", false));
				code.add(new VarInsnNode(Opcodes.ASTORE, temp));
			}
			if (row.extension() != null) {
				state();
				code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, row.extension().entry(), "(L" + STATE + ";)Z", false));
				code.add(new JumpInsnNode(Opcodes.IFEQ, platform));
			}
			if (removal.keepingPlayer() >= 0) {
				load(removal.keepingPlayer());
				code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, KEEPS_PLAYER, "(" + PLAYER + ")Z", false));
				code.add(new JumpInsnNode(Opcodes.IFEQ, platform));
			}
			if (removal.harvest()) {
				harvestCall(putBack);
			} else if (removal == Code.SERVER || removal == Code.FORGE_SERVER) {
				if (removal == Code.FORGE_SERVER) {
					load(0);
					load(1);
					state();
					code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, "fabricated$playerWillDestroy", "(L" + owner + ";" + POS + "L" + STATE + ";)V", false));
				}
				load(0);
				field(LEVEL_FIELD);
				load(1);
				code.add(new InsnNode(Opcodes.ICONST_0));
				call(REMOVE_BLOCK, putBack);
				code.add(new InsnNode(Opcodes.DUP));
				code.add(new JumpInsnNode(Opcodes.IFEQ, end));
				state();
				block();
				load(0);
				field(LEVEL_FIELD);
				load(1);
				state();
				call(DESTROY, putBack);
			} else {
				if (removal == Code.FORGE_CLIENT) {
					load(0);
					block();
					load(1);
					load(2);
					load(0);
					load(3);
					call(WILL_DESTROY, putBack);
					if (!WILL_DESTROY.desc(srg).endsWith(")V")) code.add(new InsnNode(Opcodes.POP));
				}
				load(1);
				load(2);
				load(site.operands().size() - 1);
				call(LEGACY_BLOCK, putBack);
				code.add(new IntInsnNode(Opcodes.BIPUSH, 11));
				call(SET_BLOCK, putBack);
			}
			code.add(new JumpInsnNode(Opcodes.GOTO, end));
		}

		/**
		 * Vanilla's harvest check, {@code player.hasCorrectToolForDrops(state)}; on NeoForge, where the place isn't the hook's
		 * own (its operands follow, {@link Code#PROGRESS}), the helper posting the event with its answer after it.
		 */
		void harvestCall(Set<String> putBack) {
			Code harvest = row.code();
			load(harvestPlayer(harvest));
			load(harvestState(harvest));
			call(vanillaHarvest(harvest), putBack);
			if (harvest != Code.HARVEST) return;
			for (int operand : new int[] {3, 0, 1, 2}) load(operand);
			code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, HARVEST_EVENT, HARVEST_EVENT_DESC, false));
		}

		private void load(int operand) {
			for (AbstractInsnNode insn : site.operands().get(operand)) {
				if (insn.getOpcode() >= 0) code.add(insn.clone(Map.of()));
			}
		}

		private void state() {
			if (row.code() == Code.FORGE_SERVER) {
				code.add(new VarInsnNode(Opcodes.ALOAD, temp));
				code.add(new TypeInsnNode(Opcodes.CHECKCAST, STATE));
			} else {
				load(row.code() == Code.SERVER ? 2 : 0);
			}
		}

		private void block() {
			code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, "fabricated$block", "(L" + STATE + ";)L" + BLOCK + ";", false));
		}

		private void field(Member field) {
			code.add(new FieldInsnNode(Opcodes.GETFIELD, field.owner(), field.name(srg), field.desc(srg)));
		}

		private void call(Member member, Set<String> putBack) {
			code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, member.owner(), member.name(srg), member.desc(srg), false));
			putBack.add(member.key(srg));
		}
	}

	/**
	 * The private helpers vanilla's branch calls: the checks, the block's {@code getBlock}, Forge's server's reads and
	 * NeoForge's harvest check event; each once.
	 */
	private static void addHelpers(ClassNode node, PutBack row, boolean srg) {
		Extension extension = row.extension();
		if (extension != null && indexOf(node, extension.entry(), "(L" + STATE + ";)Z") < 0) {
			node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, extension.field(), "Ljava/util/Map;", null, null));
			node.methods.add(keepsDefault(extension, node.name));
		}
		if (row.code().keepingPlayer() >= 0 && indexOf(node, KEEPS_PLAYER, "(" + PLAYER + ")Z") < 0) {
			node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, KNOWN_PLAYER, "Ljava/util/Map;", null, null));
			node.methods.add(keepsPlayer(node.name, srg));
		}
		if (checks(row) && indexOf(node, "fabricated$keeps", KEEPS) < 0) node.methods.addAll(keepsHelpers(node.name));
		if (extension != null && indexOf(node, "fabricated$block", "(L" + STATE + ";)L" + BLOCK + ";") < 0) {
			node.methods.add(helper("fabricated$block", "(L" + STATE + ";)L" + BLOCK + ";", Opcodes.ARETURN, new VarInsnNode(Opcodes.ALOAD, 0), invoke(GET_BLOCK, srg)));
		}
		if ((row.code() == Code.HARVEST || row.code() == Code.PROGRESS) && indexOf(node, HARVEST_EVENT, HARVEST_EVENT_DESC) < 0) node.methods.add(harvestEvent());
		if (row.code() != Code.FORGE_SERVER) return;
		String gameMode = "L" + row.owner() + ";";
		node.methods.add(helper("fabricated$stateAt", "(" + gameMode + POS + ")Ljava/lang/Object;", Opcodes.ARETURN, new VarInsnNode(Opcodes.ALOAD, 0),
				get(LEVEL_FIELD, srg), new VarInsnNode(Opcodes.ALOAD, 1), invoke(GET_BLOCK_STATE, srg)));
		List<AbstractInsnNode> willDestroy = new ArrayList<>(List.of(new VarInsnNode(Opcodes.ALOAD, 2), invoke(GET_BLOCK, srg), new VarInsnNode(Opcodes.ALOAD, 0),
				get(LEVEL_FIELD, srg), new VarInsnNode(Opcodes.ALOAD, 1), new VarInsnNode(Opcodes.ALOAD, 2), new VarInsnNode(Opcodes.ALOAD, 0),
				get(PLAYER_FIELD, srg), invoke(WILL_DESTROY, srg)));
		if (!WILL_DESTROY.desc(srg).endsWith(")V")) willDestroy.add(new InsnNode(Opcodes.POP));
		node.methods.add(helper("fabricated$playerWillDestroy", "(" + gameMode + POS + "L" + STATE + ";)V", Opcodes.RETURN,
				willDestroy.toArray(AbstractInsnNode[]::new)));
	}

	/**
	 * {@code private static boolean <entry>(BlockState state)}: {@link KeepsDefault#keepsDefault} of the state and its block,
	 * for the extension's methods, whose code is copied into the class beside it ({@link #keepsHelpers}), with the answers
	 * the class keeps in the extension's field, made the first time it asks (its initialiser stays as it is).
	 */
	private static MethodNode keepsDefault(Extension extension, String owner) {
		MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, extension.entry(), "(L" + STATE + ";)Z", null, null);
		InsnList code = method.instructions;
		known(code, owner, extension.field(), STATE);
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, "fabricated$block", "(L" + STATE + ";)L" + BLOCK + ";", false));
		code.add(new LdcInsnNode(Type.getObjectType(extension.stateInterface())));
		code.add(new LdcInsnNode(Type.getObjectType(extension.blockInterface())));
		code.add(new LdcInsnNode(extension.name()));
		classes(code, Type.getArgumentTypes(extension.desc()));
		classes(code, Type.getArgumentTypes(extension.blockDesc()));
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, "fabricated$keeps", KEEPS, false));
		code.add(new InsnNode(Opcodes.IRETURN));
		method.maxStack = 12;
		method.maxLocals = 1;
		return method;
	}

	/**
	 * {@code private static boolean fabricated$keepsPlayerHarvest(Player player)}: {@link KeepsDefault#keepsMethod} of the
	 * player for {@code Player.hasCorrectToolForDrops}, its answers kept as {@link #keepsDefault}'s are.
	 */
	private static MethodNode keepsPlayer(String owner, boolean srg) {
		MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, KEEPS_PLAYER, "(" + PLAYER + ")Z", null, null);
		InsnList code = method.instructions;
		known(code, owner, KNOWN_PLAYER, PLAYER_CLASS);
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(new LdcInsnNode(Type.getObjectType(PLAYER_CLASS)));
		code.add(new LdcInsnNode(HAS_CORRECT_TOOL.name(srg)));
		classes(code, Type.getArgumentTypes(HAS_CORRECT_TOOL.desc(srg)));
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, "fabricated$keepsMethod", KEEPS_METHOD, false));
		code.add(new InsnNode(Opcodes.IRETURN));
		method.maxStack = 9;
		method.maxLocals = 1;
		return method;
	}

	/** Pushes the class's store of answers in {@code field}, made the first time; the method's one parameter is of {@code param}. */
	private static void known(InsnList code, String owner, String field, String param) {
		LabelNode known = new LabelNode();
		code.add(new FieldInsnNode(Opcodes.GETSTATIC, owner, field, "Ljava/util/Map;"));
		code.add(new InsnNode(Opcodes.DUP));
		code.add(new JumpInsnNode(Opcodes.IFNONNULL, known));
		code.add(new InsnNode(Opcodes.POP));
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, "fabricated$known", "()Ljava/util/Map;", false));
		code.add(new InsnNode(Opcodes.DUP));
		code.add(new FieldInsnNode(Opcodes.PUTSTATIC, owner, field, "Ljava/util/Map;"));
		code.add(known);
		code.add(new FrameNode(Opcodes.F_NEW, 1, new Object[] {param}, 1, new Object[] {"java/util/Map"}));
	}

	/**
	 * {@code private static boolean fabricated$harvestCheck(boolean vanilla, Player player, BlockState state, BlockGetter
	 * level, BlockPos pos)}: NeoForge's {@code EventHooks.doPlayerHarvestCheck} past vanilla's call, compiled the same in
	 * NeoForge 21.1.265, 21.11.45, 26.1.2.116, 26.2.0.90 and 26.3.0.95:
	 * {@code NeoForge.EVENT_BUS.post(new PlayerEvent.HarvestCheck(player, state, level, pos, vanilla)).canHarvest()}.
	 */
	private static MethodNode harvestEvent() {
		MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, HARVEST_EVENT, HARVEST_EVENT_DESC, null, null);
		InsnList code = method.instructions;
		code.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/neoforged/neoforge/common/NeoForge", "EVENT_BUS", "L" + EVENT_BUS + ";"));
		code.add(new TypeInsnNode(Opcodes.NEW, HARVEST_CHECK));
		code.add(new InsnNode(Opcodes.DUP));
		for (int slot = 1; slot <= 4; slot++) code.add(new VarInsnNode(Opcodes.ALOAD, slot));
		code.add(new VarInsnNode(Opcodes.ILOAD, 0));
		code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, HARVEST_CHECK, "<init>", "(" + PLAYER + "L" + STATE + ";" + GETTER + POS + "Z)V", false));
		code.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, EVENT_BUS, "post", "(L" + EVENT + ";)L" + EVENT + ";", true));
		code.add(new TypeInsnNode(Opcodes.CHECKCAST, HARVEST_CHECK));
		code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, HARVEST_CHECK, "canHarvest", "()Z", false));
		code.add(new InsnNode(Opcodes.IRETURN));
		method.maxStack = 8;
		method.maxLocals = 5;
		return method;
	}

	/** Pushes a {@code Class[]} of the types. */
	private static void classes(InsnList code, Type[] types) {
		code.add(new IntInsnNode(Opcodes.BIPUSH, types.length));
		code.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Class"));
		for (int i = 0; i < types.length; i++) {
			code.add(new InsnNode(Opcodes.DUP));
			code.add(new IntInsnNode(Opcodes.BIPUSH, i));
			if (types[i].getSort() == Type.BOOLEAN) code.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/Boolean", "TYPE", "Ljava/lang/Class;"));
			else code.add(new LdcInsnNode(types[i]));
			code.add(new InsnNode(Opcodes.AASTORE));
		}
	}

	private static final String KEEPS = "(Ljava/util/Map;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/String;"
			+ "[Ljava/lang/Class;[Ljava/lang/Class;)Z";
	private static final String KEEPS_METHOD = "(Ljava/util/Map;Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)Z";
	private static final String KEEPS_OWNER = Type.getInternalName(KeepsDefault.class);
	private static volatile byte[] keepsClass;

	/**
	 * {@link KeepsDefault}'s methods, as javac wrote them, made the class's own private helpers: {@code fabricated$keeps},
	 * {@code fabricated$keepsMethod}, {@code fabricated$overrides} and {@code fabricated$declares}, their calls of each other
	 * the class's. They name only the JDK's classes. Read afresh for each class: a tree shared between threads isn't safe to
	 * copy (ASM's {@code MethodNode.accept} resets and sets its labels' state as it goes), and translation reads classes in
	 * parallel.
	 */
	private static List<MethodNode> keepsHelpers(String owner) {
		byte[] data = keepsClass;
		if (data == null) {
			try (java.io.InputStream in = KeepsDefault.class.getResourceAsStream(KeepsDefault.class.getSimpleName() + ".class")) {
				if (in == null) throw new IllegalStateException("no " + KEEPS_OWNER);
				keepsClass = data = in.readAllBytes();
			} catch (java.io.IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		}
		List<MethodNode> helpers = new ArrayList<>();
		for (MethodNode helper : read(data, 0).methods) {
			if ((helper.access & Opcodes.ACC_STATIC) == 0 || helper.name.startsWith("<")) continue;
			helper.name = helperName(helper.name);
			helper.access = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC;
			// Of KeepsDefault's source: its generic signature, its locals' names and its lines.
			helper.signature = null;
			helper.localVariables = null;
			for (AbstractInsnNode insn : helper.instructions) {
				if (insn instanceof LineNumberNode) helper.instructions.remove(insn);
				if (insn instanceof MethodInsnNode call && call.owner.equals(KEEPS_OWNER)) {
					call.owner = owner;
					call.name = helperName(call.name);
				}
			}
			helpers.add(helper);
		}
		return helpers;
	}

	private static String helperName(String name) {
		return "fabricated$" + (name.equals("keepsDefault") ? "keeps" : name);
	}

	/** A private static method of straight code, its parameters' slots enough for what it loads. */
	private static MethodNode helper(String name, String desc, int returnOpcode, AbstractInsnNode... body) {
		MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, name, desc, null, null);
		for (AbstractInsnNode insn : body) method.instructions.add(insn);
		method.instructions.add(new InsnNode(returnOpcode));
		method.maxStack = 6;
		method.maxLocals = Type.getArgumentsAndReturnSizes(desc) >> 2;
		return method;
	}

	private static MethodInsnNode invoke(Member member, boolean srg) {
		return new MethodInsnNode(Opcodes.INVOKEVIRTUAL, member.owner(), member.name(srg), member.desc(srg), false);
	}

	private static FieldInsnNode get(Member field, boolean srg) {
		return new FieldInsnNode(Opcodes.GETFIELD, field.owner(), field.name(srg), field.desc(srg));
	}

	/**
	 * A frame of the place: its locals (and {@code temp}, an {@code Object}, where not negative) and the stack below the
	 * platform's operands, with the call's result on it after it.
	 */
	private static FrameNode frame(List<Object> locals, int temp, List<Object> under, boolean result) {
		List<Object> local = compact(locals);
		if (temp >= 0) {
			int slots = slots(locals);
			for (int i = slots; i < temp; i++) local.add(Opcodes.TOP);
			local.add("java/lang/Object");
		}
		List<Object> stack = compact(under);
		if (result) stack.add(Opcodes.INTEGER);
		return new FrameNode(Opcodes.F_NEW, local.size(), local.toArray(), stack.size(), stack.toArray());
	}

	/** {@link AnalyzerAdapter}'s types as a frame's: a long or double once, not followed by its {@code TOP}. */
	private static List<Object> compact(List<Object> types) {
		List<Object> out = new ArrayList<>();
		for (int i = 0; i < types.size(); i++) {
			Object type = types.get(i);
			out.add(type);
			if (type == Opcodes.LONG || type == Opcodes.DOUBLE) i++;
		}
		return out;
	}

	private static int slots(List<Object> types) {
		return types.size();
	}

	/** The types of the locals and of the stack before an instruction, as {@link AnalyzerAdapter} has them. */
	private record State(List<Object> locals, List<Object> stack) {
	}

	/**
	 * The locals and the stack before each instruction of the method, as {@link AnalyzerAdapter} works them out from its
	 * expanded frames (none where the code isn't reached); null where it can't.
	 */
	private static Map<AbstractInsnNode, State> frames(ClassNode owner, MethodNode method) {
		Map<AbstractInsnNode, State> frames = new IdentityHashMap<>();
		AnalyzerAdapter flow = new AnalyzerAdapter(owner.name, method.access, method.name, method.desc, null);
		try {
			for (AbstractInsnNode insn : method.instructions) {
				if (flow.stack != null) frames.put(insn, new State(List.copyOf(flow.locals), List.copyOf(flow.stack)));
				insn.accept(flow);
			}
		} catch (RuntimeException e) {
			return null;
		}
		return frames;
	}

	/** The labels a jump, a switch or an exception handler goes to, or a handler's range starts or ends at. */
	private static Set<LabelNode> targets(MethodNode method) {
		Set<LabelNode> targets = VanillaSeams.jumpTargets(method);
		for (TryCatchBlockNode block : method.tryCatchBlocks) {
			targets.add(block.start);
			targets.add(block.end);
		}
		return targets;
	}

	private static MethodNode holder(ClassNode node, PutBack row, boolean srg) {
		int at = indexOf(node, row.method(srg), row.desc());
		return at < 0 ? null : node.methods.get(at);
	}

	private static int indexOf(ClassNode node, String name, String desc) {
		for (int i = 0; i < node.methods.size(); i++) {
			MethodNode m = node.methods.get(i);
			if (m.name.equals(name) && m.desc.equals(desc)) return i;
		}
		return -1;
	}

	/** The method's calls of the row's platform call: a static hook's, or an instance method's. */
	private static List<MethodInsnNode> calls(MethodNode method, PutBack row) {
		List<MethodInsnNode> found = new ArrayList<>();
		if (method.instructions == null) return found;
		Member member = row.platform();
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof MethodInsnNode call && (call.getOpcode() == Opcodes.INVOKESTATIC) == row.code().hookCall() && call.owner.equals(member.owner())
					&& call.name.equals(member.name()) && call.desc.equals(member.desc())) found.add(call);
		}
		return found;
	}

	private static List<MethodInsnNode> calls(MethodNode method, String owner, String name) {
		List<MethodInsnNode> found = new ArrayList<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) found.add(call);
		}
		return found;
	}

	/** Whether the class's code names a class of the package (a call's, a field's or a type's owner). */
	private static boolean references(ClassNode node, String pkg) {
		for (MethodNode method : node.methods) {
			if (method.instructions == null) continue;
			for (AbstractInsnNode insn : method.instructions) {
				String owner = insn instanceof MethodInsnNode m ? m.owner : insn instanceof FieldInsnNode f ? f.owner : insn instanceof TypeInsnNode t ? t.desc : null;
				if (owner != null && owner.startsWith(pkg)) return true;
			}
		}
		return false;
	}

	/**
	 * A vanilla call a platform makes otherwise in a game method, which Fabricated doesn't put back: the method, vanilla's
	 * call (its owner, its names), the platform's call that shows the platform's code is there, and why, as pre-flight's
	 * hint for a hook of the call says it.
	 */
	private record NotPutBack(String owner, String method, String srgMethod, String callOwner, Set<String> names, String hookOwner, String hook, String why) {
	}

	private static final String ITEM_CLASS = "net/minecraft/world/item/Item";
	private static final String STACK_CLASS = "net/minecraft/world/item/ItemStack";
	/**
	 * The calls not put back. Forge's server makes {@code playerWillDestroy} from its extension, once the tool is damaged,
	 * where vanilla makes it before the creative check: only the removal goes back there (SEAM-3). Both platforms' servers
	 * ask the held item's {@code canAttackBlock} (1.21.11 on: the stack's {@code canDestroyBlock}) inside their block break
	 * event's hook, which pre-cancels the event where the item says no and lets a listener overrule it, where vanilla returns
	 * before anything else; NeoForge 26.1.2 to 26.3's client too (SEAM-4).
	 */
	private static final List<NotPutBack> NOT_PUT_BACK = List.of(
			new NotPutBack(GAME_MODE, "destroyBlock", "m_9280_", WILL_DESTROY.owner(), Set.of(WILL_DESTROY.name(), WILL_DESTROY.srgName()), FORGE_HOOKS,
					"onBlockBreakEvent", "Forge's ServerPlayerGameMode makes that call through the block's IForgeBlock.onDestroyedByPlayer instead, which Fabricated"
							+ " doesn't put back there (SEAM-3)."),
			new NotPutBack(GAME_MODE, "destroyBlock", "m_9280_", ITEM_CLASS, Set.of("canAttackBlock", "m_6777_"), FORGE_HOOKS, "onBlockBreakEvent",
					breakEvent("Forge", "ServerPlayerGameMode", "canAttackBlock", "ForgeHooks.onBlockBreakEvent")),
			new NotPutBack(GAME_MODE, "destroyBlock", "m_9280_", STACK_CLASS, Set.of("canDestroyBlock"), FORGE_HOOKS, "onBlockBreakEvent",
					breakEvent("Forge", "ServerPlayerGameMode", "canDestroyBlock", "ForgeHooks.onBlockBreakEvent")),
			new NotPutBack(GAME_MODE, "destroyBlock", "m_9280_", ITEM_CLASS, Set.of("canAttackBlock"), "net/neoforged/neoforge/common/CommonHooks", "fireBlockBreak",
					breakEvent("NeoForge", "ServerPlayerGameMode", "canAttackBlock", "CommonHooks.fireBlockBreak")),
			new NotPutBack(GAME_MODE, "destroyBlock", "m_9280_", STACK_CLASS, Set.of("canDestroyBlock"), "net/neoforged/neoforge/common/CommonHooks", "fireBlockBreak",
					breakEvent("NeoForge", "ServerPlayerGameMode", "canDestroyBlock", "CommonHooks.fireBlockBreak")),
			new NotPutBack(CLIENT_GAME_MODE, "destroyBlock", "m_105267_", STACK_CLASS, Set.of("canDestroyBlock"), "net/neoforged/neoforge/common/CommonHooks",
					"fireBlockBreak", breakEvent("NeoForge", "MultiPlayerGameMode", "canDestroyBlock", "CommonHooks.fireBlockBreak")));

	private static String breakEvent(String loader, String gameMode, String check, String hook) {
		return loader + "'s " + gameMode + " asks the held item's " + check + " inside its block break event (" + hook + "), which can overrule the answer,"
				+ " so Fabricated doesn't put that check back there (SEAM-4).";
	}

	/**
	 * Why the class's platform makes vanilla's call {@code owner;name} in its method otherwise, where Fabricated doesn't put
	 * that call back, as pre-flight's hint says it ({@code Forge's ServerPlayerGameMode makes that call through the block's
	 * IForgeBlock.onDestroyedByPlayer instead, which Fabricated doesn't put back there (SEAM-3).}); null for any other.
	 */
	static String notPutBack(ClassNode node, String method, String desc, String owner, String name) {
		boolean srg = VanillaSeams.srg(node);
		for (NotPutBack row : NOT_PUT_BACK) {
			if (!row.owner().equals(node.name) || !(srg ? row.srgMethod() : row.method()).equals(method) || !DESTROY_BLOCK.equals(desc)
					|| !owner.equals(row.callOwner()) || !row.names().contains(name)) continue;
			int at = indexOf(node, method, desc);
			if (at >= 0 && !calls(node.methods.get(at), row.hookOwner(), row.hook()).isEmpty()) return row.why();
		}
		return null;
	}

	/**
	 * In how many places of the method, as read, vanilla's call {@code owner;name(desc)} is put back (each makes each call
	 * once); 0 for none.
	 */
	static int places(MethodNode method, String owner, String name, String desc) {
		if (method.invisibleAnnotations == null) return 0;
		String key = owner + ";" + name + desc;
		for (AnnotationNode ann : method.invisibleAnnotations) {
			if (VanillaSeams.MARKER.equals(ann.desc) && value(ann, "calls") instanceof List<?> calls && calls.contains(key)
					&& value(ann, "places") instanceof Integer places) return places;
		}
		return 0;
	}

	/** The calls a class as read has had put back, as {@link #restore} marked its methods. */
	static List<VanillaSeams.Restored> restoredIn(ClassNode node) {
		List<VanillaSeams.Restored> found = new ArrayList<>();
		for (MethodNode method : node.methods) {
			if (method.invisibleAnnotations == null) continue;
			for (AnnotationNode ann : method.invisibleAnnotations) {
				if (!VanillaSeams.MARKER.equals(ann.desc) || !(value(ann, "calls") instanceof List<?> calls)) continue;
				String keeps = String.valueOf(value(ann, "keeps"));
				for (Object call : calls) {
					String key = String.valueOf(call);
					int semicolon = key.indexOf(';'), paren = key.indexOf('(');
					found.add(new VanillaSeams.Restored(key.substring(0, semicolon), key.substring(semicolon + 1, paren), key.substring(paren),
							method.name + method.desc, keeps, String.valueOf(value(ann, "finding")), String.valueOf(value(ann, "through")),
							String.valueOf(value(ann, "note"))));
				}
			}
		}
		return found;
	}

	/**
	 * What putting the class's calls back changes ({@code restored}: a copy, put back), for {@link SeamConflicts}: the
	 * platform's calls they stand in for
	 * ({@code moved}), the methods holding them, the platform's own methods no longer reached for the blocks keeping the
	 * default ({@code bypassed}: its private {@code removeBlock}), and the game's other methods whose calls change for those
	 * blocks ({@code others}, by class, as names: {@link #others}).
	 */
	static void changes(ClassNode node, ClassNode restored, Set<String> moved, Set<String> holders, Set<String> bypassed, Map<String, Set<String>> others) {
		boolean srg = VanillaSeams.srg(node);
		for (PutBack row : applicable(node)) {
			MethodNode method = holder(restored, row, srg);
			Member platform = row.platform();
			if (method == null || method.invisibleAnnotations == null || method.invisibleAnnotations.stream()
					.noneMatch(a -> VanillaSeams.MARKER.equals(a.desc) && platform.key(false).equals(value(a, "platform")))) continue;
			moved.add(platform.key(false));
			holders.add(row.method(srg) + row.desc());
			if (platform.owner().equals(node.name)) bypassed.add(platform.name() + platform.desc());
			others(row).forEach((owner, names) -> others.computeIfAbsent(owner, k -> new TreeSet<>()).addAll(names));
		}
	}

	/**
	 * The game's other methods whose calls change where a row's calls are put back, by class, as names (Mojang's and SRG's):
	 * the extensions' defaults, no longer called; on Forge's server {@code Level.removeBlock}, called where Forge's default
	 * sets the block itself; NeoForge's {@code EventHooks.doPlayerHarvestCheck}, which vanilla's call and the event's helper
	 * stand in for; Forge's {@code ForgeHooks.isCorrectToolForDrops}, no longer called, and {@code
	 * Player.hasCorrectToolForDrops}, called where Forge's default only fires the event (a block needing no tool).
	 */
	static Map<String, Set<String>> others(PutBack row) {
		Map<String, Set<String>> others = new TreeMap<>();
		if (row.extension() != null) {
			others.computeIfAbsent(row.extension().stateInterface(), k -> new TreeSet<>()).add(row.extension().name());
			others.computeIfAbsent(row.extension().blockInterface(), k -> new TreeSet<>()).add(row.extension().name());
		}
		switch (row.code()) {
			case FORGE_SERVER -> {
				for (String level : List.of(LEVEL, SERVER_LEVEL)) others.computeIfAbsent(level, k -> new TreeSet<>()).addAll(List.of(REMOVE_BLOCK.name(), REMOVE_BLOCK.srgName()));
			}
			case HARVEST, PROGRESS -> others.computeIfAbsent(EVENT_HOOKS, k -> new TreeSet<>()).add(HARVEST_HOOK.name());
			case FORGE_HARVEST, FORGE_PROGRESS -> {
				others.computeIfAbsent(FORGE_HOOKS, k -> new TreeSet<>()).add(FORGE_HARVEST_HOOK.name());
				others.computeIfAbsent(PLAYER_CLASS, k -> new TreeSet<>()).addAll(List.of(HAS_CORRECT_TOOL.name(), HAS_CORRECT_TOOL.srgName()));
			}
			default -> {
			}
		}
		return others;
	}

	/** Every class whose methods' calls a put-back in the class may change ({@link #others}), of any platform's rows. */
	static Set<String> otherClasses(String owner) {
		Set<String> classes = new TreeSet<>();
		for (PutBack row : PUT_BACKS) {
			if (row.owner().equals(owner)) classes.addAll(others(row).keySet());
		}
		return classes;
	}

	/**
	 * The members the rewrite adds to the class's methods ({@code owner;name(desc)}, {@code owner;name:desc}): those its
	 * methods name more often once {@code restored}, but its helpers; and the members and classes those helpers name.
	 */
	static Set<String> added(ClassNode original, ClassNode restored) {
		Set<String> added = new LinkedHashSet<>();
		for (MethodNode method : restored.methods) {
			if (method.instructions == null) continue;
			if (method.name.startsWith("fabricated$")) {
				for (AbstractInsnNode insn : method.instructions) {
					String key = key(insn);
					if (key != null && !own(key, restored)) added.add(key);
					if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof Type type && type.getSort() == Type.OBJECT) added.add(type.getInternalName());
				}
				continue;
			}
			int at = indexOf(original, method.name, method.desc);
			if (at < 0) continue;
			Map<String, Integer> before = counts(original.methods.get(at)), after = counts(method);
			// The class's own methods it calls more often are those it declares again or adds (SEAM-1's, its helpers).
			after.forEach((key, n) -> {
				if (n > before.getOrDefault(key, 0) && !own(key, restored)) added.add(key);
			});
		}
		return added;
	}

	/** Whether a method of the class has more constant pushes once {@code restored} (its helpers apart). */
	static boolean addsConstants(ClassNode original, ClassNode restored) {
		for (MethodNode method : restored.methods) {
			int at = indexOf(original, method.name, method.desc);
			if (at >= 0 && !method.name.startsWith("fabricated$") && constants(method) > constants(original.methods.get(at))) return true;
		}
		return false;
	}

	private static int constants(MethodNode method) {
		int n = 0;
		if (method.instructions == null) return n;
		for (AbstractInsnNode insn : method.instructions) {
			int op = insn.getOpcode();
			if (op >= Opcodes.ACONST_NULL && op <= Opcodes.SIPUSH || insn instanceof LdcInsnNode) n++;
		}
		return n;
	}

	/** Whether the member is one of the class's methods (SEAM-1's, the helpers) or a field the helpers keep answers in. */
	private static boolean own(String key, ClassNode node) {
		return key.startsWith(node.name + ";") && (key.contains("(") || key.startsWith(node.name + ";fabricated$"));
	}

	private static Map<String, Integer> counts(MethodNode method) {
		Map<String, Integer> counts = new java.util.HashMap<>();
		if (method.instructions == null) return counts;
		for (AbstractInsnNode insn : method.instructions) {
			String key = key(insn);
			if (key != null) counts.merge(key, 1, Integer::sum);
		}
		return counts;
	}

	private static String key(AbstractInsnNode insn) {
		if (insn instanceof MethodInsnNode call) return call.owner + ";" + call.name + call.desc;
		if (insn instanceof FieldInsnNode field) return field.owner + ";" + field.name + ":" + field.desc;
		return null;
	}

	/**
	 * Whether every member and class {@code added} names is in the game, by the classes' own declarations and those they
	 * inherit (the JDK's taken as there): pre-flight puts nothing back that would fail as it runs. A member of a class the
	 * game's files don't hold (NeoForge's event bus, a library of the platform's) counts where the platform's code that the
	 * helpers copy names it the same way ({@code copied}, {@link #copied}). The classes are read for their members' names
	 * alone, each once in {@code classes}.
	 */
	static boolean resolves(Set<String> added, ClassIndex classes, Set<String> copied) {
		for (String key : added) {
			int semicolon = key.indexOf(';');
			if (semicolon < 0) {
				if (!key.startsWith("java/") && !classes.contains(key)) return false;
				continue;
			}
			String owner = key.substring(0, semicolon), member = key.substring(semicolon + 1);
			if (owner.startsWith("java/") || copied.contains(key) && !classes.contains(owner)) continue;
			if (!declares(classes, owner, member, member.indexOf('(') < 0, new LinkedHashSet<>())) return false;
		}
		return true;
	}

	/**
	 * The members named by the platform's code that the class's put-backs copy, as {@code owner;name(desc)}, read from the
	 * game: NeoForge's {@code EventHooks.doPlayerHarvestCheck}, whose post of its event the harvest checks' helper makes
	 * ({@link #harvestEvent}). None where no row copying it applies, or the game hasn't it.
	 */
	static Set<String> copied(ClassNode platformClass, ClassBytesSource game) {
		Set<String> copied = new java.util.HashSet<>();
		if (applicable(platformClass).stream().noneMatch(r -> r.code() == Code.HARVEST || r.code() == Code.PROGRESS)) return copied;
		byte[] data;
		try {
			data = game.read(EVENT_HOOKS);
		} catch (java.io.IOException e) {
			return copied;
		}
		if (data == null) return copied;
		// That method's calls and field reads alone: the class's others aren't parsed.
		new ClassReader(data).accept(new org.objectweb.asm.ClassVisitor(Opcodes.ASM9) {
			@Override
			public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				if (!name.equals(HARVEST_HOOK.name()) || !descriptor.equals(HARVEST_HOOK.desc())) return null;
				return new org.objectweb.asm.MethodVisitor(Opcodes.ASM9) {
					@Override
					public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
						copied.add(owner + ";" + name + descriptor);
					}

					@Override
					public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
						copied.add(owner + ";" + name + ":" + descriptor);
					}
				};
			}
		}, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		return copied;
	}

	/** Whether the class or one of its supertypes declares the member ({@code name(desc)}, {@code name:desc}). */
	private static boolean declares(ClassIndex classes, String owner, String member, boolean field, Set<String> seen) {
		if (owner == null || !seen.add(owner)) return false;
		ClassIndex.ClassInfo info = classes.get(owner);
		if (info == null) return false;
		if ((field ? info.fieldAccess(member) : info.methodAccess(member)) != null) return true;
		if (declares(classes, info.superName(), member, field, seen)) return true;
		for (String itf : info.interfaces()) if (declares(classes, itf, member, field, seen)) return true;
		return false;
	}

	private static Object value(AnnotationNode ann, String name) {
		for (int i = 0; ann.values != null && i + 1 < ann.values.size(); i += 2) {
			if (name.equals(ann.values.get(i))) return ann.values.get(i + 1);
		}
		return null;
	}

	private static byte[] write(ClassNode node) {
		ClassWriter writer = new ClassWriter(0);
		node.accept(writer);
		return writer.toByteArray();
	}

	private static ClassNode read(byte[] data, int flags) {
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, flags);
		return node;
	}
}
