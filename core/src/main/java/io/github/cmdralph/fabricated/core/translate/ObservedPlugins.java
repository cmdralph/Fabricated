package io.github.cmdralph.fabricated.core.translate;

import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;

/**
 * A Fabric mod's mixin config plugin whose {@code shouldApplyMixin} tells Fabricated what it answers Mixin (LEAVE-4).
 * Pre-flight leaves out the mixins that don't fit the platform's code; on Fabric a config's plugin turns many of them
 * down itself (Pehkui's, every version-specific {@code compatNNN} mixin but this version's), and Mixin only asks it as it
 * selects the config's mixins, once the game starts: nothing runs the plugin earlier or more often. The plugin's method
 * is renamed and another takes its place, which calls it and hands its answer to the loader's {@code PluginAnswers},
 * which records it for the report and returns it, but false for a mixin pre-flight left out whole: those stay listed in
 * the config, so that Mixin asks the plugin about them as it would on Fabric, and none applies. Each of those is made
 * {@link #inert} too, as Mixin applies every mixin of a config whose plugin it couldn't load (its {@code PluginHandle}
 * answers yes for all of them then), and the package guard may serve another jar's copy of the plugin's class.
 */
final class ObservedPlugins {
	/** The in-game record of the answers (the loader's {@code PluginAnswers}, on every platform). */
	static final String ANSWERS = "io/github/cmdralph/fabricated/loader/PluginAnswers";
	private static final String PSEUDO = "Lorg/spongepowered/asm/mixin/Pseudo;";
	private static final String NAME = "shouldApplyMixin";
	private static final String DESC = "(Ljava/lang/String;Ljava/lang/String;)Z";
	private static final String RENAMED = "fabricated$shouldApplyMixin";
	/** Within the class file's limit for a string constant, the left-out mixins' names, one per line. */
	private static final int MAX_NAMES = 60_000;

	private ObservedPlugins() {
	}

	/** Whether the plugin's class declares a {@code shouldApplyMixin} with code, which {@link #observe} rewrites. */
	static boolean observable(byte[] plugin) {
		return method(read(plugin)) != null;
	}

	/** Whether {@link #observe} can name the mixins of {@code leftOut} (by their class names) in the class. */
	static boolean canName(Collection<String> leftOut) {
		return String.join("\n", leftOut).getBytes(StandardCharsets.UTF_8).length <= MAX_NAMES;
	}

	/**
	 * The plugin's class with its {@code shouldApplyMixin} observed, turning down the mixins of {@code leftOut} (by their
	 * class names), which it must be able to name ({@link #canName}); the class must be {@link #observable}.
	 */
	static byte[] observe(byte[] plugin, Collection<String> leftOut) {
		ClassNode node = read(plugin);
		MethodNode own = method(node);
		if (own == null || !canName(leftOut)) throw new IllegalArgumentException("the plugin " + node.name + " can't be observed");
		String names = String.join("\n", leftOut);
		own.name = RENAMED;
		own.access = own.access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED) | Opcodes.ACC_PRIVATE;
		MethodNode observed = new MethodNode(Opcodes.ACC_PUBLIC, NAME, DESC, null, null);
		observed.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		observed.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
		observed.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
		observed.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.name, RENAMED, DESC, false));
		observed.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
		observed.instructions.add(new LdcInsnNode(names));
		observed.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ANSWERS, "answered", "(ZLjava/lang/String;Ljava/lang/String;)Z", false));
		observed.instructions.add(new InsnNode(Opcodes.IRETURN));
		observed.maxStack = 3;
		observed.maxLocals = 3;
		node.methods.add(observed);
		ClassWriter writer = new ClassWriter(0);
		node.accept(writer);
		return writer.toByteArray();
	}

	/**
	 * A mixin pre-flight left out whole, as it stays listed for the plugin's answers: Mixin applies nothing of it, whatever
	 * the plugin answers. It is an empty public interface of the same {@code @Mixin}, marked {@code @Pseudo}. Mixin (0.8.5,
	 * 0.8.7 and Fabric's) reads its targets' names and asks the plugin as it would the mixin's, takes an interface without
	 * methods as an accessor mixin, which may target a class or an interface and whose superclass is {@code Object}, skips
	 * a target that isn't there ({@code @Pseudo}), and finds no member, injector or initialiser to apply: applied, it only
	 * adds itself, empty, to what it targets.
	 */
	static byte[] inert(byte[] mixin) {
		ClassNode node = new ClassNode();
		new ClassReader(mixin).accept(node, ClassReader.SKIP_CODE);
		AnnotationNode declared = Annotations.find(node.invisibleAnnotations, node.visibleAnnotations, Annotations.MIXIN);
		ClassWriter writer = new ClassWriter(0);
		writer.visit(node.version, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, node.name, null, "java/lang/Object", null);
		if (node.sourceFile != null) writer.visitSource(node.sourceFile, null);
		declared.accept(writer.visitAnnotation(declared.desc, false));
		writer.visitAnnotation(PSEUDO, false).visitEnd();
		writer.visitEnd();
		return writer.toByteArray();
	}

	private static ClassNode read(byte[] plugin) {
		ClassNode node = new ClassNode();
		new ClassReader(plugin).accept(node, 0);
		return node;
	}

	/** The class's own {@code shouldApplyMixin}, where it is a concrete class that has one with code and wasn't observed. */
	private static MethodNode method(ClassNode node) {
		if ((node.access & (Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT)) != 0) return null;
		MethodNode found = null;
		for (MethodNode m : node.methods) {
			if (m.name.equals(RENAMED)) return null;
			if (m.name.equals(NAME) && m.desc.equals(DESC) && (m.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) found = m;
		}
		return found;
	}
}
