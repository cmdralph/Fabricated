package io.github.cmdralph.fabricated.core.bytecode;

import java.io.IOException;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

/** Supplies raw class file bytes by internal name. Implementations never define or load classes. */
@FunctionalInterface
public interface ClassBytesSource {
	/**
	 * @param internalName e.g. {@code net/minecraft/world/level/Level}
	 * @return the class file bytes, or {@code null} if this source does not contain the class
	 */
	byte[] read(String internalName) throws IOException;

	/**
	 * The class parsed as a tree, or {@code null} when this source doesn't have it or it can't be read (a corrupt or
	 * hostile class file is treated as absent; callers report the consequence).
	 *
	 * @param parsingOptions {@link ClassReader} options, e.g. {@link ClassReader#SKIP_CODE}
	 */
	default ClassNode readNode(String internalName, int parsingOptions) {
		try {
			byte[] data = read(internalName);
			if (data == null) return null;
			ClassNode node = new ClassNode();
			new ClassReader(data).accept(node, parsingOptions);
			return node;
		} catch (IOException | RuntimeException | StackOverflowError e) {
			return null;
		}
	}

	/** A source with no classes. */
	ClassBytesSource EMPTY = name -> null;

	/** Tries {@code this} first, then {@code other}. */
	default ClassBytesSource or(ClassBytesSource other) {
		return name -> {
			byte[] data = read(name);
			return data != null ? data : other.read(name);
		};
	}
}
