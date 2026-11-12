package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
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
	void aDefaultASuperclassHasGetsDeclaredCallingTheSuperclasss() {
		// As NeoForge's IEntityExtension on Entity, and a mod's interface added to a mob (DEFAULT-1).
		add("neo/EntityExtension", true, "java/lang/Object", List.of(), "box" + BOX);
		add("game/Entity", false, "java/lang/Object", List.of("neo/EntityExtension"));
		add("game/Mob", false, "game/Entity", List.of());
		add("game/Pig", false, "game/Mob", List.of());
		add("mod/Query", true, "java/lang/Object", List.of(), "box" + BOX);
		assertEquals(List.of(new DefaultBridge("game/Pig", "game/Mob", "box", BOX)), bridges("game/Pig", "mod/Query"));

		// None where a superclass declares the method, or where the mod's interface extends the one the superclass has.
		add("mod/SubQuery", true, "java/lang/Object", List.of("neo/EntityExtension"), "box" + BOX);
		assertEquals(List.of(), bridges("game/Pig", "mod/SubQuery"));
		add("game/Mob", false, "game/Entity", List.of(), "box" + BOX);
		assertEquals(List.of(), bridges("game/Pig", "mod/Query"));
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

	/**
	 * Bridges, and the methods of a mixin that would hide a default, are told from classes' access, supertypes and
	 * members, never their code: translation reads the classes it asks these of without their code.
	 */
	@Test
	void classesReadWithoutTheirCodeAnswerTheSame() {
		Map<String, byte[]> files = Map.of(
				"neo/Extension", compiled("neo/Extension", true, "java/lang/Object", List.of(), "box" + BOX),
				"game/Base", compiled("game/Base", false, "java/lang/Object", List.of("neo/Extension"), "tick()V"),
				"game/Thing", compiled("game/Thing", false, "game/Base", List.of(), "tick()V"),
				"mod/FabricExtension", compiled("mod/FabricExtension", true, "java/lang/Object", List.of(), "box" + BOX));
		for (int options : List.of(ClassReader.SKIP_FRAMES, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES)) {
			java.util.function.Function<String, ClassNode> read = name -> {
				byte[] data = files.get(name);
				if (data == null) return null;
				ClassNode node = new ClassNode();
				new ClassReader(data).accept(node, options);
				return node;
			};
			assertEquals(List.of(new DefaultBridge("game/Thing", "game/Base", "box", BOX)), DefaultBridge.of("game/Thing", List.of("mod/FabricExtension"), read));
			ClassNode mixin = new ClassNode();
			mixin.name = "mod/ThingMixin";
			mixin.methods.add(new MethodNode(Opcodes.ACC_PRIVATE, "box", BOX, null, null));
			assertEquals(List.of("box" + BOX), List.copyOf(HiddenDefaults.renames(mixin, List.of("game/Thing"), read).keySet()));
		}
	}

	/** A class file (an interface when {@code itf}) whose methods ({@code name + descriptor}) have code returning a default. */
	private static byte[] compiled(String name, boolean itf, String superName, List<String> interfaces, String... methods) {
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | (itf ? Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT : 0), name, null, superName,
				interfaces.toArray(String[]::new));
		for (String method : methods) {
			int paren = method.indexOf('(');
			String desc = method.substring(paren);
			MethodVisitor code = writer.visitMethod(Opcodes.ACC_PUBLIC, method.substring(0, paren), desc, null, null);
			code.visitCode();
			if (desc.endsWith(")V")) {
				code.visitInsn(Opcodes.RETURN);
			} else {
				code.visitInsn(Opcodes.ACONST_NULL);
				code.visitInsn(Opcodes.ARETURN);
			}
			code.visitMaxs(0, 0);
			code.visitEnd();
		}
		writer.visitEnd();
		return writer.toByteArray();
	}
}
