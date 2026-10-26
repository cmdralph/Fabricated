package io.github.cmdralph.fabricated.core.translate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;

/**
 * HIDE-1: a mixin's private method that has the name and descriptor of a default method its target inherits gets
 * another name. Mixin adds such a method to the target as it is: it renames a {@code @Unique} one only where the target
 * class declares the method itself, not where the class inherits it from an interface. HotSpot then finds no
 * implementation of the interface's method in the target's subclasses, so calling it on them throws
 * {@code AbstractMethodError}, though the JVM specification skips private methods there. The platforms' extension
 * interfaces declare such defaults ({@code LivingEntity self()} on {@code LivingEntity}, Forge's and NeoForge's alike),
 * which the platform calls on every entity. Fabric API's {@code fabric-entity-events-v1} gives {@code LivingEntity} a
 * private {@code LivingEntity self()}: with it on Forge, Forge's every call of {@code self()} on a mob failed, as when a
 * pig moved through a fluid. The method's own calls, in the mixin and in the classes nested in it, use the new name.
 */
final class HiddenDefaults {
	private static final String MIXIN_PACKAGE = "Lorg/spongepowered/asm/mixin/";
	private static final String MIXINEXTRAS_PACKAGE = "Lcom/llamalad7/mixinextras/";
	private static final String UNIQUE = "Lorg/spongepowered/asm/mixin/Unique;";

	private HiddenDefaults() {
	}

	/**
	 * The new names of {@code mixin}'s methods that would hide a default method one of its {@code targets} inherits, by
	 * their old name and descriptor (sorted); empty when none would.
	 *
	 * @param classes reads any class (game, installed or the mod's), or returns null when absent
	 */
	static Map<String, String> renames(ClassNode mixin, Collection<String> targets, Function<String, ClassNode> classes) {
		Set<String> added = new HashSet<>();
		Set<String> names = new HashSet<>();
		for (MethodNode m : mixin.methods) {
			names.add(m.name);
			if (addedAsIs(m)) added.add(m.name + m.desc);
		}
		Map<String, String> renames = new TreeMap<>();
		if (added.isEmpty()) return renames;
		for (String target : targets) {
			ClassNode node = classes.apply(target);
			if (node == null || (node.access & Opcodes.ACC_INTERFACE) != 0) continue;
			for (String key : hidden(node, mixin.interfaces, added, classes)) {
				int paren = key.indexOf('(');
				String name = key.substring(0, paren);
				// Its own, so another mixin's method of the same name in the same target stays apart from it.
				String renamed = name + "$fabricated$" + Integer.toHexString(mixin.name.hashCode());
				for (int i = 2; names.contains(renamed); i++) renamed = name + "$fabricated$" + Integer.toHexString(mixin.name.hashCode()) + "$" + i;
				if (renames.putIfAbsent(key, renamed) == null) names.add(renamed);
			}
		}
		return renames;
	}

	/**
	 * A private instance method Mixin adds to the target under its own name: neither a {@code @Shadow} nor an
	 * {@code @Overwrite} (which are the target's own methods) nor an injector's handler (which Mixin names itself).
	 */
	private static boolean addedAsIs(MethodNode m) {
		if ((m.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT)) != Opcodes.ACC_PRIVATE || m.name.startsWith("<")) {
			return false;
		}
		for (AnnotationNode a : Annotations.all(m)) {
			if (a.desc.equals(UNIQUE)) continue;
			if (a.desc.startsWith(MIXIN_PACKAGE) || a.desc.startsWith(MIXINEXTRAS_PACKAGE)) return false;
		}
		return true;
	}

	/**
	 * The methods of {@code added} that would hide a default method of {@code target}'s interfaces (its superclasses',
	 * and the {@code mixinInterfaces} it gets) from its subclasses: the target doesn't declare them, nor does a
	 * superclass as an instance method that isn't private (which the JVM would find before any default).
	 */
	private static List<String> hidden(ClassNode target, List<String> mixinInterfaces, Set<String> added, Function<String, ClassNode> classes) {
		Set<String> declared = new HashSet<>();
		for (MethodNode m : target.methods) declared.add(m.name + m.desc);
		List<String> interfaces = new ArrayList<>(target.interfaces);
		interfaces.addAll(mixinInterfaces);
		Set<String> supers = new HashSet<>();
		for (ClassNode sup = DefaultBridge.superclass(target, classes); sup != null && supers.add(sup.name); sup = DefaultBridge.superclass(sup, classes)) {
			for (MethodNode m : sup.methods) {
				if ((m.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) == 0) declared.add(m.name + m.desc);
			}
			interfaces.addAll(sup.interfaces);
		}
		Map<String, String> defaults = new HashMap<>();
		Set<String> hierarchy = new HashSet<>();
		for (String itf : interfaces) DefaultBridge.defaults(itf, classes, defaults, hierarchy);
		List<String> hidden = new ArrayList<>();
		for (String key : added) {
			if (defaults.containsKey(key) && !declared.contains(key)) hidden.add(key);
		}
		return hidden;
	}

	/**
	 * {@code data}, the mixin {@code mixin} or a class nested in it, with its declarations of and references to the
	 * renamed methods of {@code mixin} renamed; null when it has none.
	 */
	static byte[] rewrite(byte[] data, String mixin, Map<String, String> renames) {
		ClassReader reader = new ClassReader(data);
		ClassWriter writer = new ClassWriter(0);
		boolean[] changed = {false};
		reader.accept(new ClassRemapper(writer, new Remapper() {
			@Override
			public String mapMethodName(String owner, String name, String descriptor) {
				String renamed = owner.equals(mixin) ? renames.get(name + descriptor) : null;
				if (renamed == null) return name;
				changed[0] = true;
				return renamed;
			}
		}), 0);
		return changed[0] ? writer.toByteArray() : null;
	}
}
