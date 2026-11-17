package io.github.cmdralph.fabricated.core.bytecode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.github.cmdralph.fabricated.core.testutil.TestSources;

/**
 * SEAM-13: the shovel's path and campfire dousing, which NeoForge and Forge make through the block state's
 * {@code getToolModifiedState} of their actions, put back for the blocks whose classes keep the defaults, after the
 * platform's tool modification event and the held item's check of the action as the defaults make them; and Forge's
 * empty block check above the path, {@code isEmptyBlock}, put back as {@code getBlockState(pos).isAir()} for the levels
 * keeping it. Each fixture is the platform's {@code ShovelItem.useOn} in its form (NeoForge's, Forge's, Forge 47's under
 * SRG's names) against stand-ins of the game whose calls log what they do. The class rewritten does what the platform's
 * did for every block, held item, event listener and level; hooks of vanilla's calls put back, as FrozenLib's shovel
 * behaviours hook them, run where the block keeps the defaults, and only there.
 */
public class ShovelPathsTest {
	private static final String SHOVEL = "net/minecraft/world/item/ShovelItem";

	/** A platform's form: its extensions, actions and event hooks, and the names its game runs with. */
	enum Form {
		NEOFORGE(false, false), FORGE(true, false), FORGE_47(true, true);

		final boolean forge, srg;

		Form(boolean forge, boolean srg) {
			this.forge = forge;
			this.srg = srg;
		}

		String ext() {
			return forge ? "net.minecraftforge.common.extensions" : "net.neoforged.neoforge.common.extensions";
		}

		String action() {
			return forge ? "net.minecraftforge.common.ToolAction" : "net.neoforged.neoforge.common.ItemAbility";
		}

		String actions() {
			return forge ? "net.minecraftforge.common.ToolActions" : "net.neoforged.neoforge.common.ItemAbilities";
		}

		String events() {
			return forge ? "net.minecraftforge.event.ForgeEventFactory" : "net.neoforged.neoforge.event.EventHooks";
		}

		String stateExt() {
			return forge ? "IForgeBlockState" : "IBlockStateExtension";
		}

		String blockExt() {
			return forge ? "IForgeBlock" : "IBlockExtension";
		}

		String stackExt() {
			return forge ? "IForgeItemStack" : "IItemStackExtension";
		}
	}

	/** The members Forge 47's game names by SRG's names, as {@code $name$} in the fixtures. */
	private static final Map<String, String> SRG = new LinkedHashMap<>();
	static {
		for (String[] names : new String[][] {{"useOn", "m_6225_"}, {"getBlock", "m_60734_"}, {"isAir", "m_60795_"}, {"getBlockState", "m_8055_"},
				{"isEmptyBlock", "m_46859_"}, {"FLATTENABLES", "f_43110_"}, {"getItemInHand", "m_43722_"}, {"getPlayer", "m_43723_"}, {"getLevel", "m_43725_"},
				{"getClickedPos", "m_8083_"}}) {
			SRG.put("$" + names[0] + "$", names[1]);
		}
	}

	/** The game's stand-ins and the platform's classes in the form. */
	static Map<String, String> sources(Form form) {
		Map<String, String> sources = new HashMap<>();
		String ext = form.ext(), action = form.action(), actions = form.actions();
		String actionType = action.substring(action.lastIndexOf('.') + 1);
		put(sources, action, "public final class " + actionType + " { final String name; public " + actionType + "(String name) { this.name = name; }"
				+ " public String toString() { return name; } }");
		put(sources, actions, "public final class " + actions.substring(actions.lastIndexOf('.') + 1) + " {"
				+ " public static final " + action + " SHOVEL_FLATTEN = new " + action + "(\"flatten\"), SHOVEL_DOUSE = new " + action + "(\"douse\"),"
				+ " AXE_STRIP = new " + action + "(\"strip\"); }");
		// The platform's tool modification event: a listener answering another state, or cancelling it.
		put(sources, form.events(), """
				public class %s {
					public static net.minecraft.world.level.block.state.BlockState onToolUse(net.minecraft.world.level.block.state.BlockState state,
							net.minecraft.world.item.context.UseOnContext context, %s action, boolean simulate) {
						com.example.Hook.LOG.add("event " + action + " " + state);
						return com.example.Hook.listener.equals("changes") ? net.minecraft.world.level.block.Blocks.TUFF.defaultBlockState()
								: com.example.Hook.listener.equals("cancels") ? null : state;
					}
				}""".formatted(form.events().substring(form.events().lastIndexOf('.') + 1), action));
		// The defaults: the state's posts the event and asks the block, whose default asks the held item, then looks the path up
		// or douses the campfire, as NeoForge's and Forge's do.
		put(sources, ext + "." + form.stateExt(), """
				public interface %s {
					default net.minecraft.world.level.block.state.BlockState getToolModifiedState(net.minecraft.world.item.context.UseOnContext context, %s action,
							boolean simulate) {
						net.minecraft.world.level.block.state.BlockState self = (net.minecraft.world.level.block.state.BlockState) this;
						net.minecraft.world.level.block.state.BlockState eventState = %s.onToolUse(self, context, action, simulate);
						return eventState != self ? eventState : self.$getBlock$().getToolModifiedState(self, context, action, simulate);
					}
				}""".formatted(form.stateExt(), action, form.events()));
		put(sources, ext + "." + form.blockExt(), """
				public interface %s {
					default net.minecraft.world.level.block.state.BlockState getToolModifiedState(net.minecraft.world.level.block.state.BlockState state,
							net.minecraft.world.item.context.UseOnContext context, %s action, boolean simulate) {
						if (!context.$getItemInHand$().canPerformAction(action)) return null;
						if (action == %s.SHOVEL_FLATTEN) return net.minecraft.world.item.ShovelItem.getShovelPathingState(state);
						if (action == %s.SHOVEL_DOUSE && state.$getBlock$() instanceof net.minecraft.world.level.block.CampfireBlock
								&& state.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)) {
							if (!simulate) net.minecraft.world.level.block.CampfireBlock.dowse(context.$getPlayer$(), context.$getLevel$(), context.$getClickedPos$(), state);
							return state.setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false);
						}
						return null;
					}
				}""".formatted(form.blockExt(), action, actions, actions));
		put(sources, ext + "." + form.stackExt(), """
				public interface %s {
					default boolean canPerformAction(%s action) {
						net.minecraft.world.item.ItemStack self = (net.minecraft.world.item.ItemStack) this;
						return self.item.canPerformAction(self, action);
					}
				}""".formatted(form.stackExt(), action));
		put(sources, ext + ".ItemExtension", "public interface ItemExtension { default boolean canPerformAction(net.minecraft.world.item.ItemStack stack, " + action
				+ " action) { return false; } }");
		put(sources, "net.minecraft.world.item.Item", "public class Item implements " + ext + ".ItemExtension { final String name; public Item(String name) {"
				+ " this.name = name; } public String toString() { return name; } }");
		put(sources, "net.minecraft.world.item.ItemStack", "public final class ItemStack implements " + ext + "." + form.stackExt() + " { public final Item item;"
				+ " public ItemStack(Item item) { this.item = item; } }");
		put(sources, "net.minecraft.world.entity.Entity", "public class Entity { }");
		put(sources, "net.minecraft.world.entity.player.Player", "public class Player extends net.minecraft.world.entity.Entity { }");
		put(sources, "net.minecraft.core.BlockPos", "public record BlockPos(int y) { public BlockPos above() { return new BlockPos(y + 1); } }");
		put(sources, "net.minecraft.world.level.block.state.properties.Property", "public class Property<T extends Comparable<T>> { }");
		put(sources, "net.minecraft.world.level.block.state.properties.BooleanProperty", "public class BooleanProperty extends Property<Boolean> { }");
		put(sources, "net.minecraft.world.level.block.state.StateHolder", """
				public abstract class StateHolder<S> {
					public abstract boolean lit();
					protected abstract S with(boolean lit);
					@SuppressWarnings("unchecked")
					public <T extends Comparable<T>> T getValue(net.minecraft.world.level.block.state.properties.Property<T> property) { return (T) Boolean.valueOf(lit()); }
					public <T extends Comparable<T>, V extends T> S setValue(net.minecraft.world.level.block.state.properties.Property<T> property, V value) {
						return with((Boolean) value);
					}
				}""");
		put(sources, "net.minecraft.world.level.block.state.BlockState", """
				public final class BlockState extends StateHolder<BlockState> implements %s.%s {
					final net.minecraft.world.level.block.Block block;
					final boolean lit;
					public BlockState(net.minecraft.world.level.block.Block block, boolean lit) { this.block = block; this.lit = lit; }
					public net.minecraft.world.level.block.Block $getBlock$() { return block; }
					public boolean $isAir$() { return block == net.minecraft.world.level.block.Blocks.AIR; }
					public boolean lit() { return lit; }
					protected BlockState with(boolean lit) { return new BlockState(block, lit); }
					public String toString() { return block + (lit && block instanceof net.minecraft.world.level.block.CampfireBlock ? "[lit]" : ""); }
				}""".formatted(ext, form.stateExt()));
		put(sources, "net.minecraft.world.level.block.Block", "public class Block implements " + ext + "." + form.blockExt() + " { final String name;"
				+ " public Block(String name) { this.name = name; } public net.minecraft.world.level.block.state.BlockState defaultBlockState() {"
				+ " return new net.minecraft.world.level.block.state.BlockState(this, true); } public String toString() { return name; } }");
		put(sources, "net.minecraft.world.level.block.CampfireBlock", """
				public class CampfireBlock extends Block {
					public static final net.minecraft.world.level.block.state.properties.BooleanProperty LIT = new net.minecraft.world.level.block.state.properties.BooleanProperty();
					public CampfireBlock(String name) { super(name); }
					public static void dowse(net.minecraft.world.entity.Entity entity, net.minecraft.world.level.LevelAccessor level, net.minecraft.core.BlockPos pos,
							net.minecraft.world.level.block.state.BlockState state) {
						com.example.Hook.LOG.add("doused " + state + " at " + pos.y());
					}
				}""");
		put(sources, "net.minecraft.world.level.block.Blocks", """
				public class Blocks {
					public static final Block AIR = new Block("air"), GRASS = new Block("grass"), PATH = new Block("path"), STONE = new Block("stone"),
							TUFF = new Block("tuff"), CALCITE = new Block("calcite");
					public static final Block CAMPFIRE = new CampfireBlock("campfire");
					public static final Block OWN = new com.example.OwnBlock();
				}""");
		put(sources, "net.minecraft.world.level.LevelAccessor", "public interface LevelAccessor { }");
		put(sources, "net.minecraft.world.level.LevelReader", """
				public interface LevelReader {
					net.minecraft.world.level.block.state.BlockState $getBlockState$(net.minecraft.core.BlockPos pos);
					default boolean $isEmptyBlock$(net.minecraft.core.BlockPos pos) { return this.$getBlockState$(pos).$isAir$(); }
				}""");
		put(sources, "net.minecraft.world.level.Level", """
				public class Level implements LevelReader, LevelAccessor {
					final java.util.Map<Integer, net.minecraft.world.level.block.state.BlockState> blocks = new java.util.HashMap<>();
					public net.minecraft.world.level.block.state.BlockState $getBlockState$(net.minecraft.core.BlockPos pos) {
						return blocks.getOrDefault(pos.y(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
					}
					public void set(int y, net.minecraft.world.level.block.state.BlockState state) { blocks.put(y, state); }
					public boolean isClientSide() { return false; }
					public void levelEvent(net.minecraft.world.entity.player.Player player, int event, net.minecraft.core.BlockPos pos, int data) {
						com.example.Hook.LOG.add("level event " + event);
					}
				}""");
		put(sources, "net.minecraft.world.item.context.UseOnContext", """
				public class UseOnContext {
					final net.minecraft.world.level.Level level; final net.minecraft.core.BlockPos pos; final net.minecraft.world.item.ItemStack stack;
					public UseOnContext(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, net.minecraft.world.item.ItemStack stack) {
						this.level = level; this.pos = pos; this.stack = stack;
					}
					public net.minecraft.world.level.Level $getLevel$() { return level; }
					public net.minecraft.core.BlockPos $getClickedPos$() { return pos; }
					public net.minecraft.world.entity.player.Player $getPlayer$() { return null; }
					public net.minecraft.world.item.ItemStack $getItemInHand$() { return stack; }
				}""");
		// The platform's shovel: its flatten action, then (NeoForge) its douse action, whose answer it stores and checks, or
		// (Forge) vanilla's dousing; Forge asks the level's isEmptyBlock above the path.
		String above = form.forge ? "level.$isEmptyBlock$(pos.above())" : "level.$getBlockState$(pos.above()).$isAir$()";
		String douse = form.forge ? """
				} else if (blockState.$getBlock$() instanceof CampfireBlock && blockState.getValue(CampfireBlock.LIT)) {
					if (!level.isClientSide()) level.levelEvent(null, 1009, pos, 0);
					CampfireBlock.dowse(context.$getPlayer$(), level, pos, blockState);
					updatedState = blockState.setValue(CampfireBlock.LIT, false);
				}""" : """
				} else if ((updatedState = blockState.getToolModifiedState(context, %s.SHOVEL_DOUSE, false)) != null) {
					if (!level.isClientSide()) level.levelEvent(null, 1009, pos, 0);
				}""".formatted(actions);
		put(sources, "net.minecraft.world.item.ShovelItem", """
				import net.minecraft.core.BlockPos;
				import net.minecraft.world.item.context.UseOnContext;
				import net.minecraft.world.level.Level;
				import net.minecraft.world.level.block.Block;
				import net.minecraft.world.level.block.Blocks;
				import net.minecraft.world.level.block.CampfireBlock;
				import net.minecraft.world.level.block.state.BlockState;
				public class ShovelItem extends Item {
					protected static final java.util.Map<Block, BlockState> $FLATTENABLES$ = java.util.Map.of(Blocks.GRASS, Blocks.PATH.defaultBlockState(),
							Blocks.CALCITE, Blocks.PATH.defaultBlockState());
					public ShovelItem(String name) { super(name); }
					public static BlockState getShovelPathingState(BlockState state) { return $FLATTENABLES$.get(state.$getBlock$()); }
					@Override public boolean canPerformAction(ItemStack stack, %s action) { return action == %s.SHOVEL_FLATTEN || action == %s.SHOVEL_DOUSE; }
					public String $useOn$(UseOnContext context) {
						Level level = context.$getLevel$();
						BlockPos pos = context.$getClickedPos$();
						BlockState blockState = level.$getBlockState$(pos);
						BlockState newState = blockState.getToolModifiedState(context, %s.SHOVEL_FLATTEN, false);
						BlockState updatedState = null;
						if (newState != null && %s) {
							com.example.Hook.LOG.add("flattened");
							updatedState = newState;
						%s
						if (updatedState != null) {
							level.set(pos.y(), updatedState);
							return "set " + updatedState;
						}
						return "pass";
					}
				}""".formatted(action, actions, actions, actions, above, douse));
		// A platform mod's block answering its tool modifications itself, and a level answering its empty block check itself.
		put(sources, "com.example.OwnBlock", """
				public class OwnBlock extends net.minecraft.world.level.block.Block {
					public OwnBlock() { super("own"); }
					@Override public net.minecraft.world.level.block.state.BlockState getToolModifiedState(net.minecraft.world.level.block.state.BlockState state,
							net.minecraft.world.item.context.UseOnContext context, %s action, boolean simulate) {
						Hook.LOG.add("own block asked " + action);
						return net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
					}
				}""".formatted(action));
		put(sources, "com.example.OwnLevel", """
				public class OwnLevel extends net.minecraft.world.level.Level {
					@Override public boolean $isEmptyBlock$(net.minecraft.core.BlockPos pos) { Hook.LOG.add("own level asked"); return true; }
				}""");
		// The hooks, as FrozenLib's shovel behaviours make them: a behaviour of its own for calcite, where the hook of the
		// path's air check answers no and that of the campfire check answers air's block, the hook at the first null check
		// having set the path's state.
		put(sources, "com.example.Hook", """
				public class Hook {
					public static final java.util.List<String> LOG = new java.util.ArrayList<>();
					public static String listener = "none";
					public static boolean custom;
					private static boolean behaviour;
					public static net.minecraft.world.level.block.state.BlockState start(net.minecraft.world.level.block.state.BlockState state) {
						behaviour = custom && state.$getBlock$() == net.minecraft.world.level.block.Blocks.CALCITE;
						return null;
					}
					public static net.minecraft.world.level.block.state.BlockState preset(net.minecraft.world.level.block.state.BlockState updated) {
						if (!behaviour) return updated;
						LOG.add("behaviour ran");
						return net.minecraft.world.level.block.Blocks.TUFF.defaultBlockState();
					}
					public static boolean air(boolean vanilla) { LOG.add("air hooked"); return !behaviour && vanilla; }
					public static net.minecraft.world.level.block.Block block(net.minecraft.world.level.block.Block vanilla) {
						LOG.add("block hooked " + vanilla);
						return behaviour ? net.minecraft.world.level.block.Blocks.AIR : vanilla;
					}
				}""");
		Map<String, String> names = form.srg ? SRG : Map.of();
		sources.replaceAll((file, text) -> {
			for (String key : SRG.keySet()) text = text.replace(key, names.getOrDefault(key, key.substring(1, key.length() - 1)));
			return text;
		});
		return sources;
	}

	private static void put(Map<String, String> sources, String className, String body) {
		int dot = className.lastIndexOf('.');
		sources.put(className.replace('.', '/') + ".java", "package " + className.substring(0, dot) + ";\n" + body);
	}

	private static Map<String, byte[]> platform(Path dir, Form form) throws Exception {
		return TestSources.compileWithLocals(dir, sources(form));
	}

	/** The classes with the shovel put back, as it loads. */
	private static Map<String, byte[]> restored(Map<String, byte[]> platform, Form form) {
		Map<String, byte[]> restored = new HashMap<>(platform);
		ClassNode node = node(platform.get(SHOVEL));
		VanillaSeams.Restoring done = VanillaSeams.restoreWhole(node);
		assertNull(done.failure());
		assertTrue(!done.restored().isEmpty(), form.name());
		restored.put(SHOVEL, write(node));
		return restored;
	}

	private static final List<String> BLOCKS = List.of("GRASS", "CALCITE", "STONE", "CAMPFIRE", "UNLIT", "OWN");
	private static final List<String> LISTENERS = List.of("none", "changes", "cancels");

	@Test
	void theShovelDoesWhatThePlatformsDidForEveryBlockItemListenerAndLevel(@TempDir Path dir) throws Exception {
		for (Form form : Form.values()) {
			Map<String, byte[]> platform = platform(dir.resolve(form.name()), form), restored = restored(platform, form);
			for (String block : BLOCKS) {
				for (String listener : LISTENERS) {
					for (boolean shovel : List.of(true, false)) {
						for (boolean ownLevel : List.of(false, true)) {
							for (boolean covered : List.of(false, true)) {
								String what = form + " " + block + " " + listener + (shovel ? " shovel" : " stick") + (ownLevel ? " own level" : "") + (covered ? " covered" : "");
								assertEquals(use(platform, block, listener, shovel, ownLevel, covered), use(restored, block, listener, shovel, ownLevel, covered), what);
							}
						}
					}
				}
			}
			// What was put back, for pre-flight: the path's lookup, and NeoForge's dousing or Forge's air check.
			List<String> seams = VanillaSeams.restoredIn(node(restored.get(SHOVEL))).stream().map(r -> r.owner().substring(r.owner().lastIndexOf('/') + 1) + "."
					+ r.name() + " " + r.finding()).distinct().toList();
			String getBlock = form.srg ? "m_60734_" : "getBlock";
			List<String> expected = new ArrayList<>(List.of("BlockState." + getBlock + " SEAM-13", "Map.get SEAM-13"));
			if (form.forge) {
				expected.addAll(List.of("Level." + (form.srg ? "m_8055_" : "getBlockState") + " SEAM-13", "BlockState." + (form.srg ? "m_60795_" : "isAir") + " SEAM-13"));
			} else {
				expected.addAll(List.of("BlockState.getValue SEAM-13", "Boolean.booleanValue SEAM-13", "UseOnContext.getPlayer SEAM-13", "CampfireBlock.dowse SEAM-13",
						"Boolean.valueOf SEAM-13", "BlockState.setValue SEAM-13"));
			}
			assertEquals(expected.stream().sorted().toList(), seams.stream().sorted().toList(), form.name());
			assertEquals("SEAM-13", VanillaSeams.findings(SHOVEL));
			// Vanilla's calls, as FrozenLib's hooks count them: the path's and the campfire's getBlock, the air check, one null check before.
			MethodNode useOn = useOn(node(restored.get(SHOVEL)), form);
			assertEquals(2, calls(useOn, getBlock), form.name());
			assertEquals(1, calls(useOn, form.srg ? "m_60795_" : "isAir"), form.name());
		}
	}

	/**
	 * FrozenLib's shovel behaviours: a hook of the first null check sets the path's state for a block of its own, the hook of
	 * the air check above stops the path and the hook of the campfire check (vanilla's second {@code getBlock}) stops the
	 * dousing. Once put back, each applies, and the behaviour's state is set; a block of the platform's kind answering its
	 * tool modifications itself is asked as the platform has it, the hooks of vanilla's calls not running for it.
	 */
	@Test
	void hooksOfVanillasCallsApplyAndRunForTheBlocksKeepingTheDefaults(@TempDir Path dir) throws Exception {
		for (Form form : Form.values()) {
			Map<String, byte[]> platform = platform(dir.resolve(form.name()), form);
			// As the platform has it, the shovel makes one of vanilla's two getBlock calls (Forge's campfire check) or none, and
			// Forge no air check: the hooks of the second and of the air check find nothing.
			MethodNode before = useOn(node(platform.get(SHOVEL)), form);
			assertEquals(form.forge ? 1 : 0, calls(before, form.srg ? "m_60734_" : "getBlock"), form.name());
			assertEquals(form.forge ? 0 : 1, calls(before, form.srg ? "m_60795_" : "isAir"), form.name());
			Map<String, byte[]> hooked = hooked(restored(platform, form), form);
			List<String> calcite = use(hooked, "CALCITE", "none", true, false, false, true);
			assertTrue(calcite.contains("behaviour ran") && calcite.get(calcite.size() - 1).equals("set tuff"), form + " " + calcite);
			assertTrue(!calcite.contains("flattened") && calcite.stream().noneMatch(l -> l.startsWith("level event") || l.startsWith("doused")), form + " " + calcite);
			// Without the behaviour, the hooks pass vanilla's answers on: grass becomes a path, a lit campfire is doused.
			List<String> grass = use(hooked, "GRASS", "none", true, false, false, false);
			assertEquals("set path", grass.get(grass.size() - 1), form + " " + grass);
			assertEquals(0, grass.stream().filter(l -> l.startsWith("block hooked")).count(), form + " " + grass);
			List<String> campfire = use(hooked, "CAMPFIRE", "none", true, false, false, false);
			assertEquals("set campfire", campfire.get(campfire.size() - 1), form + " " + campfire);
			assertEquals(1, campfire.stream().filter(l -> l.startsWith("block hooked")).count(), form + " " + campfire);
			// The platform's block, and an event's own answer, are the platform's: vanilla's lookup isn't made for them.
			List<String> own = use(hooked, "OWN", "none", true, false, false, false);
			assertTrue(own.contains("own block asked " + "flatten") && own.stream().noneMatch(l -> l.startsWith("block hooked")), form + " " + own);
			List<String> changed = use(hooked, "GRASS", "changes", true, false, false, false);
			assertTrue(changed.stream().noneMatch(l -> l.startsWith("block hooked")) && changed.get(changed.size() - 1).equals("set tuff"), form + " " + changed);
			// Forge's air check: a level overriding isEmptyBlock answers it itself, without the hook.
			if (form.forge) {
				List<String> ownLevel = use(hooked, "GRASS", "none", true, true, false, false);
				assertTrue(ownLevel.contains("own level asked") && !ownLevel.contains("air hooked"), form + " " + ownLevel);
			}
		}
	}

	private static List<String> use(Map<String, byte[]> classes, String block, String listener, boolean shovel, boolean ownLevel, boolean covered) throws Exception {
		return use(classes, block, listener, shovel, ownLevel, covered, false);
	}

	/**
	 * The shovel used on the block at height 0 (a lit campfire for CAMPFIRE, an unlit one for UNLIT), air above it or
	 * stone, by a shovel or a stick, with the event's listener, in a level or a mod's level: what it logs and answers.
	 */
	@SuppressWarnings("unchecked")
	private static List<String> use(Map<String, byte[]> classes, String block, String listener, boolean shovel, boolean ownLevel, boolean covered, boolean custom)
			throws Exception {
		ClassLoader loader = loader(classes);
		Class<?> hook = loader.loadClass("com.example.Hook");
		hook.getField("listener").set(null, listener);
		hook.getField("custom").set(null, custom);
		Class<?> blocks = loader.loadClass("net.minecraft.world.level.block.Blocks"), blockClass = loader.loadClass("net.minecraft.world.level.block.Block");
		Class<?> stateClass = loader.loadClass("net.minecraft.world.level.block.state.BlockState");
		Object state = stateClass.getConstructor(blockClass, boolean.class).newInstance(blocks.getField(block.equals("UNLIT") ? "CAMPFIRE" : block).get(null),
				!block.equals("UNLIT"));
		Object level = loader.loadClass(ownLevel ? "com.example.OwnLevel" : "net.minecraft.world.level.Level").getConstructor().newInstance();
		java.lang.reflect.Method set = loader.loadClass("net.minecraft.world.level.Level").getMethod("set", int.class, stateClass);
		set.invoke(level, 0, state);
		if (covered) set.invoke(level, 1, blockClass.getMethod("defaultBlockState").invoke(blocks.getField("STONE").get(null)));
		Class<?> itemClass = loader.loadClass("net.minecraft.world.item.Item");
		Object item = shovel ? loader.loadClass(SHOVEL.replace('/', '.')).getConstructor(String.class).newInstance("shovel")
				: itemClass.getConstructor(String.class).newInstance("stick");
		Class<?> stackClass = loader.loadClass("net.minecraft.world.item.ItemStack");
		Object stack = stackClass.getConstructor(itemClass).newInstance(item);
		Object pos = loader.loadClass("net.minecraft.core.BlockPos").getConstructor(int.class).newInstance(0);
		Class<?> contextClass = loader.loadClass("net.minecraft.world.item.context.UseOnContext");
		Object context = contextClass.getConstructor(loader.loadClass("net.minecraft.world.level.Level"), pos.getClass(), stackClass).newInstance(level, pos, stack);
		// The shovel's useOn, called on a shovel whatever the hand holds, as a mod's tool may call it.
		Object shovelItem = loader.loadClass(SHOVEL.replace('/', '.')).getConstructor(String.class).newInstance("shovel");
		Object answer = null;
		for (java.lang.reflect.Method m : shovelItem.getClass().getDeclaredMethods()) {
			if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == contextClass && !m.getName().startsWith("fabricated$")) answer = m.invoke(shovelItem, context);
		}
		List<String> log = new ArrayList<>((List<String>) hook.getField("LOG").get(null));
		log.add(String.valueOf(answer));
		return log;
	}

	/**
	 * The classes with FrozenLib's hooks of the shovel woven in, as Mixin would weave them: at its start the behaviour is
	 * decided; at the first null check, the state to set is preset; after the air check and after vanilla's second
	 * {@code getBlock}, each answer passes the hook. Each finds its point as in vanilla's code, or the test fails.
	 */
	private static Map<String, byte[]> hooked(Map<String, byte[]> classes, Form form) {
		Map<String, byte[]> hooked = new HashMap<>(classes);
		ClassNode node = node(classes.get(SHOVEL));
		MethodNode useOn = useOn(node, form);
		String getBlock = form.srg ? "m_60734_" : "getBlock", isAir = form.srg ? "m_60795_" : "isAir";
		String state = "net/minecraft/world/level/block/state/BlockState", block = "net/minecraft/world/level/block/Block";
		int blocks = 0, airs = 0, nulls = 0, updated = -1;
		for (AbstractInsnNode insn : useOn.instructions.toArray()) {
			if (insn instanceof VarInsnNode store && store.getOpcode() == Opcodes.ASTORE && store.getPrevious().getOpcode() == Opcodes.ACONST_NULL && updated < 0) {
				updated = store.var;
				InsnList start = new InsnList();
				start.add(new VarInsnNode(Opcodes.ALOAD, 4));
				start.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/example/Hook", "start", "(L" + state + ";)L" + state + ";", false));
				start.add(new org.objectweb.asm.tree.InsnNode(Opcodes.POP));
				useOn.instructions.insert(store, start);
			}
			if (insn instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFNULL && nulls++ == 0) {
				InsnList preset = new InsnList();
				preset.add(new VarInsnNode(Opcodes.ALOAD, updated));
				preset.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/example/Hook", "preset", "(L" + state + ";)L" + state + ";", false));
				preset.add(new VarInsnNode(Opcodes.ASTORE, updated));
				useOn.instructions.insertBefore(jump.getPrevious(), preset);
			}
			if (insn instanceof MethodInsnNode call && call.name.equals(isAir) && airs++ == 0) {
				useOn.instructions.insert(call, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/example/Hook", "air", "(Z)Z", false));
			}
			if (insn instanceof MethodInsnNode call && call.name.equals(getBlock) && blocks++ == 1) {
				useOn.instructions.insert(call, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/example/Hook", "block", "(L" + block + ";)L" + block + ";", false));
			}
		}
		assertTrue(updated >= 0 && nulls > 0 && airs > 0 && blocks > 1, form + ": a hook's point is missing");
		// The class is written with its frames worked out again, as Mixin writes the classes it weaves.
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
			@Override
			protected String getCommonSuperClass(String a, String b) {
				return "java/lang/Object";
			}
		};
		node.accept(writer);
		hooked.put(SHOVEL, writer.toByteArray());
		return hooked;
	}

	private static MethodNode useOn(ClassNode node, Form form) {
		String name = form.srg ? "m_6225_" : "useOn";
		return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
	}

	private static int calls(MethodNode method, String name) {
		int n = 0;
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof MethodInsnNode call && call.name.equals(name)) n++;
		}
		return n;
	}

	private static ClassLoader loader(Map<String, byte[]> classes) {
		return new ClassLoader(ShovelPathsTest.class.getClassLoader()) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] data = classes.get(name.replace('.', '/'));
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
