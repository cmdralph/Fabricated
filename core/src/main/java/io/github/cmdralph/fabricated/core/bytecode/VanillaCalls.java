package io.github.cmdralph.fabricated.core.bytecode;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * The second shape of {@link VanillaSeams}' table (SEAM-3): vanilla's calls in a game method that a platform makes
 * through its block state's extension instead, put back as their own steps there, for the blocks that keep the
 * extension's default. Vanilla's {@code destroyBlock} (the server's and the client's) removes the block with
 * {@code level.removeBlock(pos, false)} (the server's, then {@code block.destroy}) or
 * {@code level.setBlock(pos, fluid.createLegacyBlock(), 11)} (the client's); NeoForge 21.1 to 26.3 and Forge 47 to 66 call
 * the state's {@code onDestroyedByPlayer} there (the server through a private {@code removeBlock} of theirs), whose default
 * makes those calls (NeoForge's) or {@code playerWillDestroy} and the {@code setBlock} (Forge's). Where the platform calls
 * it, the rewrite asks whether the block keeps the default ({@code fabricated$keepsDefault}: neither the state's class
 * nor the block's overrides either interface's method, by reflection), and then makes vanilla's calls, which hooks find;
 * otherwise the platform's call, as before. Each operand of the platform's call is evaluated as before, once, in the
 * branch the call is in; vanilla's branch loads again the plain ones it needs. What vanilla doesn't call there (the
 * block's {@code getBlock}, Forge's server reading the state and its {@code playerWillDestroy}, which vanilla makes before
 * the tool is damaged) is called through private helpers, out of hooks' sight, and a value kept goes in a local of type
 * {@code Object}, which a hook capturing a local of its type doesn't count. The method gets the frames its branches need.
 */
final class VanillaCalls {
	private static final String STATE = "net/minecraft/world/level/block/state/BlockState";
	private static final String BLOCK = "net/minecraft/world/level/block/Block";
	private static final String LEVEL = "net/minecraft/world/level/Level";
	private static final String SERVER_LEVEL = "net/minecraft/server/level/ServerLevel";
	private static final String POS = "Lnet/minecraft/core/BlockPos;";
	private static final String PLAYER = "Lnet/minecraft/world/entity/player/Player;";
	private static final String FLUID = "Lnet/minecraft/world/level/material/FluidState;";
	private static final String ITEM = "Lnet/minecraft/world/item/ItemStack;";
	private static final String GAME_MODE = "net/minecraft/server/level/ServerPlayerGameMode";
	private static final String CLIENT_GAME_MODE = "net/minecraft/client/multiplayer/MultiPlayerGameMode";
	private static final String DESTROY_BLOCK = "(" + POS + ")Z";
	private static final String KEEPS_DEFAULT = "fabricated$keepsDefault";

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

	/** The platform's extension a put-back stands in for: the block state's method and the block's, each an interface's default. */
	record Extension(String stateInterface, String blockInterface, String name, String desc) {
		String blockDesc() {
			return "(L" + STATE + ";" + desc.substring(1);
		}

		/** The package of the platform's own classes, which only its game's classes reference. */
		String platformPackage() {
			return stateInterface.substring(0, stateInterface.indexOf('/', stateInterface.indexOf('/') + 1) + 1);
		}
	}

	/** Which of vanilla's code a put-back makes in the platform's call's place. */
	enum Removal {
		/** NeoForge's server: its {@code removeBlock(pos, state, ...)}'s place gets {@code level.removeBlock(pos, false)} and, where it removed the block, {@code block.destroy(level, pos, state)}. */
		SERVER,
		/** Forge's server: as {@link #SERVER}, at its {@code removeBlock(pos, canHarvest)}, the state read at the position first and the default's {@code playerWillDestroy} made out of hooks' sight. */
		FORGE_SERVER,
		/** NeoForge's client: the extension call's place gets {@code level.setBlock(pos, fluid.createLegacyBlock(), 11)}. */
		CLIENT,
		/** Forge's client: as {@link #CLIENT}, after {@code block.playerWillDestroy(level, pos, state, player)}, which Forge's default makes there. */
		FORGE_CLIENT
	}

	/**
	 * A row: the game method, the platform's call there whose place gets vanilla's calls, the extension whose default those
	 * calls stand for, and which calls.
	 */
	record PutBack(String owner, String method, String srgMethod, Member platform, Extension extension, Removal removal) {
		String method(boolean srg) {
			return srg ? srgMethod : method;
		}
	}

	private static final String NEOFORGE_EXT = "net/neoforged/neoforge/common/extensions/";
	private static final String FORGE_EXT = "net/minecraftforge/common/extensions/";
	private static final String DESTROYED = "(L" + LEVEL + ";" + POS + PLAYER;
	private static final Extension NEOFORGE_21_1 = new Extension(NEOFORGE_EXT + "IBlockStateExtension", NEOFORGE_EXT + "IBlockExtension",
			"onDestroyedByPlayer", DESTROYED + "Z" + FLUID + ")Z");
	private static final Extension NEOFORGE = new Extension(NEOFORGE_EXT + "IBlockStateExtension", NEOFORGE_EXT + "IBlockExtension",
			"onDestroyedByPlayer", DESTROYED + ITEM + "Z" + FLUID + ")Z");
	private static final Extension FORGE = new Extension(FORGE_EXT + "IForgeBlockState", FORGE_EXT + "IForgeBlock", "onDestroyedByPlayer",
			DESTROYED + "Z" + FLUID + ")Z");

	/** The table: the platforms' forms of the server's and the client's {@code destroyBlock}, read in their classes. */
	static final List<PutBack> PUT_BACKS = List.of(
			new PutBack(GAME_MODE, "destroyBlock", "m_9280_", new Member(GAME_MODE, "removeBlock", null, "(" + POS + "L" + STATE + ";Z)Z"),
					NEOFORGE_21_1, Removal.SERVER),
			new PutBack(GAME_MODE, "destroyBlock", "m_9280_", new Member(GAME_MODE, "removeBlock", null, "(" + POS + "L" + STATE + ";Z" + ITEM + ")Z"),
					NEOFORGE, Removal.SERVER),
			new PutBack(GAME_MODE, "destroyBlock", "m_9280_", new Member(GAME_MODE, "removeBlock", null, "(" + POS + "Z)Z"), FORGE, Removal.FORGE_SERVER),
			new PutBack(CLIENT_GAME_MODE, "destroyBlock", "m_105267_", new Member(STATE, NEOFORGE_21_1.name(), null, NEOFORGE_21_1.desc()),
					NEOFORGE_21_1, Removal.CLIENT),
			new PutBack(CLIENT_GAME_MODE, "destroyBlock", "m_105267_", new Member(STATE, NEOFORGE.name(), null, NEOFORGE.desc()), NEOFORGE, Removal.CLIENT),
			new PutBack(CLIENT_GAME_MODE, "destroyBlock", "m_105267_", new Member(STATE, FORGE.name(), null, FORGE.desc()), FORGE, Removal.FORGE_CLIENT));

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
	private static final Member PLAYER_FIELD = new Member(GAME_MODE, "player", "f_9245_", "Lnet/minecraft/server/level/ServerPlayer;");

	private VanillaCalls() {
	}

	/** The rows that apply to the class as it is: its method makes the platform's call, and the class is that platform's. */
	static List<PutBack> applicable(ClassNode node) {
		List<PutBack> rows = new ArrayList<>();
		if (!CLASSES.contains(node.name)) return rows;
		boolean srg = VanillaSeams.srg(node);
		for (PutBack row : PUT_BACKS) {
			MethodNode method = holder(node, row, srg);
			if (row.owner().equals(node.name) && method != null && !calls(method, row.platform()).isEmpty() && references(node, row.extension().platformPackage())
					&& (row.removal() != Removal.FORGE_CLIENT || calls(method, WILL_DESTROY.owner(), WILL_DESTROY.name(srg)).isEmpty())) {
				rows.add(row);
			}
		}
		return rows;
	}

	/**
	 * Puts back the class's calls ({@link #applicable}), all of a method's places or none; returns what it put back, as
	 * {@code method: owner;name(desc), ...}. The method gets a marker listing them, which pre-flight reads
	 * ({@link #restoredIn}).
	 */
	static List<String> restore(ClassNode node) {
		List<PutBack> rows = applicable(node);
		List<String> restored = new ArrayList<>();
		if (rows.isEmpty()) return restored;
		boolean srg = VanillaSeams.srg(node);
		// Frames are worked out on a copy with them expanded; the rewritten method is read back with them compressed again.
		ClassNode expanded = read(write(node), ClassReader.EXPAND_FRAMES);
		List<String> rewritten = new ArrayList<>();
		for (PutBack row : rows) {
			MethodNode method = holder(expanded, row, srg);
			List<String> calls = rewrite(expanded, method, row, srg);
			if (calls.isEmpty()) continue;
			addHelpers(expanded, row, srg);
			AnnotationNode marker = new AnnotationNode(VanillaSeams.MARKER);
			marker.values = new ArrayList<>(List.of("calls", calls, "keeps", row.extension().blockInterface() + "." + row.extension().name()));
			if (method.invisibleAnnotations == null) method.invisibleAnnotations = new ArrayList<>();
			method.invisibleAnnotations.add(marker);
			rewritten.add(method.name + method.desc);
			restored.add(method.name + method.desc + ": " + String.join(", ", calls));
		}
		if (rewritten.isEmpty()) return restored;
		ClassNode compressed = read(write(expanded), 0);
		for (MethodNode method : compressed.methods) {
			int at = indexOf(node, method.name, method.desc);
			if (at < 0) node.methods.add(method);
			else if (rewritten.contains(method.name + method.desc)) node.methods.set(at, method);
		}
		return restored;
	}

	/**
	 * Rewrites each place of the method that makes the platform's call; the calls put back, or none where a place isn't in
	 * the shape the row reads (the method is then left as it was).
	 */
	private static List<String> rewrite(ClassNode owner, MethodNode method, PutBack row, boolean srg) {
		List<MethodInsnNode> places = calls(method, row.platform());
		Map<AbstractInsnNode, State> frames = frames(owner, method);
		if (frames == null) return List.of();
		List<Site> sites = new ArrayList<>();
		for (MethodInsnNode place : places) {
			Site site = site(method, place, frames);
			if (site == null || !site.plain(needed(row))) return List.of();
			sites.add(site);
		}
		int temp = method.maxLocals;
		Set<String> putBack = new LinkedHashSet<>();
		for (Site site : sites) {
			Emitter out = new Emitter(owner.name, row, srg, site, temp);
			LabelNode platform = new LabelNode(), end = new LabelNode();
			out.vanilla(platform, end, putBack);
			method.instructions.insertBefore(site.start(), out.code);
			method.instructions.insertBefore(site.start(), platform);
			method.instructions.insertBefore(site.start(), frame(site.locals(), row.removal() == Removal.FORGE_SERVER ? temp : -1, site.under(), false));
			method.instructions.insert(site.call(), frame(site.locals(), -1, site.under(), true));
			method.instructions.insert(site.call(), end);
		}
		if (row.removal() == Removal.FORGE_SERVER) method.maxLocals = temp + 1;
		method.maxStack = Math.max(method.maxStack, sites.stream().mapToInt(s -> slots(s.under())).max().orElse(0) + 7);
		return List.copyOf(putBack);
	}

	/** The platform's call at a place, its operands' instructions, and the frame before them. */
	private record Site(MethodInsnNode call, List<List<AbstractInsnNode>> operands, List<Object> locals, List<Object> under) {
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
	}

	/** The operands vanilla's code at the place loads again (negative from the end). */
	private static int[] needed(PutBack row) {
		return switch (row.removal()) {
			case SERVER -> new int[] {0, 1, 2};
			case FORGE_SERVER -> new int[] {0, 1};
			case CLIENT -> new int[] {0, 1, 2, -1};
			case FORGE_CLIENT -> new int[] {0, 1, 2, 3, -1};
		};
	}

	/**
	 * The place's operands, each the instructions that push it, found by the stack's depth before each instruction; null
	 * where the code there isn't straight (a jump, a label a jump or handler reaches, a store) or leaves something else
	 * on the stack.
	 */
	private static Site site(MethodNode method, MethodInsnNode call, Map<AbstractInsnNode, State> frames) {
		State atCall = frames.get(call);
		if (atCall == null) return null;
		Type[] args = Type.getArgumentTypes(call.desc);
		int[] sizes = new int[args.length + 1];
		sizes[0] = 1;
		for (int i = 0; i < args.length; i++) sizes[i + 1] = args[i].getSize();
		int total = 0;
		for (int size : sizes) total += size;
		int base = atCall.stack().size() - total;
		if (base < 0) return null;
		Set<LabelNode> targets = targets(method);
		List<List<AbstractInsnNode>> operands = new ArrayList<>();
		for (int i = 0; i <= args.length; i++) operands.add(new ArrayList<>());
		int depth = atCall.stack().size();
		AbstractInsnNode insn = call.getPrevious();
		for (int operand = args.length; operand >= 0; operand--) {
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
		return new Site(call, operands, atStart.locals(), under);
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
			Removal removal = row.removal();
			if (removal == Removal.FORGE_SERVER) {
				load(0);
				load(1);
				code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, "fabricated$stateAt", "(L" + owner + ";" + POS + ")Ljava/lang/Object;", false));
				code.add(new VarInsnNode(Opcodes.ASTORE, temp));
			}
			state();
			code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, KEEPS_DEFAULT, "(L" + STATE + ";)Z", false));
			code.add(new JumpInsnNode(Opcodes.IFEQ, platform));
			if (removal == Removal.SERVER || removal == Removal.FORGE_SERVER) {
				if (removal == Removal.FORGE_SERVER) {
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
				if (removal == Removal.FORGE_CLIENT) {
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

		private void load(int operand) {
			for (AbstractInsnNode insn : site.operands().get(operand)) {
				if (insn.getOpcode() >= 0) code.add(insn.clone(Map.of()));
			}
		}

		private void state() {
			if (row.removal() == Removal.FORGE_SERVER) {
				code.add(new VarInsnNode(Opcodes.ALOAD, temp));
				code.add(new TypeInsnNode(Opcodes.CHECKCAST, STATE));
			} else {
				load(row.removal() == Removal.SERVER ? 2 : 0);
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

	/** The private helpers vanilla's branch calls: the check, the block's {@code getBlock}, and Forge's server's reads. */
	private static void addHelpers(ClassNode node, PutBack row, boolean srg) {
		if (indexOf(node, KEEPS_DEFAULT, "(L" + STATE + ";)Z") >= 0) return;
		node.methods.add(keepsDefault(row.extension(), srg));
		node.methods.add(helper("fabricated$block", "(L" + STATE + ";)L" + BLOCK + ";", Opcodes.ARETURN,
				new VarInsnNode(Opcodes.ALOAD, 0), invoke(GET_BLOCK, srg)));
		if (row.removal() != Removal.FORGE_SERVER) return;
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
	 * {@code private static boolean fabricated$keepsDefault(BlockState state)}: whether the extension's methods the state's
	 * class and its block's class have are the interfaces' defaults; false where reflection can't tell (a class it can't
	 * load in a signature), so the platform's call is made.
	 */
	private static MethodNode keepsDefault(Extension extension, boolean srg) {
		MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, KEEPS_DEFAULT, "(L" + STATE + ";)Z", null, null);
		LabelNode start = new LabelNode(), end = new LabelNode(), no = new LabelNode(), failed = new LabelNode();
		InsnList code = method.instructions;
		code.add(start);
		declaredBy(code, extension.name(), extension.desc(), extension.stateInterface(), false, srg, no);
		declaredBy(code, extension.name(), extension.blockDesc(), extension.blockInterface(), true, srg, no);
		code.add(new InsnNode(Opcodes.ICONST_1));
		code.add(end);
		code.add(new InsnNode(Opcodes.IRETURN));
		Object[] locals = {STATE};
		code.add(no);
		code.add(new FrameNode(Opcodes.F_NEW, 1, locals, 0, new Object[0]));
		code.add(new InsnNode(Opcodes.ICONST_0));
		code.add(new InsnNode(Opcodes.IRETURN));
		code.add(failed);
		code.add(new FrameNode(Opcodes.F_NEW, 1, locals, 1, new Object[] {"java/lang/Throwable"}));
		code.add(new InsnNode(Opcodes.POP));
		code.add(new InsnNode(Opcodes.ICONST_0));
		code.add(new InsnNode(Opcodes.IRETURN));
		method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, failed, "java/lang/Exception"));
		method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, failed, "java/lang/LinkageError"));
		method.maxStack = 7;
		method.maxLocals = 1;
		return method;
	}

	/** {@code (block ? state.getBlock() : state).getClass().getMethod(name, params).getDeclaringClass() == iface}, else to {@code no}. */
	private static void declaredBy(InsnList code, String name, String desc, String iface, boolean block, boolean srg, LabelNode no) {
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		if (block) code.add(invoke(GET_BLOCK, srg));
		code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false));
		code.add(new LdcInsnNode(name));
		Type[] params = Type.getArgumentTypes(desc);
		code.add(new IntInsnNode(Opcodes.BIPUSH, params.length));
		code.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Class"));
		for (int i = 0; i < params.length; i++) {
			code.add(new InsnNode(Opcodes.DUP));
			code.add(new IntInsnNode(Opcodes.BIPUSH, i));
			if (params[i].getSort() == Type.BOOLEAN) code.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/Boolean", "TYPE", "Ljava/lang/Class;"));
			else code.add(new LdcInsnNode(params[i]));
			code.add(new InsnNode(Opcodes.AASTORE));
		}
		code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false));
		code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/reflect/Method", "getDeclaringClass", "()Ljava/lang/Class;", false));
		code.add(new LdcInsnNode(Type.getObjectType(iface)));
		code.add(new JumpInsnNode(Opcodes.IF_ACMPNE, no));
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
		int at = indexOf(node, row.method(srg), DESTROY_BLOCK);
		return at < 0 ? null : node.methods.get(at);
	}

	private static int indexOf(ClassNode node, String name, String desc) {
		for (int i = 0; i < node.methods.size(); i++) {
			MethodNode m = node.methods.get(i);
			if (m.name.equals(name) && m.desc.equals(desc)) return i;
		}
		return -1;
	}

	private static List<MethodInsnNode> calls(MethodNode method, Member member) {
		List<MethodInsnNode> found = new ArrayList<>();
		if (method.instructions == null) return found;
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof MethodInsnNode call && call.getOpcode() != Opcodes.INVOKESTATIC && call.owner.equals(member.owner())
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
							method.name + method.desc, keeps));
				}
			}
		}
		return found;
	}

	/**
	 * What putting the class's calls back changes ({@code restored}: a copy, put back), for {@link SeamConflicts}: the
	 * platform's calls they stand in for
	 * ({@code moved}), the methods holding them, the platform's own methods no longer reached for the blocks keeping the
	 * default ({@code bypassed}: its private {@code removeBlock}), and the extensions' defaults not called for those blocks
	 * ({@code defaults}, interface to method).
	 */
	static void changes(ClassNode node, ClassNode restored, Set<String> moved, Set<String> holders, Set<String> bypassed, Map<String, String> defaults) {
		boolean srg = VanillaSeams.srg(node);
		for (PutBack row : applicable(node)) {
			MethodNode method = holder(restored, row, srg);
			if (method == null || method.invisibleAnnotations == null || method.invisibleAnnotations.stream().noneMatch(a -> VanillaSeams.MARKER.equals(a.desc))) continue;
			Member platform = row.platform();
			moved.add(platform.key(false));
			holders.add(row.method(srg) + DESTROY_BLOCK);
			if (platform.owner().equals(node.name)) bypassed.add(platform.name() + platform.desc());
			defaults.put(row.extension().stateInterface(), row.extension().name());
			defaults.put(row.extension().blockInterface(), row.extension().name());
		}
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
					if (key != null) added.add(key);
					if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof Type type && type.getSort() == Type.OBJECT) added.add(type.getInternalName());
				}
				continue;
			}
			int at = indexOf(original, method.name, method.desc);
			if (at < 0) continue;
			Map<String, Integer> before = counts(original.methods.get(at)), after = counts(method);
			// The class's own methods it calls more often are those it declares again or adds (SEAM-1's, its helpers).
			after.forEach((key, n) -> {
				if (n > before.getOrDefault(key, 0) && !(key.startsWith(restored.name + ";") && key.contains("("))) added.add(key);
			});
		}
		return added;
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
	 * inherit (the JDK's taken as there): pre-flight puts nothing back that would fail as it runs.
	 */
	static boolean resolves(Set<String> added, ClassBytesSource game) {
		for (String key : added) {
			int semicolon = key.indexOf(';');
			if (semicolon < 0) {
				if (!key.startsWith("java/") && game.readNode(key, ClassReader.SKIP_CODE) == null) return false;
				continue;
			}
			String owner = key.substring(0, semicolon), member = key.substring(semicolon + 1);
			if (owner.startsWith("java/")) continue;
			int paren = member.indexOf('('), colon = member.indexOf(':');
			boolean field = paren < 0;
			String name = member.substring(0, field ? colon : paren), desc = member.substring(field ? colon + 1 : paren);
			if (!declares(game, owner, name, desc, field, new LinkedHashSet<>())) return false;
		}
		return true;
	}

	private static boolean declares(ClassBytesSource game, String owner, String name, String desc, boolean field, Set<String> seen) {
		if (owner == null || !seen.add(owner)) return false;
		ClassNode node = game.readNode(owner, ClassReader.SKIP_CODE);
		if (node == null) return false;
		boolean has = field ? node.fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals(desc))
				: node.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(desc));
		if (has) return true;
		if (declares(game, node.superName, name, desc, field, seen)) return true;
		for (String itf : node.interfaces) if (declares(game, itf, name, desc, field, seen)) return true;
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
