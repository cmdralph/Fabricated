package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import io.github.cmdralph.fabricated.core.testutil.TestSources;

class LibraryBridgesTest {
	/** A library as Brigadier is: a node with private maps of its children, a root node, and a dispatcher holding the root. */
	static final Map<String, String> LIBRARY = Map.of(
			"lib/Node.java", """
					package lib;
					import java.util.*;
					public class Node {
						private final Map<String, Node> children = new LinkedHashMap<>();
						private int count;
						public void add(String name, Node child) { children.put(name, child); count++; }
						public boolean has(String name) { return children.containsKey(name); }
						public int count() { return count; }
					}""",
			"lib/Root.java", "package lib; public class Root extends Node { }",
			"lib/Dispatcher.java", """
					package lib;
					public class Dispatcher {
						private final Root root = new Root();
						public Root root() { return root; }
					}""");

	/** Carpet's command removal: interfaces the mixins give the library's classes, and the mod's code using them. */
	static final Map<String, String> MOD = Map.of(
			"mod/NodeAccess.java", "package mod; public interface NodeAccess { void removeChild(String name); int childCount(); }",
			"mod/DispatcherAccess.java", "package mod; public interface DispatcherAccess { void unregister(String name); }",
			"mod/mixin/NodeMixin.java", """
					package mod.mixin;
					import java.util.Map;
					import org.spongepowered.asm.mixin.*;
					@Mixin(value = lib.Node.class, remap = false)
					public class NodeMixin implements mod.NodeAccess {
						@Shadow @Final private Map<String, lib.Node> children;
						@Shadow private int count;
						@Override public void removeChild(String name) {
							if (children.remove(name) != null) count = decrement(count);
						}
						@Override public int childCount() { return count; }
						private static int decrement(int value) { return value - 1; }
					}""",
			"mod/mixin/DispatcherMixin.java", """
					package mod.mixin;
					import org.spongepowered.asm.mixin.*;
					@Mixin(value = lib.Dispatcher.class, remap = false)
					public class DispatcherMixin implements mod.DispatcherAccess {
						@Shadow @Final private lib.Root root;
						@Override public void unregister(String name) { ((mod.NodeAccess) this.root).removeChild(name); }
					}""",
			"mod/Commands.java", """
					package mod;
					import java.util.function.Consumer;
					public class Commands {
						public static void drop(lib.Dispatcher dispatcher, String name) { ((DispatcherAccess) dispatcher).unregister(name); }
						public static boolean isNode(Object o) { return o instanceof NodeAccess; }
						public static int count(Object o) { NodeAccess node = (NodeAccess) o; return node.childCount(); }
						public static void dropLater(lib.Node node, String name) {
							Consumer<String> remover = ((NodeAccess) node)::removeChild;
							remover.accept(name);
						}
					}""");

	@Test
	void mixinsGivingALibraryClassAnInterfaceBecomeBridgesTheModCalls(@TempDir Path dir) throws Exception {
		Map<String, byte[]> classes = TestSources.compile(dir, TestSources.MIXIN_ANNOTATIONS, LIBRARY, MOD);
		Map<String, LibraryBridges.Duck> ducks = new HashMap<>();
		Map<String, byte[]> loaded = new HashMap<>(classes);
		for (String[] mixin : List.of(new String[] {"mod/mixin/NodeMixin", "lib/Node", "mod/NodeAccess$FabricatedBridge"},
				new String[] {"mod/mixin/DispatcherMixin", "lib/Dispatcher", "mod/DispatcherAccess$FabricatedBridge"})) {
			LibraryBridges.Bridge bridge = LibraryBridges.of(classes.get(mixin[0]), mixin[1], name -> node(classes, name), name -> node(classes, name),
					itf -> 1, mixin[2]);
			assertNotNull(bridge, mixin[0]);
			bridge.ducks().forEach(d -> ducks.put(d.itf(), d));
			loaded.put(bridge.name(), bridge.bytes());
		}
		assertEquals(Map.of("mod/NodeAccess", "mod/NodeAccess$FabricatedBridge", "mod/DispatcherAccess", "mod/DispatcherAccess$FabricatedBridge"),
				Map.of("mod/NodeAccess", ducks.get("mod/NodeAccess").bridge(), "mod/DispatcherAccess", ducks.get("mod/DispatcherAccess").bridge()));
		for (String name : List.copyOf(loaded.keySet())) {
			if (!name.startsWith("mod/")) continue;
			byte[] rewritten = LibraryBridges.rewrite(loaded.get(name), ducks);
			if (rewritten != null) loaded.put(name, rewritten);
		}
		assertNull(LibraryBridges.rewrite(classes.get("lib/Node"), ducks), "a class not using the interfaces stays as it is");

		ClassLoader loader = new BytesClassLoader(loaded);
		Class<?> dispatcherClass = loader.loadClass("lib.Dispatcher");
		Class<?> nodeClass = loader.loadClass("lib.Node");
		Class<?> commands = loader.loadClass("mod.Commands");
		Object dispatcher = dispatcherClass.getConstructor().newInstance();
		Object root = dispatcherClass.getMethod("root").invoke(dispatcher);
		nodeClass.getMethod("add", String.class, nodeClass).invoke(root, "tick", nodeClass.getConstructor().newInstance());
		nodeClass.getMethod("add", String.class, nodeClass).invoke(root, "spawn", nodeClass.getConstructor().newInstance());
		Method has = nodeClass.getMethod("has", String.class);

		commands.getMethod("drop", dispatcherClass, String.class).invoke(null, dispatcher, "tick");
		assertFalse((Boolean) has.invoke(root, "tick"), "the dispatcher's bridge removed the child through the node's");
		assertEquals(1, commands.getMethod("count", Object.class).invoke(null, root), "the field the bridge wrote");
		commands.getMethod("dropLater", nodeClass, String.class).invoke(null, root, "spawn");
		assertFalse((Boolean) has.invoke(root, "spawn"), "a method reference to the interface's method calls the bridge");

		Method isNode = commands.getMethod("isNode", Object.class);
		assertTrue((Boolean) isNode.invoke(null, root), "a subclass of the library class has the interface");
		assertFalse((Boolean) isNode.invoke(null, "text"));
		InvocationTargetException e = assertThrows(InvocationTargetException.class, () -> commands.getMethod("count", Object.class).invoke(null, "text"));
		assertTrue(e.getCause() instanceof ClassCastException, "a cast of another object still fails: " + e.getCause());
	}

	@Test
	void aMixinWithStateOfItsOwnOrAnInterfaceImplementedElsewhereIsNotBridged(@TempDir Path dir) throws Exception {
		Map<String, String> mod = Map.of(
				"mod/Counter.java", "package mod; public interface Counter { int next(); }",
				"mod/mixin/CounterMixin.java", """
						package mod.mixin;
						import org.spongepowered.asm.mixin.*;
						@Mixin(lib.Node.class)
						public class CounterMixin implements mod.Counter {
							@Unique private int next;
							@Override public int next() { return next++; }
						}""",
				"mod/mixin/CountMixin.java", """
						package mod.mixin;
						import org.spongepowered.asm.mixin.*;
						@Mixin(lib.Node.class)
						public class CountMixin implements mod.Counter {
							@Shadow private int count;
							@Override public int next() { return count; }
						}""");
		Map<String, byte[]> classes = TestSources.compile(dir, TestSources.MIXIN_ANNOTATIONS, LIBRARY, mod);
		assertNull(LibraryBridges.of(classes.get("mod/mixin/CounterMixin"), "lib/Node", name -> node(classes, name), name -> node(classes, name),
				itf -> 1, "mod/Counter$FabricatedBridge"), "a field of the mixin's own is state the library class doesn't have");
		assertNotNull(LibraryBridges.of(classes.get("mod/mixin/CountMixin"), "lib/Node", name -> node(classes, name), name -> node(classes, name),
				itf -> 1, "mod/Counter$FabricatedBridge"));
		assertNull(LibraryBridges.of(classes.get("mod/mixin/CountMixin"), "lib/Node", name -> node(classes, name), name -> node(classes, name),
				itf -> 2, "mod/Counter$FabricatedBridge"), "another class implementing the interface would get the bridge's calls");
	}

	private static ClassNode node(Map<String, byte[]> classes, String name) {
		byte[] data = classes.get(name);
		if (data == null) return null;
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, ClassReader.SKIP_FRAMES);
		return node;
	}

	/** Defines the given classes itself, verifying them as any class loader's. */
	private static final class BytesClassLoader extends ClassLoader {
		private final Map<String, byte[]> classes;

		BytesClassLoader(Map<String, byte[]> classes) {
			super(LibraryBridgesTest.class.getClassLoader());
			this.classes = classes;
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			synchronized (getClassLoadingLock(name)) {
				byte[] data = classes.get(name.replace('.', '/'));
				if (data == null) return super.loadClass(name, resolve);
				Class<?> c = findLoadedClass(name);
				if (c == null) c = defineClass(name, data, 0, data.length);
				if (resolve) resolveClass(c);
				return c;
			}
		}
	}
}
