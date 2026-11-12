package io.github.cmdralph.fabricated.core.bytecode;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
	 * hostile class file is treated as absent; callers report the consequence). A hostile class may nest annotations
	 * without end (a stack overflow) or, for ASM before 9.9, claim an attribute larger than an array holds (SEC-6).
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
		} catch (IOException | RuntimeException | StackOverflowError | OutOfMemoryError e) {
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

	/**
	 * This source, each class read from it once, and whether it has one, kept: for classes that don't change, which a
	 * discovery reads again and again (pre-flight, translation and the linkage checks each read the game's). Callers
	 * never change the bytes they are given. Thread-safe: two threads reading a class first at once both get one copy.
	 * A read that throws is not kept.
	 */
	default ClassBytesSource memoised() {
		byte[] absent = new byte[0];
		Map<String, byte[]> read = new ConcurrentHashMap<>();
		return name -> {
			byte[] kept = read.get(name);
			if (kept == null) {
				byte[] data = read(name);
				if (data == null) data = absent;
				kept = read.putIfAbsent(name, data);
				if (kept == null) kept = data;
			}
			return kept == absent ? null : kept;
		};
	}
}
