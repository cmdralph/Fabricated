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
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * A method Fabricated declares on a game class when an interface a Fabric mod adds to it (with a mixin or a class
 * tweaker) brings a default method the class already inherits from another of its interfaces, with the same name and
 * descriptor (CORPUS-11: Entity Culling's {@code BlockEntityRenderFabricExtension} and NeoForge's
 * {@code IBlockEntityRendererExtension}, both on {@code BlockEntityRenderer}). The JVM refuses to choose between two
 * such defaults ({@code IncompatibleClassChangeError}) when the method is called on a class that declares neither. The
 * declared method calls the default the class already had, NeoForge's, which NeoForge's own classes override where
 * they need to. It is declared after Mixin applies, where no mixin declares the method (Fabricated's own resolve some
 * such pairs, as Fabric API's {@code FabricBlockStateModel} and NeoForge's extension on {@code BlockStateModel}).
 *
 * @param target     the class (internal name) that gets the method
 * @param owner      its direct superinterface whose default the method calls
 * @param name       the method's name
 * @param descriptor the method's descriptor
 */
public record DefaultBridge(String target, String owner, String name, String descriptor) {

	/**
	 * The bridges {@code target} needs once it also has the {@code added} interfaces.
	 *
	 * @param classes reads any class (game, installed or the mod's), or returns null when absent
	 */
	static List<DefaultBridge> of(String target, Collection<String> added, Function<String, ClassNode> classes) {
		ClassNode node = classes.apply(target);
		if (node == null || added.isEmpty()) return List.of();
		// A method the class (or, for a class, a superclass) declares wins over any default.
		Set<String> declared = new HashSet<>();
		for (MethodNode m : node.methods) declared.add(m.name + m.desc);
		if ((node.access & Opcodes.ACC_INTERFACE) == 0) {
			for (ClassNode sup = superclass(node, classes); sup != null; sup = superclass(sup, classes)) {
				for (MethodNode m : sup.methods) declared.add(m.name + m.desc);
			}
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
		return bridges;
	}

	private static ClassNode superclass(ClassNode node, Function<String, ClassNode> classes) {
		return node.superName == null ? null : classes.apply(node.superName);
	}

	/** Collects the default methods of {@code itf} and its superinterfaces, each where it is declared first going up. */
	private static void defaults(String itf, Function<String, ClassNode> classes, Map<String, String> defaults, Set<String> hierarchy) {
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
