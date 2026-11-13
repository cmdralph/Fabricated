package io.github.cmdralph.fabricated.core.translate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * A method Fabricated declares on a game class when an interface a Fabric mod adds to it (with a mixin or a class
 * tweaker) brings a default method the class already inherits from another of its interfaces, with the same name and
 * descriptor (CORPUS-11: Entity Culling's {@code BlockEntityRenderFabricExtension} and NeoForge's
 * {@code IBlockEntityRendererExtension}, both on {@code BlockEntityRenderer}). The JVM refuses to choose between two
 * such defaults ({@code IncompatibleClassChangeError}) when the method is called on a class that declares neither. The
 * declared method calls the default the class already had, NeoForge's, which NeoForge's own classes override where
 * they need to. It is declared after Mixin applies, where no mixin declares the method. Fabric API's renderer gives
 * {@code BlockStateModel} two such pairs ({@code FabricBlockStateModel}'s and NeoForge's extension's
 * {@code createGeometryKey} and {@code particleMaterial}).
 *
 * <p>A class's defaults through its superclasses' interfaces count too (DEFAULT-1: {@code ILivingEntityExtension.self()}
 * on every mob, which {@code LivingEntity} implements): the method then calls its superclass's ({@code super.name(...)}),
 * which the JVM resolves to that default.
 *
 * @param target     the class (internal name) that gets the method
 * @param owner      its direct superinterface whose default the method calls, or its superclass, whose method it calls
 * @param name       the method's name
 * @param descriptor the method's descriptor
 */
public record DefaultBridge(String target, String owner, String name, String descriptor) {
	/**
	 * The invisible annotation on the bridge methods Fabricated declares. The JVM and reflection never read invisible
	 * annotations, and no class of that name exists: it only marks the method, for the loader to take it out again where
	 * it declared it before Mixin ({@code FabricClassTransforms.removeDefaultBridges}).
	 */
	public static final String MARKER = "Lio/github/cmdralph/fabricated/loader/DefaultBridgeMethod;";

	/**
	 * The method declaring the bridge on its target: {@code public R name(args) { return Owner.super.name(args); }}, or
	 * {@code super.name(args)} where the owner is the class's superclass (not {@code ownerIsInterface}, DEFAULT-1), with
	 * its maxs (it has no branches, so no frames), and its {@link #MARKER}. It is synthetic, as a method no source
	 * declares, and not a compiler's bridge ({@code ACC_BRIDGE}): reflection tells it from a method the class or a mod's
	 * mixin declares, which overrides the default the bridge only calls. SEAM-3 and SEAM-4's check of whether a block
	 * keeps the platform's default reads it so ({@code KeepsDefault}, SEAM-7).
	 */
	public MethodNode method(boolean ownerIsInterface) {
		MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC, name, descriptor, null, null);
		method.invisibleAnnotations = new ArrayList<>(List.of(new AnnotationNode(MARKER)));
		InsnList code = method.instructions;
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		int slot = 1;
		for (Type argument : Type.getArgumentTypes(descriptor)) {
			code.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), slot));
			slot += argument.getSize();
		}
		code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, owner, name, descriptor, ownerIsInterface));
		Type result = Type.getReturnType(descriptor);
		code.add(new InsnNode(result.getOpcode(Opcodes.IRETURN)));
		method.maxLocals = slot;
		method.maxStack = Math.max(slot, result.getSize());
		return method;
	}

	/** Whether the method is a bridge {@link #method} made, by its {@link #MARKER}. */
	public static boolean isBridge(MethodNode method) {
		return method.invisibleAnnotations != null && method.invisibleAnnotations.stream().anyMatch(a -> MARKER.equals(a.desc));
	}

	/**
	 * The bridges {@code target} needs once it also has the {@code added} interfaces.
	 *
	 * @param classes reads any class (game, installed or the mod's), or returns null when absent
	 */
	static List<DefaultBridge> of(String target, Collection<String> added, Function<String, ClassNode> classes) {
		ClassNode node = classes.apply(target);
		if (node == null || added.isEmpty()) return List.of();
		boolean isClass = (node.access & Opcodes.ACC_INTERFACE) == 0;
		List<ClassNode> superclasses = isClass ? superclasses(node, classes) : List.of();
		// A method the class (or, for a class, a superclass) declares wins over any default.
		Set<String> declared = new HashSet<>();
		for (MethodNode m : node.methods) declared.add(m.name + m.desc);
		for (ClassNode sup : superclasses) {
			for (MethodNode m : sup.methods) declared.add(m.name + m.desc);
		}
		Map<String, String> addedDefaults = new HashMap<>();
		Set<String> addedHierarchy = new HashSet<>();
		for (String itf : added) defaults(itf, classes, addedDefaults, addedHierarchy);

		List<DefaultBridge> bridges = new ArrayList<>();
		Set<String> bridged = new HashSet<>();
		for (String direct : node.interfaces) {
			if (added.contains(direct)) continue;
			Map<String, String> inherited = new HashMap<>();
			Set<String> hierarchy = new HashSet<>();
			defaults(direct, classes, inherited, hierarchy);
			for (Map.Entry<String, String> e : inherited.entrySet()) {
				String key = e.getKey();
				String other = addedDefaults.get(key);
				// No conflict where one declaring interface extends the other: the JVM takes the more specific one.
				if (other == null || declared.contains(key) || hierarchy.contains(other) || addedHierarchy.contains(e.getValue())
						|| !bridged.add(key)) {
					continue;
				}
				int paren = key.indexOf('(');
				bridges.add(new DefaultBridge(target, direct, key.substring(0, paren), key.substring(paren)));
			}
		}
		if (isClass && node.superName != null) {
			// The defaults the class has through its superclasses' interfaces, reached through its superclass (DEFAULT-1).
			Map<String, String> inherited = new HashMap<>();
			Set<String> hierarchy = new HashSet<>();
			for (ClassNode sup : superclasses) {
				for (String itf : sup.interfaces) defaults(itf, classes, inherited, hierarchy);
			}
			for (Map.Entry<String, String> e : inherited.entrySet()) {
				String key = e.getKey();
				String other = addedDefaults.get(key);
				if (other == null || declared.contains(key) || hierarchy.contains(other) || addedHierarchy.contains(e.getValue())
						|| !bridged.add(key)) {
					continue;
				}
				int paren = key.indexOf('(');
				bridges.add(new DefaultBridge(target, node.superName, key.substring(0, paren), key.substring(paren)));
			}
		}
		return bridges;
	}

	static ClassNode superclass(ClassNode node, Function<String, ClassNode> classes) {
		return node.superName == null ? null : classes.apply(node.superName);
	}

	/**
	 * The class's superclasses that can be read, nearest first. A chain that comes back round, which only a broken jar's
	 * classes have (the mod's own are read too), ends where it would repeat, so the walk ends.
	 */
	private static List<ClassNode> superclasses(ClassNode node, Function<String, ClassNode> classes) {
		List<ClassNode> superclasses = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		seen.add(node.name);
		for (ClassNode sup = superclass(node, classes); sup != null && seen.add(sup.name); sup = superclass(sup, classes)) {
			superclasses.add(sup);
		}
		return superclasses;
	}

	/** Collects the default methods of {@code itf} and its superinterfaces, each where it is declared first going up. */
	static void defaults(String itf, Function<String, ClassNode> classes, Map<String, String> defaults, Set<String> hierarchy) {
		if (!hierarchy.add(itf)) return;
		ClassNode node = classes.apply(itf);
		if (node == null) return;
		for (MethodNode m : node.methods) {
			if ((m.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0 && !m.name.startsWith("<")) {
				defaults.putIfAbsent(m.name + m.desc, itf);
			}
		}
		for (String sup : node.interfaces) defaults(sup, classes, defaults, hierarchy);
	}
}
