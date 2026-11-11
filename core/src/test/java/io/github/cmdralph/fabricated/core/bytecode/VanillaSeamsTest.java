package io.github.cmdralph.fabricated.core.bytecode;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.core.testutil.TestSources;

class VanillaSeamsTest {
	private static final String RESOLVER = "net/minecraft/world/level/block/piston/PistonStructureResolver";
	private static final String STATE = "net/minecraft/world/level/block/state/BlockState";

	/** A block state with the platform's default stickiness, and the blocks a platform mod's override refuses. */
	private static final String BLOCK_STATE = "package net.minecraft.world.level.block.state; public class BlockState {"
			+ " final String name; final boolean sticky; final java.util.Set<String> refuses;"
			+ " public BlockState(String name, boolean sticky, java.util.Set<String> refuses) { this.name = name; this.sticky = sticky; this.refuses = refuses; }"
			+ " public boolean isAir() { return name.equals(\"air\"); }"
			+ " public boolean isStickyBlock() { return sticky; }"
			+ " public boolean canStickTo(BlockState other) {"
			+ "  if (refuses.contains(other.name)) return false;"
			+ "  if (name.equals(\"honey\") && other.name.equals(\"slime\")) return false;"
			+ "  if (name.equals(\"slime\") && other.name.equals(\"honey\")) return false;"
			+ "  return isStickyBlock() || other.isStickyBlock(); } }";

	/**
	 * The resolver's checks as NeoForge 21.1 and Forge write them: an origin's stickiness, the line check (and NeoForge
	 * 26.1.2's), each followed by more of its conditions as there, the branching check, and a check of a state the code
	 * computes, which isn't a local.
	 */
	private static String resolver(String extra) {
		return "package net.minecraft.world.level.block.piston; import net.minecraft.world.level.block.state.BlockState;"
				+ " public class PistonStructureResolver {"
				+ " static boolean blocked(BlockState state) { return false; }"
				+ " public static boolean origin(BlockState state) { return state.isStickyBlock(); }"
				+ " public static boolean line(BlockState old, BlockState next) {"
				+ "  if (next.isAir() || !(old.canStickTo(next) && next.canStickTo(old)) || blocked(next)) return false; return true; }"
				+ " public static boolean neoForge2612Line(BlockState old, BlockState next) {"
				+ "  if (next.isAir() || !(old.canStickTo(next) && old.canStickTo(old)) || blocked(next)) return false; return true; }"
				+ " public static boolean branch(BlockState neighbour, BlockState from) {"
				+ "  if (neighbour.canStickTo(from) && from.canStickTo(neighbour) && !neighbour.isAir()) return true; return false; }"
				+ " static BlockState same(BlockState state) { return state; }"
				+ " public static boolean computed(BlockState a, BlockState b) { return same(a).canStickTo(b) && b.canStickTo(a); }" + extra + " }";
	}

	@Test
	void vanillasStickinessMethodsArePutBackWhereThePlatformsCodeAsksItsExtensions(@TempDir Path dir) throws Exception {
		Map<String, byte[]> platform = TestSources.compile(dir, Map.of(STATE.replace('.', '/') + ".java", BLOCK_STATE, RESOLVER + ".java", resolver("")));
		ClassNode node = node(platform.get(RESOLVER));
		assertEquals(List.of("isSticky(L" + STATE + ";)Z", "canStickToEachOther(L" + STATE + ";L" + STATE + ";)Z"), VanillaSeams.restore(node));

		assertEquals(List.of("isSticky"), calls(node, "origin"));
		assertEquals(List.of("isAir", "canStickToEachOther", "blocked"), calls(node, "line"));
		assertEquals(List.of("isAir", "canStickToEachOther", "blocked"), calls(node, "neoForge2612Line"));
		assertEquals(List.of("canStickToEachOther", "isAir"), calls(node, "branch"));
		// A state the code computes isn't a local the later call loads: left as the platform has it.
		assertEquals(List.of("same", "canStickTo", "canStickTo"), calls(node, "computed"));
		assertEquals(List.of("isStickyBlock"), calls(node, "isSticky"));
		assertEquals(List.of("canStickTo", "canStickTo"), calls(node, "canStickToEachOther"));
		assertEquals(List.of(new VanillaSeams.Restored("isSticky", "(L" + STATE + ";)Z", "a.isStickyBlock()"),
				new VanillaSeams.Restored("canStickToEachOther", "(L" + STATE + ";L" + STATE + ";)Z", "a.canStickTo(b) && b.canStickTo(a)")),
				VanillaSeams.restoredIn(node(write(node))));

		// Every check answers as the platform's code did; NeoForge 26.1.2's line check asks both ways, as its branching check does.
		Map<String, byte[]> restored = new HashMap<>(platform);
		restored.put(RESOLVER, write(node));
		Class<?> before = load(platform), after = load(restored);
		List<Object> states = states(before), statesAfter = states(after);
		for (int a = 0; a < states.size(); a++) {
			assertEquals(origin(before, states.get(a)), origin(after, statesAfter.get(a)));
			for (int b = 0; b < states.size(); b++) {
				for (String check : List.of("line", "branch", "computed")) {
					assertEquals(check(before, check, states.get(a), states.get(b)), check(after, check, statesAfter.get(a), statesAfter.get(b)), check + " " + a + " " + b);
				}
				assertEquals(check(before, "line", states.get(a), states.get(b)), check(after, "neoForge2612Line", statesAfter.get(a), statesAfter.get(b)));
			}
		}
		// Slime pulls stone, honey doesn't stick to slime, and a platform mod's block refusing slime keeps refusing it.
		assertTrue(check(after, "branch", statesAfter.get(STONE), statesAfter.get(SLIME)));
		assertFalse(check(after, "branch", statesAfter.get(SLIME), statesAfter.get(HONEY)));
		assertFalse(check(after, "line", statesAfter.get(SLIME), statesAfter.get(REFUSER)));

		// A hook of vanilla's isSticky takes effect where the platform asked its extension: here, every state is sticky.
		ClassNode hooked = node(write(node));
		MethodNode isSticky = hooked.methods.stream().filter(m -> m.name.equals("isSticky")).findFirst().orElseThrow();
		isSticky.instructions.clear();
		isSticky.instructions.add(new InsnNode(Opcodes.ICONST_1));
		isSticky.instructions.add(new InsnNode(Opcodes.IRETURN));
		restored.put(RESOLVER, write(hooked));
		Class<?> withHook = load(restored);
		assertTrue(origin(withHook, states(withHook).get(STONE)));
	}

	@Test
	void aClassThatKeepsVanillasMethodsOrHasNoneOfThePlatformsCodeIsLeftAlone(@TempDir Path dir) throws Exception {
		// NeoForge 26.2 and 26.3 keep both methods, their bodies asking the extensions.
		String kept = " private static boolean isSticky(BlockState state) { return state.isStickyBlock(); }"
				+ " private static boolean canStickToEachOther(BlockState a, BlockState b) { return a.canStickTo(b) && b.canStickTo(a); }";
		Map<String, byte[]> neoForge262 = TestSources.compile(dir.resolve("kept"), Map.of(STATE + ".java", BLOCK_STATE, RESOLVER + ".java", resolver(kept)));
		assertTrue(VanillaSeams.restore(node(neoForge262.get(RESOLVER))).isEmpty());
		ClassBytesSource game = neoForge262::get;
		assertArrayEquals(neoForge262.get(RESOLVER), VanillaSeams.over(game).read(RESOLVER));

		// Another class's calls of the extensions are no seam's.
		Map<String, byte[]> other = TestSources.compile(dir.resolve("other"), Map.of(STATE + ".java", BLOCK_STATE, "net/minecraft/Other.java",
				"package net.minecraft; public class Other { static boolean sticky(net.minecraft.world.level.block.state.BlockState s) { return s.isStickyBlock(); } }"));
		assertTrue(VanillaSeams.restore(node(other.get("net/minecraft/Other"))).isEmpty());
		assertArrayEquals(other.get("net/minecraft/Other"), VanillaSeams.over(other::get).read("net/minecraft/Other"));
	}

	@Test
	void aSeamIsPutBackUnderItsSrgNameWhereTheGameRunsWithThem(@TempDir Path dir) throws Exception {
		// Forge 47 runs 1.20.1 with SRG's member names: the resolver's own methods are named m_<number>_.
		Map<String, byte[]> forge47 = TestSources.compile(dir, Map.of(STATE + ".java", BLOCK_STATE, RESOLVER + ".java",
				resolver(" public boolean m_60422_() { return true; }")));
		ClassNode node = node(forge47.get(RESOLVER));
		assertEquals(List.of("m_155937_(L" + STATE + ";)Z", "m_155939_(L" + STATE + ";L" + STATE + ";)Z"), VanillaSeams.restore(node));
		assertEquals(List.of("m_155937_"), calls(node, "origin"));
		Map<String, byte[]> restored = new HashMap<>(forge47);
		restored.put(RESOLVER, VanillaSeams.over(forge47::get).read(RESOLVER));
		Class<?> loaded = load(restored);
		assertTrue(origin(loaded, states(loaded).get(SLIME)));
	}

	private static final int STONE = 0, SLIME = 1, HONEY = 2, REFUSER = 4;

	/** Stone, slime, honey, air, a platform mod's block refusing slime, and a platform mod's sticky block refusing stone. */
	private static List<Object> states(Class<?> resolver) throws Exception {
		Constructor<?> state = resolver.getClassLoader().loadClass(STATE.replace('/', '.')).getConstructor(String.class, boolean.class, java.util.Set.class);
		List<Object> states = new ArrayList<>();
		states.add(state.newInstance("stone", false, java.util.Set.of()));
		states.add(state.newInstance("slime", true, java.util.Set.of()));
		states.add(state.newInstance("honey", true, java.util.Set.of()));
		states.add(state.newInstance("air", false, java.util.Set.of()));
		states.add(state.newInstance("refuser", false, java.util.Set.of("slime")));
		states.add(state.newInstance("modsticky", true, java.util.Set.of("stone")));
		return states;
	}

	private static boolean origin(Class<?> resolver, Object state) throws Exception {
		return (boolean) resolver.getMethod("origin", state.getClass()).invoke(null, state);
	}

	private static boolean check(Class<?> resolver, String name, Object a, Object b) throws Exception {
		Method check = resolver.getMethod(name, a.getClass(), b.getClass());
		return (boolean) check.invoke(null, a, b);
	}

	/** The resolver of these classes, defined (and so verified) by a loader of its own. */
	private static Class<?> load(Map<String, byte[]> classes) throws Exception {
		ClassLoader loader = new ClassLoader(VanillaSeamsTest.class.getClassLoader()) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] data = classes.get(name.replace('.', '/'));
				if (data == null) throw new ClassNotFoundException(name);
				return defineClass(name, data, 0, data.length);
			}
		};
		Class<?> resolver = loader.loadClass(RESOLVER.replace('/', '.'));
		for (Method m : resolver.getDeclaredMethods()) m.setAccessible(true);
		return resolver;
	}

	/**
	 * As a class loads, the rewrite is all or nothing: it puts the seams back as {@link VanillaSeams#restore} does, and
	 * where it throws, the class keeps its own methods, untouched, and the failure is returned, not thrown.
	 */
	@Test
	void theRewriteAtLoadLeavesTheClassAsItWasWhereItFails(@TempDir Path dir) throws Exception {
		Map<String, byte[]> platform = TestSources.compile(dir, Map.of(STATE.replace('.', '/') + ".java", BLOCK_STATE, RESOLVER + ".java", resolver("")));
		ClassNode whole = node(platform.get(RESOLVER));
		VanillaSeams.Restoring done = VanillaSeams.restoreWhole(whole);
		assertEquals(null, done.failure());
		assertEquals(VanillaSeams.restore(node(platform.get(RESOLVER))), done.restored());
		assertEquals(List.of("isAir", "canStickToEachOther", "blocked"), calls(whole, "line"));

		// A method the rewrite can't read (no descriptor): it throws, and the class's methods are those it had, as they were.
		ClassNode broken = node(platform.get(RESOLVER));
		broken.methods.add(new MethodNode(Opcodes.ACC_STATIC, "unreadable", null, null, null));
		List<MethodNode> methods = List.copyOf(broken.methods);
		List<List<String>> before = methods.stream().map(m -> calls(broken, m.name)).toList();
		VanillaSeams.Restoring failed = VanillaSeams.restoreWhole(broken);
		assertTrue(failed.failure() instanceof NullPointerException, String.valueOf(failed.failure()));
		assertEquals(List.of(), failed.restored());
		assertEquals(methods.size(), broken.methods.size());
		for (int i = 0; i < methods.size(); i++) assertTrue(methods.get(i) == broken.methods.get(i), methods.get(i).name);
		assertEquals(before, methods.stream().map(m -> calls(broken, m.name)).toList());
		assertEquals(List.of("isAir", "canStickTo", "canStickTo", "blocked"), calls(broken, "line"));
	}

	private static List<String> calls(ClassNode node, String method) {
		List<String> calls = new ArrayList<>();
		for (MethodNode m : node.methods) {
			if (!m.name.equals(method)) continue;
			for (AbstractInsnNode insn : m.instructions) if (insn instanceof MethodInsnNode call) calls.add(call.name);
		}
		return calls;
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
