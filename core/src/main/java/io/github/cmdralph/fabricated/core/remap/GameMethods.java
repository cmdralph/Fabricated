package io.github.cmdralph.fabricated.core.remap;

import java.util.List;
import java.util.function.BiFunction;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

/**
 * A game class's methods, each its name and descriptor ({@code name(desc)}), in its class file's order, by its runtime
 * name: what a mixin's selector naming a method by its name alone picks, the first of that name (Mixin's default
 * quantifier takes one). Where the runtime gives a method's overloads names of their own, which Intermediary leaves
 * under one official name, remapping such a selector takes the name of the overload it picks on Fabric (PLAIN-2:
 * {@code RenderSystem.setShaderTexture} on Forge 43).
 */
@FunctionalInterface
public interface GameMethods {
	/** Knows no class. */
	GameMethods NONE = name -> null;

	/**
	 * @param runtimeName a game class's internal name at runtime
	 * @return its methods' names and descriptors in its class file's order, or null when the class isn't known
	 */
	List<String> of(String runtimeName);

	/**
	 * The classes a reader gives, by runtime name.
	 *
	 * @param classes a class's tree, read with the parsing options given it, or null when there is no such class
	 */
	static GameMethods ofClasses(BiFunction<String, Integer, ClassNode> classes) {
		return name -> {
			ClassNode node = classes.apply(name, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
			return node == null ? null : node.methods.stream().map(m -> m.name + m.desc).toList();
		};
	}
}
