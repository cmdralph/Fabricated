package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

class DefaultBridgeTest {
	private static final String BOX = "(Ljava/lang/Object;)Ljava/lang/String;";

	private final Map<String, ClassNode> classes = new HashMap<>();

	/**
	 * An interface (a class when {@code itf} is false) with these interfaces and methods, each {@code name + descriptor},
	 * abstract when it starts with "-".
	 */
	private ClassNode add(String name, boolean itf, String superName, List<String> interfaces, String... methods) {
		ClassNode node = new ClassNode();
		node.name = name;
		node.access = Opcodes.ACC_PUBLIC | (itf ? Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT : 0);
		node.superName = superName;
		node.interfaces.addAll(interfaces);
		for (String method : methods) {
			boolean abstractMethod = method.startsWith("-");
			String m = abstractMethod ? method.substring(1) : method;
			int paren = m.indexOf('(');
			node.methods.add(new MethodNode(Opcodes.ACC_PUBLIC | (abstractMethod ? Opcodes.ACC_ABSTRACT : 0), m.substring(0, paren), m.substring(paren), null, null));
		}
		classes.put(name, node);
		return node;
	}

	private List<DefaultBridge> bridges(String target, String added) {
		return DefaultBridge.of(target, List.of(added), classes::get);
	}

	@Test
	void aDefaultTheTargetHasTwiceGetsDeclaredCallingTheOneItHad() {
		// As NeoForge's IBlockEntityRendererExtension on BlockEntityRenderer, and Entity Culling's interface added to it.
		add("neo/Extension", true, "java/lang/Object", List.of(), "box" + BOX);
		add("game/Renderer", true, "java/lang/Object", List.of("neo/Extension"));
		add("mod/FabricExtension", true, "java/lang/Object", List.of(), "box" + BOX, "other()V");
		assertEquals(List.of(new DefaultBridge("game/Renderer", "neo/Extension", "box", BOX)), bridges("game/Renderer", "mod/FabricExtension"));

		// A class gets it too, unless it or a superclass declares the method (which wins over any default).
		add("game/Base", false, "java/lang/Object", List.of());
		add("game/Thing", false, "game/Base", List.of("neo/Extension"));
		assertEquals(List.of(new DefaultBridge("game/Thing", "neo/Extension", "box", BOX)), bridges("game/Thing", "mod/FabricExtension"));
		add("game/Base", false, "java/lang/Object", List.of(), "box" + BOX);
		assertEquals(List.of(), bridges("game/Thing", "mod/FabricExtension"));
	}

	@Test
	void noBridgeWhereTheJvmCanChoose() {
		add("neo/Extension", true, "java/lang/Object", List.of(), "box" + BOX);
		add("game/Renderer", true, "java/lang/Object", List.of("neo/Extension"));
		add("mod/Abstract", true, "java/lang/Object", List.of(), "-box" + BOX);
		add("mod/OtherDescriptor", true, "java/lang/Object", List.of(), "box(Ljava/lang/Object;)Ljava/lang/Object;");
		add("mod/Sub", true, "java/lang/Object", List.of("neo/Extension"), "box" + BOX);
		assertEquals(List.of(), bridges("game/Renderer", "mod/Abstract"), "an abstract method is no second default");
		assertEquals(List.of(), bridges("game/Renderer", "mod/OtherDescriptor"), "another descriptor is another method");
		assertEquals(List.of(), bridges("game/Renderer", "mod/Sub"), "an interface extending NeoForge's overrides its default");
		add("game/Declaring", true, "java/lang/Object", List.of("neo/Extension"), "box" + BOX);
		add("mod/FabricExtension", true, "java/lang/Object", List.of(), "box" + BOX);
		assertEquals(List.of(), bridges("game/Declaring", "mod/FabricExtension"), "the target declares the method itself");
		assertEquals(List.of(), bridges("game/Absent", "mod/FabricExtension"), "an absent target");
	}
}
